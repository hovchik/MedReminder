package com.medreminder.alarm

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.os.Build
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

    private fun timeFormatter(): java.time.format.DateTimeFormatter =
        java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.getDefault())

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

    suspend fun notifyCaregiversOnSkipped(
        context: Context,
        db: AppDatabase,
        medicationId: Long,
        medicationName: String
    ) {
        notifyCaregivers(context, db, medicationId, medicationName, isMissed = false, isSkipped = true)
    }

    private suspend fun notifyCaregivers(
        context: Context,
        db: AppDatabase,
        medicationId: Long,
        medicationName: String,
        isMissed: Boolean,
        isSkipped: Boolean = false
    ) {
        Log.d(TAG, "notifyCaregivers: medId=$medicationId, name=$medicationName, isMissed=$isMissed, isSkipped=$isSkipped")
        val medication = db.medicationDao().getMedicationById(medicationId)
        if (medication == null) {
            Log.w(TAG, "notifyCaregivers: medication not found for id=$medicationId")
            return
        }
        if (!medication.notifyCaregivers) {
            Log.d(TAG, "notifyCaregivers: notifyCaregivers=false for '$medicationName', skipping")
            return
        }

        val caregivers = when {
            isMissed -> db.caregiverDao().getCaregiversForMissedAlert()
            isSkipped -> db.caregiverDao().getCaregiversForCancelledAlert()
            else -> db.caregiverDao().getCaregiversForTakenAlert()
        }

        Log.d(TAG, "notifyCaregivers: found ${caregivers.size} caregivers (isMissed=$isMissed, isSkipped=$isSkipped)")
        if (caregivers.isEmpty()) return

        val now = timeFormatter().format(
            java.time.Instant.now().atZone(java.time.ZoneId.systemDefault())
        )
        val dosageInfo = if (medication.dosage.isNotBlank()) " (${medication.dosage}${if (medication.dosageUnit.isNotBlank()) " ${medication.dosageUnit}" else ""})" else ""

        var message = when {
            isMissed -> "MedReminder: $medicationName$dosageInfo was MISSED at $now. Please check on the patient."
            isSkipped -> "MedReminder: $medicationName$dosageInfo was skipped at $now. The patient declined this dose."
            else -> "MedReminder: $medicationName$dosageInfo was taken at $now. No action needed."
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
            val sanitizedPhone = sanitizePhoneNumber(domain.phone)
            if (sanitizedPhone.isNotBlank()) {
                sendSms(context, sanitizedPhone, message, domain.name, medicationName)
            }
            if (domain.email.isNotBlank()) {
                val subject = when {
                    medication.isEmergency && isMissed -> "EMERGENCY: MedReminder - $medicationName missed"
                    isSkipped -> "MedReminder - $medicationName skipped"
                    else -> "MedReminder Notification"
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
                ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    locationManager.getLastKnownLocation(LocationManager.FUSED_PROVIDER)
                } else null

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

    private fun sanitizePhoneNumber(phone: String): String {
        // Strip everything except digits and leading +
        val stripped = phone.trim()
        if (stripped.isEmpty()) return ""
        val prefix = if (stripped.startsWith("+")) "+" else ""
        val digits = stripped.filter { it.isDigit() }
        if (digits.length < 7) return "" // minimum length for a valid phone number
        return prefix + digits
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
        // On Android 10+ starting activities from background is restricted.
        // Show a notification instead so the user can tap to call.
        try {
            val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return

            val channelId = "caregiver_call_fallback"
            val channel = android.app.NotificationChannel(
                channelId,
                "Caregiver Call Fallback",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channel)

            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phone")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pendingIntent = PendingIntent.getActivity(
                context, phone.hashCode() and 0x7FFFFFFF, dialIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(com.medreminder.R.drawable.ic_medication)
                .setContentTitle("SMS failed — tap to call caregiver")
                .setContentText("Could not send SMS to $phone. Tap to call instead.")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            nm.notify(8000 + (phone.hashCode() and 0x7FFFFFFF) % 1000, notification)
            Log.d(TAG, "Fallback notification shown for $phone")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show fallback notification for $phone", e)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun sendEmail(context: Context, email: String, subject: String, body: String) {
        // Email sending from background (BroadcastReceivers) would pop up the email
        // compose UI unexpectedly. Instead, log the notification for now.
        // A proper implementation would use a server-side email API or WorkManager
        // to queue email notifications.
        Log.i(TAG, "Email notification for $email: subject='$subject', body='$body'")
    }
}
