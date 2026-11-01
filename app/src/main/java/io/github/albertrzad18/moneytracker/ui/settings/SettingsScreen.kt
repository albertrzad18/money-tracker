package io.github.albertrzad18.moneytracker.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.albertrzad18.moneytracker.R
import io.github.albertrzad18.moneytracker.data.db.Instrument
import io.github.albertrzad18.moneytracker.data.db.isOpen
import io.github.albertrzad18.moneytracker.data.repo.AppSettings
import io.github.albertrzad18.moneytracker.data.repo.PortfolioRepository
import io.github.albertrzad18.moneytracker.data.repo.SettingsRepository
import io.github.albertrzad18.moneytracker.data.repo.WidgetTheme
import io.github.albertrzad18.moneytracker.ui.components.DecimalField
import io.github.albertrzad18.moneytracker.ui.components.SectionTitle
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
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings,
    /** Instruments with open lots, in widget order. */
    val instruments: List<Instrument>,
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
        combine(settings.settings, portfolio.instruments, portfolio.lots, refreshing) { s, instruments, lots, busy ->
            val active = lots.filter { it.isOpen }.map { it.instrumentId }.toSet()
            SettingsUiState(s, instruments.filter { it.id in active }, busy)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) }) { padding ->
        val current = state ?: return@Scaffold
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AlertsSection(current.settings, viewModel)
            WidgetSection(current, viewModel)
            RefreshSection(current, viewModel)
            AboutSection()
        }
    }
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

    SectionTitle(stringResource(R.string.settings_alerts))
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_alerts_enabled), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_alerts_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.alertsEnabled, onCheckedChange = viewModel::setAlertsEnabled)
            }
            if (settings.alertsEnabled) {
                Row {
                    DecimalField(
                        value = upText,
                        onValueChange = {
                            upText = it
                            save(it, downText)
                        },
                        label = stringResource(R.string.field_alert_up),
                        isError = Formatters.parseDecimal(upText)?.takeIf { it.signum() >= 0 } == null,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    DecimalField(
                        value = downText,
                        onValueChange = {
                            downText = it
                            save(upText, it)
                        },
                        label = stringResource(R.string.field_alert_down),
                        isError = Formatters.parseDecimal(downText)?.takeIf { it.signum() >= 0 } == null,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    stringResource(R.string.alerts_zero_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Text(stringResource(R.string.settings_notifications_blocked), color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text(stringResource(R.string.action_allow_notifications))
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    var opacity by remember(state.settings.widgetOpacity) { mutableFloatStateOf(state.settings.widgetOpacity.toFloat()) }
    SectionTitle(stringResource(R.string.settings_widget))
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_widget_theme), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                WidgetTheme.entries.forEachIndexed { index, theme ->
                    SegmentedButton(
                        selected = state.settings.widgetTheme == theme,
                        onClick = { viewModel.setWidgetTheme(theme) },
                        shape = SegmentedButtonDefaults.itemShape(index, WidgetTheme.entries.size),
                    ) {
                        Text(
                            stringResource(
                                when (theme) {
                                    WidgetTheme.SYSTEM -> R.string.theme_system
                                    WidgetTheme.LIGHT -> R.string.theme_light
                                    WidgetTheme.DARK -> R.string.theme_dark
                                },
                            ),
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.settings_widget_opacity, opacity.toInt()),
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = opacity,
                onValueChange = { opacity = it },
                onValueChangeFinished = { viewModel.setWidgetOpacity(opacity.toInt()) },
                valueRange = 0f..100f,
                steps = 9,
            )
            Text(stringResource(R.string.settings_widget_instruments), style = MaterialTheme.typography.labelLarge)
            if (state.instruments.isEmpty()) {
                Text(
                    stringResource(R.string.settings_widget_no_instruments),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.instruments.forEachIndexed { index, instrument ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = instrument.onWidget, onCheckedChange = { viewModel.setOnWidget(instrument.id, it) })
                    Column(Modifier.weight(1f)) {
                        Text(instrument.symbol, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            instrument.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { viewModel.move(instrument.id, up = true) }, enabled = index > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.action_move_up))
                    }
                    IconButton(onClick = { viewModel.move(instrument.id, up = false) }, enabled = index < state.instruments.lastIndex) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.action_move_down))
                    }
                }
            }
        }
    }
}

@Composable
private fun RefreshSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    SectionTitle(stringResource(R.string.settings_refresh))
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_refresh_description), style = MaterialTheme.typography.bodyMedium)
            val last = state.settings.lastRefreshAt
            Text(
                if (last == null) {
                    stringResource(R.string.settings_never_refreshed)
                } else {
                    stringResource(
                        if (state.settings.lastRefreshOk) R.string.updated_at else R.string.updated_at_failed,
                        Formatters.dateTime(last),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (state.settings.lastRefreshOk) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
            Button(onClick = viewModel::refreshNow, enabled = !state.refreshing) {
                Text(stringResource(if (state.refreshing) R.string.refreshing else R.string.action_refresh))
            }
        }
    }
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    SectionTitle(stringResource(R.string.settings_about))
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_data_sources), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.settings_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.settings_version, version),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
