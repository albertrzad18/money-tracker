package io.github.albertrzad18.moneytracker.data.repo

import io.github.albertrzad18.moneytracker.data.db.AppDatabase
import io.github.albertrzad18.moneytracker.data.db.AssetKind
import io.github.albertrzad18.moneytracker.data.db.FxRate
import io.github.albertrzad18.moneytracker.data.db.Quote
import io.github.albertrzad18.moneytracker.data.remote.CoinGeckoApi
import io.github.albertrzad18.moneytracker.data.remote.NbpApi
import io.github.albertrzad18.moneytracker.data.remote.YahooApi
import io.github.albertrzad18.moneytracker.data.remote.YahooMeta
import io.github.albertrzad18.moneytracker.domain.CRYPTO_CURRENCIES
import io.github.albertrzad18.moneytracker.domain.Calc
import io.github.albertrzad18.moneytracker.domain.EtfAliases
import io.github.albertrzad18.moneytracker.domain.InstrumentCandidate
import io.github.albertrzad18.moneytracker.domain.MarketHours
import io.github.albertrzad18.moneytracker.domain.ResolvedInstrument
import io.github.albertrzad18.moneytracker.domain.XtbTickerMapper
import io.github.albertrzad18.moneytracker.util.runCatchingCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class SearchResults(
    val securities: List<InstrumentCandidate> = emptyList(),
    val crypto: List<InstrumentCandidate> = emptyList(),
    /** Both sources failed, most likely no connection. */
    val failed: Boolean = false,
)

internal fun Double.toBigDecimalExact(): BigDecimal = BigDecimal.valueOf(this)

