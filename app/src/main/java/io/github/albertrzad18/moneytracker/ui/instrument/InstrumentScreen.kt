package io.github.albertrzad18.moneytracker.ui.instrument

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.albertrzad18.moneytracker.R
import io.github.albertrzad18.moneytracker.data.db.AssetKind
import io.github.albertrzad18.moneytracker.data.db.Lot
import io.github.albertrzad18.moneytracker.domain.Calc
import io.github.albertrzad18.moneytracker.domain.ChartRange
import io.github.albertrzad18.moneytracker.domain.Position
import io.github.albertrzad18.moneytracker.domain.RealizedLot
import io.github.albertrzad18.moneytracker.ui.components.ChartReferenceLine
import io.github.albertrzad18.moneytracker.ui.components.ChartSeries
import io.github.albertrzad18.moneytracker.ui.components.DateField
import io.github.albertrzad18.moneytracker.ui.components.DecimalField
import io.github.albertrzad18.moneytracker.ui.components.GainText
import io.github.albertrzad18.moneytracker.ui.components.LabeledValue
import io.github.albertrzad18.moneytracker.ui.components.LineChart
import io.github.albertrzad18.moneytracker.ui.components.SectionTitle
import io.github.albertrzad18.moneytracker.ui.portfolio.LotLine
import io.github.albertrzad18.moneytracker.ui.theme.ChartColors
import io.github.albertrzad18.moneytracker.ui.theme.gainColor
import io.github.albertrzad18.moneytracker.util.Formatters
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstrumentScreen(
    onBack: () -> Unit,
    onAddLot: (Long, String) -> Unit,
    onEditLot: (Long) -> Unit,
    viewModel: InstrumentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chart by viewModel.chart.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()
    var selling by remember { mutableStateOf<Lot?>(null) }
    var deleting by remember { mutableStateOf<Lot?>(null) }

    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    val instrument = state.instrument

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(instrument?.symbol.orEmpty())
                        Text(
                            instrument?.name.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (instrument != null) {
                        IconButton(onClick = { onAddLot(instrument.id, state.currency) }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_lot))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (instrument == null) return@Scaffold
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.currencies.size > 1) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    state.currencies.forEachIndexed { index, currency ->
                        SegmentedButton(
                            selected = currency == state.currency,
                            onClick = { viewModel.selectCurrency(currency) },
                            shape = SegmentedButtonDefaults.itemShape(index, state.currencies.size),
                        ) { Text(currency) }
                    }
                }
            }

            PriceHeader(state.position, state.currency)

            ChartCard(
                chart = chart,
                range = range,
                position = state.position,
                currency = state.currency,
                isCrypto = instrument.kind == AssetKind.CRYPTO,
                onRange = viewModel::selectRange,
                onRetry = viewModel::retryChart,
            )

            state.position?.let { position ->
                StatsCard(position)
                SectionTitle(stringResource(R.string.section_open_lots))
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        position.lots.forEachIndexed { index, result ->
                            if (index > 0) HorizontalDivider()
                            LotLine(result, position.currency) {
                                LotMenu(
                                    items = listOf(
                                        R.string.action_edit to { onEditLot(result.lot.id) },
                                        R.string.action_sell to { selling = result.lot },
                                        R.string.action_delete to { deleting = result.lot },
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            if (state.realized.isNotEmpty()) {
                SectionTitle(stringResource(R.string.section_sold_lots))
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        state.realized.forEachIndexed { index, realized ->
                            if (index > 0) HorizontalDivider()
                            RealizedLine(realized) {
                                LotMenu(
                                    items = listOf(
                                        R.string.action_reopen to { viewModel.reopen(realized.lot.id) },
                                        R.string.action_delete to { deleting = realized.lot },
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selling?.let { lot ->
        SellDialog(
            lot = lot,
            currentPrice = state.position?.quote?.price,
            onDismiss = { selling = null },
            onConfirm = { date, price, quantity ->
                viewModel.sell(lot.id, date, price, quantity)
                selling = null
            },
        )
    }
    deleting?.let { lot ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_lot_title)) },
            text = { Text(stringResource(R.string.delete_lot_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(lot.id)
                    deleting = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun PriceHeader(position: Position?, currency: String) {
    val quote = position?.quote ?: return
    Row(verticalAlignment = Alignment.Bottom) {
        Text(Formatters.money(quote.price, currency), style = MaterialTheme.typography.headlineMedium)
        quote.dayChangePct?.let {
            Text(
                stringResource(R.string.day_change, Formatters.pct(it)),
                style = MaterialTheme.typography.bodyMedium,
                color = gainColor(it),
                modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun ChartCard(
    chart: ChartState,
    range: ChartRange,
    position: Position?,
    currency: String,
    isCrypto: Boolean,
    onRange: (ChartRange) -> Unit,
    onRetry: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChartRange.entries.forEach { option ->
                    FilterChip(
                        selected = option == range,
                        onClick = { onRange(option) },
                        label = { Text(stringResource(rangeLabel(option))) },
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                when (chart) {
                    ChartState.Loading -> CircularProgressIndicator()
                    ChartState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.chart_error), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                    }
                    is ChartState.Loaded -> {
                        val lineColor = ChartColors.slots().first()
                        val references = position?.lots.orEmpty().map { result ->
                            ChartReferenceLine(
                                value = result.lot.buyPrice.toFloat(),
                                label = "${Formatters.shortDate(result.lot.buyDate)} @${Formatters.price(result.lot.buyPrice)}",
                                color = gainColor(result.pct).copy(alpha = 0.85f),
                            )
                        }
                        val zone = ZoneId.systemDefault()
                        LineChart(
                            series = listOf(
                                ChartSeries(
                                    points = chart.points.map { it.time.toEpochMilli() to it.price.toFloat() },
                                    color = lineColor,
                                    fill = true,
                                ),
                            ),
                            referenceLines = references,
                            formatX = { millis ->
                                val date = Instant.ofEpochMilli(millis).atZone(zone)
                                if (range == ChartRange.WEEK) Formatters.dateTime(date.toInstant()) else Formatters.shortDate(date.toLocalDate())
                            },
                            formatY = { Formatters.price(BigDecimal(it.toDouble())) },
                            description = stringResource(R.string.chart_price_description, currency),
                            modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                        )
                    }
                }
            }
            if (isCrypto && (range == ChartRange.MAX)) {
                Text(
                    stringResource(R.string.chart_crypto_limit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!position?.lots.isNullOrEmpty()) {
                Text(
                    stringResource(R.string.chart_buy_lines_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun rangeLabel(range: ChartRange): Int = when (range) {
    ChartRange.WEEK -> R.string.range_week
    ChartRange.MONTH -> R.string.range_month
    ChartRange.HALF_YEAR -> R.string.range_half_year
    ChartRange.YEAR -> R.string.range_year
    ChartRange.MAX -> R.string.range_max
}

@Composable
private fun StatsCard(position: Position) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.total_result), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                GainText(position.pct, style = MaterialTheme.typography.titleMedium)
            }
            LabeledValue(
                label = stringResource(if (position.isWeightedAverage) R.string.label_average_weighted else R.string.label_average_simple),
                value = Formatters.money(position.averagePrice, position.currency),
            )
            LabeledValue(stringResource(R.string.label_lots_count), position.lots.size.toString())
            position.quantity?.let { LabeledValue(stringResource(R.string.label_quantity), Formatters.quantity(it)) }
            position.cost?.let { LabeledValue(stringResource(R.string.label_invested), Formatters.money(it, position.currency)) }
            position.value?.let { LabeledValue(stringResource(R.string.label_value), Formatters.money(it, position.currency)) }
            position.gain?.let {
                LabeledValue(stringResource(R.string.label_gain), Formatters.signedMoney(it, position.currency), valueColor = gainColor(it))
            }
        }
    }
}

@Composable
private fun RealizedLine(realized: RealizedLot, trailing: @Composable () -> Unit) {
    val lot = realized.lot
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "${Formatters.date(lot.buyDate)} → ${lot.sellDate?.let(Formatters::date).orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                buildString {
                    append('@').append(Formatters.price(lot.buyPrice))
                    append(" → ").append(lot.sellPrice?.let { Formatters.money(it, lot.currency) }.orEmpty())
                    lot.quantity?.let { append("  × ").append(Formatters.quantity(it)) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            GainText(realized.pct)
            realized.gain?.let {
                Text(Formatters.signedMoney(it, lot.currency), style = MaterialTheme.typography.bodySmall, color = gainColor(it))
            }
        }
        trailing()
    }
}

@Composable
private fun LotMenu(items: List<Pair<Int, () -> Unit>>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
        }
    }
}

@Composable
private fun SellDialog(
    lot: Lot,
    currentPrice: BigDecimal?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, BigDecimal, BigDecimal?) -> Unit,
) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var priceText by rememberSaveable { mutableStateOf(currentPrice?.let { Formatters.editable(it) }.orEmpty()) }
    var quantityText by rememberSaveable { mutableStateOf(lot.quantity?.let { Formatters.editable(it) }.orEmpty()) }
    val price = Formatters.parseDecimal(priceText)?.takeIf { it.signum() > 0 }
    val owned = lot.quantity
    val quantity = Formatters.parseDecimal(quantityText)?.takeIf { it.signum() > 0 && (owned == null || it <= owned) }
    val quantityValid = owned == null || quantity != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sell_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.sell_lot_summary, Formatters.date(lot.buyDate), Formatters.money(lot.buyPrice, lot.currency)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                DateField(
                    date = date,
                    onDateChange = { date = it },
                    label = stringResource(R.string.field_sell_date),
                    minDate = lot.buyDate,
                )
                DecimalField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = stringResource(R.string.field_sell_price, lot.currency),
                    isError = priceText.isNotBlank() && price == null,
                )
                if (owned != null) {
                    DecimalField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = stringResource(R.string.field_sell_quantity, Formatters.quantity(owned)),
                        isError = !quantityValid,
                    )
                }
                price?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.sell_result), modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        GainText(Calc.changePct(lot.buyPrice, it))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = price != null && quantityValid,
                onClick = { onConfirm(date, price!!, if (owned != null) quantity else null) },
            ) { Text(stringResource(R.string.action_sell)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
