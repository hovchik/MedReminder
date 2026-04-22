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
        const val COMBINED_NOTIFICATION_ID = 9999
        const val TAG = "AlarmReceiver"
        private const val TOLERANCE_MINUTES = 2L
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

                // Query for all concurrent pending dose logs within tolerance window
                val now = System.currentTimeMillis()
                val windowStart = now - TOLERANCE_MINUTES * 60 * 1000
                val windowEnd = now + TOLERANCE_MINUTES * 60 * 1000
                val concurrentDoseLogs = db.doseLogDao().getPendingDoseLogsInWindow(windowStart, windowEnd)

                // Build arrays of medication data for all concurrent alarms
                val doseLogIds = mutableListOf<Long>()
                val scheduleIds = mutableListOf<Long>()
                val medicationIds = mutableListOf<Long>()
                val medicationNames = mutableListOf<String>()
                val medicationDosages = mutableListOf<String>()
                val medicationColors = mutableListOf<String>()

                for (doseLog in concurrentDoseLogs) {
                    val med = db.medicationDao().getMedicationById(doseLog.medicationId)
                    if (med != null && med.isActive) {
                        doseLogIds.add(doseLog.id)
                        scheduleIds.add(doseLog.scheduleId)
                        medicationIds.add(doseLog.medicationId)
                        medicationNames.add(med.name)
                        medicationDosages.add("${med.dosage} ${med.dosageUnit}")
                        medicationColors.add(med.color)
                    }
                }

                // Fallback: if query returned nothing (edge case), use the current alarm data
                if (doseLogIds.isEmpty()) {
                    doseLogIds.add(doseLogId)
                    scheduleIds.add(scheduleId)
                    medicationIds.add(medicationId)
                    medicationNames.add(medicationName)
                    medicationDosages.add(medicationDosage)
                    medicationColors.add(medicationColor)
                }

                withContext(Dispatchers.Main) {
                    createNotificationChannel(context)
                    showFullScreenAlarm(
                        context,
                        doseLogIds.toLongArray(),
                        scheduleIds.toLongArray(),
                        medicationIds.toLongArray(),
                        medicationNames.toTypedArray(),
                        medicationDosages.toTypedArray(),
                        medicationColors.toTypedArray()
                    )
                    showNotification(
                        context,
                        doseLogIds.toLongArray(),
                        scheduleIds.toLongArray(),
                        medicationIds.toLongArray(),
                        medicationNames.toTypedArray(),
                        medicationDosages.toTypedArray(),
                        medicationColors.toTypedArray()
                    )
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
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(channel)
    }

    private fun showFullScreenAlarm(
        context: Context,
        doseLogIds: LongArray,
        scheduleIds: LongArray,
        medicationIds: LongArray,
        names: Array<String>,
        dosages: Array<String>,
        colors: Array<String>
    ) {
        val alarmIntent = Intent(context, FullScreenAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(FullScreenAlarmActivity.EXTRA_DOSE_LOG_IDS, doseLogIds)
            putExtra(FullScreenAlarmActivity.EXTRA_SCHEDULE_IDS, scheduleIds)
            putExtra(FullScreenAlarmActivity.EXTRA_MEDICATION_IDS, medicationIds)
            putExtra(FullScreenAlarmActivity.EXTRA_MEDICATION_NAMES, names)
            putExtra(FullScreenAlarmActivity.EXTRA_MEDICATION_DOSAGES, dosages)
            putExtra(FullScreenAlarmActivity.EXTRA_MEDICATION_COLORS, colors)
        }
        context.startActivity(alarmIntent)
    }

    private fun showNotification(
        context: Context,
        doseLogIds: LongArray,
        scheduleIds: LongArray,
        medicationIds: LongArray,
        names: Array<String>,
        dosages: Array<String>,
        colors: Array<String>
    ) {
        val notificationId = if (doseLogIds.size > 1) {
            COMBINED_NOTIFICATION_ID
        } else {
            NOTIFICATION_ID_BASE + (doseLogIds.first().toInt() and 0xFFFF)
        }

        // Use stable request code base to avoid Int overflow (notificationId * 10 could overflow)
        val requestCodeBase = (notificationId and 0x0FFFFFFF) * 4

        // Taken action
        val takenIntent = Intent(context, TakenReceiver::class.java).apply {
            putExtra(FullScreenAlarmActivity.EXTRA_DOSE_LOG_IDS, doseLogIds)
            putExtra(FullScreenAlarmActivity.EXTRA_MEDICATION_IDS, medicationIds)
            putExtra(FullScreenAlarmActivity.EXTRA_MEDICATION_NAMES, names)
        }
        val takenPending = PendingIntent.getBroadcast(
            context, requestCodeBase, takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze action
        val snoozeIntent = Intent(context, SnoozeReceiver::class.java).apply {
            putExtra(FullScreenAlarmActivity.EXTRA_DOSE_LOG_IDS, doseLogIds)
            putExtra(FullScreenAlarmActivity.EXTRA_SCHEDULE_IDS, scheduleIds)
            putExtra(FullScreenAlarmActivity.EXTRA_MEDICATION_IDS, medicationIds)
            putExtra(FullScreenAlarmActivity.EXTRA_MEDICATION_NAMES, names)
            putExtra(FullScreenAlarmActivity.EXTRA_MEDICATION_DOSAGES, dosages)
            putExtra(FullScreenAlarmActivity.EXTRA_MEDICATION_COLORS, colors)
        }
        val snoozePending = PendingIntent.getBroadcast(
            context, requestCodeBase + 1, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel action
        val cancelIntent = Intent(context, CancelReceiver::class.java).apply {
            putExtra(FullScreenAlarmActivity.EXTRA_DOSE_LOG_IDS, doseLogIds)
            putExtra(FullScreenAlarmActivity.EXTRA_MEDICATION_IDS, medicationIds)
            putExtra(FullScreenAlarmActivity.EXTRA_MEDICATION_NAMES, names)
        }
        val cancelPending = PendingIntent.getBroadcast(
            context, requestCodeBase + 2, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Full-screen intent
        val fullScreenIntent = Intent(context, FullScreenAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(FullScreenAlarmActivity.EXTRA_DOSE_LOG_IDS, doseLogIds)
            putExtra(FullScreenAlarmActivity.EXTRA_SCHEDULE_IDS, scheduleIds)
            putExtra(FullScreenAlarmActivity.EXTRA_MEDICATION_IDS, medicationIds)
            putExtra(FullScreenAlarmActivity.EXTRA_MEDICATION_NAMES, names)
            putExtra(FullScreenAlarmActivity.EXTRA_MEDICATION_DOSAGES, dosages)
            putExtra(FullScreenAlarmActivity.EXTRA_MEDICATION_COLORS, colors)
        }
        val fullScreenPending = PendingIntent.getActivity(
            context, requestCodeBase + 3, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (names.size > 1) {
            context.getString(R.string.time_to_take_meds_count, names.size)
        } else {
            context.getString(R.string.time_to_take_med, names.first())
        }
        val contentText = if (names.size > 1) {
            names.joinToString(", ")
        } else {
            dosages.first()
        }

        // Cancel any previous individual notifications for these dose logs
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        for (id in doseLogIds) {
            manager.cancel(NOTIFICATION_ID_BASE + id.toInt())
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPending, true)
            .addAction(R.drawable.ic_check, context.getString(R.string.notification_action_taken), takenPending)
            .addAction(R.drawable.ic_snooze, context.getString(R.string.notification_action_snooze), snoozePending)
            .addAction(R.drawable.ic_close, context.getString(R.string.notification_action_cancel), cancelPending)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .build()

        manager.notify(notificationId, notification)
    }
}
