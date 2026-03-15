package com.medreminder.ai.local

import com.medreminder.ai.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudClaudeAiProvider @Inject constructor() : AiProvider {

    override val type = AiProviderType.CLOUD
    override val displayName = "Cloud AI (Claude)"

    private var apiKey: String? = null

    fun configure(apiKey: String) {
        this.apiKey = apiKey
    }

    override fun isAvailable(): Boolean = !apiKey.isNullOrBlank()

    override suspend fun generateAnalysis(input: AnalysisInput): AnalysisResult {
        val startTime = System.currentTimeMillis()
        val prompt = buildPrompt(input)

        // Placeholder: In production, this would call the Claude API
        // For now, generate a structured local response
        val result = generateLocalFallbackAnalysis(input)

        return result.copy(
            providerUsed = AiProviderType.CLOUD,
            latencyMs = System.currentTimeMillis() - startTime
        )
    }

    private fun buildPrompt(input: AnalysisInput): String {
        val medsSection = input.medications.joinToString("\n") { med ->
            "- ${med.name} ${med.dosage} (${med.form}, ${med.frequency})" +
                    if (med.instructions.isNotBlank()) " [${med.instructions}]" else ""
        }

        return """
            |Analyze this medication adherence data and provide health insights.
            |
            |Medications:
            |$medsSection
            |
            |Adherence Rate: ${String.format("%.1f", input.adherenceRate)}%
            |Period: ${input.periodDays} days
            |Total Doses: ${input.totalDoses}
            |Taken: ${input.takenDoses}
            |Missed: ${input.missedDoses}
            |Skipped: ${input.skippedDoses}
            |Current Streak: ${input.currentStreak} days
            |
            |Analysis Type: ${input.analysisType.name}
            |
            |Provide:
            |1. A brief summary of adherence
            |2. Key insights
            |3. Actionable recommendations
            |4. Risk level assessment (LOW, MODERATE, HIGH)
            |
            |Respond in JSON format:
            |{"summary": "...", "insights": ["..."], "recommendations": ["..."], "riskLevel": "LOW|MODERATE|HIGH"}
        """.trimMargin()
    }

    private fun generateLocalFallbackAnalysis(input: AnalysisInput): AnalysisResult {
        val riskLevel = when {
            input.adherenceRate >= 90f -> RiskLevel.LOW
            input.adherenceRate >= 70f -> RiskLevel.MODERATE
            else -> RiskLevel.HIGH
        }

        val summary = when (input.analysisType) {
            AnalysisType.DAILY -> buildDailySummary(input, riskLevel)
            AnalysisType.WEEKLY -> buildWeeklySummary(input, riskLevel)
        }

        val insights = buildInsights(input)
        val recommendations = buildRecommendations(input, riskLevel)

        return AnalysisResult(
            summary = summary,
            insights = insights,
            recommendations = recommendations,
            riskLevel = riskLevel
        )
    }

    private fun buildDailySummary(input: AnalysisInput, riskLevel: RiskLevel): String {
        return when (riskLevel) {
            RiskLevel.LOW -> "Great job! You've taken ${input.takenDoses} of ${input.totalDoses} doses today. Your adherence rate of ${String.format("%.0f", input.adherenceRate)}% shows excellent medication management."
            RiskLevel.MODERATE -> "You've taken ${input.takenDoses} of ${input.totalDoses} doses. Your adherence rate of ${String.format("%.0f", input.adherenceRate)}% could be improved. Consider setting additional reminders."
            RiskLevel.HIGH -> "You've missed several doses today (${input.missedDoses} missed). With an adherence rate of ${String.format("%.0f", input.adherenceRate)}%, it's important to prioritize taking your medications on time."
        }
    }

    private fun buildWeeklySummary(input: AnalysisInput, riskLevel: RiskLevel): String {
        return when (riskLevel) {
            RiskLevel.LOW -> "Excellent week! Your ${String.format("%.0f", input.adherenceRate)}% adherence rate over ${input.periodDays} days shows strong medication management. You've maintained a ${input.currentStreak}-day streak."
            RiskLevel.MODERATE -> "Your weekly adherence of ${String.format("%.0f", input.adherenceRate)}% shows room for improvement. You missed ${input.missedDoses} doses this period."
            RiskLevel.HIGH -> "This week needs attention. With ${String.format("%.0f", input.adherenceRate)}% adherence and ${input.missedDoses} missed doses, consider reviewing your medication schedule."
        }
    }

    private fun buildInsights(input: AnalysisInput): List<String> {
        val insights = mutableListOf<String>()

        if (input.currentStreak > 0) {
            insights.add("You're on a ${input.currentStreak}-day streak of taking all your medications.")
        }
        if (input.missedDoses > 0) {
            insights.add("You've missed ${input.missedDoses} doses in the last ${input.periodDays} days.")
        }
        if (input.skippedDoses > 0) {
            insights.add("You've intentionally skipped ${input.skippedDoses} doses.")
        }
        if (input.adherenceRate >= 95f) {
            insights.add("Your adherence is in the top tier - keep it up!")
        }
        if (input.medications.size > 3) {
            insights.add("Managing ${input.medications.size} medications requires careful scheduling.")
        }

        return insights.ifEmpty { listOf("Keep tracking your medications for more detailed insights.") }
    }

    private fun buildRecommendations(input: AnalysisInput, riskLevel: RiskLevel): List<String> {
        val recommendations = mutableListOf<String>()

        when (riskLevel) {
            RiskLevel.HIGH -> {
                recommendations.add("Set up additional reminders for frequently missed doses.")
                recommendations.add("Consider adding a caregiver to help track your medications.")
                recommendations.add("Talk to your healthcare provider about simplifying your medication schedule.")
            }
            RiskLevel.MODERATE -> {
                recommendations.add("Try taking medications at the same time each day to build a habit.")
                recommendations.add("Use the snooze feature instead of missing doses entirely.")
            }
            RiskLevel.LOW -> {
                recommendations.add("Maintain your excellent routine!")
                if (input.medications.any { it.instructions.isNotBlank() }) {
                    recommendations.add("Continue following the special instructions for your medications.")
                }
            }
        }

        return recommendations
    }
}
