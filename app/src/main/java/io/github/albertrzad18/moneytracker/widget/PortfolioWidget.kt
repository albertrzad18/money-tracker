package io.github.albertrzad18.moneytracker.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.LocalAppWidgetOptions
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.albertrzad18.moneytracker.MainActivity
import io.github.albertrzad18.moneytracker.R
import io.github.albertrzad18.moneytracker.data.db.AppDatabase
import io.github.albertrzad18.moneytracker.data.repo.AppSettings
import io.github.albertrzad18.moneytracker.data.repo.PortfolioRepository
import io.github.albertrzad18.moneytracker.data.repo.QuoteRepository
import io.github.albertrzad18.moneytracker.data.repo.SettingsRepository
import io.github.albertrzad18.moneytracker.data.repo.WidgetTheme
import io.github.albertrzad18.moneytracker.domain.LotResult
import io.github.albertrzad18.moneytracker.domain.PortfolioSummary
import io.github.albertrzad18.moneytracker.domain.Position
import io.github.albertrzad18.moneytracker.domain.SummaryCalculator
import io.github.albertrzad18.moneytracker.ui.components.deviationFraction
import io.github.albertrzad18.moneytracker.ui.theme.WidgetColors
import io.github.albertrzad18.moneytracker.util.Formatters
import io.github.albertrzad18.moneytracker.work.RefreshScheduler
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Duration
import java.time.Instant
import androidx.glance.color.ColorProvider as DayNightColorProvider

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun portfolioRepository(): PortfolioRepository
    fun settingsRepository(): SettingsRepository
    fun quoteRepository(): QuoteRepository
    fun database(): AppDatabase
    fun clock(): Clock
}

private data class WidgetData(
    /** Positions chosen for the widget, in the app's order. */
    val positions: List<Position>,
    /** Whole portfolio in PLN, for the 2×2 total. */
    val summary: PortfolioSummary,
    /** Portfolio value over the last year from cached history; empty when nothing is cached. */
    val spark: List<Float>,
    val settings: AppSettings,
)

/** Layouts by size: 2×2 total, 4×1 row of tiles, 4×2 grid of tiles, 4×3 list of purchases. */
private val SIZE_2X2 = DpSize(120.dp, 120.dp)
private val SIZE_4X1 = DpSize(250.dp, 48.dp)
private val SIZE_4X2 = DpSize(250.dp, 130.dp)
private val SIZE_4X3 = DpSize(250.dp, 210.dp)

/** Data is read only from the local database, so the widget also works offline. */
class PortfolioWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SIZE_2X2, SIZE_4X1, SIZE_4X2, SIZE_4X3))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val sparkline = WidgetSparkline(entryPoint.database(), entryPoint.clock())
        val data = combine(
            entryPoint.portfolioRepository().positions,
            entryPoint.quoteRepository().latestFxRates,
            entryPoint.settingsRepository().settings,
        ) { positions, fx, settings ->
            WidgetData(
                positions = positions.filter { it.instrument.onWidget },
                summary = SummaryCalculator.summarize(positions, emptyList(), fx),
                spark = sparkline.values(),
                settings = settings,
            )
        }
        val initial = data.first()
        provideContent {
            val state by data.collectAsState(initial)
            WidgetBody(state)
        }
    }
}

class PortfolioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PortfolioWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshScheduler.schedulePeriodic(context)
        RefreshScheduler.refreshNow(context)
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RefreshScheduler.refreshNow(context)
    }
}

private val STALE_AFTER: Duration = Duration.ofHours(2)

/** Glance colors for the chosen theme; System follows day/night. */
private class WidgetPalette(private val theme: WidgetTheme, opacityPercent: Int) {
    private fun pick(light: Color, dark: Color): ColorProvider = when (theme) {
        WidgetTheme.LIGHT -> ColorProvider(light)
        WidgetTheme.DARK -> ColorProvider(dark)
        WidgetTheme.SYSTEM -> DayNightColorProvider(day = light, night = dark)
    }

