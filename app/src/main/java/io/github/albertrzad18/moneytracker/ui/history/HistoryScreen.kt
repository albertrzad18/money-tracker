package io.github.albertrzad18.moneytracker.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.albertrzad18.moneytracker.R
import io.github.albertrzad18.moneytracker.data.repo.PortfolioRepository
import io.github.albertrzad18.moneytracker.data.repo.QuoteRepository
import io.github.albertrzad18.moneytracker.domain.CurrencyAmount
import io.github.albertrzad18.moneytracker.domain.RealizedLot
import io.github.albertrzad18.moneytracker.domain.SummaryCalculator
import io.github.albertrzad18.moneytracker.ui.components.EmptyState
import io.github.albertrzad18.moneytracker.ui.components.GainText
import io.github.albertrzad18.moneytracker.ui.components.LabeledValue
import io.github.albertrzad18.moneytracker.ui.theme.gainColor
import io.github.albertrzad18.moneytracker.util.Formatters
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import javax.inject.Inject

data class HistoryUiState(
    val loaded: Boolean = false,
    val lots: List<RealizedLot> = emptyList(),
    val totals: List<CurrencyAmount> = emptyList(),
    val totalPln: BigDecimal? = null,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(portfolio: PortfolioRepository, quotes: QuoteRepository) : ViewModel() {
    val state: StateFlow<HistoryUiState> = combine(portfolio.realized, quotes.latestFxRates) { realized, fx ->
        val summary = SummaryCalculator.summarize(emptyList(), realized, fx)
        HistoryUiState(true, realized, summary.realizedPerCurrency, summary.realizedPln)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onOpenInstrument: (Long, String) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_history)) }) }) { padding ->
        if (state.loaded && state.lots.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.History,
                title = stringResource(R.string.history_empty_title),
                body = stringResource(R.string.history_empty_body),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.totals.isNotEmpty()) {
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.realized_result), style = MaterialTheme.typography.titleSmall)
                            state.totals.forEach {
                                LabeledValue(it.currency, Formatters.signedMoney(it.amount, it.currency), valueColor = gainColor(it.amount))
                            }
                            if (state.totals.size > 1 || state.totals.any { it.currency != "PLN" }) {
                                state.totalPln?.let {
                                    LabeledValue(stringResource(R.string.total_in_pln), "≈ " + Formatters.signedMoney(it, "PLN"), valueColor = gainColor(it))
                                }
                            }
                        }
                    }
                }
            }
            items(state.lots, key = { it.lot.id }) { realized ->
                ElevatedCard(
                    onClick = { onOpenInstrument(realized.instrument.id, realized.lot.currency) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RealizedRow(realized)
                }
            }
        }
    }
}

@Composable
private fun RealizedRow(realized: RealizedLot) {
    val lot = realized.lot
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(realized.instrument.symbol, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(
                    R.string.history_line,
                    Formatters.date(lot.buyDate),
                    Formatters.price(lot.buyPrice),
                    lot.sellDate?.let(Formatters::date).orEmpty(),
                    lot.sellPrice?.let { Formatters.money(it, lot.currency) }.orEmpty(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            GainText(realized.pct, style = MaterialTheme.typography.titleSmall)
            realized.gain?.let {
                Text(Formatters.signedMoney(it, lot.currency), style = MaterialTheme.typography.bodySmall, color = gainColor(it))
            }
        }
    }
}
