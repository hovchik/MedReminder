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
    val missedCount: Int = 0,
    val skippedCount: Int = 0,
    val upcomingCount: Int = 0,
    val nextDoseTime: Long? = null,
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
        doses.sortedWith(compareBy<DoseLog> { d ->
            when (d.status) {
                DoseStatus.PENDING -> 0
                DoseStatus.SNOOZED -> 1
                DoseStatus.MISSED -> 2
                DoseStatus.TAKEN -> 3
                DoseStatus.SKIPPED -> 4
            }
        }.thenBy { it.scheduledTime })

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
        val sixHoursAhead = System.currentTimeMillis() + 6 * 60 * 60 * 1000
        val upcomingEnd = maxOf(endOfDay, sixHoursAhead) // all of today + up to 6h into tomorrow

        viewModelScope.launch {
            repository.getTodayDoses(startOfDay, upcomingEnd).collect { allDoses ->
                // Show all not-yet-executed doses on the Today screen:
                // PENDING, SNOOZED, and MISSED (still actionable).
                // Only TAKEN and SKIPPED are fully executed → History screen.
                val unexecutedDoses = allDoses.filter {
                    it.status == DoseStatus.PENDING || it.status == DoseStatus.SNOOZED || it.status == DoseStatus.MISSED
                }
                val taken = allDoses.count { it.status == DoseStatus.TAKEN }
                val missed = allDoses.count { it.status == DoseStatus.MISSED }
                val skipped = allDoses.count { it.status == DoseStatus.SKIPPED }
                val total = allDoses.size
                val rate = if (total > 0) taken.toFloat() / total * 100f else 0f

                val now = System.currentTimeMillis()
                val nextDose = unexecutedDoses
                    .filter { it.status == DoseStatus.PENDING && it.scheduledTime > now }
                    .minByOrNull { it.scheduledTime }
                    ?.scheduledTime

                _uiState.update {
                    it.copy(
                        todayDoses = sortDoses(unexecutedDoses),
                        userDoseGroups = buildUserDoseGroups(unexecutedDoses),
                        takenCount = taken,
                        totalCount = total,
                        missedCount = missed,
                        skippedCount = skipped,
                        upcomingCount = unexecutedDoses.size,
                        nextDoseTime = nextDose,
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

    fun getScheduleForDose(dose: DoseLog): Schedule? {
        val meds = _uiState.value.medications
        return meds.flatMap { it.schedules }.find { it.id == dose.scheduleId }
    }

    fun getMedicationForDose(dose: DoseLog): Medication? {
        return _uiState.value.medications.find { it.id == dose.medicationId }
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
            val sixHoursAhead = System.currentTimeMillis() + 6 * 60 * 60 * 1000
            val upcomingEnd = maxOf(endOfDay, sixHoursAhead) // all of today + up to 6h into tomorrow
            val schedules = repository.getAllActiveSchedules()

            for (schedule in schedules) {
                if (schedule.frequency == ScheduleFrequency.AS_NEEDED) continue
                if (!isScheduledForDay(schedule, Calendar.getInstance())) continue

                if (schedule.frequency == ScheduleFrequency.EVERY_X_HOURS && schedule.intervalHours > 0) {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, schedule.timeHour)
                        set(Calendar.MINUTE, schedule.timeMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    while (cal.timeInMillis <= upcomingEnd) {
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
                    val doseTime = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, schedule.timeHour)
                        set(Calendar.MINUTE, schedule.timeMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (doseTime.timeInMillis <= upcomingEnd) {
                        val dayStart = DateUtils.getStartOfDay(doseTime.timeInMillis)
                        val dayEnd = DateUtils.getEndOfDay(doseTime.timeInMillis)
                        val exists = repository.doseLogExistsForWindow(schedule.id, dayStart, dayEnd)
                        if (!exists) {
                            repository.createDoseLog(
                                DoseLog(
                                    medicationId = schedule.medicationId,
                                    scheduleId = schedule.id,
                                    scheduledTime = doseTime.timeInMillis,
                                    status = if (doseTime.timeInMillis < System.currentTimeMillis() - 3600000)
                                        DoseStatus.MISSED else DoseStatus.PENDING
                                )
                            )
                        }
                    }
                }
            }

            // Also generate doses for tomorrow if the +6h window crosses midnight
            val tomorrowCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            if (upcomingEnd > DateUtils.getStartOfDay(tomorrowCal.timeInMillis)) {
                for (schedule in schedules) {
                    if (schedule.frequency == ScheduleFrequency.AS_NEEDED) continue
                    if (schedule.frequency == ScheduleFrequency.EVERY_X_HOURS) continue // already handled above
                    if (!isScheduledForDay(schedule, tomorrowCal)) continue

                    val doseTime = Calendar.getInstance().apply {
                        timeInMillis = tomorrowCal.timeInMillis
                        set(Calendar.HOUR_OF_DAY, schedule.timeHour)
                        set(Calendar.MINUTE, schedule.timeMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (doseTime.timeInMillis <= upcomingEnd) {
                        val dayStart = DateUtils.getStartOfDay(doseTime.timeInMillis)
                        val dayEnd = DateUtils.getEndOfDay(doseTime.timeInMillis)
                        val exists = repository.doseLogExistsForWindow(schedule.id, dayStart, dayEnd)
                        if (!exists) {
                            repository.createDoseLog(
                                DoseLog(
                                    medicationId = schedule.medicationId,
                                    scheduleId = schedule.id,
                                    scheduledTime = doseTime.timeInMillis,
                                    status = DoseStatus.PENDING
                                )
                            )
                        }
                    }
                }
            }

            // Reschedule alarms
            alarmScheduler.scheduleAllAlarms()
        }
    }

    private fun isScheduledForDay(schedule: Schedule, day: Calendar): Boolean {
        return when (schedule.frequency) {
            ScheduleFrequency.DAILY -> true
            ScheduleFrequency.SPECIFIC_DAYS -> {
                day.get(Calendar.DAY_OF_WEEK) in schedule.daysOfWeek
            }
            ScheduleFrequency.INTERVAL -> {
                if (schedule.intervalDays <= 0) return false
                val daysSinceStart = ((day.timeInMillis - schedule.startDate) /
                        (24 * 60 * 60 * 1000)).toInt()
                daysSinceStart >= 0 && daysSinceStart % schedule.intervalDays == 0
            }
            ScheduleFrequency.EVERY_X_HOURS -> true
            ScheduleFrequency.AS_NEEDED -> false
        }
    }
}
