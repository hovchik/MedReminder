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
        val doseLogId = intent.getLongExtra(AlarmScheduler.EXTRA_DOSE_LOG_ID, -1)
        val scheduleId = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULE_ID, -1)
        val medicationId = intent.getLongExtra(AlarmScheduler.EXTRA_MEDICATION_ID, -1)
        val name = intent.getStringExtra(AlarmScheduler.EXTRA_MEDICATION_NAME) ?: "Medication"
        val dosage = intent.getStringExtra(AlarmScheduler.EXTRA_MEDICATION_DOSAGE) ?: ""
        val color = intent.getStringExtra(AlarmScheduler.EXTRA_MEDICATION_COLOR) ?: "#4A90D9"

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = androidx.room.Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, AppDatabase.DATABASE_NAME
                ).build()
                val snoozeUntil = System.currentTimeMillis() + 10 * 60 * 1000
                db.doseLogDao().snoozeDose(doseLogId, snoozeUntil)

                val scheduler = AlarmScheduler(context.applicationContext, db.scheduleDao())
                scheduler.scheduleSnoozeAlarm(
                    doseLogId, scheduleId, medicationId, name, dosage, color, 10
                )

                val nm = context.getSystemService(NotificationManager::class.java)
                nm.cancel(AlarmReceiver.NOTIFICATION_ID_BASE + doseLogId.toInt())

                withContext(Dispatchers.Main) {
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
        val doseLogId = intent.getLongExtra(AlarmScheduler.EXTRA_DOSE_LOG_ID, -1)
        val name = intent.getStringExtra(AlarmScheduler.EXTRA_MEDICATION_NAME) ?: "Medication"

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = androidx.room.Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, AppDatabase.DATABASE_NAME
                ).build()
                db.doseLogDao().updateDoseStatus(doseLogId, "taken")

                val log = db.doseLogDao().getDoseLogById(doseLogId)
                log?.let { db.medicationDao().decrementStock(it.medicationId) }

                val nm = context.getSystemService(NotificationManager::class.java)
                nm.cancel(AlarmReceiver.NOTIFICATION_ID_BASE + doseLogId.toInt())

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.marked_as_taken, name), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("TakenReceiver", "Error", e)
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
                    val db = androidx.room.Room.databaseBuilder(
                        context.applicationContext, AppDatabase::class.java, AppDatabase.DATABASE_NAME
                    ).build()
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
