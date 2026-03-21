package com.medreminder.alarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.medreminder.R
import com.medreminder.data.local.AppDatabase
import kotlinx.coroutines.*

class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val doseLogIds = intent.getLongArrayExtra(FullScreenAlarmActivity.EXTRA_DOSE_LOG_IDS)
        val scheduleIds = intent.getLongArrayExtra(FullScreenAlarmActivity.EXTRA_SCHEDULE_IDS)
        val medicationIds = intent.getLongArrayExtra(FullScreenAlarmActivity.EXTRA_MEDICATION_IDS)
        val names = intent.getStringArrayExtra(FullScreenAlarmActivity.EXTRA_MEDICATION_NAMES)
        val dosages = intent.getStringArrayExtra(FullScreenAlarmActivity.EXTRA_MEDICATION_DOSAGES)
        val colors = intent.getStringArrayExtra(FullScreenAlarmActivity.EXTRA_MEDICATION_COLORS)

        // Fallback to legacy single-medication extras
        val legacyDoseLogId = intent.getLongExtra(AlarmScheduler.EXTRA_DOSE_LOG_ID, -1)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val snoozeUntil = System.currentTimeMillis() + 10 * 60 * 1000
                val scheduler = AlarmScheduler(context.applicationContext, db.scheduleDao())
                val nm = context.getSystemService(NotificationManager::class.java)

                if (doseLogIds != null && scheduleIds != null && medicationIds != null &&
                    names != null && dosages != null && colors != null
                ) {
                    for (i in doseLogIds.indices) {
                        db.doseLogDao().snoozeDose(doseLogIds[i], snoozeUntil)
                        scheduler.scheduleSnoozeAlarm(
                            doseLogIds[i], scheduleIds[i], medicationIds[i],
                            names[i], dosages[i], colors[i], 10
                        )
                        nm.cancel(AlarmReceiver.NOTIFICATION_ID_BASE + doseLogIds[i].toInt())
                    }
                    nm.cancel(AlarmReceiver.COMBINED_NOTIFICATION_ID)
                } else if (legacyDoseLogId > 0) {
                    val scheduleId = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULE_ID, -1)
                    val medicationId = intent.getLongExtra(AlarmScheduler.EXTRA_MEDICATION_ID, -1)
                    val name = intent.getStringExtra(AlarmScheduler.EXTRA_MEDICATION_NAME) ?: "Medication"
                    val dosage = intent.getStringExtra(AlarmScheduler.EXTRA_MEDICATION_DOSAGE) ?: ""
                    val color = intent.getStringExtra(AlarmScheduler.EXTRA_MEDICATION_COLOR) ?: "#4A90D9"

                    db.doseLogDao().snoozeDose(legacyDoseLogId, snoozeUntil)
                    scheduler.scheduleSnoozeAlarm(
                        legacyDoseLogId, scheduleId, medicationId, name, dosage, color, 10
                    )
                    nm.cancel(AlarmReceiver.NOTIFICATION_ID_BASE + legacyDoseLogId.toInt())
                }

                withContext(Dispatchers.Main) {
                    FullScreenAlarmActivity.finishIfShowing()
                    Toast.makeText(context, context.getString(R.string.snoozed_10m), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("SnoozeReceiver", "Error", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class TakenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val doseLogIds = intent.getLongArrayExtra(FullScreenAlarmActivity.EXTRA_DOSE_LOG_IDS)
        val medicationIds = intent.getLongArrayExtra(FullScreenAlarmActivity.EXTRA_MEDICATION_IDS)
        val names = intent.getStringArrayExtra(FullScreenAlarmActivity.EXTRA_MEDICATION_NAMES)

        // Fallback to legacy single-medication extras
        val legacyDoseLogId = intent.getLongExtra(AlarmScheduler.EXTRA_DOSE_LOG_ID, -1)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val nm = context.getSystemService(NotificationManager::class.java)

                if (doseLogIds != null && medicationIds != null && names != null) {
                    for (i in doseLogIds.indices) {
                        db.doseLogDao().updateDoseStatus(doseLogIds[i], "taken")
                        db.medicationDao().decrementStock(medicationIds[i])
                        CaregiverNotificationHelper.notifyCaregiversOnTaken(
                            context.applicationContext, db, medicationIds[i], names[i]
                        )
                        nm.cancel(AlarmReceiver.NOTIFICATION_ID_BASE + doseLogIds[i].toInt())
                    }
                    nm.cancel(AlarmReceiver.COMBINED_NOTIFICATION_ID)

                    val toastMsg = if (names.size > 1) {
                        context.getString(R.string.all_marked_as_taken)
                    } else {
                        context.getString(R.string.marked_as_taken, names.first())
                    }
                    withContext(Dispatchers.Main) {
                        FullScreenAlarmActivity.finishIfShowing()
                        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                    }
                } else if (legacyDoseLogId > 0) {
                    val name = intent.getStringExtra(AlarmScheduler.EXTRA_MEDICATION_NAME) ?: "Medication"
                    db.doseLogDao().updateDoseStatus(legacyDoseLogId, "taken")

                    val log = db.doseLogDao().getDoseLogById(legacyDoseLogId)
                    log?.let {
                        db.medicationDao().decrementStock(it.medicationId)
                        CaregiverNotificationHelper.notifyCaregiversOnTaken(
                            context.applicationContext, db, it.medicationId, name
                        )
                    }

                    nm.cancel(AlarmReceiver.NOTIFICATION_ID_BASE + legacyDoseLogId.toInt())

                    withContext(Dispatchers.Main) {
                        FullScreenAlarmActivity.finishIfShowing()
                        Toast.makeText(context, context.getString(R.string.marked_as_taken, name), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("TakenReceiver", "Error", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class CancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val doseLogIds = intent.getLongArrayExtra(FullScreenAlarmActivity.EXTRA_DOSE_LOG_IDS)
        val medicationIds = intent.getLongArrayExtra(FullScreenAlarmActivity.EXTRA_MEDICATION_IDS)
        val names = intent.getStringArrayExtra(FullScreenAlarmActivity.EXTRA_MEDICATION_NAMES)

        // Fallback to legacy single-medication extras
        val legacyDoseLogId = intent.getLongExtra(AlarmScheduler.EXTRA_DOSE_LOG_ID, -1)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val nm = context.getSystemService(NotificationManager::class.java)

                if (doseLogIds != null && medicationIds != null && names != null) {
                    for (i in doseLogIds.indices) {
                        db.doseLogDao().updateDoseStatus(doseLogIds[i], "skipped")
                        CaregiverNotificationHelper.notifyCaregiversOnSkipped(
                            context.applicationContext, db, medicationIds[i], names[i]
                        )
                        nm.cancel(AlarmReceiver.NOTIFICATION_ID_BASE + doseLogIds[i].toInt())
                    }
                    nm.cancel(AlarmReceiver.COMBINED_NOTIFICATION_ID)
                } else if (doseLogIds != null) {
                    for (i in doseLogIds.indices) {
                        db.doseLogDao().updateDoseStatus(doseLogIds[i], "skipped")
                        val log = db.doseLogDao().getDoseLogById(doseLogIds[i])
                        log?.let {
                            val med = db.medicationDao().getMedicationById(it.medicationId)
                            CaregiverNotificationHelper.notifyCaregiversOnSkipped(
                                context.applicationContext, db, it.medicationId, med?.name ?: "Medication"
                            )
                        }
                        nm.cancel(AlarmReceiver.NOTIFICATION_ID_BASE + doseLogIds[i].toInt())
                    }
                    nm.cancel(AlarmReceiver.COMBINED_NOTIFICATION_ID)
                } else if (legacyDoseLogId > 0) {
                    db.doseLogDao().updateDoseStatus(legacyDoseLogId, "skipped")
                    val log = db.doseLogDao().getDoseLogById(legacyDoseLogId)
                    log?.let {
                        val med = db.medicationDao().getMedicationById(it.medicationId)
                        CaregiverNotificationHelper.notifyCaregiversOnSkipped(
                            context.applicationContext, db, it.medicationId, med?.name ?: "Medication"
                        )
                    }
                    nm.cancel(AlarmReceiver.NOTIFICATION_ID_BASE + legacyDoseLogId.toInt())
                }

                withContext(Dispatchers.Main) {
                    FullScreenAlarmActivity.finishIfShowing()
                    Toast.makeText(context, context.getString(R.string.schedule_cancelled), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("CancelReceiver", "Error", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Log.d("BootReceiver", "Rescheduling all alarms after boot/update")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    val scheduler = AlarmScheduler(context.applicationContext, db.scheduleDao())
                    scheduler.scheduleAllAlarms()
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error rescheduling", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
