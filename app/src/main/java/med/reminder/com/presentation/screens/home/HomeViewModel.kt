package med.reminder.com.presentation.screens.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import med.reminder.com.R
import med.reminder.com.alarm.AlarmScheduler
import med.reminder.com.alarm.CaregiverNotificationHelper
import med.reminder.com.data.local.AppDatabase
import med.reminder.com.data.preferences.UserPreferencesManager
import med.reminder.com.domain.model.*
import med.reminder.com.domain.repository.MedicationRepository
import med.reminder.com.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class UserDoseGroup(
    val userId: Long?,       // null = self
    val userName: String,    // display name ("Me" for self, family member name otherwise)
    val photoUri: String? = null,
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
    private val database: AppDatabase,
    private val userPreferencesManager: UserPreferencesManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Trigger to restart the today-data Flow with fresh time bounds
    private val refreshTrigger = MutableStateFlow(0)

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

    private suspend fun buildUserDoseGroups(doses: List<DoseLog>): List<UserDoseGroup> {
        val grouped = doses.groupBy { it.assignedToId }
        val selfLabel = context.getString(R.string.my_medications_label)
        val selfPhotoUri = userPreferencesManager.userPhotoUri.first()
        return grouped.map { (userId, userDoses) ->
            val sorted = sortDoses(userDoses)
            val photoUri = if (userId == null) {
                selfPhotoUri
            } else {
                repository.getFamilyMemberById(userId)?.photoUri
            }
            UserDoseGroup(
                userId = userId,
                userName = if (userId == null) selfLabel else userDoses.first().assignedToName,
                photoUri = photoUri,
                doses = sorted,
                takenCount = userDoses.count { it.status == DoseStatus.TAKEN },
                totalCount = userDoses.size
            )
        }.sortedBy { if (it.userId == null) 0 else 1 } // Self first
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadTodayData() {
        viewModelScope.launch {
            // Restart the Flow with fresh time bounds each time refreshTrigger is updated
            // (e.g. after generateTodayDoses on screen resume or schedule edits)
            refreshTrigger.flatMapLatest {
                val startOfDay = DateUtils.getStartOfDay()
                val endOfDay = DateUtils.getEndOfDay()
                val sixHoursAhead = System.currentTimeMillis() + 6 * 60 * 60 * 1000
                val upcomingEnd = maxOf(endOfDay, sixHoursAhead)
                repository.getTodayDoses(startOfDay, upcomingEnd)
            }.collect { allDoses ->
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
            val doseLog = repository.getDoseLogById(logId)
            repository.markDoseTaken(logId)
            // Send SMS/call to caregivers if conditions are met
            if (doseLog != null) {
                val med = repository.getMedicationById(doseLog.medicationId)
                Log.d(TAG, "markDoseTaken: logId=$logId, medId=${doseLog.medicationId}, " +
                        "medFound=${med != null}, notifyCaregivers=${med?.notifyCaregivers}")
                if (med != null) {
                    CaregiverNotificationHelper.notifyCaregiversOnTaken(
                        context, database, med.id, med.name
                    )
                }
            } else {
                Log.w(TAG, "markDoseTaken: doseLog not found for logId=$logId")
            }
            loadStreak()
        }
    }

    fun markDoseSkipped(logId: Long) {
        viewModelScope.launch {
            val doseLog = repository.getDoseLogById(logId)
            repository.markDoseSkipped(logId)
            if (doseLog != null) {
                val med = repository.getMedicationById(doseLog.medicationId)
                if (med != null) {
                    CaregiverNotificationHelper.notifyCaregiversOnSkipped(
                        context, database, med.id, med.name
                    )
                }
            }
            loadStreak()
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

    /**
     * Fire-and-forget version for lifecycle callbacks.
     */
    fun generateTodayDoses() {
        viewModelScope.launch {
            doGenerateTodayDoses()
        }
    }

    /**
     * Suspending version used by pull-to-refresh so the indicator stays
     * visible until the work is actually complete.
     */
    suspend fun refreshTodayDoses() {
        _isRefreshing.value = true
        try {
            doGenerateTodayDoses()
        } finally {
            _isRefreshing.value = false
        }
    }

    private suspend fun doGenerateTodayDoses() {
        val startOfDay = DateUtils.getStartOfDay()
        val endOfDay = DateUtils.getEndOfDay()
        val sixHoursAhead = System.currentTimeMillis() + 6 * 60 * 60 * 1000
        val upcomingEnd = maxOf(endOfDay, sixHoursAhead) // all of today + up to 6h into tomorrow

        // Remove stale pending/snoozed logs for schedules that were deleted or deactivated
        repository.deleteOrphanPendingLogs(startOfDay, upcomingEnd)

        val schedules = repository.getAllActiveSchedules()

        for (schedule in schedules) {
            if (schedule.frequency == ScheduleFrequency.AS_NEEDED) continue
            if (isScheduleExpired(schedule)) continue
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
                    // Use a narrow window around the specific dose time so that
                    // an old TAKEN/MISSED log at a previous time doesn't block
                    // creation of a new PENDING log at the updated time.
                    val exists = repository.doseLogExistsForWindow(
                        schedule.id, doseTime.timeInMillis - 60000, doseTime.timeInMillis + 60000
                    )
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
                if (isScheduleExpired(schedule)) continue
                if (!isScheduledForDay(schedule, tomorrowCal)) continue

                val doseTime = Calendar.getInstance().apply {
                    timeInMillis = tomorrowCal.timeInMillis
                    set(Calendar.HOUR_OF_DAY, schedule.timeHour)
                    set(Calendar.MINUTE, schedule.timeMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (doseTime.timeInMillis <= upcomingEnd) {
                    val exists = repository.doseLogExistsForWindow(
                        schedule.id, doseTime.timeInMillis - 60000, doseTime.timeInMillis + 60000
                    )
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

        // Trigger a refresh of the today-data Flow with fresh time bounds
        // so the UI picks up any dose logs created or changed by schedule edits
        refreshTrigger.value++
    }

    private fun isScheduleExpired(schedule: Schedule): Boolean {
        val now = System.currentTimeMillis()
        if (schedule.endDate != null && now > schedule.endDate) return true
        if (schedule.durationType != DurationType.ONGOING && schedule.durationValue > 0) {
            val expirationCal = Calendar.getInstance().apply { timeInMillis = schedule.startDate }
            when (schedule.durationType) {
                DurationType.DAYS -> expirationCal.add(Calendar.DAY_OF_YEAR, schedule.durationValue)
                DurationType.MONTHS -> expirationCal.add(Calendar.MONTH, schedule.durationValue)
                else -> {}
            }
            if (now > expirationCal.timeInMillis) return true
        }
        return false
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