@Singleton
class QuoteRepository @Inject constructor(
    db: AppDatabase,
    private val yahoo: YahooApi,
    private val coinGecko: CoinGeckoApi,
    private val nbp: NbpApi,
    private val clock: Clock,
) {
    private val dao = db.portfolioDao()
    private val historyDao = db.historyDao()

    val latestFxRates: Flow<List<FxRate>> = historyDao.observeLatestFxRates()

    suspend fun search(query: String): SearchResults = coroutineScope {
        val trimmed = query.trim()
        val securities = async { runCatchingCancellable { searchYahoo(XtbTickerMapper.toYahoo(trimmed) ?: trimmed) } }
        val aliasEtfs = EtfAliases.symbolsFor(trimmed).map { symbol ->
            async { runCatchingCancellable { searchYahoo(symbol).filter { it.symbol == symbol } }.getOrDefault(emptyList()) }
        }
        val crypto = async { runCatchingCancellable { searchCrypto(trimmed) } }
        val securitiesResult = securities.await()
        val aliasEtfResults = aliasEtfs.flatMap { it.await() }
        val cryptoResult = crypto.await()
        SearchResults(
            securities = (aliasEtfResults + securitiesResult.getOrDefault(emptyList())).distinctBy { it.symbol },
            crypto = cryptoResult.getOrDefault(emptyList()),
            failed = securitiesResult.isFailure && cryptoResult.isFailure,
        )
    }

    private suspend fun searchYahoo(query: String): List<InstrumentCandidate> =
        yahoo.search(query).quotes.mapNotNull { quote ->
            val kind = when (quote.quoteType) {
                "EQUITY" -> AssetKind.STOCK
                "ETF" -> AssetKind.ETF
                else -> return@mapNotNull null
            }
            InstrumentCandidate(
                kind = kind,
                providerId = quote.symbol,
                symbol = quote.symbol,
                name = quote.longname ?: quote.shortname ?: quote.symbol,
                exchange = quote.exchDisp ?: quote.exchange,
                currency = null,
            )
        }

    private suspend fun searchCrypto(query: String): List<InstrumentCandidate> =
        coinGecko.search(query).coins.take(MAX_CRYPTO_RESULTS).map { coin ->
            InstrumentCandidate(
                kind = AssetKind.CRYPTO,
                providerId = coin.id,
                symbol = coin.symbol.uppercase(),
                name = coin.name,
                exchange = null,
                currency = null,
            )
        }

    /** Looks up the trading currency and current price of a search result. */
    suspend fun resolve(candidate: InstrumentCandidate): ResolvedInstrument {
        if (candidate.kind == AssetKind.CRYPTO) {
            val prices = cryptoPrices(listOf(candidate.providerId), CRYPTO_CURRENCIES)[candidate.providerId].orEmpty()
            return ResolvedInstrument(
                candidate = candidate,
                prices = prices.mapValues { it.value.price },
                dayChangePct = prices.mapNotNull { (currency, price) -> price.dayChangePct?.let { currency to it } }.toMap(),
            )
        }
        val meta = yahoo.chart(candidate.providerId, interval = "1d", range = "1d").chart?.result?.firstOrNull()?.meta
            ?: error("No quote for ${candidate.providerId}")
        val currency = meta.currency ?: error("No currency for ${candidate.providerId}")
        val price = meta.regularMarketPrice?.toBigDecimalExact()
        return ResolvedInstrument(
            candidate = candidate.copy(currency = currency),
            prices = price?.let { mapOf(currency to it) }.orEmpty(),
            dayChangePct = dayChangePct(meta)?.let { mapOf(currency to it) }.orEmpty(),
        )
    }

    /**
     * Fetches prices for every instrument with an open lot. Returns false if any of them failed;
     * prices that did arrive are saved either way and old ones are never removed.
     */
    suspend fun refreshQuotes(force: Boolean): Boolean {
        val openLots = dao.getOpenLots()
        val activeIds = openLots.map { it.instrumentId }.toSet()
        val instruments = dao.getInstruments().filter { it.id in activeIds }
        val (crypto, securities) = instruments.partition { it.kind == AssetKind.CRYPTO }
        val now = clock.instant()
        val quotes = mutableListOf<Quote>()
        var failures = 0

        if (securities.isNotEmpty()) {
            val existing = dao.getQuotes().associateBy { it.instrumentId }
            val fetchTimes = securities.map { existing[it.id]?.fetchedAt }
            val oldestFetch = if (fetchTimes.any { it == null }) null else fetchTimes.filterNotNull().minOrNull()
            if (force || !MarketHours.canSkipStocks(now, oldestFetch)) {
                for (chunk in securities.chunked(SPARK_BATCH_SIZE)) {
                    runCatchingCancellable { yahoo.spark(chunk.joinToString(",") { it.providerId }) }
                        .onSuccess { response ->
                            val metas = response.spark?.result.orEmpty()
                                .mapNotNull { result -> result.response?.firstOrNull()?.meta?.let { result.symbol.uppercase() to it } }
                                .toMap()
                            for (instrument in chunk) {
                                val meta = metas[instrument.providerId.uppercase()]
                                val price = meta?.regularMarketPrice
                                val currency = instrument.currency ?: meta?.currency
                                if (meta == null || price == null || currency == null) {
                                    failures++
                                    continue
                                }
                                quotes += Quote(
                                    instrumentId = instrument.id,
                                    currency = currency,
                                    price = price.toBigDecimalExact(),
                                    dayChangePct = dayChangePct(meta),
                                    marketTime = meta.regularMarketTime?.let(Instant::ofEpochSecond),
                                    fetchedAt = now,
                                )
                            }
                        }
                        .onFailure { failures += chunk.size }
                }
            }
        }

        if (crypto.isNotEmpty()) {
            val currenciesByInstrument = openLots
                .filter { lot -> crypto.any { it.id == lot.instrumentId } }
                .groupBy({ it.instrumentId }, { it.currency })
                .mapValues { it.value.distinct() }
            val allCurrencies = currenciesByInstrument.values.flatten().distinct()
            runCatchingCancellable { cryptoPrices(crypto.map { it.providerId }.distinct(), allCurrencies) }
                .onSuccess { prices ->
                    for (instrument in crypto) {
                        val byCurrency = prices[instrument.providerId].orEmpty()
                        for (currency in currenciesByInstrument[instrument.id].orEmpty()) {
                            val price = byCurrency[currency]
                            if (price == null) {
                                failures++
                                continue
                            }
                            quotes += Quote(instrument.id, currency, price.price, price.dayChangePct, price.updatedAt, now)
                        }
                    }
                }
                .onFailure { failures += crypto.size }
        }

        if (quotes.isNotEmpty()) dao.upsertQuotes(quotes)
        return failures == 0
    }

    /** Stores today's NBP table A, used to show portfolio totals in PLN. */
    suspend fun refreshFx(): Boolean = runCatchingCancellable {
        val table = nbp.currentTable().firstOrNull() ?: return@runCatchingCancellable false
        val date = LocalDate.parse(table.effectiveDate)
        historyDao.upsertFxRates(table.rates.map { FxRate(it.code, date, it.mid.toBigDecimalExact()) })
        true
    }.getOrDefault(false)

    private data class CryptoPrice(val price: BigDecimal, val dayChangePct: BigDecimal?, val updatedAt: Instant?)

    /** Returns coin id -> currency code (upper case) -> price. */
    private suspend fun cryptoPrices(ids: List<String>, currencies: List<String>): Map<String, Map<String, CryptoPrice>> {
        val json = coinGecko.simplePrice(ids.joinToString(","), currencies.joinToString(",") { it.lowercase() })
        return ids.associateWith { id ->
            val coin = json[id] as? JsonObject ?: return@associateWith emptyMap()
            val updatedAt = coin["last_updated_at"]?.jsonPrimitive?.longOrNull?.let(Instant::ofEpochSecond)
            currencies.mapNotNull { currency ->
                val key = currency.lowercase()
                val price = coin[key]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val change = coin["${key}_24h_change"]?.jsonPrimitive?.doubleOrNull
                currency to CryptoPrice(price.toBigDecimalExact(), change?.toBigDecimalExact(), updatedAt)
            }.toMap()
        }
    }

    private fun dayChangePct(meta: YahooMeta): BigDecimal? {
        meta.regularMarketChangePercent?.let { return it.toBigDecimalExact() }
        val price = meta.regularMarketPrice ?: return null
        val previous = meta.chartPreviousClose ?: return null
        return Calc.changePct(previous.toBigDecimalExact(), price.toBigDecimalExact())
    }

    private companion object {
        const val SPARK_BATCH_SIZE = 20
        const val MAX_CRYPTO_RESULTS = 8
    }
}
