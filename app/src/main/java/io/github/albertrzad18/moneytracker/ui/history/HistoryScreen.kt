package io.github.albertrzad18.moneytracker.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.albertrzad18.moneytracker.R
import io.github.albertrzad18.moneytracker.data.db.isOpen
import io.github.albertrzad18.moneytracker.data.repo.PortfolioRepository
import io.github.albertrzad18.moneytracker.data.repo.QuoteRepository
import io.github.albertrzad18.moneytracker.domain.CurrencyAmount
import io.github.albertrzad18.moneytracker.domain.RealizedLot
import io.github.albertrzad18.moneytracker.domain.SummaryCalculator
import io.github.albertrzad18.moneytracker.ui.components.EmptyState
import io.github.albertrzad18.moneytracker.ui.components.Lucide
import io.github.albertrzad18.moneytracker.ui.components.ScreenTitle
import io.github.albertrzad18.moneytracker.ui.components.pressScale
import io.github.albertrzad18.moneytracker.ui.summary.RealizedBlock
import io.github.albertrzad18.moneytracker.ui.theme.SmallCardShape
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
    /** Instruments that still have open purchases; only those can be opened. */
    val openInstrumentIds: Set<Long> = emptySet(),
)

@HiltViewModel
class HistoryViewModel @Inject constructor(portfolio: PortfolioRepository, quotes: QuoteRepository) : ViewModel() {
    val state: StateFlow<HistoryUiState> = combine(portfolio.realized, portfolio.lots, quotes.latestFxRates) { realized, lots, fx ->
        val summary = SummaryCalculator.summarize(emptyList(), realized, fx)
        val open = lots.filter { it.isOpen }.map { it.instrumentId }.toSet()
        HistoryUiState(true, realized, summary.realizedPerCurrency, summary.realizedPln, open)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}

@Composable
fun HistoryScreen(onOpenInstrument: (Long, String) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "title") { ScreenTitle(stringResource(R.string.nav_history)) }
        if (state.loaded && state.lots.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Lucide.History,
                    title = stringResource(R.string.history_empty_title),
                    body = stringResource(R.string.history_empty_body),
                    circleColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = 40.dp),
                )
            }
            return@LazyColumn
        }
        items(state.lots, key = { it.lot.id }) { realized ->
            val canOpen = realized.instrument.id in state.openInstrumentIds
            RealizedCard(
                realized = realized,
                onClick = if (canOpen) ({ onOpenInstrument(realized.instrument.id, realized.lot.currency) }) else null,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
            )
        }
        if (state.totals.isNotEmpty()) {
            item(key = "totals") {
                RealizedBlock(
                    rows = state.totals.map { it.currency to it.amount },
                    totalPln = state.totalPln?.takeIf { state.totals.size > 1 || state.totals.any { it.currency != "PLN" } },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun RealizedCard(realized: RealizedLot, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val lot = realized.lot
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val interaction = remember { MutableInteractionSource() }
    val clickable = if (onClick != null) {
        Modifier.pressScale(interaction, 0.985f).clickable(interaction, indication = null, onClick = onClick)
    } else {
        Modifier
    }
    Column(
        modifier
            .fillMaxWidth()
            .then(clickable)
            .clip(SmallCardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(realized.instrument.symbol, style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize * 18 / 19))
                Text(realized.instrument.name, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Formatters.pct(realized.pct), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = gainColor(realized.pct))
                realized.gain?.let {
                    Text(Formatters.signedMoney(it, lot.currency), style = MaterialTheme.typography.bodySmall, color = gainColor(realized.pct))
                }
            }
        }
        Text(
            "${Formatters.date(lot.buyDate)} → ${lot.sellDate?.let(Formatters::date).orEmpty()}",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            buildString {
                append('@').append(Formatters.price(lot.buyPrice))
                append(" → ").append(lot.sellPrice?.let { Formatters.money(it, lot.currency) }.orEmpty())
                lot.quantity?.let { append("  × ").append(Formatters.quantity(it)) }
            },
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
    }
}
