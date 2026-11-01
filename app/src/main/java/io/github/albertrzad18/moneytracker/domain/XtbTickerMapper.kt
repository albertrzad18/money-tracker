package io.github.albertrzad18.moneytracker.domain

/**
 * XTB shows tickers as `SYMBOL.COUNTRY` (PKN.PL, AAPL.US, CSPX.UK). Yahoo uses exchange suffixes
 * instead (PKN.WA, AAPL, CSPX.L). Anything that does not look like an XTB ticker returns null.
 */
object XtbTickerMapper {
    private val suffixes = mapOf(
        "PL" to ".WA",
        "US" to "",
        "UK" to ".L",
        "DE" to ".DE",
        "NL" to ".AS",
        "FR" to ".PA",
        "IT" to ".MI",
        "ES" to ".MC",
        "PT" to ".LS",
        "BE" to ".BR",
        "CH" to ".SW",
        "DK" to ".CO",
        "SE" to ".ST",
        "NO" to ".OL",
        "FI" to ".HE",
        "CZ" to ".PR",
        "AT" to ".VI",
        "IE" to ".IR",
    )

    private val pattern = Regex("^([A-Z0-9][A-Z0-9.\\-]*)\\.([A-Z]{2})$")

    fun toYahoo(input: String): String? {
        val match = pattern.matchEntire(input.trim().uppercase()) ?: return null
        val (base, market) = match.destructured
        val suffix = suffixes[market] ?: return null
        // US share classes: XTB "BRK.B.US" is Yahoo "BRK-B".
        val symbol = if (market == "US") base.replace('.', '-') else base
        return symbol + suffix
    }
}
