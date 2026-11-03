package io.github.albertrzad18.moneytracker.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import java.math.BigDecimal

// Palette "1b Spokojna lista" (Organic: cream, sand, terracotta, sage).

internal val LightColors = lightColorScheme(
    primary = Color(0xFFC67139),
    onPrimary = Color(0xFFF9F4ED),
    primaryContainer = Color(0xFFFFE1D0),
    onPrimaryContainer = Color(0xFF643312),
    secondary = Color(0xFF7A8A5E),
    onSecondary = Color(0xFFF9F4ED),
    secondaryContainer = Color(0xFFFFE1D0),
    onSecondaryContainer = Color(0xFF643312),
    tertiary = Color(0xFF8C491A),
    onTertiary = Color(0xFFF9F4ED),
    background = Color(0xFFF5EAD8),
    onBackground = Color(0xFF201E1D),
    surface = Color(0xFFEBDDC5),
    onSurface = Color(0xFF201E1D),
    surfaceVariant = Color(0xFFEBDDC5),
    onSurfaceVariant = Color(0xFF645C50),
    surfaceContainerLowest = Color(0xFFF5EAD8),
    surfaceContainerLow = Color(0xFFEBDDC5),
    surfaceContainer = Color(0xFFEBDDC5),
    surfaceContainerHigh = Color(0xFFEBDDC5),
    surfaceContainerHighest = Color(0xFFEBDDC5),
    surfaceBright = Color(0xFFF5EAD8),
    surfaceDim = Color(0xFFEBDDC5),
    inverseSurface = Color(0xFF201E1D),
    inverseOnSurface = Color(0xFFF5EAD8),
    inversePrimary = Color(0xFFD67F48),
    error = Color(0xFFA83A2C),
    onError = Color(0xFFF9F4ED),
    errorContainer = Color(0xFFF6D9CF),
    onErrorContainer = Color(0xFFA83A2C),
    outline = Color(0x29201E1D),
    outlineVariant = Color(0x1A201E1D),
    scrim = Color(0x6B2E2B25),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFD67F48),
    onPrimary = Color(0xFF201E1D),
    primaryContainer = Color(0xFF643312),
    onPrimaryContainer = Color(0xFFFFE1D0),
    secondary = Color(0xFFAEBF92),
    onSecondary = Color(0xFF201E1D),
    secondaryContainer = Color(0xFF643312),
    onSecondaryContainer = Color(0xFFFFE1D0),
    tertiary = Color(0xFFF6A06B),
    onTertiary = Color(0xFF201E1D),
    background = Color(0xFF1F1D1A),
    onBackground = Color(0xFFF5EAD8),
    surface = Color(0xFF2D2A25),
    onSurface = Color(0xFFF5EAD8),
    surfaceVariant = Color(0xFF2D2A25),
    onSurfaceVariant = Color(0xFFC0B6A5),
    surfaceContainerLowest = Color(0xFF1F1D1A),
    surfaceContainerLow = Color(0xFF2D2A25),
    surfaceContainer = Color(0xFF2D2A25),
    surfaceContainerHigh = Color(0xFF2D2A25),
    surfaceContainerHighest = Color(0xFF2D2A25),
    surfaceBright = Color(0xFF2D2A25),
    surfaceDim = Color(0xFF1F1D1A),
    inverseSurface = Color(0xFFF5EAD8),
    inverseOnSurface = Color(0xFF1F1D1A),
    inversePrimary = Color(0xFFC67139),
    error = Color(0xFFF19A87),
    onError = Color(0xFF201E1D),
    errorContainer = Color(0xFF45261F),
    onErrorContainer = Color(0xFFF19A87),
    outline = Color(0x29F5EAD8),
    outlineVariant = Color(0x1AF5EAD8),
    scrim = Color(0x8C000000),
)

