package io.github.albertrzad18.moneytracker.domain

import io.github.albertrzad18.moneytracker.data.db.Lot
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.NavigableMap

/** Portfolio value and invested amount in PLN on a given day. */
data class TimelinePoint(val date: LocalDate, val value: BigDecimal, val invested: BigDecimal)

object PortfolioTimeline {
    /** Above this span the chart uses weekly points to stay light. */
    private const val DAILY_LIMIT_DAYS = 400L

    /**
     * Builds the chart from lots that have a quantity. [prices] is keyed by (instrumentId, currency),
     * [fx] by NBP code. Missing prices or rates for a day fall back to the nearest known value;
     * a lot whose series is missing entirely is skipped.
     */
    fun build(
        lots: List<Lot>,
        prices: Map<Pair<Long, String>, NavigableMap<LocalDate, BigDecimal>>,
        fx: Map<String, NavigableMap<LocalDate, BigDecimal>>,
        from: LocalDate,
        to: LocalDate,
    ): List<TimelinePoint> {
        if (from > to) return emptyList()
        val step = if (ChronoUnit.DAYS.between(from, to) > DAILY_LIMIT_DAYS) 7L else 1L
        val dates = generateSequence(from) { it.plusDays(step) }.takeWhile { it <= to }.toMutableList()
        if (dates.last() != to) dates += to

        return dates.map { date ->
            var value = BigDecimal.ZERO
            var invested = BigDecimal.ZERO
            for (lot in lots) {
                val quantity = lot.quantity ?: continue
                if (lot.buyDate > date) continue
                val sellDate = lot.sellDate
                if (sellDate != null && sellDate <= date) continue
                val series = prices[lot.instrumentId to lot.currency] ?: continue
                val close = nearest(series, date) ?: continue
                val rate = rateOn(lot.currency, date, fx) ?: continue
                value = value.add(quantity.multiply(close).multiply(rate))
                invested = invested.add(quantity.multiply(lot.buyPrice).multiply(rate))
            }
            TimelinePoint(date, value, invested)
        }
    }

    private fun rateOn(currency: String, date: LocalDate, fx: Map<String, NavigableMap<LocalDate, BigDecimal>>): BigDecimal? {
        val (code, divisor) = Fx.normalize(currency)
        val rate = if (code == "PLN") BigDecimal.ONE else fx[code]?.let { nearest(it, date) } ?: return null
        return rate.divide(divisor, Calc.MC)
    }

    /** Last value on or before [date]; for days before the series starts, its first value. */
    private fun nearest(series: NavigableMap<LocalDate, BigDecimal>, date: LocalDate): BigDecimal? =
        (series.floorEntry(date) ?: series.ceilingEntry(date))?.value
}
