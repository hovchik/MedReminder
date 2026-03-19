package com.medreminder.ai.local

import android.content.Context
import com.medreminder.ai.*
import com.medreminder.ai.capability.DeviceAiCapabilityDetector
import com.medreminder.ai.runtime.LocalModelRuntime
import com.medreminder.ai.runtime.SystemAiRuntimeAdapter
import dagger.hilt.android.qualifiers.ApplicationContext
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemAiProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capabilityDetector: DeviceAiCapabilityDetector,
    private val promptAdapter: PromptAdapter
) : AiProvider {

    companion object {
        private const val TAG = "SystemAiProvider"
    }

    override val type = AiProviderType.SYSTEM_AI
    override val displayName = "System AI (On-Device)"

    private var runtime: LocalModelRuntime? = null

    fun initialize() {
        try {
            if (capabilityDetector.hasAiCoreSupport()) {
                runtime = SystemAiRuntimeAdapter(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize System AI runtime", e)
            runtime = null
        }
    }

    override fun isAvailable(): Boolean {
        return runtime != null
    }

    /**
     * Returns true if the device hardware/software supports System AI,
     * even if the runtime hasn't been initialized yet.
     */
    fun hasDeviceCapability(): Boolean {
        return capabilityDetector.hasAiCoreSupport() ||
                capabilityDetector.hasMlKitGenAiSupport()
    }

    override suspend fun generateAnalysis(input: AnalysisInput): AnalysisResult {
        val startTime = System.currentTimeMillis()
        val activeRuntime = runtime
        if (activeRuntime == null) {
            Log.w(TAG, "System AI runtime not available, cannot generate analysis")
            throw SystemAiUnavailableException("System AI runtime not initialized")
        }

        return try {
            val prompt = promptAdapter.adaptPrompt(
                input = input,
                supportsStructuredJson = activeRuntime.supportsStructuredJson()
            )

            val rawResponse = activeRuntime.runPrompt(prompt)
            val result = promptAdapter.parseResponse(rawResponse, input)

            result.copy(
                providerUsed = AiProviderType.SYSTEM_AI,
                latencyMs = System.currentTimeMillis() - startTime
            )
        } catch (e: SystemAiUnavailableException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "System AI analysis failed", e)
            throw SystemAiUnavailableException("System AI analysis failed: ${e.message}", e)
        }
    }
}

/** Thrown when System AI cannot produce a result and the caller should fall back. */
class SystemAiUnavailableException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
