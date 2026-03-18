package com.medreminder.ai.local

import android.util.Log
import com.medreminder.ai.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

enum class CloudAiService(val displayName: String) {
    CLAUDE("Claude"),
    CHATGPT("ChatGPT"),
    DEEPSEEK("DeepSeek")
}

@Singleton
class CloudAiProvider @Inject constructor() : AiProvider {

    override val type = AiProviderType.CLOUD
    override val displayName: String
        get() = "Cloud AI (${activeService.displayName})"

    var activeService: CloudAiService = CloudAiService.CLAUDE
        private set

    private var apiKey: String? = null

    fun configure(apiKey: String, service: CloudAiService = CloudAiService.CLAUDE) {
        this.apiKey = apiKey
        this.activeService = service
    }

    fun setService(service: CloudAiService) {
        this.activeService = service
    }

    fun setApiKey(key: String) {
        this.apiKey = key
    }

    fun getApiKey(): String? = apiKey

    override fun isAvailable(): Boolean = true // Always available; falls back to local analysis if no API key

    override suspend fun generateAnalysis(input: AnalysisInput): AnalysisResult {
        val startTime = System.currentTimeMillis()
        val key = apiKey

        if (key.isNullOrBlank()) {
            return generateLocalFallbackAnalysis(input).copy(
                providerUsed = AiProviderType.CLOUD,
                latencyMs = System.currentTimeMillis() - startTime
            )
        }

        return try {
            val prompt = buildPrompt(input)
            val responseText = when (activeService) {
                CloudAiService.CLAUDE -> callClaudeApi(key, prompt)
                CloudAiService.CHATGPT -> callChatGptApi(key, prompt)
                CloudAiService.DEEPSEEK -> callDeepSeekApi(key, prompt)
            }
            parseApiResponse(responseText, activeService).copy(
                providerUsed = AiProviderType.CLOUD,
                latencyMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            Log.e(TAG, "Cloud API call failed (${activeService.name}), using fallback", e)
            generateLocalFallbackAnalysis(input).copy(
                providerUsed = AiProviderType.CLOUD,
                latencyMs = System.currentTimeMillis() - startTime
            )
        }
    }

    private fun callClaudeApi(apiKey: String, prompt: String): String {
        val url = URL("https://api.anthropic.com/v1/messages")
        val body = JSONObject().apply {
            put("model", "claude-sonnet-4-6-20250514")
            put("max_tokens", 1024)
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        return makeHttpPost(url, body.toString(), mapOf(
            "x-api-key" to apiKey,
            "anthropic-version" to "2023-06-01",
            "Content-Type" to "application/json"
        )).let { response ->
            val json = JSONObject(response)
            json.getJSONArray("content").getJSONObject(0).getString("text")
        }
    }

    private fun callChatGptApi(apiKey: String, prompt: String): String {
        val url = URL("https://api.openai.com/v1/chat/completions")
        val body = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("max_tokens", 1024)
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        return makeHttpPost(url, body.toString(), mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json"
        )).let { response ->
            val json = JSONObject(response)
            json.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
        }
    }

    private fun callDeepSeekApi(apiKey: String, prompt: String): String {
        val url = URL("https://api.deepseek.com/chat/completions")
        val body = JSONObject().apply {
            put("model", "deepseek-chat")
            put("max_tokens", 1024)
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        return makeHttpPost(url, body.toString(), mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json"
        )).let { response ->
            val json = JSONObject(response)
            json.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
        }
    }

    private fun makeHttpPost(url: URL, body: String, headers: Map<String, String>): String {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 60_000
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }

        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                val errorBody = conn.errorStream?.let {
                    BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
                } ?: ""
                throw Exception("API returned HTTP $responseCode: $errorBody")
            }

            return BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseApiResponse(responseText: String, service: CloudAiService): AnalysisResult {
        return try {
            // Try to extract JSON from the response
            val jsonStr = extractJson(responseText)
            val json = JSONObject(jsonStr)
            AnalysisResult(
                summary = json.optString("summary", "Analysis complete."),
                insights = json.optJSONArray("insights")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                recommendations = json.optJSONArray("recommendations")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                riskLevel = try {
                    RiskLevel.valueOf(json.optString("riskLevel", "LOW").uppercase())
                } catch (_: Exception) { RiskLevel.LOW }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse structured response from ${service.name}, using raw text", e)
            AnalysisResult(summary = responseText.take(500))
        }
    }

    private fun extractJson(text: String): String {
        // Find JSON object in the response text
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) {
            text.substring(start, end + 1)
        } else {
            text
        }
    }

