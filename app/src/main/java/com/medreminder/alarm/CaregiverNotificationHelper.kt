package com.medreminder.alarm

import android.Manifest
import android.app.PendingIntent
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

        // For missed doses: always notify caregivers who opted in (safety-critical).
        // For taken doses: only notify if the medication has notifyCaregivers enabled.
        if (!isMissed && !medication.notifyCaregivers) return

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

            // Try all available providers and pick the most recent location
            var bestLocation: android.location.Location? = null

            val providers = locationManager.getProviders(true) // only enabled providers
            for (provider in providers) {
                try {
                    val location = locationManager.getLastKnownLocation(provider)
                    if (location != null) {
                        if (bestLocation == null || location.time > bestLocation.time) {
                            bestLocation = location
                        }
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "No permission for provider $provider")
                }
            }

            if (bestLocation != null) {
                "https://maps.google.com/?q=${bestLocation.latitude},${bestLocation.longitude}"
            } else {
                Log.w(TAG, "No location available from any provider (${providers.joinToString()})")
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
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            if (smsManager == null) {
                Log.e(TAG, "SmsManager not available, falling back to call for $phone")
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
                data = Uri.parse("mailto:$email")
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // On Android 11+ resolveActivity() returns null due to package visibility
            // restrictions, so just try to start the activity directly.
            context.startActivity(intent)
            Log.d(TAG, "Email intent launched for $email")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send email to $email (no email app?)", e)
        }
    }
}
