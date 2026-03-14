package com.medreminder.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medreminder.alarm.AlarmScheduler
import com.medreminder.domain.model.*
import com.medreminder.domain.repository.MedicationRepository
import com.medreminder.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val todayDoses: List<DoseLog> = emptyList(),
    val medications: List<Medication> = emptyList(),
    val takenCount: Int = 0,
    val totalCount: Int = 0,
    val adherenceRate: Float = 0f,
    val currentStreak: Int = 0,
    val refillAlerts: List<Medication> = emptyList(),
    val greeting: String = "",
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MedicationRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadTodayData()
        loadMedications()
        loadRefillAlerts()
        loadStreak()
        updateGreeting()
    }

    private fun loadTodayData() {
        val startOfDay = DateUtils.getStartOfDay()
        val endOfDay = DateUtils.getEndOfDay()

        viewModelScope.launch {
            repository.getTodayDoses(startOfDay, endOfDay).collect { doses ->
                val taken = doses.count { it.status == DoseStatus.TAKEN }
                val total = doses.size
                val rate = if (total > 0) taken.toFloat() / total * 100f else 0f

                _uiState.update {
                    it.copy(
                        todayDoses = doses.sortedBy { d ->
                            when (d.status) {
                                DoseStatus.PENDING -> 0
                                DoseStatus.SNOOZED -> 1
                                DoseStatus.TAKEN -> 2
                                DoseStatus.SKIPPED -> 3
                                DoseStatus.MISSED -> 4
                            }
                        },
                        takenCount = taken,
                        totalCount = total,
                        adherenceRate = rate,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun loadMedications() {
        viewModelScope.launch {
            repository.getMedicationsWithSchedules().collect { meds ->
                _uiState.update { it.copy(medications = meds) }
            }
        }
    }

    private fun loadRefillAlerts() {
        viewModelScope.launch {
            repository.getMedicationsNeedingRefill().collect { meds ->
                _uiState.update { it.copy(refillAlerts = meds) }
            }
        }
    }

    private fun loadStreak() {
        viewModelScope.launch {
            val streak = repository.getCurrentStreak()
            _uiState.update { it.copy(currentStreak = streak) }
        }
    }

    private fun updateGreeting() {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        _uiState.update { it.copy(greeting = greeting) }
    }

    fun markDoseTaken(logId: Long) {
        viewModelScope.launch {
            repository.markDoseTaken(logId)
            loadStreak()
        }
    }

    fun markDoseSkipped(logId: Long) {
        viewModelScope.launch {
            repository.markDoseSkipped(logId)
        }
    }

    fun snoozeDose(logId: Long) {
        viewModelScope.launch {
            val snoozeUntil = System.currentTimeMillis() + 10 * 60 * 1000
            repository.snoozeDose(logId, snoozeUntil)
        }
    }

    fun generateTodayDoses() {
        viewModelScope.launch {
            val startOfDay = DateUtils.getStartOfDay()
            val endOfDay = DateUtils.getEndOfDay()
            val schedules = repository.getAllActiveSchedules()

            for (schedule in schedules) {
                if (schedule.frequency == ScheduleFrequency.AS_NEEDED) continue

                val exists = repository.doseLogExistsForWindow(schedule.id, startOfDay, endOfDay)
                if (!exists) {
                    val cal = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, schedule.timeHour)
                        set(java.util.Calendar.MINUTE, schedule.timeMinute)
                        set(java.util.Calendar.SECOND, 0)
                    }
                    repository.createDoseLog(
                        DoseLog(
                            medicationId = schedule.medicationId,
                            scheduleId = schedule.id,
                            scheduledTime = cal.timeInMillis,
                            status = if (cal.timeInMillis < System.currentTimeMillis() - 3600000)
                                DoseStatus.MISSED else DoseStatus.PENDING
                        )
                    )
                }
            }
            // Reschedule alarms
            alarmScheduler.scheduleAllAlarms()
        }
    }
}
