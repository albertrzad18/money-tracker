package io.github.albertrzad18.moneytracker.domain

import io.github.albertrzad18.moneytracker.data.db.AssetKind
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** Search result before it is saved as an [io.github.albertrzad18.moneytracker.data.db.Instrument]. */
data class InstrumentCandidate(
    val kind: AssetKind,
    val providerId: String,
    val symbol: String,
    val name: String,
    val exchange: String?,
    /** Known for Yahoo instruments after resolving; null for crypto. */
    val currency: String?,
)

/** A candidate together with its current price in each available currency. */
data class ResolvedInstrument(
    val candidate: InstrumentCandidate,
    val prices: Map<String, BigDecimal>,
    val dayChangePct: Map<String, BigDecimal>,
)

data class LotInput(
    val buyDate: LocalDate,
    val buyPrice: BigDecimal,
    val quantity: BigDecimal?,
    val currency: String,
    val alertUpPct: BigDecimal?,
    val alertDownPct: BigDecimal?,
)

enum class ChartRange { WEEK, MONTH, HALF_YEAR, YEAR, MAX }

data class ChartPoint(val time: Instant, val price: BigDecimal)

/** Currencies offered for crypto purchases. */
val CRYPTO_CURRENCIES = listOf("PLN", "USD", "EUR")
