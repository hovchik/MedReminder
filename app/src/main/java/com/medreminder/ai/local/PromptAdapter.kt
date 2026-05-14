package com.medreminder.ai.local

import com.medreminder.ai.*
import com.medreminder.ai.AiLanguageHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptAdapter @Inject constructor() {

    fun adaptPrompt(input: AnalysisInput, supportsStructuredJson: Boolean, isThinkingModel: Boolean = false): String {
        val medsSection = input.medications.joinToString("\n") { med ->
            val base = "- ${med.name} ${med.dosage} (${med.form}, ${med.frequency})"
            val extra = buildList {
                if (med.instructions.isNotBlank()) add("[${med.instructions}]")
                if (med.takenCount + med.missedCount > 0) {
                    add("adherence ${String.format("%.0f", med.adherenceRate)}%")
                }
                if (med.isEmergency) add("CRITICAL")
                if (med.needsRefill) add("LOW STOCK: ${med.currentStock}")
            }
            if (extra.isNotEmpty()) "$base — ${extra.joinToString(", ")}" else base
        }

        val timeOfDayInfo = input.timeOfDayBreakdown?.let { tod ->
            "\nTime-of-day: Morning ${String.format("%.0f", tod.morningRate)}%, " +
            "Afternoon ${String.format("%.0f", tod.afternoonRate)}%, " +
            "Evening ${String.format("%.0f", tod.eveningRate)}%, " +
            "Night ${String.format("%.0f", tod.nightRate)}%"
        } ?: ""

        val doseEventsInfo = if (input.recentDoseEvents.isNotEmpty()) {
            "\nRecent events:\n" + input.recentDoseEvents.take(5).joinToString("\n") { ev ->
                val delay = if (ev.delayMinutes > 0) " (${ev.delayMinutes}min late)" else ""
                "  ${ev.scheduledTime} ${ev.medicationName} ${ev.status}$delay"
            }
        } else ""

        val basePrompt = """
            |Analyze medication adherence data and provide health insights.
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
            |Snoozed: ${input.totalSnoozedCount}
            |Current Streak: ${input.currentStreak} days
            |Longest Streak: ${input.longestStreak} days
            |Avg Dose Delay: ${String.format("%.0f", input.averageDelayMinutes)} min$timeOfDayInfo$doseEventsInfo
            |Analysis Type: ${input.analysisType.name}
        """.trimMargin()

        val languageInstruction = AiLanguageHelper.getLanguageInstruction()

        // For thinking models (e.g. Qwen3), append /no_think to disable the
        // internal reasoning phase and avoid wasting tokens on <think> blocks.
        val noThinkSuffix = if (isThinkingModel) "\n/no_think" else ""

        return if (supportsStructuredJson) {
            """
                |$basePrompt
                |
                |Respond in JSON:
                |{"summary": "...", "insights": ["..."], "recommendations": ["..."], "riskLevel": "LOW|MODERATE|HIGH"}$languageInstruction$noThinkSuffix
            """.trimMargin()
        } else {
            """
                |$basePrompt
                |
                |Provide:
                |SUMMARY: A brief summary of adherence patterns
                |INSIGHTS: Key observations (one per line, prefixed with "- ")
                |RECOMMENDATIONS: Actionable suggestions (one per line, prefixed with "- ")
                |RISK: LOW, MODERATE, or HIGH$languageInstruction$noThinkSuffix
            """.trimMargin()
        }
    }

    /**
     * Strip Qwen3-style `<think>...</think>` reasoning blocks from the response.
     * Safe to call on any response — returns it unchanged if no thinking tags are found.
     */
    fun stripThinkingTokens(response: String): String {
        return response.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
    }

    fun parseResponse(rawResponse: String, input: AnalysisInput): AnalysisResult {
        // Try JSON parsing first
        try {
            return parseJsonResponse(rawResponse)
        } catch (_: Exception) {
            // Fall through to plain text parsing
        }

        // Try structured plain text parsing
        try {
            return parsePlainTextResponse(rawResponse)
        } catch (_: Exception) {
            // Fall through to fallback
        }

        // Fallback: generate based on input data
        return generateFallbackResult(input, rawResponse)
    }

    private fun parseJsonResponse(response: String): AnalysisResult {
        // Simple JSON extraction without external library
        val jsonStart = response.indexOf('{')
        val jsonEnd = response.lastIndexOf('}')
        if (jsonStart < 0 || jsonEnd < 0) throw IllegalArgumentException("No JSON found")

        val json = response.substring(jsonStart, jsonEnd + 1)

        val summary = extractJsonString(json, "summary")
        val insights = extractJsonArray(json, "insights")
        val recommendations = extractJsonArray(json, "recommendations")
        val riskStr = extractJsonString(json, "riskLevel")

        val riskLevel = try {
            RiskLevel.valueOf(riskStr.uppercase())
        } catch (_: Exception) {
            RiskLevel.LOW
        }

        return AnalysisResult(
            summary = summary,
            insights = insights,
            recommendations = recommendations,
            riskLevel = riskLevel
        )
    }

    private fun parsePlainTextResponse(response: String): AnalysisResult {
        val lines = response.lines()
        var summary = ""
        val insights = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        var riskLevel = RiskLevel.LOW
        var currentSection = ""

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("SUMMARY:", ignoreCase = true) -> {
                    currentSection = "summary"
                    summary = trimmed.substringAfter(":").trim()
                }
                trimmed.startsWith("INSIGHTS:", ignoreCase = true) -> {
                    currentSection = "insights"
                }
                trimmed.startsWith("RECOMMENDATIONS:", ignoreCase = true) -> {
                    currentSection = "recommendations"
                }
                trimmed.startsWith("RISK:", ignoreCase = true) -> {
                    val risk = trimmed.substringAfter(":").trim().uppercase()
                    riskLevel = try { RiskLevel.valueOf(risk) } catch (_: Exception) { RiskLevel.LOW }
                }
                trimmed.startsWith("- ") -> {
                    val item = trimmed.removePrefix("- ").trim()
                    when (currentSection) {
                        "insights" -> insights.add(item)
                        "recommendations" -> recommendations.add(item)
                    }
                }
                currentSection == "summary" && trimmed.isNotBlank() -> {
                    summary += " $trimmed"
                }
            }
        }

        if (summary.isBlank()) throw IllegalArgumentException("No summary found")

        return AnalysisResult(
            summary = summary.trim(),
            insights = insights,
            recommendations = recommendations,
            riskLevel = riskLevel
        )
    }

    private fun generateFallbackResult(input: AnalysisInput, rawResponse: String): AnalysisResult {
        val riskLevel = when {
            input.adherenceRate >= 90f -> RiskLevel.LOW
            input.adherenceRate >= 70f -> RiskLevel.MODERATE
            else -> RiskLevel.HIGH
        }

        val summary = if (rawResponse.isNotBlank() && rawResponse.length > 20) {
            rawResponse.take(500)
        } else {
            "Your adherence rate is ${String.format("%.0f", input.adherenceRate)}% " +
                    "with ${input.takenDoses} of ${input.totalDoses} doses taken over ${input.periodDays} days."
        }

        return AnalysisResult(
            summary = summary,
            insights = listOf("Adherence rate: ${String.format("%.0f", input.adherenceRate)}%"),
            recommendations = when (riskLevel) {
                RiskLevel.HIGH -> listOf("Consider setting up additional reminders", "Talk to your healthcare provider")
                RiskLevel.MODERATE -> listOf("Try to maintain a consistent medication schedule")
                RiskLevel.LOW -> listOf("Keep up the great work!")
            },
            riskLevel = riskLevel
        )
    }

    private fun extractJsonString(json: String, key: String): String {
        val pattern = """"$key"\s*:\s*"([^"]*(?:\\.[^"]*)*)"""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)?.replace("\\\"", "\"") ?: ""
    }

    private fun extractJsonArray(json: String, key: String): List<String> {
        val pattern = """"$key"\s*:\s*\[([^\]]*)\]""".toRegex()
        val match = pattern.find(json)?.groupValues?.get(1) ?: return emptyList()
        return """"([^"]*(?:\\.[^"]*)*)"""".toRegex().findAll(match).map {
            it.groupValues[1].replace("\\\"", "\"")
        }.toList()
    }
}
