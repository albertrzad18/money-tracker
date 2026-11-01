package io.github.albertrzad18.moneytracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertEvaluatorTest {
    private fun evaluate(pct: String, upFired: Boolean = false, downFired: Boolean = false, up: String = "20", down: String = "10") =
        AlertEvaluator.evaluate(bd(pct), bd(up), bd(down), upFired, downFired)

    @Test
    fun `fires once when the rise threshold is crossed`() {
        assertEquals(AlertEvaluator.Decision(notifyUp = true, notifyDown = false, upFired = true, downFired = false), evaluate("20.5"))
        assertEquals(AlertEvaluator.Decision(notifyUp = false, notifyDown = false, upFired = true, downFired = false), evaluate("25", upFired = true))
    }

    @Test
    fun `re-arms only after falling back by the margin`() {
        assertEquals(true, evaluate("19.5", upFired = true).upFired)
        assertEquals(false, evaluate("18.9", upFired = true).upFired)
    }

    @Test
    fun `fires on the drop threshold`() {
        val decision = evaluate("-10")
        assertEquals(true, decision.notifyDown)
        assertEquals(true, decision.downFired)
        assertEquals(false, evaluate("-8.5", downFired = true).downFired)
        assertEquals(true, evaluate("-9.5", downFired = true).downFired)
    }

    @Test
    fun `zero threshold disables the direction`() {
        val decision = evaluate("500", up = "0")
        assertEquals(false, decision.notifyUp)
        assertEquals(false, decision.upFired)
    }
}
