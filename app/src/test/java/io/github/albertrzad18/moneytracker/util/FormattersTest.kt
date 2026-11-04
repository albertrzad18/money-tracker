package io.github.albertrzad18.moneytracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class FormattersTest {
    private val pl = Locale.forLanguageTag("pl-PL")

    /** Locales use different (non-breaking) spaces for grouping; normalise them for comparison. */
    private fun String.normalized() = replace(' ', ' ').replace(' ', ' ')

    @Test
    fun `price precision follows its size`() {
        assertEquals("305 914", Formatters.price(BigDecimal("305914.4"), pl).normalized())
        assertEquals("344,63", Formatters.price(BigDecimal("344.63"), pl))
        assertEquals("0,5432", Formatters.price(BigDecimal("0.5432"), pl))
        assertEquals("0,00001234", Formatters.price(BigDecimal("0.00001234"), pl))
        assertEquals("1,234.50", Formatters.price(BigDecimal("1234.5"), Locale.US))
    }

    @Test
    fun `percent has a sign and one decimal`() {
        assertEquals("+22,4%", Formatters.pct(BigDecimal("22.44"), pl))
        assertEquals("−10,0%", Formatters.pct(BigDecimal("-10"), pl))
        assertEquals("0,0%", Formatters.pct(BigDecimal("0.04"), pl))
        assertEquals("—", Formatters.pct(null, pl))
    }

    @Test
    fun `money uses short currency symbols`() {
        assertEquals("146,38 zł", Formatters.money(BigDecimal("146.38"), "PLN", pl))
        assertEquals("+12,00 $", Formatters.signedMoney(BigDecimal("12"), "USD", pl))
        assertEquals("−3,50 €", Formatters.signedMoney(BigDecimal("-3.5"), "EUR", pl))
    }

    @Test
    fun `parses both decimal separators and grouping spaces`() {
        assertEquals(0, BigDecimal("1234.5").compareTo(Formatters.parseDecimal("1 234,5")))
        assertEquals(0, BigDecimal("1234.5").compareTo(Formatters.parseDecimal("1234.5")))
        assertEquals(0, BigDecimal("250000").compareTo(Formatters.parseDecimal("250 000")))
        assertNull(Formatters.parseDecimal("abc"))
        assertNull(Formatters.parseDecimal(""))
    }

    @Test
    fun `editable value round-trips through parsing`() {
        val text = Formatters.editable(BigDecimal("128.50"), pl)
        assertEquals("128,5", text)
        assertEquals(0, BigDecimal("128.5").compareTo(Formatters.parseDecimal(text)))
    }

    @Test
    fun `symbol splits off a short exchange suffix`() {
        assertEquals("ETFBW20TR" to ".WA", Formatters.symbolParts("ETFBW20TR.WA"))
        assertEquals("CNDX" to ".L", Formatters.symbolParts("CNDX.L"))
        assertEquals("BTC" to "", Formatters.symbolParts("BTC"))
        assertEquals("BRK-B" to "", Formatters.symbolParts("BRK-B"))
        assertEquals(".WA" to "", Formatters.symbolParts(".WA"))
    }
}
