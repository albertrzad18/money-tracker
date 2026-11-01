package io.github.albertrzad18.moneytracker.ui.lot

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.albertrzad18.moneytracker.data.db.AssetKind
import io.github.albertrzad18.moneytracker.data.db.Instrument
import io.github.albertrzad18.moneytracker.data.repo.AppSettings
import io.github.albertrzad18.moneytracker.data.repo.PortfolioRepository
import io.github.albertrzad18.moneytracker.data.repo.QuoteRepository
import io.github.albertrzad18.moneytracker.data.repo.SearchResults
import io.github.albertrzad18.moneytracker.data.repo.SettingsRepository
import io.github.albertrzad18.moneytracker.di.ApplicationScope
import io.github.albertrzad18.moneytracker.domain.CRYPTO_CURRENCIES
import io.github.albertrzad18.moneytracker.domain.InstrumentCandidate
import io.github.albertrzad18.moneytracker.domain.LotInput
import io.github.albertrzad18.moneytracker.domain.ResolvedInstrument
import io.github.albertrzad18.moneytracker.ui.LotEditorRoute
import io.github.albertrzad18.moneytracker.util.Formatters
import io.github.albertrzad18.moneytracker.util.runCatchingCancellable
import io.github.albertrzad18.moneytracker.widget.WidgetUpdater
import io.github.albertrzad18.moneytracker.work.Refresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class SearchState(
    val results: SearchResults = SearchResults(),
    val loading: Boolean = false,
    val searched: Boolean = false,
)

data class LotForm(
    val symbol: String,
    val name: String,
    val exchange: String?,
    val kind: AssetKind,
    /** Set for a new instrument coming from the search. */
    val resolved: ResolvedInstrument?,
    /** Set when the instrument is already in the portfolio. */
    val instrumentId: Long?,
    val currentPrices: Map<String, BigDecimal>,
    val currencyOptions: List<String>,
    val currency: String,
    val date: LocalDate,
    val priceText: String = "",
    val quantityText: String = "",
    val customAlerts: Boolean = false,
    val alertUpText: String = "",
    val alertDownText: String = "",
) {
    val price: BigDecimal? get() = Formatters.parseDecimal(priceText)?.takeIf { it.signum() > 0 }
    val quantity: BigDecimal? get() = Formatters.parseDecimal(quantityText)?.takeIf { it.signum() > 0 }
    val priceError: Boolean get() = priceText.isNotBlank() && price == null
    val quantityError: Boolean get() = quantityText.isNotBlank() && quantity == null
    private val alertUp: BigDecimal? get() = Formatters.parseDecimal(alertUpText)?.takeIf { it.signum() >= 0 }
    private val alertDown: BigDecimal? get() = Formatters.parseDecimal(alertDownText)?.takeIf { it.signum() >= 0 }
    val alertUpError: Boolean get() = customAlerts && alertUpText.isNotBlank() && alertUp == null
    val alertDownError: Boolean get() = customAlerts && alertDownText.isNotBlank() && alertDown == null
    val currentPrice: BigDecimal? get() = currentPrices[currency]
    val isValid: Boolean get() = price != null && !quantityError && !alertUpError && !alertDownError

    fun toInput(): LotInput? {
        if (!isValid) return null
        return LotInput(
            buyDate = date,
            buyPrice = price!!,
            quantity = quantity,
            currency = currency,
            // An empty custom field falls back to the global default.
            alertUpPct = if (customAlerts) alertUp else null,
            alertDownPct = if (customAlerts) alertDown else null,
        )
    }
}

