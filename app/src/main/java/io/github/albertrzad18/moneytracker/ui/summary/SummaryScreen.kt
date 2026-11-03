package io.github.albertrzad18.moneytracker.ui.summary

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import io.github.albertrzad18.moneytracker.domain.PortfolioSummary
import io.github.albertrzad18.moneytracker.domain.SummaryCalculator
import io.github.albertrzad18.moneytracker.ui.components.AreaChart
import io.github.albertrzad18.moneytracker.ui.components.DonutChart
import io.github.albertrzad18.moneytracker.ui.components.EmptyState
import io.github.albertrzad18.moneytracker.ui.components.LabeledValue
import io.github.albertrzad18.moneytracker.ui.components.LegendDot
import io.github.albertrzad18.moneytracker.ui.components.Lucide
import io.github.albertrzad18.moneytracker.ui.components.MoneyCard
import io.github.albertrzad18.moneytracker.ui.components.PillButton
import io.github.albertrzad18.moneytracker.ui.components.PillButtonStyle
import io.github.albertrzad18.moneytracker.ui.components.RangeChips
import io.github.albertrzad18.moneytracker.ui.components.ScreenTitle
import io.github.albertrzad18.moneytracker.ui.components.SegmentedPills
import io.github.albertrzad18.moneytracker.ui.components.shareSlices
import io.github.albertrzad18.moneytracker.ui.theme.MoneyTheme
import io.github.albertrzad18.moneytracker.ui.theme.Pill
import io.github.albertrzad18.moneytracker.ui.theme.amountColor
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
import javax.inject.Inject

sealed interface TimelineState {
    data object Loading : TimelineState
    data object Error : TimelineState
    data class Loaded(val result: TimelineResult) : TimelineState
}

enum class TimelineRange(val months: Long?) { HALF_YEAR(6), YEAR(12), MAX(null) }

enum class AllocationBy { INSTRUMENT, KIND }

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

    private val _allocationBy = MutableStateFlow(AllocationBy.INSTRUMENT)
    val allocationBy: StateFlow<AllocationBy> = _allocationBy.asStateFlow()

    private val _timelineRange = MutableStateFlow(TimelineRange.MAX)
    val timelineRange: StateFlow<TimelineRange> = _timelineRange.asStateFlow()

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

    fun setAllocationBy(by: AllocationBy) {
        _allocationBy.value = by
    }

    fun setTimelineRange(range: TimelineRange) {
        _timelineRange.value = range
    }
}

