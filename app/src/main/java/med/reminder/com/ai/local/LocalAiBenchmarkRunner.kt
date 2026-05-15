package med.reminder.com.ai.local

import med.reminder.com.ai.*
import med.reminder.com.ai.runtime.LocalModelRuntime
import javax.inject.Inject
import javax.inject.Singleton

data class BenchmarkResult(
    val modelId: String,
    val providerType: AiProviderType,
    val inferenceLatencyMs: Long,
    val tokenGenerationSpeed: Float, // tokens per second (estimated)
    val memoryUsageMb: Long,
    val success: Boolean,
    val errorMessage: String? = null
)

@Singleton
class LocalAiBenchmarkRunner @Inject constructor() {

    private val testPrompt = """
        |Analyze medication adherence data and provide health insights.
        |
        |Medications:
        |- Aspirin 81mg (Pill, Every day) [Take with food]
        |- Lisinopril 10mg (Pill, Every day)
        |
        |Adherence Rate: 85.0%
        |Period: 7 days
        |Total Doses: 14
        |Taken: 12
        |Missed: 2
        |Skipped: 0
        |Current Streak: 3 days
        |Analysis Type: DAILY
        |
        |Provide a brief summary, insights, and recommendations.
    """.trimMargin()

    suspend fun runBenchmark(provider: AiProvider): BenchmarkResult {
        val runtime = Runtime.getRuntime()
        val memBefore = runtime.totalMemory() - runtime.freeMemory()

        val input = AnalysisInput(
            medications = listOf(
                MedicationSummary("Aspirin", "81mg", "Pill", "Every day", "Take with food"),
                MedicationSummary("Lisinopril", "10mg", "Pill", "Every day", "")
            ),
            adherenceRate = 85f,
            totalDoses = 14,
            takenDoses = 12,
            missedDoses = 2,
            skippedDoses = 0,
            currentStreak = 3,
            periodDays = 7,
            analysisType = AnalysisType.DAILY
        )

        return try {
            val startTime = System.currentTimeMillis()
            val result = provider.generateAnalysis(input)
            val latency = System.currentTimeMillis() - startTime

            val memAfter = runtime.totalMemory() - runtime.freeMemory()
            val memUsedMb = (memAfter - memBefore) / (1024 * 1024)

            // Estimate token speed based on response length and latency
            val estimatedTokens = result.summary.split(" ").size +
                    result.insights.sumOf { it.split(" ").size } +
                    result.recommendations.sumOf { it.split(" ").size }
            val tokensPerSecond = if (latency > 0) {
                (estimatedTokens * 1000f) / latency
            } else 0f

            BenchmarkResult(
                modelId = provider.type.name,
                providerType = provider.type,
                inferenceLatencyMs = latency,
                tokenGenerationSpeed = tokensPerSecond,
                memoryUsageMb = maxOf(0, memUsedMb),
                success = true
            )
        } catch (e: Exception) {
            BenchmarkResult(
                modelId = provider.type.name,
                providerType = provider.type,
                inferenceLatencyMs = 0,
                tokenGenerationSpeed = 0f,
                memoryUsageMb = 0,
                success = false,
                errorMessage = e.message
            )
        }
    }

    suspend fun runRuntimeBenchmark(runtime: LocalModelRuntime, modelId: String): BenchmarkResult {
        val memBefore = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

        return try {
            val startTime = System.currentTimeMillis()
            val response = runtime.runPrompt(testPrompt)
            val latency = System.currentTimeMillis() - startTime

            val memAfter = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
            val memUsedMb = (memAfter - memBefore) / (1024 * 1024)

            val estimatedTokens = response.split(" ").size
            val tokensPerSecond = if (latency > 0) {
                (estimatedTokens * 1000f) / latency
            } else 0f

            BenchmarkResult(
                modelId = modelId,
                providerType = AiProviderType.CUSTOM_LOCAL,
                inferenceLatencyMs = latency,
                tokenGenerationSpeed = tokensPerSecond,
                memoryUsageMb = maxOf(0, memUsedMb),
                success = true
            )
        } catch (e: Exception) {
            BenchmarkResult(
                modelId = modelId,
                providerType = AiProviderType.CUSTOM_LOCAL,
                inferenceLatencyMs = 0,
                tokenGenerationSpeed = 0f,
                memoryUsageMb = 0,
                success = false,
                errorMessage = e.message
            )
        }
    }
}
