package io.github.albertrzad18.moneytracker.data.repo

import io.github.albertrzad18.moneytracker.data.db.AppDatabase
import io.github.albertrzad18.moneytracker.data.db.AssetKind
import io.github.albertrzad18.moneytracker.data.db.FxRate
import io.github.albertrzad18.moneytracker.data.db.Instrument
import io.github.albertrzad18.moneytracker.data.db.PricePoint
import io.github.albertrzad18.moneytracker.data.db.SeriesMeta
import io.github.albertrzad18.moneytracker.data.remote.CoinGeckoApi
import io.github.albertrzad18.moneytracker.data.remote.NbpApi
import io.github.albertrzad18.moneytracker.data.remote.YahooApi
import io.github.albertrzad18.moneytracker.data.remote.YahooChartResult
import io.github.albertrzad18.moneytracker.domain.Calc
import io.github.albertrzad18.moneytracker.domain.ChartPoint
import io.github.albertrzad18.moneytracker.domain.ChartRange
import io.github.albertrzad18.moneytracker.domain.Fx
import io.github.albertrzad18.moneytracker.domain.PortfolioTimeline
import io.github.albertrzad18.moneytracker.domain.TimelinePoint
import io.github.albertrzad18.moneytracker.util.runCatchingCancellable
import retrofit2.HttpException
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.NavigableMap
import java.util.TreeMap
import javax.inject.Inject
import javax.inject.Singleton

data class TimelineResult(
    val points: List<TimelinePoint>,
    /** Symbols or currencies whose history could not be downloaded; their lots are left out. */
    val missing: List<String>,
)

