package med.reminder.com

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import med.reminder.com.alarm.DailyDoseWorker
import med.reminder.com.billing.BillingManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MedReminderApp : Application(), Configuration.Provider {

    @Inject
    lateinit var billingManager: BillingManager

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        DailyDoseWorker.schedule(this)
        billingManager.initialize()

        // Tear down the billing connection (and any pending reconnect callbacks)
        // when the app process moves to the background, so the BillingClient
        // and Handler don't outlive their useful lifetime.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                billingManager.endConnection()
            }

            override fun onStart(owner: LifecycleOwner) {
                billingManager.initialize()
            }
        })
    }
}
