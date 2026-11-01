package io.github.albertrzad18.moneytracker.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Unofficial Yahoo Finance endpoints. They need a browser-like User-Agent (added in NetworkModule)
 * and may change without notice, which is why everything goes through [YahooApi] only.
 */
interface YahooApi {
    /** Latest prices for many symbols at once (up to ~20 per call). */
    @GET("v7/finance/spark")
    suspend fun spark(
        @Query("symbols") symbols: String,
        @Query("range") range: String = "1d",
        @Query("interval") interval: String = "1d",
    ): YahooSparkResponse

    @GET("v8/finance/chart/{symbol}")
    suspend fun chart(
        @Path("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("range") range: String? = null,
        @Query("period1") period1: Long? = null,
        @Query("period2") period2: Long? = null,
    ): YahooChartResponse

    @GET("https://query2.finance.yahoo.com/v1/finance/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("quotesCount") quotesCount: Int = 10,
        @Query("newsCount") newsCount: Int = 0,
        @Query("listsCount") listsCount: Int = 0,
    ): YahooSearchResponse
}

@Serializable
data class YahooSparkResponse(val spark: YahooSpark? = null)

@Serializable
data class YahooSpark(val result: List<YahooSparkResult>? = null)

@Serializable
data class YahooSparkResult(
    val symbol: String,
    val response: List<YahooChartResult>? = null,
)

@Serializable
data class YahooChartResponse(val chart: YahooChart? = null)

@Serializable
data class YahooChart(
    val result: List<YahooChartResult>? = null,
    val error: YahooError? = null,
)

@Serializable
data class YahooError(val code: String? = null, val description: String? = null)

@Serializable
data class YahooChartResult(
    val meta: YahooMeta,
    val timestamp: List<Long>? = null,
    val indicators: YahooIndicators? = null,
)

@Serializable
data class YahooMeta(
    val symbol: String,
    val currency: String? = null,
    val exchangeName: String? = null,
    val fullExchangeName: String? = null,
    val instrumentType: String? = null,
    val longName: String? = null,
    val shortName: String? = null,
    val regularMarketPrice: Double? = null,
    val regularMarketChangePercent: Double? = null,
    val chartPreviousClose: Double? = null,
    val regularMarketTime: Long? = null,
    val gmtoffset: Long? = null,
)

@Serializable
data class YahooIndicators(val quote: List<YahooQuoteIndicator>? = null)

@Serializable
data class YahooQuoteIndicator(val close: List<Double?>? = null)

@Serializable
data class YahooSearchResponse(val quotes: List<YahooSearchQuote> = emptyList())

@Serializable
data class YahooSearchQuote(
    val symbol: String,
    val shortname: String? = null,
    val longname: String? = null,
    val quoteType: String? = null,
    val exchange: String? = null,
    val exchDisp: String? = null,
)
