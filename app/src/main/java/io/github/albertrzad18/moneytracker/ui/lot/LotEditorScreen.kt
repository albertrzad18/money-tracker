package io.github.albertrzad18.moneytracker.ui.lot

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.albertrzad18.moneytracker.R
import io.github.albertrzad18.moneytracker.data.db.AssetKind
import io.github.albertrzad18.moneytracker.domain.Calc
import io.github.albertrzad18.moneytracker.domain.InstrumentCandidate
import io.github.albertrzad18.moneytracker.ui.components.CircleIconButton
import io.github.albertrzad18.moneytracker.ui.components.DeleteLotDialog
import io.github.albertrzad18.moneytracker.ui.components.FieldHint
import io.github.albertrzad18.moneytracker.ui.components.FieldLabel
import io.github.albertrzad18.moneytracker.ui.components.LocalSnackbar
import io.github.albertrzad18.moneytracker.ui.components.Lucide
import io.github.albertrzad18.moneytracker.ui.components.PillButton
import io.github.albertrzad18.moneytracker.ui.components.PillDateField
import io.github.albertrzad18.moneytracker.ui.components.PillDecimalField
import io.github.albertrzad18.moneytracker.ui.components.PillTextField
import io.github.albertrzad18.moneytracker.ui.components.SegmentedPills
import io.github.albertrzad18.moneytracker.ui.components.SwitchRow
import io.github.albertrzad18.moneytracker.ui.theme.Durations
import io.github.albertrzad18.moneytracker.ui.theme.InsetShape
import io.github.albertrzad18.moneytracker.ui.theme.MoneyTheme
import io.github.albertrzad18.moneytracker.ui.theme.SmallCardShape
import io.github.albertrzad18.moneytracker.ui.theme.emphasized
import io.github.albertrzad18.moneytracker.ui.theme.gainColor
import io.github.albertrzad18.moneytracker.util.Formatters

@Composable
fun LotEditorScreen(onDone: () -> Unit, viewModel: LotEditorViewModel = hiltViewModel()) {
    val form = viewModel.form
    val canReturnToSearch = form != null && viewModel.hasSearch
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val snackbar = LocalSnackbar.current
    val resources = LocalResources.current
    BackHandler(enabled = canReturnToSearch) { viewModel.backToSearch() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(
                Lucide.ArrowLeft,
                stringResource(R.string.action_back),
                onClick = { if (canReturnToSearch) viewModel.backToSearch() else onDone() },
            )
            Text(
                stringResource(if (viewModel.isEdit) R.string.title_edit_lot else R.string.title_add_lot),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            )
            if (viewModel.isEdit) {
                CircleIconButton(Lucide.Trash, stringResource(R.string.action_delete), { confirmDelete = true }, tint = MoneyTheme.colors.loss)
            }
        }

        val slide = with(LocalDensity.current) { 48.dp.roundToPx() }
        AnimatedContent(
            targetState = form != null,
            transitionSpec = {
                val direction = if (targetState) 1 else -1
                (fadeIn(emphasized(Durations.TAB_FADE)) + slideInHorizontally(emphasized(Durations.SCREEN_PUSH)) { slide * direction }) togetherWith
                    (fadeOut(emphasized(Durations.TAB_FADE / 2)) + slideOutHorizontally(emphasized(Durations.SCREEN_PUSH)) { -slide * direction })
            },
            label = "editorStep",
        ) { showForm ->
            // The outgoing form keeps its last data while it slides away after "back to search".
            val held = remember { arrayOfNulls<LotForm>(1) }
            viewModel.form?.let { held[0] = it }
            val current = if (showForm) held[0] else null
            when {
                showForm && current != null -> FormStep(viewModel, current) {
                    snackbar.show(
                        if (viewModel.isEdit) resources.getString(R.string.snackbar_saved) else resources.getString(R.string.snackbar_added, current.symbol),
                    )
                    onDone()
                }
                viewModel.hasSearch -> SearchStep(viewModel)
                else -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, trackColor = Color.Transparent)
            }
        }
    }

    if (confirmDelete) {
        DeleteLotDialog(
            buyDate = form?.let { Formatters.date(it.date) }.orEmpty(),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                viewModel.delete {
                    snackbar.show(resources.getString(R.string.snackbar_deleted))
                    onDone()
                }
            },
        )
    }
}