    private val light = WidgetColors.Light
    private val dark = WidgetColors.Dark
    private val alpha = opacityPercent.coerceIn(0, 100) / 100f

    val background = pick(light.background.copy(alpha = alpha), dark.background.copy(alpha = alpha))
    val text = pick(light.text, dark.text)
    val muted = pick(light.muted, dark.muted)
    val chart = pick(light.chart, dark.chart)
    val line = pick(light.line, dark.line)

    fun tone(pct: BigDecimal?): ColorProvider = pick(light.tone(pct), dark.tone(pct))

    fun tile(pct: BigDecimal?): GlanceTileColors {
        val l = light.tile(pct)
        val d = dark.tile(pct)
        return GlanceTileColors(
            background = pick(l.background.copy(alpha = alpha), d.background.copy(alpha = alpha)),
            symbol = pick(l.symbol, d.symbol),
            exchange = pick(l.symbol.copy(alpha = 0.6f), d.symbol.copy(alpha = 0.6f)),
            value = pick(l.value, d.value),
        )
    }
}

private class GlanceTileColors(val background: ColorProvider, val symbol: ColorProvider, val exchange: ColorProvider, val value: ColorProvider)

@Composable
private fun WidgetBody(data: WidgetData) {
    val size = LocalSize.current
    // LocalSize is the matched Responsive bucket; bars need the real width (portrait = min width).
    val reported = LocalAppWidgetOptions.current.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
    val width = if (reported > 0) reported.dp else size.width
    val colors = WidgetPalette(data.settings.widgetTheme, data.settings.widgetOpacity)
    when {
        // 4×1 and 4×2 have no widget background: every position is its own tile.
        size.height < 100.dp -> TileStrip(data, colors, width)
        size.width < 240.dp -> Card(colors) { Total(data, colors, width) }
        size.height < 200.dp -> TileGrid(data, colors)
        else -> Card(colors) { Purchases(data, colors, width) }
    }
}

@Composable
private fun Card(colors: WidgetPalette, content: @Composable () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background), colorFilter = ColorFilter.tint(colors.background))
            .cornerRadius(22.dp)
            .clickable(actionStartActivity(MainActivity.launchIntent(context))),
    ) { content() }
}

private fun textStyle(color: ColorProvider, size: TextUnit, weight: FontWeight = FontWeight.Normal, align: TextAlign = TextAlign.Start) =
    TextStyle(color = color, fontSize = size, fontWeight = weight, textAlign = align)

/** "HH:mm" of the last refresh, or "—"; stale or failed refreshes switch to the loss color. */
@Composable
private fun UpdatedText(settings: AppSettings, colors: WidgetPalette, prefix: Boolean, size: TextUnit = 11.sp, weight: FontWeight = FontWeight.Normal) {
    val context = LocalContext.current
    val now = Instant.now()
    val last = settings.lastRefreshAt
    val stale = last == null || !settings.lastRefreshOk || Duration.between(last, now) > STALE_AFTER
    val time = last?.let { Formatters.dateTime(it, now) } ?: "—"
    Text(
        text = if (prefix) context.getString(R.string.widget_title_time, time) else time,
        style = textStyle(if (stale) colors.tone(BigDecimal.ONE.negate()) else colors.muted, size, weight),
        maxLines = 1,
    )
}

@Composable
private fun RefreshIcon(settings: AppSettings, colors: WidgetPalette, size: Dp) {
    val context = LocalContext.current
    if (settings.isRefreshing(Instant.now())) {
        CircularProgressIndicator(modifier = GlanceModifier.size(size), color = colors.muted)
    } else {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_refresh),
            contentDescription = context.getString(R.string.action_refresh),
            colorFilter = ColorFilter.tint(colors.muted),
            // A larger touch area than the icon itself.
            modifier = GlanceModifier.size(size + 12.dp).padding(6.dp).clickable(actionRunCallback<RefreshAction>()),
        )
    }
}

@Composable
private fun EmptyText(colors: WidgetPalette) {
    val context = LocalContext.current
    Box(GlanceModifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
        Text(context.getString(R.string.widget_empty), style = textStyle(colors.muted, 13.sp, align = TextAlign.Center))
    }
}

