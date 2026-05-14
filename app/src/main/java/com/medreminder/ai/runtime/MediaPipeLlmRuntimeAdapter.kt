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
        /**
         * Total token budget for the model session. Many small on-device
         * models (Gemma-2B, etc.) only support 1024-2048 tokens. Using 1024
         * as the safe default to avoid native GATHER_ND index-out-of-bounds
         * crashes (SIGSEGV) that cannot be caught in Java/Kotlin.
         */
        private const val MAX_TOKENS = 1024
        private const val MAX_TOP_K = 64
        private const val TOP_K = 40
        private const val TEMPERATURE = 0.7f
        /**
         * Very conservative character limit for input prompts.
         * ~3-4 chars per token → 512 tokens input budget ≈ 1536 chars,
         * leaving ~512 tokens for the generated response.
         */
        private const val MAX_PROMPT_CHARS = 1536
    }

    private var llmInference: LlmInference? = null

    override suspend fun runPrompt(prompt: String): String {
        if (!isLoaded()) warmup()

        val inference = llmInference
            ?: throw IllegalStateException("MediaPipe LLM not loaded")

        // Truncate prompt to stay within the model's context window and avoid
        // native GATHER_ND index-out-of-bounds crashes (SIGSEGV).
        val safePrompt = if (prompt.length > MAX_PROMPT_CHARS) {
            Log.w(TAG, "Prompt truncated from ${prompt.length} to $MAX_PROMPT_CHARS chars to prevent native crash")
            prompt.take(MAX_PROMPT_CHARS)
        } else {
            prompt
        }

        return withContext(Dispatchers.Default) {
            var session: LlmInferenceSession? = null
            try {
                val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(TOP_K)
                    .setTemperature(TEMPERATURE)
                    .build()
                session = LlmInferenceSession.createFromOptions(inference, sessionOptions)
                session.addQueryChunk(safePrompt)
                session.generateResponse()
            } catch (e: Exception) {
                Log.e(TAG, "MediaPipe inference failed", e)
                // Native engine state is corrupted after a GATHER_ND / TFLite error.
                // Release the engine so it gets recreated on the next call.
                releaseEngine()
                throw e
            } finally {
                try {
                    session?.close()
                } catch (closeEx: Exception) {
                    Log.w(TAG, "Error closing session", closeEx)
                }
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
                Log.d(TAG, "MediaPipe LLM loaded from: $modelPath (maxTokens=$MAX_TOKENS)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load MediaPipe LLM from: $modelPath", e)
                llmInference = null
                throw e
            }
        }
    }

    override fun release() {
        releaseEngine()
    }

    private fun releaseEngine() {
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing MediaPipe LLM", e)
        }
        llmInference = null
    }
}
