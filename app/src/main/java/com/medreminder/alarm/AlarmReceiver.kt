package com.medreminder.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.medreminder.R
import com.medreminder.data.local.AppDatabase
import com.medreminder.data.local.entity.DoseLogEntity
import com.medreminder.util.DateUtils
import kotlinx.coroutines.*

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "medication_alarm"
        const val CHANNEL_NAME = "Medication Alarms"
        const val NOTIFICATION_ID_BASE = 10000
        const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULE_ID, -1)
        val medicationId = intent.getLongExtra(AlarmScheduler.EXTRA_MEDICATION_ID, -1)
        val medicationName = intent.getStringExtra(AlarmScheduler.EXTRA_MEDICATION_NAME) ?: "Medication"
        val medicationDosage = intent.getStringExtra(AlarmScheduler.EXTRA_MEDICATION_DOSAGE) ?: ""
        val medicationColor = intent.getStringExtra(AlarmScheduler.EXTRA_MEDICATION_COLOR) ?: "#4A90D9"
        val existingDoseLogId = intent.getLongExtra(AlarmScheduler.EXTRA_DOSE_LOG_ID, -1)

        Log.d(TAG, "Alarm received for $medicationName (schedule=$scheduleId)")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)

                // Reuse existing dose log if available (from generateTodayDoses or snooze)
                val doseLogId = if (existingDoseLogId > 0) {
                    existingDoseLogId
                } else {
                    // Check if a dose log already exists for this schedule today
                    val startOfDay = DateUtils.getStartOfDay()
                    val endOfDay = DateUtils.getEndOfDay()
                    val existing = db.doseLogDao().findActiveDoseLogForSchedule(
                        scheduleId, startOfDay, endOfDay
                    )
                    existing?.id ?: db.doseLogDao().insertDoseLog(
                        DoseLogEntity(
                            medicationId = medicationId,
                            scheduleId = scheduleId,
                            scheduledTime = System.currentTimeMillis(),
                            status = "pending"
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    createNotificationChannel(context)
                    showFullScreenAlarm(context, doseLogId, scheduleId, medicationId,
                        medicationName, medicationDosage, medicationColor)
                    showNotification(context, doseLogId, scheduleId, medicationId,
                        medicationName, medicationDosage)
                }

                // Reschedule for next occurrence
                val scheduler = AlarmScheduler(context, db.scheduleDao())
                scheduler.scheduleAllAlarms()

            } catch (e: Exception) {
                Log.e(TAG, "Error processing alarm", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Persistent medication reminders"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setBypassDnd(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun showFullScreenAlarm(
        context: Context,
        doseLogId: Long,
        scheduleId: Long,
        medicationId: Long,
        name: String,
        dosage: String,
        color: String
    ) {
        val alarmIntent = Intent(context, FullScreenAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmScheduler.EXTRA_DOSE_LOG_ID, doseLogId)
            putExtra(AlarmScheduler.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(AlarmScheduler.EXTRA_MEDICATION_ID, medicationId)
            putExtra(AlarmScheduler.EXTRA_MEDICATION_NAME, name)
            putExtra(AlarmScheduler.EXTRA_MEDICATION_DOSAGE, dosage)
            putExtra(AlarmScheduler.EXTRA_MEDICATION_COLOR, color)
        }
        context.startActivity(alarmIntent)
    }

    private fun showNotification(
        context: Context,
        doseLogId: Long,
        scheduleId: Long,
        medicationId: Long,
        name: String,
        dosage: String
    ) {
        // Taken action
        val takenIntent = Intent(context, TakenReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_DOSE_LOG_ID, doseLogId)
            putExtra(AlarmScheduler.EXTRA_MEDICATION_NAME, name)
        }
        val takenPending = PendingIntent.getBroadcast(
            context, (doseLogId * 10).toInt(), takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze action
        val snoozeIntent = Intent(context, SnoozeReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_DOSE_LOG_ID, doseLogId)
            putExtra(AlarmScheduler.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(AlarmScheduler.EXTRA_MEDICATION_ID, medicationId)
            putExtra(AlarmScheduler.EXTRA_MEDICATION_NAME, name)
            putExtra(AlarmScheduler.EXTRA_MEDICATION_DOSAGE, dosage)
        }
        val snoozePending = PendingIntent.getBroadcast(
            context, (doseLogId * 10 + 1).toInt(), snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Full-screen intent
        val fullScreenIntent = Intent(context, FullScreenAlarmActivity::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_DOSE_LOG_ID, doseLogId)
            putExtra(AlarmScheduler.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(AlarmScheduler.EXTRA_MEDICATION_ID, medicationId)
            putExtra(AlarmScheduler.EXTRA_MEDICATION_NAME, name)
            putExtra(AlarmScheduler.EXTRA_MEDICATION_DOSAGE, dosage)
        }
        val fullScreenPending = PendingIntent.getActivity(
            context, (doseLogId * 10 + 2).toInt(), fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(context.getString(R.string.time_to_take_med, name))
            .setContentText(dosage)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPending, true)
            .addAction(R.drawable.ic_check, "Taken", takenPending)
            .addAction(R.drawable.ic_snooze, "Snooze 10m", snoozePending)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_BASE + doseLogId.toInt(), notification)
    }
}
