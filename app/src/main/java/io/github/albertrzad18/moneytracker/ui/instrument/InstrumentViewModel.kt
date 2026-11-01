package io.github.albertrzad18.moneytracker.ui.instrument

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.albertrzad18.moneytracker.data.db.Instrument
import io.github.albertrzad18.moneytracker.data.repo.HistoryRepository
import io.github.albertrzad18.moneytracker.data.repo.PortfolioRepository
import io.github.albertrzad18.moneytracker.domain.ChartPoint
import io.github.albertrzad18.moneytracker.domain.ChartRange
import io.github.albertrzad18.moneytracker.domain.Position
import io.github.albertrzad18.moneytracker.domain.RealizedLot
import io.github.albertrzad18.moneytracker.ui.InstrumentRoute
import io.github.albertrzad18.moneytracker.util.runCatchingCancellable
import io.github.albertrzad18.moneytracker.widget.WidgetUpdater
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

data class InstrumentUiState(
    val loaded: Boolean = false,
    /** The instrument was removed together with its last lot. */
    val deleted: Boolean = false,
    val instrument: Instrument? = null,
    val currency: String = "",
    val currencies: List<String> = emptyList(),
    val position: Position? = null,
    val realized: List<RealizedLot> = emptyList(),
)

sealed interface ChartState {
    data object Loading : ChartState
    data object Error : ChartState
    data class Loaded(val points: List<ChartPoint>) : ChartState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InstrumentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val portfolio: PortfolioRepository,
    private val history: HistoryRepository,
    private val widgetUpdater: WidgetUpdater,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<InstrumentRoute>()
    private val selectedCurrency = MutableStateFlow(route.currency)
    private val chartRequest = MutableStateFlow(ChartRequest(ChartRange.MONTH, 0))

    private data class ChartRequest(val range: ChartRange, val attempt: Int)

    val range: StateFlow<ChartRange> = chartRequest.map { it.range }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChartRange.MONTH)

    val state: StateFlow<InstrumentUiState> = combine(
        portfolio.observeInstrument(route.instrumentId),
        portfolio.positions,
        portfolio.realized,
        selectedCurrency,
    ) { instrument, positions, realized, requested ->
        if (instrument == null) return@combine InstrumentUiState(loaded = true, deleted = true)
        val ownPositions = positions.filter { it.instrument.id == instrument.id }
        val ownRealized = realized.filter { it.instrument.id == instrument.id }
        val currencies = (ownPositions.map { it.currency } + ownRealized.map { it.lot.currency }).distinct().sorted()
        val currency = requested.takeIf { it in currencies } ?: currencies.firstOrNull() ?: requested
        InstrumentUiState(
            loaded = true,
            instrument = instrument,
            currency = currency,
            currencies = currencies,
            position = ownPositions.find { it.currency == currency },
            realized = ownRealized.filter { it.lot.currency == currency },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InstrumentUiState())

    val chart: StateFlow<ChartState> = combine(
        state.map { it.instrument to it.currency }.distinctUntilChanged(),
        chartRequest,
    ) { target, request -> target to request }
        .flatMapLatest { (target, request) ->
            flow {
                val instrument = target.first ?: return@flow
                emit(ChartState.Loading)
                emit(
                    runCatchingCancellable { history.chart(instrument, target.second, request.range) }
                        .fold(onSuccess = { ChartState.Loaded(it) }, onFailure = { ChartState.Error }),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartState.Loading)

    fun selectCurrency(currency: String) {
        selectedCurrency.value = currency
    }

    fun selectRange(range: ChartRange) {
        chartRequest.value = ChartRequest(range, chartRequest.value.attempt)
    }

    fun retryChart() {
        chartRequest.value = chartRequest.value.copy(attempt = chartRequest.value.attempt + 1)
    }

    fun sell(lotId: Long, date: LocalDate, price: BigDecimal, quantity: BigDecimal?) = modify {
        portfolio.sellLot(lotId, date, price, quantity)
    }

    fun reopen(lotId: Long) = modify { portfolio.reopenLot(lotId) }

    fun delete(lotId: Long) = modify { portfolio.deleteLot(lotId) }

    private fun modify(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            widgetUpdater.update()
        }
    }
}
