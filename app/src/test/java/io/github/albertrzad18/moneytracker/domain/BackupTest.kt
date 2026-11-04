package io.github.albertrzad18.moneytracker.domain

import io.github.albertrzad18.moneytracker.data.db.AssetKind
import io.github.albertrzad18.moneytracker.data.db.Instrument
import io.github.albertrzad18.moneytracker.data.db.Lot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class BackupTest {
    private val now = Instant.parse("2026-11-04T16:00:00Z")
    private val etf = Instrument(id = 7, kind = AssetKind.ETF, providerId = "CNDX.L", symbol = "CNDX.L", name = "iShares NASDAQ 100", exchange = "LSE", currency = "USD", onWidget = false, sortOrder = 3)
    private val btc = Instrument(id = 9, kind = AssetKind.CRYPTO, providerId = "bitcoin", symbol = "BTC", name = "Bitcoin", exchange = null, currency = null)
    private val open = Lot(id = 1, instrumentId = 7, buyDate = LocalDate.of(2026, 10, 20), buyPrice = BigDecimal("1625.00"), quantity = BigDecimal("0.154"), currency = "USD", alertUpPct = BigDecimal("15"), alertUpFired = true)
    private val sold = Lot(id = 2, instrumentId = 9, buyDate = LocalDate.of(2025, 1, 5), buyPrice = BigDecimal("0.00001234"), currency = "PLN", sellDate = LocalDate.of(2026, 3, 1), sellPrice = BigDecimal("410000"))

    @Test
    fun `round trip keeps instruments and every purchase exactly`() {
        val imported = Backup.decode(Backup.encode(listOf(etf, btc), listOf(open, sold), now))

        assertEquals(2, imported.size)
        val (instrument, lots) = imported[0]
        assertEquals(etf.copy(id = 0, sortOrder = 0), instrument)
        val lot = lots.single()
        assertEquals(open.buyDate, lot.buyDate)
        assertEquals(0, open.buyPrice.compareTo(lot.buyPrice))
        assertEquals(0, open.quantity!!.compareTo(lot.quantity))
        assertEquals(0, BigDecimal(15).compareTo(lot.alertUpPct))
        assertEquals(null, lot.alertDownPct)
        // Fired alerts start over in the new installation.
        assertFalse(lot.alertUpFired)

        val crypto = imported[1].lots.single()
        assertEquals(BigDecimal("0.00001234"), crypto.buyPrice)
        assertEquals(LocalDate.of(2026, 3, 1), crypto.sellDate)
        assertEquals(0, BigDecimal("410000").compareTo(crypto.sellPrice))
        assertEquals(null, crypto.quantity)
    }

    @Test
    fun `imported lots match the originals as the same purchase`() {
        val imported = Backup.decode(Backup.encode(listOf(etf, btc), listOf(open, sold), now))
        assertTrue(Backup.isSamePurchase(open, imported[0].lots.single()))
        assertTrue(Backup.isSamePurchase(sold, imported[1].lots.single()))
    }

    @Test
    fun `a different price, quantity or sale is another purchase`() {
        assertFalse(Backup.isSamePurchase(open, open.copy(buyPrice = BigDecimal("1626"))))
        assertFalse(Backup.isSamePurchase(open, open.copy(quantity = null)))
        assertFalse(Backup.isSamePurchase(open, open.copy(sellDate = LocalDate.of(2026, 11, 1), sellPrice = BigDecimal(1700))))
        assertTrue(Backup.isSamePurchase(open, open.copy(buyPrice = BigDecimal("1625"), quantity = BigDecimal("0.1540"))))
    }

    @Test
    fun `rejects files that are not a valid backup`() {
        listOf(
            "not json",
            """{"format":"something-else","exportedAt":"x","instruments":[]}""",
            """{"format":"money-tracker-backup","version":99,"exportedAt":"x","instruments":[]}""",
            """{"format":"money-tracker-backup","exportedAt":"x","instruments":[{"kind":"BOND","providerId":"X","symbol":"X","name":"X"}]}""",
            """{"format":"money-tracker-backup","exportedAt":"x","instruments":[{"kind":"ETF","providerId":"X","symbol":"X","name":"X","lots":[{"buyDate":"2026-13-01","buyPrice":"1","currency":"PLN"}]}]}""",
            """{"format":"money-tracker-backup","exportedAt":"x","instruments":[{"kind":"ETF","providerId":"X","symbol":"X","name":"X","lots":[{"buyDate":"2026-01-01","buyPrice":"abc","currency":"PLN"}]}]}""",
        ).forEach { text ->
            assertThrows(text, InvalidBackupException::class.java) { Backup.decode(text) }
        }
    }
}
