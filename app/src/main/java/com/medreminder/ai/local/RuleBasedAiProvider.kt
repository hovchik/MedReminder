package com.medreminder.ai.local

import com.medreminder.ai.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A rule-based AI provider that generates insights using deterministic data analysis.
 * Used as a fallback when System AI is unavailable and no cloud/local model is configured.
 * Always available — requires no model, API key, or device capabilities.
 */
@Singleton
class RuleBasedAiProvider @Inject constructor() : AiProvider {

    override val type = AiProviderType.SYSTEM_AI
    override val displayName = "Rule-Based Analysis"

    override fun isAvailable(): Boolean = true

    override suspend fun generateAnalysis(input: AnalysisInput): AnalysisResult {
        val startTime = System.currentTimeMillis()

        val riskLevel = assessRisk(input)
        val summary = buildSummary(input)
        val insights = buildInsights(input)
        val recommendations = buildRecommendations(input, riskLevel)

        return AnalysisResult(
            summary = summary,
            insights = insights,
            recommendations = recommendations,
            riskLevel = riskLevel,
            providerUsed = AiProviderType.SYSTEM_AI,
            latencyMs = System.currentTimeMillis() - startTime
        )
    }

    private fun assessRisk(input: AnalysisInput): RiskLevel {
        val hasCriticalMeds = input.medications.any { it.isEmergency }
        val hasLowStock = input.medications.any { it.needsRefill }

        return when {
            input.adherenceRate < 50f && hasCriticalMeds -> RiskLevel.HIGH
            input.adherenceRate < 60f -> RiskLevel.HIGH
            input.adherenceRate < 70f && hasCriticalMeds -> RiskLevel.HIGH
            input.adherenceRate < 80f -> RiskLevel.MODERATE
            hasLowStock -> RiskLevel.MODERATE
            input.missedDoses > 0 && hasCriticalMeds -> RiskLevel.MODERATE
            else -> RiskLevel.LOW
        }
    }

    private fun buildSummary(input: AnalysisInput): String {
        val userName = input.targetUserName.ifBlank { input.userName }.ifBlank { "You" }
        val periodLabel = if (input.analysisType == AnalysisType.DAILY) "today" else "this week"
        val rate = String.format("%.0f", input.adherenceRate)

        val base = if (input.isSelfAnalysis) {
            "Your adherence rate $periodLabel is $rate%."
        } else {
            "$userName's adherence rate $periodLabel is $rate%."
        }

        val detail = when {
            input.totalDoses == 0 -> " No doses were scheduled."
            input.adherenceRate >= 95f -> " Excellent — all doses are on track!"
            input.adherenceRate >= 80f -> " ${input.takenDoses} of ${input.totalDoses} doses taken."
            input.adherenceRate >= 60f -> " ${input.missedDoses} dose(s) missed out of ${input.totalDoses}."
            else -> " ${input.missedDoses} of ${input.totalDoses} doses were missed — this needs attention."
        }

        return base + detail
    }

    private fun buildInsights(input: AnalysisInput): List<String> {
        val insights = mutableListOf<String>()

        // Adherence insight
        when {
            input.adherenceRate >= 95f -> insights.add("Adherence is excellent at ${String.format("%.0f", input.adherenceRate)}%.")
            input.adherenceRate >= 80f -> insights.add("Adherence is good at ${String.format("%.0f", input.adherenceRate)}%, with room for improvement.")
            input.adherenceRate >= 60f -> insights.add("Adherence is moderate at ${String.format("%.0f", input.adherenceRate)}%. Several doses are being missed.")
            else -> insights.add("Adherence is low at ${String.format("%.0f", input.adherenceRate)}%. This may affect treatment effectiveness.")
        }

        // Streak insight
        if (input.currentStreak > 1) {
            insights.add("Currently on a ${input.currentStreak}-day streak of full adherence.")
        } else if (input.currentStreak == 0 && input.totalDoses > 0) {
            insights.add("The adherence streak was broken. Missing doses interrupts treatment consistency.")
        }

        // Time-of-day analysis
        input.timeOfDayBreakdown?.let { tod ->
            val worstTime = listOf(
                "morning" to tod.morningRate,
                "afternoon" to tod.afternoonRate,
                "evening" to tod.eveningRate,
                "night" to tod.nightRate
            ).filter { it.second > 0f }.minByOrNull { it.second }

            if (worstTime != null && worstTime.second < 80f) {
                insights.add("${worstTime.first.replaceFirstChar { it.uppercase() }} doses have the lowest adherence at ${String.format("%.0f", worstTime.second)}%.")
            }
        }

        // Snoozed insight
        if (input.totalSnoozedCount > 2) {
            insights.add("${input.totalSnoozedCount} doses were snoozed, suggesting reminder timing may need adjustment.")
        }

        // Per-medication insights
        input.medications.filter { it.isEmergency && it.missedCount > 0 }.forEach { med ->
            insights.add("${med.name} is marked as critical and has ${med.missedCount} missed dose(s).")
        }

        input.medications.filter { it.needsRefill }.forEach { med ->
            insights.add("${med.name} is running low on stock (${med.currentStock} remaining).")
        }

        // Worst medication
        val worstMed = input.medications
            .filter { it.takenCount + it.missedCount > 0 }
            .minByOrNull { it.adherenceRate }
        if (worstMed != null && worstMed.adherenceRate < 80f) {
            insights.add("${worstMed.name} has the lowest adherence at ${String.format("%.0f", worstMed.adherenceRate)}%.")
        }

        return insights.take(5)
    }

    private fun buildRecommendations(input: AnalysisInput, riskLevel: RiskLevel): List<String> {
        val recs = mutableListOf<String>()

        when (riskLevel) {
            RiskLevel.HIGH -> {
                recs.add("Consider discussing your medication schedule with your healthcare provider.")
                if (input.missedDoses > input.takenDoses) {
                    recs.add("Try setting additional reminders or asking a caregiver for help.")
                }
            }
            RiskLevel.MODERATE -> {
                recs.add("Try to take doses at the same time each day to build a routine.")
            }
            RiskLevel.LOW -> {
                recs.add("Great job maintaining your medication schedule!")
            }
        }

        // Time-of-day recommendations
        input.timeOfDayBreakdown?.let { tod ->
            if (tod.morningRate < 70f && tod.morningRate > 0f) {
                recs.add("Morning doses are frequently missed — try linking them to breakfast or another daily habit.")
            }
            if (tod.eveningRate < 70f && tod.eveningRate > 0f) {
                recs.add("Evening doses need attention — set a reminder before your usual bedtime.")
            }
            if (tod.nightRate < 70f && tod.nightRate > 0f) {
                recs.add("Night-time doses are often missed — consider a bedside reminder or alarm.")
            }
        }

        // Snooze recommendation
        if (input.totalSnoozedCount > 3) {
            recs.add("Frequent snoozing suggests the reminder times may not fit your routine. Consider adjusting your schedule.")
        }

        // Refill recommendations
        val refillNeeded = input.medications.filter { it.needsRefill }
        if (refillNeeded.isNotEmpty()) {
            recs.add("Refill needed for: ${refillNeeded.joinToString(", ") { it.name }}.")
        }

        // Caregiver recommendation for high risk
        if (riskLevel == RiskLevel.HIGH && !input.hasCaregivers) {
            recs.add("Consider adding a caregiver who can be notified about missed doses.")
        }

        return recs.take(5)
    }
}
