package io.github.albertrzad18.moneytracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.albertrzad18.moneytracker.R
import io.github.albertrzad18.moneytracker.ui.theme.gainColor
import io.github.albertrzad18.moneytracker.util.Formatters
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun GainText(pct: BigDecimal?, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyMedium) {
    Text(
        text = Formatters.pct(pct),
        modifier = modifier,
        style = style,
        color = gainColor(pct),
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Label on the left, value on the right. */
@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Row(modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun DecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

/** Read-only field that opens a date picker. Dates after [maxDate] or before [minDate] cannot be picked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minDate: LocalDate? = null,
    maxDate: LocalDate = LocalDate.now(),
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = Formatters.date(date),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // The text field swallows clicks, so an overlay opens the dialog.
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val day = Instant.ofEpochMilli(utcTimeMillis).atOffset(ZoneOffset.UTC).toLocalDate()
                    return day <= maxDate && (minDate == null || day >= minDate)
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onDateChange(Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DatePicker(state = state)
        }
    }
}
