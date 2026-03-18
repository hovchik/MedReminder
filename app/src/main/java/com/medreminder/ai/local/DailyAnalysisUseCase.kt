package com.medreminder.ai.local

import com.medreminder.ai.*
import com.medreminder.data.local.DoseLogDao
import com.medreminder.data.local.ScheduleDao
import com.medreminder.data.preferences.UserPreferencesManager
import com.medreminder.domain.repository.MedicationRepository
import com.medreminder.util.DateUtils
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailyAnalysisUseCase @Inject constructor(
    private val repository: MedicationRepository,
    private val providerSelector: AiProviderSelector,
    private val doseLogDao: DoseLogDao,
    private val scheduleDao: ScheduleDao,
    private val userPreferencesManager: UserPreferencesManager
) {
    suspend fun analyze(): AnalysisResult {
        val provider = providerSelector.selectProvider()

        val startOfDay = DateUtils.getStartOfDay()
        val endOfDay = DateUtils.getEndOfDay()

        // Gather medication data
        val medications = repository.getActiveMedications().first()
        val adherenceStats = repository.getAdherenceStats(startOfDay, endOfDay)
        val streak = repository.getCurrentStreak()

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

        // Global enriched data
        val totalSnoozed = doseLogDao.getTotalSnoozedCount(startOfDay, endOfDay) ?: 0
        val avgDelayMs = doseLogDao.getAverageDelayMs(startOfDay, endOfDay)
        val timeOfDay = buildTimeOfDayBreakdown(startOfDay, endOfDay)
        val caregivers = repository.getActiveCaregivers().first()
        val familyMembers = repository.getActiveFamilyMembers().first()
        val userName = userPreferencesManager.userName.first()
        val userAge = userPreferencesManager.userAge.first()

        val sortedByAdherence = medicationSummaries.filter {
            it.takenCount + it.missedCount + it.skippedCount > 0
        }.sortedBy { it.adherenceRate }

        val input = AnalysisInput(
            medications = medicationSummaries,
            adherenceRate = adherenceStats.adherenceRate,
            totalDoses = adherenceStats.totalDoses,
            takenDoses = adherenceStats.takenDoses,
            missedDoses = adherenceStats.missedDoses,
            skippedDoses = adherenceStats.skippedDoses,
            currentStreak = streak,
            longestStreak = adherenceStats.longestStreak,
            periodDays = 1,
            analysisType = AnalysisType.DAILY,
            totalSnoozedCount = totalSnoozed,
            averageDelayMinutes = if (avgDelayMs != null && avgDelayMs > 0) avgDelayMs / 60000f else 0f,
            timeOfDayBreakdown = timeOfDay,
            worstMedication = sortedByAdherence.firstOrNull()?.name,
            bestMedication = sortedByAdherence.lastOrNull()?.name,
            userName = userName,
            userAge = userAge,
            hasCaregivers = caregivers.isNotEmpty(),
            caregiverCount = caregivers.size,
            familyMemberCount = familyMembers.size
        )

        return provider.generateAnalysis(input)
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
