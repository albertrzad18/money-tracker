package io.github.albertrzad18.moneytracker.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.albertrzad18.moneytracker.R
import io.github.albertrzad18.moneytracker.data.db.AssetKind
import io.github.albertrzad18.moneytracker.data.repo.HistoryRepository
import io.github.albertrzad18.moneytracker.data.repo.PortfolioRepository
import io.github.albertrzad18.moneytracker.data.repo.QuoteRepository
import io.github.albertrzad18.moneytracker.data.repo.TimelineResult
import io.github.albertrzad18.moneytracker.domain.AllocationSlice
import io.github.albertrzad18.moneytracker.domain.Calc
import io.github.albertrzad18.moneytracker.domain.PortfolioSummary
import io.github.albertrzad18.moneytracker.domain.SummaryCalculator
import io.github.albertrzad18.moneytracker.ui.components.ChartSeries
import io.github.albertrzad18.moneytracker.ui.components.DonutChart
import io.github.albertrzad18.moneytracker.ui.components.EmptyState
import io.github.albertrzad18.moneytracker.ui.components.GainText
import io.github.albertrzad18.moneytracker.ui.components.LabeledValue
import io.github.albertrzad18.moneytracker.ui.components.LegendItem
import io.github.albertrzad18.moneytracker.ui.components.LineChart
import io.github.albertrzad18.moneytracker.ui.lot.kindLabel
import io.github.albertrzad18.moneytracker.ui.theme.ChartColors
import io.github.albertrzad18.moneytracker.ui.theme.gainColor
import io.github.albertrzad18.moneytracker.util.Formatters
import io.github.albertrzad18.moneytracker.util.runCatchingCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

sealed interface TimelineState {
    data object Loading : TimelineState
    data object Error : TimelineState
    data class Loaded(val result: TimelineResult) : TimelineState
}

enum class TimelineRange(val months: Long?) { HALF_YEAR(6), YEAR(12), MAX(null) }

