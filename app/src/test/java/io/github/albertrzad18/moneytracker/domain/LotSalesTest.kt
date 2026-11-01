package io.github.albertrzad18.moneytracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LotSalesTest {
    private val saleDate = LocalDate.of(2026, 6, 1)

    @Test
    fun `selling everything closes the lot`() {
        val result = LotSales.sell(lot(7, 1, "100", quantity = "5"), saleDate, bd("120"), bd("5"))

        assertNull(result.soldPart)
        assertEquals(saleDate, result.updated.sellDate)
        assertDecimal("120", result.updated.sellPrice)
        assertDecimal("5", result.updated.quantity)
    }

    @Test
    fun `selling part splits the lot`() {
        val result = LotSales.sell(lot(7, 1, "100", quantity = "5"), saleDate, bd("120"), bd("2"))

        assertNull(result.updated.sellDate)
        assertDecimal("3", result.updated.quantity)
        val sold = result.soldPart!!
        assertEquals(0L, sold.id)
        assertDecimal("2", sold.quantity)
        assertDecimal("100", sold.buyPrice)
        assertEquals(saleDate, sold.sellDate)
    }

    @Test
    fun `a lot without quantity is always sold whole`() {
        val result = LotSales.sell(lot(7, 1, "100"), saleDate, bd("90"), null)
        assertNull(result.soldPart)
        assertEquals(saleDate, result.updated.sellDate)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a sold lot cannot be sold again`() {
        LotSales.sell(lot(7, 1, "100", sellDate = saleDate, sellPrice = "110"), saleDate, bd("120"), null)
    }
}
