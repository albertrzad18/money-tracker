package io.github.albertrzad18.moneytracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XtbTickerMapperTest {
    @Test
    fun `maps XTB country suffixes to Yahoo exchange suffixes`() {
        assertEquals("PKN.WA", XtbTickerMapper.toYahoo("PKN.PL"))
        assertEquals("AAPL", XtbTickerMapper.toYahoo("AAPL.US"))
        assertEquals("CSPX.L", XtbTickerMapper.toYahoo("CSPX.UK"))
        assertEquals("VWCE.DE", XtbTickerMapper.toYahoo("VWCE.DE"))
        assertEquals("IWDA.AS", XtbTickerMapper.toYahoo("IWDA.NL"))
        assertEquals("MC.PA", XtbTickerMapper.toYahoo("MC.FR"))
    }

    @Test
    fun `is case insensitive and trims`() {
        assertEquals("CDR.WA", XtbTickerMapper.toYahoo("  cdr.pl "))
    }

    @Test
    fun `US share classes use a dash`() {
        assertEquals("BRK-B", XtbTickerMapper.toYahoo("BRK.B.US"))
    }

    @Test
    fun `leaves Yahoo symbols and plain names alone`() {
        assertNull(XtbTickerMapper.toYahoo("PKN.WA"))
        assertNull(XtbTickerMapper.toYahoo("bitcoin"))
        assertNull(XtbTickerMapper.toYahoo("Vanguard FTSE"))
    }
}
