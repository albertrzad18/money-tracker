package io.github.albertrzad18.moneytracker.domain

import io.github.albertrzad18.moneytracker.data.db.AssetKind
import io.github.albertrzad18.moneytracker.data.db.FxRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TreeMap

class SummaryAndTimelineTest {
    private val day = LocalDate.of(2026, 10, 30)

    @Test
    fun `summary converts each currency with NBP rates`() {
        val positions = PortfolioCalculator.positions(
            instruments = listOf(instrument(1, "PKN.WA"), instrument(2, "AAPL", currency = "USD")),
            lots = listOf(lot(1, 1, "100", quantity = "10"), lot(2, 2, "100", quantity = "1", currency = "USD")),
            quotes = listOf(quote(1, "110"), quote(2, "120", "USD")),
        )
        val summary = SummaryCalculator.summarize(positions, emptyList(), listOf(FxRate("USD", day, bd("4"))))

        // PLN: cost 1000, value 1100. USD: cost 100 * 4 = 400, value 120 * 4 = 480.
        assertDecimal("1580", summary.valuePln)
        assertDecimal("1400", summary.costPln)
        assertDecimal("180", summary.gainPln)
        assertEquals(listOf("PLN", "USD"), summary.perCurrency.map { it.currency })
        assertEquals(listOf("PKN.WA", "AAPL"), summary.byInstrument.map { it.label })
        assertEquals(1f, summary.byInstrument.sumOf { it.share.toDouble() }.toFloat(), 0.0001f)
        assertEquals(day, summary.fxDate)
    }

    @Test
    fun `pence are divided by 100 and missing rates are reported`() {
        val positions = PortfolioCalculator.positions(
            instruments = listOf(instrument(1, "VOD.L", currency = "GBp"), instrument(2, "SAP.DE", currency = "EUR")),
            lots = listOf(lot(1, 1, "100", quantity = "100", currency = "GBp"), lot(2, 2, "10", quantity = "1", currency = "EUR")),
            quotes = listOf(quote(1, "100", "GBp"), quote(2, "10", "EUR")),
        )
        val summary = SummaryCalculator.summarize(positions, emptyList(), listOf(FxRate("GBP", day, bd("5"))))

        // 100 shares at 100 pence = 100 GBP = 500 PLN; EUR has no rate.
        assertDecimal("500", summary.valuePln)
        assertEquals(setOf("EUR"), summary.missingFx)
    }

    @Test
    fun `lots without quantity are counted separately`() {
        val positions = PortfolioCalculator.positions(listOf(instrument(1)), listOf(lot(1, 1, "10")), listOf(quote(1, "11")))
        val summary = SummaryCalculator.summarize(positions, emptyList(), emptyList())
        assertEquals(1, summary.lotsWithoutQuantity)
        assertNull(summary.valuePln)
        assertTrue(summary.perCurrency.isEmpty())
    }

    @Test
    fun `allocation by kind adds up instruments`() {
        val positions = PortfolioCalculator.positions(
            instruments = listOf(instrument(1, "A"), instrument(2, "B"), instrument(3, "BTC", AssetKind.CRYPTO, null)),
            lots = listOf(lot(1, 1, "1", "100"), lot(2, 2, "1", "100"), lot(3, 3, "1", "200")),
            quotes = listOf(quote(1, "1"), quote(2, "1"), quote(3, "1")),
        )
        val byKind = SummaryCalculator.summarize(positions, emptyList(), emptyList()).byKind
        assertEquals(listOf(AssetKind.STOCK, AssetKind.CRYPTO).toSet(), byKind.map { it.kind }.toSet())
        assertEquals(0.5f, byKind.first { it.kind == AssetKind.STOCK }.share, 0.0001f)
    }

    @Test
    fun `timeline values open lots with the rate of each day`() {
        val start = LocalDate.of(2026, 1, 5)
        val lots = listOf(
            lot(1, 1, "100", quantity = "2", currency = "USD", date = start),
            lot(2, 1, "100", quantity = "1", currency = "USD", date = start.plusDays(2), sellDate = start.plusDays(3), sellPrice = "120"),
        )
        val prices = mapOf(
            (1L to "USD") to TreeMap(mapOf(start to bd("100"), start.plusDays(1) to bd("110"), start.plusDays(3) to bd("120"))),
        )
        val fx = mapOf("USD" to TreeMap(mapOf(start to bd("4"), start.plusDays(2) to bd("5"))))

        val points = PortfolioTimeline.build(lots, prices, fx, start, start.plusDays(3))

        assertEquals(4, points.size)
        assertDecimal("800", points[0].value) // 2 * 100 * 4
        assertDecimal("880", points[1].value) // 2 * 110 * 4
        assertDecimal("1650", points[2].value) // 3 * 110 (carried) * 5
        assertDecimal("1500", points[2].invested) // 3 * 100 * 5
        assertDecimal("1200", points[3].value) // lot 2 sold that day: 2 * 120 * 5
    }

    @Test
    fun `long timelines use weekly points and end today`() {
        val start = LocalDate.of(2024, 1, 1)
        val end = LocalDate.of(2026, 1, 3)
        val points = PortfolioTimeline.build(
            lots = listOf(lot(1, 1, "10", quantity = "1", date = start)),
            prices = mapOf((1L to "PLN") to TreeMap(mapOf(start to BigDecimal.TEN))),
            fx = emptyMap(),
            from = start,
            to = end,
        )
        assertEquals(7L, java.time.temporal.ChronoUnit.DAYS.between(points[0].date, points[1].date))
        assertEquals(end, points.last().date)
    }

    @Test
    fun `stocks are skipped at the weekend once fetched after friday`() {
        val warsaw = ZoneId.of("Europe/Warsaw")
        val saturdayNoon = ZonedDateTime.of(2026, 10, 31, 12, 0, 0, 0, warsaw)
        assertEquals(DayOfWeek.SATURDAY, saturdayNoon.dayOfWeek)
        val fridayEvening = saturdayNoon.minusHours(16).toInstant()
        val saturdayMorning = saturdayNoon.minusHours(4).toInstant()

        assertEquals(false, MarketHours.canSkipStocks(saturdayNoon.toInstant(), fridayEvening))
        assertEquals(true, MarketHours.canSkipStocks(saturdayNoon.toInstant(), saturdayMorning))
        assertEquals(true, MarketHours.canSkipStocks(saturdayNoon.plusDays(1).toInstant(), saturdayMorning))
        assertEquals(false, MarketHours.canSkipStocks(saturdayNoon.plusDays(2).toInstant(), saturdayMorning))
        assertEquals(false, MarketHours.canSkipStocks(saturdayNoon.toInstant(), null))
    }
}
