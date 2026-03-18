package com.medreminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.medreminder.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires 15 minutes after an alarm was shown. If the dose is still pending/snoozed,
 * it notifies caregivers that the dose was missed.
 */
class MissedDoseCheckReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "MissedDoseCheck"
        const val EXTRA_DOSE_LOG_IDS = "missed_check_dose_log_ids"
        const val EXTRA_MEDICATION_IDS = "missed_check_medication_ids"
        const val EXTRA_MEDICATION_NAMES = "missed_check_medication_names"
        const val ACTION = "com.medreminder.MISSED_DOSE_CHECK"
        const val MISSED_CHECK_DELAY_MINUTES = 15
    }

    override fun onReceive(context: Context, intent: Intent) {
        val doseLogIds = intent.getLongArrayExtra(EXTRA_DOSE_LOG_IDS) ?: return
        val medicationIds = intent.getLongArrayExtra(EXTRA_MEDICATION_IDS) ?: return
        val medicationNames = intent.getStringArrayExtra(EXTRA_MEDICATION_NAMES) ?: return

        Log.d(TAG, "Missed dose check fired for ${doseLogIds.size} dose(s)")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)

                for (i in doseLogIds.indices) {
                    val doseLog = db.doseLogDao().getDoseLogById(doseLogIds[i])
                    if (doseLog != null && (doseLog.status == "pending" || doseLog.status == "snoozed")) {
                        Log.d(TAG, "Dose ${doseLogIds[i]} still ${doseLog.status} after $MISSED_CHECK_DELAY_MINUTES min, notifying caregivers")
                        CaregiverNotificationHelper.notifyCaregiversOnMissed(
                            context.applicationContext, db, medicationIds[i], medicationNames[i]
                        )
                    } else {
                        Log.d(TAG, "Dose ${doseLogIds[i]} already handled (status=${doseLog?.status}), skipping caregiver notification")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking missed doses", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
