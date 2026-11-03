package io.github.albertrzad18.moneytracker.ui.portfolio

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.albertrzad18.moneytracker.R
import io.github.albertrzad18.moneytracker.data.repo.PortfolioRepository
import io.github.albertrzad18.moneytracker.data.repo.QuoteRepository
import io.github.albertrzad18.moneytracker.data.repo.SettingsRepository
import io.github.albertrzad18.moneytracker.domain.LotResult
import io.github.albertrzad18.moneytracker.domain.PortfolioSummary
import io.github.albertrzad18.moneytracker.domain.Position
import io.github.albertrzad18.moneytracker.domain.SummaryCalculator
import io.github.albertrzad18.moneytracker.ui.components.CircleIconButton
import io.github.albertrzad18.moneytracker.ui.components.DeviationBar
import io.github.albertrzad18.moneytracker.ui.components.EmptyState
import io.github.albertrzad18.moneytracker.ui.components.LocalSnackbar
import io.github.albertrzad18.moneytracker.ui.components.Lucide
import io.github.albertrzad18.moneytracker.ui.components.ShareLegend
import io.github.albertrzad18.moneytracker.ui.components.StructureBar
import io.github.albertrzad18.moneytracker.ui.components.pressScale
import io.github.albertrzad18.moneytracker.ui.components.shareSlices
import io.github.albertrzad18.moneytracker.ui.theme.Durations
import io.github.albertrzad18.moneytracker.ui.theme.MoneyTheme
import io.github.albertrzad18.moneytracker.ui.theme.emphasized
import io.github.albertrzad18.moneytracker.ui.theme.gainColor
import io.github.albertrzad18.moneytracker.util.Formatters
import io.github.albertrzad18.moneytracker.work.Refresher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class PortfolioUiState(
    val loaded: Boolean = false,
    val positions: List<Position> = emptyList(),
    /** PLN totals and allocation for the hero; values are null without quantities or FX rates. */
    val summary: PortfolioSummary? = null,
    val lastRefreshAt: Instant? = null,
    val lastRefreshOk: Boolean = true,
    val refreshing: Boolean = false,
)

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    portfolio: PortfolioRepository,
    quotes: QuoteRepository,
    private val settings: SettingsRepository,
    private val refresher: Refresher,
    private val clock: Clock,
) : ViewModel() {
    private val manualRefresh = MutableStateFlow(false)
    private val refreshResults = Channel<Boolean>(Channel.CONFLATED)

    /** Outcome of each refresh the user started: true when every price was downloaded. */
    val refreshFinished: Flow<Boolean> = refreshResults.receiveAsFlow()

    val state: StateFlow<PortfolioUiState> =
        combine(portfolio.positions, quotes.latestFxRates, settings.settings, manualRefresh) { positions, fx, s, manual ->
            PortfolioUiState(
                loaded = true,
                positions = positions,
                summary = SummaryCalculator.summarize(positions, emptyList(), fx),
                lastRefreshAt = s.lastRefreshAt,
                lastRefreshOk = s.lastRefreshOk,
                refreshing = manual || s.isRefreshing(clock.instant()),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PortfolioUiState())

    init {
        viewModelScope.launch {
            val last = settings.current().lastRefreshAt
            if (last == null || Duration.between(last, clock.instant()) > AUTO_REFRESH_AFTER) refresher.refresh(force = false)
        }
    }

    fun refresh() {
        if (manualRefresh.value) return
        viewModelScope.launch {
            manualRefresh.value = true
            try {
                refreshResults.trySend(refresher.refresh(force = true))
            } finally {
                manualRefresh.value = false
            }
        }
    }

    private companion object {
        val AUTO_REFRESH_AFTER: Duration = Duration.ofMinutes(10)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    onAddLot: () -> Unit,
    onOpenPosition: (Long, String) -> Unit,
    viewModel: PortfolioViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.refreshFinished.collect { ok ->
            snackbar.show(resources.getString(if (ok) R.string.snackbar_refreshed else R.string.snackbar_refresh_failed))
        }
    }

    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
            item(key = "top") { TopRow(state, onRefresh = viewModel::refresh) }
            when {
                !state.loaded -> Unit
                state.positions.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        icon = Lucide.ChartLine,
                        title = stringResource(R.string.portfolio_empty_title),
                        body = stringResource(R.string.portfolio_empty_body),
                        actionLabel = stringResource(R.string.action_add_lot),
                        actionIcon = Lucide.Plus,
                        onAction = onAddLot,
                        modifier = Modifier.padding(top = 48.dp),
                    )
                }
                else -> {
                    state.summary?.takeIf { it.valuePln != null }?.let { summary -> item(key = "hero") { Hero(summary) } }
                    item(key = "gap") { Spacer(Modifier.height(30.dp)) }
                    items(state.positions, key = { it.key }) { position ->
                        PositionItem(
                            position = position,
                            onClick = { onOpenPosition(position.instrument.id, position.currency) },
                            modifier = Modifier.animateItem().padding(start = 20.dp, end = 20.dp, bottom = 30.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopRow(state: PortfolioUiState, onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, top = 18.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.nav_portfolio), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        state.lastRefreshAt?.let { last ->
            Text(
                Formatters.dateTime(last),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.lastRefreshOk) MaterialTheme.colorScheme.onSurfaceVariant else MoneyTheme.colors.loss,
            )
        }
        Spacer(Modifier.width(4.dp))
        RefreshButton(refreshing = state.refreshing, onClick = onRefresh)
    }
}

/** 40 dp round button whose icon turns 360° in 1.1 s, and keeps turning while a refresh runs. */
@Composable
fun RefreshButton(refreshing: Boolean, onClick: () -> Unit) {
    val rotation = remember { Animatable(0f) }
    val running by rememberUpdatedState(refreshing)
    LaunchedEffect(Unit) {
        // Whole turns only: a turn that started always finishes, even if the refresh ends sooner.
        snapshotFlow { running }.collect { active ->
            while (active && running) {
                rotation.animateTo(rotation.targetValue + 360f, emphasized(Durations.REFRESH_TURN))
            }
        }
    }
    CircleIconButton(
        icon = Lucide.RefreshCw,
        contentDescription = stringResource(R.string.action_refresh),
        onClick = onClick,
        size = 40.dp,
        iconSize = 18.dp,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        iconModifier = Modifier.graphicsLayer { rotationZ = rotation.value },
    )
}

@Composable
private fun Hero(summary: PortfolioSummary) {
    val colors = MoneyTheme.colors
    val slices = shareSlices(summary.byInstrument, colors.shares, stringResource(R.string.allocation_other))
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, top = 22.dp, end = 20.dp)) {
        Text(stringResource(R.string.summary_value), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            Formatters.money(summary.valuePln!!, "PLN"),
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(Formatters.pct(summary.pct), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = gainColor(summary.pct))
            summary.gainPln?.let {
                Text(
                    stringResource(R.string.gain_since_purchase, Formatters.signedMoney(it, "PLN")),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (slices.isNotEmpty()) {
            StructureBar(slices, Modifier.padding(top = 22.dp))
            ShareLegend(slices)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PositionItem(position: Position, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.985f)
            .clickable(interaction, indication = null, onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(position.instrument.symbol, style = MaterialTheme.typography.titleMedium)
                Text(
                    position.instrument.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(Formatters.pct(position.pct), style = MaterialTheme.typography.titleLarge, color = gainColor(position.pct))
        }
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val small = MaterialTheme.typography.bodySmall
            position.quote?.let { quote ->
                Text(Formatters.money(quote.price, position.currency), style = small, fontWeight = FontWeight.SemiBold)
                quote.dayChangePct?.let {
                    Text(stringResource(R.string.day_change, Formatters.pct(it)), style = small, color = gainColor(it))
                }
                Text("·", style = small, color = muted)
            }
            Text(stringResource(R.string.average_short, Formatters.money(position.averagePrice, position.currency)), style = small, color = muted)
        }
        Column(Modifier.padding(top = 10.dp)) {
            position.lots.forEach { LotBarRow(it) }
        }
    }
}

/** "dd.MM.yy  @price", the deviation bar and the result. */
@Composable
private fun LotBarRow(result: LotResult) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            buildAnnotatedString {
                append(Formatters.shortDate(result.lot.buyDate))
                withStyle(SpanStyle(color = muted)) { append("  @" + Formatters.price(result.lot.buyPrice)) }
            },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.width(132.dp),
        )
        Spacer(Modifier.width(12.dp))
        DeviationBar(result.pct, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            Formatters.pct(result.pct),
            style = MaterialTheme.typography.labelLarge,
            color = gainColor(result.pct),
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(54.dp),
        )
    }
}
