package io.github.albertrzad18.moneytracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PortfolioDao {
    @Query("SELECT * FROM instruments ORDER BY sortOrder, symbol")
    fun observeInstruments(): Flow<List<Instrument>>

    @Query("SELECT * FROM instruments ORDER BY sortOrder, symbol")
    suspend fun getInstruments(): List<Instrument>

    @Query("SELECT * FROM instruments WHERE id = :id")
    fun observeInstrument(id: Long): Flow<Instrument?>

    @Query("SELECT * FROM instruments WHERE id = :id")
    suspend fun getInstrument(id: Long): Instrument?

    @Query("SELECT * FROM instruments WHERE kind = :kind AND providerId = :providerId")
    suspend fun findInstrument(kind: AssetKind, providerId: String): Instrument?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM instruments")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insertInstrument(instrument: Instrument): Long

    @Update
    suspend fun updateInstruments(instruments: List<Instrument>)

    @Query("DELETE FROM instruments WHERE id = :id")
    suspend fun deleteInstrument(id: Long)

    @Query("SELECT * FROM lots ORDER BY buyDate, id")
    fun observeLots(): Flow<List<Lot>>

    @Query("SELECT * FROM lots WHERE sellDate IS NULL ORDER BY buyDate, id")
    suspend fun getOpenLots(): List<Lot>

    @Query("SELECT * FROM lots WHERE quantity IS NOT NULL ORDER BY buyDate, id")
    suspend fun getLotsWithQuantity(): List<Lot>

    @Query("SELECT * FROM lots WHERE id = :id")
    suspend fun getLot(id: Long): Lot?

    @Query("SELECT COUNT(*) FROM lots WHERE instrumentId = :instrumentId")
    suspend fun countLots(instrumentId: Long): Int

    @Insert
    suspend fun insertLot(lot: Lot): Long

    @Update
    suspend fun updateLots(lots: List<Lot>)

    @Query("DELETE FROM lots WHERE id = :id")
    suspend fun deleteLot(id: Long)

    @Query("UPDATE lots SET alertUpFired = :upFired, alertDownFired = :downFired WHERE id = :id")
    suspend fun setAlertState(id: Long, upFired: Boolean, downFired: Boolean)

    @Query("SELECT * FROM quotes WHERE instrumentId = :instrumentId")
    suspend fun getQuotesForInstrument(instrumentId: Long): List<Quote>

    @Query("SELECT * FROM quotes")
    fun observeQuotes(): Flow<List<Quote>>

    @Query("SELECT * FROM quotes")
    suspend fun getQuotes(): List<Quote>

    @Upsert
    suspend fun upsertQuotes(quotes: List<Quote>)
}
