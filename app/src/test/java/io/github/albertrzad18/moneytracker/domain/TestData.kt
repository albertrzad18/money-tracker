package io.github.albertrzad18.moneytracker.domain

import io.github.albertrzad18.moneytracker.data.db.AssetKind
import io.github.albertrzad18.moneytracker.data.db.Instrument
import io.github.albertrzad18.moneytracker.data.db.Lot
import io.github.albertrzad18.moneytracker.data.db.Quote
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

fun bd(value: String) = BigDecimal(value)

fun instrument(id: Long, symbol: String = "SYM$id", kind: AssetKind = AssetKind.STOCK, currency: String? = "PLN") = Instrument(
    id = id,
    kind = kind,
    providerId = symbol,
    symbol = symbol,
    name = symbol,
    exchange = null,
    currency = currency,
)

fun lot(
    id: Long,
    instrumentId: Long,
    price: String,
    quantity: String? = null,
    currency: String = "PLN",
    date: LocalDate = LocalDate.of(2026, 1, 1),
    sellDate: LocalDate? = null,
    sellPrice: String? = null,
) = Lot(
    id = id,
    instrumentId = instrumentId,
    buyDate = date,
    buyPrice = bd(price),
    quantity = quantity?.let(::bd),
    currency = currency,
    sellDate = sellDate,
    sellPrice = sellPrice?.let(::bd),
)

fun quote(instrumentId: Long, price: String, currency: String = "PLN") =
    Quote(instrumentId, currency, bd(price), null, null, Instant.EPOCH)

/** Compares numerically, ignoring scale (1.0 == 1.00). */
fun assertDecimal(expected: String, actual: BigDecimal?) {
    org.junit.Assert.assertNotNull("expected $expected but was null", actual)
    org.junit.Assert.assertEquals("expected $expected but was $actual", 0, bd(expected).compareTo(actual!!.setScale(6, java.math.RoundingMode.HALF_UP)))
}
