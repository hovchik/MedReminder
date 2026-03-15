package com.medreminder.ai.local

import com.medreminder.ai.*
import com.medreminder.domain.repository.MedicationRepository
import com.medreminder.util.DateUtils
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailyAnalysisUseCase @Inject constructor(
    private val repository: MedicationRepository,
    private val providerSelector: AiProviderSelector
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
            MedicationSummary(
                name = med.name,
                dosage = "${med.dosage} ${med.dosageUnit}",
                form = med.form.displayName,
                frequency = med.schedules.firstOrNull()?.frequency?.displayName ?: "Unknown",
                instructions = med.instructions
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
            periodDays = 1,
            analysisType = AnalysisType.DAILY
        )

        return provider.generateAnalysis(input)
    }
}
