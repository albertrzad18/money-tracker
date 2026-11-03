package io.github.albertrzad18.moneytracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/** The Organic palette by default; [dynamicColor] switches the Material scheme to Material You. */
@Composable
fun MoneyTrackerTheme(
    dark: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalMoneyColors provides if (dark) DarkMoneyColors else LightMoneyColors) {
        MaterialTheme(colorScheme = colors, typography = MoneyTypography, shapes = MoneyShapes) {
            // Screens draw straight on the background (no Surface), so text defaults to its color here.
            CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
        }
    }
}

/** Shortcut for the extra colors: `MoneyTheme.colors.gain`. */
object MoneyTheme {
    val colors: MoneyColors
        @Composable get() = LocalMoneyColors.current
}
