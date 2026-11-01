package io.github.albertrzad18.moneytracker.work

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.albertrzad18.moneytracker.data.repo.PortfolioRepository
import io.github.albertrzad18.moneytracker.data.repo.SettingsRepository
import io.github.albertrzad18.moneytracker.domain.AlertEvaluator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val portfolio: PortfolioRepository,
    private val settings: SettingsRepository,
) {
    suspend fun check() {
        val current = settings.current()
        if (!current.alertsEnabled) return
        for (position in portfolio.currentPositions()) {
            for (result in position.lots) {
                val pct = result.pct ?: continue
                val lot = result.lot
                val up = lot.alertUpPct ?: current.defaultAlertUpPct
                val down = lot.alertDownPct ?: current.defaultAlertDownPct
                val decision = AlertEvaluator.evaluate(pct, up, down, lot.alertUpFired, lot.alertDownFired)
                if (decision.notifyUp) Notifications.showAlert(context, position, lot, pct, up, rising = true)
                if (decision.notifyDown) Notifications.showAlert(context, position, lot, pct, down, rising = false)
                if (decision.upFired != lot.alertUpFired || decision.downFired != lot.alertDownFired) {
                    portfolio.setAlertState(lot.id, decision.upFired, decision.downFired)
                }
            }
        }
    }
}
