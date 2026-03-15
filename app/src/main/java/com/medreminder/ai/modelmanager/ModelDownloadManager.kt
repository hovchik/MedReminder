package com.medreminder.ai.modelmanager

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.medreminder.ai.local.InstallState
import com.medreminder.ai.local.LocalAiModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadState(
    val modelId: String = "",
    val status: DownloadStatus = DownloadStatus.IDLE,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val speedBytesPerSec: Long = 0,
    val etaSeconds: Long = 0,
    val message: String = "",
    val retryCount: Int = 0
) {
    val progressFraction: Float
        get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f

    val progressPercent: Int
        get() = (progressFraction * 100).toInt()

    val downloadedMb: Long
        get() = downloadedBytes / (1024 * 1024)

    val totalMb: Long
        get() = totalBytes / (1024 * 1024)

    val speedMbPerSec: Float
        get() = speedBytesPerSec / (1024f * 1024f)
}

enum class DownloadStatus {
    IDLE,
    CONNECTING,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: LocalModelManager
) {
    companion object {
        private const val BUFFER_SIZE = 64 * 1024           // 64 KB read buffer
        private const val PROGRESS_UPDATE_INTERVAL_MS = 250L
        private const val MAX_RETRIES = 4
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 60_000
    }

    private val _downloadState = MutableStateFlow(DownloadState())
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private var downloadJob: Job? = null
    @Volatile private var isPaused = false
    @Volatile private var isCancelled = false

    private fun getModelsDir(): File {
        val dir = File(context.filesDir, "ai_models")
        dir.mkdirs()
        return dir
    }

    private fun getPartialFile(model: LocalAiModel): File {
        val modelDir = File(getModelsDir(), model.modelId)
        modelDir.mkdirs()
        return File(modelDir, "${model.modelId}.${model.fileFormat}.part")
    }

    private fun getFinalFile(model: LocalAiModel): File {
        val modelDir = File(getModelsDir(), model.modelId)
        modelDir.mkdirs()
        return File(modelDir, "${model.modelId}.${model.fileFormat}")
    }

    /**
     * Start downloading the model. Supports resume from partial downloads.
     * Uses HTTP Range headers for resumable downloads.
     */
    fun startDownload(model: LocalAiModel, scope: CoroutineScope) {
        if (downloadJob?.isActive == true) {
            return // Download already in progress
        }

        isPaused = false
        isCancelled = false

        downloadJob = scope.launch(Dispatchers.IO) {
            executeDownload(model)
        }
    }

    fun pauseDownload() {
        isPaused = true
        _downloadState.update {
            it.copy(
                status = DownloadStatus.PAUSED,
                message = "Download paused"
            )
        }
    }

    fun resumeDownload(model: LocalAiModel, scope: CoroutineScope) {
        if (downloadJob?.isActive == true && !isPaused) return

        isPaused = false
        isCancelled = false

        downloadJob = scope.launch(Dispatchers.IO) {
            executeDownload(model)
        }
    }

    fun cancelDownload(model: LocalAiModel) {
        isCancelled = true
        downloadJob?.cancel()

        // Clean up partial file
        getPartialFile(model).delete()

        _downloadState.update {
            DownloadState(
                modelId = model.modelId,
                status = DownloadStatus.CANCELLED,
                message = "Download cancelled"
            )
        }
    }

    fun isWifiConnected(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (_: Exception) {
            false
        }
    }

    fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun executeDownload(model: LocalAiModel) {
        val partialFile = getPartialFile(model)
        val finalFile = getFinalFile(model)

        // If already fully downloaded, skip to verification
        // Use a tolerance check instead of exact match
        val expectedBytes = model.sizeMb * 1024L * 1024L
        if (finalFile.exists() && expectedBytes > 0 && finalFile.length() >= expectedBytes * 0.8) {
            _downloadState.update {
                DownloadState(
                    modelId = model.modelId,
                    status = DownloadStatus.VERIFYING,
                    totalBytes = finalFile.length(),
                    downloadedBytes = finalFile.length(),
                    message = "Verifying existing file..."
                )
            }
            finishDownload(model, finalFile)
            return
        }

        // Determine resume offset from partial file
        var resumeOffset = if (partialFile.exists()) partialFile.length() else 0L

        modelManager.registerModel(model.copy(installState = InstallState.DOWNLOADING))

        var retryCount = 0
        var lastError: Exception? = null

        while (retryCount <= MAX_RETRIES && !isCancelled) {
            try {
                _downloadState.update {
                    DownloadState(
                        modelId = model.modelId,
                        status = DownloadStatus.CONNECTING,
                        downloadedBytes = resumeOffset,
                        retryCount = retryCount,
                        message = if (retryCount > 0) "Retrying (attempt ${retryCount + 1})..." else "Connecting..."
                    )
                }

                val success = downloadWithResume(model, partialFile, resumeOffset)

                if (success && !isCancelled) {
                    // Rename partial -> final
                    if (!partialFile.renameTo(finalFile)) {
                        // Fallback: copy and delete if rename fails (cross-filesystem)
                        partialFile.copyTo(finalFile, overwrite = true)
                        partialFile.delete()
                    }
                    finishDownload(model, finalFile)
                    return
                } else if (isPaused) {
                    // Save progress to DB for later resume
                    modelManager.registerModel(
                        model.copy(
                            installState = InstallState.PAUSED,
                            downloadedBytes = if (partialFile.exists()) partialFile.length() else 0L
                        )
                    )
                    return
                }

            } catch (e: IOException) {
                lastError = e
                retryCount++
                resumeOffset = if (partialFile.exists()) partialFile.length() else 0L

                if (retryCount <= MAX_RETRIES && !isCancelled) {
                    val delayMs = getRetryDelayMs(retryCount)
                    _downloadState.update {
                        it.copy(
                            status = DownloadStatus.CONNECTING,
                            retryCount = retryCount,
                            message = "Network error: ${e.message}. Retrying in ${delayMs / 1000}s..."
                        )
                    }
                    delay(delayMs)
                }
            } catch (e: CancellationException) {
                throw e // Let coroutine cancellation propagate
            } catch (e: Exception) {
                lastError = e
                break
            }
        }

        // All retries exhausted
        modelManager.updateInstallState(model.modelId, InstallState.FAILED)
        _downloadState.update {
            DownloadState(
                modelId = model.modelId,
                status = DownloadStatus.FAILED,
                downloadedBytes = if (partialFile.exists()) partialFile.length() else 0L,
                totalBytes = expectedBytes,
                retryCount = retryCount,
                message = "Download failed: ${lastError?.message ?: "Unknown error"}"
            )
        }
    }

    /**
     * Performs the actual HTTP download with Range header support.
     * Writes directly to disk using RandomAccessFile for efficient resume.
     * Returns true if download completed successfully.
     */
    private suspend fun downloadWithResume(
        model: LocalAiModel,
        partialFile: File,
        resumeOffset: Long
    ): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(model.downloadUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "MedReminder/1.0")
                setRequestProperty("Accept", "*/*")

                // Request resume from offset
                if (resumeOffset > 0) {
                    setRequestProperty("Range", "bytes=$resumeOffset-")
                }
            }

            val responseCode = connection.responseCode

            // Handle redirects manually for cases where HttpURLConnection doesn't follow
            if (responseCode in 301..308) {
                val redirectUrl = connection.getHeaderField("Location")
                connection.disconnect()
                if (redirectUrl != null) {
                    connection = (URL(redirectUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = CONNECT_TIMEOUT_MS
                        readTimeout = READ_TIMEOUT_MS
                        requestMethod = "GET"
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "MedReminder/1.0")
                        setRequestProperty("Accept", "*/*")
                        if (resumeOffset > 0) {
                            setRequestProperty("Range", "bytes=$resumeOffset-")
                        }
                    }
                } else {
                    throw IOException("Redirect without Location header")
                }
            }

            val finalResponseCode = connection.responseCode

            // Determine total size and whether server supports resume
            val totalBytes: Long
            val startOffset: Long

            when (finalResponseCode) {
                HttpURLConnection.HTTP_PARTIAL -> {
                    // Server supports Range, sending remaining bytes
                    startOffset = resumeOffset

                    // Prefer Content-Range for total size: "bytes 1000-9999/10000"
                    val contentRange = connection.getHeaderField("Content-Range")
                    val fullSize = contentRange?.substringAfter("/", "")?.toLongOrNull()
                    if (fullSize != null && fullSize > 0) {
                        totalBytes = fullSize
                    } else {
                        val contentLength = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: 0L
                        totalBytes = startOffset + contentLength
                    }
                }
                HttpURLConnection.HTTP_OK -> {
                    // Server returned full file (no Range support or fresh download)
                    startOffset = 0L
                    totalBytes = connection.getHeaderField("Content-Length")?.toLongOrNull()
                        ?: (model.sizeMb * 1024L * 1024L)

                    // If we had a partial file but server doesn't support resume, start over
                    if (resumeOffset > 0) {
                        partialFile.delete()
                    }
                }
                else -> {
                    val errorBody = try {
                        connection.errorStream?.bufferedReader()?.readText()?.take(200) ?: ""
                    } catch (_: Exception) { "" }
                    throw IOException("HTTP $finalResponseCode: ${connection.responseMessage}. $errorBody".trim())
                }
            }

            _downloadState.update {
                DownloadState(
                    modelId = model.modelId,
                    status = DownloadStatus.DOWNLOADING,
                    totalBytes = totalBytes,
                    downloadedBytes = startOffset,
                    message = "Downloading ${model.displayName}..."
                )
            }

            // Stream data to disk
            val inputStream = connection.inputStream
                ?: throw IOException("Server returned no data")
            val raf = RandomAccessFile(partialFile, "rw")

            try {
                raf.seek(startOffset)

                val buffer = ByteArray(BUFFER_SIZE)
                var bytesWritten = startOffset
                var bytesRead: Int
                var lastProgressUpdate = System.currentTimeMillis()
                var lastSpeedCalcTime = System.currentTimeMillis()
                var lastSpeedCalcBytes = bytesWritten
                var currentSpeed = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (isCancelled) {
                        return@withContext false
                    }

                    if (isPaused) {
                        return@withContext false
                    }

                    raf.write(buffer, 0, bytesRead)
                    bytesWritten += bytesRead

                    // Throttled progress updates
                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate >= PROGRESS_UPDATE_INTERVAL_MS) {
                        // Calculate speed
                        val timeDeltaMs = now - lastSpeedCalcTime
                        if (timeDeltaMs > 0) {
                            val bytesDelta = bytesWritten - lastSpeedCalcBytes
                            currentSpeed = (bytesDelta * 1000) / timeDeltaMs
                            lastSpeedCalcTime = now
                            lastSpeedCalcBytes = bytesWritten
                        }

                        // Calculate ETA
                        val remaining = totalBytes - bytesWritten
                        val eta = if (currentSpeed > 0) remaining / currentSpeed else 0L

                        _downloadState.update {
                            it.copy(
                                downloadedBytes = bytesWritten,
                                speedBytesPerSec = currentSpeed,
                                etaSeconds = eta
                            )
                        }
                        lastProgressUpdate = now
                    }
                }

                // Final progress update
                _downloadState.update {
                    it.copy(
                        downloadedBytes = bytesWritten,
                        speedBytesPerSec = 0,
                        etaSeconds = 0
                    )
                }
            } finally {
                try { raf.close() } catch (_: Exception) {}
                try { inputStream.close() } catch (_: Exception) {}
            }

            return@withContext !isCancelled && !isPaused
        } finally {
            connection?.disconnect()
        }
    }

    private suspend fun finishDownload(model: LocalAiModel, file: File) {
        _downloadState.update {
            it.copy(
                status = DownloadStatus.VERIFYING,
                message = "Verifying download..."
            )
        }

        // Verify checksum if provided
        if (model.checksum.isNotBlank()) {
            val fileChecksum = computeSha256(file)
            if (!fileChecksum.equals(model.checksum, ignoreCase = true)) {
                file.delete()
                modelManager.updateInstallState(model.modelId, InstallState.FAILED)
                _downloadState.update {
                    DownloadState(
                        modelId = model.modelId,
                        status = DownloadStatus.FAILED,
                        message = "Checksum verification failed. File may be corrupted."
                    )
                }
                return
            }
        }

        // Verify file size is reasonable (only if we have an expected size)
        if (model.sizeMb > 0) {
            val fileSizeMb = file.length() / (1024L * 1024L)
            if (fileSizeMb < 1) {
                // File is essentially empty
                file.delete()
                modelManager.updateInstallState(model.modelId, InstallState.FAILED)
                _downloadState.update {
                    DownloadState(
                        modelId = model.modelId,
                        status = DownloadStatus.FAILED,
                        message = "Downloaded file is empty or too small (${file.length()} bytes)"
                    )
                }
                return
            }
        }

        // Mark installed
        modelManager.markInstalled(model.modelId, file.absolutePath)

        _downloadState.update {
            DownloadState(
                modelId = model.modelId,
                status = DownloadStatus.COMPLETED,
                totalBytes = file.length(),
                downloadedBytes = file.length(),
                message = "${model.displayName} downloaded successfully"
            )
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(BUFFER_SIZE).use { stream ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun getRetryDelayMs(retryCount: Int): Long {
        // Exponential backoff: 2s, 4s, 8s, 16s
        return (1L shl retryCount) * 1000L
    }

    /** Clean up partial downloads for a given model. */
    suspend fun cleanupPartialDownload(model: LocalAiModel) {
        getPartialFile(model).delete()
        getFinalFile(model).delete()
        val modelDir = File(getModelsDir(), model.modelId)
        if (modelDir.exists() && modelDir.listFiles()?.isEmpty() == true) {
            modelDir.delete()
        }
    }

    /** Check for existing partial download and return bytes already downloaded. */
    fun getExistingDownloadBytes(model: LocalAiModel): Long {
        val partialFile = getPartialFile(model)
        return if (partialFile.exists()) partialFile.length() else 0L
    }

    fun resetState() {
        _downloadState.update { DownloadState() }
    }
}