/** 2×2: total result, value in PLN and a one-year sparkline. */
@Composable
private fun Total(data: WidgetData, colors: WidgetPalette, width: Dp) {
    val context = LocalContext.current
    val summary = data.summary
    Column(GlanceModifier.fillMaxSize().padding(start = 14.dp, top = 8.dp, end = 8.dp, bottom = 12.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.defaultWeight()) { UpdatedText(data.settings, colors, prefix = true, weight = FontWeight.Medium) }
            RefreshIcon(data.settings, colors, 14.dp)
        }
        Spacer(GlanceModifier.height(4.dp))
        Text(Formatters.pct(summary.pct), style = textStyle(colors.tone(summary.pct), 30.sp, FontWeight.Bold), maxLines = 1)
        summary.valuePln?.let { value ->
            Text(
                Formatters.quantity(value.setScale(0, RoundingMode.HALF_UP)) + " " + Formatters.currencySymbol("PLN"),
                style = textStyle(colors.text, 13.sp, FontWeight.Medium),
                maxLines = 1,
            )
        }
        if (summary.valuePln == null && data.positions.isEmpty()) {
            Text(context.getString(R.string.widget_empty), style = textStyle(colors.muted, 11.sp), maxLines = 2)
        }
        Spacer(GlanceModifier.defaultWeight())
        if (data.spark.size >= 2) {
            val bitmap = sparklineBitmap(context, data.spark, width - 28.dp, 30.dp)
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.chart),
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.padding(end = 6.dp).fillMaxWidth().height(30.dp),
            )
        }
    }
}

/** Tap on any tile refreshes, as in the design; the 2×2 and 4×3 cards open the app. */
private val refreshOnTap = actionRunCallback<RefreshAction>()

private val TILE_GAP = 4.dp

/** One rounded tile tinted by the result; a full color for moves of 10% or more. */
@Composable
private fun Tile(
    colors: GlanceTileColors,
    modifier: GlanceModifier,
    horizontalPadding: Pair<Dp, Dp>,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .background(ImageProvider(R.drawable.widget_tile), colorFilter = ColorFilter.tint(colors.background))
            .cornerRadius(18.dp)
            .padding(start = horizontalPadding.first, end = horizontalPadding.second)
            .clickable(refreshOnTap),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** "+14,1" large and "%" small; an em dash when the result is unknown. */
@Composable
private fun TileResult(pct: BigDecimal?, color: ColorProvider, size: TextUnit, percentSize: TextUnit) {
    val text = Formatters.pct(pct)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text.removeSuffix("%"), style = textStyle(color, size, FontWeight.Bold), maxLines = 1)
        if (text.endsWith("%")) Text("%", style = textStyle(color, percentSize, FontWeight.Bold), maxLines = 1)
    }
}

@Composable
private fun EmptyTile(colors: WidgetPalette) {
    val context = LocalContext.current
    Box(
        GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_tile), colorFilter = ColorFilter.tint(colors.background))
            .cornerRadius(18.dp)
            .padding(12.dp)
            .clickable(actionStartActivity(MainActivity.launchIntent(context))),
        contentAlignment = Alignment.Center,
    ) {
        Text(context.getString(R.string.widget_empty), style = textStyle(colors.muted, 13.sp, align = TextAlign.Center))
    }
}

/**
 * 4×1: a row of tiles, as many as fit. Each tile is as wide as its symbol or result needs and the
 * spare width is shared equally, like `flex: 1 1 auto` in the design.
 */
