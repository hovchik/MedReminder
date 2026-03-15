package com.medreminder.ai.local

import androidx.room.*

enum class RuntimeType {
    LITE_RT,
    MEDIA_PIPE,
    SYSTEM_AI
}

enum class InstallState {
    NOT_INSTALLED,
    DOWNLOADING,
    INSTALLING,
    INSTALLED,
    FAILED,
    VALIDATING
}

data class LocalAiModel(
    val modelId: String,
    val displayName: String,
    val runtimeType: RuntimeType,
    val fileFormat: String,
    val quantization: String,
    val requiredRamMb: Int,
    val recommendedRamMb: Int,
    val sizeMb: Long,
    val localPath: String,
    val installState: InstallState,
    val checksum: String,
    val version: String,
    val supportsStructuredJson: Boolean,
    val supportsStreaming: Boolean,
    val supportsTextGeneration: Boolean
)

@Entity(tableName = "local_ai_models")
data class LocalAiModelEntity(
    @PrimaryKey val modelId: String,
    val displayName: String,
    val runtimeType: String,
    val fileFormat: String,
    val quantization: String,
    val requiredRamMb: Int,
    val recommendedRamMb: Int,
    val sizeMb: Long,
    val localPath: String,
    val installState: String,
    val checksum: String,
    val version: String,
    val supportsStructuredJson: Boolean,
    val supportsStreaming: Boolean,
    val supportsTextGeneration: Boolean
)

fun LocalAiModelEntity.toDomain() = LocalAiModel(
    modelId = modelId,
    displayName = displayName,
    runtimeType = try { RuntimeType.valueOf(runtimeType.uppercase()) } catch (_: Exception) { RuntimeType.LITE_RT },
    fileFormat = fileFormat,
    quantization = quantization,
    requiredRamMb = requiredRamMb,
    recommendedRamMb = recommendedRamMb,
    sizeMb = sizeMb,
    localPath = localPath,
    installState = try { InstallState.valueOf(installState.uppercase()) } catch (_: Exception) { InstallState.NOT_INSTALLED },
    checksum = checksum,
    version = version,
    supportsStructuredJson = supportsStructuredJson,
    supportsStreaming = supportsStreaming,
    supportsTextGeneration = supportsTextGeneration
)

fun LocalAiModel.toEntity() = LocalAiModelEntity(
    modelId = modelId,
    displayName = displayName,
    runtimeType = runtimeType.name.lowercase(),
    fileFormat = fileFormat,
    quantization = quantization,
    requiredRamMb = requiredRamMb,
    recommendedRamMb = recommendedRamMb,
    sizeMb = sizeMb,
    localPath = localPath,
    installState = installState.name.lowercase(),
    checksum = checksum,
    version = version,
    supportsStructuredJson = supportsStructuredJson,
    supportsStreaming = supportsStreaming,
    supportsTextGeneration = supportsTextGeneration
)
