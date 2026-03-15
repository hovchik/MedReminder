package com.medreminder.ai.modelmanager

import android.content.Context
import android.net.Uri
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.medreminder.ai.local.InstallState
import com.medreminder.ai.local.LocalAiModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class InstallProgress(
    val modelId: String = "",
    val state: InstallState = InstallState.NOT_INSTALLED,
    val progress: Float = 0f,
    val message: String = "",
    val downloadedMb: Long = 0,
    val totalMb: Long = 0,
    val speedMbPerSec: Float = 0f,
    val etaSeconds: Long = 0
)

@Singleton
class ModelInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: LocalModelManager,
    private val validator: ModelCompatibilityValidator,
    private val downloadManager: ModelDownloadManager
) {
    private val _installProgress = MutableStateFlow(InstallProgress())
    val installProgress: StateFlow<InstallProgress> = _installProgress

    private fun getModelsDir(): File {
        val dir = File(context.filesDir, "ai_models")
        dir.mkdirs()
        return dir
    }

    /**
     * Start downloading a model using the foreground ModelDownloadManager.
     * Supports pause/resume, retry with backoff, and checksum verification.
     */
    fun downloadModel(model: LocalAiModel, scope: CoroutineScope) {
        // Validate compatibility before downloading
        val validation = validator.validate(model)
        if (!validation.isCompatible) {
            _installProgress.value = InstallProgress(
                modelId = model.modelId,
                state = InstallState.FAILED,
                message = "Incompatible: ${validation.reasons.joinToString(", ")}"
            )
            return
        }

        // Bridge ModelDownloadManager state to InstallProgress
        downloadManager.downloadState
            .onEach { ds ->
                _installProgress.value = InstallProgress(
                    modelId = ds.modelId,
                    state = when (ds.status) {
                        DownloadStatus.IDLE -> InstallState.NOT_INSTALLED
                        DownloadStatus.CONNECTING -> InstallState.DOWNLOADING
                        DownloadStatus.DOWNLOADING -> InstallState.DOWNLOADING
                        DownloadStatus.PAUSED -> InstallState.PAUSED
                        DownloadStatus.VERIFYING -> InstallState.VALIDATING
                        DownloadStatus.COMPLETED -> InstallState.INSTALLED
                        DownloadStatus.FAILED -> InstallState.FAILED
                        DownloadStatus.CANCELLED -> InstallState.NOT_INSTALLED
                    },
                    progress = ds.progressFraction,
                    message = ds.message,
                    downloadedMb = ds.downloadedMb,
                    totalMb = ds.totalMb,
                    speedMbPerSec = ds.speedMbPerSec,
                    etaSeconds = ds.etaSeconds
                )
            }
            .launchIn(scope)

        downloadManager.startDownload(model, scope)
    }

    /**
     * Schedule a background download using WorkManager.
     * Continues even when the app is in the background, shows notifications.
     */
    fun downloadModelInBackground(model: LocalAiModel) {
        val validation = validator.validate(model)
        if (!validation.isCompatible) {
            _installProgress.value = InstallProgress(
                modelId = model.modelId,
                state = InstallState.FAILED,
                message = "Incompatible: ${validation.reasons.joinToString(", ")}"
            )
            return
        }

        val workRequest = ModelDownloadWorker.buildWorkRequest(model)
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "model_download_${model.modelId}",
                androidx.work.ExistingWorkPolicy.KEEP,
                workRequest
            )

        _installProgress.value = InstallProgress(
            modelId = model.modelId,
            state = InstallState.DOWNLOADING,
            message = "Download started in background..."
        )
    }

    /** Observe background download progress from WorkManager. */
    fun observeBackgroundDownload(modelId: String): Flow<InstallProgress> {
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow("model_download_$modelId")
            .map { workInfos ->
                val info = workInfos.firstOrNull() ?: return@map InstallProgress()
                when (info.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = info.progress.getInt(ModelDownloadWorker.KEY_PROGRESS, 0)
                        val downloaded = info.progress.getLong(ModelDownloadWorker.KEY_DOWNLOADED_BYTES, 0)
                        val total = info.progress.getLong(ModelDownloadWorker.KEY_TOTAL_BYTES, 0)
                        val speed = info.progress.getLong(ModelDownloadWorker.KEY_SPEED, 0)
                        InstallProgress(
                            modelId = modelId,
                            state = InstallState.DOWNLOADING,
                            progress = progress / 100f,
                            downloadedMb = downloaded / (1024 * 1024),
                            totalMb = total / (1024 * 1024),
                            speedMbPerSec = speed / (1024f * 1024f),
                            message = "Downloading... $progress%"
                        )
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        InstallProgress(
                            modelId = modelId,
                            state = InstallState.INSTALLED,
                            progress = 1f,
                            message = "Download complete"
                        )
                    }
                    WorkInfo.State.FAILED -> {
                        val error = info.outputData.getString("error") ?: "Download failed"
                        InstallProgress(
                            modelId = modelId,
                            state = InstallState.FAILED,
                            message = error
                        )
                    }
                    WorkInfo.State.ENQUEUED -> {
                        InstallProgress(
                            modelId = modelId,
                            state = InstallState.DOWNLOADING,
                            message = "Waiting for network..."
                        )
                    }
                    else -> InstallProgress(modelId = modelId)
                }
            }
    }

    fun pauseDownload() {
        downloadManager.pauseDownload()
    }

    fun resumeDownload(model: LocalAiModel, scope: CoroutineScope) {
        downloadManager.resumeDownload(model, scope)
    }

    fun cancelDownload(model: LocalAiModel) {
        downloadManager.cancelDownload(model)
        // Also cancel any background work
        WorkManager.getInstance(context)
            .cancelUniqueWork("model_download_${model.modelId}")
    }

    suspend fun importModelFromUri(uri: Uri, model: LocalAiModel): Boolean {
        _installProgress.value = InstallProgress(
            modelId = model.modelId,
            state = InstallState.INSTALLING,
            progress = 0f,
            message = "Importing ${model.displayName}..."
        )

        try {
            val modelDir = File(getModelsDir(), model.modelId)
            modelDir.mkdirs()

            val destFile = File(modelDir, "${model.modelId}.${model.fileFormat}")

            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var totalBytes = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        _installProgress.value = _installProgress.value.copy(
                            progress = minOf(0.9f, totalBytes.toFloat() / (model.sizeMb * 1024 * 1024)),
                            downloadedMb = totalBytes / (1024 * 1024)
                        )
                    }
                }
            } ?: throw IllegalStateException("Cannot open input stream")

            _installProgress.value = _installProgress.value.copy(
                state = InstallState.VALIDATING,
                progress = 0.95f,
                message = "Validating model..."
            )

            val importedModel = model.copy(
                localPath = destFile.absolutePath,
                installState = InstallState.INSTALLED,
                sizeMb = destFile.length() / (1024 * 1024)
            )

            modelManager.registerModel(importedModel)

            _installProgress.value = InstallProgress(
                modelId = model.modelId,
                state = InstallState.INSTALLED,
                progress = 1f,
                message = "${model.displayName} imported successfully"
            )

            return true
        } catch (e: Exception) {
            _installProgress.value = InstallProgress(
                modelId = model.modelId,
                state = InstallState.FAILED,
                message = "Import failed: ${e.message}"
            )
            return false
        }
    }

    suspend fun registerExistingModelDirectory(model: LocalAiModel, directoryPath: String): Boolean {
        val dir = File(directoryPath)
        if (!dir.exists() || !dir.isDirectory) {
            _installProgress.value = InstallProgress(
                modelId = model.modelId,
                state = InstallState.FAILED,
                message = "Directory not found: $directoryPath"
            )
            return false
        }

        val modelFiles = dir.listFiles()?.filter {
            it.extension in listOf("tflite", "bin", "gguf", "onnx", "task")
        } ?: emptyList()

        if (modelFiles.isEmpty()) {
            _installProgress.value = InstallProgress(
                modelId = model.modelId,
                state = InstallState.FAILED,
                message = "No compatible model files found in directory"
            )
            return false
        }

        val modelFile = modelFiles.first()
        val registeredModel = model.copy(
            localPath = modelFile.absolutePath,
            installState = InstallState.INSTALLED,
            sizeMb = modelFile.length() / (1024 * 1024)
        )

        modelManager.registerModel(registeredModel)

        _installProgress.value = InstallProgress(
            modelId = model.modelId,
            state = InstallState.INSTALLED,
            progress = 1f,
            message = "${model.displayName} registered successfully"
        )

        return true
    }

    suspend fun uninstallModel(modelId: String): Boolean {
        try {
            val model = modelManager.getModel(modelId) ?: return false
            if (model.localPath.isNotBlank()) {
                val file = File(model.localPath)
                file.delete()
                // Also delete partial file
                File(file.parent, file.name + ".part").delete()
                file.parentFile?.takeIf { it.name == modelId }?.deleteRecursively()
            }
            modelManager.removeModel(modelId)
            return true
        } catch (_: Exception) {
            return false
        }
    }

    fun isWifiAvailable(): Boolean = downloadManager.isWifiConnected()

    fun isNetworkAvailable(): Boolean = downloadManager.isNetworkAvailable()
}
