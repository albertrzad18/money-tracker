package io.github.albertrzad18.moneytracker.ui.portfolio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.albertrzad18.moneytracker.R
import io.github.albertrzad18.moneytracker.data.repo.PortfolioRepository
import io.github.albertrzad18.moneytracker.data.repo.SettingsRepository
import io.github.albertrzad18.moneytracker.domain.LotResult
import io.github.albertrzad18.moneytracker.domain.Position
import io.github.albertrzad18.moneytracker.ui.components.EmptyState
import io.github.albertrzad18.moneytracker.ui.components.GainText
import io.github.albertrzad18.moneytracker.ui.theme.gainColor
import io.github.albertrzad18.moneytracker.util.Formatters
import io.github.albertrzad18.moneytracker.work.Refresher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class PortfolioUiState(
    val loaded: Boolean = false,
    val positions: List<Position> = emptyList(),
    val lastRefreshAt: Instant? = null,
    val lastRefreshOk: Boolean = true,
    val refreshing: Boolean = false,
)

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    portfolio: PortfolioRepository,
    private val settings: SettingsRepository,
    private val refresher: Refresher,
    private val clock: Clock,
) : ViewModel() {
    private val manualRefresh = MutableStateFlow(false)

    val state: StateFlow<PortfolioUiState> =
        combine(portfolio.positions, settings.settings, manualRefresh) { positions, s, manual ->
            PortfolioUiState(
                loaded = true,
                positions = positions,
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
                refresher.refresh(force = true)
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.nav_portfolio))
                        state.lastRefreshAt?.let { last ->
                            Text(
                                text = stringResource(
                                    if (state.lastRefreshOk) R.string.updated_at else R.string.updated_at_failed,
                                    Formatters.dateTime(last),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.lastRefreshOk) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddLot,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_add_lot)) },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            when {
                !state.loaded -> Unit
                state.positions.isEmpty() -> EmptyState(
                    icon = Icons.AutoMirrored.Outlined.ShowChart,
                    title = stringResource(R.string.portfolio_empty_title),
                    body = stringResource(R.string.portfolio_empty_body),
                    actionLabel = stringResource(R.string.action_add_lot),
                    onAction = onAddLot,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.positions, key = { it.key }) { position ->
                        PositionCard(position, onClick = { onOpenPosition(position.instrument.id, position.currency) })
                    }
                }
            }
        }
    }
}

@Composable
fun PositionCard(position: Position, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(position.instrument.symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        position.instrument.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        position.quote?.let { Formatters.money(it.price, position.currency) } ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    position.quote?.dayChangePct?.let {
                        Text(
                            stringResource(R.string.day_change, Formatters.pct(it)),
                            style = MaterialTheme.typography.bodySmall,
                            color = gainColor(it),
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        if (position.isWeightedAverage) R.string.average_price_weighted else R.string.average_price_simple,
                        Formatters.money(position.averagePrice, position.currency),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                GainText(position.pct, style = MaterialTheme.typography.titleSmall)
            }
            if (position.value != null && position.gain != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.position_value, Formatters.money(position.value, position.currency)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        Formatters.signedMoney(position.gain, position.currency),
                        style = MaterialTheme.typography.bodySmall,
                        color = gainColor(position.gain),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            position.lots.forEach { LotLine(it, position.currency) }
        }
    }
}

/** One purchase: date, buy price (and quantity), result. */
@Composable
fun LotLine(result: LotResult, currency: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val lot = result.lot
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(Formatters.date(lot.buyDate), style = MaterialTheme.typography.bodyMedium)
            Text(
                buildString {
                    append('@').append(Formatters.money(lot.buyPrice, currency))
                    lot.quantity?.let { append("  × ").append(Formatters.quantity(it)) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            GainText(result.pct)
            result.gain?.let {
                Text(Formatters.signedMoney(it, currency), style = MaterialTheme.typography.bodySmall, color = gainColor(it))
            }
        }
        trailing()
    }
}
