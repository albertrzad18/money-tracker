package io.github.albertrzad18.moneytracker.domain

import java.math.BigDecimal

/** Converts amounts to PLN with NBP mid rates (1 unit of currency = rate PLN). */
object Fx {
    /**
     * NBP code for [currency] and the minor-unit divisor. Yahoo quotes some London listings
     * in pence (GBp), which is 1/100 of GBP.
     */
    fun normalize(currency: String): Pair<String, BigDecimal> = when (currency) {
        "GBp", "GBX" -> "GBP" to BigDecimal(100)
        "ZAc" -> "ZAR" to BigDecimal(100)
        "ILA" -> "ILS" to BigDecimal(100)
        else -> currency.uppercase() to BigDecimal.ONE
    }

    /** PLN value of one unit of [currency], or null when the rate is unknown. */
    fun rateToPln(currency: String, rates: Map<String, BigDecimal>): BigDecimal? {
        val (code, divisor) = normalize(currency)
        val rate = if (code == "PLN") BigDecimal.ONE else rates[code] ?: return null
        return rate.divide(divisor, Calc.MC)
    }

    fun toPln(amount: BigDecimal, currency: String, rates: Map<String, BigDecimal>): BigDecimal? =
        rateToPln(currency, rates)?.multiply(amount)

    fun needsRate(currency: String): Boolean = normalize(currency).first != "PLN"
}
