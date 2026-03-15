package com.medreminder.ai

data class AnalysisInput(
    val medications: List<MedicationSummary>,
    val adherenceRate: Float,
    val totalDoses: Int,
    val takenDoses: Int,
    val missedDoses: Int,
    val skippedDoses: Int,
    val currentStreak: Int,
    val periodDays: Int,
    val weeklyBreakdown: List<DayBreakdown> = emptyList(),
    val analysisType: AnalysisType = AnalysisType.DAILY
)

data class MedicationSummary(
    val name: String,
    val dosage: String,
    val form: String,
    val frequency: String,
    val instructions: String = ""
)

data class DayBreakdown(
    val dayName: String,
    val taken: Int,
    val total: Int,
    val rate: Float
)

enum class AnalysisType {
    DAILY,
    WEEKLY
}

data class AnalysisResult(
    val summary: String,
    val insights: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val providerUsed: AiProviderType = AiProviderType.CLOUD,
    val latencyMs: Long = 0
)

enum class RiskLevel {
    LOW, MODERATE, HIGH
}

enum class AiProviderType {
    CLOUD,
    SYSTEM_AI,
    CUSTOM_LOCAL,
    AUTO
}

interface AiProvider {
    val type: AiProviderType
    val displayName: String
    suspend fun generateAnalysis(input: AnalysisInput): AnalysisResult
    fun isAvailable(): Boolean
}