/** Colors outside the Material scheme: results, charts, the green block, switch track. */
@Immutable
data class MoneyColors(
    val gain: Color,
    val gainContainer: Color,
    val loss: Color,
    val lossContainer: Color,
    val neutral: Color,
    val chartLine: Color,
    val chartFill: Color,
    val chartSecondary: Color,
    /** Bar tracks and hairlines. */
    val line: Color,
    /** Background of fields and lists inside a card. */
    val inset: Color,
    val soft: Color,
    val onSoft: Color,
    val switchTrackOff: Color,
    val switchThumb: Color,
    val widgetBackground: Color,
    /** Share colors, largest first (donut, structure bar). */
    val shares: List<Color>,
    val isDark: Boolean,
) {
    fun tone(tone: Tone): Color = when (tone) {
        Tone.UP -> gain
        Tone.DOWN -> loss
        Tone.FLAT -> neutral
    }

    fun toneContainer(tone: Tone): Color = when (tone) {
        Tone.UP -> gainContainer
        Tone.DOWN -> lossContainer
        Tone.FLAT -> line
    }
}

private val Shares = listOf(Color(0xFFC67139), Color(0xFF7A8A5E), Color(0xFFD9A441), Color(0xFF8C491A), Color(0xFFA19786))

internal val LightMoneyColors = MoneyColors(
    gain = Color(0xFF56633F),
    gainContainer = Color(0xFFDFE9C9),
    loss = Color(0xFFA83A2C),
    lossContainer = Color(0xFFF6D9CF),
    neutral = Color(0xFF645C50),
    chartLine = Color(0xFFC67139),
    chartFill = Color(0x29C67139),
    chartSecondary = Color(0xFF7A8A5E),
    line = Color(0x1A201E1D),
    inset = Color(0xFFF5EAD8),
    soft = Color(0xFFE1EECC),
    onSoft = Color(0xFF3D472B),
    switchTrackOff = Color(0xFFDCD3C4),
    switchThumb = Color(0xFFF9F4ED),
    widgetBackground = Color(0xFFF9F4ED),
    shares = Shares,
    isDark = false,
)

internal val DarkMoneyColors = MoneyColors(
    gain = Color(0xFFB9CB98),
    gainContainer = Color(0xFF323A24),
    loss = Color(0xFFF19A87),
    lossContainer = Color(0xFF45261F),
    neutral = Color(0xFFC0B6A5),
    chartLine = Color(0xFFE08A52),
    chartFill = Color(0x2EE08A52),
    chartSecondary = Color(0xFFAEBF92),
    line = Color(0x1AF5EAD8),
    inset = Color(0xFF1F1D1A),
    soft = Color(0xFF3D472B),
    onSoft = Color(0xFFE1EECC),
    switchTrackOff = Color(0xFF474238),
    switchThumb = Color(0xFFF5EAD8),
    widgetBackground = Color(0xFF262420),
    shares = Shares,
    isDark = true,
)

val LocalMoneyColors = staticCompositionLocalOf { LightMoneyColors }

enum class Tone { UP, DOWN, FLAT }

private val PCT_FLAT = BigDecimal("0.05")
private val AMOUNT_FLAT = BigDecimal("0.005")

/** Tone of a percent result; |x| < 0,05 counts as flat, like the rounded "0,0%". */
fun toneOfPct(pct: BigDecimal?): Tone = toneOf(pct, PCT_FLAT)

/** Tone of an amount; anything that rounds to zero cents is flat. */
fun toneOfAmount(amount: BigDecimal?): Tone = toneOf(amount, AMOUNT_FLAT)

private fun toneOf(value: BigDecimal?, flatBelow: BigDecimal): Tone = when {
    value == null || value.abs() < flatBelow -> Tone.FLAT
    value.signum() > 0 -> Tone.UP
    else -> Tone.DOWN
}

/** Green for gains, red for losses, muted for flat or unknown. Always shown next to a +/− sign. */
@Composable
@ReadOnlyComposable
fun gainColor(pct: BigDecimal?): Color = LocalMoneyColors.current.tone(toneOfPct(pct))

@Composable
@ReadOnlyComposable
fun amountColor(amount: BigDecimal?): Color = LocalMoneyColors.current.tone(toneOfAmount(amount))
