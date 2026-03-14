package com.example.medreminder.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Re-schedule reminders after device reboot
            // In a full implementation, this would read active medications
            // from the database and reschedule their alarms
        }
    }
}
