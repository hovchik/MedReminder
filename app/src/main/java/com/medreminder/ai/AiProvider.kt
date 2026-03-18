package com.medreminder.ai

data class AnalysisInput(
    val medications: List<MedicationSummary>,
    val adherenceRate: Float,
    val totalDoses: Int,
    val takenDoses: Int,
    val missedDoses: Int,
    val skippedDoses: Int,
    val currentStreak: Int,
    val longestStreak: Int = 0,
    val periodDays: Int,
    val weeklyBreakdown: List<DayBreakdown> = emptyList(),
    val analysisType: AnalysisType = AnalysisType.DAILY,
    // Enriched data
    val totalSnoozedCount: Int = 0,
    val averageDelayMinutes: Float = 0f,
    val timeOfDayBreakdown: TimeOfDayBreakdown? = null,
    val worstMedication: String? = null,
    val bestMedication: String? = null,
    val userName: String = "",
    val userAge: Int = 0,
    val hasCaregivers: Boolean = false,
    val caregiverCount: Int = 0,
    val familyMemberCount: Int = 0,
    // Recent dose event timeline for AI context
    val recentDoseEvents: List<DoseEvent> = emptyList()
)

/** A single dose event from the log — gives the AI a timeline view of what happened. */
data class DoseEvent(
    val medicationName: String,
    val scheduledTime: String, // formatted "8:00 AM"
    val status: String,        // taken, missed, skipped, snoozed
    val delayMinutes: Int = 0, // positive = late, 0 = on time
    val snoozeCount: Int = 0
)

data class MedicationSummary(
    val name: String,
    val dosage: String,
    val form: String,
    val frequency: String,
    val instructions: String = "",
    // Enriched fields
    val scheduledTimes: List<String> = emptyList(), // e.g. ["8:00 AM", "8:00 PM"]
    val isEmergency: Boolean = false,
    val currentStock: Int = 0,
    val refillThreshold: Int = 5,
    val needsRefill: Boolean = false,
    val takenCount: Int = 0,
    val missedCount: Int = 0,
    val skippedCount: Int = 0,
    val snoozedCount: Int = 0,
    val adherenceRate: Float = 0f,
    val averageDelayMinutes: Float = 0f, // avg delay for this specific medication
    val assignedTo: String = "" // empty = self
)

data class DayBreakdown(
    val dayName: String,
    val taken: Int,
    val total: Int,
    val rate: Float
)

data class TimeOfDayBreakdown(
    val morningRate: Float = 0f,   // 5:00-11:59
    val afternoonRate: Float = 0f, // 12:00-16:59
    val eveningRate: Float = 0f,   // 17:00-20:59
    val nightRate: Float = 0f      // 21:00-4:59
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
