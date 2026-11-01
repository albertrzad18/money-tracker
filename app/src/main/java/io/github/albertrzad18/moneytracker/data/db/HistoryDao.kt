package io.github.albertrzad18.moneytracker.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM price_history WHERE instrumentId = :instrumentId AND currency = :currency ORDER BY date")
    suspend fun getPrices(instrumentId: Long, currency: String): List<PricePoint>

    @Upsert
    suspend fun upsertPrices(points: List<PricePoint>)

    @Query("SELECT * FROM fx_rates WHERE code = :code ORDER BY date")
    suspend fun getFxRates(code: String): List<FxRate>

    @Query(
        """
        SELECT f.* FROM fx_rates f
        JOIN (SELECT code, MAX(date) AS maxDate FROM fx_rates GROUP BY code) latest
          ON f.code = latest.code AND f.date = latest.maxDate
        """,
    )
    fun observeLatestFxRates(): Flow<List<FxRate>>

    @Upsert
    suspend fun upsertFxRates(rates: List<FxRate>)

    @Query("SELECT * FROM series_meta WHERE `key` = :key")
    suspend fun getMeta(key: String): SeriesMeta?

    @Upsert
    suspend fun upsertMeta(meta: SeriesMeta)
}
