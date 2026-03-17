package com.medreminder.ai.modelmanager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.medreminder.R
import com.medreminder.ai.local.InstallState
import com.medreminder.ai.local.LocalAiModel
import com.medreminder.ai.local.RuntimeType
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * WorkManager-based background download worker for AI models.
 * Handles large file downloads that should continue when the app is backgrounded.
 * Supports resumable downloads via HTTP Range headers.
 */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val modelManager: LocalModelManager
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_MODEL_NAME = "model_name"
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_FILE_FORMAT = "file_format"
        const val KEY_EXPECTED_SIZE_MB = "expected_size_mb"
        const val KEY_CHECKSUM = "checksum"
        const val KEY_RUNTIME_TYPE = "runtime_type"

        const val KEY_PROGRESS = "download_progress"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_SPEED = "download_speed"

        private const val NOTIFICATION_CHANNEL_ID = "model_download"
        private const val NOTIFICATION_ID = 9001
        private const val BUFFER_SIZE = 256 * 1024
        private const val MAX_RETRIES = 4
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val PROGRESS_UPDATE_INTERVAL_MS = 500L
        private const val CHUNK_CONNECTIONS_WIFI = 4
        private const val CHUNK_CONNECTIONS_MOBILE = 2
        private const val MIN_CHUNK_SIZE = 2L * 1024 * 1024

        fun buildWorkRequest(model: LocalAiModel): OneTimeWorkRequest {
            val inputData = Data.Builder()
                .putString(KEY_MODEL_ID, model.modelId)
                .putString(KEY_MODEL_NAME, model.displayName)
                .putString(KEY_DOWNLOAD_URL, model.downloadUrl)
                .putString(KEY_FILE_FORMAT, model.fileFormat)
                .putLong(KEY_EXPECTED_SIZE_MB, model.sizeMb)
                .putString(KEY_CHECKSUM, model.checksum)
                .putString(KEY_RUNTIME_TYPE, model.runtimeType.name)
                .build()

            return OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setInputData(inputData)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .addTag("model_download_${model.modelId}")
                .build()
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return@withContext Result.failure()
        val modelName = inputData.getString(KEY_MODEL_NAME) ?: "AI Model"
        val downloadUrl = inputData.getString(KEY_DOWNLOAD_URL) ?: return@withContext Result.failure()
        val fileFormat = inputData.getString(KEY_FILE_FORMAT) ?: "bin"
        val expectedSizeMb = inputData.getLong(KEY_EXPECTED_SIZE_MB, 0)
        val checksum = inputData.getString(KEY_CHECKSUM) ?: ""

        createNotificationChannel()

        val modelsDir = File(applicationContext.filesDir, "ai_models")
        val modelDir = File(modelsDir, modelId)
        modelDir.mkdirs()

        val partialFile = File(modelDir, "$modelId.$fileFormat.part")
        val finalFile = File(modelDir, "$modelId.$fileFormat")

        // If already complete, verify and finish
        if (finalFile.exists() && expectedSizeMb > 0 &&
            finalFile.length() >= expectedSizeMb * 1024 * 1024 * 0.8
        ) {
            return@withContext finishAndVerify(modelId, modelName, finalFile, checksum, expectedSizeMb)
        }

        var retryCount = 0
        while (retryCount <= MAX_RETRIES) {
            try {
                val resumeOffset = if (partialFile.exists()) partialFile.length() else 0L

                showProgressNotification(modelName, 0, 0)

                modelManager.updateInstallState(modelId, InstallState.DOWNLOADING)

                val success = performDownload(
                    url = downloadUrl,
                    partialFile = partialFile,
                    resumeOffset = resumeOffset,
                    modelId = modelId,
                    modelName = modelName,
                    expectedSizeMb = expectedSizeMb
                )

                if (success) {
                    partialFile.renameTo(finalFile)
                    return@withContext finishAndVerify(modelId, modelName, finalFile, checksum, expectedSizeMb)
                } else {
                    // Paused or cancelled by WorkManager
                    return@withContext Result.retry()
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                retryCount++
                if (retryCount > MAX_RETRIES) {
                    modelManager.updateInstallState(modelId, InstallState.FAILED)
                    showFailureNotification(modelName, e.message ?: "Download failed")
                    return@withContext Result.failure(
                        Data.Builder().putString("error", e.message).build()
                    )
                }
                // Exponential backoff
                val delayMs = (1L shl retryCount) * 1000L
                kotlinx.coroutines.delay(delayMs)
            }
        }

        Result.failure()
    }

    private fun isWifiConnected(): Boolean {
        return try {
            val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Probes the server via HEAD to resolve redirects and check Range support.
     */
    private fun probeDownload(downloadUrl: String, expectedSizeMb: Long): Triple<String, Long, Boolean> {
        var url = downloadUrl
        var supportsRange = false
        var totalBytes = expectedSizeMb * 1024 * 1024

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "HEAD"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "MedReminder/1.0")
                setRequestProperty("Range", "bytes=0-0")
            }
            val code = connection.responseCode
            val finalUrl = connection.url?.toString() ?: url
            url = finalUrl

            when (code) {
                HttpURLConnection.HTTP_PARTIAL, 206 -> {
                    supportsRange = true
                    val contentRange = connection.getHeaderField("Content-Range")
                    val fullSize = contentRange?.substringAfter("/", "")?.toLongOrNull()
                    if (fullSize != null && fullSize > 0) totalBytes = fullSize
                }
                HttpURLConnection.HTTP_OK -> {
                    val acceptRanges = connection.getHeaderField("Accept-Ranges")
                    supportsRange = acceptRanges?.contains("bytes", ignoreCase = true) == true
                    val cl = connection.getHeaderField("Content-Length")?.toLongOrNull()
                    if (cl != null && cl > 0) totalBytes = cl
                }
            }
        } catch (_: Exception) {
            // Fall back to single-stream
        } finally {
            connection?.disconnect()
        }

        return Triple(url, totalBytes, supportsRange)
    }

    private fun openRangeConnection(url: String, rangeStart: Long, rangeEnd: Long): HttpURLConnection {
        var connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MedReminder/1.0")
            setRequestProperty("Range", "bytes=$rangeStart-$rangeEnd")
        }
        val code = connection.responseCode
        if (code in 301..308) {
            val loc = connection.getHeaderField("Location")
            connection.disconnect()
            if (loc != null) {
                connection = (URL(loc).openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "MedReminder/1.0")
                    setRequestProperty("Range", "bytes=$rangeStart-$rangeEnd")
                }
            }
        }
        return connection
    }

    /**
     * Downloads a single chunk of the file.
     */
    private fun downloadChunk(
        url: String,
        chunkFile: File,
        rangeStart: Long,
        rangeEnd: Long,
        chunkBytesWritten: LongArray,
        chunkIndex: Int
    ): Boolean {
        val existingBytes = if (chunkFile.exists()) chunkFile.length() else 0L
        val actualStart = rangeStart + existingBytes
        if (actualStart > rangeEnd) {
            chunkBytesWritten[chunkIndex] = rangeEnd - rangeStart + 1
            return true
        }

        var connection: HttpURLConnection? = null
        try {
            connection = openRangeConnection(url, actualStart, rangeEnd)
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_PARTIAL && code != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP $code for chunk $chunkIndex")
            }

            val inputStream = connection.inputStream ?: throw IOException("No data for chunk $chunkIndex")
            val raf = RandomAccessFile(chunkFile, "rw")
            raf.seek(existingBytes)

            try {
                val buffer = ByteArray(BUFFER_SIZE)
                var written = existingBytes
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (isStopped) return false
                    raf.write(buffer, 0, bytesRead)
                    written += bytesRead
                    chunkBytesWritten[chunkIndex] = written
                }
            } finally {
                try { raf.close() } catch (_: Exception) {}
                try { inputStream.close() } catch (_: Exception) {}
            }
            return true
        } finally {
            connection?.disconnect()
        }
    }

    private suspend fun performDownload(
        url: String,
        partialFile: File,
        resumeOffset: Long,
        modelId: String,
        modelName: String,
        expectedSizeMb: Long
    ): Boolean = withContext(Dispatchers.IO) {
        // Probe for Range support and total size
        val (resolvedUrl, totalBytes, supportsRange) = probeDownload(url, expectedSizeMb)

        if (totalBytes <= 0) {
            throw IOException("Cannot determine file size")
        }

        val remainingBytes = totalBytes - resumeOffset
        val maxConnections = if (isWifiConnected()) CHUNK_CONNECTIONS_WIFI else CHUNK_CONNECTIONS_MOBILE
        val useChunked = supportsRange && remainingBytes > MIN_CHUNK_SIZE * 2 && maxConnections > 1

        if (!useChunked) {
            // Single-stream fallback
            return@withContext singleStreamDownload(
                resolvedUrl, partialFile, resumeOffset, totalBytes, modelId, modelName
            )
        }

        // Split into chunks
        val chunkCount = maxConnections.coerceAtMost(
            (remainingBytes / MIN_CHUNK_SIZE).toInt().coerceAtLeast(1)
        )
        val chunkSize = remainingBytes / chunkCount

        data class Chunk(val index: Int, val start: Long, val end: Long)
        val chunks = (0 until chunkCount).map { i ->
            val start = resumeOffset + i * chunkSize
            val end = if (i == chunkCount - 1) totalBytes - 1 else start + chunkSize - 1
            Chunk(i, start, end)
        }
        val chunkFiles = chunks.map { c ->
            File(partialFile.parent, "${partialFile.name}.chunk${c.index}")
        }
        val chunkBytesWritten = LongArray(chunkCount) { i ->
            if (chunkFiles[i].exists()) chunkFiles[i].length() else 0L
        }

        // Progress reporting coroutine
        val progressJob = launch {
            var lastSpeedTime = System.currentTimeMillis()
            var lastSpeedBytes = chunkBytesWritten.sum() + resumeOffset
            while (isActive) {
                delay(PROGRESS_UPDATE_INTERVAL_MS)
                val totalWritten = chunkBytesWritten.sum() + resumeOffset
                val now = System.currentTimeMillis()
                val timeDelta = now - lastSpeedTime
                val speed = if (timeDelta > 0) ((totalWritten - lastSpeedBytes) * 1000) / timeDelta else 0L
                lastSpeedTime = now
                lastSpeedBytes = totalWritten
                val progress = if (totalBytes > 0) ((totalWritten * 100) / totalBytes).toInt() else 0

                setProgressAsync(
                    Data.Builder()
                        .putInt(KEY_PROGRESS, progress)
                        .putLong(KEY_DOWNLOADED_BYTES, totalWritten)
                        .putLong(KEY_TOTAL_BYTES, totalBytes)
                        .putLong(KEY_SPEED, speed)
                        .build()
                )
                showProgressNotification(modelName, progress, speed)
            }
        }

        var allSuccess = true
        try {
            val deferreds = chunks.map { chunk ->
                async {
                    downloadChunk(
                        resolvedUrl, chunkFiles[chunk.index],
                        chunk.start, chunk.end,
                        chunkBytesWritten, chunk.index
                    )
                }
            }
            for (d in deferreds) {
                if (!d.await()) {
                    allSuccess = false
                    break
                }
            }
        } finally {
            progressJob.cancel()
        }

        if (!allSuccess || isStopped) return@withContext false

        // Merge chunks
        RandomAccessFile(partialFile, "rw").use { raf ->
            for (i in chunks.indices) {
                raf.seek(chunks[i].start)
                chunkFiles[i].inputStream().buffered(BUFFER_SIZE).use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        raf.write(buffer, 0, bytesRead)
                    }
                }
                chunkFiles[i].delete()
            }
        }

        return@withContext true
    }

    /**
     * Single-stream download fallback when server doesn't support Range or file is small.
     */
    private suspend fun singleStreamDownload(
        url: String,
        partialFile: File,
        resumeOffset: Long,
        totalBytes: Long,
        modelId: String,
        modelName: String
    ): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", "MedReminder/1.0")
                if (resumeOffset > 0) {
                    setRequestProperty("Range", "bytes=$resumeOffset-")
                }
            }

            val responseCode = connection.responseCode
            val startOffset: Long = when (responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> resumeOffset
                HttpURLConnection.HTTP_OK -> {
                    if (resumeOffset > 0) partialFile.delete()
                    0L
                }
                else -> throw IOException("HTTP $responseCode: ${connection.responseMessage}")
            }

            val inputStream = connection.inputStream
            val raf = RandomAccessFile(partialFile, "rw")
            raf.seek(startOffset)

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesWritten = startOffset
            var bytesRead: Int
            var lastUpdate = System.currentTimeMillis()
            var lastSpeedTime = System.currentTimeMillis()
            var lastSpeedBytes = bytesWritten

            try {
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (isStopped) return@withContext false
                    raf.write(buffer, 0, bytesRead)
                    bytesWritten += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastUpdate >= PROGRESS_UPDATE_INTERVAL_MS) {
                        val timeDelta = now - lastSpeedTime
                        val speed = if (timeDelta > 0) {
                            ((bytesWritten - lastSpeedBytes) * 1000) / timeDelta
                        } else 0L
                        lastSpeedTime = now
                        lastSpeedBytes = bytesWritten
                        val progress = if (totalBytes > 0) ((bytesWritten * 100) / totalBytes).toInt() else 0

                        setProgressAsync(
                            Data.Builder()
                                .putInt(KEY_PROGRESS, progress)
                                .putLong(KEY_DOWNLOADED_BYTES, bytesWritten)
                                .putLong(KEY_TOTAL_BYTES, totalBytes)
                                .putLong(KEY_SPEED, speed)
                                .build()
                        )
                        showProgressNotification(modelName, progress, speed)
                        lastUpdate = now
                    }
                }
            } finally {
                try { raf.close() } catch (_: Exception) {}
                try { inputStream.close() } catch (_: Exception) {}
            }
            return@withContext !isStopped
        } finally {
            connection?.disconnect()
        }
    }

    private suspend fun finishAndVerify(
        modelId: String,
        modelName: String,
        file: File,
        checksum: String,
        expectedSizeMb: Long
    ): Result {
        // Verify checksum
        if (checksum.isNotBlank()) {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered(BUFFER_SIZE).use { stream ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            val computed = digest.digest().joinToString("") { "%02x".format(it) }
            if (!computed.equals(checksum, ignoreCase = true)) {
                file.delete()
                modelManager.updateInstallState(modelId, InstallState.FAILED)
                showFailureNotification(modelName, "Checksum verification failed")
                return Result.failure()
            }
        }

        // Verify size
        val fileSizeMb = file.length() / (1024 * 1024)
        if (expectedSizeMb > 0 && fileSizeMb < expectedSizeMb * 0.8) {
            file.delete()
            modelManager.updateInstallState(modelId, InstallState.FAILED)
            showFailureNotification(modelName, "Download incomplete")
            return Result.failure()
        }

        modelManager.markInstalled(modelId, file.absolutePath)
        showCompletionNotification(modelName)

        return Result.success(
            Data.Builder()
                .putString(KEY_MODEL_ID, modelId)
                .putLong(KEY_DOWNLOADED_BYTES, file.length())
                .build()
        )
    }

    // ── Notifications ───────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Model Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "AI model download progress"
                setShowBadge(false)
            }
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun showProgressNotification(modelName: String, progress: Int, speed: Long) {
        val speedText = if (speed > 0) {
            val mbPerSec = speed / (1024f * 1024f)
            String.format(" • %.1f MB/s", mbPerSec)
        } else ""

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading $modelName")
            .setContentText("$progress%$speedText")
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification(modelName: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("$modelName ready")
            .setContentText("AI model downloaded and verified")
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun showFailureNotification(modelName: String, reason: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("$modelName download failed")
            .setContentText(reason)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFICATION_ID + 2, notification)
    }
}
