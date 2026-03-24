package com.medreminder

import android.app.Application
import androidx.work.Configuration
import com.medreminder.alarm.DailyDoseWorker
import com.medreminder.billing.BillingManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MedReminderApp : Application() {

    @Inject
    lateinit var billingManager: BillingManager

    override fun onCreate() {
        super.onCreate()
        DailyDoseWorker.schedule(this)
        billingManager.initialize()
    }
}
