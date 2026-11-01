package io.github.albertrzad18.moneytracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.albertrzad18.moneytracker.MainActivity
import io.github.albertrzad18.moneytracker.R
import io.github.albertrzad18.moneytracker.data.repo.AppSettings
import io.github.albertrzad18.moneytracker.data.repo.PortfolioRepository
import io.github.albertrzad18.moneytracker.data.repo.SettingsRepository
import io.github.albertrzad18.moneytracker.data.repo.WidgetTheme
import io.github.albertrzad18.moneytracker.domain.LotResult
import io.github.albertrzad18.moneytracker.domain.Position
import io.github.albertrzad18.moneytracker.util.Formatters
import io.github.albertrzad18.moneytracker.work.RefreshScheduler
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import androidx.glance.color.ColorProvider as DayNightColorProvider

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun portfolioRepository(): PortfolioRepository
    fun settingsRepository(): SettingsRepository
}

private data class WidgetData(val positions: List<Position>, val settings: AppSettings)

/** Data is read only from the local database, so the widget also works offline. */
class PortfolioWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val data = combine(
            entryPoint.portfolioRepository().positions,
            entryPoint.settingsRepository().settings,
        ) { positions, settings -> WidgetData(positions.filter { it.instrument.onWidget }, settings) }
        val initial = data.first()
        provideContent {
            val state by data.collectAsState(initial)
            WidgetBody(context, state)
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

private class WidgetPalette(
    val background: ColorProvider,
    val text: ColorProvider,
    val secondary: ColorProvider,
    val up: ColorProvider,
    val down: ColorProvider,
    val warning: ColorProvider,
)

private fun palette(theme: WidgetTheme, opacityPercent: Int): WidgetPalette {
    val alpha = opacityPercent.coerceIn(0, 100) / 100f
    fun pick(light: Color, dark: Color): ColorProvider = when (theme) {
        WidgetTheme.LIGHT -> ColorProvider(light)
        WidgetTheme.DARK -> ColorProvider(dark)
        WidgetTheme.SYSTEM -> DayNightColorProvider(day = light, night = dark)
    }
    return WidgetPalette(
        background = pick(Color(0xFFFCFCFB).copy(alpha = alpha), Color(0xFF1A1A19).copy(alpha = alpha)),
        text = pick(Color(0xFF0B0B0B), Color(0xFFF0EFEC)),
        secondary = pick(Color(0xFF52514E), Color(0xFFC3C2B7)),
        up = pick(Color(0xFF1B7F3B), Color(0xFF6DD58C)),
        down = pick(Color(0xFFC62828), Color(0xFFFF8A80)),
        warning = pick(Color(0xFFB26A00), Color(0xFFFFB74D)),
    )
}

@Composable
private fun WidgetBody(context: Context, data: WidgetData) {
    val colors = palette(data.settings.widgetTheme, data.settings.widgetOpacity)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.background)
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Header(context, data.settings, colors)
        Spacer(GlanceModifier.height(4.dp))
        if (data.positions.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(MainActivity.launchIntent(context))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = context.getString(R.string.widget_empty),
                    style = TextStyle(color = colors.secondary, fontSize = 13.sp),
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                data.positions.forEachIndexed { index, position ->
                    item(itemId = -(index + 1L)) {
                        GroupHeader(context, position, colors, first = index == 0)
                    }
                    items(position.lots, itemId = { it.lot.id }) { result ->
                        LotRow(context, position, result, colors)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(context: Context, settings: AppSettings, colors: WidgetPalette) {
    val now = Instant.now()
    val last = settings.lastRefreshAt
    val stale = last == null || !settings.lastRefreshOk || Duration.between(last, now) > STALE_AFTER
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = context.getString(R.string.widget_title),
            style = TextStyle(color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(MainActivity.launchIntent(context))),
        )
        Text(
            text = (if (stale) "! " else "") + (last?.let { Formatters.dateTime(it, now) } ?: "—"),
            style = TextStyle(color = if (stale) colors.warning else colors.secondary, fontSize = 12.sp),
        )
        Spacer(GlanceModifier.width(8.dp))
        if (settings.isRefreshing(now)) {
            CircularProgressIndicator(modifier = GlanceModifier.size(20.dp), color = colors.secondary)
        } else {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_refresh),
                contentDescription = context.getString(R.string.action_refresh),
                colorFilter = ColorFilter.tint(colors.secondary),
                modifier = GlanceModifier.size(22.dp).clickable(actionRunCallback<RefreshAction>()),
            )
        }
    }
}

@Composable
private fun GroupHeader(context: Context, position: Position, colors: WidgetPalette, first: Boolean) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = if (first) 2.dp else 8.dp, bottom = 1.dp)
            .clickable(openInstrument(context, position)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = position.instrument.symbol,
            style = TextStyle(color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            text = position.quote?.let { Formatters.money(it.price, position.currency) } ?: "—",
            style = TextStyle(color = colors.text, fontSize = 13.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun LotRow(context: Context, position: Position, result: LotResult, colors: WidgetPalette) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 1.dp, bottom = 1.dp)
            .clickable(openInstrument(context, position)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${Formatters.shortDate(result.lot.buyDate)}  @${Formatters.price(result.lot.buyPrice)}",
            style = TextStyle(color = colors.secondary, fontSize = 12.sp),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            text = Formatters.pct(result.pct),
            style = TextStyle(color = gainColor(result.pct, colors), fontSize = 13.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}

private fun openInstrument(context: Context, position: Position) =
    actionStartActivity(MainActivity.instrumentIntent(context, position.instrument.id, position.currency))

private fun gainColor(pct: BigDecimal?, colors: WidgetPalette): ColorProvider = when {
    pct == null -> colors.secondary
    pct.signum() > 0 -> colors.up
    pct.signum() < 0 -> colors.down
    else -> colors.secondary
}
