package com.medreminder.presentation.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medreminder.R
import com.medreminder.alarm.AlarmScheduler
import com.medreminder.data.preferences.UserPreferencesManager
import com.medreminder.domain.model.*
import com.medreminder.domain.repository.MedicationRepository
import com.medreminder.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class UserDoseGroup(
    val userId: Long?,       // null = self
    val userName: String,    // display name ("Me" for self, family member name otherwise)
    val doses: List<DoseLog>,
    val takenCount: Int,
    val totalCount: Int
)

data class HomeUiState(
    val todayDoses: List<DoseLog> = emptyList(),
    val userDoseGroups: List<UserDoseGroup> = emptyList(),
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
    private val alarmScheduler: AlarmScheduler,
    private val userPreferencesManager: UserPreferencesManager,
    @ApplicationContext private val context: Context
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

    private fun sortDoses(doses: List<DoseLog>): List<DoseLog> =
        doses.sortedBy { d ->
            when (d.status) {
                DoseStatus.PENDING -> 0
                DoseStatus.SNOOZED -> 1
                DoseStatus.TAKEN -> 2
                DoseStatus.SKIPPED -> 3
                DoseStatus.MISSED -> 4
            }
        }

    private fun buildUserDoseGroups(doses: List<DoseLog>): List<UserDoseGroup> {
        val grouped = doses.groupBy { it.assignedToId }
        val selfLabel = context.getString(R.string.my_medications_label)
        return grouped.map { (userId, userDoses) ->
            val sorted = sortDoses(userDoses)
            UserDoseGroup(
                userId = userId,
                userName = if (userId == null) selfLabel else userDoses.first().assignedToName,
                doses = sorted,
                takenCount = userDoses.count { it.status == DoseStatus.TAKEN },
                totalCount = userDoses.size
            )
        }.sortedBy { if (it.userId == null) 0 else 1 } // Self first
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
                        todayDoses = sortDoses(doses),
                        userDoseGroups = buildUserDoseGroups(doses),
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
        val timeGreeting = when {
            hour < 12 -> context.getString(R.string.greeting_morning)
            hour < 17 -> context.getString(R.string.greeting_afternoon)
            else -> context.getString(R.string.greeting_evening)
        }
        viewModelScope.launch {
            userPreferencesManager.userName.collect { name ->
                val greeting = if (name.isNotBlank()) {
                    context.getString(R.string.greeting_with_name, timeGreeting, name)
                } else {
                    timeGreeting
                }
                _uiState.update { it.copy(greeting = greeting) }
            }
        }
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

    fun snoozeDose(dose: DoseLog) {
        viewModelScope.launch {
            val snoozeUntil = System.currentTimeMillis() + 10 * 60 * 1000
            repository.snoozeDose(dose.id, snoozeUntil)
            alarmScheduler.scheduleSnoozeAlarm(
                doseLogId = dose.id,
                scheduleId = dose.scheduleId,
                medicationId = dose.medicationId,
                medicationName = dose.medicationName,
                medicationDosage = dose.medicationDosage,
                medicationColor = dose.medicationColor,
                snoozeMinutes = 10
            )
        }
    }

    fun generateTodayDoses() {
        viewModelScope.launch {
            val startOfDay = DateUtils.getStartOfDay()
            val endOfDay = DateUtils.getEndOfDay()
            val schedules = repository.getAllActiveSchedules()

            for (schedule in schedules) {
                if (schedule.frequency == ScheduleFrequency.AS_NEEDED) continue
                if (!isScheduledForToday(schedule)) continue

                if (schedule.frequency == ScheduleFrequency.EVERY_X_HOURS && schedule.intervalHours > 0) {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, schedule.timeHour)
                        set(Calendar.MINUTE, schedule.timeMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    while (cal.timeInMillis <= endOfDay) {
                        if (cal.timeInMillis >= startOfDay) {
                            val alreadyExists = repository.doseLogExistsForWindow(
                                schedule.id, cal.timeInMillis - 60000, cal.timeInMillis + 60000
                            )
                            if (!alreadyExists) {
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
                        cal.add(Calendar.HOUR_OF_DAY, schedule.intervalHours)
                    }
                } else {
                    val exists = repository.doseLogExistsForWindow(schedule.id, startOfDay, endOfDay)
                    if (!exists) {
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, schedule.timeHour)
                            set(Calendar.MINUTE, schedule.timeMinute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
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
            }
            // Reschedule alarms
            alarmScheduler.scheduleAllAlarms()
        }
    }

    private fun isScheduledForToday(schedule: Schedule): Boolean {
        val today = Calendar.getInstance()
        return when (schedule.frequency) {
            ScheduleFrequency.DAILY -> true
            ScheduleFrequency.SPECIFIC_DAYS -> {
                today.get(Calendar.DAY_OF_WEEK) in schedule.daysOfWeek
            }
            ScheduleFrequency.INTERVAL -> {
                if (schedule.intervalDays <= 0) return false
                val daysSinceStart = ((today.timeInMillis - schedule.startDate) /
                        (24 * 60 * 60 * 1000)).toInt()
                daysSinceStart >= 0 && daysSinceStart % schedule.intervalDays == 0
            }
            ScheduleFrequency.EVERY_X_HOURS -> true
            ScheduleFrequency.AS_NEEDED -> false
        }
    }
}
