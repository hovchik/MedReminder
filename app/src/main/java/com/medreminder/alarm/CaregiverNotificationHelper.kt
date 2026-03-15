package com.medreminder.alarm

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
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

        var message = if (isMissed) {
            "MedReminder: $medicationName dose was missed."
        } else {
            "MedReminder: $medicationName dose was taken."
        }

        // For emergency medications that are missed, append location
        if (medication.isEmergency && isMissed) {
            val locationText = getLastKnownLocation(context)
            if (locationText != null) {
                message += " EMERGENCY - Location: $locationText"
            } else {
                message += " EMERGENCY - Location unavailable."
            }
        }

        for (caregiver in caregivers) {
            val domain = caregiver.toDomain()
            if (domain.phone.isNotBlank()) {
                sendSms(context, domain.phone, message)
            }
            if (domain.email.isNotBlank()) {
                val subject = if (medication.isEmergency && isMissed) {
                    "EMERGENCY: MedReminder - $medicationName missed"
                } else {
                    "MedReminder Notification"
                }
                sendEmail(context, domain.email, subject, message)
            }
        }
    }

    private fun getLastKnownLocation(context: Context): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Location permission not granted")
            return null
        }

        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (location != null) {
                val mapsUrl = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
                mapsUrl
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
            null
        }
    }

    private fun sendSms(context: Context, phone: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()
            // Split long messages (SMS limit is 160 chars)
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phone, null, message, null, null)
            }
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
