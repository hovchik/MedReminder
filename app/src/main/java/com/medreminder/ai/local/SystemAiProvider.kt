package com.medreminder.ai.local

import android.content.Context
import com.medreminder.ai.*
import com.medreminder.ai.capability.DeviceAiCapabilityDetector
import com.medreminder.ai.runtime.LocalModelRuntime
import com.medreminder.ai.runtime.SystemAiRuntimeAdapter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemAiProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capabilityDetector: DeviceAiCapabilityDetector,
    private val promptAdapter: PromptAdapter
) : AiProvider {

    override val type = AiProviderType.SYSTEM_AI
    override val displayName = "System AI (On-Device)"

    private var runtime: LocalModelRuntime? = null

    fun initialize() {
        if (capabilityDetector.hasAiCoreSupport()) {
            runtime = SystemAiRuntimeAdapter(context)
        }
    }

    override fun isAvailable(): Boolean {
        return capabilityDetector.hasAiCoreSupport() ||
                capabilityDetector.hasMlKitGenAiSupport()
    }

    override suspend fun generateAnalysis(input: AnalysisInput): AnalysisResult {
        val startTime = System.currentTimeMillis()
        val activeRuntime = runtime ?: throw IllegalStateException("System AI runtime not initialized")

        val prompt = promptAdapter.adaptPrompt(
            input = input,
            supportsStructuredJson = activeRuntime.supportsStructuredJson()
        )

        val rawResponse = activeRuntime.runPrompt(prompt)
        val result = promptAdapter.parseResponse(rawResponse, input)

        return result.copy(
            providerUsed = AiProviderType.SYSTEM_AI,
            latencyMs = System.currentTimeMillis() - startTime
        )
    }
}
