package com.medreminder.alarm

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.medreminder.data.local.AppDatabase
import com.medreminder.data.local.entity.DoseLogEntity
import com.medreminder.domain.model.ScheduleFrequency
import com.medreminder.domain.model.toDomain
import com.medreminder.util.DateUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.*
import java.util.concurrent.TimeUnit

@HiltWorker
class DailyDoseWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val database: AppDatabase,
    private val alarmScheduler: AlarmScheduler
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "DailyDoseWorker"
        const val WORK_NAME = "daily_dose_generation"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build()

            // Run every day at midnight
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 1)
                set(Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            val initialDelay = cal.timeInMillis - System.currentTimeMillis()

            val workRequest = PeriodicWorkRequestBuilder<DailyDoseWorker>(
                1, TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d(TAG, "Daily worker scheduled")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Running daily dose generation")

            // Mark yesterday's pending as missed and notify caregivers
            val startOfToday = DateUtils.getStartOfDay()
            val pendingBeforeCutoff = database.doseLogDao().getPendingDoses(startOfToday - 3600000)
            database.doseLogDao().markOverdueDosesAsMissed(startOfToday - 3600000)

            for (dose in pendingBeforeCutoff) {
                val med = database.medicationDao().getMedicationById(dose.medicationId)
                if (med != null) {
                    CaregiverNotificationHelper.notifyCaregiversOnMissed(
                        applicationContext, database, med.id, med.name
                    )
                }
            }

            // Generate today's doses
            generateTodayDoses()

            // Reschedule all alarms
            alarmScheduler.scheduleAllAlarms()

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate daily doses", e)
            Result.retry()
        }
    }

    private suspend fun generateTodayDoses() {
        val startOfDay = DateUtils.getStartOfDay()
        val endOfDay = DateUtils.getEndOfDay()
        val schedulesWithMeds = database.scheduleDao().getAllActiveSchedulesWithMedication()

        for (swm in schedulesWithMeds) {
            val schedule = swm.schedule.toDomain()
            val medication = swm.medication

            if (!medication.isActive || !schedule.isEnabled) continue
            if (schedule.frequency == ScheduleFrequency.AS_NEEDED) continue

            // Check if schedule applies today
            if (!isScheduledForToday(schedule)) continue

            if (schedule.frequency == ScheduleFrequency.EVERY_X_HOURS && schedule.intervalHours > 0) {
                // Generate multiple dose logs for every-X-hours schedules
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, schedule.timeHour)
                    set(Calendar.MINUTE, schedule.timeMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                while (cal.timeInMillis <= endOfDay) {
                    if (cal.timeInMillis >= startOfDay) {
                        val alreadyExists = database.doseLogDao().doseLogExistsForWindow(
                            schedule.id, cal.timeInMillis - 60000, cal.timeInMillis + 60000
                        )
                        if (!alreadyExists) {
                            database.doseLogDao().insertDoseLog(
                                DoseLogEntity(
                                    medicationId = medication.id,
                                    scheduleId = schedule.id,
                                    scheduledTime = cal.timeInMillis,
                                    status = "pending"
                                )
                            )
                            Log.d(TAG, "Created dose log for ${medication.name} at ${cal.get(Calendar.HOUR_OF_DAY)}:${cal.get(Calendar.MINUTE)}")
                        }
                    }
                    cal.add(Calendar.HOUR_OF_DAY, schedule.intervalHours)
                }
            } else {
                // Single dose per day for other frequencies
                val exists = database.doseLogDao().doseLogExistsForWindow(
                    schedule.id, startOfDay, endOfDay
                )
                if (exists) continue

                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, schedule.timeHour)
                    set(Calendar.MINUTE, schedule.timeMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                database.doseLogDao().insertDoseLog(
                    DoseLogEntity(
                        medicationId = medication.id,
                        scheduleId = schedule.id,
                        scheduledTime = cal.timeInMillis,
                        status = "pending"
                    )
                )
                Log.d(TAG, "Created dose log for ${medication.name} at ${schedule.timeHour}:${schedule.timeMinute}")
            }
        }
    }

    private fun isScheduledForToday(schedule: com.medreminder.domain.model.Schedule): Boolean {
        val today = Calendar.getInstance()

        // Check if schedule has expired (endDate set by duration)
        if (schedule.endDate != null && today.timeInMillis > schedule.endDate) return false

        // Check if schedule has started
        if (today.timeInMillis < schedule.startDate) return false

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
            ScheduleFrequency.EVERY_X_HOURS -> true // always scheduled, alarm handles timing
            ScheduleFrequency.AS_NEEDED -> false
        }
    }
}
