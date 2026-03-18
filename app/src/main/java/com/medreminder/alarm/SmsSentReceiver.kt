package com.medreminder.alarm

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives the delivery result for each SMS part sent by [CaregiverNotificationHelper].
 *
 * On success (RESULT_OK) it simply logs.  On failure it posts a high-priority
 * notification that lets the user tap to call the caregiver, instead of trying
 * to start a phone-call Activity from the background (which is blocked on
 * Android 10+).
 */
class SmsSentReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_CAREGIVER_NAME = "extra_caregiver_name"
        const val EXTRA_MEDICATION_NAME = "extra_medication_name"
        private const val TAG = "SmsSentReceiver"
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

        Log.w(TAG, "SMS to $phone failed: $errorReason")

        // Show a notification with a tap-to-call action.  We must NOT try to
        // start an Activity directly from a BroadcastReceiver — on Android 10+
        // background activity starts are blocked and the call would fail silently.
        CaregiverNotificationHelper.showCallNotification(
            context.applicationContext, phone, caregiverName, medicationName
        )
    }
}
