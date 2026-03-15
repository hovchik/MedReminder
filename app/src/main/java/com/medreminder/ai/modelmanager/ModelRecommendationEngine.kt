package com.medreminder.ai.modelmanager

import com.medreminder.ai.capability.DeviceAiCapabilities
import com.medreminder.ai.capability.DeviceAiCapabilityDetector
import com.medreminder.ai.capability.DevicePerformanceClass
import com.medreminder.ai.capability.RamTier
import com.medreminder.ai.local.InstallState
import com.medreminder.ai.local.LocalAiModel
import com.medreminder.ai.local.RuntimeType
import javax.inject.Inject
import javax.inject.Singleton

data class ModelRecommendation(
    val model: LocalAiModel,
    val compatibilityScore: Int,       // 0-100
    val performanceEstimate: PerformanceEstimate,
    val compatibilityTag: CompatibilityTag,
    val reasons: List<String>,
    val warnings: List<String>
)

data class PerformanceEstimate(
    val estimatedTokensPerSec: Float,
    val estimatedLoadTimeSec: Int,
    val estimatedRamUsageMb: Int,
    val willUseGpu: Boolean
)

enum class CompatibilityTag(val label: String) {
    BEST_FIT("Best for this device"),
    RECOMMENDED("Recommended"),
    COMPATIBLE("Compatible"),
    MARGINAL("May be slow"),
    INCOMPATIBLE("Not compatible")
}

