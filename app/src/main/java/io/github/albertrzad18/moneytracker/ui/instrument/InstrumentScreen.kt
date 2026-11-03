package io.github.albertrzad18.moneytracker.ui.instrument

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.albertrzad18.moneytracker.R
import io.github.albertrzad18.moneytracker.data.db.AssetKind
import io.github.albertrzad18.moneytracker.data.db.Lot
import io.github.albertrzad18.moneytracker.domain.ChartPoint
import io.github.albertrzad18.moneytracker.domain.ChartRange
import io.github.albertrzad18.moneytracker.domain.LotResult
import io.github.albertrzad18.moneytracker.domain.Position
import io.github.albertrzad18.moneytracker.domain.RealizedLot
import io.github.albertrzad18.moneytracker.ui.components.AreaChart
import io.github.albertrzad18.moneytracker.ui.components.ChartRef
import io.github.albertrzad18.moneytracker.ui.components.CircleIconButton
import io.github.albertrzad18.moneytracker.ui.components.DeleteLotDialog
import io.github.albertrzad18.moneytracker.ui.components.DeviationBar
import io.github.albertrzad18.moneytracker.ui.components.LocalSnackbar
import io.github.albertrzad18.moneytracker.ui.components.Lucide
import io.github.albertrzad18.moneytracker.ui.components.PillButton
import io.github.albertrzad18.moneytracker.ui.components.PillButtonStyle
import io.github.albertrzad18.moneytracker.ui.components.RangeChips
import io.github.albertrzad18.moneytracker.ui.components.SectionTitle
import io.github.albertrzad18.moneytracker.ui.components.SegmentedPills
import io.github.albertrzad18.moneytracker.ui.theme.MenuShape
import io.github.albertrzad18.moneytracker.ui.theme.MoneyTheme
import io.github.albertrzad18.moneytracker.ui.theme.amountColor
import io.github.albertrzad18.moneytracker.ui.theme.gainColor
import io.github.albertrzad18.moneytracker.util.Formatters
import java.math.BigDecimal
import java.time.ZoneId

private val RANGES = listOf(
    ChartRange.WEEK to R.string.range_week,
    ChartRange.MONTH to R.string.range_month,
    ChartRange.HALF_YEAR to R.string.range_half_year,
    ChartRange.YEAR to R.string.range_year,
    ChartRange.MAX to R.string.range_max,
)

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
    var sellingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val snackbar = LocalSnackbar.current
    val resources = LocalResources.current

    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    val instrument = state.instrument
    val position = state.position

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Lucide.ArrowLeft, stringResource(R.string.action_back), onBack)
            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Text(instrument?.symbol.orEmpty(), style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Text(
                    instrument?.name.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (instrument != null) {
                CircleIconButton(Lucide.Plus, stringResource(R.string.action_add_lot), { onAddLot(instrument.id, state.currency) })
            }
        }
        if (instrument == null) return@Column

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 40.dp),
        ) {
            if (state.currencies.size > 1) {
                SegmentedPills(
                    options = state.currencies,
                    selectedIndex = state.currencies.indexOf(state.currency),
                    onSelect = { viewModel.selectCurrency(state.currencies[it]) },
                    modifier = Modifier.padding(bottom = 16.dp),
                    background = MaterialTheme.colorScheme.surface,
                )
            }

            PriceHeader(position, state.currency)

            Box(Modifier.padding(top = 30.dp).fillMaxWidth().height(200.dp)) {
                PriceChart(chart, range, position, state.currency, onRetry = viewModel::retryChart)
            }
            RangeChips(
                options = RANGES.map { stringResource(it.second) },
                selectedIndex = RANGES.indexOfFirst { it.first == range },
                onSelect = { viewModel.selectRange(RANGES[it].first) },
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            )
            val hint = buildString {
                val lotCount = position?.lots?.size ?: 0
                append(
                    stringResource(
                        when (lotCount) {
                            0 -> R.string.chart_touch_hint
                            1 -> R.string.chart_buy_line_hint
                            else -> R.string.chart_buy_lines_hint
                        },
                    ),
                )
                if (instrument.kind == AssetKind.CRYPTO && range == ChartRange.MAX) append(' ').append(stringResource(R.string.chart_crypto_limit))
            }
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp),
            )

            position?.let { open ->
                StatsGrid(open, Modifier.padding(top = 30.dp))
                SectionTitle(stringResource(R.string.section_open_lots), Modifier.padding(top = 36.dp, bottom = 8.dp))
                open.lots.forEach { result ->
                    OpenLotRow(
                        result = result,
                        currency = open.currency,
                        onEdit = { onEditLot(result.lot.id) },
                        onSell = { sellingId = result.lot.id },
                        onDelete = { deletingId = result.lot.id },
                    )
                }
            }

            if (state.realized.isNotEmpty()) {
                SectionTitle(stringResource(R.string.section_sold_lots), Modifier.padding(top = 28.dp, bottom = 4.dp))
                state.realized.forEach { realized ->
                    SoldLotRow(
                        realized = realized,
                        onReopen = { viewModel.reopen(realized.lot.id) },
                        onDelete = { deletingId = realized.lot.id },
                    )
                }
            }
        }
    }

    val allLots = position?.lots.orEmpty().map { it.lot } + state.realized.map { it.lot }
    sellingId?.let { id ->
        val lot = position?.lots?.find { it.lot.id == id }?.lot
        if (lot == null) {
            sellingId = null
        } else {
            SellSheet(
                lot = lot,
                currentPrice = position.quote?.price,
                onDismiss = { sellingId = null },
                onConfirm = { date, price, quantity, pct ->
                    viewModel.sell(lot.id, date, price, quantity)
                    sellingId = null
                    snackbar.show(resources.getString(R.string.snackbar_sold, Formatters.pct(pct)))
                },
            )
        }
    }
    deletingId?.let { id ->
        val lot: Lot? = allLots.find { it.id == id }
        if (lot == null) {
            deletingId = null
        } else {
            DeleteLotDialog(
                buyDate = Formatters.date(lot.buyDate),
                onDismiss = { deletingId = null },
                onConfirm = {
                    viewModel.delete(lot.id)
                    deletingId = null
                    snackbar.show(resources.getString(R.string.snackbar_deleted))
                },
            )
        }
    }
}

