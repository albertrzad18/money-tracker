package io.github.albertrzad18.moneytracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.albertrzad18.moneytracker.ui.theme.Durations
import io.github.albertrzad18.moneytracker.ui.theme.Pill
import io.github.albertrzad18.moneytracker.ui.theme.emphasized
import kotlinx.coroutines.delay

/** App-wide confirmation messages ("Kursy zaktualizowane", "Zakup usunięty"…). */
@Stable
class SnackbarController {
    private data class Message(val text: String, val id: Long)

    private var current by mutableStateOf<Message?>(null)
    private var counter = 0L

    val text: String? get() = current?.text
    internal val id: Long get() = current?.id ?: 0L

    fun show(text: String) {
        current = Message(text, ++counter)
    }

    internal fun dismiss(id: Long) {
        if (current?.id == id) current = null
    }
}

val LocalSnackbar = staticCompositionLocalOf { SnackbarController() }

/**
 * Pill snackbar in inverted colors (text color background), 14/600. Shows for 2.2 s and enters with
 * a fade and a 16 dp rise.
 */
@Composable
fun MoneySnackbarHost(controller: SnackbarController, modifier: Modifier = Modifier) {
    val text = controller.text
    val id = controller.id
    // Keeps the last text visible while the snackbar fades out.
    val lastText = remember { arrayOf("") }
    if (text != null) lastText[0] = text
    LaunchedEffect(id) {
        if (id != 0L) {
            delay(Durations.SNACKBAR_VISIBLE)
            controller.dismiss(id)
        }
    }
    AnimatedVisibility(
        visible = text != null,
        modifier = modifier,
        enter = fadeIn(emphasized(Durations.DIALOG_FADE)) + slideInVertically(emphasized(Durations.TAB_SLIDE)) { it / 3 },
        exit = fadeOut(emphasized(Durations.DIALOG_FADE)) + slideOutVertically(emphasized(Durations.TAB_SLIDE)) { it / 3 },
    ) {
        Surface(
            shape = Pill,
            color = MaterialTheme.colorScheme.onBackground,
            contentColor = MaterialTheme.colorScheme.background,
            shadowElevation = 3.dp,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                lastText[0],
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}
