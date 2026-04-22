package com.medreminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.medreminder.data.local.ScheduleDao
import com.medreminder.domain.model.toDomain
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduleDao: ScheduleDao
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val EXTRA_SCHEDULE_ID = "schedule_id"
        const val EXTRA_MEDICATION_ID = "medication_id"
        const val EXTRA_MEDICATION_NAME = "medication_name"
        const val EXTRA_MEDICATION_DOSAGE = "medication_dosage"
        const val EXTRA_MEDICATION_COLOR = "medication_color"
        const val EXTRA_DOSE_LOG_ID = "dose_log_id"
        const val TAG = "AlarmScheduler"
    }

    suspend fun scheduleAllAlarms() {
        cancelAllAlarms()
        val schedulesWithMeds = scheduleDao.getAllActiveSchedulesWithMedication()
        for (swm in schedulesWithMeds) {
            val schedule = swm.schedule.toDomain()
            val medication = swm.medication

            if (!medication.isActive || !schedule.isEnabled) continue
            if (schedule.frequency.name == "AS_NEEDED") continue

            val nextAlarmTime = calculateNextAlarmTime(schedule)
            if (nextAlarmTime != null) {
                setAlarm(
                    scheduleId = schedule.id,
                    medicationId = medication.id,
                    medicationName = medication.name,
                    medicationDosage = "${medication.dosage} ${medication.dosageUnit}",
                    medicationColor = medication.color,
                    triggerTime = nextAlarmTime
                )
            }
        }
        Log.d(TAG, "Scheduled ${schedulesWithMeds.size} alarms")
    }

    fun scheduleAlarm(
        scheduleId: Long,
        medicationId: Long,
        medicationName: String,
        medicationDosage: String,
        medicationColor: String,
        timeHour: Int,
        timeMinute: Int
    ) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, timeHour)
            set(Calendar.MINUTE, timeMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        setAlarm(scheduleId, medicationId, medicationName, medicationDosage, medicationColor, calendar.timeInMillis)
    }

    private fun setAlarm(
        scheduleId: Long,
        medicationId: Long,
        medicationName: String,
        medicationDosage: String,
        medicationColor: String,
        triggerTime: Long
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.medreminder.MEDICATION_ALARM"
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra(EXTRA_MEDICATION_NAME, medicationName)
            putExtra(EXTRA_MEDICATION_DOSAGE, medicationDosage)
            putExtra(EXTRA_MEDICATION_COLOR, medicationColor)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (scheduleId and 0x7FFFFFFF).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
                        pendingIntent
                    )
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
                    )
                }
            } else {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
                    pendingIntent
                )
            }
            Log.d(TAG, "Alarm set for $medicationName at ${Date(triggerTime)}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot schedule exact alarm", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
            )
        }
    }

    fun cancelAlarm(scheduleId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.medreminder.MEDICATION_ALARM"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, (scheduleId and 0x7FFFFFFF).toInt(), intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    private suspend fun cancelAllAlarms() {
        val schedules = scheduleDao.getAllActiveSchedules()
        schedules.forEach { cancelAlarm(it.id) }
    }

    fun scheduleSnoozeAlarm(
        doseLogId: Long,
        scheduleId: Long,
        medicationId: Long,
        medicationName: String,
        medicationDosage: String,
        medicationColor: String,
        snoozeMinutes: Int = 10
    ) {
        val triggerTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.medreminder.MEDICATION_ALARM"
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra(EXTRA_MEDICATION_NAME, medicationName)
            putExtra(EXTRA_MEDICATION_DOSAGE, medicationDosage)
            putExtra(EXTRA_MEDICATION_COLOR, medicationColor)
            putExtra(EXTRA_DOSE_LOG_ID, doseLogId)
        }

        // Use a distinct high range to avoid colliding with regular alarm request codes
        // which use (scheduleId and 0x7FFFFFFF)
        val requestCode = ((0x40000000L + scheduleId * 100 + doseLogId % 100) and 0x7FFFFFFF).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
                pendingIntent
            )
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    private fun calculateNextAlarmTime(schedule: com.medreminder.domain.model.Schedule): Long? {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, schedule.timeHour)
            set(Calendar.MINUTE, schedule.timeMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        when (schedule.frequency) {
            com.medreminder.domain.model.ScheduleFrequency.DAILY -> {
                if (calendar.timeInMillis <= System.currentTimeMillis()) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            com.medreminder.domain.model.ScheduleFrequency.SPECIFIC_DAYS -> {
                if (schedule.daysOfWeek.isEmpty()) return null
                // If today's time has already passed, start the search from tomorrow.
                if (calendar.timeInMillis <= System.currentTimeMillis()) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
                var found = false
                for (i in 0..6) {
                    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                    if (schedule.daysOfWeek.contains(dayOfWeek)) {
                        found = true
                        break
                    }
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
                if (!found) return null
            }
            com.medreminder.domain.model.ScheduleFrequency.INTERVAL -> {
                if (schedule.intervalDays <= 0) return null
                if (calendar.timeInMillis <= System.currentTimeMillis()) {
                    val daysSinceStart = ((System.currentTimeMillis() - schedule.startDate) /
                            (24 * 60 * 60 * 1000)).toInt()
                    val nextInterval = ((daysSinceStart / schedule.intervalDays) + 1) * schedule.intervalDays
                    calendar.timeInMillis = schedule.startDate
                    calendar.add(Calendar.DAY_OF_YEAR, nextInterval)
                    calendar.set(Calendar.HOUR_OF_DAY, schedule.timeHour)
                    calendar.set(Calendar.MINUTE, schedule.timeMinute)
                }
            }
            com.medreminder.domain.model.ScheduleFrequency.EVERY_X_HOURS -> {
                if (schedule.intervalHours <= 0) return null
                val now = System.currentTimeMillis()
                val intervalMs = schedule.intervalHours * 60 * 60 * 1000L
                if (calendar.timeInMillis <= now) {
                    val elapsed = now - calendar.timeInMillis
                    val periods = (elapsed / intervalMs) + 1
                    calendar.timeInMillis = calendar.timeInMillis + periods * intervalMs
                }
            }
            com.medreminder.domain.model.ScheduleFrequency.AS_NEEDED -> return null
        }

        // Check end date
        schedule.endDate?.let {
            if (calendar.timeInMillis > it) return null
        }

        return calendar.timeInMillis
    }
}
