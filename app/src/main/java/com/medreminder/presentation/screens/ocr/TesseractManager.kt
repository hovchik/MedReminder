package com.medreminder.presentation.screens.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages Tesseract OCR engine for languages not supported by ML Kit
 * (Armenian, Russian, Farsi).
 *
 * Downloads traineddata files on first use from the tessdata_best repository,
 * then caches them in the app's internal storage.
 */
class TesseractManager(private val context: Context) {

    companion object {
        private const val TAG = "TesseractManager"
        private const val TESSDATA_DIR = "tessdata"
        private const val BASE_URL =
            "https://github.com/tesseract-ocr/tessdata_best/raw/main/"

        /** Supported Tesseract language codes */
        val SUPPORTED_LANGUAGES = setOf("rus", "hye", "fas")
    }

    private var tessApi: TessBaseAPI? = null
    private var currentLangCode: String? = null

    /**
     * Returns the parent directory that contains the tessdata/ folder.
     */
    private fun getDataPath(): String {
        return context.filesDir.absolutePath
    }

    /**
     * Returns the tessdata directory, creating it if necessary.
     */
    private fun getTessdataDir(): File {
        val dir = File(getDataPath(), TESSDATA_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Check if the traineddata file for a given language already exists locally.
     */
    fun isLanguageAvailable(langCode: String): Boolean {
        val file = File(getTessdataDir(), "$langCode.traineddata")
        return file.exists() && file.length() > 0
    }

    /**
     * Downloads the traineddata file for the given language.
     * Reports progress via the [onProgress] callback (0.0 to 1.0).
     *
     * @return true if download succeeded
     */
    suspend fun downloadLanguageData(
        langCode: String,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL$langCode.traineddata")
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.connect()

            // GitHub raw URLs redirect — follow redirects
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Download failed for $langCode: HTTP $responseCode")
                connection.disconnect()
                return@withContext false
            }

            val totalBytes = connection.contentLength
            val destFile = File(getTessdataDir(), "$langCode.traineddata")
            val tempFile = File(getTessdataDir(), "$langCode.traineddata.tmp")

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            onProgress(totalRead.toFloat() / totalBytes)
                        }
                    }
                }
            }
            connection.disconnect()

            // Rename temp to final
            tempFile.renameTo(destFile)
            Log.d(TAG, "Downloaded $langCode.traineddata (${destFile.length()} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download $langCode traineddata", e)
            // Clean up partial file
            File(getTessdataDir(), "$langCode.traineddata.tmp").delete()
            false
        }
    }

    /**
     * Initializes the Tesseract engine for the given language.
     * The traineddata file must already be downloaded.
     *
     * @return true if initialization succeeded
     */
    fun initEngine(langCode: String): Boolean {
        // Reuse existing engine if already initialized for this language
        if (tessApi != null && currentLangCode == langCode) return true

        try {
            release()
            val api = TessBaseAPI()
            // Use LSTM-only mode — required for tessdata_best models
            val success = api.init(getDataPath(), langCode, TessBaseAPI.OEM_LSTM_ONLY)
            if (success) {
                // Use single block of text PSM for medication labels
                api.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
                tessApi = api
                currentLangCode = langCode
                Log.d(TAG, "Tesseract initialized for $langCode (LSTM-only)")
            } else {
                Log.e(TAG, "Tesseract init failed for $langCode")
                api.recycle()
            }
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Tesseract init error", e)
            return false
        }
    }

    /**
     * Preprocesses a bitmap for better OCR accuracy:
     * converts to grayscale and increases contrast.
     */
    private fun preprocessBitmap(bitmap: Bitmap): Bitmap {
        // Upscale small images — Tesseract needs ~300 DPI / min ~1500px width
        val minWidth = 1500
        val source = if (bitmap.width < minWidth) {
            val scale = minWidth.toFloat() / bitmap.width
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }

        val width = source.width
        val height = source.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Convert to high-contrast grayscale
        val contrast = 1.5f
        val translate = (-(128f * contrast) + 128f)
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, translate,
                0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, translate,
                0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        if (source != bitmap) source.recycle()
        return result
    }

    /**
     * Recognizes text from a bitmap using the initialized Tesseract engine.
     * Applies preprocessing for better accuracy on complex scripts.
     *
     * @return recognized text, or empty string on failure
     */
    suspend fun recognizeText(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        try {
            val api = tessApi ?: return@withContext ""
            val processed = preprocessBitmap(bitmap)
            api.setImage(processed)
            val text = api.utF8Text ?: ""
            api.clear()
            processed.recycle()
            text
        } catch (e: Exception) {
            Log.e(TAG, "Tesseract recognition failed", e)
            ""
        }
    }

    /**
     * Releases Tesseract resources.
     */
    fun release() {
        try {
            tessApi?.recycle()
        } catch (_: Exception) {}
        tessApi = null
        currentLangCode = null
    }
}
