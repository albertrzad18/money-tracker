package io.github.albertrzad18.moneytracker.util

import android.text.format.DateFormat
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {
    private const val MINUS = '−'
    private val TEN_THOUSAND = BigDecimal(10_000)
    private val CENT = BigDecimal("0.01")

    /** Price with precision that fits its size: 305 914 / 344,63 / 0,5432 / 0,00001234. */
    fun price(value: BigDecimal, locale: Locale = Locale.getDefault()): String {
        val abs = value.abs()
        val (min, max) = when {
            abs >= TEN_THOUSAND -> 0 to 0
            abs >= BigDecimal.ONE -> 2 to 2
            abs >= CENT -> 2 to 4
            else -> 2 to 8
        }
        return number(value, min, max, locale)
    }

    fun money(value: BigDecimal, currency: String, locale: Locale = Locale.getDefault()): String =
        "${price(value, locale)} ${currencySymbol(currency)}"

    /** Amount with an explicit sign, for gains and losses. */
    fun signedMoney(value: BigDecimal, currency: String, locale: Locale = Locale.getDefault()): String {
        val formatted = money(value.abs(), currency, locale)
        return when {
            isZero(value, 2) -> formatted
            value.signum() > 0 -> "+$formatted"
            else -> "$MINUS$formatted"
        }
    }

    /** "+22,4%", "−10,0%", "0,0%"; an em dash when unknown. */
    fun pct(value: BigDecimal?, locale: Locale = Locale.getDefault()): String {
        if (value == null) return "—"
        val formatted = number(value.abs(), 1, 1, locale) + "%"
        return when {
            isZero(value, 1) -> formatted
            value.signum() > 0 -> "+$formatted"
            else -> "$MINUS$formatted"
        }
    }

    fun quantity(value: BigDecimal, locale: Locale = Locale.getDefault()): String = number(value, 0, 8, locale)

    /** Plain number for an input field, using the locale's decimal separator. */
    fun editable(value: BigDecimal, locale: Locale = Locale.getDefault()): String {
        val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        return value.stripTrailingZeros().toPlainString().replace('.', separator)
    }

    /** Accepts both "1 234,5" and "1234.5". Returns null for anything else. */
    fun parseDecimal(input: String): BigDecimal? {
        val cleaned = input.trim()
            .replace(" ", "")
            .replace(" ", "")
            .replace(" ", "")
            .replace(',', '.')
        if (cleaned.isEmpty()) return null
        return cleaned.toBigDecimalOrNull()
    }

    fun currencySymbol(code: String): String = when (code) {
        "PLN" -> "zł"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "GBp", "GBX" -> "GBX"
        else -> code
    }

    fun date(date: LocalDate): String = pattern("ddMMyyyy").format(date)

    fun shortDate(date: LocalDate): String = pattern("ddMMyy").format(date)

    /** Time only for today, otherwise day and time. */
    fun dateTime(instant: Instant, now: Instant = Instant.now()): String {
        val zone = ZoneId.systemDefault()
        val local = instant.atZone(zone)
        val skeleton = if (local.toLocalDate() == now.atZone(zone).toLocalDate()) "jm" else "ddMMjm"
        return pattern(skeleton).format(local)
    }

    private fun pattern(skeleton: String): DateTimeFormatter {
        val locale = Locale.getDefault()
        return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }

    private fun number(value: BigDecimal, minFraction: Int, maxFraction: Int, locale: Locale): String {
        val format = NumberFormat.getNumberInstance(locale) as DecimalFormat
        format.minimumFractionDigits = minFraction
        format.maximumFractionDigits = maxFraction
        format.roundingMode = RoundingMode.HALF_UP
        format.isGroupingUsed = true
        return format.format(value)
    }

    private fun isZero(value: BigDecimal, scale: Int): Boolean =
        value.setScale(scale, RoundingMode.HALF_UP).signum() == 0
}