    private fun buildPrompt(input: AnalysisInput): String {
        val medsSection = input.medications.joinToString("\n") { med ->
            val parts = mutableListOf<String>()
            parts.add("- ${med.name} ${med.dosage} (${med.form}, ${med.frequency})")
            if (med.scheduledTimes.isNotEmpty()) {
                parts.add("  Schedule: ${med.scheduledTimes.joinToString(", ")}")
            }
            if (med.instructions.isNotBlank()) {
                parts.add("  Instructions: ${med.instructions}")
            }
            parts.add("  Adherence: ${String.format("%.0f", med.adherenceRate)}% (taken ${med.takenCount}, missed ${med.missedCount}, skipped ${med.skippedCount})")
            if (med.snoozedCount > 0) {
                parts.add("  Snoozed ${med.snoozedCount} times")
            }
            if (med.averageDelayMinutes > 0) {
                parts.add("  Avg delay: ${String.format("%.0f", med.averageDelayMinutes)} min after scheduled time")
            }
            if (med.isEmergency) {
                parts.add("  *** EMERGENCY/CRITICAL medication ***")
            }
            if (med.needsRefill) {
                parts.add("  LOW STOCK: ${med.currentStock} remaining (refill at ${med.refillThreshold})")
            } else if (med.currentStock > 0) {
                parts.add("  Stock: ${med.currentStock} remaining")
            }
            if (med.assignedTo.isNotBlank()) {
                parts.add("  Assigned to: ${med.assignedTo}")
            }
            parts.joinToString("\n")
        }

        val timeOfDaySection = input.timeOfDayBreakdown?.let { tod ->
            """
            |
            |Time-of-Day Adherence:
            |  Morning (5am-12pm): ${String.format("%.0f", tod.morningRate)}%
            |  Afternoon (12pm-5pm): ${String.format("%.0f", tod.afternoonRate)}%
            |  Evening (5pm-9pm): ${String.format("%.0f", tod.eveningRate)}%
            |  Night (9pm-5am): ${String.format("%.0f", tod.nightRate)}%
            """.trimMargin()
        } ?: ""

        val weeklySection = if (input.weeklyBreakdown.isNotEmpty()) {
            "\n\nDaily Breakdown:\n" + input.weeklyBreakdown.joinToString("\n") { day ->
                "  ${day.dayName}: ${day.taken}/${day.total} (${String.format("%.0f", day.rate)}%)"
            }
        } else ""

        val userSection = if (input.userName.isNotBlank() || input.userAge > 0) {
            val parts = mutableListOf<String>()
            if (input.userName.isNotBlank()) parts.add("Name: ${input.userName}")
            if (input.userAge > 0) parts.add("Age: ${input.userAge}")
            "\n\nPatient: ${parts.joinToString(", ")}"
        } else ""

        val contextSection = buildList {
            if (input.totalSnoozedCount > 0) add("Total snoozes: ${input.totalSnoozedCount}")
            if (input.averageDelayMinutes > 0) add("Avg dose delay: ${String.format("%.0f", input.averageDelayMinutes)} min")
            if (input.longestStreak > 0) add("Longest streak ever: ${input.longestStreak} days")
            if (input.hasCaregivers) add("Has ${input.caregiverCount} caregiver(s) monitoring")
            if (input.familyMemberCount > 0) add("Managing meds for ${input.familyMemberCount} family member(s)")
            if (input.worstMedication != null && input.medications.size > 1)
                add("Lowest adherence: ${input.worstMedication}")
            if (input.bestMedication != null && input.medications.size > 1)
                add("Highest adherence: ${input.bestMedication}")
        }.let {
            if (it.isNotEmpty()) "\n\nAdditional Context:\n" + it.joinToString("\n") { s -> "  $s" }
            else ""
        }

        return """
            |You are a medication adherence analyst for a health app. Analyze the following data and provide personalized, actionable health insights. Be specific and reference the actual medication names, patterns, and numbers.
            |$userSection
            |
            |Medications:
            |$medsSection
            |
            |Overall Adherence: ${String.format("%.1f", input.adherenceRate)}%
            |Period: ${if (input.periodDays == 1) "Today" else "Last ${input.periodDays} days"}
            |Total Doses: ${input.totalDoses} | Taken: ${input.takenDoses} | Missed: ${input.missedDoses} | Skipped: ${input.skippedDoses}
            |Current Streak: ${input.currentStreak} days
            |$timeOfDaySection$weeklySection$contextSection
            |
            |Provide a thorough analysis with:
            |1. A personalized summary (2-3 sentences, reference specific medications by name)
            |2. 3-5 specific insights based on the data patterns (timing, specific meds, trends)
            |3. 3-5 actionable recommendations tailored to the observed patterns
            |4. Risk level: LOW (≥90%), MODERATE (70-89%), HIGH (<70%)
            |
            |Respond ONLY with valid JSON:
            |{"summary": "...", "insights": ["...", "..."], "recommendations": ["...", "..."], "riskLevel": "LOW|MODERATE|HIGH"}
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
        if (input.longestStreak > input.currentStreak && input.longestStreak > 0) {
            insights.add("Your best streak was ${input.longestStreak} days — you can beat it!")
        }
        if (input.missedDoses > 0) {
            insights.add("You've missed ${input.missedDoses} doses in the last ${input.periodDays} day(s).")
        }
        if (input.skippedDoses > 0) {
            insights.add("You've intentionally skipped ${input.skippedDoses} doses.")
        }
        if (input.totalSnoozedCount > 0) {
            insights.add("You snoozed ${input.totalSnoozedCount} time(s) — consider adjusting reminder times.")
        }
        if (input.averageDelayMinutes > 15) {
            insights.add("On average, you take doses ${String.format("%.0f", input.averageDelayMinutes)} minutes late.")
        }
        if (input.adherenceRate >= 95f) {
            insights.add("Your adherence is in the top tier — keep it up!")
        }
        // Time-of-day insights
        input.timeOfDayBreakdown?.let { tod ->
            val worst = listOf("Morning" to tod.morningRate, "Afternoon" to tod.afternoonRate,
                "Evening" to tod.eveningRate, "Night" to tod.nightRate)
                .filter { it.second > 0f }
                .minByOrNull { it.second }
            if (worst != null && worst.second < 80f) {
                insights.add("${worst.first} doses have the lowest adherence (${String.format("%.0f", worst.second)}%).")
            }
        }
        // Per-medication insights
        val worstMed = input.medications.filter { it.takenCount + it.missedCount > 0 }
            .minByOrNull { it.adherenceRate }
        if (worstMed != null && worstMed.adherenceRate < 80f && input.medications.size > 1) {
            insights.add("${worstMed.name} has the lowest adherence at ${String.format("%.0f", worstMed.adherenceRate)}%.")
        }
        // Stock warning
        val lowStock = input.medications.filter { it.needsRefill }
        if (lowStock.isNotEmpty()) {
            insights.add("${lowStock.joinToString(", ") { it.name }} need(s) a refill soon.")
        }
        if (input.medications.size > 3) {
            insights.add("Managing ${input.medications.size} medications requires careful scheduling.")
        }

        return insights.take(5).ifEmpty { listOf("Keep tracking your medications for more detailed insights.") }
    }

    private fun buildRecommendations(input: AnalysisInput, riskLevel: RiskLevel): List<String> {
        val recommendations = mutableListOf<String>()

        when (riskLevel) {
            RiskLevel.HIGH -> {
                recommendations.add("Set up additional reminders for frequently missed doses.")
                if (!input.hasCaregivers) {
                    recommendations.add("Consider adding a caregiver to help track your medications.")
                }
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

        // Enriched recommendations
        if (input.totalSnoozedCount > 3) {
            recommendations.add("You snooze often — consider rescheduling medications to a more convenient time.")
        }
        input.timeOfDayBreakdown?.let { tod ->
            if (tod.eveningRate < 70f && tod.morningRate > 85f) {
                recommendations.add("Evening doses are often missed. Try setting a dinner-time reminder.")
            }
            if (tod.morningRate < 70f && tod.eveningRate > 85f) {
                recommendations.add("Morning doses need attention. Try linking them to your morning routine.")
            }
        }
        val emergencyMissed = input.medications.filter { it.isEmergency && it.missedCount > 0 }
        if (emergencyMissed.isNotEmpty()) {
            recommendations.add("Critical medication ${emergencyMissed.first().name} was missed — please prioritize this.")
        }
        val refillNeeded = input.medications.filter { it.needsRefill }
        if (refillNeeded.isNotEmpty()) {
            recommendations.add("Refill ${refillNeeded.joinToString(", ") { it.name }} before running out.")
        }

        return recommendations.take(5)
    }

    companion object {
        private const val TAG = "CloudAiProvider"
    }
}
