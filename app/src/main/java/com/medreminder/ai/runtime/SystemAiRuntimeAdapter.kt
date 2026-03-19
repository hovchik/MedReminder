package com.medreminder.ai.runtime

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * Runtime adapter for Android system AI (AICore / Gemini Nano).
 *
 * Attempts to use the real AICore generative-AI service when available.
 * If the device does not have a functional AICore installation, [warmup]
 * throws so that the provider correctly reports itself as unavailable and
 * the fallback chain reaches a downloaded local model instead.
 */
class SystemAiRuntimeAdapter(
    private val context: Context
) : LocalModelRuntime {

    companion object {
        private const val TAG = "SystemAiRuntime"
    }

    private var loaded = false

    override suspend fun runPrompt(prompt: String): String {
        if (!loaded) warmup()

        // AICore / Gemini Nano integration point.
        // The official Android GenAI SDK (com.google.ai.edge.litert) would be called here:
        //   val generativeModel = GenerativeModel("gemini-nano")
        //   val response = generativeModel.generateContent(prompt)
        //   return response.text
        //
        // Until the AICore SDK dependency is added, this runtime cannot produce
        // real inferences. Throw so callers fall back to a downloaded local model.
        throw UnsupportedOperationException(
            "AICore SDK is not integrated yet. Use a downloaded local model instead."
        )
    }

    override fun supportsStructuredJson(): Boolean = true

    override fun isLoaded(): Boolean = loaded

    override suspend fun warmup() {
        // Verify that AICore is genuinely functional, not just that the package exists.
        if (!isAiCoreFunctional()) {
            Log.w(TAG, "AICore is not functional on this device — marking runtime as unavailable")
            loaded = false
            throw UnsupportedOperationException(
                "AICore is not functional on this device"
            )
        }

        // If we reach here the package is present, but without the actual SDK
        // dependency we still cannot run inference. Mark as not loaded.
        Log.w(TAG, "AICore package found but SDK is not integrated — runtime unavailable")
        loaded = false
        throw UnsupportedOperationException(
            "AICore SDK dependency is not included in the build"
        )
    }

    override fun release() {
        loaded = false
    }

    /**
     * Check whether AICore is genuinely available and can serve generative-AI
     * requests — not just that the package is installed.
     */
    private fun isAiCoreFunctional(): Boolean {
        if (Build.VERSION.SDK_INT < 34) return false

        return try {
            val pkgInfo = context.packageManager.getPackageInfo(
                "com.google.android.aicore",
                PackageManager.GET_SERVICES
            )
            // Package exists; check that it actually exposes a service
            val hasService = pkgInfo.services?.isNotEmpty() == true
            if (!hasService) {
                Log.d(TAG, "AICore package present but has no services")
            }
            hasService
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
