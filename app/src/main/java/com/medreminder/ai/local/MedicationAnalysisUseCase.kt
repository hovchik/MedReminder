package com.medreminder.ai.local

import com.medreminder.ai.*
import com.medreminder.ai.AiLanguageHelper
import com.medreminder.billing.SubscriptionLimitException
import com.medreminder.billing.SubscriptionRepository
import com.medreminder.data.local.DoseLogDao
import com.medreminder.data.local.MedicationDao
import com.medreminder.data.preferences.UserPreferencesManager
import com.medreminder.domain.model.toDomain
import com.medreminder.domain.repository.MedicationRepository
import com.medreminder.util.DateUtils
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
            if (!com.medreminder.domain.model.SubscriptionPlans.hasCloudAi(tier)) {
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
        val startOfDay = DateUtils.getStartOfDay()
        val endOfDay = DateUtils.getEndOfDay()

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

    private fun parseMedicationResult(
        jsonStr: String,
        providerType: AiProviderType,
        latencyMs: Long
    ): MedicationAnalysisResult {
        val json = JSONObject(jsonStr)

        val infoJson = json.getJSONObject("medicationInfo")
        val info = MedicationInfoSection(
            description = infoJson.optString("description", ""),
            drugClass = infoJson.optString("drugClass", ""),
            commonUses = jsonArrayToList(infoJson.optJSONArray("commonUses")),
            sideEffects = jsonArrayToList(infoJson.optJSONArray("sideEffects")),
            importantWarnings = jsonArrayToList(infoJson.optJSONArray("importantWarnings")),
            interactions = jsonArrayToList(infoJson.optJSONArray("interactions"))
        )

        val dosingJson = json.getJSONObject("dosingPredictions")
        val dosing = DosingPredictionSection(
            adherenceForecast = dosingJson.optString("adherenceForecast", ""),
            optimizationTips = jsonArrayToList(dosingJson.optJSONArray("optimizationTips")),
            stockForecast = dosingJson.optString("stockForecast", ""),
            riskAssessment = dosingJson.optString("riskAssessment", "")
        )

        val regionsArray = json.optJSONArray("bodyRegions") ?: JSONArray()
        val bodyRegions = (0 until regionsArray.length()).mapNotNull { i ->
            val regionStr = regionsArray.optString(i, "")
            try {
                BodyRegion.valueOf(regionStr)
            } catch (_: Exception) {
                // Try matching by display name
                BodyRegion.entries.find {
                    it.name.equals(regionStr, ignoreCase = true) ||
                    it.displayName.equals(regionStr, ignoreCase = true)
                }
            }
        }

        return MedicationAnalysisResult(
            medicationInfo = info,
            dosingPredictions = dosing,
            bodyRegions = bodyRegions.ifEmpty { listOf(BodyRegion.FULL_BODY) },
            providerUsed = providerType,
            latencyMs = latencyMs
        )
    }

    private fun generateFallbackResult(
        input: MedicationAnalysisInput,
        providerType: AiProviderType,
        latencyMs: Long
    ): MedicationAnalysisResult {
        val bodyRegions = guessBodyRegions(input.medicationName, input.instructions, input.form)

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
            bodyRegions = bodyRegions,
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

    private fun guessBodyRegions(name: String, instructions: String, form: String): List<BodyRegion> {
        val text = "$name $instructions".lowercase()
        val regions = mutableSetOf<BodyRegion>()

        // Map common medication keywords to body regions
        val mappings = mapOf(
            listOf("aspirin", "ibuprofen", "naproxen", "acetaminophen", "paracetamol", "tylenol", "advil", "pain", "analgesic", "nsaid") to BodyRegion.JOINTS,
            listOf("heart", "cardiac", "cardio", "atenolol", "metoprolol", "amlodipine", "lisinopril", "losartan", "blood pressure", "hypertension", "beta-blocker") to BodyRegion.HEART,
            listOf("lung", "breath", "asthma", "inhaler", "bronch", "salbutamol", "albuterol", "fluticasone", "montelukast") to BodyRegion.LUNGS,
            listOf("stomach", "antacid", "omeprazole", "pantoprazole", "ranitidine", "digest", "gastro", "acid reflux", "gerd", "nausea") to BodyRegion.STOMACH,
            listOf("liver", "hepat", "ursodiol") to BodyRegion.LIVER,
            listOf("kidney", "renal", "diuretic", "furosemide") to BodyRegion.KIDNEYS,
            listOf("brain", "head", "migraine", "headache", "neuro", "seizure", "epilep", "gabapentin", "pregabalin", "sumatriptan") to BodyRegion.HEAD,
            listOf("eye", "ophthalm", "glaucoma", "timolol", "latanoprost") to BodyRegion.EYES,
            listOf("ear", "otic") to BodyRegion.EARS,
            listOf("throat", "cough", "pharyn", "tonsil") to BodyRegion.THROAT,
            listOf("skin", "derma", "eczema", "psoriasis", "rash", "topical", "cream", "ointment") to BodyRegion.SKIN,
            listOf("blood", "coagul", "warfarin", "heparin", "anemia", "iron", "clot", "anticoagul") to BodyRegion.BLOOD,
            listOf("immune", "allerg", "antihist", "cetirizine", "loratadine", "fexofenadine", "prednisone", "autoimmune") to BodyRegion.IMMUNE,
            listOf("thyroid", "hormone", "insulin", "diabetes", "metformin", "levothyroxine", "endocrine", "testosterone", "estrogen") to BodyRegion.HORMONES,
            listOf("anxiety", "depress", "ssri", "sertraline", "fluoxetine", "escitalopram", "venlafaxine", "duloxetine", "mental", "mood", "sleep", "melatonin", "zolpidem") to BodyRegion.NERVOUS_SYSTEM,
            listOf("bone", "osteo", "calcium", "vitamin d", "alendronate", "fracture") to BodyRegion.BONES,
            listOf("antibiotic", "amoxicillin", "azithromycin", "ciprofloxacin", "infection", "antiviral", "antifungal") to BodyRegion.IMMUNE
        )

        for ((keywords, region) in mappings) {
            if (keywords.any { it in text }) {
                regions.add(region)
            }
        }

        // Form-based hints
        when (form.lowercase()) {
            "inhaler" -> regions.add(BodyRegion.LUNGS)
            "cream" -> regions.add(BodyRegion.SKIN)
            "drops" -> if (regions.isEmpty()) regions.add(BodyRegion.EYES)
        }

        return regions.toList().ifEmpty { listOf(BodyRegion.FULL_BODY) }
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

        val bodyRegionValues = BodyRegion.entries.joinToString(", ") { it.name }

        return """
            |You are a pharmaceutical analysis AI inside a medication management app. Provide a comprehensive analysis of a SINGLE medication including drug information, dosing predictions, and which body systems it targets.
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
            |Provide a deep analysis of this medication in the following JSON format. Be specific, data-driven, and reference the patient's actual adherence patterns.
            |
            |For "bodyRegions", use ONLY values from this list: $bodyRegionValues
            |Choose the body regions this medication primarily acts on (1-3 regions typically).
            |
            |Respond ONLY with valid JSON (no markdown, no extra text):
            |{
            |  "medicationInfo": {
            |    "description": "What this medication is and what it does (2-3 sentences)",
            |    "drugClass": "The pharmacological class (e.g., NSAID, Beta-blocker, SSRI)",
            |    "commonUses": ["Use 1", "Use 2", "Use 3"],
            |    "sideEffects": ["Side effect 1", "Side effect 2", "Side effect 3"],
            |    "importantWarnings": ["Warning 1", "Warning 2"],
            |    "interactions": ["Interaction 1", "Interaction 2"]
            |  },
            |  "dosingPredictions": {
            |    "adherenceForecast": "Prediction about future adherence based on patterns (2-3 sentences)",
            |    "optimizationTips": ["Tip 1 specific to this patient's data", "Tip 2"],
            |    "stockForecast": "When they'll need a refill based on current usage rate",
            |    "riskAssessment": "Overall risk assessment for this medication's adherence (2-3 sentences)"
            |  },
            |  "bodyRegions": ["REGION_1", "REGION_2"]
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

        fun rate(group: List<com.medreminder.data.local.entity.DoseLogEntity>): Float {
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
