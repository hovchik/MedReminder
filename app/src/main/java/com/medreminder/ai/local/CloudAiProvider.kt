package com.medreminder.ai.local

import android.util.Log
import com.medreminder.ai.*
import com.medreminder.ai.AiLanguageHelper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

enum class CloudAiService(val displayName: String) {
    CLAUDE("Claude"),
    CHATGPT("ChatGPT"),
    DEEPSEEK("DeepSeek"),
    GEMINI("Gemini")
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

    override fun isAvailable(): Boolean = !apiKey.isNullOrBlank()

    /**
     * Send a raw prompt and return the raw text response from the active cloud service.
     * Returns null if the API key is missing or the call fails.
     */
    override suspend fun generateRawCompletion(prompt: String): String? {
        val key = apiKey
        if (key.isNullOrBlank()) {
            Log.w(TAG, "No API key configured for ${activeService.name}, cannot call cloud AI")
            return null
        }
        return try {
            val rawResponse = withContext(Dispatchers.IO) {
                when (activeService) {
                    CloudAiService.CLAUDE -> callClaudeApi(key, prompt)
                    CloudAiService.CHATGPT -> callChatGptApi(key, prompt)
                    CloudAiService.DEEPSEEK -> callDeepSeekApi(key, prompt)
                    CloudAiService.GEMINI -> callGeminiApi(key, prompt)
                }
            }
            extractJson(rawResponse)
        } catch (e: Exception) {
            Log.e(TAG, "Cloud raw completion failed (${activeService.name})", e)
            null
        }
    }

