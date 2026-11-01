package io.github.albertrzad18.moneytracker.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

/** National Bank of Poland exchange rates, table A (average rates). */
interface NbpApi {
    @GET("exchangerates/tables/A/?format=json")
    suspend fun currentTable(): List<NbpTable>

    /** Dates are ISO (yyyy-MM-dd); one request may cover at most 367 days. Returns 404 when there is no data. */
    @GET("exchangerates/rates/A/{code}/{start}/{end}/?format=json")
    suspend fun rates(
        @Path("code") code: String,
        @Path("start") start: String,
        @Path("end") end: String,
    ): NbpRatesResponse
}

@Serializable
data class NbpTable(val effectiveDate: String, val rates: List<NbpTableRate> = emptyList())

@Serializable
data class NbpTableRate(val code: String, val mid: Double)

@Serializable
data class NbpRatesResponse(val code: String, val rates: List<NbpRate> = emptyList())

@Serializable
data class NbpRate(val effectiveDate: String, val mid: Double)
