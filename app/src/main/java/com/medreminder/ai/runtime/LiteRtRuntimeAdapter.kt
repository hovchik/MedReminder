package com.medreminder.ai.runtime

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runtime adapter for LiteRT-LM model files (.litertlm).
 * Uses the LiteRT-LM Engine API which natively supports the .litertlm bundle format.
 *
 * NOTE: .litertlm is an evolution of .task — it is NOT a raw TFLite flatbuffer.
 * MediaPipe's LlmInference cannot parse it; this adapter uses the dedicated
 * LiteRT-LM SDK instead.
 */
class LiteRtRuntimeAdapter(
    private val context: Context,
    private val modelPath: String
) : LocalModelRuntime {

    companion object {
        private const val TAG = "LiteRtRuntime"
    }

    private var engine: Engine? = null

    override suspend fun runPrompt(prompt: String): String {
        if (!isLoaded()) warmup()

        val eng = engine
            ?: throw IllegalStateException("LiteRT-LM engine not loaded")

        return withContext(Dispatchers.Default) {
            val conversation = eng.createConversation()
            try {
                val response = conversation.sendMessage(prompt)
                response.toString()
            } catch (e: Exception) {
                Log.e(TAG, "LiteRT-LM inference failed", e)
                throw e
            } finally {
                try {
                    conversation.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing LiteRT-LM conversation", e)
                }
            }
        }
    }

    override fun supportsStructuredJson(): Boolean = true

    override fun isLoaded(): Boolean = engine != null

    override suspend fun warmup() {
        withContext(Dispatchers.IO) {
            try {
                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU,
                    cacheDir = context.cacheDir.absolutePath
                )
                val eng = Engine(config)
                eng.initialize()
                engine = eng
                Log.d(TAG, "LiteRT-LM engine loaded from: $modelPath")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load LiteRT-LM engine from: $modelPath", e)
                engine = null
                throw e
            }
        }
    }

    override fun release() {
        try {
            engine?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing LiteRT-LM engine", e)
        }
        engine = null
    }
}
