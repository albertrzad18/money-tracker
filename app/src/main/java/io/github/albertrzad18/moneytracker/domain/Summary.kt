package io.github.albertrzad18.moneytracker.domain

import io.github.albertrzad18.moneytracker.data.db.AssetKind
import io.github.albertrzad18.moneytracker.data.db.FxRate
import java.math.BigDecimal
import java.time.LocalDate

data class CurrencyTotal(
    val currency: String,
    val cost: BigDecimal,
    val value: BigDecimal,
    val gain: BigDecimal,
    val pct: BigDecimal?,
)

data class CurrencyAmount(val currency: String, val amount: BigDecimal)

data class AllocationSlice(
    val label: String,
    val kind: AssetKind?,
    val valuePln: BigDecimal,
    val share: Float,
)

data class PortfolioSummary(
    val valuePln: BigDecimal?,
    val costPln: BigDecimal?,
    val gainPln: BigDecimal?,
    val pct: BigDecimal?,
    val perCurrency: List<CurrencyTotal>,
    val byInstrument: List<AllocationSlice>,
    val byKind: List<AllocationSlice>,
    val realizedPerCurrency: List<CurrencyAmount>,
    val realizedPln: BigDecimal?,
    /** Open lots without a quantity: they only count in percent results. */
    val lotsWithoutQuantity: Int,
    /** Currencies left out of PLN totals because no NBP rate was available. */
    val missingFx: Set<String>,
    val fxDate: LocalDate?,
) {
    val isEmpty: Boolean get() = perCurrency.isEmpty() && realizedPerCurrency.isEmpty()
}

object SummaryCalculator {
    fun summarize(positions: List<Position>, realized: List<RealizedLot>, fxRates: List<FxRate>): PortfolioSummary {
        val rates = fxRates.associate { it.code to it.mid }
        val missingFx = mutableSetOf<String>()

        val valued = positions.filter { it.cost != null && it.value != null }
        val perCurrency = valued.groupBy { it.currency }.map { (currency, list) ->
            val cost = Calc.sum(list.map { it.cost!! })
            val value = Calc.sum(list.map { it.value!! })
            CurrencyTotal(currency, cost, value, value.subtract(cost), Calc.changePct(cost, value))
        }.sortedBy { it.currency }

        var costPln = BigDecimal.ZERO
        var valuePln = BigDecimal.ZERO
        val instrumentValues = linkedMapOf<Long, Pair<AllocationKey, BigDecimal>>()
        for (position in valued) {
            val rate = Fx.rateToPln(position.currency, rates)
            if (rate == null) {
                missingFx += position.currency
                continue
            }
            val value = position.value!!.multiply(rate)
            costPln = costPln.add(position.cost!!.multiply(rate))
            valuePln = valuePln.add(value)
            val key = AllocationKey(position.instrument.symbol, position.instrument.kind)
            val previous = instrumentValues[position.instrument.id]?.second ?: BigDecimal.ZERO
            instrumentValues[position.instrument.id] = key to previous.add(value)
        }
        val hasPlnTotals = valued.isNotEmpty() && valued.any { Fx.rateToPln(it.currency, rates) != null }

        val byInstrument = slices(
            instrumentValues.values.map { (key, value) -> key.label to (key.kind to value) },
            valuePln,
        )
        val byKind = slices(
            instrumentValues.values
                .groupBy { it.first.kind }
                .map { (kind, list) -> kind.name to (kind to Calc.sum(list.map { it.second })) },
            valuePln,
        )

        val realizedWithGain = realized.filter { it.gain != null }
        val realizedPerCurrency = realizedWithGain.groupBy { it.lot.currency }
            .map { (currency, list) -> CurrencyAmount(currency, Calc.sum(list.map { it.gain!! })) }
            .sortedBy { it.currency }
        var realizedPln: BigDecimal? = if (realizedPerCurrency.isEmpty()) null else BigDecimal.ZERO
        for (amount in realizedPerCurrency) {
            val pln = Fx.toPln(amount.amount, amount.currency, rates)
            if (pln == null) missingFx += amount.currency else realizedPln = realizedPln?.add(pln)
        }

        return PortfolioSummary(
            valuePln = valuePln.takeIf { hasPlnTotals },
            costPln = costPln.takeIf { hasPlnTotals },
            gainPln = valuePln.subtract(costPln).takeIf { hasPlnTotals },
            pct = if (hasPlnTotals) Calc.changePct(costPln, valuePln) else null,
            perCurrency = perCurrency,
            byInstrument = byInstrument,
            byKind = byKind,
            realizedPerCurrency = realizedPerCurrency,
            realizedPln = realizedPln,
            lotsWithoutQuantity = positions.sumOf { p -> p.lots.count { it.lot.quantity == null } },
            missingFx = missingFx,
            fxDate = fxRates.maxOfOrNull { it.date },
        )
    }

    private data class AllocationKey(val label: String, val kind: AssetKind)

    private fun slices(entries: List<Pair<String, Pair<AssetKind, BigDecimal>>>, total: BigDecimal): List<AllocationSlice> {
        if (total.signum() <= 0) return emptyList()
        return entries
            .map { (label, data) ->
                AllocationSlice(label, data.first, data.second, data.second.divide(total, Calc.MC).toFloat())
            }
            .sortedByDescending { it.valuePln }
    }
}
