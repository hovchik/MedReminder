package com.medreminder.ai.local

import com.medreminder.ai.*
import com.medreminder.domain.repository.MedicationRepository
import com.medreminder.util.DateUtils
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeeklyReportUseCase @Inject constructor(
    private val repository: MedicationRepository,
    private val providerSelector: AiProviderSelector
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
            MedicationSummary(
                name = med.name,
                dosage = "${med.dosage} ${med.dosageUnit}",
                form = med.form.displayName,
                frequency = med.schedules.firstOrNull()?.frequency?.displayName ?: "Unknown",
                instructions = med.instructions
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

        val input = AnalysisInput(
            medications = medicationSummaries,
            adherenceRate = adherenceStats.adherenceRate,
            totalDoses = adherenceStats.totalDoses,
            takenDoses = adherenceStats.takenDoses,
            missedDoses = adherenceStats.missedDoses,
            skippedDoses = adherenceStats.skippedDoses,
            currentStreak = streak,
            periodDays = 7,
            weeklyBreakdown = weeklyBreakdown,
            analysisType = AnalysisType.WEEKLY
        )

        return provider.generateAnalysis(input)
    }
}
