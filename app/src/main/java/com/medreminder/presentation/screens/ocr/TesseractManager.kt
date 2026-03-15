package com.medreminder.presentation.screens.ocr

import android.content.Context
import android.graphics.Bitmap
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
 * Downloads traineddata files on first use from the tessdata_fast repository,
 * then caches them in the app's internal storage.
 */
class TesseractManager(private val context: Context) {

    companion object {
        private const val TAG = "TesseractManager"
        private const val TESSDATA_DIR = "tessdata"
        private const val BASE_URL =
            "https://github.com/tesseract-ocr/tessdata_fast/raw/main/"

        /** Supported Tesseract language codes */
        val SUPPORTED_LANGUAGES = setOf("rus", "hye", "fas")
    }

    private var tessApi: TessBaseAPI? = null

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
            connection.readTimeout = 30_000
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
        try {
            release()
            val api = TessBaseAPI()
            val success = api.init(getDataPath(), langCode)
            if (success) {
                tessApi = api
                Log.d(TAG, "Tesseract initialized for $langCode")
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
     * Recognizes text from a bitmap using the initialized Tesseract engine.
     *
     * @return recognized text, or empty string on failure
     */
    suspend fun recognizeText(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        try {
            val api = tessApi ?: return@withContext ""
            api.setImage(bitmap)
            val text = api.utF8Text ?: ""
            api.clear()
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
    }
}
