package com.medreminder.ai.local

import com.medreminder.ai.*
import com.medreminder.ai.modelmanager.LocalModelManager
import com.medreminder.ai.runtime.LocalModelRuntime
import com.medreminder.ai.runtime.LiteRtRuntimeAdapter
import com.medreminder.ai.runtime.MediaPipeLlmRuntimeAdapter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomLocalModelProvider @Inject constructor(
    private val modelManager: LocalModelManager,
    private val promptAdapter: PromptAdapter
) : AiProvider {

    override val type = AiProviderType.CUSTOM_LOCAL
    override val displayName = "Local Model (Custom)"

    private var activeRuntime: LocalModelRuntime? = null

    override fun isAvailable(): Boolean {
        // activeRuntime is set by loadModel() which is called by
        // AiProviderSelector.ensureActiveModelLoaded() before the provider is used.
        return activeRuntime != null
    }

    fun getActiveRuntime(): LocalModelRuntime? = activeRuntime

    suspend fun loadModel(modelId: String): Boolean {
        val model = modelManager.getModel(modelId) ?: return false
        if (model.installState != InstallState.INSTALLED) return false

        activeRuntime = when (model.runtimeType) {
            RuntimeType.LITE_RT -> LiteRtRuntimeAdapter(model.localPath)
            RuntimeType.MEDIA_PIPE -> MediaPipeLlmRuntimeAdapter(model.localPath)
            RuntimeType.SYSTEM_AI -> null // Handled by SystemAiProvider
        }

        return activeRuntime != null
    }

    fun unloadModel() {
        activeRuntime = null
    }

    override suspend fun generateAnalysis(input: AnalysisInput): AnalysisResult {
        val startTime = System.currentTimeMillis()
        val runtime = activeRuntime ?: throw IllegalStateException("No local model loaded")

        val prompt = promptAdapter.adaptPrompt(
            input = input,
            supportsStructuredJson = runtime.supportsStructuredJson()
        )

        val rawResponse = runtime.runPrompt(prompt)
        val result = promptAdapter.parseResponse(rawResponse, input)

        return result.copy(
            providerUsed = AiProviderType.CUSTOM_LOCAL,
            latencyMs = System.currentTimeMillis() - startTime
        )
    }
}
