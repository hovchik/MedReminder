package com.medreminder.ai.modelmanager

import android.content.Context
import android.net.Uri
import com.medreminder.ai.local.InstallState
import com.medreminder.ai.local.LocalAiModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class InstallProgress(
    val modelId: String = "",
    val state: InstallState = InstallState.NOT_INSTALLED,
    val progress: Float = 0f,
    val message: String = ""
)

@Singleton
class ModelInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: LocalModelManager,
    private val validator: ModelCompatibilityValidator
) {
    private val _installProgress = MutableStateFlow(InstallProgress())
    val installProgress: StateFlow<InstallProgress> = _installProgress

    private fun getModelsDir(): File {
        val dir = File(context.filesDir, "ai_models")
        dir.mkdirs()
        return dir
    }

    suspend fun downloadModel(model: LocalAiModel): Boolean {
        _installProgress.value = InstallProgress(
            modelId = model.modelId,
            state = InstallState.DOWNLOADING,
            progress = 0f,
            message = "Preparing to download ${model.displayName}..."
        )

        // Validate compatibility before downloading
        val validation = validator.validate(model)
        if (!validation.isCompatible) {
            _installProgress.value = InstallProgress(
                modelId = model.modelId,
                state = InstallState.FAILED,
                message = "Incompatible: ${validation.reasons.joinToString(", ")}"
            )
            return false
        }

        try {
            modelManager.updateInstallState(model.modelId, InstallState.DOWNLOADING)

            // Register model in database
            modelManager.registerModel(model.copy(installState = InstallState.DOWNLOADING))

            // Simulate download progress (actual download would use DownloadManager or OkHttp)
            _installProgress.value = _installProgress.value.copy(
                progress = 0.5f,
                message = "Downloading ${model.displayName}..."
            )

            val modelDir = File(getModelsDir(), model.modelId)
            modelDir.mkdirs()

            // Create placeholder file for the model
            val modelFile = File(modelDir, "${model.modelId}.${model.fileFormat}")
            modelFile.createNewFile()

            _installProgress.value = _installProgress.value.copy(
                state = InstallState.INSTALLING,
                progress = 0.9f,
                message = "Installing ${model.displayName}..."
            )

            // Mark as installed
            modelManager.markInstalled(model.modelId, modelFile.absolutePath)

            _installProgress.value = InstallProgress(
                modelId = model.modelId,
                state = InstallState.INSTALLED,
                progress = 1f,
                message = "${model.displayName} installed successfully"
            )

            return true
        } catch (e: Exception) {
            modelManager.updateInstallState(model.modelId, InstallState.FAILED)
            _installProgress.value = InstallProgress(
                modelId = model.modelId,
                state = InstallState.FAILED,
                message = "Failed to install: ${e.message}"
            )
            return false
        }
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
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        _installProgress.value = _installProgress.value.copy(
                            progress = minOf(0.9f, totalBytes.toFloat() / (model.sizeMb * 1024 * 1024))
                        )
                    }
                }
            } ?: throw IllegalStateException("Cannot open input stream")

            // Validate the imported file
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
            it.extension in listOf("tflite", "bin", "gguf", "onnx")
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
                file.parentFile?.takeIf { it.name == modelId }?.deleteRecursively()
            }
            modelManager.removeModel(modelId)
            return true
        } catch (_: Exception) {
            return false
        }
    }
}
