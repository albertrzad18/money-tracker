package io.github.albertrzad18.moneytracker.domain

/**
 * XTB lists ETFs under the index name ("NASDAQ 100" for CNDX.UK), but Yahoo search ranks other funds
 * first and names Warsaw ETFs only by ticker ("ETFBNDXPL C.WETP PLN"). This maps index names to the
 * Yahoo symbols of the ETFs most likely meant, so they come up first.
 */
object EtfAliases {
    private val aliases = mapOf(
        "NASDAQ100" to listOf("CNDX.L", "ETFBNDXPL.WA", "ETFBNQ2ST.WA", "ETFBNQ3LV.WA"),
        "NDX" to listOf("CNDX.L", "ETFBNDXPL.WA"),
        "SP500" to listOf("CSPX.L", "ETFBSPXPL.WA"),
        "SPX" to listOf("CSPX.L", "ETFBSPXPL.WA"),
        "WIG20" to listOf("ETFBW20TR.WA", "ETFBW20LV.WA", "ETFBW20ST.WA"),
        "MWIG40" to listOf("ETFBM40TR.WA", "ETFBM40LV.WA", "ETFBM40ST.WA"),
        "SWIG80" to listOf("ETFBS80TR.WA"),
        "DYWIDENDA" to listOf("ETFBDIVPL.WA"),
        "DIVIDEND" to listOf("ETFBDIVPL.WA"),
        "TBSP" to listOf("ETFBTBSP.WA"),
        "OBLIGACJE" to listOf("ETFBTBSP.WA"),
        "BITCOIN" to listOf("ETFBTCPL.WA"),
        "NATO" to listOf("ETFNATO.WA"),
    )

    private const val MIN_PREFIX = 3

    /** Yahoo symbols of ETFs tracking what [query] names; "nasdaq", "S&P 500", "wig 20" all match. */
    fun symbolsFor(query: String): List<String> {
        val normalized = query.uppercase().filter { it.isLetterOrDigit() }
        if (normalized.length < MIN_PREFIX) return emptyList()
        return aliases
            .filter { (alias, _) -> alias.startsWith(normalized) || normalized.startsWith(alias) }
            .values.flatten().distinct()
    }
}
