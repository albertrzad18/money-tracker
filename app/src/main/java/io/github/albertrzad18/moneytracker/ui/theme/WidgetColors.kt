package io.github.albertrzad18.moneytracker.ui.theme

import androidx.compose.ui.graphics.Color
import java.math.BigDecimal

/** Home screen widget palette; also used by the widget preview in Settings. */
data class WidgetColors(
    val background: Color,
    val text: Color,
    val muted: Color,
    val gain: Color,
    val loss: Color,
    val gainContainer: Color,
    val lossContainer: Color,
    /** Text on a tile filled with the full gain or loss color. */
    val onStrong: Color,
    val chart: Color,
    val line: Color,
) {
    fun tone(pct: BigDecimal?): Color = when (toneOfPct(pct)) {
        Tone.UP -> gain
        Tone.DOWN -> loss
        Tone.FLAT -> muted
    }

    /** Colors of a position tile: a soft tint of the result, or the full color for a move of 10% or more. */
    fun tile(pct: BigDecimal?): TileColors {
        val tone = toneOfPct(pct)
        val strong = tone != Tone.FLAT && pct != null && pct.abs() >= STRONG_MOVE
        return when {
            strong -> TileColors(background = tone(pct), symbol = onStrong, value = onStrong)
            tone == Tone.UP -> TileColors(background = gainContainer, symbol = text, value = gain)
            tone == Tone.DOWN -> TileColors(background = lossContainer, symbol = text, value = loss)
            else -> TileColors(background = background, symbol = text, value = muted)
        }
    }

    companion object {
        private val STRONG_MOVE = BigDecimal(10)

        val Light = WidgetColors(
            background = LightMoneyColors.widgetBackground,
            text = LightColors.onBackground,
            muted = LightColors.onSurfaceVariant,
            gain = LightMoneyColors.gain,
            loss = LightMoneyColors.loss,
            gainContainer = LightMoneyColors.gainContainer,
            lossContainer = LightMoneyColors.lossContainer,
            onStrong = LightMoneyColors.widgetBackground,
            chart = LightMoneyColors.chartLine,
            line = LightMoneyColors.line,
        )
        val Dark = WidgetColors(
            background = DarkMoneyColors.widgetBackground,
            text = DarkColors.onBackground,
            muted = DarkColors.onSurfaceVariant,
            gain = DarkMoneyColors.gain,
            loss = DarkMoneyColors.loss,
            gainContainer = DarkMoneyColors.gainContainer,
            lossContainer = DarkMoneyColors.lossContainer,
            onStrong = DarkColors.surfaceContainerLowest,
            chart = DarkMoneyColors.chartLine,
            line = DarkMoneyColors.line,
        )

        fun of(dark: Boolean): WidgetColors = if (dark) Dark else Light
    }
}

data class TileColors(val background: Color, val symbol: Color, val value: Color)
