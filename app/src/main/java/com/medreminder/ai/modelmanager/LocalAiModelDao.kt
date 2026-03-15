package com.medreminder.ai.modelmanager

import androidx.room.*
import com.medreminder.ai.local.LocalAiModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalAiModelDao {

    @Query("SELECT * FROM local_ai_models ORDER BY displayName ASC")
    fun getAllModels(): Flow<List<LocalAiModelEntity>>

    @Query("SELECT * FROM local_ai_models WHERE installState = 'installed'")
    fun getInstalledModels(): Flow<List<LocalAiModelEntity>>

    @Query("SELECT * FROM local_ai_models WHERE modelId = :modelId")
    suspend fun getModelById(modelId: String): LocalAiModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: LocalAiModelEntity)

    @Update
    suspend fun updateModel(model: LocalAiModelEntity)

    @Delete
    suspend fun deleteModel(model: LocalAiModelEntity)

    @Query("UPDATE local_ai_models SET installState = :state WHERE modelId = :modelId")
    suspend fun updateInstallState(modelId: String, state: String)

    @Query("UPDATE local_ai_models SET localPath = :path, installState = 'installed' WHERE modelId = :modelId")
    suspend fun setModelInstalled(modelId: String, path: String)

    @Query("SELECT COUNT(*) FROM local_ai_models WHERE installState = 'installed'")
    suspend fun getInstalledModelCount(): Int

    @Query("SELECT SUM(sizeMb) FROM local_ai_models WHERE installState = 'installed'")
    suspend fun getTotalInstalledSizeMb(): Long?
}