@HiltViewModel
class SummaryViewModel @Inject constructor(
    portfolio: PortfolioRepository,
    quotes: QuoteRepository,
    private val history: HistoryRepository,
) : ViewModel() {
    val summary: StateFlow<PortfolioSummary?> =
        combine(portfolio.positions, portfolio.realized, quotes.latestFxRates) { positions, realized, fx ->
            SummaryCalculator.summarize(positions, realized, fx)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _timeline = MutableStateFlow<TimelineState>(TimelineState.Loading)
    val timeline: StateFlow<TimelineState> = _timeline.asStateFlow()

    init {
        loadTimeline()
    }

    fun loadTimeline() {
        viewModelScope.launch {
            _timeline.value = TimelineState.Loading
            _timeline.value = runCatchingCancellable { history.portfolioTimeline() }
                .fold(onSuccess = { TimelineState.Loaded(it) }, onFailure = { TimelineState.Error })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(viewModel: SummaryViewModel = hiltViewModel()) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_summary)) }) }) { padding ->
        val current = summary ?: return@Scaffold
        if (current.isEmpty && current.lotsWithoutQuantity == 0) {
            EmptyState(
                icon = Icons.Outlined.PieChart,
                title = stringResource(R.string.summary_empty_title),
                body = stringResource(R.string.summary_empty_body),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TotalsCard(current)
            if (current.byInstrument.isNotEmpty()) AllocationCard(current)
            TimelineCard(timeline, onRetry = viewModel::loadTimeline)
            if (current.realizedPerCurrency.isNotEmpty()) RealizedCard(current)
            if (current.lotsWithoutQuantity > 0) {
                Text(
                    stringResource(R.string.summary_lots_without_quantity, current.lotsWithoutQuantity),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TotalsCard(summary: PortfolioSummary) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.summary_value), style = MaterialTheme.typography.titleSmall)
            val value = summary.valuePln
            if (value == null) {
                Text(stringResource(R.string.summary_no_values), style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("≈ " + Formatters.money(value, "PLN"), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(12.dp))
                    GainText(summary.pct, Modifier.padding(bottom = 4.dp), MaterialTheme.typography.titleMedium)
                }
                summary.costPln?.let { LabeledValue(stringResource(R.string.label_invested), "≈ " + Formatters.money(it, "PLN")) }
                summary.gainPln?.let {
                    LabeledValue(stringResource(R.string.label_gain), "≈ " + Formatters.signedMoney(it, "PLN"), valueColor = gainColor(it))
                }
            }
            if (summary.perCurrency.size > 1 || summary.perCurrency.any { it.currency != "PLN" }) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.summary_per_currency), style = MaterialTheme.typography.labelLarge)
                summary.perCurrency.forEach { total ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(total.currency, modifier = Modifier.width(56.dp), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            Formatters.money(total.value, total.currency),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            Formatters.signedMoney(total.gain, total.currency),
                            style = MaterialTheme.typography.bodySmall,
                            color = gainColor(total.gain),
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        GainText(total.pct)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val note = buildString {
                append(stringResource(R.string.summary_fx_note, summary.fxDate?.let(Formatters::date) ?: "—"))
                if (summary.missingFx.isNotEmpty()) {
                    append(' ').append(stringResource(R.string.summary_missing_fx, summary.missingFx.sorted().joinToString()))
                }
            }
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllocationCard(summary: PortfolioSummary) {
    var byKind by rememberSaveable { mutableStateOf(false) }
    val kindLabels = AssetKind.entries.associateWith { stringResource(kindLabel(it)) }
    val otherLabel = stringResource(R.string.allocation_other)
    val slots = ChartColors.slots()
    val slices = foldSlices(
        slices = if (byKind) summary.byKind.map { it.copy(label = kindLabels[it.kind] ?: it.label) } else summary.byInstrument,
        otherLabel = otherLabel,
    )
    val colors = slices.mapIndexed { i, slice -> if (slice.kind == null && slice.label == otherLabel) ChartColors.other else slots[i] }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.summary_allocation), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(false to R.string.allocation_by_instrument, true to R.string.allocation_by_kind).forEachIndexed { index, (kind, label) ->
                    SegmentedButton(
                        selected = byKind == kind,
                        onClick = { byKind = kind },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                    ) { Text(stringResource(label)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DonutChart(
                    values = slices.map { it.share },
                    colors = colors,
                    modifier = Modifier.size(132.dp),
                    description = slices.joinToString { "${it.label} ${Formatters.pct(BigDecimal(it.share * 100.0)).removePrefix("+")}" },
                )
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    slices.forEachIndexed { i, slice ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LegendItem(colors[i], slice.label, Modifier.weight(1f))
                            Text(
                                Formatters.pct(BigDecimal(slice.share * 100.0)).removePrefix("+"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Keeps the 7 largest slices and folds the rest into "Other", so no generated 9th hue is ever needed. */
private fun foldSlices(slices: List<AllocationSlice>, otherLabel: String): List<AllocationSlice> {
    if (slices.size <= ChartColors.MAX_SLOTS) return slices
    val kept = slices.take(ChartColors.MAX_SLOTS - 1)
    val rest = slices.drop(ChartColors.MAX_SLOTS - 1)
    return kept + AllocationSlice(
        label = otherLabel,
        kind = null,
        valuePln = Calc.sum(rest.map { it.valuePln }),
        share = rest.sumOf { it.share.toDouble() }.toFloat(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineCard(state: TimelineState, onRetry: () -> Unit) {
    var range by rememberSaveable { mutableStateOf(TimelineRange.MAX) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.summary_timeline), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TimelineRange.entries.forEach { option ->
                    FilterChip(
                        selected = option == range,
                        onClick = { range = option },
                        label = {
                            Text(
                                stringResource(
                                    when (option) {
                                        TimelineRange.HALF_YEAR -> R.string.range_half_year
                                        TimelineRange.YEAR -> R.string.range_year
                                        TimelineRange.MAX -> R.string.range_max
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                when (state) {
                    TimelineState.Loading -> CircularProgressIndicator()
                    TimelineState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.chart_error), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                    }
                    is TimelineState.Loaded -> TimelineChart(state.result, range)
                }
            }
            if (state is TimelineState.Loaded) {
                val slots = ChartColors.slots()
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendItem(slots[0], stringResource(R.string.series_value))
                    LegendItem(slots[1], stringResource(R.string.series_invested))
                }
                if (state.result.missing.isNotEmpty()) {
                    Text(
                        stringResource(R.string.timeline_missing, state.result.missing.joinToString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    stringResource(R.string.timeline_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun TimelineChart(result: TimelineResult, range: TimelineRange) {
    val from = range.months?.let { LocalDate.now().minusMonths(it) }
    val points = result.points.filter { from == null || it.date >= from }
    val slots = ChartColors.slots()
    fun x(date: LocalDate) = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val valueLabel = stringResource(R.string.series_value)
    val investedLabel = stringResource(R.string.series_invested)
    LineChart(
        series = listOf(
            ChartSeries(points.map { x(it.date) to it.value.toFloat() }, slots[0], valueLabel, fill = true),
            ChartSeries(points.map { x(it.date) to it.invested.toFloat() }, slots[1], investedLabel),
        ),
        formatX = { millis -> Formatters.shortDate(java.time.Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate()) },
        formatY = { Formatters.price(BigDecimal(it.toDouble())) + " zł" },
        description = stringResource(R.string.summary_timeline),
        modifier = Modifier.fillMaxSize().padding(top = 8.dp),
    )
}

@Composable
private fun RealizedCard(summary: PortfolioSummary) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.realized_result), style = MaterialTheme.typography.titleSmall)
            summary.realizedPerCurrency.forEach {
                LabeledValue(it.currency, Formatters.signedMoney(it.amount, it.currency), valueColor = gainColor(it.amount))
            }
            summary.realizedPln?.let {
                if (summary.realizedPerCurrency.any { amount -> amount.currency != "PLN" }) {
                    LabeledValue(stringResource(R.string.total_in_pln), "≈ " + Formatters.signedMoney(it, "PLN"), valueColor = gainColor(it))
                }
            }
        }
    }
}
