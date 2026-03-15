package com.medreminder

import android.app.Application
import androidx.work.Configuration
import com.medreminder.alarm.DailyDoseWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MedReminderApp : Application() {

    override fun onCreate() {
        super.onCreate()
        DailyDoseWorker.schedule(this)
    }
}
