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
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
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
            if (schedule.frequency == com.medreminder.domain.model.ScheduleFrequency.AS_NEEDED) continue

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
            scheduleRequestCode(scheduleId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()

        try {
            if (canExact) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
                    pendingIntent
                )
            } else {
                Log.w(TAG, "SCHEDULE_EXACT_ALARM not granted; alarm for $medicationName may drift")
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
                )
            }
            Log.d(TAG, "Alarm set for $medicationName at ${Date(triggerTime)}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Exact alarm scheduling denied; falling back to inexact", e)
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
            context, scheduleRequestCode(scheduleId), intent,
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

        val pendingIntent = PendingIntent.getBroadcast(
            context, snoozeRequestCode(doseLogId), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()

        try {
            if (canExact) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
                )
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    /**
     * Stable request-code mapping for regular per-schedule alarms.
     * Uses a 32-bit mix that distributes across the int space and is
     * disjoint from [snoozeRequestCode]'s namespace by construction.
     */
    private fun scheduleRequestCode(scheduleId: Long): Int =
        (mix64(scheduleId) and 0x3FFFFFFF).toInt() // top bit 0, second bit 0

    /**
     * Stable request-code mapping for snooze alarms (keyed by doseLogId,
     * which is unique per dose). Uses the 0x4000_0000 namespace bit so
     * it never collides with a regular alarm request code.
     */
    private fun snoozeRequestCode(doseLogId: Long): Int =
        ((mix64(doseLogId) and 0x3FFFFFFF) or 0x40000000).toInt()

    private fun mix64(value: Long): Long {
        // SplitMix64 finalizer — high-quality avalanche, no collisions for distinct longs.
        var z = value
        z = (z xor (z ushr 30)) * -4658895280553007687L  // 0xbf58476d1ce4e5b9
        z = (z xor (z ushr 27)) * -6534734903238641935L  // 0x94d049bb133111eb
        return z xor (z ushr 31)
    }

    private fun calculateNextAlarmTime(schedule: com.medreminder.domain.model.Schedule): Long? {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val time = LocalTime.of(schedule.timeHour, schedule.timeMinute)
        val todayAtTime = now.toLocalDate().atTime(time).atZone(zone)

        val next: ZonedDateTime = when (schedule.frequency) {
            com.medreminder.domain.model.ScheduleFrequency.DAILY -> {
                if (todayAtTime.isAfter(now)) todayAtTime else todayAtTime.plusDays(1)
            }
            com.medreminder.domain.model.ScheduleFrequency.SPECIFIC_DAYS -> {
                if (schedule.daysOfWeek.isEmpty()) return null
                var candidate = if (todayAtTime.isAfter(now)) todayAtTime else todayAtTime.plusDays(1)
                var found = false
                for (i in 0..6) {
                    if (schedule.daysOfWeek.contains(candidate.calendarDayOfWeek())) {
                        found = true
                        break
                    }
                    candidate = candidate.plusDays(1)
                }
                if (!found) return null
                candidate
            }
            com.medreminder.domain.model.ScheduleFrequency.INTERVAL -> {
                if (schedule.intervalDays <= 0) return null
                val startDate = java.time.Instant.ofEpochMilli(schedule.startDate)
                    .atZone(zone).toLocalDate()
                val today = now.toLocalDate()
                val daysSinceStart = ChronoUnit.DAYS.between(startDate, today).toInt()
                if (todayAtTime.isAfter(now) && daysSinceStart >= 0 &&
                    daysSinceStart % schedule.intervalDays == 0
                ) {
                    todayAtTime
                } else {
                    val nextOffset = if (daysSinceStart < 0) 0
                    else ((daysSinceStart / schedule.intervalDays) + 1) * schedule.intervalDays
                    startDate.plusDays(nextOffset.toLong()).atTime(time).atZone(zone)
                }
            }
            com.medreminder.domain.model.ScheduleFrequency.EVERY_X_HOURS -> {
                if (schedule.intervalHours <= 0) return null
                var candidate = todayAtTime
                while (!candidate.isAfter(now)) {
                    candidate = candidate.plusHours(schedule.intervalHours.toLong())
                }
                candidate
            }
            com.medreminder.domain.model.ScheduleFrequency.AS_NEEDED -> return null
        }

        val triggerMillis = next.toInstant().toEpochMilli()
        schedule.endDate?.let { if (triggerMillis > it) return null }
        return triggerMillis
    }

    private fun ZonedDateTime.calendarDayOfWeek(): Int = when (dayOfWeek) {
        DayOfWeek.SUNDAY -> Calendar.SUNDAY
        DayOfWeek.MONDAY -> Calendar.MONDAY
        DayOfWeek.TUESDAY -> Calendar.TUESDAY
        DayOfWeek.WEDNESDAY -> Calendar.WEDNESDAY
        DayOfWeek.THURSDAY -> Calendar.THURSDAY
        DayOfWeek.FRIDAY -> Calendar.FRIDAY
        DayOfWeek.SATURDAY -> Calendar.SATURDAY
    }
}
