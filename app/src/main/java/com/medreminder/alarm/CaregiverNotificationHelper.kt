package com.medreminder.alarm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import com.medreminder.data.local.AppDatabase
import com.medreminder.domain.model.toDomain

object CaregiverNotificationHelper {

    private const val TAG = "CaregiverNotify"

    suspend fun notifyCaregiversOnMissed(
        context: Context,
        db: AppDatabase,
        medicationId: Long,
        medicationName: String
    ) {
        notifyCaregivers(context, db, medicationId, medicationName, isMissed = true)
    }

    suspend fun notifyCaregiversOnTaken(
        context: Context,
        db: AppDatabase,
        medicationId: Long,
        medicationName: String
    ) {
        notifyCaregivers(context, db, medicationId, medicationName, isMissed = false)
    }

    private suspend fun notifyCaregivers(
        context: Context,
        db: AppDatabase,
        medicationId: Long,
        medicationName: String,
        isMissed: Boolean
    ) {
        val medication = db.medicationDao().getMedicationById(medicationId) ?: return
        if (!medication.notifyCaregivers) return

        val caregivers = if (isMissed) {
            db.caregiverDao().getCaregiversForMissedAlert()
        } else {
            db.caregiverDao().getCaregiversForTakenAlert()
        }

        if (caregivers.isEmpty()) return

        val message = if (isMissed) {
            "MedReminder: $medicationName dose was missed."
        } else {
            "MedReminder: $medicationName dose was taken."
        }

        for (caregiver in caregivers) {
            val domain = caregiver.toDomain()
            if (domain.phone.isNotBlank()) {
                sendSms(context, domain.phone, message)
            }
            if (domain.email.isNotBlank()) {
                sendEmail(context, domain.email, "MedReminder Notification", message)
            }
        }
    }

    private fun sendSms(context: Context, phone: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phone, null, message, null, null)
            Log.d(TAG, "SMS sent to $phone")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $phone", e)
        }
    }

    private fun sendEmail(context: Context, email: String, subject: String, body: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Log.d(TAG, "Email intent launched for $email")
            } else {
                Log.w(TAG, "No email app available for $email")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send email to $email", e)
        }
    }
}
