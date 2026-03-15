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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
        private const val BUFFER_SIZE = 64 * 1024
        private const val MAX_RETRIES = 4
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val PROGRESS_UPDATE_INTERVAL_MS = 500L

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

    private suspend fun performDownload(
        url: String,
        partialFile: File,
        resumeOffset: Long,
        modelId: String,
        modelName: String,
        expectedSizeMb: Long
    ): Boolean {
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

            val totalBytes: Long
            val startOffset: Long

            when (responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> {
                    startOffset = resumeOffset
                    val contentLength = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: 0L
                    totalBytes = startOffset + contentLength
                }
                HttpURLConnection.HTTP_OK -> {
                    startOffset = 0L
                    totalBytes = connection.getHeaderField("Content-Length")?.toLongOrNull()
                        ?: (expectedSizeMb * 1024 * 1024)
                    if (resumeOffset > 0) {
                        partialFile.delete()
                    }
                }
                else -> {
                    throw java.io.IOException("HTTP $responseCode: ${connection.responseMessage}")
                }
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
                    if (isStopped) {
                        raf.close()
                        return false
                    }

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

                        val progress = if (totalBytes > 0) {
                            ((bytesWritten * 100) / totalBytes).toInt()
                        } else 0

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

                raf.close()
            } finally {
                try { raf.close() } catch (_: Exception) {}
                try { inputStream.close() } catch (_: Exception) {}
            }

            return !isStopped

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
