package io.github.albertrzad18.moneytracker.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.albertrzad18.moneytracker.R
import io.github.albertrzad18.moneytracker.data.repo.AppSettings
import io.github.albertrzad18.moneytracker.data.repo.SettingsRepository
import io.github.albertrzad18.moneytracker.ui.history.HistoryScreen
import io.github.albertrzad18.moneytracker.ui.instrument.InstrumentScreen
import io.github.albertrzad18.moneytracker.ui.lot.LotEditorScreen
import io.github.albertrzad18.moneytracker.ui.portfolio.PortfolioScreen
import io.github.albertrzad18.moneytracker.ui.settings.SettingsScreen
import io.github.albertrzad18.moneytracker.ui.summary.SummaryScreen
import io.github.albertrzad18.moneytracker.work.Notifications
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
object PortfolioRoute

@Serializable
object SummaryRoute

@Serializable
object HistoryRoute

@Serializable
object SettingsRoute

@Serializable
data class InstrumentRoute(val instrumentId: Long, val currency: String)

/** New lot (search first), another lot of a known instrument ([instrumentId]), or an edit ([lotId]). */
@Serializable
data class LotEditorRoute(val lotId: Long = 0, val instrumentId: Long = 0, val currency: String? = null)

private enum class TopLevel(val route: Any, @StringRes val label: Int, val icon: ImageVector) {
    PORTFOLIO(PortfolioRoute, R.string.nav_portfolio, Icons.Outlined.AccountBalanceWallet),
    SUMMARY(SummaryRoute, R.string.nav_summary, Icons.Outlined.PieChart),
    HISTORY(HistoryRoute, R.string.nav_history, Icons.Outlined.History),
    SETTINGS(SettingsRoute, R.string.nav_settings, Icons.Outlined.Settings),
}

@HiltViewModel
class MainViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<AppSettings?> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onNotificationPromptShown() {
        viewModelScope.launch { settings.setNotificationPromptShown() }
    }
}

@Composable
fun MoneyTrackerNavHost(deepLinks: Flow<InstrumentRoute>) {
    val navController = rememberNavController()
    LaunchedEffect(navController) {
        deepLinks.collect { route -> navController.navigate(route) { launchSingleTop = true } }
    }
    NotificationPermissionPrompt()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBottomBar = TopLevel.entries.any { destination?.hasRoute(it.route::class) == true }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevel.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination?.hierarchy?.any { it.hasRoute(item.route::class) } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = PortfolioRoute,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            val openInstrument = { id: Long, currency: String -> navController.navigate(InstrumentRoute(id, currency)) }
            composable<PortfolioRoute> {
                PortfolioScreen(onAddLot = { navController.navigate(LotEditorRoute()) }, onOpenPosition = openInstrument)
            }
            composable<SummaryRoute> { SummaryScreen() }
            composable<HistoryRoute> { HistoryScreen(onOpenInstrument = openInstrument) }
            composable<SettingsRoute> { SettingsScreen() }
            composable<InstrumentRoute> {
                InstrumentScreen(
                    onBack = { navController.popBackStack() },
                    onAddLot = { id, currency -> navController.navigate(LotEditorRoute(instrumentId = id, currency = currency)) },
                    onEditLot = { lotId -> navController.navigate(LotEditorRoute(lotId = lotId)) },
                )
            }
            composable<LotEditorRoute> { LotEditorScreen(onDone = { navController.popBackStack() }) }
        }
    }
}

/** Asks once for the Android 13+ notification permission that threshold alerts need. */
@Composable
private fun NotificationPermissionPrompt(viewModel: MainViewModel = hiltViewModel()) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val current = settings ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(current.notificationPromptShown, current.alertsEnabled) {
        if (current.alertsEnabled && !current.notificationPromptShown && !Notifications.canNotify(context)) {
            viewModel.onNotificationPromptShown()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