@Composable
private fun TileStrip(data: WidgetData, colors: WidgetPalette, width: Dp) {
    if (data.positions.isEmpty()) return EmptyTile(colors)
    val fontScale = LocalContext.current.resources.configuration.fontScale
    fun natural(position: Position): Float {
        val symbol = Formatters.symbolParts(position.instrument.symbol).first
        val result = Formatters.pct(position.pct).removeSuffix("%")
        return 16f + maxOf(symbol.length * 6.4f, result.length * 9f + 8f) * fontScale
    }
    val shown = mutableListOf<Pair<Position, Float>>()
    var used = 0f
    for (position in data.positions.take(MAX_STRIP_TILES)) {
        val w = natural(position)
        val next = used + w + if (shown.isEmpty()) 0f else TILE_GAP.value
        if (shown.isNotEmpty() && next > width.value) break
        shown += position to w
        used = next
    }
    val spare = ((width.value - used) / shown.size).coerceAtLeast(0f)
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(GlanceModifier.fillMaxWidth().height(58.dp)) {
            shown.forEachIndexed { i, (position, w) ->
                if (i > 0) Spacer(GlanceModifier.width(TILE_GAP))
                val last = i == shown.lastIndex
                val tile = colors.tile(position.pct)
                val size = if (last) GlanceModifier.defaultWeight() else GlanceModifier.width((w + spare).dp)
                Tile(tile, size.fillMaxHeight(), 9.dp to 7.dp) {
                    Text(
                        Formatters.symbolParts(position.instrument.symbol).first,
                        style = textStyle(tile.symbol, 10.sp, FontWeight.Bold),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    TileResult(position.pct, tile.value, 15.sp, 10.sp)
                }
            }
        }
    }
}

// RemoteViews rows hold at most ten children: five tiles and four gaps.
private const val MAX_STRIP_TILES = 5

