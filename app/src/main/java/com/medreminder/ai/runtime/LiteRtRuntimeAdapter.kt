package com.medreminder.ai.runtime

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runtime adapter for LiteRT model files (.tflite, .litertlm).
 * Uses MediaPipe LLM Inference API which supports both .task and .tflite formats.
 *
 * NOTE: .litertlm bundles require the LiteRT-LM SDK (com.google.ai.edge.litertlm)
 * which needs Kotlin 2.2+. Until the project upgrades Kotlin, only .task models
 * should be used. This adapter is kept for forward compatibility.
 */
class LiteRtRuntimeAdapter(
    private val context: Context,
    private val modelPath: String
) : LocalModelRuntime {

    companion object {
        private const val TAG = "LiteRtRuntime"
        private const val MAX_TOKENS = 4096
        private const val MAX_TOP_K = 64
        private const val TOP_K = 40
        private const val TEMPERATURE = 0.7f
    }

    private var llmInference: LlmInference? = null

    override suspend fun runPrompt(prompt: String): String {
        if (!isLoaded()) warmup()

        val inference = llmInference
            ?: throw IllegalStateException("LiteRT engine not loaded")

        return withContext(Dispatchers.Default) {
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(TOP_K)
                .setTemperature(TEMPERATURE)
                .build()
            val session = LlmInferenceSession.createFromOptions(inference, sessionOptions)
            try {
                session.addQueryChunk(prompt)
                session.generateResponse()
            } catch (e: Exception) {
                Log.e(TAG, "LiteRT inference failed", e)
                throw e
            } finally {
                session.close()
            }
        }
    }

    override fun supportsStructuredJson(): Boolean = true

    override fun isLoaded(): Boolean = llmInference != null

    override suspend fun warmup() {
        withContext(Dispatchers.IO) {
            try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(MAX_TOKENS)
                    .setMaxTopK(MAX_TOP_K)
                    .build()
                llmInference = LlmInference.createFromOptions(context, options)
                Log.d(TAG, "LiteRT model loaded from: $modelPath")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load LiteRT model from: $modelPath", e)
                llmInference = null
                throw e
            }
        }
    }

    override fun release() {
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing LiteRT engine", e)
        }
        llmInference = null
    }
}
