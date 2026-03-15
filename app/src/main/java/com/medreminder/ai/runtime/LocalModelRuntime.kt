package com.medreminder.ai.runtime

interface LocalModelRuntime {
    suspend fun runPrompt(prompt: String): String
    fun supportsStructuredJson(): Boolean
    fun isLoaded(): Boolean
    suspend fun warmup()
    fun release()
}
