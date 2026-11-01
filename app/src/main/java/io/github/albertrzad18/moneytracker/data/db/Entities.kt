package io.github.albertrzad18.moneytracker.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

enum class AssetKind { STOCK, ETF, CRYPTO }

@Entity(
    tableName = "instruments",
    indices = [Index(value = ["kind", "providerId"], unique = true)],
)
data class Instrument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: AssetKind,
    /** Yahoo Finance symbol for stocks and ETFs, CoinGecko coin id for crypto. */
    val providerId: String,
    val symbol: String,
    val name: String,
    val exchange: String?,
    /** Trading currency reported by Yahoo. Null for crypto, whose currency is chosen per lot. */
    val currency: String?,
    val onWidget: Boolean = true,
    val sortOrder: Int = 0,
)

/** A single purchase. It stays in the table after being sold (sellDate set) and shows up in the history. */
@Entity(
    tableName = "lots",
    foreignKeys = [
        ForeignKey(
            entity = Instrument::class,
            parentColumns = ["id"],
            childColumns = ["instrumentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("instrumentId")],
)
data class Lot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val instrumentId: Long,
    val buyDate: LocalDate,
    val buyPrice: BigDecimal,
    val quantity: BigDecimal? = null,
    val currency: String,
    /** Alert thresholds in percent (both stored as positive numbers); null means the global default. */
    val alertUpPct: BigDecimal? = null,
    val alertDownPct: BigDecimal? = null,
    val alertUpFired: Boolean = false,
    val alertDownFired: Boolean = false,
    val sellDate: LocalDate? = null,
    val sellPrice: BigDecimal? = null,
)

val Lot.isOpen: Boolean get() = sellDate == null

@Entity(
    tableName = "quotes",
    primaryKeys = ["instrumentId", "currency"],
    foreignKeys = [
        ForeignKey(
            entity = Instrument::class,
            parentColumns = ["id"],
            childColumns = ["instrumentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class Quote(
    val instrumentId: Long,
    val currency: String,
    val price: BigDecimal,
    val dayChangePct: BigDecimal?,
    val marketTime: Instant?,
    val fetchedAt: Instant,
)

/** Cached daily close used by the portfolio-over-time chart. */
@Entity(
    tableName = "price_history",
    primaryKeys = ["instrumentId", "currency", "date"],
    foreignKeys = [
        ForeignKey(
            entity = Instrument::class,
            parentColumns = ["id"],
            childColumns = ["instrumentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PricePoint(
    val instrumentId: Long,
    val currency: String,
    val date: LocalDate,
    val close: BigDecimal,
)

/** NBP average rate: 1 unit of [code] = [mid] PLN. */
@Entity(tableName = "fx_rates", primaryKeys = ["code", "date"])
data class FxRate(
    val code: String,
    val date: LocalDate,
    val mid: BigDecimal,
)

/** Tracks which part of a cached series (price history or FX) has already been downloaded. */
@Entity(tableName = "series_meta")
data class SeriesMeta(
    @PrimaryKey val key: String,
    val coveredFrom: LocalDate,
    val fetchedAt: Instant,
)
