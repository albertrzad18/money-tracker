package io.github.albertrzad18.moneytracker.domain

import io.github.albertrzad18.moneytracker.data.db.Instrument
import io.github.albertrzad18.moneytracker.data.db.Lot
import io.github.albertrzad18.moneytracker.data.db.Quote
import io.github.albertrzad18.moneytracker.data.db.isOpen
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

object Calc {
    val MC: MathContext = MathContext.DECIMAL64
    private val HUNDRED = BigDecimal(100)

    /** Percent change from [from] to [to]; null when [from] is zero. */
    fun changePct(from: BigDecimal, to: BigDecimal): BigDecimal? =
        if (from.signum() == 0) null else to.subtract(from).multiply(HUNDRED).divide(from, MC)

    fun sum(values: Iterable<BigDecimal>): BigDecimal = values.fold(BigDecimal.ZERO, BigDecimal::add)
}

/** One open purchase measured against the current price. [gain] and [value] need a quantity. */
data class LotResult(
    val lot: Lot,
    val pct: BigDecimal?,
    val gain: BigDecimal?,
    val value: BigDecimal?,
)

/**
 * Open purchases of one instrument in one currency. Crypto bought in PLN and in USD gives
 * two positions, so every number inside a position uses a single currency.
 */
data class Position(
    val instrument: Instrument,
    val currency: String,
    val quote: Quote?,
    val lots: List<LotResult>,
    /** Sum over lots that have a quantity; null when none has. */
    val quantity: BigDecimal?,
    /** Weighted by quantity when every lot has one, otherwise the plain mean of buy prices. */
    val averagePrice: BigDecimal,
    val isWeightedAverage: Boolean,
    val cost: BigDecimal?,
    val value: BigDecimal?,
    val gain: BigDecimal?,
    val pct: BigDecimal?,
) {
    val key: String get() = "${instrument.id}:$currency"
}

data class RealizedLot(
    val lot: Lot,
    val instrument: Instrument,
    val pct: BigDecimal?,
    val gain: BigDecimal?,
)

object PortfolioCalculator {
    fun positions(instruments: List<Instrument>, lots: List<Lot>, quotes: List<Quote>): List<Position> {
        val quoteMap = quotes.associateBy { it.instrumentId to it.currency }
        val openLots = lots.filter { it.isOpen }.groupBy { it.instrumentId }
        return instruments.flatMap { instrument ->
            openLots[instrument.id].orEmpty()
                .groupBy { it.currency }
                .toSortedMap()
                .map { (currency, group) ->
                    position(instrument, currency, quoteMap[instrument.id to currency], group)
                }
        }
    }

    fun position(instrument: Instrument, currency: String, quote: Quote?, lots: List<Lot>): Position {
        require(lots.isNotEmpty())
        val price = quote?.price
        val withQuantity = lots.filter { it.quantity != null }
        val allHaveQuantity = withQuantity.size == lots.size

        val quantity = withQuantity.takeIf { it.isNotEmpty() }?.let { list -> Calc.sum(list.map { it.quantity!! }) }
        val cost = withQuantity.takeIf { it.isNotEmpty() }
            ?.let { list -> Calc.sum(list.map { it.quantity!!.multiply(it.buyPrice) }) }
        val value = if (quantity != null && price != null) quantity.multiply(price) else null
        val gain = if (cost != null && value != null) value.subtract(cost) else null

        val weighted = allHaveQuantity && quantity!!.signum() > 0
        val averagePrice = if (weighted) {
            cost!!.divide(quantity, Calc.MC)
        } else {
            Calc.sum(lots.map { it.buyPrice }).divide(BigDecimal(lots.size), Calc.MC)
        }

        return Position(
            instrument = instrument,
            currency = currency,
            quote = quote,
            lots = lots.sortedWith(compareBy<Lot>({ it.buyDate }, { it.id })).map { lotResult(it, price) },
            quantity = quantity,
            averagePrice = averagePrice,
            isWeightedAverage = weighted,
            cost = cost,
            value = value,
            gain = gain,
            // With a weighted average this equals gain / cost.
            pct = price?.let { Calc.changePct(averagePrice, it) },
        )
    }

    fun lotResult(lot: Lot, price: BigDecimal?): LotResult {
        val quantity = lot.quantity
        return LotResult(
            lot = lot,
            pct = price?.let { Calc.changePct(lot.buyPrice, it) },
            gain = if (price != null && quantity != null) price.subtract(lot.buyPrice).multiply(quantity) else null,
            value = if (price != null && quantity != null) price.multiply(quantity) else null,
        )
    }

    fun realized(lot: Lot, instrument: Instrument): RealizedLot {
        val sellPrice = requireNotNull(lot.sellPrice) { "Lot ${lot.id} is not sold" }
        return RealizedLot(
            lot = lot,
            instrument = instrument,
            pct = Calc.changePct(lot.buyPrice, sellPrice),
            gain = lot.quantity?.let { sellPrice.subtract(lot.buyPrice).multiply(it) },
        )
    }
}

object LotSales {
    /** [updated] replaces the original lot; [soldPart] is a new closed lot when only part was sold. */
    data class Result(val updated: Lot, val soldPart: Lot?)

    fun sell(lot: Lot, date: LocalDate, price: BigDecimal, quantity: BigDecimal?): Result {
        require(lot.isOpen) { "Lot ${lot.id} is already sold" }
        require(price.signum() > 0) { "Sell price must be positive" }
        val owned = lot.quantity
        if (quantity == null || owned == null || quantity >= owned) {
            return Result(updated = lot.copy(sellDate = date, sellPrice = price), soldPart = null)
        }
        require(quantity.signum() > 0) { "Sold quantity must be positive" }
        return Result(
            updated = lot.copy(quantity = owned.subtract(quantity)),
            soldPart = lot.copy(
                id = 0,
                quantity = quantity,
                sellDate = date,
                sellPrice = price,
                alertUpFired = false,
                alertDownFired = false,
            ),
        )
    }
}
