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
class WeeklyReportUseCase @Inject constructor(
    private val repository: MedicationRepository,
    private val providerSelector: AiProviderSelector,
    private val doseLogDao: DoseLogDao,
    private val scheduleDao: ScheduleDao,
    private val userPreferencesManager: UserPreferencesManager
) {
    suspend fun analyze(): AnalysisResult {
        val provider = providerSelector.selectProvider()

        val now = System.currentTimeMillis()
        val startOfWeek = DateUtils.daysAgo(7)

        // Gather medication data
        val medications = repository.getActiveMedications().first()
        val adherenceStats = repository.getAdherenceStats(startOfWeek, now)
        val streak = repository.getCurrentStreak()

        val medicationSummaries = medications.map { med ->
            val schedules = med.schedules
            val takenCount = doseLogDao.getTakenCountForMedication(med.id, startOfWeek, now)
            val missedCount = doseLogDao.getMissedCountForMedication(med.id, startOfWeek, now)
            val skippedCount = doseLogDao.getSkippedCountForMedication(med.id, startOfWeek, now)
            val totalCount = doseLogDao.getTotalCountForMedication(med.id, startOfWeek, now)
            val snoozedCount = doseLogDao.getSnoozedCountForMedication(med.id, startOfWeek, now) ?: 0
            val avgDelayMs = doseLogDao.getAverageDelayMsForMedication(med.id, startOfWeek, now)

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

        val weeklyBreakdown = adherenceStats.weeklyData.map { day ->
            DayBreakdown(
                dayName = day.dayName,
                taken = day.taken,
                total = day.total,
                rate = day.rate
            )
        }

        // Global enriched data
        val totalSnoozed = doseLogDao.getTotalSnoozedCount(startOfWeek, now) ?: 0
        val avgDelayMs = doseLogDao.getAverageDelayMs(startOfWeek, now)
        val timeOfDay = buildTimeOfDayBreakdown(startOfWeek, now)
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
            periodDays = 7,
            weeklyBreakdown = weeklyBreakdown,
            analysisType = AnalysisType.WEEKLY,
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
