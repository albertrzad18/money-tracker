package io.github.albertrzad18.moneytracker.widget

import io.github.albertrzad18.moneytracker.data.db.AppDatabase
import io.github.albertrzad18.moneytracker.domain.Fx
import io.github.albertrzad18.moneytracker.domain.PortfolioTimeline
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.NavigableMap
import java.util.TreeMap

/**
 * Portfolio value over the last year for the 2×2 widget, built only from price and FX history
 * already cached in the database (the Summary chart fills that cache). Never touches the network.
 */
class WidgetSparkline(db: AppDatabase, private val clock: Clock) {
    private val portfolioDao = db.portfolioDao()
    private val historyDao = db.historyDao()

    suspend fun values(): List<Float> {
        val lots = portfolioDao.getLotsWithQuantity()
        if (lots.isEmpty()) return emptyList()
        val today = LocalDate.now(clock)
        val from = maxOf(lots.minOf { it.buyDate }, today.minusYears(1))

        val prices = mutableMapOf<Pair<Long, String>, NavigableMap<LocalDate, BigDecimal>>()
        for (key in lots.map { it.instrumentId to it.currency }.distinct()) {
            val series = historyDao.getPrices(key.first, key.second).associateTo(TreeMap()) { it.date to it.close }
            if (series.isNotEmpty()) prices[key] = series
        }
        if (prices.isEmpty()) return emptyList()

        val fx = mutableMapOf<String, NavigableMap<LocalDate, BigDecimal>>()
        for (code in lots.map { Fx.normalize(it.currency).first }.filter { it != "PLN" }.distinct()) {
            val series = historyDao.getFxRates(code).associateTo(TreeMap()) { it.date to it.mid }
            if (series.isNotEmpty()) fx[code] = series
        }

        return PortfolioTimeline.build(lots, prices, fx, from, today).map { it.value.toFloat() }
    }
}
