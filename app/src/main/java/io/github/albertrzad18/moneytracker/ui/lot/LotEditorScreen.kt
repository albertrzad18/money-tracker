package io.github.albertrzad18.moneytracker.ui.lot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.albertrzad18.moneytracker.R
import io.github.albertrzad18.moneytracker.data.db.AssetKind
import io.github.albertrzad18.moneytracker.domain.InstrumentCandidate
import io.github.albertrzad18.moneytracker.ui.components.DateField
import io.github.albertrzad18.moneytracker.ui.components.DecimalField
import io.github.albertrzad18.moneytracker.ui.components.SectionTitle
import io.github.albertrzad18.moneytracker.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotEditorScreen(onDone: () -> Unit, viewModel: LotEditorViewModel = hiltViewModel()) {
    val form = viewModel.form
    val canReturnToSearch = form != null && viewModel.hasSearch
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = canReturnToSearch) { viewModel.backToSearch() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.isEdit) R.string.title_edit_lot else R.string.title_add_lot)) },
                navigationIcon = {
                    IconButton(onClick = { if (canReturnToSearch) viewModel.backToSearch() else onDone() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (viewModel.isEdit) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            if (form == null) {
                if (viewModel.hasSearch) SearchContent(viewModel) else LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                FormContent(viewModel, form, onDone)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_lot_title)) },
            text = { Text(stringResource(R.string.delete_lot_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onDone)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SearchContent(viewModel: LotEditorViewModel) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val search = viewModel.search
    OutlinedTextField(
        value = viewModel.query,
        onValueChange = viewModel::onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focusRequester),
        label = { Text(stringResource(R.string.search_label)) },
        placeholder = { Text(stringResource(R.string.search_placeholder)) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (viewModel.query.isNotEmpty()) {
                IconButton(onClick = { viewModel.onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_clear))
                }
            }
        },
    )
    if (search.loading || viewModel.resolving) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (viewModel.resolveFailed) {
        Text(
            stringResource(R.string.resolve_failed),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
    val results = search.results
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        when {
            viewModel.query.isBlank() -> item { Hint(stringResource(R.string.search_hint)) }
            results.failed -> item { Hint(stringResource(R.string.search_failed)) }
            search.searched && !search.loading && results.securities.isEmpty() && results.crypto.isEmpty() ->
                item { Hint(stringResource(R.string.search_no_results)) }
        }
        if (results.securities.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.search_section_securities), Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)) }
            items(results.securities, key = { "s:" + it.providerId }) { CandidateRow(it) { viewModel.select(it) } }
        }
        if (results.crypto.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.search_section_crypto), Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)) }
            items(results.crypto, key = { "c:" + it.providerId }) { CandidateRow(it) { viewModel.select(it) } }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun CandidateRow(candidate: InstrumentCandidate, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(candidate.symbol, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(candidate.name) },
        trailingContent = {
            Text(
                listOfNotNull(stringResource(kindLabel(candidate.kind)), candidate.exchange).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

fun kindLabel(kind: AssetKind): Int = when (kind) {
    AssetKind.STOCK -> R.string.kind_stock
    AssetKind.ETF -> R.string.kind_etf
    AssetKind.CRYPTO -> R.string.kind_crypto
}

@Composable
private fun FormContent(viewModel: LotEditorViewModel, form: LotForm, onDone: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(form.symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(form.name, form.exchange).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                form.currentPrice?.let {
                    Text(Formatters.money(it, form.currency), style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        DateField(
            date = form.date,
            onDateChange = { date -> viewModel.update { it.copy(date = date) } },
            label = stringResource(R.string.field_buy_date),
        )

        if (form.currencyOptions.size > 1) {
            Text(stringResource(R.string.field_currency), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                form.currencyOptions.forEachIndexed { index, currency ->
                    SegmentedButton(
                        selected = currency == form.currency,
                        onClick = { viewModel.update { it.copy(currency = currency) } },
                        shape = SegmentedButtonDefaults.itemShape(index, form.currencyOptions.size),
                    ) { Text(currency) }
                }
            }
        }

        DecimalField(
            value = form.priceText,
            onValueChange = { text -> viewModel.update { it.copy(priceText = text) } },
            label = stringResource(R.string.field_buy_price, form.currency),
            isError = form.priceError,
            supportingText = form.currentPrice?.let { stringResource(R.string.current_price_hint, Formatters.money(it, form.currency)) },
        )
        form.currentPrice?.let { price ->
            if (form.priceText.isBlank()) {
                TextButton(onClick = { viewModel.update { it.copy(priceText = Formatters.editable(price)) } }) {
                    Text(stringResource(R.string.action_use_current_price))
                }
            }
        }

        DecimalField(
            value = form.quantityText,
            onValueChange = { text -> viewModel.update { it.copy(quantityText = text) } },
            label = stringResource(R.string.field_quantity),
            isError = form.quantityError,
            supportingText = stringResource(R.string.field_quantity_hint),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.field_custom_alerts), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(
                        R.string.default_alerts_hint,
                        Formatters.quantity(settings.defaultAlertUpPct),
                        Formatters.quantity(settings.defaultAlertDownPct),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = form.customAlerts, onCheckedChange = { checked -> viewModel.update { it.copy(customAlerts = checked) } })
        }
        if (form.customAlerts) {
            Row {
                DecimalField(
                    value = form.alertUpText,
                    onValueChange = { text -> viewModel.update { it.copy(alertUpText = text) } },
                    label = stringResource(R.string.field_alert_up),
                    isError = form.alertUpError,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                DecimalField(
                    value = form.alertDownText,
                    onValueChange = { text -> viewModel.update { it.copy(alertDownText = text) } },
                    label = stringResource(R.string.field_alert_down),
                    isError = form.alertDownError,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                stringResource(R.string.alerts_zero_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(
            onClick = { viewModel.save(onDone) },
            enabled = form.isValid && !viewModel.saving,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.action_save)) }
    }
}
