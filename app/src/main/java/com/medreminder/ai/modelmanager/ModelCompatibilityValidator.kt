package com.medreminder.ai.modelmanager

import android.os.Build
import com.medreminder.ai.capability.DeviceAiCapabilityDetector
import com.medreminder.ai.local.LocalAiModel
import com.medreminder.ai.local.RuntimeType
import javax.inject.Inject
import javax.inject.Singleton

data class ValidationResult(
    val isCompatible: Boolean,
    val reasons: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

@Singleton
class ModelCompatibilityValidator @Inject constructor(
    private val capabilityDetector: DeviceAiCapabilityDetector
) {
    fun validate(model: LocalAiModel): ValidationResult {
        val reasons = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Check ABI compatibility
        if (!checkAbiCompatibility(model)) {
            reasons.add("Device ABI not supported by this model")
        }

        // Check RAM requirement
        val caps = capabilityDetector.detectCapabilities()
        val deviceRamMb = when (caps.ramTier) {
            com.medreminder.ai.capability.RamTier.LOW -> 3072L
            com.medreminder.ai.capability.RamTier.MEDIUM -> 5120L
            com.medreminder.ai.capability.RamTier.HIGH -> 7168L
            com.medreminder.ai.capability.RamTier.VERY_HIGH -> 10240L
        }

        if (model.requiredRamMb > deviceRamMb) {
            reasons.add("Requires ${model.requiredRamMb} MB RAM, device has approximately ${deviceRamMb} MB")
        } else if (model.recommendedRamMb > deviceRamMb) {
            warnings.add("Recommended ${model.recommendedRamMb} MB RAM for optimal performance")
        }

        // Check storage requirement
        if (model.sizeMb > caps.availableStorageMb) {
            reasons.add("Requires ${model.sizeMb} MB storage, only ${caps.availableStorageMb} MB available")
        } else if (model.sizeMb > caps.availableStorageMb * 0.8) {
            warnings.add("Model will use most of available storage")
        }

        // Check runtime support
        if (!checkRuntimeSupport(model)) {
            reasons.add("Runtime ${model.runtimeType.name} not supported on this device")
        }

        return ValidationResult(
            isCompatible = reasons.isEmpty(),
            reasons = reasons,
            warnings = warnings
        )
    }

    private fun checkAbiCompatibility(model: LocalAiModel): Boolean {
        val supportedAbis = Build.SUPPORTED_ABIS.toSet()
        // Most TFLite and MediaPipe models support arm64-v8a and x86_64
        val requiredAbis = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        return supportedAbis.any { it in requiredAbis }
    }

    private fun checkRuntimeSupport(model: LocalAiModel): Boolean {
        return when (model.runtimeType) {
            RuntimeType.LITE_RT -> true // Bundled with app
            RuntimeType.MEDIA_PIPE -> true // Bundled with app
            RuntimeType.SYSTEM_AI -> capabilityDetector.hasAnySystemAi()
        }
    }
}
