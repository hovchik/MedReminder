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
    /** The specific person this analysis is for (self or a family member). */
    val targetUserName: String = "",
    val targetUserAge: Int = 0,
    val isSelfAnalysis: Boolean = true,
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
    val latencyMs: Long = 0,
    /** Non-null when the cloud call failed and local fallback was used. */
    val cloudError: String? = null
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

    /**
     * Send a raw prompt to the provider and return the raw text response.
     * Used for medication-specific deep analysis where a custom prompt is built.
     * Returns null if the provider cannot handle raw completions.
     */
    suspend fun generateRawCompletion(prompt: String): String? = null
}

// --- Medication-specific AI analysis ---

/** Input for a single-medication deep analysis */
data class MedicationAnalysisInput(
    val medicationName: String,
    val dosage: String,
    val form: String,
    val frequency: String,
    val instructions: String,
    val scheduledTimes: List<String>,
    val isEmergency: Boolean,
    val currentStock: Int,
    val refillThreshold: Int,
    val needsRefill: Boolean,
    val notes: String,
    val assignedTo: String,
    // Adherence data
    val takenCount: Int,
    val missedCount: Int,
    val skippedCount: Int,
    val snoozedCount: Int,
    val adherenceRate: Float,
    val averageDelayMinutes: Float,
    // Period context
    val periodDays: Int,
    val currentStreak: Int,
    // Timeline
    val recentDoseEvents: List<DoseEvent> = emptyList(),
    val timeOfDayBreakdown: TimeOfDayBreakdown? = null,
    // User context
    val userName: String = "",
    val userAge: Int = 0
)

/** Result of a single-medication AI analysis */
data class MedicationAnalysisResult(
    val medicationInfo: MedicationInfoSection,
    val dosingPredictions: DosingPredictionSection,
    val providerUsed: AiProviderType = AiProviderType.CLOUD,
    val latencyMs: Long = 0
)

data class MedicationInfoSection(
    val description: String = "",            // What this medication is / does
    val drugClass: String = "",              // e.g. "NSAID", "Beta-blocker"
    val genericName: String = "",       // Generic / INN name
    val brandNames: List<String> = emptyList(), // Known brand names
    val commonUses: List<String> = emptyList(),       // e.g. ["Pain relief", "Inflammation"]
    val mechanismOfAction: String = "", // How the drug works in the body
    val sideEffects: List<String> = emptyList(),      // Common side effects
    val seriousSideEffects: List<String> = emptyList(), // Rare but serious side effects
    val importantWarnings: List<String> = emptyList(), // Critical warnings
    val contraindications: List<String> = emptyList(), // When NOT to take this drug
    val interactions: List<String> = emptyList(),      // Known drug interactions to watch
    val foodInteractions: List<String> = emptyList(), // Food/drink interactions
    val pregnancyCategory: String = "", // FDA pregnancy safety category
    val storageInstructions: String = "", // How to store the medication
    val halfLife: String = "",          // Approximate half-life
    val onsetOfAction: String = "",     // How long until it starts working
    val missedDoseAdvice: String = ""   // What to do if a dose is missed
)

data class DosingPredictionSection(
    val adherenceForecast: String = "",      // e.g. "Based on your pattern, you're likely to miss evening doses"
    val optimizationTips: List<String> = emptyList(),  // Personalized timing suggestions
    val stockForecast: String = "",          // e.g. "At current rate, you'll run out in ~12 days"
    val riskAssessment: String = ""          // Overall risk summary for this specific med
)

/** Represents a body region the medication targets */
enum class BodyRegion(val displayName: String) {
    HEAD("Head / Brain"),
    EYES("Eyes"),
    EARS("Ears"),
    THROAT("Throat"),
    HEART("Heart"),
    LUNGS("Lungs"),
    STOMACH("Stomach / GI"),
    LIVER("Liver"),
    KIDNEYS("Kidneys"),
    JOINTS("Joints / Muscles"),
    SKIN("Skin"),
    BLOOD("Blood / Circulation"),
    IMMUNE("Immune System"),
    HORMONES("Hormonal / Endocrine"),
    NERVOUS_SYSTEM("Nervous System"),
    BONES("Bones"),
    FULL_BODY("Full Body / Systemic")
}
