package med.reminder.com.ai.modelmanager

import med.reminder.com.ai.local.*
import kotlinx.coroutines.flow.Flow
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

    suspend fun getInstalledModelsSuspend(): List<LocalAiModel> =
        modelDao.getInstalledModelsList().map { it.toDomain() }

    fun getInstalledModels(): List<LocalAiModel> {
        return emptyList() // Prefer Flow-based or suspend access
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

    /**
     * @deprecated Use ModelRecommendationEngine.getFullModelCatalog() instead
     */
    fun getAvailableModelsCatalog(): List<LocalAiModel> = emptyList()
}
