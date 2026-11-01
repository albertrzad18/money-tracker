package io.github.albertrzad18.moneytracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import java.math.BigDecimal

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F6E56),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F2D4),
    onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF4B635A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE9DC),
    onSecondaryContainer = Color(0xFF072019),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AD5B8),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF00513E),
    onPrimaryContainer = Color(0xFFA6F2D4),
    secondary = Color(0xFFB2CCC1),
    onSecondary = Color(0xFF1E352D),
    secondaryContainer = Color(0xFF344C43),
    onSecondaryContainer = Color(0xFFCDE9DC),
)

@Composable
fun MoneyTrackerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Green for gains, red for losses, neutral for zero or unknown. Always shown next to a +/− sign. */
@Composable
fun gainColor(value: BigDecimal?): Color {
    val dark = isSystemInDarkTheme()
    return when {
        value == null || value.signum() == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        value.signum() > 0 -> if (dark) Color(0xFF6DD58C) else Color(0xFF1B7F3B)
        else -> if (dark) Color(0xFFFF8A80) else Color(0xFFC62828)
    }
}

/** Categorical series colors (validated reference palette), in fixed order. */
object ChartColors {
    private val light = listOf(
        Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100),
        Color(0xFFE87BA4), Color(0xFF008300), Color(0xFF6250D6), Color(0xFFE34948),
    )
    private val dark = listOf(
        Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500),
        Color(0xFFD55181), Color(0xFF008300), Color(0xFF9085E9), Color(0xFFE66767),
    )

    /** Neutral slot used for the folded "Other" group. */
    val other = Color(0xFF8A8985)

    const val MAX_SLOTS = 8

    @Composable
    fun slots(): List<Color> = if (isSystemInDarkTheme()) dark else light
}