@Singleton
class HistoryRepository @Inject constructor(
    db: AppDatabase,
    private val yahoo: YahooApi,
    private val coinGecko: CoinGeckoApi,
    private val nbp: NbpApi,
    private val clock: Clock,
) {
    private val portfolioDao = db.portfolioDao()
    private val historyDao = db.historyDao()

    /** Price chart of one instrument; downloaded every time, as it is only shown on demand. */
    suspend fun chart(instrument: Instrument, currency: String, range: ChartRange): List<ChartPoint> {
        if (instrument.kind == AssetKind.CRYPTO) {
            val days = when (range) {
                ChartRange.WEEK -> "7"
                ChartRange.MONTH -> "30"
                ChartRange.HALF_YEAR -> "180"
                // The free CoinGecko API does not go further back than a year.
                ChartRange.YEAR, ChartRange.MAX -> "365"
            }
            return coinGecko.marketChart(instrument.providerId, currency.lowercase(), days).prices
                .filter { it.size >= 2 }
                .map { ChartPoint(Instant.ofEpochMilli(it[0].toLong()), it[1].toBigDecimalExact()) }
        }
        val (yahooRange, interval) = when (range) {
            ChartRange.WEEK -> "5d" to "30m"
            ChartRange.MONTH -> "1mo" to "1d"
            ChartRange.HALF_YEAR -> "6mo" to "1d"
            ChartRange.YEAR -> "1y" to "1d"
            ChartRange.MAX -> "max" to "1wk"
        }
        val result = yahoo.chart(instrument.providerId, interval = interval, range = yahooRange).chart?.result?.firstOrNull()
            ?: return emptyList()
        return closes(result).map { (time, close) -> ChartPoint(time, close) }
    }

    /** Value of all lots with a quantity over time, in PLN. */
    suspend fun portfolioTimeline(): TimelineResult {
        val lots = portfolioDao.getLotsWithQuantity()
        if (lots.isEmpty()) return TimelineResult(emptyList(), emptyList())
        val instruments = portfolioDao.getInstruments().associateBy { it.id }
        val from = lots.minOf { it.buyDate }
        val missing = mutableListOf<String>()

        val prices = mutableMapOf<Pair<Long, String>, NavigableMap<LocalDate, BigDecimal>>()
        for ((key, group) in lots.groupBy { it.instrumentId to it.currency }) {
            val instrument = instruments[key.first] ?: continue
            // A week of margin so a purchase on a Monday still has the previous close.
            val start = group.minOf { it.buyDate }.minusDays(7)
            runCatchingCancellable { dailyCloses(instrument, key.second, start) }
                .onSuccess { if (it.isEmpty()) missing += instrument.symbol else prices[key] = it }
                .onFailure { missing += instrument.symbol }
        }

        val fx = mutableMapOf<String, NavigableMap<LocalDate, BigDecimal>>()
        val codes = lots.map { Fx.normalize(it.currency).first }.filter { it != "PLN" }.distinct()
        for (code in codes) {
            runCatchingCancellable { fxSeries(code, from.minusDays(7)) }
                .onSuccess { if (it.isEmpty()) missing += code else fx[code] = it }
                .onFailure { missing += code }
        }

        return TimelineResult(
            points = PortfolioTimeline.build(lots, prices, fx, from, LocalDate.now(clock)),
            missing = missing.distinct(),
        )
    }

    /** Daily closes from [from] until today, cached in the database. */
    private suspend fun dailyCloses(instrument: Instrument, currency: String, from: LocalDate): NavigableMap<LocalDate, BigDecimal> {
        val key = "price:${instrument.id}:$currency"
        refreshSeries(key, from) { fetchFrom, today ->
            val points = fetchDailyCloses(instrument, currency, fetchFrom, today)
            historyDao.upsertPrices(points.map { (date, close) -> PricePoint(instrument.id, currency, date, close) })
        }
        return historyDao.getPrices(instrument.id, currency).associateTo(TreeMap()) { it.date to it.close }
    }

    private suspend fun fxSeries(code: String, from: LocalDate): NavigableMap<LocalDate, BigDecimal> {
        refreshSeries("fx:$code", from) { fetchFrom, today ->
            var start = fetchFrom
            while (start <= today) {
                // NBP accepts at most 367 days per request.
                val end = minOf(start.plusDays(360), today)
                val rates = try {
                    nbp.rates(code, start.toString(), end.toString()).rates
                } catch (e: HttpException) {
                    if (e.code() == 404) emptyList() else throw e
                }
                historyDao.upsertFxRates(rates.map { FxRate(code, LocalDate.parse(it.effectiveDate), it.mid.toBigDecimalExact()) })
                start = end.plusDays(1)
            }
        }
        return historyDao.getFxRates(code).associateTo(TreeMap()) { it.date to it.mid }
    }

    /**
     * Downloads the part of a cached series that is missing: everything from [from] when the cache
     * starts later, otherwise only the last few days once the cache is older than [CACHE_TTL].
     */
    private suspend fun refreshSeries(key: String, from: LocalDate, fetch: suspend (LocalDate, LocalDate) -> Unit) {
        val meta = historyDao.getMeta(key)
        val now = clock.instant()
        val today = LocalDate.now(clock)
        val fetchFrom = when {
            meta == null || meta.coveredFrom > from -> from
            meta.fetchedAt.plus(CACHE_TTL) < now -> maxOf(from, meta.fetchedAt.atZone(clock.zone).toLocalDate().minusDays(5))
            else -> return
        }
        fetch(fetchFrom, today)
        historyDao.upsertMeta(SeriesMeta(key, minOf(from, meta?.coveredFrom ?: from), now))
    }

    private suspend fun fetchDailyCloses(
        instrument: Instrument,
        currency: String,
        from: LocalDate,
        today: LocalDate,
    ): Map<LocalDate, BigDecimal> {
        if (instrument.kind != AssetKind.CRYPTO) return yahooDailyCloses(instrument.providerId, from)

        val points = sortedMapOf<LocalDate, BigDecimal>()
        val coinGeckoStart = today.minusDays(COINGECKO_MAX_DAYS - 1)
        if (from < coinGeckoStart) {
            // Older crypto history comes from Yahoo, quoted in USD or EUR.
            runCatchingCancellable { olderCryptoCloses(instrument, currency, from) }
                .onSuccess { older -> older.filterKeys { it < coinGeckoStart }.let(points::putAll) }
        }
        val days = ChronoUnit.DAYS.between(maxOf(from, coinGeckoStart), today) + 1
        coinGecko.marketChart(instrument.providerId, currency.lowercase(), days.toString(), interval = "daily").prices
            .filter { it.size >= 2 }
            .forEach { point ->
                val date = Instant.ofEpochMilli(point[0].toLong()).atOffset(ZoneOffset.UTC).toLocalDate()
                points[date] = point[1].toBigDecimalExact()
            }
        return points
    }

    private suspend fun olderCryptoCloses(instrument: Instrument, currency: String, from: LocalDate): Map<LocalDate, BigDecimal> {
        val quoteCurrency = if (currency == "EUR") "EUR" else "USD"
        val closes = yahooDailyCloses("${instrument.symbol.uppercase()}-$quoteCurrency", from)
        if (currency == quoteCurrency) return closes
        if (currency != "PLN") return emptyMap()
        val usdPln = fxSeries("USD", from)
        return closes.mapNotNull { (date, close) ->
            val rate = (usdPln.floorEntry(date) ?: usdPln.ceilingEntry(date))?.value ?: return@mapNotNull null
            date to close.multiply(rate, Calc.MC)
        }.toMap()
    }

    private suspend fun yahooDailyCloses(symbol: String, from: LocalDate): Map<LocalDate, BigDecimal> {
        val result = yahoo.chart(
            symbol,
            interval = "1d",
            period1 = from.atStartOfDay(ZoneOffset.UTC).toEpochSecond(),
            period2 = clock.instant().epochSecond,
        ).chart?.result?.firstOrNull() ?: return emptyMap()
        val offset = result.meta.gmtoffset ?: 0
        return closes(result).associate { (time, close) ->
            time.plusSeconds(offset).atOffset(ZoneOffset.UTC).toLocalDate() to close
        }
    }

    private fun closes(result: YahooChartResult): List<Pair<Instant, BigDecimal>> {
        val timestamps = result.timestamp.orEmpty()
        val closes = result.indicators?.quote?.firstOrNull()?.close.orEmpty()
        return timestamps.indices.mapNotNull { i ->
            val close = closes.getOrNull(i) ?: return@mapNotNull null
            Instant.ofEpochSecond(timestamps[i]) to close.toBigDecimalExact()
        }
    }

    private companion object {
        val CACHE_TTL: Duration = Duration.ofHours(6)
        const val COINGECKO_MAX_DAYS = 365L
    }
}
