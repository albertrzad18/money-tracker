package io.github.albertrzad18.moneytracker.work

import io.github.albertrzad18.moneytracker.data.repo.QuoteRepository
import io.github.albertrzad18.moneytracker.data.repo.SettingsRepository
import io.github.albertrzad18.moneytracker.widget.WidgetUpdater
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** One refresh: quotes, NBP rates, alerts and the widget. Used by the worker and by the app. */
@Singleton
class Refresher @Inject constructor(
    private val quotes: QuoteRepository,
    private val settings: SettingsRepository,
    private val alerts: AlertNotifier,
    private val widgetUpdater: WidgetUpdater,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    /** Returns true when every price was downloaded. */
    suspend fun refresh(force: Boolean): Boolean = mutex.withLock {
        settings.markRefreshStarted(clock.instant())
        widgetUpdater.update()
        var completed = false
        var ok = false
        try {
            ok = quotes.refreshQuotes(force)
            quotes.refreshFx()
            alerts.check()
            completed = true
        } finally {
            withContext(NonCancellable) {
                if (completed) settings.markRefreshFinished(clock.instant(), ok) else settings.clearRefreshStarted()
                widgetUpdater.update()
            }
        }
        ok
    }
}
