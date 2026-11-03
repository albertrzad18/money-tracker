package io.github.albertrzad18.moneytracker.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.albertrzad18.moneytracker.R
import io.github.albertrzad18.moneytracker.data.db.Instrument
import io.github.albertrzad18.moneytracker.data.repo.AppSettings
import io.github.albertrzad18.moneytracker.data.repo.PortfolioRepository
import io.github.albertrzad18.moneytracker.data.repo.SettingsRepository
import io.github.albertrzad18.moneytracker.data.repo.WidgetTheme
import io.github.albertrzad18.moneytracker.domain.Position
import io.github.albertrzad18.moneytracker.ui.components.CircleIconButton
import io.github.albertrzad18.moneytracker.ui.components.Lucide
import io.github.albertrzad18.moneytracker.ui.components.MoneyCard
import io.github.albertrzad18.moneytracker.ui.components.MoneyCheckbox
import io.github.albertrzad18.moneytracker.ui.components.PillButton
import io.github.albertrzad18.moneytracker.ui.components.PillButtonStyle
import io.github.albertrzad18.moneytracker.ui.components.PillDecimalField
import io.github.albertrzad18.moneytracker.ui.components.ScreenTitle
import io.github.albertrzad18.moneytracker.ui.components.SectionTitle
import io.github.albertrzad18.moneytracker.ui.components.SegmentedPills
import io.github.albertrzad18.moneytracker.ui.components.SwitchRow
import io.github.albertrzad18.moneytracker.ui.theme.InsetShape
import io.github.albertrzad18.moneytracker.ui.theme.MoneyTheme
import io.github.albertrzad18.moneytracker.ui.theme.WidgetColors
import io.github.albertrzad18.moneytracker.util.Formatters
import io.github.albertrzad18.moneytracker.widget.WidgetUpdater
import io.github.albertrzad18.moneytracker.work.Notifications
import io.github.albertrzad18.moneytracker.work.Refresher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings,
    /** Instruments with open lots, in widget order. */
    val instruments: List<Instrument>,
    /** Open positions in widget order, for the live widget preview. */
    val positions: List<Position>,
    val refreshing: Boolean,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val portfolio: PortfolioRepository,
    private val refresher: Refresher,
    private val widgetUpdater: WidgetUpdater,
) : ViewModel() {
    private val refreshing = MutableStateFlow(false)

    val state: StateFlow<SettingsUiState?> =
        combine(settings.settings, portfolio.instruments, portfolio.positions, refreshing) { s, instruments, positions, busy ->
            val active = positions.map { it.instrument.id }.toSet()
            SettingsUiState(s, instruments.filter { it.id in active }, positions, busy)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setAlertsEnabled(enabled: Boolean) = launch { settings.setAlertsEnabled(enabled) }

    fun setDefaultAlerts(up: BigDecimal, down: BigDecimal) = launch { settings.setDefaultAlerts(up, down) }

    fun setWidgetTheme(theme: WidgetTheme) = launchAndUpdateWidget { settings.setWidgetTheme(theme) }

    fun setWidgetOpacity(percent: Int) = launchAndUpdateWidget { settings.setWidgetOpacity(percent) }

    fun setOnWidget(instrumentId: Long, onWidget: Boolean) = launchAndUpdateWidget { portfolio.setOnWidget(instrumentId, onWidget) }

    fun move(instrumentId: Long, up: Boolean) = launchAndUpdateWidget { portfolio.moveInstrument(instrumentId, up) }

    fun refreshNow() {
        if (refreshing.value) return
        launch {
            refreshing.value = true
            try {
                refresher.refresh(force = true)
            } finally {
                refreshing.value = false
            }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun launchAndUpdateWidget(block: suspend () -> Unit) = launch {
        block()
        widgetUpdater.update()
    }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle(stringResource(R.string.nav_settings))
        val current = state ?: return@Column
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AlertsSection(current.settings, viewModel)
            WidgetSection(current, viewModel)
            LanguageSection()
            RefreshSection(current, viewModel)
            AboutSection()
        }
    }
}

@Composable
private fun SettingsHeader(text: String) {
    SectionTitle(text, Modifier.padding(start = 6.dp, top = 12.dp), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp))
}

@Composable
private fun SmallLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), modifier = modifier.padding(start = 6.dp))
}

