package io.github.albertrzad18.moneytracker.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.albertrzad18.moneytracker.R
import io.github.albertrzad18.moneytracker.data.repo.AppSettings
import io.github.albertrzad18.moneytracker.data.repo.SettingsRepository
import io.github.albertrzad18.moneytracker.ui.components.LocalSnackbar
import io.github.albertrzad18.moneytracker.ui.components.Lucide
import io.github.albertrzad18.moneytracker.ui.components.MoneySnackbarHost
import io.github.albertrzad18.moneytracker.ui.components.PillButton
import io.github.albertrzad18.moneytracker.ui.components.SnackbarController
import io.github.albertrzad18.moneytracker.ui.history.HistoryScreen
import io.github.albertrzad18.moneytracker.ui.instrument.InstrumentScreen
import io.github.albertrzad18.moneytracker.ui.lot.LotEditorScreen
import io.github.albertrzad18.moneytracker.ui.portfolio.PortfolioScreen
import io.github.albertrzad18.moneytracker.ui.portfolio.PortfolioViewModel
import io.github.albertrzad18.moneytracker.ui.settings.SettingsScreen
import io.github.albertrzad18.moneytracker.ui.summary.SummaryScreen
import io.github.albertrzad18.moneytracker.ui.theme.Durations
import io.github.albertrzad18.moneytracker.ui.theme.Pill
import io.github.albertrzad18.moneytracker.ui.theme.emphasized
import io.github.albertrzad18.moneytracker.work.Notifications
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

/** The four tabs with the navigation bar. */
@Serializable
object MainRoute

@Serializable
data class InstrumentRoute(val instrumentId: Long, val currency: String)

/** New lot (search first), another lot of a known instrument ([instrumentId]), or an edit ([lotId]). */
@Serializable
data class LotEditorRoute(val lotId: Long = 0, val instrumentId: Long = 0, val currency: String? = null)

private enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    PORTFOLIO(R.string.nav_portfolio, Lucide.Wallet),
    SUMMARY(R.string.nav_summary, Lucide.ChartPie),
    HISTORY(R.string.nav_history, Lucide.History),
    SETTINGS(R.string.nav_settings, Lucide.Settings),
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

    val snackbar = remember { SnackbarController() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val onMain = backStackEntry?.destination?.hasRoute(MainRoute::class) != false
    val parallax = with(LocalDensity.current) { 56.dp.roundToPx() }
    val push = emphasized<androidx.compose.ui.unit.IntOffset>(Durations.SCREEN_PUSH)

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // Detail screens slide in from the right while the screen below shifts 56 dp left.
            NavHost(
                navController = navController,
                startDestination = MainRoute,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { slideInHorizontally(push) { (it * 1.04f).toInt() } },
                exitTransition = { slideOutHorizontally(push) { -parallax } },
                popEnterTransition = { slideInHorizontally(push) { -parallax } },
                popExitTransition = { slideOutHorizontally(push) { (it * 1.04f).toInt() } },
            ) {
                val openInstrument = { id: Long, currency: String -> navController.navigate(InstrumentRoute(id, currency)) }
                composable<MainRoute> {
                    MainTabs(onAddLot = { navController.navigate(LotEditorRoute()) }, onOpenInstrument = openInstrument)
                }
                composable<InstrumentRoute> {
                    InstrumentScreen(
                        onBack = { navController.popBackStack() },
                        onAddLot = { id, currency -> navController.navigate(LotEditorRoute(instrumentId = id, currency = currency)) },
                        onEditLot = { lotId -> navController.navigate(LotEditorRoute(lotId = lotId)) },
                    )
                }
                composable<LotEditorRoute> { LotEditorScreen(onDone = { navController.popBackStack() }) }
            }
            val bottom by animateDpAsState(if (onMain) 96.dp else 28.dp, emphasized(Durations.SCREEN_PUSH), label = "snackbar")
            MoneySnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = bottom),
            )
        }
    }
}

