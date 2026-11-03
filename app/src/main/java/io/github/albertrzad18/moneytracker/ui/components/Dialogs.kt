package io.github.albertrzad18.moneytracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.albertrzad18.moneytracker.R
import io.github.albertrzad18.moneytracker.ui.theme.CardShape
import io.github.albertrzad18.moneytracker.ui.theme.Durations
import io.github.albertrzad18.moneytracker.ui.theme.emphasized
import kotlinx.coroutines.launch

/** Dialog card (radius 32, padding 24) that scales from .94 and fades in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyDialog(
    onDismissRequest: () -> Unit,
    title: String,
    body: String,
    buttons: @Composable () -> Unit,
) {
    val scale = remember { Animatable(0.94f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, emphasized(Durations.DIALOG_SCALE)) }
        launch { alpha.animateTo(1f, emphasized(Durations.DIALOG_FADE)) }
    }
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = CardShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            },
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                    buttons()
                }
            }
        }
    }
}

/** "Usunąć zakup?" with the purchase date; [onConfirm] deletes it. */
@Composable
fun DeleteLotDialog(buyDate: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    MoneyDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.delete_lot_title),
        body = stringResource(R.string.delete_lot_body, buyDate),
    ) {
        PillButton(stringResource(R.string.action_cancel), onDismiss, style = PillButtonStyle.Text, height = 44.dp)
        PillButton(stringResource(R.string.action_delete), onConfirm, style = PillButtonStyle.Danger, height = 44.dp)
    }
}
