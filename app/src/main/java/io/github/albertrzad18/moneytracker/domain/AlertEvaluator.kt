package io.github.albertrzad18.moneytracker.domain

import java.math.BigDecimal

/**
 * Decides whether a lot crossed its alert thresholds. Each direction fires once and re-arms only
 * after the result moves back by [REARM_MARGIN] percentage points, so a price hovering around
 * the threshold does not send a notification every refresh. A threshold of zero disables that direction.
 */
object AlertEvaluator {
    val REARM_MARGIN: BigDecimal = BigDecimal.ONE

    data class Decision(
        val notifyUp: Boolean,
        val notifyDown: Boolean,
        val upFired: Boolean,
        val downFired: Boolean,
    )

    fun evaluate(
        pct: BigDecimal,
        upPct: BigDecimal,
        downPct: BigDecimal,
        upFired: Boolean,
        downFired: Boolean,
    ): Decision {
        var notifyUp = false
        var newUpFired = false
        if (upPct.signum() > 0) {
            newUpFired = upFired
            if (!upFired && pct >= upPct) {
                notifyUp = true
                newUpFired = true
            } else if (upFired && pct < upPct.subtract(REARM_MARGIN)) {
                newUpFired = false
            }
        }

        var notifyDown = false
        var newDownFired = false
        if (downPct.signum() > 0) {
            val threshold = downPct.negate()
            newDownFired = downFired
            if (!downFired && pct <= threshold) {
                notifyDown = true
                newDownFired = true
            } else if (downFired && pct > threshold.add(REARM_MARGIN)) {
                newDownFired = false
            }
        }
        return Decision(notifyUp, notifyDown, newUpFired, newDownFired)
    }
}