@OptIn(FlowPreview::class)
@HiltViewModel
class LotEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val portfolio: PortfolioRepository,
    private val quotes: QuoteRepository,
    settings: SettingsRepository,
    private val refresher: Refresher,
    private val widgetUpdater: WidgetUpdater,
    @ApplicationScope private val appScope: CoroutineScope,
    private val clock: Clock,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<LotEditorRoute>()
    val isEdit: Boolean = route.lotId != 0L

    /** False when the screen was opened for a known instrument, so there is no search to go back to. */
    val hasSearch: Boolean = !isEdit && route.instrumentId == 0L

    val settings: StateFlow<AppSettings> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    var query by mutableStateOf("")
        private set
    var search by mutableStateOf(SearchState())
        private set
    var form by mutableStateOf<LotForm?>(null)
        private set
    var resolving by mutableStateOf(false)
        private set
    var resolveFailed by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set

    private val queryFlow = MutableStateFlow("")

    init {
        when {
            isEdit -> viewModelScope.launch { loadLot(route.lotId) }
            route.instrumentId != 0L -> viewModelScope.launch { loadInstrument(route.instrumentId, route.currency) }
            else -> viewModelScope.launch {
                queryFlow.debounce(SEARCH_DEBOUNCE_MS).map { it.trim() }.distinctUntilChanged().collectLatest { text ->
                    if (text.length < 2) {
                        search = SearchState()
                    } else {
                        search = search.copy(loading = true)
                        search = SearchState(results = quotes.search(text), loading = false, searched = true)
                    }
                }
            }
        }
    }

    fun onQueryChange(value: String) {
        query = value
        queryFlow.value = value
    }

    fun select(candidate: InstrumentCandidate) {
        if (resolving) return
        viewModelScope.launch {
            resolving = true
            resolveFailed = false
            runCatchingCancellable { quotes.resolve(candidate) }
                .onSuccess { resolved -> form = newForm(resolved) }
                .onFailure { resolveFailed = true }
            resolving = false
        }
    }

    fun backToSearch() {
        form = null
    }

    fun update(transform: (LotForm) -> LotForm) {
        form = form?.let(transform)
    }

    fun save(onDone: () -> Unit) {
        val current = form ?: return
        val input = current.toInput() ?: return
        if (saving) return
        viewModelScope.launch {
            saving = true
            when {
                isEdit -> portfolio.updateLot(route.lotId, input)
                current.instrumentId != null -> portfolio.addLot(current.instrumentId, input)
                current.resolved != null -> portfolio.addLot(current.resolved, input)
            }
            widgetUpdater.update()
            // Fetch fresh prices (and a quote in a newly used currency) without holding up the screen.
            appScope.launch { refresher.refresh(force = false) }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            portfolio.deleteLot(route.lotId)
            widgetUpdater.update()
            onDone()
        }
    }

    private fun newForm(resolved: ResolvedInstrument): LotForm {
        val candidate = resolved.candidate
        val options = if (candidate.kind == AssetKind.CRYPTO) CRYPTO_CURRENCIES else listOfNotNull(candidate.currency)
        return LotForm(
            symbol = candidate.symbol,
            name = candidate.name,
            exchange = candidate.exchange,
            kind = candidate.kind,
            resolved = resolved,
            instrumentId = null,
            currentPrices = resolved.prices,
            currencyOptions = options,
            currency = options.first(),
            date = LocalDate.now(clock),
        )
    }

    private suspend fun loadInstrument(instrumentId: Long, currency: String?) {
        val instrument = portfolio.getInstrument(instrumentId) ?: return
        val options = currencyOptions(instrument)
        form = LotForm(
            symbol = instrument.symbol,
            name = instrument.name,
            exchange = instrument.exchange,
            kind = instrument.kind,
            resolved = null,
            instrumentId = instrument.id,
            currentPrices = portfolio.getQuotes(instrument.id).associate { it.currency to it.price },
            currencyOptions = options,
            currency = currency?.takeIf { it in options } ?: options.first(),
            date = LocalDate.now(clock),
        )
    }

    private suspend fun loadLot(lotId: Long) {
        val lot = portfolio.getLot(lotId) ?: return
        val instrument = portfolio.getInstrument(lot.instrumentId) ?: return
        val custom = lot.alertUpPct != null || lot.alertDownPct != null
        form = LotForm(
            symbol = instrument.symbol,
            name = instrument.name,
            exchange = instrument.exchange,
            kind = instrument.kind,
            resolved = null,
            instrumentId = instrument.id,
            currentPrices = portfolio.getQuotes(instrument.id).associate { it.currency to it.price },
            currencyOptions = (currencyOptions(instrument) + lot.currency).distinct(),
            currency = lot.currency,
            date = lot.buyDate,
            priceText = Formatters.editable(lot.buyPrice),
            quantityText = lot.quantity?.let { Formatters.editable(it) }.orEmpty(),
            customAlerts = custom,
            alertUpText = lot.alertUpPct?.let { Formatters.editable(it) }.orEmpty(),
            alertDownText = lot.alertDownPct?.let { Formatters.editable(it) }.orEmpty(),
        )
    }

    private fun currencyOptions(instrument: Instrument): List<String> =
        if (instrument.kind == AssetKind.CRYPTO) CRYPTO_CURRENCIES else listOfNotNull(instrument.currency)

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
