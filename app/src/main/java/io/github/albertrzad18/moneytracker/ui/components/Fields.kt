package io.github.albertrzad18.moneytracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.albertrzad18.moneytracker.R
import io.github.albertrzad18.moneytracker.ui.theme.MoneyTheme
import io.github.albertrzad18.moneytracker.ui.theme.Pill
import io.github.albertrzad18.moneytracker.util.Formatters
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Single-line pill field. The border is the outline color, the accent while focused (or always when
 * [accentBorder]) and the loss color on error. [label] sits above the field.
 */
@Composable
fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    height: Dp = 52.dp,
    isError: Boolean = false,
    accentBorder: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    fieldModifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val money = MoneyTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = when {
        isError -> money.loss
        accentBorder || focused -> scheme.primary
        else -> scheme.outline
    }
    val borderWidth = if (isError || accentBorder || focused) 1.5.dp else 1.dp
    Column(modifier.fillMaxWidth()) {
        if (label != null) FieldLabel(label)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onBackground),
            cursorBrush = SolidColor(scheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            interactionSource = interaction,
            modifier = fieldModifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(height)
                        .clip(Pill)
                        .background(money.inset)
                        .border(borderWidth, borderColor, Pill)
                        .padding(start = if (leading != null) 16.dp else 18.dp, end = if (trailing != null) 6.dp else 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leading != null) {
                        leading()
                        Spacer(Modifier.width(10.dp))
                    }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1)
                        }
                        inner()
                    }
                    trailing?.invoke()
                }
            },
        )
    }
}

@Composable
fun PillDecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    height: Dp = 52.dp,
    placeholder: String? = null,
) {
    PillTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        modifier = modifier,
        isError = isError,
        height = height,
        keyboardType = KeyboardType.Decimal,
    )
}

/** Read-only pill showing a date with a calendar icon; opens a date picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PillDateField(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    minDate: LocalDate? = null,
    maxDate: LocalDate = LocalDate.now(),
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        FieldLabel(label)
        Row(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(Pill)
                .background(MoneyTheme.colors.inset)
                .border(1.dp, scheme.outline, Pill)
                .clickable(role = Role.Button, onClickLabel = label) { open = true }
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Formatters.date(date), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(Lucide.Calendar, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
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
        val colors = DatePickerDefaults.colors(containerColor = scheme.background)
        DatePickerDialog(
            onDismissRequest = { open = false },
            shape = MaterialTheme.shapes.extraLarge,
            colors = colors,
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
            DatePicker(state = state, colors = colors)
        }
    }
}

/** Checkbox drawn as a 24 dp rounded square (radius 8), accent when checked. */
@Composable
fun MoneyCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraSmall
    Box(
        modifier
            .size(24.dp)
            .clip(shape)
            .background(if (checked) scheme.primary else Color.Transparent)
            .border(if (checked) 0.dp else 1.5.dp, if (checked) Color.Transparent else scheme.onSurfaceVariant.copy(alpha = 0.6f), shape)
            .clickable(role = Role.Checkbox, onClickLabel = contentDescription) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Lucide.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(16.dp))
    }
}
