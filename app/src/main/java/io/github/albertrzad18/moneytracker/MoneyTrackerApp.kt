package io.github.albertrzad18.moneytracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.albertrzad18.moneytracker.di.ApplicationScope
import io.github.albertrzad18.moneytracker.widget.WidgetUpdater
import io.github.albertrzad18.moneytracker.work.Notifications
import io.github.albertrzad18.moneytracker.work.RefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MoneyTrackerApp : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var widgetUpdater: WidgetUpdater

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        RefreshScheduler.schedulePeriodic(this)
    }

    /** A new language (in-app or system) or day/night: redraw the widgets with the new resources. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        appScope.launch { widgetUpdater.update() }
    }
}