    override suspend fun generateAnalysis(input: AnalysisInput): AnalysisResult {
        val startTime = System.currentTimeMillis()
        val key = apiKey

        if (key.isNullOrBlank()) {
            Log.w(TAG, "No API key configured for ${activeService.name}, using local fallback")
            return generateLocalFallbackAnalysis(input).copy(
                providerUsed = AiProviderType.CLOUD,
                latencyMs = System.currentTimeMillis() - startTime
            )
        }

        return try {
            val prompt = buildPrompt(input)
            val responseText = withContext(Dispatchers.IO) {
                when (activeService) {
                    CloudAiService.CLAUDE -> callClaudeApi(key, prompt)
                    CloudAiService.CHATGPT -> callChatGptApi(key, prompt)
                    CloudAiService.DEEPSEEK -> callDeepSeekApi(key, prompt)
                    CloudAiService.GEMINI -> callGeminiApi(key, prompt)
                }
            }
            parseApiResponse(responseText, activeService).copy(
                providerUsed = AiProviderType.CLOUD,
                latencyMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            val errorDetail = e.message ?: e.toString()
            Log.e(TAG, "Cloud API call failed (${activeService.name}): $errorDetail", e)
            generateLocalFallbackAnalysis(input).copy(
                providerUsed = AiProviderType.CLOUD,
                latencyMs = System.currentTimeMillis() - startTime,
                cloudError = "Cloud AI (${activeService.displayName}) failed: $errorDetail"
            )
        }
    }

    private fun generateLocalFallbackAnalysis(input: AnalysisInput): AnalysisResult {
        val riskLevel = when {
            input.adherenceRate >= 90f -> RiskLevel.LOW
            input.adherenceRate >= 70f -> RiskLevel.MODERATE
            else -> RiskLevel.HIGH
        }

        val isDaily = input.analysisType == AnalysisType.DAILY
        val summary = if (isDaily) {
            when (riskLevel) {
                RiskLevel.LOW -> "Great job today! You've taken ${input.takenDoses} of ${input.totalDoses} doses with ${String.format("%.0f", input.adherenceRate)}% adherence."
                RiskLevel.MODERATE -> "You've taken ${input.takenDoses} of ${input.totalDoses} doses today (${String.format("%.0f", input.adherenceRate)}%). Consider setting additional reminders."
                RiskLevel.HIGH -> "You've missed ${input.missedDoses} doses today (${String.format("%.0f", input.adherenceRate)}% adherence). Please prioritize taking your medications on time."
            }
        } else {
            when (riskLevel) {
                RiskLevel.LOW -> "Excellent week! ${String.format("%.0f", input.adherenceRate)}% adherence over ${input.periodDays} days with a ${input.currentStreak}-day streak."
                RiskLevel.MODERATE -> "Your weekly adherence of ${String.format("%.0f", input.adherenceRate)}% shows room for improvement. You missed ${input.missedDoses} doses this period."
                RiskLevel.HIGH -> "This week needs attention — ${String.format("%.0f", input.adherenceRate)}% adherence with ${input.missedDoses} missed doses. Consider reviewing your schedule."
            }
        }

        val insights = buildList {
            if (input.currentStreak > 0) add("You're on a ${input.currentStreak}-day streak.")
            if (input.missedDoses > 0) add("You've missed ${input.missedDoses} dose(s) in this period.")
            if (input.totalSnoozedCount > 0) add("You snoozed ${input.totalSnoozedCount} time(s) — consider adjusting reminder times.")
            if (input.averageDelayMinutes > 15) add("Average dose delay: ${String.format("%.0f", input.averageDelayMinutes)} minutes.")
            input.timeOfDayBreakdown?.let { tod ->
                val worst = listOf("Morning" to tod.morningRate, "Afternoon" to tod.afternoonRate,
                    "Evening" to tod.eveningRate, "Night" to tod.nightRate)
                    .filter { it.second > 0f }.minByOrNull { it.second }
                if (worst != null && worst.second < 80f) add("${worst.first} doses have the lowest adherence (${String.format("%.0f", worst.second)}%).")
            }
            if (isEmpty()) add("Keep tracking your medications for more detailed insights.")
        }.take(5)

        val recommendations = buildList {
            when (riskLevel) {
                RiskLevel.HIGH -> {
                    add("Set up additional reminders for frequently missed doses.")
                    add("Talk to your healthcare provider about simplifying your schedule.")
                }
                RiskLevel.MODERATE -> {
                    add("Try taking medications at the same time each day to build a habit.")
                    add("Use the snooze feature instead of missing doses entirely.")
                }
                RiskLevel.LOW -> add("Maintain your excellent routine!")
            }
            val emergencyMissed = input.medications.filter { it.isEmergency && it.missedCount > 0 }
            if (emergencyMissed.isNotEmpty()) add("Critical medication ${emergencyMissed.first().name} was missed — please prioritize this.")
            val refillNeeded = input.medications.filter { it.needsRefill }
            if (refillNeeded.isNotEmpty()) add("Refill ${refillNeeded.joinToString(", ") { it.name }} before running out.")
        }.take(5)

        return AnalysisResult(
            summary = summary,
            insights = insights,
            recommendations = recommendations,
            riskLevel = riskLevel
        )
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
        val url = URL("https://api.deepseek.com/v1/chat/completions")
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

        val response = makeHttpPost(url, body.toString(), mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json"
        ))
        Log.d(TAG, "DeepSeek raw response (first 500 chars): ${response.take(500)}")
        val json = JSONObject(response)

        // Check for API-level error
        if (json.has("error")) {
            val err = json.optJSONObject("error")
            val errMsg = err?.optString("message") ?: json.getString("error")
            throw Exception("DeepSeek API error: $errMsg")
        }

        val choices = json.optJSONArray("choices")
            ?: throw Exception("DeepSeek response missing 'choices': ${response.take(200)}")
        return choices.getJSONObject(0)
            .getJSONObject("message").getString("content")
    }

