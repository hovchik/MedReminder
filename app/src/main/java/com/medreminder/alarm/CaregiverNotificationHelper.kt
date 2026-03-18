package com.medreminder.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.medreminder.R
import com.medreminder.data.local.AppDatabase
import com.medreminder.domain.model.toDomain

object CaregiverNotificationHelper {

    private const val TAG = "CaregiverNotify"
    private const val CHANNEL_ID = "sms_failure_channel"
    private const val NOTIFICATION_ID_BASE = 9000

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
            Log.w(TAG, "SEND_SMS permission not granted for $phone")
            showCallNotification(context, phone, caregiverName, medicationName)
            return
        }
        try {
            val smsManager = getSmsManager(context)

            if (smsManager == null) {
                Log.e(TAG, "SmsManager not available for $phone")
                showCallNotification(context, phone, caregiverName, medicationName)
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
            Log.e(TAG, "Failed to send SMS to $phone", e)
            showCallNotification(context, phone, caregiverName, medicationName)
        }
    }

    /**
     * Obtain the platform SmsManager, handling API-level differences.
     *
     * Android 12 (API 31) deprecated the static SmsManager.getDefault() in favour
     * of Context.getSystemService(SmsManager::class.java), which returns an instance
     * bound to the default subscription.  On earlier API levels we fall back to the
     * static method.  If the device has no telephony at all we return null.
     */
    private fun getSmsManager(context: Context): SmsManager? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Prefer the context-based API on Android 12+
                val mgr = context.getSystemService(SmsManager::class.java)
                if (mgr != null) return mgr

                // Fallback: try to create one for the default subscription
                val subId = SubscriptionManager.getDefaultSmsSubscriptionId()
                if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(subId)
                } else {
                    null
                }
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obtaining SmsManager", e)
            null
        }
    }

    /**
     * Show a high-priority notification that lets the user tap to call the caregiver.
     *
     * On Android 10+ we cannot start Activities from a background context (receiver/service),
     * so instead of calling context.startActivity() directly, we always post a notification
     * with a PendingIntent that opens the dialer or places a call when tapped.
     */
    fun showCallNotification(
        context: Context,
        phone: String,
        caregiverName: String,
        medicationName: String
    ) {
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
        // and is not restricted by background activity start limitations via PendingIntent
        val callAction = if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            Intent(Intent.ACTION_CALL).apply { data = Uri.parse("tel:$phone") }
        } else {
            Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:$phone") }
        }
        callAction.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val pendingCallIntent = PendingIntent.getActivity(
            context, phone.hashCode(), callAction,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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

        nm.notify(NOTIFICATION_ID_BASE + phone.hashCode(), notification)
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
            // Starting an activity from background fails on Android 10+.
            // Email is a secondary notification channel, so just log the failure.
            Log.e(TAG, "Failed to send email to $email (background restriction or no email app)", e)
        }
    }
}
