package com.medreminder.alarm

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
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
        Log.d(TAG, "notifyCaregivers: medId=$medicationId, name=$medicationName, isMissed=$isMissed")
        val medication = db.medicationDao().getMedicationById(medicationId)
        if (medication == null) {
            Log.w(TAG, "notifyCaregivers: medication not found for id=$medicationId")
            return
        }
        if (!medication.notifyCaregivers) {
            Log.d(TAG, "notifyCaregivers: notifyCaregivers=false for '$medicationName', skipping")
            return
        }

        val caregivers = if (isMissed) {
            db.caregiverDao().getCaregiversForMissedAlert()
        } else {
            db.caregiverDao().getCaregiversForTakenAlert()
        }

        Log.d(TAG, "notifyCaregivers: found ${caregivers.size} caregivers (isMissed=$isMissed)")
        if (caregivers.isEmpty()) return

        var message = if (isMissed) {
            "MedReminder: $medicationName dose was missed."
        } else {
            "MedReminder: $medicationName dose was taken."
        }

        // For emergency medications, append location for both taken and missed
        if (medication.isEmergency) {
            val locationText = getLastKnownLocation(context)
            if (locationText != null) {
                message += if (isMissed) {
                    " EMERGENCY - Location: $locationText"
                } else {
                    " Location: $locationText"
                }
            } else if (isMissed) {
                message += " EMERGENCY - Location unavailable."
            }
        }

        for (caregiver in caregivers) {
            val domain = caregiver.toDomain()
            Log.d(TAG, "notifyCaregivers: caregiver='${domain.name}', phone='${domain.phone}', email='${domain.email}'")
            if (domain.phone.isNotBlank()) {
                sendSms(context, domain.phone, message, domain.name, medicationName)
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
                ?: locationManager.getLastKnownLocation(LocationManager.FUSED_PROVIDER)

            if (location != null) {
                "https://maps.google.com/?q=${location.latitude},${location.longitude}"
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
            null
        }
    }

    private fun sendSms(
        context: Context,
        phone: String,
        message: String,
        caregiverName: String,
        medicationName: String
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "SEND_SMS permission not granted, falling back to call for $phone")
            makeCallFallback(context, phone)
            return
        }
        try {
            @Suppress("DEPRECATION")
            val smsManager: SmsManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
            if (smsManager == null) {
                Log.e(TAG, "SmsManager unavailable, falling back to call for $phone")
                makeCallFallback(context, phone)
                return
            }
            val parts = smsManager.divideMessage(message)

            // Create sent PendingIntent per part to track delivery
            val sentIntents = ArrayList<PendingIntent>(parts.size)
            for (i in parts.indices) {
                val sentIntent = Intent(context, SmsSentReceiver::class.java).apply {
                    putExtra(SmsSentReceiver.EXTRA_PHONE_NUMBER, phone)
                    putExtra(SmsSentReceiver.EXTRA_CAREGIVER_NAME, caregiverName)
                    putExtra(SmsSentReceiver.EXTRA_MEDICATION_NAME, medicationName)
                }
                val requestCode = (phone.hashCode() + i) and 0x7FFFFFFF
                val pi = PendingIntent.getBroadcast(
                    context, requestCode, sentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                sentIntents.add(pi)
            }

            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phone, null, parts, sentIntents, null)
            } else {
                smsManager.sendTextMessage(phone, null, message, sentIntents[0], null)
            }
            Log.d(TAG, "SMS sent to $phone (tracking enabled)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $phone, attempting call fallback", e)
            makeCallFallback(context, phone)
        }
    }

    private fun makeCallFallback(context: Context, phone: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$phone")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)
                Log.d(TAG, "Fallback call initiated to $phone")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initiate fallback call to $phone", e)
            }
        } else {
            Log.w(TAG, "CALL_PHONE permission not granted for fallback call to $phone")
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