@Singleton
class ModelRecommendationEngine @Inject constructor(
    private val capabilityDetector: DeviceAiCapabilityDetector,
    private val validator: ModelCompatibilityValidator
) {

    /**
     * Catalog of verified, downloadable models hosted on HuggingFace litert-community.
     * Only includes models confirmed to work with MediaPipe LLM Inference on Android.
     *
     * Note: Phi-2, Falcon-RW-1B, and StableLM-3B were removed because:
     * - No pre-converted MediaPipe-compatible files exist on litert-community
     * - Known issues with infinite loops in MediaPipe LLM Inference (github.com/google-ai-edge/mediapipe-samples/issues/492)
     * - Old storage.googleapis.com URLs are defunct (404)
     */
    fun getFullModelCatalog(): List<LocalAiModel> = listOf(
        // --- Gemma 3 1B INT4 — smallest, runs on nearly any device ---
        LocalAiModel(
            modelId = "gemma3-1b-it-int4",
            displayName = "Gemma 3 1B (INT4)",
            description = "Newest Gemma, 1B parameters. Extremely compact, ideal for low-RAM devices.",
            runtimeType = RuntimeType.MEDIA_PIPE,
            fileFormat = "task",
            quantization = "INT4",
            requiredRamMb = 1024,
            recommendedRamMb = 2048,
            sizeMb = 560,
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
            localPath = "",
            installState = InstallState.NOT_INSTALLED,
            checksum = "",
            version = "3.0",
            supportsStructuredJson = false,
            supportsStreaming = true,
            supportsTextGeneration = true,
            parameterCount = "1B"
        ),
        // --- Gemma 3 1B INT4 (4096 context) — longer context window ---
        LocalAiModel(
            modelId = "gemma3-1b-it-int4-ctx4096",
            displayName = "Gemma 3 1B (INT4, 4K context)",
            description = "Gemma 3 1B with extended 4096 token context window for longer analysis.",
            runtimeType = RuntimeType.MEDIA_PIPE,
            fileFormat = "task",
            quantization = "INT4",
            requiredRamMb = 1536,
            recommendedRamMb = 3072,
            sizeMb = 620,
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_block128_ekv4096.task",
            localPath = "",
            installState = InstallState.NOT_INSTALLED,
            checksum = "",
            version = "3.0",
            supportsStructuredJson = false,
            supportsStreaming = true,
            supportsTextGeneration = true,
            parameterCount = "1B"
        ),
        // --- Gemma 3 1B INT8 — higher quality at 1B ---
        LocalAiModel(
            modelId = "gemma3-1b-it-int8",
            displayName = "Gemma 3 1B (INT8 - Higher Quality)",
            description = "INT8 quantization of Gemma 3 1B. Better output quality, needs more RAM.",
            runtimeType = RuntimeType.MEDIA_PIPE,
            fileFormat = "task",
            quantization = "INT8",
            requiredRamMb = 2048,
            recommendedRamMb = 4096,
            sizeMb = 1100,
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q8_ekv1280.task",
            localPath = "",
            installState = InstallState.NOT_INSTALLED,
            checksum = "",
            version = "3.0",
            supportsStructuredJson = false,
            supportsStreaming = true,
            supportsTextGeneration = true,
            parameterCount = "1B"
        ),
        // --- Gemma 2 2B INT8 — larger model, higher quality ---
        LocalAiModel(
            modelId = "gemma2-2b-it-int8",
            displayName = "Gemma 2 2B (INT8)",
            description = "Google's 2B instruction-tuned model. Strong text quality, needs 4+ GB RAM.",
            runtimeType = RuntimeType.MEDIA_PIPE,
            fileFormat = "task",
            quantization = "INT8",
            requiredRamMb = 3072,
            recommendedRamMb = 6144,
            sizeMb = 2710,
            downloadUrl = "https://huggingface.co/litert-community/Gemma2-2B-IT/resolve/main/Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280.task",
            localPath = "",
            installState = InstallState.NOT_INSTALLED,
            checksum = "",
            version = "2.0",
            supportsStructuredJson = false,
            supportsStreaming = true,
            supportsTextGeneration = true,
            parameterCount = "2B"
        )
    )

    /**
     * Analyze each model against device capabilities, score, rank, and return
     * a sorted list of recommendations (best-fit first).
     */
    fun getRecommendations(): List<ModelRecommendation> {
        val caps = capabilityDetector.detectCapabilities()
        val catalog = getFullModelCatalog()

        return catalog
            .map { model -> scoreModel(model, caps) }
            .sortedByDescending { it.compatibilityScore }
    }

    /** Return only models tagged BEST_FIT, RECOMMENDED, or COMPATIBLE. */
    fun getCompatibleRecommendations(): List<ModelRecommendation> =
        getRecommendations().filter {
            it.compatibilityTag != CompatibilityTag.INCOMPATIBLE
        }

    /** Single best model for this device. */
    fun getBestRecommendation(): ModelRecommendation? =
        getRecommendations().firstOrNull {
            it.compatibilityTag in setOf(CompatibilityTag.BEST_FIT, CompatibilityTag.RECOMMENDED)
        }

    private fun scoreModel(model: LocalAiModel, caps: DeviceAiCapabilities): ModelRecommendation {
        val validation = validator.validate(model)
        val reasons = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var score = 0

        // ── Compatibility gate ──────────────────────────────────────────
        if (!validation.isCompatible) {
            return ModelRecommendation(
                model = model,
                compatibilityScore = 0,
                performanceEstimate = PerformanceEstimate(0f, 0, 0, false),
                compatibilityTag = CompatibilityTag.INCOMPATIBLE,
                reasons = validation.reasons,
                warnings = validation.warnings
            )
        }

        // ── RAM score (0-30) ────────────────────────────────────────────
        val ramHeadroom = caps.totalRamMb - model.requiredRamMb
        val ramScore = when {
            ramHeadroom >= model.recommendedRamMb -> 30
            ramHeadroom >= model.requiredRamMb -> 25
            ramHeadroom >= 1024 -> 20
            ramHeadroom >= 512 -> 10
            else -> 5
        }
        score += ramScore

        if (caps.totalRamMb < model.recommendedRamMb) {
            warnings.add("Device has ${caps.totalRamMb} MB RAM; ${model.recommendedRamMb} MB recommended")
        } else {
            reasons.add("Sufficient RAM (${caps.totalRamMb} MB available)")
        }

        // ── Storage score (0-15) ────────────────────────────────────────
        val storageHeadroom = caps.availableStorageMb - model.sizeMb
        val storageScore = when {
            storageHeadroom >= 5000 -> 15
            storageHeadroom >= 2000 -> 12
            storageHeadroom >= 500 -> 8
            else -> 3
        }
        score += storageScore

        if (storageHeadroom < 1000) {
            warnings.add("Only ${caps.availableStorageMb} MB free after download")
        }

        // ── Model size preference (0-20) – smaller is better on mobile ─
        val sizeScore = when {
            model.sizeMb < 700 -> 20
            model.sizeMb < 1200 -> 16
            model.sizeMb < 1800 -> 12
            model.sizeMb < 2500 -> 6
            else -> 2
        }
        score += sizeScore
        reasons.add("${model.sizeMb} MB download (${model.quantization})")

        // ── GPU acceleration bonus (0-10) ───────────────────────────────
        val willUseGpu = caps.hasGpuCompute && caps.supportedAbis.any { it == "arm64-v8a" }
        if (willUseGpu) {
            score += 10
            reasons.add("GPU-accelerated inference available")
        }

        // ── Performance class bonus (0-15) ──────────────────────────────
        val perfScore = when (caps.performanceClass) {
            DevicePerformanceClass.PREMIUM -> 15
            DevicePerformanceClass.HIGH -> 12
            DevicePerformanceClass.MEDIUM -> 8
            DevicePerformanceClass.LOW -> 3
        }
        score += perfScore

        // ── Quality bonus for structured JSON support (0-10) ────────────
        if (model.supportsStructuredJson) {
            score += 10
            reasons.add("Supports structured JSON output")
        }

        // ── Determine compatibility tag ─────────────────────────────────
        val tag = when {
            score >= 80 -> CompatibilityTag.BEST_FIT
            score >= 60 -> CompatibilityTag.RECOMMENDED
            score >= 40 -> CompatibilityTag.COMPATIBLE
            score >= 20 -> CompatibilityTag.MARGINAL
            else -> CompatibilityTag.INCOMPATIBLE
        }

        // ── Performance estimates ───────────────────────────────────────
        val estimatedTps = estimateTokensPerSec(model, caps, willUseGpu)
        val estimatedLoadSec = estimateLoadTime(model, caps)
        val estimatedRamUsage = estimateRamUsage(model)

        warnings.addAll(validation.warnings)

        return ModelRecommendation(
            model = model,
            compatibilityScore = score.coerceIn(0, 100),
            performanceEstimate = PerformanceEstimate(
                estimatedTokensPerSec = estimatedTps,
                estimatedLoadTimeSec = estimatedLoadSec,
                estimatedRamUsageMb = estimatedRamUsage,
                willUseGpu = willUseGpu
            ),
            compatibilityTag = tag,
            reasons = reasons,
            warnings = warnings
        )
    }

    private fun estimateTokensPerSec(
        model: LocalAiModel,
        caps: DeviceAiCapabilities,
        useGpu: Boolean
    ): Float {
        // Rough heuristic based on model size, quantization, device class
        val baseTps = when {
            model.sizeMb < 700 -> 25f
            model.sizeMb < 1200 -> 15f
            model.sizeMb < 1800 -> 10f
            else -> 6f
        }

        val quantMultiplier = when {
            model.quantization.contains("INT4", ignoreCase = true) ||
                    model.quantization.contains("Q4", ignoreCase = true) -> 1.4f
            model.quantization.contains("INT8", ignoreCase = true) ||
                    model.quantization.contains("Q8", ignoreCase = true) -> 1.0f
            else -> 0.8f
        }

        val deviceMultiplier = when (caps.performanceClass) {
            DevicePerformanceClass.PREMIUM -> 2.0f
            DevicePerformanceClass.HIGH -> 1.5f
            DevicePerformanceClass.MEDIUM -> 1.0f
            DevicePerformanceClass.LOW -> 0.5f
        }

        val gpuBoost = if (useGpu) 1.5f else 1.0f

        return baseTps * quantMultiplier * deviceMultiplier * gpuBoost
    }

    private fun estimateLoadTime(model: LocalAiModel, caps: DeviceAiCapabilities): Int {
        val baseSec = (model.sizeMb / 500).toInt().coerceAtLeast(1)
        return when (caps.performanceClass) {
            DevicePerformanceClass.PREMIUM -> baseSec
            DevicePerformanceClass.HIGH -> (baseSec * 1.5).toInt()
            DevicePerformanceClass.MEDIUM -> baseSec * 2
            DevicePerformanceClass.LOW -> baseSec * 4
        }
    }

    private fun estimateRamUsage(model: LocalAiModel): Int {
        // Typically quantized models use 1.2-1.5x their file size in RAM
        return ((model.sizeMb * 1.3).toLong()).toInt()
    }
}