@Composable
private fun SearchStep(viewModel: LotEditorViewModel) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val search = viewModel.search
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxSize()) {
        PillTextField(
            value = viewModel.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = stringResource(R.string.search_placeholder),
            accentBorder = true,
            imeAction = ImeAction.Search,
            leading = { Icon(Lucide.Search, contentDescription = stringResource(R.string.search_label), tint = muted, modifier = Modifier.size(18.dp)) },
            trailing = if (viewModel.query.isNotEmpty()) {
                { CircleIconButton(Lucide.X, stringResource(R.string.action_clear), { viewModel.onQueryChange("") }, size = 40.dp, iconSize = 18.dp, tint = muted) }
            } else {
                null
            },
            modifier = Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp),
            fieldModifier = Modifier.focusRequester(focusRequester),
        )
        Box(Modifier.fillMaxWidth().height(3.dp).padding(horizontal = 40.dp)) {
            if (search.loading || viewModel.resolving) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, trackColor = Color.Transparent)
            }
        }
        if (viewModel.resolveFailed) {
            Text(
                stringResource(R.string.resolve_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MoneyTheme.colors.loss,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        val results = search.results
        LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp), modifier = Modifier.navigationBarsPadding()) {
            when {
                viewModel.query.isBlank() -> item { Hint(stringResource(R.string.search_hint)) }
                results.failed -> item { Hint(stringResource(R.string.search_failed)) }
                search.searched && !search.loading && results.securities.isEmpty() && results.crypto.isEmpty() ->
                    item { Hint(stringResource(R.string.search_no_results)) }
            }
            if (results.securities.isNotEmpty()) {
                item { GroupHeader(stringResource(R.string.search_section_securities)) }
                items(results.securities, key = { "s:" + it.providerId }) { CandidateRow(it) { viewModel.select(it) } }
            }
            if (results.crypto.isNotEmpty()) {
                item { GroupHeader(stringResource(R.string.search_section_crypto)) }
                items(results.crypto, key = { "c:" + it.providerId }) { CandidateRow(it) { viewModel.select(it) } }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(start = 14.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun CandidateRow(candidate: InstrumentCandidate, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(InsetShape)
            .background(if (pressed) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(candidate.symbol, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(
                candidate.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            listOfNotNull(stringResource(kindLabel(candidate.kind)), candidate.exchange).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun kindLabel(kind: AssetKind): Int = when (kind) {
    AssetKind.STOCK -> R.string.kind_stock
    AssetKind.ETF -> R.string.kind_etf
    AssetKind.CRYPTO -> R.string.kind_crypto
}

@Composable
private fun FormStep(viewModel: LotEditorViewModel, form: LotForm, onSaved: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(SmallCardShape).background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(form.symbol, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(form.name, form.exchange).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            form.currentPrice?.let {
                Text(Formatters.money(it, form.currency), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
        }

        PillDateField(
            date = form.date,
            onDateChange = { date -> viewModel.update { it.copy(date = date) } },
            label = stringResource(R.string.field_buy_date),
        )

        if (form.currencyOptions.size > 1) {
            Column {
                FieldLabel(stringResource(R.string.field_currency))
                SegmentedPills(
                    options = form.currencyOptions,
                    selectedIndex = form.currencyOptions.indexOf(form.currency),
                    onSelect = { index -> viewModel.update { it.copy(currency = form.currencyOptions[index]) } },
                    background = MaterialTheme.colorScheme.surface,
                )
            }
        }

        Column {
            PillDecimalField(
                value = form.priceText,
                onValueChange = { text -> viewModel.update { it.copy(priceText = text) } },
                label = stringResource(R.string.field_buy_price, form.currency),
                isError = form.priceError,
            )
            form.currentPrice?.let { current ->
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, top = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.current_price_hint, Formatters.money(current, form.currency)),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                        modifier = Modifier.weight(1f),
                    )
                    val price = form.price
                    when {
                        form.priceText.isBlank() -> Text(
                            stringResource(R.string.action_use_current_price),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable { viewModel.update { it.copy(priceText = Formatters.editable(current)) } }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                        price != null -> {
                            val pct = Calc.changePct(price, current)
                            Text(
                                stringResource(R.string.price_preview_now, Formatters.pct(pct)),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = gainColor(pct),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }

        Column {
            PillDecimalField(
                value = form.quantityText,
                onValueChange = { text -> viewModel.update { it.copy(quantityText = text) } },
                label = stringResource(R.string.field_quantity),
                isError = form.quantityError,
            )
            FieldHint(stringResource(R.string.field_quantity_hint))
        }

        SwitchRow(
            title = stringResource(R.string.field_custom_alerts),
            description = stringResource(
                R.string.default_alerts_hint,
                Formatters.quantity(settings.defaultAlertUpPct),
                Formatters.quantity(settings.defaultAlertDownPct),
            ),
            checked = form.customAlerts,
            onCheckedChange = { checked -> viewModel.update { it.copy(customAlerts = checked) } },
            modifier = Modifier.padding(start = 6.dp, top = 4.dp),
        )
        AnimatedVisibility(
            visible = form.customAlerts,
            enter = fadeIn(emphasized(Durations.TAB_FADE)) + expandVertically(emphasized(Durations.TAB_SLIDE)),
            exit = fadeOut(emphasized(Durations.TAB_FADE / 2)) + shrinkVertically(emphasized(Durations.TAB_SLIDE)),
        ) {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillDecimalField(
                        value = form.alertUpText,
                        onValueChange = { text -> viewModel.update { it.copy(alertUpText = text) } },
                        label = stringResource(R.string.field_alert_up),
                        isError = form.alertUpError,
                        height = 48.dp,
                        modifier = Modifier.weight(1f),
                    )
                    PillDecimalField(
                        value = form.alertDownText,
                        onValueChange = { text -> viewModel.update { it.copy(alertDownText = text) } },
                        label = stringResource(R.string.field_alert_down),
                        isError = form.alertDownError,
                        height = 48.dp,
                        modifier = Modifier.weight(1f),
                    )
                }
                FieldHint(stringResource(R.string.alerts_zero_hint))
            }
        }

        Spacer(Modifier.height(4.dp))
        PillButton(
            text = stringResource(R.string.action_save),
            onClick = { viewModel.save(onSaved) },
            enabled = form.isValid && !viewModel.saving,
            height = 54.dp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
