package io.github.albertrzad18.moneytracker.domain

import io.github.albertrzad18.moneytracker.data.db.AssetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PortfolioCalculatorTest {
    @Test
    fun `change is measured against each purchase separately`() {
        val positions = PortfolioCalculator.positions(
            instruments = listOf(instrument(1)),
            lots = listOf(lot(1, 1, "250000"), lot(2, 1, "340000")),
            quotes = listOf(quote(1, "306000")),
        )
        val results = positions.single().lots
        assertDecimal("22.4", results[0].pct)
        assertDecimal("-10", results[1].pct)
    }

    @Test
    fun `average is weighted by quantity when every lot has one`() {
        val position = PortfolioCalculator.positions(
            instruments = listOf(instrument(1)),
            lots = listOf(lot(1, 1, "100", quantity = "3"), lot(2, 1, "200", quantity = "1")),
            quotes = listOf(quote(1, "150")),
        ).single()

        assertTrue(position.isWeightedAverage)
        assertDecimal("125", position.averagePrice)
        assertDecimal("4", position.quantity)
        assertDecimal("500", position.cost)
        assertDecimal("600", position.value)
        assertDecimal("100", position.gain)
        assertDecimal("20", position.pct)
        assertDecimal("150", position.lots[0].gain)
        assertDecimal("-50", position.lots[1].gain)
    }

    @Test
    fun `without quantities the plain mean of buy prices is used and amounts stay empty`() {
        val position = PortfolioCalculator.positions(
            instruments = listOf(instrument(1)),
            lots = listOf(lot(1, 1, "100"), lot(2, 1, "200", quantity = "1")),
            quotes = listOf(quote(1, "180")),
        ).single()

        assertFalse(position.isWeightedAverage)
        assertDecimal("150", position.averagePrice)
        assertDecimal("20", position.pct)
        assertNull(position.lots[0].gain)
        assertDecimal("-20", position.lots[1].gain)
    }

    @Test
    fun `crypto bought in two currencies gives two positions with their own quotes`() {
        val btc = instrument(1, "BTC", AssetKind.CRYPTO, currency = null)
        val positions = PortfolioCalculator.positions(
            instruments = listOf(btc),
            lots = listOf(lot(1, 1, "300000", currency = "PLN"), lot(2, 1, "70000", currency = "USD")),
            quotes = listOf(quote(1, "330000", "PLN"), quote(1, "77000", "USD")),
        )

        assertEquals(listOf("PLN", "USD"), positions.map { it.currency })
        assertDecimal("10", positions[0].lots.single().pct)
        assertDecimal("10", positions[1].lots.single().pct)
    }

    @Test
    fun `sold lots are left out and a missing quote gives no result`() {
        val positions = PortfolioCalculator.positions(
            instruments = listOf(instrument(1), instrument(2)),
            lots = listOf(
                lot(1, 1, "10", sellDate = LocalDate.of(2026, 2, 1), sellPrice = "12"),
                lot(2, 2, "10"),
            ),
            quotes = emptyList(),
        )

        assertEquals(listOf(2L), positions.map { it.instrument.id })
        assertNull(positions.single().pct)
        assertNull(positions.single().lots.single().pct)
    }

    @Test
    fun `positions keep the instrument order`() {
        val positions = PortfolioCalculator.positions(
            instruments = listOf(instrument(2), instrument(1)),
            lots = listOf(lot(1, 1, "10"), lot(2, 2, "10")),
            quotes = emptyList(),
        )
        assertEquals(listOf(2L, 1L), positions.map { it.instrument.id })
    }

    @Test
    fun `realized result uses the sell price`() {
        val realized = PortfolioCalculator.realized(
            lot(1, 1, "100", quantity = "2", sellDate = LocalDate.of(2026, 3, 1), sellPrice = "130"),
            instrument(1),
        )
        assertDecimal("30", realized.pct)
        assertDecimal("60", realized.gain)
    }

    @Test
    fun `change from zero is undefined`() {
        assertNull(Calc.changePct(bd("0"), bd("5")))
    }
}
