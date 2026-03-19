package com.medreminder.ai.local

import com.medreminder.ai.*
import com.medreminder.data.local.DoseLogDao
import com.medreminder.data.local.MedicationDao
import com.medreminder.data.local.ScheduleDao
import com.medreminder.data.local.entity.DoseLogEntity
import com.medreminder.data.preferences.UserPreferencesManager
import com.medreminder.domain.repository.MedicationRepository
import com.medreminder.util.DateUtils
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailyAnalysisUseCase @Inject constructor(
    private val repository: MedicationRepository,
    private val providerSelector: AiProviderSelector,
    private val ruleBasedProvider: RuleBasedAiProvider,
    private val doseLogDao: DoseLogDao,
    private val medicationDao: MedicationDao,
    private val scheduleDao: ScheduleDao,
    private val userPreferencesManager: UserPreferencesManager
) {
    /**
     * Analyze all medications (original behaviour, kept for backward compatibility).
     */
    suspend fun analyze(): AnalysisResult {
        val userName = userPreferencesManager.userName.first()
        val userAge = userPreferencesManager.userAge.first()
        return analyzeForUser(
            assignedToId = null,
            targetName = userName,
            targetAge = userAge,
            isSelf = true
        )
    }

    /**
     * Analyze medications for a specific user.
     * @param assignedToId null = primary user (self), non-null = family member ID
     * @param targetName display name of the person being analyzed
     * @param targetAge age of the person being analyzed
     * @param isSelf true if this is the primary user
     */
    suspend fun analyzeForUser(
        assignedToId: Long?,
        targetName: String,
        targetAge: Int,
        isSelf: Boolean
    ): AnalysisResult {
        val provider = providerSelector.selectProvider()

        val startOfDay = DateUtils.getStartOfDay()
        val endOfDay = DateUtils.getEndOfDay()

        val allMedications = repository.getActiveMedications().first()
        // Filter medications to those belonging to this user
        val medications = allMedications.filter { med ->
            if (isSelf) med.assignedToId == null || med.assignedToId == 0L
            else med.assignedToId == assignedToId
        }

        val medicationIds = medications.map { it.id }.toSet()

        val medicationSummaries = medications.map { med ->
            val schedules = med.schedules
            val takenCount = doseLogDao.getTakenCountForMedication(med.id, startOfDay, endOfDay)
            val missedCount = doseLogDao.getMissedCountForMedication(med.id, startOfDay, endOfDay)
            val skippedCount = doseLogDao.getSkippedCountForMedication(med.id, startOfDay, endOfDay)
            val totalCount = doseLogDao.getTotalCountForMedication(med.id, startOfDay, endOfDay)
            val snoozedCount = doseLogDao.getSnoozedCountForMedication(med.id, startOfDay, endOfDay) ?: 0
            val avgDelayMs = doseLogDao.getAverageDelayMsForMedication(med.id, startOfDay, endOfDay)

            MedicationSummary(
                name = med.name,
                dosage = "${med.dosage} ${med.dosageUnit}",
                form = med.form.displayName,
                frequency = schedules.firstOrNull()?.frequency?.displayName ?: "Unknown",
                instructions = med.instructions,
                scheduledTimes = schedules.map { it.timeFormatted },
                isEmergency = med.isEmergency,
                currentStock = med.currentStock,
                refillThreshold = med.refillThreshold,
                needsRefill = med.refillReminder && med.currentStock > 0 && med.currentStock <= med.refillThreshold,
                takenCount = takenCount,
                missedCount = missedCount,
                skippedCount = skippedCount,
                snoozedCount = snoozedCount,
                adherenceRate = if (totalCount > 0) takenCount.toFloat() / totalCount * 100f else 0f,
                averageDelayMinutes = if (avgDelayMs != null && avgDelayMs > 0) avgDelayMs / 60000f else 0f,
                assignedTo = med.assignedToName
            )
        }

        // Compute aggregated stats from per-user medications
        val totalTaken = medicationSummaries.sumOf { it.takenCount }
        val totalMissed = medicationSummaries.sumOf { it.missedCount }
        val totalSkipped = medicationSummaries.sumOf { it.skippedCount }
        val totalSnoozed = medicationSummaries.sumOf { it.snoozedCount }
        val totalDoses = totalTaken + totalMissed + totalSkipped
        val adherenceRate = if (totalDoses > 0) totalTaken.toFloat() / totalDoses * 100f else 0f

        val streak = repository.getCurrentStreak()
        val timeOfDay = buildTimeOfDayBreakdown(startOfDay, endOfDay)
        val caregivers = repository.getActiveCaregivers().first()
        val familyMembers = repository.getActiveFamilyMembers().first()
        val userName = userPreferencesManager.userName.first()
        val userAge = userPreferencesManager.userAge.first()

        val sortedByAdherence = medicationSummaries.filter {
            it.takenCount + it.missedCount + it.skippedCount > 0
        }.sortedBy { it.adherenceRate }

        // Build today's dose event timeline filtered to this user's medications
        val recentEvents = buildDoseEvents(startOfDay, endOfDay, medicationIds)

        val input = AnalysisInput(
            medications = medicationSummaries,
            adherenceRate = adherenceRate,
            totalDoses = totalDoses,
            takenDoses = totalTaken,
            missedDoses = totalMissed,
            skippedDoses = totalSkipped,
            currentStreak = streak,
            longestStreak = 0,
            periodDays = 1,
            analysisType = AnalysisType.DAILY,
            totalSnoozedCount = totalSnoozed,
            averageDelayMinutes = 0f,
            timeOfDayBreakdown = timeOfDay,
            worstMedication = sortedByAdherence.firstOrNull()?.name,
            bestMedication = sortedByAdherence.lastOrNull()?.name,
            userName = userName,
            userAge = userAge,
            targetUserName = targetName,
            targetUserAge = targetAge,
            isSelfAnalysis = isSelf,
            hasCaregivers = caregivers.isNotEmpty(),
            caregiverCount = caregivers.size,
            familyMemberCount = familyMembers.size,
            recentDoseEvents = recentEvents
        )

        return try {
            provider.generateAnalysis(input)
        } catch (e: SystemAiUnavailableException) {
            android.util.Log.w("DailyAnalysis", "System AI unavailable, falling back to rule-based", e)
            ruleBasedProvider.generateAnalysis(input)
        }
    }

    private suspend fun buildDoseEvents(start: Long, end: Long, medicationIds: Set<Long>): List<DoseEvent> {
        val logs = doseLogDao.getLogsForDateRangeSync(start, end)
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        return logs
            .filter { it.status != "pending" && it.medicationId in medicationIds }
            .take(30)
            .map { log ->
                val med = medicationDao.getMedicationById(log.medicationId)
                val delayMs = if (log.actionTime != null && log.actionTime > log.scheduledTime)
                    log.actionTime - log.scheduledTime else 0L
                DoseEvent(
                    medicationName = med?.name ?: "Unknown",
                    scheduledTime = timeFormat.format(Date(log.scheduledTime)),
                    status = log.status,
                    delayMinutes = (delayMs / 60000).toInt(),
                    snoozeCount = log.snoozeCount
                )
            }
    }

    private suspend fun buildTimeOfDayBreakdown(start: Long, end: Long): TimeOfDayBreakdown {
        val morningTaken = doseLogDao.getTakenCountByHourRange(start, end, 5, 11)
        val morningTotal = doseLogDao.getTotalCountByHourRange(start, end, 5, 11)
        val afternoonTaken = doseLogDao.getTakenCountByHourRange(start, end, 12, 16)
        val afternoonTotal = doseLogDao.getTotalCountByHourRange(start, end, 12, 16)
        val eveningTaken = doseLogDao.getTakenCountByHourRange(start, end, 17, 20)
        val eveningTotal = doseLogDao.getTotalCountByHourRange(start, end, 17, 20)
        val nightTaken = doseLogDao.getTakenCountByHourRange(start, end, 21, 23) +
                doseLogDao.getTakenCountByHourRange(start, end, 0, 4)
        val nightTotal = doseLogDao.getTotalCountByHourRange(start, end, 21, 23) +
                doseLogDao.getTotalCountByHourRange(start, end, 0, 4)

        return TimeOfDayBreakdown(
            morningRate = if (morningTotal > 0) morningTaken.toFloat() / morningTotal * 100f else 0f,
            afternoonRate = if (afternoonTotal > 0) afternoonTaken.toFloat() / afternoonTotal * 100f else 0f,
            eveningRate = if (eveningTotal > 0) eveningTaken.toFloat() / eveningTotal * 100f else 0f,
            nightRate = if (nightTotal > 0) nightTaken.toFloat() / nightTotal * 100f else 0f
        )
    }
}
