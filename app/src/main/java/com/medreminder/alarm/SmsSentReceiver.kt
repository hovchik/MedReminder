package com.medreminder.alarm

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.medreminder.R

class SmsSentReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_CAREGIVER_NAME = "extra_caregiver_name"
        const val EXTRA_MEDICATION_NAME = "extra_medication_name"
        private const val TAG = "SmsSentReceiver"
        private const val CHANNEL_ID = "sms_failure_channel"
        private const val NOTIFICATION_ID_BASE = 9000
    }

    override fun onReceive(context: Context, intent: Intent) {
        val phone = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: return
        val caregiverName = intent.getStringExtra(EXTRA_CAREGIVER_NAME) ?: ""
        val medicationName = intent.getStringExtra(EXTRA_MEDICATION_NAME) ?: ""

        if (resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "SMS sent successfully to $phone")
            return
        }

        val errorReason = when (resultCode) {
            android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic failure"
            android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE -> "No service"
            android.telephony.SmsManager.RESULT_ERROR_NULL_PDU -> "Null PDU"
            android.telephony.SmsManager.RESULT_ERROR_RADIO_OFF -> "Radio off"
            else -> "Unknown error (code: $resultCode)"
        }

        Log.w(TAG, "SMS to $phone failed: $errorReason. Attempting phone call fallback.")

        // Show a notification so the user can manually initiate the call.
        // Starting activities (ACTION_CALL/ACTION_DIAL) directly from a BroadcastReceiver
        // is restricted on Android 10+ and unreliable from background context.
        showFailureNotification(context, phone, caregiverName, medicationName)
    }

    private fun showFailureNotification(
        context: Context,
        phone: String,
        caregiverName: String,
        medicationName: String
    ) {
        // Show notification so user can manually tap to call
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.sms_failure_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.sms_failure_channel_desc)
            }
            nm.createNotificationChannel(channel)
        }

        // Use ACTION_DIAL (not ACTION_CALL) so it works without CALL_PHONE permission
        // and the user explicitly confirms the call
        val callIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phone")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingCallIntent = android.app.PendingIntent.getActivity(
            context, phone.hashCode(), callIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val displayName = caregiverName.ifBlank { phone }
        val title = context.getString(R.string.sms_failed_title)
        val body = context.getString(R.string.sms_failed_body, displayName, medicationName)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_medication)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingCallIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFICATION_ID_BASE + (phone.hashCode() and 0x7FFFFFFF) % 1000, notification)
    }
}
