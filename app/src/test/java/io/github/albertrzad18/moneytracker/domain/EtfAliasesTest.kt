package io.github.albertrzad18.moneytracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EtfAliasesTest {
    @Test
    fun `finds Nasdaq 100 ETFs however the index is written`() {
        listOf("NASDAQ100", "nasdaq 100", "Nasdaq-100", "nasdaq", "Nasdaq 100 ETF").forEach {
            val symbols = EtfAliases.symbolsFor(it)
            assertEquals(it, "CNDX.L", symbols.first())
            assertTrue(it, "ETFBNDXPL.WA" in symbols)
        }
    }

    @Test
    fun `matches other indexes`() {
        assertEquals(listOf("CSPX.L", "ETFBSPXPL.WA"), EtfAliases.symbolsFor("S&P 500"))
        assertTrue("ETFBW20TR.WA" in EtfAliases.symbolsFor("wig20"))
    }

    @Test
    fun `ignores short and unrelated queries`() {
        assertEquals(emptyList<String>(), EtfAliases.symbolsFor("sp"))
        assertEquals(emptyList<String>(), EtfAliases.symbolsFor("Apple"))
        assertEquals(emptyList<String>(), EtfAliases.symbolsFor("PKN.WA"))
    }
}
