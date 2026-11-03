package io.github.albertrzad18.moneytracker.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/** M3 emphasized decelerate, the main curve of the app. */
val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

object Durations {
    const val SCREEN_PUSH = 430
    const val TAB_FADE = 240
    const val TAB_SLIDE = 360
    const val FAB = 320
    const val SHEET = 420
    const val SCRIM = 320
    const val DIALOG_SCALE = 300
    const val DIALOG_FADE = 240
    const val SWITCH_THUMB = 240
    const val SWITCH_TRACK = 220
    const val REFRESH_TURN = 1100
    const val SNACKBAR_VISIBLE = 2200L
    const val CHART_MORPH = 420
    const val DONUT = 500
    const val PRESS = 160
}

fun <T> emphasized(durationMillis: Int, delayMillis: Int = 0): FiniteAnimationSpec<T> =
    tween(durationMillis, delayMillis, Emphasized)
