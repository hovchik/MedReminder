package com.medreminder.ai.modelmanager

import com.medreminder.ai.local.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalModelManager @Inject constructor(
    private val modelDao: LocalAiModelDao
) {
    fun getAllModelsFlow(): Flow<List<LocalAiModel>> =
        modelDao.getAllModels().map { list -> list.map { it.toDomain() } }

    fun getInstalledModelsFlow(): Flow<List<LocalAiModel>> =
        modelDao.getInstalledModels().map { list -> list.map { it.toDomain() } }

    fun getInstalledModels(): List<LocalAiModel> {
        // Blocking call for synchronous checks - prefer Flow-based access
        return emptyList() // Will be populated from database
    }

    suspend fun getModel(modelId: String): LocalAiModel? =
        modelDao.getModelById(modelId)?.toDomain()

    suspend fun registerModel(model: LocalAiModel) {
        modelDao.insertModel(model.toEntity())
    }

    suspend fun updateModel(model: LocalAiModel) {
        modelDao.updateModel(model.toEntity())
    }

    suspend fun removeModel(modelId: String) {
        val entity = modelDao.getModelById(modelId)
        if (entity != null) {
            modelDao.deleteModel(entity)
        }
    }

    suspend fun updateInstallState(modelId: String, state: InstallState) {
        modelDao.updateInstallState(modelId, state.name.lowercase())
    }

    suspend fun markInstalled(modelId: String, localPath: String) {
        modelDao.setModelInstalled(modelId, localPath)
    }

    suspend fun getInstalledModelCount(): Int = modelDao.getInstalledModelCount()

    suspend fun getTotalStorageUsedMb(): Long = modelDao.getTotalInstalledSizeMb() ?: 0L

    fun getAvailableModelsCatalog(): List<LocalAiModel> {
        // Catalog of models that can be downloaded
        return listOf(
            LocalAiModel(
                modelId = "gemma-2b-it-q4",
                displayName = "Gemma 2B-IT (Q4)",
                runtimeType = RuntimeType.MEDIA_PIPE,
                fileFormat = "tflite",
                quantization = "Q4_0",
                requiredRamMb = 2048,
                recommendedRamMb = 4096,
                sizeMb = 1300,
                localPath = "",
                installState = InstallState.NOT_INSTALLED,
                checksum = "",
                version = "1.0",
                supportsStructuredJson = false,
                supportsStreaming = true,
                supportsTextGeneration = true
            ),
            LocalAiModel(
                modelId = "phi-2-q4",
                displayName = "Phi-2 (Q4)",
                runtimeType = RuntimeType.LITE_RT,
                fileFormat = "tflite",
                quantization = "Q4_K_M",
                requiredRamMb = 3072,
                recommendedRamMb = 6144,
                sizeMb = 1700,
                localPath = "",
                installState = InstallState.NOT_INSTALLED,
                checksum = "",
                version = "1.0",
                supportsStructuredJson = true,
                supportsStreaming = true,
                supportsTextGeneration = true
            ),
            LocalAiModel(
                modelId = "tinyllama-1b-q8",
                displayName = "TinyLlama 1.1B (Q8)",
                runtimeType = RuntimeType.MEDIA_PIPE,
                fileFormat = "tflite",
                quantization = "Q8_0",
                requiredRamMb = 1536,
                recommendedRamMb = 3072,
                sizeMb = 1100,
                localPath = "",
                installState = InstallState.NOT_INSTALLED,
                checksum = "",
                version = "1.0",
                supportsStructuredJson = false,
                supportsStreaming = true,
                supportsTextGeneration = true
            )
        )
    }
}
