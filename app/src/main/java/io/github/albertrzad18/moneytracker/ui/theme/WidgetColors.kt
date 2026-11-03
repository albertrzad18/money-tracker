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
    val chart: Color,
    val line: Color,
) {
    fun tone(pct: BigDecimal?): Color = when (toneOfPct(pct)) {
        Tone.UP -> gain
        Tone.DOWN -> loss
        Tone.FLAT -> muted
    }

    companion object {
        val Light = WidgetColors(
            background = LightMoneyColors.widgetBackground,
            text = LightColors.onBackground,
            muted = LightColors.onSurfaceVariant,
            gain = LightMoneyColors.gain,
            loss = LightMoneyColors.loss,
            chart = LightMoneyColors.chartLine,
            line = LightMoneyColors.line,
        )
        val Dark = WidgetColors(
            background = DarkMoneyColors.widgetBackground,
            text = DarkColors.onBackground,
            muted = DarkColors.onSurfaceVariant,
            gain = DarkMoneyColors.gain,
            loss = DarkMoneyColors.loss,
            chart = DarkMoneyColors.chartLine,
            line = DarkMoneyColors.line,
        )

        fun of(dark: Boolean): WidgetColors = if (dark) Dark else Light
    }
}
