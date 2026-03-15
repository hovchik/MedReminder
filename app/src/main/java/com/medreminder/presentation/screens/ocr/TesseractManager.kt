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

        // jsDelivr CDN reliably serves GitHub LFS files (primary source)
        private const val CDN_URL =
            "https://cdn.jsdelivr.net/gh/tesseract-ocr/tessdata_best@main/"

        // Direct GitHub raw URL as fallback
        private const val GITHUB_URL =
            "https://github.com/tesseract-ocr/tessdata_best/raw/main/"

        // Minimum valid traineddata file size (500 KB).
        // Real traineddata files are 1–15 MB; anything smaller is a
        // corrupted download, HTML error page, or Git LFS pointer.
        private const val MIN_TRAINEDDATA_SIZE = 500_000L

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
     * Check if a valid traineddata file for a given language exists locally.
     * Validates the file is large enough to be a real traineddata (not a
     * Git LFS pointer or error page).
     */
    fun isLanguageAvailable(langCode: String): Boolean {
        val file = File(getTessdataDir(), "$langCode.traineddata")
        val available = file.exists() && file.length() > MIN_TRAINEDDATA_SIZE
        if (file.exists() && !available) {
            // File exists but is too small — corrupted or LFS pointer, delete it
            Log.w(TAG, "Deleting invalid $langCode.traineddata (${file.length()} bytes)")
            file.delete()
        }
        return available
    }

    /**
     * Opens an HTTP connection that manually follows redirects across domains.
     * HttpURLConnection.instanceFollowRedirects does not follow redirects
     * to different hosts (e.g. github.com → objects.githubusercontent.com),
     * which breaks GitHub LFS downloads.
     */
    private fun openConnectionFollowingRedirects(
        startUrl: String,
        maxRedirects: Int = 10
    ): HttpURLConnection {
        var url = URL(startUrl)
        var redirects = 0

        while (true) {
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false  // we handle redirects manually
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()

                if (location == null || redirects >= maxRedirects) {
                    throw java.io.IOException(
                        "Redirect failed: ${if (location == null) "no Location header" else "too many redirects"}"
                    )
                }

                url = if (location.startsWith("http")) {
                    URL(location)
                } else {
                    URL(url, location)
                }
                redirects++
                Log.d(TAG, "Following redirect #$redirects → $url")
            } else {
                return connection
            }
        }
    }

    /**
     * Downloads the traineddata file for the given language.
     * Tries jsDelivr CDN first (reliable for GitHub LFS), then falls back
     * to direct GitHub raw URL with manual redirect following.
     *
     * Reports progress via the [onProgress] callback (0.0 to 1.0).
     *
     * @return true if download succeeded
     */
    suspend fun downloadLanguageData(
        langCode: String,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        // Delete any existing corrupted file
        val destFile = File(getTessdataDir(), "$langCode.traineddata")
        if (destFile.exists() && destFile.length() <= MIN_TRAINEDDATA_SIZE) {
            destFile.delete()
        }

        val urls = listOf(
            "$CDN_URL$langCode.traineddata",
            "$GITHUB_URL$langCode.traineddata"
        )

        for ((index, downloadUrl) in urls.withIndex()) {
            val sourceName = if (index == 0) "jsDelivr CDN" else "GitHub raw"
            Log.d(TAG, "Trying download from $sourceName: $downloadUrl")

            val success = tryDownload(downloadUrl, langCode, onProgress)
            if (success) {
                Log.d(TAG, "Downloaded $langCode.traineddata from $sourceName (${destFile.length()} bytes)")
                return@withContext true
            }

            Log.w(TAG, "Download from $sourceName failed, ${if (index < urls.lastIndex) "trying fallback..." else "no more sources"}")
        }

        false
    }

    private fun tryDownload(
        downloadUrl: String,
        langCode: String,
        onProgress: (Float) -> Unit
    ): Boolean {
        val tempFile = File(getTessdataDir(), "$langCode.traineddata.tmp")
        val destFile = File(getTessdataDir(), "$langCode.traineddata")

        return try {
            val connection = openConnectionFollowingRedirects(downloadUrl)

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Download failed: HTTP $responseCode")
                connection.disconnect()
                return false
            }

            val totalBytes = connection.contentLength
            Log.d(TAG, "Downloading $langCode.traineddata, Content-Length: $totalBytes")

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

            // Validate file size
            if (tempFile.length() <= MIN_TRAINEDDATA_SIZE) {
                Log.e(TAG, "Downloaded file too small (${tempFile.length()} bytes) — likely corrupted or LFS pointer")
                tempFile.delete()
                return false
            }

            // Atomic rename
            destFile.delete()
            tempFile.renameTo(destFile)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            tempFile.delete()
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
                Log.e(TAG, "Tesseract init failed for $langCode — traineddata may be corrupted")
                api.recycle()
                // Delete potentially corrupted file so next attempt re-downloads
                File(getTessdataDir(), "$langCode.traineddata").delete()
            }
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Tesseract init error", e)
            return false
        }
    }

    /**
     * Preprocesses a bitmap for better OCR accuracy:
     * upscales small images, converts to grayscale, and increases contrast.
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
