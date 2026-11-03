package io.github.albertrzad18.moneytracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.albertrzad18.moneytracker.R

@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: FontWeight) =
    Font(R.font.manrope, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

/** Manrope variable font, weights 400–800. */
val Manrope = FontFamily(
    manrope(FontWeight.Normal),
    manrope(FontWeight.Medium),
    manrope(FontWeight.SemiBold),
    manrope(FontWeight.Bold),
    manrope(FontWeight.ExtraBold),
)

/** Tabular figures everywhere, so numbers line up in lists. */
private fun style(size: Float, weight: FontWeight, lineHeight: Float = 1.4f, tracking: Float = 0f) = TextStyle(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = tracking.em,
    fontFeatureSettings = "tnum",
)

/**
 * Roles: displayLarge portfolio value, displayMedium instrument price, displaySmall large screen title,
 * headlineMedium sheet / empty-state title, headlineSmall small title and dialog, titleLarge top bar and
 * position result, titleMedium symbol and section, titleSmall stat value, labelLarge range pill,
 * labelMedium meta, labelSmall navigation.
 */
val MoneyTypography = Typography(
    displayLarge = style(40f, FontWeight.Bold, 1.1f, -0.01f),
    displayMedium = style(38f, FontWeight.Bold, 1.1f),
    displaySmall = style(30f, FontWeight.Bold, 1.1f),
    headlineLarge = style(26f, FontWeight.Bold, 1.15f),
    headlineMedium = style(24f, FontWeight.Bold, 1.15f),
    headlineSmall = style(22f, FontWeight.Bold, 1.2f),
    titleLarge = style(20f, FontWeight.Bold, 1.15f),
    titleMedium = style(19f, FontWeight.Bold, 1.15f),
    titleSmall = style(16f, FontWeight.Bold, 1.3f),
    bodyLarge = style(15f, FontWeight.Normal),
    bodyMedium = style(14f, FontWeight.Normal),
    bodySmall = style(12f, FontWeight.Normal, 1.35f),
    labelLarge = style(13f, FontWeight.Bold, 1.3f),
    labelMedium = style(12f, FontWeight.SemiBold, 1.3f),
    labelSmall = style(11.5f, FontWeight.Medium, 1.3f),
)
