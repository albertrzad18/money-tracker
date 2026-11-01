package io.github.albertrzad18.moneytracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.albertrzad18.moneytracker.work.Notifications
import io.github.albertrzad18.moneytracker.work.RefreshScheduler
import javax.inject.Inject

@HiltAndroidApp
class MoneyTrackerApp : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        RefreshScheduler.schedulePeriodic(this)
    }
}
