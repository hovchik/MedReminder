package com.medreminder.ai.local

import android.content.Context
import android.util.Log
import com.medreminder.ai.*
import com.medreminder.ai.modelmanager.LocalModelManager
import com.medreminder.ai.runtime.LocalModelRuntime
import com.medreminder.ai.runtime.LiteRtRuntimeAdapter
import com.medreminder.ai.runtime.MediaPipeLlmRuntimeAdapter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomLocalModelProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: LocalModelManager,
    private val promptAdapter: PromptAdapter
) : AiProvider {

    companion object {
        private const val TAG = "CustomLocalModelProvider"
    }

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

        // Release previous runtime before loading a new one
        activeRuntime?.release()
        activeRuntime = null

        activeRuntime = try {
            when (model.runtimeType) {
                RuntimeType.LITE_RT -> LiteRtRuntimeAdapter(context, model.localPath)
                RuntimeType.MEDIA_PIPE -> MediaPipeLlmRuntimeAdapter(context, model.localPath)
                RuntimeType.SYSTEM_AI -> null // Handled by SystemAiProvider
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create runtime for model $modelId", e)
            null
        }

        return activeRuntime != null
    }

    fun unloadModel() {
        activeRuntime?.release()
        activeRuntime = null
    }

    /**
     * Send a raw prompt to the local model and return the raw text response.
     * Returns null if no runtime is loaded or inference fails.
     */
    suspend fun generateRawCompletion(prompt: String): String? {
        val runtime = activeRuntime ?: return null
        return try {
            runtime.runPrompt(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Local model raw completion failed", e)
            null
        }
    }

    override suspend fun generateAnalysis(input: AnalysisInput): AnalysisResult {
        val startTime = System.currentTimeMillis()
        val runtime = activeRuntime
        if (runtime == null) {
            Log.w(TAG, "No local model loaded, cannot generate analysis")
            throw LocalModelUnavailableException("No local model loaded")
        }

        return try {
            val prompt = promptAdapter.adaptPrompt(
                input = input,
                supportsStructuredJson = runtime.supportsStructuredJson()
            )

            val rawResponse = runtime.runPrompt(prompt)
            val result = promptAdapter.parseResponse(rawResponse, input)

            result.copy(
                providerUsed = AiProviderType.CUSTOM_LOCAL,
                latencyMs = System.currentTimeMillis() - startTime
            )
        } catch (e: LocalModelUnavailableException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Local model analysis failed", e)
            throw LocalModelUnavailableException("Local model analysis failed: ${e.message}", e)
        }
    }
}

/** Thrown when a custom local model cannot produce a result and the caller should fall back. */
class LocalModelUnavailableException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
