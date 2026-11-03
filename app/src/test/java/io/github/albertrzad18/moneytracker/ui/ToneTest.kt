package io.github.albertrzad18.moneytracker.ui

import io.github.albertrzad18.moneytracker.ui.components.deviationFraction
import io.github.albertrzad18.moneytracker.ui.theme.Tone
import io.github.albertrzad18.moneytracker.ui.theme.toneOfAmount
import io.github.albertrzad18.moneytracker.ui.theme.toneOfPct
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class ToneTest {
    @Test
    fun `percent below 0,05 is flat like the rounded 0,0 percent`() {
        assertEquals(Tone.FLAT, toneOfPct(BigDecimal("0.049")))
        assertEquals(Tone.FLAT, toneOfPct(BigDecimal("-0.049")))
        assertEquals(Tone.FLAT, toneOfPct(null))
        assertEquals(Tone.UP, toneOfPct(BigDecimal("0.05")))
        assertEquals(Tone.DOWN, toneOfPct(BigDecimal("-12.3")))
    }

    @Test
    fun `amount is flat only when it rounds to zero cents`() {
        assertEquals(Tone.FLAT, toneOfAmount(BigDecimal("0.004")))
        assertEquals(Tone.UP, toneOfAmount(BigDecimal("0.01")))
        assertEquals(Tone.DOWN, toneOfAmount(BigDecimal("-0.01")))
    }

    @Test
    fun `thirty percent fills half the deviation bar and more is capped`() {
        assertEquals(0f, deviationFraction(null), 0f)
        assertEquals(0.5f, deviationFraction(BigDecimal(15)), 1e-6f)
        assertEquals(1f, deviationFraction(BigDecimal(-30)), 1e-6f)
        assertEquals(1f, deviationFraction(BigDecimal(85)), 1e-6f)
    }
}