@Composable
private fun MainTabs(onAddLot: () -> Unit, onOpenInstrument: (Long, String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.PORTFOLIO) }
    BackHandler(enabled = tab != Tab.PORTFOLIO) { tab = Tab.PORTFOLIO }
    val tabStates = rememberSaveableStateHolder()
    val portfolio: PortfolioViewModel = hiltViewModel()
    val portfolioState by portfolio.state.collectAsStateWithLifecycle()
    val slide = with(LocalDensity.current) { 12.dp.roundToPx() }
    val fabSlide = with(LocalDensity.current) { 20.dp.roundToPx() }
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barHeight = 66.dp + max(systemBottom, 14.dp)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    (fadeIn(emphasized(Durations.TAB_FADE)) + slideInVertically(emphasized(Durations.TAB_SLIDE)) { slide }) togetherWith
                        fadeOut(tween(Durations.TAB_FADE / 2))
                },
                label = "tabs",
            ) { current ->
                tabStates.SaveableStateProvider(current.name) {
                    when (current) {
                        Tab.PORTFOLIO -> PortfolioScreen(onAddLot = onAddLot, onOpenPosition = onOpenInstrument, viewModel = portfolio)
                        Tab.SUMMARY -> SummaryScreen()
                        Tab.HISTORY -> HistoryScreen(onOpenInstrument = onOpenInstrument)
                        Tab.SETTINGS -> SettingsScreen()
                    }
                }
            }
            MoneyNavigationBar(selected = tab, onSelect = { tab = it }, height = barHeight)
        }

        // The extended FAB lives only on a non-empty Portfolio; the empty state has its own button.
        AnimatedVisibility(
            visible = tab == Tab.PORTFOLIO && portfolioState.loaded && portfolioState.positions.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = barHeight + 16.dp),
            enter = fadeIn(emphasized(Durations.FAB)) +
                scaleIn(emphasized(Durations.FAB), initialScale = 0.8f) +
                slideInVertically(emphasized(Durations.FAB)) { fabSlide },
            exit = fadeOut(emphasized(Durations.FAB / 2)) + scaleOut(emphasized(Durations.FAB / 2), targetScale = 0.8f) +
                slideOutVertically(emphasized(Durations.FAB / 2)) { fabSlide },
        ) {
            PillButton(
                text = stringResource(R.string.action_add_lot),
                onClick = onAddLot,
                icon = Lucide.Plus,
                height = 56.dp,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 22.dp),
                elevated = true,
            )
        }
    }
}

/** 80 dp bar on the surface color with a 60×32 pill indicator behind the active icon. */
@Composable
private fun MoneyNavigationBar(selected: Tab, onSelect: (Tab) -> Unit, height: androidx.compose.ui.unit.Dp) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(scheme.surface)
            .navigationBarsPadding()
            .selectableGroup(),
    ) {
        Tab.entries.forEach { item ->
            val active = item == selected
            val indicatorWidth by animateDpAsState(if (active) 60.dp else 32.dp, emphasized(Durations.TAB_SLIDE), label = "indicator")
            val indicator by animateColorAsState(
                if (active) scheme.secondaryContainer else Color.Transparent,
                emphasized(Durations.TAB_FADE),
                label = "indicatorColor",
            )
            val tint by animateColorAsState(
                if (active) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                emphasized(Durations.TAB_FADE),
                label = "navTint",
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(item) })
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(width = indicatorWidth, height = 32.dp).clip(Pill).background(indicator), contentAlignment = Alignment.Center) {
                    Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(item.label),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) scheme.onSurface else scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
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
    val context = LocalContext.current
    LaunchedEffect(current.notificationPromptShown, current.alertsEnabled) {
        if (current.alertsEnabled && !current.notificationPromptShown && !Notifications.canNotify(context)) {
            viewModel.onNotificationPromptShown()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
