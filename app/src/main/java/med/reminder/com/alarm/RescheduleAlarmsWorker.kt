package med.reminder.com.alarm

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import med.reminder.com.data.local.AppDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Lightweight Worker that reschedules all medication alarms.
 *
 * Used instead of doing the (potentially slow) reschedule work inline
 * inside a BroadcastReceiver's `goAsync()` window, which is limited to
 * ~10 seconds before an ANR.  WorkManager is not subject to that limit.
 */
@HiltWorker
class RescheduleAlarmsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val alarmScheduler: AlarmScheduler
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "RescheduleAlarmsWorker"
        private const val WORK_NAME = "reschedule_alarms"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<RescheduleAlarmsWorker>()
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            alarmScheduler.scheduleAllAlarms()
            Log.d(TAG, "Alarms rescheduled successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reschedule alarms", e)
            Result.retry()
        }
    }
}

