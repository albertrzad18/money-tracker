package io.github.albertrzad18.moneytracker.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** CoinGecko public API (no key). History is limited to the last 365 days without a paid plan. */
interface CoinGeckoApi {
    /** Response keys are dynamic: `{"bitcoin": {"pln": 1.0, "pln_24h_change": 0.5, "last_updated_at": 1}}`. */
    @GET("simple/price")
    suspend fun simplePrice(
        @Query("ids") ids: String,
        @Query("vs_currencies") currencies: String,
        @Query("include_24hr_change") include24hChange: Boolean = true,
        @Query("include_last_updated_at") includeLastUpdatedAt: Boolean = true,
    ): JsonObject

    @GET("search")
    suspend fun search(@Query("query") query: String): CoinGeckoSearchResponse

    @GET("coins/{id}/market_chart")
    suspend fun marketChart(
        @Path("id") id: String,
        @Query("vs_currency") currency: String,
        @Query("days") days: String,
        @Query("interval") interval: String? = null,
    ): CoinGeckoMarketChart
}

@Serializable
data class CoinGeckoSearchResponse(val coins: List<CoinGeckoCoin> = emptyList())

@Serializable
data class CoinGeckoCoin(
    val id: String,
    val name: String,
    val symbol: String,
    @SerialName("market_cap_rank") val marketCapRank: Int? = null,
)

/** `prices` holds `[timestampMillis, price]` pairs. */
@Serializable
data class CoinGeckoMarketChart(val prices: List<List<Double>> = emptyList())