@Composable
private fun PriceHeader(position: Position?, currency: String) {
    val quote = position?.quote ?: return
    Text(Formatters.money(quote.price, currency), style = MaterialTheme.typography.displayMedium)
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val style = MaterialTheme.typography.bodyMedium
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        quote.dayChangePct?.let {
            Text(stringResource(R.string.day_change, Formatters.pct(it)), style = style, fontWeight = FontWeight.Bold, color = gainColor(it))
            Text("·", style = style, color = muted)
        }
        Text(stringResource(R.string.instrument_result), style = style, color = muted)
        Text(Formatters.pct(position.pct), style = style, fontWeight = FontWeight.Bold, color = gainColor(position.pct))
    }
}

/** Price chart without a card; keeps the previous series on screen while a new range loads, then morphs. */
@Composable
private fun PriceChart(chart: ChartState, range: ChartRange, position: Position?, currency: String, onRetry: () -> Unit) {
    // Plain holder, not state: it only changes together with [chart], which already recomposes.
    val shown = remember { arrayOfNulls<Pair<List<ChartPoint>, ChartRange>>(1) }
    if (chart is ChartState.Loaded) shown[0] = chart.points to range
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val current = shown[0]
    when {
        chart is ChartState.Error && current == null -> ChartMessage(stringResource(R.string.chart_error), onRetry)
        current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.primary)
        }
        current.first.size < 2 -> ChartMessage(stringResource(R.string.chart_no_data), null)
        else -> {
            val (points, shownRange) = current
            val refs = buildList {
                position?.lots?.forEach { add(ChartRef(it.lot.buyPrice.toFloat(), gainColor(it.pct))) }
                if ((position?.lots?.size ?: 0) > 1) add(ChartRef(position!!.averagePrice.toFloat(), muted, dotted = true))
            }
            val zone = ZoneId.systemDefault()
            AreaChart(
                values = points.map { it.price.toFloat() },
                refs = refs,
                formatValue = { Formatters.price(BigDecimal.valueOf(it.toDouble())) },
                tooltip = { i ->
                    val point = points[i]
                    val label = if (shownRange == ChartRange.WEEK) {
                        Formatters.dateTime(point.time)
                    } else {
                        Formatters.date(point.time.atZone(zone).toLocalDate())
                    }
                    "${Formatters.money(point.price, currency)} · $label"
                },
                description = stringResource(R.string.chart_price_description, currency),
                modifier = Modifier.fillMaxSize(),
            )
            if (chart is ChartState.Error) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ChartMessage(stringResource(R.string.chart_error), onRetry) }
            }
        }
    }
}