    private fun callGeminiApi(apiKey: String, prompt: String): String {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
        val body = JSONObject().apply {
            put("contents", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 1024)
            })
        }

        val response = makeHttpPost(url, body.toString(), mapOf(
            "Content-Type" to "application/json"
        ))
        val json = JSONObject(response)

        // Check for API-level error
        if (json.has("error")) {
            val err = json.optJSONObject("error")
            val errMsg = err?.optString("message") ?: json.getString("error")
            throw Exception("Gemini API error: $errMsg")
        }

        val candidates = json.optJSONArray("candidates")
            ?: throw Exception("Gemini response missing 'candidates': ${response.take(200)}")
        return candidates.getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
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
            "\n\nDaily Breakdown (last 7 days):\n" + input.weeklyBreakdown.joinToString("\n") { day ->
                "  ${day.dayName}: ${day.taken}/${day.total} (${String.format("%.0f", day.rate)}%)"
            }
        } else ""

        val userSection = if (input.userName.isNotBlank() || input.userAge > 0) {
            val parts = mutableListOf<String>()
            if (input.userName.isNotBlank()) parts.add("Name: ${input.userName}")
            if (input.userAge > 0) parts.add("Age: ${input.userAge}")
            "\nPatient: ${parts.joinToString(", ")}"
        } else ""

        val contextSection = buildList {
            if (input.totalSnoozedCount > 0) add("Total snoozes in period: ${input.totalSnoozedCount}")
            if (input.averageDelayMinutes > 0) add("Avg dose delay: ${String.format("%.0f", input.averageDelayMinutes)} min")
            if (input.longestStreak > 0) add("Longest streak ever: ${input.longestStreak} days")
            if (input.hasCaregivers) add("Has ${input.caregiverCount} caregiver(s) monitoring")
            if (input.familyMemberCount > 0) add("Managing meds for ${input.familyMemberCount + 1} people (self + ${input.familyMemberCount} family)")
            if (input.worstMedication != null && input.medications.size > 1)
                add("Lowest adherence medication: ${input.worstMedication}")
            if (input.bestMedication != null && input.medications.size > 1)
                add("Highest adherence medication: ${input.bestMedication}")
        }.let {
            if (it.isNotEmpty()) "\n\nAdditional Context:\n" + it.joinToString("\n") { s -> "  $s" }
            else ""
        }

        val doseEventsSection = if (input.recentDoseEvents.isNotEmpty()) {
            "\n\nRecent Dose Event Log:\n" + input.recentDoseEvents.joinToString("\n") { ev ->
                val delay = if (ev.delayMinutes > 0) " (${ev.delayMinutes}min late)" else ""
                val snooze = if (ev.snoozeCount > 0) " [snoozed ${ev.snoozeCount}x]" else ""
                "  ${ev.scheduledTime} | ${ev.medicationName} | ${ev.status.uppercase()}$delay$snooze"
            }
        } else ""

        val isDaily = input.analysisType == AnalysisType.DAILY
        val periodLabel = if (isDaily) "Today" else "Last ${input.periodDays} days"

        val roleAndFocus = if (isDaily) {
            """You are an expert medication adherence coach inside a health app. Analyze TODAY's medication data for this patient. Focus on:
            |- What happened today: which doses were taken, missed, or are still pending
            |- Immediate action items: any doses still due today, late doses, medications that need attention RIGHT NOW
            |- Encouragement for what went well today
            |- Specific timing observations (were morning meds taken? evening meds missed?)
            |- Medication stock warnings if any are running low
            |Be warm, specific, and motivational. Reference actual medication names and times.""".trimMargin()
        } else {
            """You are an expert medication adherence analyst inside a health app. Analyze the WEEKLY medication data for this patient. Focus on:
            |- Week-over-week trends: which days were best/worst, any patterns (e.g., weekends worse)
            |- Per-medication analysis: which meds have lowest adherence and why (timing, frequency)
            |- Behavioral patterns: snoozing habits, consistent late-taking, skipping patterns
            |- Time-of-day weaknesses: morning vs evening adherence differences
            |- Stock management: any medications running low
            |- Streak and motivation: current streak vs best, progress trajectory
            |Be analytical, specific, and data-driven. Reference actual medication names, days, and percentages.""".trimMargin()
        }

        return """
            |$roleAndFocus
            |$userSection
            |
            |== MEDICATIONS ==
            |$medsSection
            |
            |== ADHERENCE SUMMARY ($periodLabel) ==
            |Overall Adherence: ${String.format("%.1f", input.adherenceRate)}%
            |Total Doses: ${input.totalDoses} | Taken: ${input.takenDoses} | Missed: ${input.missedDoses} | Skipped: ${input.skippedDoses}
            |Current Streak: ${input.currentStreak} days | Best Streak: ${input.longestStreak} days
            |$timeOfDaySection$weeklySection$contextSection$doseEventsSection
            |
            |== INSTRUCTIONS ==
            |Provide a thorough, personalized analysis:
            |1. "summary": A ${if (isDaily) "warm, encouraging" else "detailed, analytical"} 2-4 sentence overview referencing specific medications by name
            |2. "insights": 3-5 specific, data-backed observations (mention medication names, times, percentages, patterns)
            |3. "recommendations": 3-5 actionable, personalized suggestions (not generic — based on THIS patient's actual data)
            |4. "riskLevel": "LOW" (≥90%), "MODERATE" (70-89%), or "HIGH" (<70%)
            |
            |Respond ONLY with valid JSON (no markdown, no extra text):
            |{"summary": "...", "insights": ["...", "..."], "recommendations": ["...", "..."], "riskLevel": "LOW|MODERATE|HIGH"}${AiLanguageHelper.getLanguageInstruction()}
        """.trimMargin()
    }

    companion object {
        private const val TAG = "CloudAiProvider"
    }
}
