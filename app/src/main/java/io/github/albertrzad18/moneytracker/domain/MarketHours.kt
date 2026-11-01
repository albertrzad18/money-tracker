package io.github.albertrzad18.moneytracker.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object MarketHours {
    private val WARSAW: ZoneId = ZoneId.of("Europe/Warsaw")

    /**
     * Stock and ETF prices do not change at the weekend. Once every stock quote was fetched after
     * Friday's close (Saturday 00:00 in Warsaw is after the US close), periodic refreshes can skip them.
     */
    fun canSkipStocks(now: Instant, oldestStockFetch: Instant?): Boolean {
        if (oldestStockFetch == null) return false
        val local = now.atZone(WARSAW)
        if (local.dayOfWeek != DayOfWeek.SATURDAY && local.dayOfWeek != DayOfWeek.SUNDAY) return false
        val saturdayStart = local.toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
            .atStartOfDay(WARSAW)
            .toInstant()
        return oldestStockFetch >= saturdayStart
    }
}