@Composable
private fun AlertsSection(settings: AppSettings, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    var canNotify by remember { mutableStateOf(Notifications.canNotify(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = granted
    }
    var upText by rememberSaveable { mutableStateOf(Formatters.editable(settings.defaultAlertUpPct)) }
    var downText by rememberSaveable { mutableStateOf(Formatters.editable(settings.defaultAlertDownPct)) }

    fun save(up: String, down: String) {
        val upValue = Formatters.parseDecimal(up)?.takeIf { it.signum() >= 0 } ?: return
        val downValue = Formatters.parseDecimal(down)?.takeIf { it.signum() >= 0 } ?: return
        viewModel.setDefaultAlerts(upValue, downValue)
    }

    SettingsHeader(stringResource(R.string.settings_alerts))
    MoneyCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SwitchRow(
            title = stringResource(R.string.settings_alerts_enabled),
            description = stringResource(R.string.settings_alerts_description),
            checked = settings.alertsEnabled,
            onCheckedChange = viewModel::setAlertsEnabled,
        )
        if (settings.alertsEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillDecimalField(
                    value = upText,
                    onValueChange = {
                        upText = it
                        save(it, downText)
                    },
                    label = stringResource(R.string.field_alert_up),
                    isError = Formatters.parseDecimal(upText)?.takeIf { it.signum() >= 0 } == null,
                    height = 48.dp,
                    modifier = Modifier.weight(1f),
                )
                PillDecimalField(
                    value = downText,
                    onValueChange = {
                        downText = it
                        save(upText, it)
                    },
                    label = stringResource(R.string.field_alert_down),
                    isError = Formatters.parseDecimal(downText)?.takeIf { it.signum() >= 0 } == null,
                    height = 48.dp,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                stringResource(R.string.alerts_zero_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp).offset(y = (-4).dp),
            )
            if (!canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Text(stringResource(R.string.settings_notifications_blocked), style = MaterialTheme.typography.bodyMedium, color = MoneyTheme.colors.loss)
                PillButton(
                    stringResource(R.string.action_allow_notifications),
                    onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    style = PillButtonStyle.Outlined,
                    height = 44.dp,
                )
            }
        }
    }
}

@Composable
private fun WidgetSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    var opacity by remember(state.settings.widgetOpacity) { mutableFloatStateOf(state.settings.widgetOpacity.toFloat()) }
    val themes = WidgetTheme.entries
    SettingsHeader(stringResource(R.string.settings_widget))
    MoneyCard(contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WidgetPreview(
            positions = state.positions.filter { it.instrument.onWidget },
            theme = state.settings.widgetTheme,
            opacityPercent = opacity.toInt(),
        )
        SmallLabel(stringResource(R.string.settings_widget_theme), Modifier.padding(top = 4.dp))
        SegmentedPills(
            options = themes.map {
                stringResource(
                    when (it) {
                        WidgetTheme.SYSTEM -> R.string.theme_system
                        WidgetTheme.LIGHT -> R.string.theme_light
                        WidgetTheme.DARK -> R.string.theme_dark
                    },
                )
            },
            selectedIndex = themes.indexOf(state.settings.widgetTheme),
            onSelect = { viewModel.setWidgetTheme(themes[it]) },
        )
        Row(Modifier.padding(top = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallLabel(stringResource(R.string.settings_widget_opacity), Modifier.weight(1f))
            Text("${opacity.toInt()}%", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = opacity,
            onValueChange = { opacity = it },
            onValueChangeFinished = { viewModel.setWidgetOpacity(opacity.toInt()) },
            valueRange = 0f..100f,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MoneyTheme.colors.switchTrackOff,
                activeTickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            ),
            modifier = Modifier.height(28.dp),
        )
        Column(Modifier.padding(top = 6.dp)) {
            SmallLabel(stringResource(R.string.settings_widget_instruments))
            Text(
                stringResource(R.string.settings_widget_order),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        if (state.instruments.isEmpty()) {
            Text(
                stringResource(R.string.settings_widget_no_instruments),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
        } else {
            Column(Modifier.fillMaxWidth().clip(InsetShape).background(MoneyTheme.colors.inset).padding(4.dp)) {
                state.instruments.forEachIndexed { index, instrument ->
                    WidgetInstrumentRow(
                        instrument = instrument,
                        canMoveUp = index > 0,
                        canMoveDown = index < state.instruments.lastIndex,
                        onToggle = { viewModel.setOnWidget(instrument.id, it) },
                        onMove = { up -> viewModel.move(instrument.id, up) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetInstrumentRow(
    instrument: Instrument,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onMove: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        MoneyCheckbox(instrument.onWidget, onToggle, contentDescription = instrument.symbol)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).alpha(if (instrument.onWidget) 1f else 0.55f)) {
            Text(instrument.symbol, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                instrument.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CircleIconButton(Lucide.ChevronUp, stringResource(R.string.action_move_up), { onMove(true) }, size = 40.dp, iconSize = 18.dp, enabled = canMoveUp)
        CircleIconButton(Lucide.ChevronDown, stringResource(R.string.action_move_down), { onMove(false) }, size = 40.dp, iconSize = 18.dp, enabled = canMoveDown)
    }
}

/** The 4×1 widget drawn on a flat "wallpaper", following theme and opacity as they change. */
@Composable
private fun WidgetPreview(positions: List<Position>, theme: WidgetTheme, opacityPercent: Int) {
    val appDark = MoneyTheme.colors.isDark
    val wall = if (appDark) {
        listOf(Color(0xFF3D472B), Color(0xFF56633F), Color(0xFF643312))
    } else {
        listOf(Color(0xFFCCDBB2), Color(0xFFF6A06B), Color(0xFFAEBF92))
    }
    val widgetDark = when (theme) {
        WidgetTheme.SYSTEM -> appDark
        WidgetTheme.LIGHT -> false
        WidgetTheme.DARK -> true
    }
    val colors = WidgetColors.of(widgetDark)
    val description = stringResource(R.string.settings_widget_preview)
    Box(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(InsetShape)
            .background(wall[0])
            .semantics { contentDescription = description },
    ) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = 30.dp, y = (-40).dp).size(130.dp).clip(CircleShape).background(wall[1]))
        Box(Modifier.align(Alignment.BottomStart).offset(x = (-20).dp, y = 60.dp).size(110.dp).clip(CircleShape).background(wall[2]))
        Row(
            Modifier
                .padding(start = 12.dp, top = 12.dp)
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredSize(328.dp, 72.dp)
                .graphicsLayer {
                    scaleX = 0.828f
                    scaleY = 0.828f
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                .background(colors.background.copy(alpha = opacityPercent / 100f))
                .padding(start = 18.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            positions.take(3).forEach { position ->
                Column(Modifier.weight(1f)) {
                    Text(
                        position.instrument.symbol,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        Formatters.pct(position.pct),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.tone(position.pct),
                        maxLines = 1,
                    )
                }
            }
            if (positions.isEmpty()) Spacer(Modifier.weight(1f))
            Icon(Lucide.RefreshCw, contentDescription = null, tint = colors.muted, modifier = Modifier.size(16.dp))
        }
    }
}

/** Per-app language: system, Polish or English. AppCompat stores the choice and recreates the screen. */
@Composable
private fun LanguageSection() {
    val tags = listOf("", "pl", "en")
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')
    SettingsHeader(stringResource(R.string.settings_language))
    MoneyCard(contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SegmentedPills(
            options = listOf(
                stringResource(R.string.theme_system),
                stringResource(R.string.language_polish),
                stringResource(R.string.language_english),
            ),
            selectedIndex = tags.indexOf(current).coerceAtLeast(0),
            onSelect = { index ->
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags[index]))
            },
        )
        Text(
            stringResource(R.string.settings_language_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun RefreshSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsHeader(stringResource(R.string.settings_refresh))
    MoneyCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.settings_refresh_description), style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val last = state.settings.lastRefreshAt
            val text = buildString {
                append(
                    when {
                        last == null -> stringResource(R.string.settings_never_refreshed)
                        isToday(last) -> stringResource(R.string.settings_last_refresh_today, Formatters.dateTime(last))
                        else -> stringResource(R.string.settings_last_refresh, Formatters.dateTime(last))
                    },
                )
                if (last != null && !state.settings.lastRefreshOk) append(" · ").append(stringResource(R.string.refresh_partial))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal),
                color = if (state.settings.lastRefreshOk) MaterialTheme.colorScheme.onSurfaceVariant else MoneyTheme.colors.loss,
                modifier = Modifier.weight(1f).padding(end = 10.dp),
            )
            PillButton(
                stringResource(if (state.refreshing) R.string.refreshing else R.string.action_refresh_now),
                onClick = viewModel::refreshNow,
                enabled = !state.refreshing,
                style = PillButtonStyle.Outlined,
                height = 44.dp,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                contentPadding = PaddingValues(horizontal = 18.dp),
            )
        }
    }
}

private fun isToday(instant: Instant): Boolean {
    val zone = ZoneId.systemDefault()
    return instant.atZone(zone).toLocalDate() == Instant.now().atZone(zone).toLocalDate()
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    SettingsHeader(stringResource(R.string.settings_about))
    MoneyCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_data_sources), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.settings_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.settings_version, version), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
