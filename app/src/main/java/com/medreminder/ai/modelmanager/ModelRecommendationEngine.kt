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
     * Catalog of downloadable models on HuggingFace.
     *
     * Only .task format models are listed — these work with the MediaPipe
     * LlmInference API bundled in the app. Gated models (e.g. Gemma) require
     * a HuggingFace access token configured in Settings.
     *
     * .litertlm models (Gemma 3n, Qwen 3.5 VL) are NOT supported yet because
     * they require the LiteRT LM SDK (com.google.ai.edge.litert) which needs
     * Kotlin 2.2+. The project currently uses Kotlin 1.9.22.
     *
     * Removed: Phi-2, Falcon-RW-1B, StableLM-3B (broken with MediaPipe).
     */
    fun getFullModelCatalog(): List<LocalAiModel> = listOf(
        // --- SmolLM 135M — ultra-tiny, instant on any device ---
        LocalAiModel(
            modelId = "smollm-135m-instruct-q8",
            displayName = "SmolLM 135M (Q8)",
            description = "Tiny 135M model, only 159 MB. Fastest load time, basic text generation.",
            runtimeType = RuntimeType.MEDIA_PIPE,
            fileFormat = "task",
            quantization = "Q8",
            requiredRamMb = 256,
            recommendedRamMb = 512,
            sizeMb = 159,
            downloadUrl = "https://huggingface.co/litert-community/SmolLM-135M-Instruct/resolve/main/SmolLM-135M-Instruct_multi-prefill-seq_q8_ekv1280.task",
            localPath = "",
            installState = InstallState.NOT_INSTALLED,
            checksum = "",
            version = "1.0",
            supportsStructuredJson = false,
            supportsStreaming = true,
            supportsTextGeneration = true,
            parameterCount = "135M"
        ),
        // --- Qwen2.5 0.5B — small and capable, MediaPipe ---
        LocalAiModel(
            modelId = "qwen2.5-0.5b-instruct-q8",
            displayName = "Qwen2.5 0.5B (Q8)",
            description = "Compact 0.5B instruction-tuned model. Good quality for its size, MediaPipe-compatible.",
            runtimeType = RuntimeType.MEDIA_PIPE,
            fileFormat = "task",
            quantization = "Q8",
            requiredRamMb = 512,
            recommendedRamMb = 1024,
            sizeMb = 521,
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            localPath = "",
            installState = InstallState.NOT_INSTALLED,
            checksum = "",
            version = "2.5",
            supportsStructuredJson = false,
            supportsStreaming = true,
            supportsTextGeneration = true,
            parameterCount = "0.5B"
        ),
        // --- Qwen2.5 1.5B — mid-range, instruction-tuned, MediaPipe ---
        LocalAiModel(
            modelId = "qwen2.5-1.5b-instruct-q8",
            displayName = "Qwen2.5 1.5B (Q8)",
            description = "Instruction-tuned 1.5B model from the Qwen2.5 family. Good balance of quality and speed.",
            runtimeType = RuntimeType.MEDIA_PIPE,
            fileFormat = "task",
            quantization = "Q8",
            requiredRamMb = 2048,
            recommendedRamMb = 3072,
            sizeMb = 1524,
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            localPath = "",
            installState = InstallState.NOT_INSTALLED,
            checksum = "",
            version = "2.5",
            supportsStructuredJson = false,
            supportsStreaming = true,
            supportsTextGeneration = true,
            parameterCount = "1.5B"
        ),
        // --- Qwen3 4B Thinking — high quality with reasoning, MediaPipe ---
        LocalAiModel(
            modelId = "qwen3-4b-thinking",
            displayName = "Qwen3 4B Thinking",
            description = "4B model with reasoning capabilities. INT4 quantized, 2048 context. MediaPipe-compatible.",
            runtimeType = RuntimeType.MEDIA_PIPE,
            fileFormat = "task",
            quantization = "INT4",
            requiredRamMb = 3072,
            recommendedRamMb = 6144,
            sizeMb = 2001,
            downloadUrl = "https://huggingface.co/harithoppil/qwen3-4b-thinking-litert/resolve/main/qwen3_thinking_4b_q4_block128_ekv2048.task",
            localPath = "",
            installState = InstallState.NOT_INSTALLED,
            checksum = "",
            version = "3.0",
            supportsStructuredJson = false,
            supportsStreaming = true,
            supportsTextGeneration = true,
            parameterCount = "4B",
            isThinkingModel = true
        ),
        // --- Llama 3.2 1B — Meta's mobile-optimized Llama, compact ---
        LocalAiModel(
            modelId = "llama3.2-1b-q8",
            displayName = "Llama 3.2 1B (Q8)",
            description = "Meta's Llama 3.2 1B, optimized for mobile. Good quality for its size, Q8 quantized.",
            runtimeType = RuntimeType.MEDIA_PIPE,
            fileFormat = "task",
            quantization = "Q8",
            requiredRamMb = 2048,
            recommendedRamMb = 4096,
            sizeMb = 2212,
            downloadUrl = "https://huggingface.co/vimal-yuvabe/llama-3.2-1b-tflite/resolve/main/llama-3.2-1b-q8.task",
            localPath = "",
            installState = InstallState.NOT_INSTALLED,
            checksum = "",
            version = "3.2",
            supportsStructuredJson = false,
            supportsStreaming = true,
            supportsTextGeneration = true,
            parameterCount = "1B"
        ),
        // --- Llama 3.2 3B — Meta's mobile-optimized Llama, higher quality ---
        LocalAiModel(
            modelId = "llama3.2-3b-q8",
            displayName = "Llama 3.2 3B (Q8)",
            description = "Meta's Llama 3.2 3B, optimized for mobile. Strong reasoning for on-device use, Q8 quantized.",
            runtimeType = RuntimeType.MEDIA_PIPE,
            fileFormat = "task",
            quantization = "Q8",
            requiredRamMb = 4096,
            recommendedRamMb = 6144,
            sizeMb = 5622,
            downloadUrl = "https://huggingface.co/vimal-yuvabe/llama-3.2-3b-tflite/resolve/main/llama-3.2-3B-q8.task",
            localPath = "",
            installState = InstallState.NOT_INSTALLED,
            checksum = "",
            version = "3.2",
            supportsStructuredJson = false,
            supportsStreaming = true,
            supportsTextGeneration = true,
            parameterCount = "3B"
        ),
        // --- Gemma 3 1B — compact Google model, gated (requires HuggingFace token) ---
        LocalAiModel(
            modelId = "gemma3-1b-it-int4",
            displayName = "Gemma 3 1B (INT4)",
            description = "Google's Gemma 3 1B instruction-tuned. Good for nutrition text parsing and medication names. Requires HuggingFace token.",
            runtimeType = RuntimeType.MEDIA_PIPE,
            fileFormat = "task",
            quantization = "INT4",
            requiredRamMb = 512,
            recommendedRamMb = 1024,
            sizeMb = 529,
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
        // NOTE: Gemma 3n (.litertlm) and Qwen 3.5 VL (.litertlm) models are NOT
        // listed here because .litertlm requires the LiteRT LM SDK
        // (com.google.ai.edge.litert) which needs Kotlin 2.2+. The current project
        // uses Kotlin 1.9.22 + MediaPipe LlmInference which only supports .task files.
        // These models can be re-added once the project upgrades to Kotlin 2.2+ and
        // integrates the LiteRT LM SDK.
        //
        // --- Phi-4 Mini — medical text reasoning, open (no auth) ---
        LocalAiModel(
            modelId = "phi4-mini-q8",
            displayName = "Phi-4 Mini (Q8)",
            description = "Microsoft Phi-4 Mini instruction-tuned. Strong medical text reasoning and medication parsing. No auth required.",
            runtimeType = RuntimeType.MEDIA_PIPE,
            fileFormat = "task",
            quantization = "Q8",
            requiredRamMb = 4096,
            recommendedRamMb = 6144,
            sizeMb = 3700,
            downloadUrl = "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task",
            localPath = "",
            installState = InstallState.NOT_INSTALLED,
            checksum = "",
            version = "4.0",
            supportsStructuredJson = false,
            supportsStreaming = true,
            supportsTextGeneration = true,
            parameterCount = "3.8B"
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
