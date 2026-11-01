package io.github.albertrzad18.moneytracker.data.repo

import androidx.room.withTransaction
import io.github.albertrzad18.moneytracker.data.db.AppDatabase
import io.github.albertrzad18.moneytracker.data.db.Instrument
import io.github.albertrzad18.moneytracker.data.db.Lot
import io.github.albertrzad18.moneytracker.data.db.Quote
import io.github.albertrzad18.moneytracker.data.db.isOpen
import io.github.albertrzad18.moneytracker.domain.LotInput
import io.github.albertrzad18.moneytracker.domain.LotSales
import io.github.albertrzad18.moneytracker.domain.PortfolioCalculator
import io.github.albertrzad18.moneytracker.domain.Position
import io.github.albertrzad18.moneytracker.domain.RealizedLot
import io.github.albertrzad18.moneytracker.domain.ResolvedInstrument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PortfolioRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
) {
    private val dao = db.portfolioDao()

    val instruments: Flow<List<Instrument>> = dao.observeInstruments()
    val lots: Flow<List<Lot>> = dao.observeLots()

    /** Open purchases grouped per instrument and currency, in the user's order. */
    val positions: Flow<List<Position>> =
        combine(instruments, lots, dao.observeQuotes()) { instruments, lots, quotes ->
            PortfolioCalculator.positions(instruments, lots, quotes)
        }

    /** Sold lots, most recent sale first. */
    val realized: Flow<List<RealizedLot>> = combine(instruments, lots) { instruments, lots ->
        val byId = instruments.associateBy { it.id }
        lots.filter { !it.isOpen }
            .mapNotNull { lot -> byId[lot.instrumentId]?.let { PortfolioCalculator.realized(lot, it) } }
            .sortedWith(compareByDescending<RealizedLot> { it.lot.sellDate }.thenByDescending { it.lot.id })
    }

    fun observeInstrument(id: Long): Flow<Instrument?> = dao.observeInstrument(id)

    suspend fun getInstrument(id: Long): Instrument? = dao.getInstrument(id)

    suspend fun getLot(id: Long): Lot? = dao.getLot(id)

    suspend fun currentPositions(): List<Position> = positions.first()

    /**
     * Saves a purchase, creating the instrument on first use. The price known from the search
     * is stored as a quote right away so the widget can show a result before the next refresh.
     */
    suspend fun addLot(resolved: ResolvedInstrument, input: LotInput): Long = db.withTransaction {
        val candidate = resolved.candidate
        val existing = dao.findInstrument(candidate.kind, candidate.providerId)
        val instrumentId = existing?.id ?: dao.insertInstrument(
            Instrument(
                kind = candidate.kind,
                providerId = candidate.providerId,
                symbol = candidate.symbol,
                name = candidate.name,
                exchange = candidate.exchange,
                currency = candidate.currency,
                sortOrder = dao.maxSortOrder() + 1,
            ),
        )
        dao.insertLot(input.toLot(instrumentId))
        resolved.prices[input.currency]?.let { price ->
            dao.upsertQuotes(
                listOf(
                    Quote(
                        instrumentId = instrumentId,
                        currency = input.currency,
                        price = price,
                        dayChangePct = resolved.dayChangePct[input.currency],
                        marketTime = null,
                        fetchedAt = clock.instant(),
                    ),
                ),
            )
        }
        instrumentId
    }

    /** Adds another purchase of an instrument that is already in the portfolio. */
    suspend fun addLot(instrumentId: Long, input: LotInput): Long = dao.insertLot(input.toLot(instrumentId))

    suspend fun updateLot(lotId: Long, input: LotInput) {
        val lot = dao.getLot(lotId) ?: return
        val thresholdsChanged = lot.alertUpPct != input.alertUpPct || lot.alertDownPct != input.alertDownPct
        dao.updateLots(
            listOf(
                lot.copy(
                    buyDate = input.buyDate,
                    buyPrice = input.buyPrice,
                    quantity = input.quantity,
                    currency = input.currency,
                    alertUpPct = input.alertUpPct,
                    alertDownPct = input.alertDownPct,
                    // A changed price or threshold means earlier alerts no longer apply.
                    alertUpFired = lot.alertUpFired && !thresholdsChanged && lot.buyPrice == input.buyPrice,
                    alertDownFired = lot.alertDownFired && !thresholdsChanged && lot.buyPrice == input.buyPrice,
                ),
            ),
        )
    }

    /** Only touches the alert flags, so a concurrent edit of the lot is not overwritten. */
    suspend fun setAlertState(lotId: Long, upFired: Boolean, downFired: Boolean) =
        dao.setAlertState(lotId, upFired, downFired)

    suspend fun getQuotes(instrumentId: Long): List<Quote> = dao.getQuotesForInstrument(instrumentId)

    /** Deletes a lot; the instrument goes too once it has no lots left. */
    suspend fun deleteLot(lotId: Long): Unit = db.withTransaction {
        val lot = dao.getLot(lotId) ?: return@withTransaction
        dao.deleteLot(lotId)
        if (dao.countLots(lot.instrumentId) == 0) dao.deleteInstrument(lot.instrumentId)
    }

    suspend fun sellLot(lotId: Long, date: LocalDate, price: BigDecimal, quantity: BigDecimal?): Unit = db.withTransaction {
        val lot = dao.getLot(lotId) ?: return@withTransaction
        val result = LotSales.sell(lot, date, price, quantity)
        dao.updateLots(listOf(result.updated))
        result.soldPart?.let { dao.insertLot(it) }
    }

    suspend fun reopenLot(lotId: Long) {
        val lot = dao.getLot(lotId) ?: return
        dao.updateLots(listOf(lot.copy(sellDate = null, sellPrice = null, alertUpFired = false, alertDownFired = false)))
    }

    suspend fun setOnWidget(instrumentId: Long, onWidget: Boolean) {
        val instrument = dao.getInstrument(instrumentId) ?: return
        dao.updateInstruments(listOf(instrument.copy(onWidget = onWidget)))
    }

    /** Moves an instrument one place up or down; the order is shared by the app list and the widget. */
    suspend fun moveInstrument(instrumentId: Long, up: Boolean): Unit = db.withTransaction {
        val ordered = dao.getInstruments().toMutableList()
        val index = ordered.indexOfFirst { it.id == instrumentId }
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in ordered.indices) return@withTransaction
        ordered.add(target, ordered.removeAt(index))
        dao.updateInstruments(ordered.mapIndexed { order, instrument -> instrument.copy(sortOrder = order) })
    }

    private fun LotInput.toLot(instrumentId: Long) = Lot(
        instrumentId = instrumentId,
        buyDate = buyDate,
        buyPrice = buyPrice,
        quantity = quantity,
        currency = currency,
        alertUpPct = alertUpPct,
        alertDownPct = alertDownPct,
    )
}