@Composable
fun SummaryScreen(viewModel: SummaryViewModel = hiltViewModel()) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    val allocationBy by viewModel.allocationBy.collectAsStateWithLifecycle()
    val timelineRange by viewModel.timelineRange.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle(stringResource(R.string.nav_summary))
        val current = summary ?: return@Column
        if (current.isEmpty && current.lotsWithoutQuantity == 0) {
            EmptyState(
                icon = Lucide.ChartPie,
                title = stringResource(R.string.summary_empty_title),
                body = stringResource(R.string.summary_empty_body),
                modifier = Modifier.padding(top = 40.dp),
            )
            return@Column
        }
        Column(
            Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TotalsCard(current)
            if (current.byInstrument.isNotEmpty()) AllocationCard(current, allocationBy, viewModel::setAllocationBy)
            TimelineCard(timeline, timelineRange, viewModel::setTimelineRange, onRetry = viewModel::loadTimeline)
            if (current.realizedPerCurrency.isNotEmpty()) RealizedBlock(current)
            if (current.lotsWithoutQuantity > 0) {
                Text(
                    stringResource(R.string.summary_lots_without_quantity, current.lotsWithoutQuantity),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
}

@Composable
private fun TotalsCard(summary: PortfolioSummary) {
    MoneyCard {
        CardTitle(stringResource(R.string.summary_value))
        val value = summary.valuePln
        if (value == null) {
            Text(stringResource(R.string.summary_no_values), style = MaterialTheme.typography.bodyMedium)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("≈ " + Formatters.money(value, "PLN"), style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.width(10.dp))
                Text(
                    Formatters.pct(summary.pct),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = gainColor(summary.pct),
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            summary.costPln?.let { LabeledValue(stringResource(R.string.label_invested), "≈ " + Formatters.money(it, "PLN")) }
            summary.gainPln?.let {
                LabeledValue(stringResource(R.string.label_gain), "≈ " + Formatters.signedMoney(it, "PLN"), valueColor = amountColor(it))
            }
        }
        if (summary.perCurrency.size > 1 || summary.perCurrency.any { it.currency != "PLN" }) {
            Text(
                stringResource(R.string.summary_per_currency),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                summary.perCurrency.forEach { total ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .clip(Pill)
                            .background(MoneyTheme.colors.inset)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(total.currency, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                        Text(Formatters.money(total.value, total.currency), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(
                            Formatters.signedMoney(total.gain, total.currency),
                            style = MaterialTheme.typography.bodySmall,
                            color = amountColor(total.gain),
                            modifier = Modifier.padding(end = 10.dp),
                            maxLines = 1,
                        )
                        Text(Formatters.pct(total.pct), style = MaterialTheme.typography.labelLarge, color = gainColor(total.pct))
                    }
                }
            }
        }
        val note = buildString {
            append(stringResource(R.string.summary_fx_note, summary.fxDate?.let(Formatters::date) ?: "—"))
            if (summary.missingFx.isNotEmpty()) {
                append(' ').append(stringResource(R.string.summary_missing_fx, summary.missingFx.sorted().joinToString()))
            }
        }
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun AllocationCard(summary: PortfolioSummary, by: AllocationBy, onBy: (AllocationBy) -> Unit) {
    val kindLabels = mapOf(
        AssetKind.STOCK to stringResource(R.string.kind_stock_plural),
        AssetKind.ETF to stringResource(R.string.kind_etf_plural),
        AssetKind.CRYPTO to stringResource(R.string.kind_crypto_plural),
    )
    val source = when (by) {
        AllocationBy.INSTRUMENT -> summary.byInstrument
        AllocationBy.KIND -> summary.byKind.map { it.copy(label = kindLabels[it.kind] ?: it.label) }
    }
    val slices = shareSlices(source, MoneyTheme.colors.shares, stringResource(R.string.allocation_other))

    MoneyCard {
        CardTitle(stringResource(R.string.summary_allocation))
        SegmentedPills(
            options = listOf(stringResource(R.string.allocation_by_instrument), stringResource(R.string.allocation_by_kind)),
            selectedIndex = by.ordinal,
            onSelect = { onBy(AllocationBy.entries[it]) },
        )
        Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                values = slices.map { it.share },
                colors = slices.map { it.color },
                modifier = Modifier.size(124.dp),
                description = slices.joinToString { "${it.label} ${it.shareText}" },
            )
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                slices.forEach { slice ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LegendDot(slice.color, slice.label, Modifier.weight(1f), dotSize = 10.dp, fontSize = 13)
                        Text(slice.shareText, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineCard(state: TimelineState, range: TimelineRange, onRange: (TimelineRange) -> Unit, onRetry: () -> Unit) {
    val ranges = TimelineRange.entries
    MoneyCard {
        CardTitle(stringResource(R.string.summary_timeline))
        RangeChips(
            options = ranges.map {
                stringResource(
                    when (it) {
                        TimelineRange.HALF_YEAR -> R.string.range_half_year
                        TimelineRange.YEAR -> R.string.range_year
                        TimelineRange.MAX -> R.string.range_max
                    },
                )
            },
            selectedIndex = range.ordinal,
            onSelect = { onRange(ranges[it]) },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        )
        Box(Modifier.fillMaxWidth().padding(top = 34.dp, bottom = 10.dp).height(170.dp), contentAlignment = Alignment.Center) {
            when (state) {
                TimelineState.Loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                TimelineState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.chart_error), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    PillButton(stringResource(R.string.action_retry), onRetry, style = PillButtonStyle.Outlined, height = 40.dp)
                }
                is TimelineState.Loaded -> TimelineChart(state.result, range)
            }
        }
        if (state is TimelineState.Loaded) {
            val colors = MoneyTheme.colors
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 6.dp)) {
                LegendDot(colors.chartLine, stringResource(R.string.series_value))
                LegendDot(colors.chartSecondary, stringResource(R.string.series_invested))
            }
            if (state.result.missing.isNotEmpty()) {
                Text(
                    stringResource(R.string.timeline_missing, state.result.missing.joinToString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Text(
                stringResource(R.string.timeline_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun TimelineChart(result: TimelineResult, range: TimelineRange) {
    val from = range.months?.let { LocalDate.now().minusMonths(it) }
    val points = result.points.filter { from == null || it.date >= from }
    if (points.size < 2) {
        Text(stringResource(R.string.chart_no_data), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    AreaChart(
        values = points.map { it.value.toFloat() },
        secondary = points.map { it.invested.toFloat() },
        floorAtZero = true,
        formatValue = { Formatters.money(BigDecimal.valueOf(it.toDouble()).setScale(0, java.math.RoundingMode.HALF_UP), "PLN") },
        tooltip = { i -> "≈ ${Formatters.money(points[i].value, "PLN")} · ${Formatters.date(points[i].date)}" },
        backgroundColor = MaterialTheme.colorScheme.surface,
        description = stringResource(R.string.summary_timeline),
        modifier = Modifier.fillMaxSize(),
    )
}

/** Realized result on the green block: per currency and the PLN total. */
@Composable
fun RealizedBlock(summary: PortfolioSummary) {
    RealizedBlock(
        rows = summary.realizedPerCurrency.map { it.currency to it.amount },
        totalPln = summary.realizedPln?.takeIf { summary.realizedPerCurrency.any { amount -> amount.currency != "PLN" } },
    )
}

@Composable
fun RealizedBlock(rows: List<Pair<String, BigDecimal>>, totalPln: BigDecimal?, modifier: Modifier = Modifier) {
    val colors = MoneyTheme.colors
    MoneyCard(modifier, color = colors.soft) {
        Text(stringResource(R.string.realized_result), style = MaterialTheme.typography.titleMedium, color = colors.onSoft, modifier = Modifier.padding(bottom = 10.dp))
        rows.forEach { (currency, amount) -> RealizedRow(currency, Formatters.signedMoney(amount, currency)) }
        totalPln?.let { RealizedRow(stringResource(R.string.total_in_pln), "≈ " + Formatters.signedMoney(it, "PLN")) }
    }
}

@Composable
private fun RealizedRow(label: String, value: String) {
    val ink = MoneyTheme.colors.onSoft
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = ink)
    }
}