@Composable
private fun ChartMessage(text: String, onRetry: (() -> Unit)?) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) {
            Spacer(Modifier.height(8.dp))
            PillButton(stringResource(R.string.action_retry), onRetry, style = PillButtonStyle.Outlined, height = 40.dp)
        }
    }
}

/** Two-column grid; fields without data are left out. */
@Composable
private fun StatsGrid(position: Position, modifier: Modifier = Modifier) {
    val currency = position.currency
    val stats = buildList {
        add(
            Triple(
                stringResource(if (position.isWeightedAverage) R.string.label_average_weighted else R.string.label_average_simple),
                Formatters.money(position.averagePrice, currency),
                Color.Unspecified,
            ),
        )
        add(Triple(stringResource(R.string.label_lots_count), position.lots.size.toString(), Color.Unspecified))
        position.quantity?.let { add(Triple(stringResource(R.string.label_quantity), Formatters.quantity(it), Color.Unspecified)) }
        position.cost?.let { add(Triple(stringResource(R.string.label_invested), Formatters.money(it, currency), Color.Unspecified)) }
        position.value?.let { add(Triple(stringResource(R.string.label_value), Formatters.money(it, currency), Color.Unspecified)) }
        position.gain?.let { add(Triple(stringResource(R.string.label_gain), Formatters.signedMoney(it, currency), amountColor(it))) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        stats.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { (label, value, color) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.titleSmall, color = color, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun lotDetails(lot: Lot): String = buildString {
    append('@').append(Formatters.money(lot.buyPrice, lot.currency))
    lot.quantity?.let { append("  × ").append(Formatters.quantity(it)) }
}

@Composable
private fun OpenLotRow(result: LotResult, currency: String, onEdit: () -> Unit, onSell: () -> Unit, onDelete: () -> Unit) {
    val lot = result.lot
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Formatters.date(lot.buyDate), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(lotDetails(lot), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 10.dp)) {
                Text(Formatters.pct(result.pct), style = MaterialTheme.typography.titleSmall, color = gainColor(result.pct))
                result.gain?.let {
                    Text(Formatters.signedMoney(it, currency), style = MaterialTheme.typography.bodySmall, color = gainColor(result.pct))
                }
            }
            LotMenu(
                listOf(
                    MenuAction(R.string.action_edit, Lucide.Pencil, onClick = onEdit),
                    MenuAction(R.string.action_sell, Lucide.HandCoins, onClick = onSell),
                    MenuAction(R.string.action_delete, Lucide.Trash, danger = true, onClick = onDelete),
                ),
            )
        }
        DeviationBar(result.pct, Modifier.fillMaxWidth().padding(top = 6.dp), centerTick = true)
    }
}

@Composable
private fun SoldLotRow(realized: RealizedLot, onReopen: () -> Unit, onDelete: () -> Unit) {
    val lot = realized.lot
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "${Formatters.date(lot.buyDate)} → ${lot.sellDate?.let(Formatters::date).orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
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
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 10.dp)) {
            Text(Formatters.pct(realized.pct), style = MaterialTheme.typography.titleSmall, color = gainColor(realized.pct))
            realized.gain?.let {
                Text(Formatters.signedMoney(it, lot.currency), style = MaterialTheme.typography.bodySmall, color = gainColor(realized.pct))
            }
        }
        LotMenu(
            listOf(
                MenuAction(R.string.action_reopen, Lucide.Undo, onClick = onReopen),
                MenuAction(R.string.action_delete, Lucide.Trash, danger = true, onClick = onDelete),
            ),
        )
    }
}

private class MenuAction(val label: Int, val icon: ImageVector, val danger: Boolean = false, val onClick: () -> Unit)

/** "More" button with a context menu: surface, radius 22, large shadow, 44 dp items. */
@Composable
private fun LotMenu(actions: List<MenuAction>) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.offset(x = 10.dp)) {
        CircleIconButton(
            icon = Lucide.EllipsisVertical,
            contentDescription = stringResource(R.string.action_more),
            onClick = { expanded = true },
            size = 40.dp,
            iconSize = 20.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MenuShape,
            containerColor = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            tonalElevation = 0.dp,
            modifier = Modifier.widthIn(min = 184.dp).padding(horizontal = 6.dp),
        ) {
            actions.forEach { action ->
                val color = if (action.danger) MoneyTheme.colors.loss else MaterialTheme.colorScheme.onSurface
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            expanded = false
                            action.onClick()
                        }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(action.icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(action.label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
                }
            }
        }
    }
}
