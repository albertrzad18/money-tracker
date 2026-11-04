package io.github.albertrzad18.moneytracker.domain

import io.github.albertrzad18.moneytracker.data.db.AssetKind
import io.github.albertrzad18.moneytracker.data.db.Instrument
import io.github.albertrzad18.moneytracker.data.db.Lot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Export file: every instrument with all its purchases, sold ones too. Numbers and dates are
 * strings so nothing is lost to floating point. Quotes and price history are not included;
 * they are downloaded again after an import.
 */
@Serializable
data class BackupFile(
    val format: String = Backup.FORMAT,
    val version: Int = Backup.VERSION,
    val exportedAt: String,
    val instruments: List<BackupInstrument>,
)

@Serializable
data class BackupInstrument(
    val kind: String,
    val providerId: String,
    val symbol: String,
    val name: String,
    val exchange: String? = null,
    val currency: String? = null,
    val onWidget: Boolean = true,
    val lots: List<BackupLot> = emptyList(),
)

@Serializable
data class BackupLot(
    val buyDate: String,
    val buyPrice: String,
    val quantity: String? = null,
    val currency: String,
    val alertUpPct: String? = null,
    val alertDownPct: String? = null,
    val sellDate: String? = null,
    val sellPrice: String? = null,
)

/** An instrument read from a backup, with its lots not yet tied to a database id. */
data class ImportedInstrument(val instrument: Instrument, val lots: List<Lot>)

class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

object Backup {
    const val FORMAT = "money-tracker-backup"
    const val VERSION = 1

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** [instruments] in the user's order; lots of other instruments are ignored. */
    fun encode(instruments: List<Instrument>, lots: List<Lot>, now: Instant): String {
        val byInstrument = lots.groupBy { it.instrumentId }
        val file = BackupFile(
            exportedAt = now.toString(),
            instruments = instruments.map { instrument ->
                BackupInstrument(
                    kind = instrument.kind.name,
                    providerId = instrument.providerId,
                    symbol = instrument.symbol,
                    name = instrument.name,
                    exchange = instrument.exchange,
                    currency = instrument.currency,
                    onWidget = instrument.onWidget,
                    lots = byInstrument[instrument.id].orEmpty().map { it.toBackup() },
                )
            },
        )
        return json.encodeToString(BackupFile.serializer(), file)
    }

    /** Reads and validates a backup; any malformed field rejects the whole file. */
    fun decode(text: String): List<ImportedInstrument> {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException("Not a backup file", e)
        }
        if (file.format != FORMAT) throw InvalidBackupException("Unknown format ${file.format}")
        if (file.version > VERSION) throw InvalidBackupException("Backup version ${file.version} is newer than this app")
        return try {
            file.instruments.map { it.toImported() }
        } catch (e: RuntimeException) {
            throw InvalidBackupException("Malformed value: ${e.message}", e)
        }
    }

    /** Same purchase already in the portfolio, so importing one file twice adds nothing. */
    fun isSamePurchase(a: Lot, b: Lot): Boolean =
        a.buyDate == b.buyDate &&
            a.currency == b.currency &&
            a.sellDate == b.sellDate &&
            a.buyPrice.compareTo(b.buyPrice) == 0 &&
            sameNumber(a.quantity, b.quantity) &&
            sameNumber(a.sellPrice, b.sellPrice)

    private fun sameNumber(a: BigDecimal?, b: BigDecimal?): Boolean =
        if (a == null || b == null) a == b else a.compareTo(b) == 0

    private fun Lot.toBackup() = BackupLot(
        buyDate = buyDate.toString(),
        buyPrice = buyPrice.toPlainString(),
        quantity = quantity?.toPlainString(),
        currency = currency,
        alertUpPct = alertUpPct?.toPlainString(),
        alertDownPct = alertDownPct?.toPlainString(),
        sellDate = sellDate?.toString(),
        sellPrice = sellPrice?.toPlainString(),
    )

    private fun BackupInstrument.toImported(): ImportedInstrument {
        require(providerId.isNotBlank()) { "empty providerId" }
        return ImportedInstrument(
            instrument = Instrument(
                kind = AssetKind.valueOf(kind),
                providerId = providerId,
                symbol = symbol,
                name = name,
                exchange = exchange,
                currency = currency,
                onWidget = onWidget,
            ),
            lots = lots.map { it.toLot() },
        )
    }

    private fun BackupLot.toLot(): Lot {
        val price = BigDecimal(buyPrice)
        require(price.signum() > 0) { "buyPrice $buyPrice" }
        require(currency.isNotBlank()) { "empty currency" }
        return Lot(
            instrumentId = 0,
            buyDate = LocalDate.parse(buyDate),
            buyPrice = price,
            quantity = quantity?.let(::BigDecimal),
            currency = currency,
            alertUpPct = alertUpPct?.let(::BigDecimal),
            alertDownPct = alertDownPct?.let(::BigDecimal),
            sellDate = sellDate?.let(LocalDate::parse),
            sellPrice = sellPrice?.let(::BigDecimal),
        )
    }
}