/** 4×2: the first four positions as a 2×2 grid of tiles. */
@Composable
private fun TileGrid(data: WidgetData, colors: WidgetPalette) {
    if (data.positions.isEmpty()) return EmptyTile(colors)
    val rows = data.positions.take(4).chunked(2)
    Column(GlanceModifier.fillMaxSize()) {
        rows.forEachIndexed { r, row ->
            if (r > 0) Spacer(GlanceModifier.height(TILE_GAP))
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                row.forEachIndexed { i, position ->
                    if (i > 0) Spacer(GlanceModifier.width(TILE_GAP))
                    val tile = colors.tile(position.pct)
                    val (symbol, exchange) = Formatters.symbolParts(position.instrument.symbol)
                    Tile(tile, GlanceModifier.defaultWeight().fillMaxHeight(), 14.dp to 14.dp) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(symbol, style = textStyle(tile.symbol, 12.sp, FontWeight.Bold), maxLines = 1)
                            if (exchange.isNotEmpty()) {
                                Spacer(GlanceModifier.width(6.dp))
                                Text(exchange, style = textStyle(tile.exchange, 11.sp, FontWeight.Medium), maxLines = 1)
                            }
                        }
                        Spacer(GlanceModifier.height(5.dp))
                        Text(Formatters.pct(position.pct), style = textStyle(tile.value, 24.sp, FontWeight.Bold), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(settings: AppSettings, colors: WidgetPalette) {
    val context = LocalContext.current
    Row(GlanceModifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            context.getString(R.string.widget_title),
            style = textStyle(colors.text, 13.sp, FontWeight.Bold),
            modifier = GlanceModifier.defaultWeight(),
        )
        UpdatedText(settings, colors, prefix = false)
        Spacer(GlanceModifier.width(2.dp))
        RefreshIcon(settings, colors, 16.dp)
    }
}

/** 4×3: every position with its price, and every purchase with a bar. Scrolls. */
@Composable
private fun Purchases(data: WidgetData, colors: WidgetPalette, width: Dp) {
    Column(GlanceModifier.fillMaxSize().padding(start = 12.dp, top = 6.dp, end = 6.dp, bottom = 10.dp)) {
        Header(data.settings, colors)
        if (data.positions.isEmpty()) return@Column EmptyText(colors)
        val barWidth = width - 18.dp - 14.dp - 104.dp - 50.dp - 20.dp
        LazyColumn(GlanceModifier.fillMaxSize()) {
            items(data.positions, itemId = { it.instrument.id * 31 + it.currency.hashCode() }) { position ->
                PositionBlock(position, colors, barWidth)
            }
        }
    }
}

@Composable
private fun PositionBlock(position: Position, colors: WidgetPalette, barWidth: Dp) {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxWidth().padding(start = 4.dp, top = 5.dp, end = 8.dp, bottom = 6.dp).clickable(openInstrument(context, position))) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(position.instrument.symbol, style = textStyle(colors.text, 12.sp, FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            Text(position.quote?.let { Formatters.money(it.price, position.currency) } ?: "—", style = textStyle(colors.muted, 12.sp), maxLines = 1)
        }
        // RemoteViews containers hold at most ten children, so long purchase lists are split.
        position.lots.chunked(9).forEach { chunk ->
            Column(GlanceModifier.fillMaxWidth()) {
                chunk.forEach { LotRow(it, colors, barWidth) }
            }
        }
    }
}

@Composable
private fun LotRow(result: LotResult, colors: WidgetPalette, barWidth: Dp) {
    Row(GlanceModifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${Formatters.shortDate(result.lot.buyDate)}  @${Formatters.price(result.lot.buyPrice)}",
            style = textStyle(colors.muted, 11.sp),
            maxLines = 1,
            modifier = GlanceModifier.width(104.dp),
        )
        Spacer(GlanceModifier.width(10.dp))
        DeviationBar(result.pct, barWidth, 4.dp, colors)
        Spacer(GlanceModifier.width(10.dp))
        Text(
            Formatters.pct(result.pct),
            style = textStyle(colors.tone(result.pct), 12.sp, FontWeight.Bold, TextAlign.End),
            maxLines = 1,
            modifier = GlanceModifier.width(50.dp),
        )
    }
}

/** Track with a fill growing from the middle: right for a gain, left for a loss (±30% fills a half). */
@Composable
private fun DeviationBar(pct: BigDecimal?, width: Dp, thickness: Dp, colors: WidgetPalette) {
    val safeWidth = if (width < 24.dp) 24.dp else width
    val half = safeWidth / 2
    val fill = half * deviationFraction(pct)
    val gain = (pct?.signum() ?: 0) >= 0
    val tone = colors.tone(pct)
    Box(GlanceModifier.width(safeWidth).height(thickness), contentAlignment = Alignment.Center) {
        Spacer(GlanceModifier.width(safeWidth).height(thickness).background(colors.line).cornerRadius(thickness / 2))
        Row(GlanceModifier.width(safeWidth).height(thickness)) {
            Box(GlanceModifier.width(half).height(thickness), contentAlignment = Alignment.CenterEnd) {
                if (!gain && fill > 0.dp) Spacer(GlanceModifier.width(fill).height(thickness).background(tone).cornerRadius(thickness / 2))
            }
            Box(GlanceModifier.width(half).height(thickness), contentAlignment = Alignment.CenterStart) {
                if (gain && fill > 0.dp) Spacer(GlanceModifier.width(fill).height(thickness).background(tone).cornerRadius(thickness / 2))
            }
        }
    }
}

private fun openInstrument(context: Context, position: Position) =
    actionStartActivity(MainActivity.instrumentIntent(context, position.instrument.id, position.currency))

/** White line on a transparent bitmap; the widget tints it, so it follows day/night. */
private fun sparklineBitmap(context: Context, values: List<Float>, width: Dp, height: Dp): Bitmap {
    val density = context.resources.displayMetrics.density
    val w = (width.value * density).toInt().coerceAtLeast(2)
    val h = (height.value * density).toInt().coerceAtLeast(2)
    val stroke = 2f * density
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    var lo = values.min()
    var hi = values.max()
    val pad = if (hi > lo) (hi - lo) * 0.1f else 1f
    lo -= pad
    hi += pad
    val top = stroke
    val bottom = h - stroke
    val path = android.graphics.Path()
    values.forEachIndexed { i, v ->
        val x = stroke / 2 + i.toFloat() / (values.size - 1) * (w - stroke)
        val y = bottom - (v - lo) / (hi - lo) * (bottom - top)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = android.graphics.Color.WHITE
    }
    android.graphics.Canvas(bitmap).drawPath(path, paint)
    return bitmap
}
