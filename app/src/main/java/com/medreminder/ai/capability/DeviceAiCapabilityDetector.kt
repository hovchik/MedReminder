package com.medreminder.ai.capability

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceAiCapabilities(
    val androidVersion: Int,
    val hasAiCore: Boolean,
    val hasMlKitGenAi: Boolean,
    val ramTier: RamTier,
    val availableStorageMb: Long,
    val performanceClass: DevicePerformanceClass,
    val supportedRuntimes: List<String>
)

enum class RamTier(val displayName: String, val minRamMb: Long) {
    LOW("Low (< 4 GB)", 0),
    MEDIUM("Medium (4-6 GB)", 4096),
    HIGH("High (6-8 GB)", 6144),
    VERY_HIGH("Very High (> 8 GB)", 8192)
}

enum class DevicePerformanceClass(val displayName: String) {
    LOW("Basic - Cloud AI recommended"),
    MEDIUM("Medium - Small local models supported"),
    HIGH("High - Most local models supported"),
    PREMIUM("Premium - All local models supported")
}

@Singleton
class DeviceAiCapabilityDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var cachedCapabilities: DeviceAiCapabilities? = null

    fun detectCapabilities(): DeviceAiCapabilities {
        cachedCapabilities?.let { return it }

        val caps = DeviceAiCapabilities(
            androidVersion = Build.VERSION.SDK_INT,
            hasAiCore = checkAiCoreAvailability(),
            hasMlKitGenAi = checkMlKitGenAiAvailability(),
            ramTier = detectRamTier(),
            availableStorageMb = getAvailableStorageMb(),
            performanceClass = determinePerformanceClass(),
            supportedRuntimes = detectSupportedRuntimes()
        )

        cachedCapabilities = caps
        return caps
    }

    fun hasAiCoreSupport(): Boolean = detectCapabilities().hasAiCore

    fun hasMlKitGenAiSupport(): Boolean = detectCapabilities().hasMlKitGenAi

    fun hasAnySystemAi(): Boolean = hasAiCoreSupport() || hasMlKitGenAiSupport()

    fun canRunLocalModels(): Boolean {
        val caps = detectCapabilities()
        return caps.ramTier >= RamTier.MEDIUM &&
                caps.availableStorageMb >= 500 &&
                caps.performanceClass >= DevicePerformanceClass.MEDIUM
    }

    fun getRecommendedMode(): RecommendedAiMode {
        val caps = detectCapabilities()

        return when {
            caps.hasAiCore || caps.hasMlKitGenAi -> RecommendedAiMode.SYSTEM_AI
            canRunLocalModels() -> RecommendedAiMode.CUSTOM_LOCAL
            else -> RecommendedAiMode.CLOUD
        }
    }

    fun getDeviceMessage(): String {
        return when (getRecommendedMode()) {
            RecommendedAiMode.SYSTEM_AI ->
                "This device supports built-in on-device AI. The app can use the system AI engine."
            RecommendedAiMode.CUSTOM_LOCAL ->
                "This device does not expose built-in AI to apps, but it can run a downloadable local model."
            RecommendedAiMode.CLOUD ->
                "This device is not ideal for local AI. Cloud AI is recommended."
        }
    }

    private fun checkAiCoreAvailability(): Boolean {
        // Check if Android AICore service is available (Android 14+)
        if (Build.VERSION.SDK_INT < 34) return false

        return try {
            val pm = context.packageManager
            // AICore is provided by Google Play Services or a system package
            pm.getPackageInfo("com.google.android.aicore", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun checkMlKitGenAiAvailability(): Boolean {
        // Check if ML Kit GenAI inference is available via Google Play Services
        return try {
            val pm = context.packageManager
            val gmsInfo = pm.getPackageInfo("com.google.android.gms", 0)
            // ML Kit GenAI requires GMS version 24.26+ (approximate)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                gmsInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                gmsInfo.versionCode.toLong()
            }
            // GMS version code pattern: XXYYZZ where XX=major
            versionCode >= 242600000L
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun detectRamTier(): RamTier {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        return when {
            totalRamMb >= RamTier.VERY_HIGH.minRamMb -> RamTier.VERY_HIGH
            totalRamMb >= RamTier.HIGH.minRamMb -> RamTier.HIGH
            totalRamMb >= RamTier.MEDIUM.minRamMb -> RamTier.MEDIUM
            else -> RamTier.LOW
        }
    }

    private fun getAvailableStorageMb(): Long {
        val stat = StatFs(Environment.getDataDirectory().path)
        return (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)
    }

    private fun determinePerformanceClass(): DevicePerformanceClass {
        val ramTier = detectRamTier()
        val sdkVersion = Build.VERSION.SDK_INT

        return when {
            ramTier >= RamTier.VERY_HIGH && sdkVersion >= 33 -> DevicePerformanceClass.PREMIUM
            ramTier >= RamTier.HIGH && sdkVersion >= 31 -> DevicePerformanceClass.HIGH
            ramTier >= RamTier.MEDIUM && sdkVersion >= 28 -> DevicePerformanceClass.MEDIUM
            else -> DevicePerformanceClass.LOW
        }
    }

    private fun detectSupportedRuntimes(): List<String> {
        val runtimes = mutableListOf<String>()

        if (checkAiCoreAvailability()) {
            runtimes.add("AICore (Gemini Nano)")
        }
        if (checkMlKitGenAiAvailability()) {
            runtimes.add("ML Kit GenAI")
        }
        // LiteRT and MediaPipe are bundled, always available if the app includes them
        runtimes.add("LiteRT")
        runtimes.add("MediaPipe LLM Inference")

        return runtimes
    }

    fun invalidateCache() {
        cachedCapabilities = null
    }
}

enum class RecommendedAiMode {
    SYSTEM_AI,
    CUSTOM_LOCAL,
    CLOUD
}
