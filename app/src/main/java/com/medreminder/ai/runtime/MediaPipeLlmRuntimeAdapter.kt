package com.medreminder.ai.runtime

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runtime adapter for MediaPipe LLM Inference API.
 * Runs .task model files on-device using Google's MediaPipe GenAI SDK.
 */
class MediaPipeLlmRuntimeAdapter(
    private val context: Context,
    private val modelPath: String
) : LocalModelRuntime {

    companion object {
        private const val TAG = "MediaPipeRuntime"
        private const val MAX_TOKENS = 1024
        private const val MAX_TOP_K = 64
        private const val TOP_K = 40
        private const val TEMPERATURE = 0.7f
    }

    private var llmInference: LlmInference? = null

    override suspend fun runPrompt(prompt: String): String {
        if (!isLoaded()) warmup()

        val inference = llmInference
            ?: throw IllegalStateException("MediaPipe LLM not loaded")

        return withContext(Dispatchers.Default) {
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(TOP_K)
                .setTemperature(TEMPERATURE)
                .build()
            val session = LlmInferenceSession.createFromOptions(inference, sessionOptions)
            try {
                session.generateResponse(prompt)
            } catch (e: Exception) {
                Log.e(TAG, "MediaPipe inference failed", e)
                throw e
            } finally {
                session.close()
            }
        }
    }

    override fun supportsStructuredJson(): Boolean = false

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
                Log.d(TAG, "MediaPipe LLM loaded from: $modelPath")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load MediaPipe LLM from: $modelPath", e)
                llmInference = null
                throw e
            }
        }
    }

    override fun release() {
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing MediaPipe LLM", e)
        }
        llmInference = null
    }
}
