package med.reminder.com.ai.local

import med.reminder.com.ai.*
import med.reminder.com.ai.AiLanguageHelper
import med.reminder.com.billing.SubscriptionLimitException
import med.reminder.com.billing.SubscriptionRepository
import med.reminder.com.data.local.DoseLogDao
import med.reminder.com.data.local.MedicationDao
import med.reminder.com.data.preferences.UserPreferencesManager
import med.reminder.com.domain.model.toDomain
import med.reminder.com.domain.repository.MedicationRepository
import med.reminder.com.util.DateUtils
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationAnalysisUseCase @Inject constructor(
    private val repository: MedicationRepository,
    private val providerSelector: AiProviderSelector,
    private val subscriptionRepository: SubscriptionRepository,
    private val doseLogDao: DoseLogDao,
    private val medicationDao: MedicationDao,
    private val userPreferencesManager: UserPreferencesManager
) {
    private data class CachedAnalysis(
        val result: MedicationAnalysisResult,
        val timestamp: Long
    )

    private val cache = java.util.concurrent.ConcurrentHashMap<Long, CachedAnalysis>()

    /** Returns a cached result if still valid (within 1 hour), or null. */
    fun getCachedResult(medicationId: Long): MedicationAnalysisResult? {
        val cached = cache[medicationId] ?: return null
        if (System.currentTimeMillis() - cached.timestamp > 3_600_000L) {
            cache.remove(medicationId)
            return null
        }
        return cached.result
    }

    suspend fun analyze(medicationId: Long): MedicationAnalysisResult {
        getCachedResult(medicationId)?.let { return it }

        val provider = providerSelector.selectProvider()

        // Subscription checks only apply to Cloud AI — local/on-device AI is always free
        if (provider.type == AiProviderType.CLOUD) {
            val tier = subscriptionRepository.getCurrentTierOnce()
            if (!med.reminder.com.domain.model.SubscriptionPlans.hasCloudAi(tier)) {
                throw SubscriptionLimitException(
                    "Cloud AI requires a Pro or Premium subscription. Please upgrade your plan or switch to on-device AI."
                )
            }
            if (!subscriptionRepository.canPerformDeepAnalysis()) {
                throw SubscriptionLimitException(
                    "You've reached your Cloud AI medication deep analysis limit for this month. Upgrade to Premium for unlimited analyses, or switch to on-device AI."
                )
            }
        }

        val now = System.currentTimeMillis()
        val start30Days = DateUtils.daysAgo(30)

        // Load medication with schedules
        val medEntity = medicationDao.getMedicationById(medicationId)
            ?: throw IllegalArgumentException("Medication not found")
        val schedules = repository.getSchedulesForMedication(medicationId)
        val medication = medEntity.toDomain(schedules)

        // Gather adherence stats for this medication (last 30 days)
        val takenCount = doseLogDao.getTakenCountForMedication(medicationId, start30Days, now)
        val missedCount = doseLogDao.getMissedCountForMedication(medicationId, start30Days, now)
        val skippedCount = doseLogDao.getSkippedCountForMedication(medicationId, start30Days, now)
        val totalCount = doseLogDao.getTotalCountForMedication(medicationId, start30Days, now)
        val snoozedCount = doseLogDao.getSnoozedCountForMedication(medicationId, start30Days, now) ?: 0
        val avgDelayMs = doseLogDao.getAverageDelayMsForMedication(medicationId, start30Days, now)
        val adherenceRate = if (totalCount > 0) takenCount.toFloat() / totalCount * 100f else 0f
        val streak = repository.getCurrentStreak()

        // Time-of-day breakdown for this medication
        val timeOfDay = buildTimeOfDayBreakdown(medicationId, start30Days, now)

        // Recent dose events for this medication
        val recentEvents = buildDoseEvents(medicationId, start30Days, now)

        val userName = userPreferencesManager.userName.first()
        val userAge = userPreferencesManager.userAge.first()

        val input = MedicationAnalysisInput(
            medicationName = medication.name,
            dosage = "${medication.dosage} ${medication.dosageUnit}",
            form = medication.form.displayName,
            frequency = medication.schedules.firstOrNull()?.frequency?.displayName ?: "Unknown",
            instructions = medication.instructions,
            scheduledTimes = medication.schedules.map { it.timeFormatted },
            isEmergency = medication.isEmergency,
            currentStock = medication.currentStock,
            refillThreshold = medication.refillThreshold,
            needsRefill = medication.refillReminder && medication.currentStock > 0 && medication.currentStock <= medication.refillThreshold,
            notes = medication.notes,
            assignedTo = medication.assignedToName,
            takenCount = takenCount,
            missedCount = missedCount,
            skippedCount = skippedCount,
            snoozedCount = snoozedCount,
            adherenceRate = adherenceRate,
            averageDelayMinutes = if (avgDelayMs != null && avgDelayMs > 0) avgDelayMs / 60000f else 0f,
            periodDays = 30,
            currentStreak = streak,
            recentDoseEvents = recentEvents,
            timeOfDayBreakdown = timeOfDay,
            userName = userName,
            userAge = userAge
        )

        val prompt = buildMedicationPrompt(input)
        val startTime = System.currentTimeMillis()

        // Generate the medication-specific result
        val result = generateMedicationResult(input, provider, prompt, startTime)
        cache[medicationId] = CachedAnalysis(result, System.currentTimeMillis())
        // Only count Cloud AI analyses against the monthly quota
        if (provider.type == AiProviderType.CLOUD) {
            subscriptionRepository.incrementDeepAnalysisCount()
        }
        return result
    }

    private suspend fun generateMedicationResult(
        input: MedicationAnalysisInput,
        provider: AiProvider,
        prompt: String,
        startTime: Long
    ): MedicationAnalysisResult {
        // Use the interface method — works for any provider (Cloud, Local, System AI)
        val rawJson: String? = try {
            provider.generateRawCompletion(prompt)
        } catch (e: Exception) {
            android.util.Log.e("MedicationAnalysis", "Provider ${provider.type.name} raw completion failed", e)
            null
        }

        val latencyMs = System.currentTimeMillis() - startTime

        if (rawJson != null) {
            try {
                return parseMedicationResult(rawJson, provider.type, latencyMs)
            } catch (e: Exception) {
                android.util.Log.e("MedicationAnalysis", "Failed to parse AI response, using fallback", e)
            }
        }

        // Fallback when no AI provider returned a result or parsing failed
        return generateFallbackResult(input, provider.type, latencyMs)
    }

    /**
     * Sanitizes raw AI-generated JSON by escaping unescaped control characters
     * (newlines, tabs, etc.) that appear inside JSON string values, then
     * repairs truncated JSON by closing any open strings, arrays, and objects.
     */
    private fun sanitizeAndRepairJson(raw: String): String {
        // Step 1: escape control characters inside JSON string values
        val sb = StringBuilder(raw.length)
        var inString = false
        var escaped = false
        for (ch in raw) {
            when {
                escaped -> {
                    sb.append(ch)
                    escaped = false
                }
                ch == '\\' && inString -> {
                    sb.append(ch)
                    escaped = true
                }
                ch == '"' -> {
                    inString = !inString
                    sb.append(ch)
                }
                inString && ch == '\n' -> sb.append("\\n")
                inString && ch == '\r' -> sb.append("\\r")
                inString && ch == '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }

        // Step 2: repair truncated JSON by closing open structures
        var sanitized = sb.toString().trimEnd()

        // If we ended inside an escape sequence, drop the trailing backslash
        if (sanitized.endsWith("\\")) {
            sanitized = sanitized.dropLast(1)
        }

        // Re-scan to find what structures are still open
        var inStr = false
        var esc = false
        val stack = ArrayDeque<Char>() // tracks open '{' and '['
        for (ch in sanitized) {
            when {
                esc -> esc = false
                ch == '\\' && inStr -> esc = true
                ch == '"' -> inStr = !inStr
                !inStr && ch == '{' -> stack.addLast('{')
                !inStr && ch == '[' -> stack.addLast('[')
                !inStr && ch == '}' -> { if (stack.isNotEmpty() && stack.last() == '{') stack.removeLast() }
                !inStr && ch == ']' -> { if (stack.isNotEmpty() && stack.last() == '[') stack.removeLast() }
            }
        }

        val repair = StringBuilder(sanitized)

        // Close an unterminated string
        if (inStr) {
            repair.append('"')
        }

        // Remove any trailing comma or colon (invalid trailing token before closing)
        val trimmed = repair.toString().trimEnd()
        if (trimmed.endsWith(",") || trimmed.endsWith(":")) {
            repair.clear()
            repair.append(trimmed.dropLast(1))
        }

        // Close all open arrays / objects in reverse order
        while (stack.isNotEmpty()) {
            when (stack.removeLast()) {
                '[' -> repair.append(']')
                '{' -> repair.append('}')
            }
        }

        return repair.toString()
    }

    private fun parseMedicationResult(
        jsonStr: String,
        providerType: AiProviderType,
        latencyMs: Long
    ): MedicationAnalysisResult {
        val json = JSONObject(sanitizeAndRepairJson(jsonStr))

        val infoJson = json.optJSONObject("medicationInfo")
        val info = if (infoJson != null) MedicationInfoSection(
            description = infoJson.optString("description", ""),
            drugClass = infoJson.optString("drugClass", ""),
            genericName = infoJson.optString("genericName", ""),
            brandNames = jsonArrayToList(infoJson.optJSONArray("brandNames")),
            commonUses = jsonArrayToList(infoJson.optJSONArray("commonUses")),
            mechanismOfAction = infoJson.optString("mechanismOfAction", ""),
            sideEffects = jsonArrayToList(infoJson.optJSONArray("sideEffects")),
            seriousSideEffects = jsonArrayToList(infoJson.optJSONArray("seriousSideEffects")),
            importantWarnings = jsonArrayToList(infoJson.optJSONArray("importantWarnings")),
            contraindications = jsonArrayToList(infoJson.optJSONArray("contraindications")),
            interactions = jsonArrayToList(infoJson.optJSONArray("interactions")),
            foodInteractions = jsonArrayToList(infoJson.optJSONArray("foodInteractions")),
            pregnancyCategory = infoJson.optString("pregnancyCategory", ""),
            storageInstructions = infoJson.optString("storageInstructions", ""),
            halfLife = infoJson.optString("halfLife", ""),
            onsetOfAction = infoJson.optString("onsetOfAction", ""),
            missedDoseAdvice = infoJson.optString("missedDoseAdvice", "")
        ) else MedicationInfoSection()

        val dosingJson = json.optJSONObject("dosingPredictions")
        val dosing = if (dosingJson != null) DosingPredictionSection(
            adherenceForecast = dosingJson.optString("adherenceForecast", ""),
            optimizationTips = jsonArrayToList(dosingJson.optJSONArray("optimizationTips")),
            stockForecast = dosingJson.optString("stockForecast", ""),
            riskAssessment = dosingJson.optString("riskAssessment", "")
        ) else DosingPredictionSection()

        return MedicationAnalysisResult(
            medicationInfo = info,
            dosingPredictions = dosing,
            providerUsed = providerType,
            latencyMs = latencyMs
        )
    }

    private fun generateFallbackResult(
        input: MedicationAnalysisInput,
        providerType: AiProviderType,
        latencyMs: Long
    ): MedicationAnalysisResult {

        val stockForecast = if (input.currentStock > 0 && input.takenCount > 0 && input.periodDays > 0) {
            val dailyRate = input.takenCount.toFloat() / input.periodDays
            val daysLeft = if (dailyRate > 0) (input.currentStock / dailyRate).toInt() else 0
            "At your current rate, your supply will last approximately $daysLeft days."
        } else "Stock tracking not available for this medication."

        val adherenceForecast = when {
            input.adherenceRate >= 95 -> "Excellent adherence! You're very consistent with ${input.medicationName}."
            input.adherenceRate >= 80 -> "Good adherence overall. A few missed doses — watch for patterns."
            input.adherenceRate >= 60 -> "Moderate adherence. Consider setting additional reminders."
            else -> "Your adherence to ${input.medicationName} needs attention. Missing doses frequently can reduce effectiveness."
        }

        return MedicationAnalysisResult(
            medicationInfo = MedicationInfoSection(
                description = "${input.medicationName} is a ${input.form.lowercase()} medication taken ${input.frequency.lowercase()}.",
                drugClass = "Consult your doctor for drug classification.",
                commonUses = listOf("As prescribed by your healthcare provider"),
                sideEffects = listOf("Consult your doctor or pharmacist for side effect information."),
                importantWarnings = if (input.isEmergency) listOf("This is marked as an EMERGENCY medication.") else emptyList(),
                interactions = listOf("Consult your pharmacist for interaction information.")
            ),
            dosingPredictions = DosingPredictionSection(
                adherenceForecast = adherenceForecast,
                optimizationTips = buildFallbackTips(input),
                stockForecast = stockForecast,
                riskAssessment = when {
                    input.adherenceRate >= 90 -> "Low risk — keep up the good work!"
                    input.adherenceRate >= 70 -> "Moderate risk — some doses are being missed."
                    else -> "High risk — significant number of missed doses detected."
                }
            ),
            providerUsed = providerType,
            latencyMs = latencyMs
        )
    }

    private fun buildFallbackTips(input: MedicationAnalysisInput): List<String> = buildList {
        if (input.averageDelayMinutes > 15) {
            add("You're taking ${input.medicationName} an average of ${input.averageDelayMinutes.toInt()} minutes late. Try to take it closer to the scheduled time.")
        }
        if (input.snoozedCount > 3) {
            add("You've snoozed this medication ${input.snoozedCount} times recently. Consider adjusting the schedule to a more convenient time.")
        }
        if (input.missedCount > input.takenCount / 4) {
            add("Consider pairing this medication with a daily routine (e.g., meals, brushing teeth) to reduce missed doses.")
        }
        if (isEmpty()) {
            add("Keep taking ${input.medicationName} as prescribed at your scheduled times.")
        }
    }


    private fun buildMedicationPrompt(input: MedicationAnalysisInput): String {
        val eventsSection = if (input.recentDoseEvents.isNotEmpty()) {
            "\n\nRecent Dose Events (last 30 days):\n" + input.recentDoseEvents.take(20).joinToString("\n") { ev ->
                val delay = if (ev.delayMinutes > 0) " (${ev.delayMinutes}min late)" else ""
                val snooze = if (ev.snoozeCount > 0) " [snoozed ${ev.snoozeCount}x]" else ""
                "  ${ev.scheduledTime} | ${ev.status.uppercase()}$delay$snooze"
            }
        } else ""

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

        val userSection = buildList {
            if (input.userName.isNotBlank()) add("Patient: ${input.userName}")
            if (input.userAge > 0) add("Age: ${input.userAge}")
        }.let { if (it.isNotEmpty()) "\n${it.joinToString(", ")}" else "" }


        return """
            |You are a comprehensive pharmaceutical analysis AI inside a medication management app. Provide the MOST DETAILED and THOROUGH analysis possible of this medication. Act as if you are a senior clinical pharmacist giving a full drug monograph to a patient.
            |$userSection
            |
            |== MEDICATION ==
            |Name: ${input.medicationName}
            |Dosage: ${input.dosage}
            |Form: ${input.form}
            |Frequency: ${input.frequency}
            |Schedule: ${input.scheduledTimes.joinToString(", ")}
            |Instructions: ${input.instructions.ifBlank { "None specified" }}
            |Notes: ${input.notes.ifBlank { "None" }}
            |Emergency medication: ${input.isEmergency}
            |${if (input.assignedTo.isNotBlank()) "Assigned to: ${input.assignedTo}" else ""}
            |
            |== ADHERENCE DATA (Last ${input.periodDays} days) ==
            |Adherence Rate: ${String.format("%.1f", input.adherenceRate)}%
            |Taken: ${input.takenCount} | Missed: ${input.missedCount} | Skipped: ${input.skippedCount} | Snoozed: ${input.snoozedCount}
            |Average Delay: ${String.format("%.0f", input.averageDelayMinutes)} minutes
            |Current Streak: ${input.currentStreak} days
            |$timeOfDaySection
            |
            |== STOCK ==
            |Current Stock: ${input.currentStock}
            |Refill Threshold: ${input.refillThreshold}
            |Needs Refill: ${input.needsRefill}
            |$eventsSection
            |
            |== INSTRUCTIONS ==
            |Provide an EXHAUSTIVE pharmaceutical analysis of this medication. Include ALL available clinical knowledge. Be specific, data-driven, and reference the patient's actual adherence patterns where relevant.
            |
            |IMPORTANT: Fill EVERY field as completely as possible. Provide at least 4-6 items for lists like sideEffects, commonUses, interactions. Do NOT leave fields empty or with placeholder text. Use real pharmaceutical knowledge.
            |
            |Respond ONLY with valid JSON (no markdown, no extra text):
            |{
            |  "medicationInfo": {
            |    "description": "Comprehensive description of the medication: what it is, how it works at a high level, its therapeutic purpose, and why it's prescribed. Write 4-6 detailed sentences.",
            |    "drugClass": "Full pharmacological classification (e.g., 'Selective Serotonin Reuptake Inhibitor (SSRI) — Antidepressant')",
            |    "genericName": "The International Nonproprietary Name (INN) / generic name of the active ingredient",
            |    "brandNames": ["Brand 1", "Brand 2", "Brand 3", "Brand 4"],
            |    "commonUses": ["Primary indication with detail", "Secondary indication", "Off-label use 1", "Off-label use 2"],
            |    "mechanismOfAction": "Detailed explanation of HOW the drug works at the molecular/cellular level, which receptors/enzymes it targets, and how this produces the therapeutic effect. Write 3-4 sentences.",
            |    "sideEffects": ["Common side effect 1 (frequency %)", "Common side effect 2", "Common side effect 3", "Common side effect 4", "Common side effect 5", "Common side effect 6"],
            |    "seriousSideEffects": ["Rare but serious side effect 1 — seek immediate medical attention", "Serious side effect 2", "Serious side effect 3"],
            |    "importantWarnings": ["Critical warning 1 with context", "Warning 2", "Black box warning if applicable"],
            |    "contraindications": ["When NOT to take this medication 1", "Contraindication 2", "Contraindication 3"],
            |    "interactions": ["Drug interaction 1 — what happens and why", "Drug interaction 2", "Drug interaction 3", "Drug interaction 4"],
            |    "foodInteractions": ["Food/drink interaction 1 (e.g., grapefruit, alcohol, dairy)", "Food interaction 2"],
            |    "pregnancyCategory": "FDA pregnancy category (A/B/C/D/X) with brief explanation",
            |    "storageInstructions": "How to properly store this medication (temperature, light, moisture)",
            |    "halfLife": "Approximate elimination half-life (e.g., '4-6 hours') and what this means for dosing",
            |    "onsetOfAction": "How long until the patient feels the effect (e.g., '30-60 minutes for pain relief; 2-4 weeks for full antidepressant effect')",
            |    "missedDoseAdvice": "Specific guidance on what to do if a dose is missed — take ASAP, skip, or never double dose"
            |  },
            |  "dosingPredictions": {
            |    "adherenceForecast": "Detailed prediction about future adherence based on the patient's specific patterns — reference their actual data (missed doses times, delay patterns, snooze behavior). Write 3-4 sentences.",
            |    "optimizationTips": ["Highly specific tip 1 referencing patient's actual time-of-day data", "Tip 2 about their snooze/delay pattern", "Tip 3 about medication-specific best practices", "Tip 4"],
            |    "stockForecast": "Calculate exactly when they'll need a refill based on their current stock and actual daily consumption rate. Be precise with dates.",
            |    "riskAssessment": "Comprehensive risk assessment: what are the clinical consequences of their current adherence pattern for THIS specific medication? What could happen if they continue missing doses at this rate? Write 3-4 sentences."
            |  }
            |}${AiLanguageHelper.getLanguageInstruction()}
        """.trimMargin()
    }

    private suspend fun buildDoseEvents(medicationId: Long, start: Long, end: Long): List<DoseEvent> {
        val logs = doseLogDao.getLogsForDateRangeSync(start, end)
            .filter { it.medicationId == medicationId && it.status != "pending" }
        val timeFormat = SimpleDateFormat("EEE h:mm a", Locale.getDefault())
        return logs.takeLast(30).map { log ->
            val delayMs = if (log.actionTime != null && log.actionTime > log.scheduledTime)
                log.actionTime - log.scheduledTime else 0L
            DoseEvent(
                medicationName = "",
                scheduledTime = timeFormat.format(Date(log.scheduledTime)),
                status = log.status,
                delayMinutes = (delayMs / 60000).toInt(),
                snoozeCount = log.snoozeCount
            )
        }
    }

    private suspend fun buildTimeOfDayBreakdown(medicationId: Long, start: Long, end: Long): TimeOfDayBreakdown {
        // Use global counts filtered by medication from logs
        val logs = doseLogDao.getLogsForDateRangeSync(start, end)
            .filter { it.medicationId == medicationId && it.status != "pending" }

        val cal = Calendar.getInstance()
        fun hourOf(timeMs: Long): Int { cal.timeInMillis = timeMs; return cal.get(Calendar.HOUR_OF_DAY) }

        val morning = logs.filter { hourOf(it.scheduledTime) in 5..11 }
        val afternoon = logs.filter { hourOf(it.scheduledTime) in 12..16 }
        val evening = logs.filter { hourOf(it.scheduledTime) in 17..20 }
        val night = logs.filter { hourOf(it.scheduledTime) in 21..23 || hourOf(it.scheduledTime) in 0..4 }

        fun rate(group: List<med.reminder.com.data.local.entity.DoseLogEntity>): Float {
            val total = group.size
            val taken = group.count { it.status == "taken" }
            return if (total > 0) taken.toFloat() / total * 100f else 0f
        }

        return TimeOfDayBreakdown(
            morningRate = rate(morning),
            afternoonRate = rate(afternoon),
            eveningRate = rate(evening),
            nightRate = rate(night)
        )
    }

    private fun jsonArrayToList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { array.getString(it) }
    }
}
