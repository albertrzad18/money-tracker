package io.github.albertrzad18.moneytracker.data.remote

import io.github.albertrzad18.moneytracker.di.AppModule
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real responses saved on 2026-11-01; guards the DTOs against the shape of each API. */
class ApiFixturesTest {
    private val json = AppModule.provideJson()

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "Missing fixture $name" }.readText()

    @Test
    fun `yahoo spark gives price and currency per symbol`() {
        val response = json.decodeFromString<YahooSparkResponse>(fixture("yahoo_spark.json"))
        val metas = response.spark!!.result!!.associate { it.symbol to it.response!!.first().meta }

        val orlen = metas.getValue("PKN.WA")
        assertEquals("PLN", orlen.currency)
        assertNotNull(orlen.regularMarketPrice)
        assertEquals("USD", metas.getValue("AAPL").currency)
    }

    @Test
    fun `yahoo search returns typed quotes`() {
        val response = json.decodeFromString<YahooSearchResponse>(fixture("yahoo_search.json"))
        val vwce = response.quotes.first { it.symbol == "VWCE.DE" }
        assertEquals("ETF", vwce.quoteType)
        assertNotNull(vwce.longname ?: vwce.shortname)
    }

    @Test
    fun `coingecko simple price has per currency keys`() {
        val response = json.decodeFromString<JsonObject>(fixture("coingecko_simple_price.json"))
        val bitcoin = response.getValue("bitcoin").jsonObject
        assertTrue(bitcoin.getValue("pln").jsonPrimitive.doubleOrNull!! > 0)
        assertNotNull(bitcoin["pln_24h_change"])
        assertNotNull(bitcoin["last_updated_at"])
    }

    @Test
    fun `nbp table contains usd and eur`() {
        val tables = json.decodeFromString<List<NbpTable>>(fixture("nbp_table.json"))
        val codes = tables.single().rates.map { it.code }
        assertTrue(codes.containsAll(listOf("USD", "EUR", "GBP")))
    }
}
