package io.github.albertrzad18.moneytracker.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

enum class WidgetTheme { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val alertsEnabled: Boolean = true,
    val defaultAlertUpPct: BigDecimal = BigDecimal(20),
    val defaultAlertDownPct: BigDecimal = BigDecimal(10),
    val widgetTheme: WidgetTheme = WidgetTheme.SYSTEM,
    /** Widget background opacity in percent. */
    val widgetOpacity: Int = 90,
    val lastRefreshAt: Instant? = null,
    val lastRefreshOk: Boolean = true,
    val refreshStartedAt: Instant? = null,
    val notificationPromptShown: Boolean = false,
) {
    /** A refresh counts as running for at most two minutes, in case the process died mid-way. */
    fun isRefreshing(now: Instant): Boolean {
        val started = refreshStartedAt ?: return false
        val finished = lastRefreshAt
        return (finished == null || started > finished) && started.plusSeconds(120) > now
    }
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.settingsStore

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            alertsEnabled = prefs[ALERTS_ENABLED] ?: true,
            defaultAlertUpPct = prefs[ALERT_UP]?.toBigDecimalOrNull() ?: BigDecimal(20),
            defaultAlertDownPct = prefs[ALERT_DOWN]?.toBigDecimalOrNull() ?: BigDecimal(10),
            widgetTheme = prefs[WIDGET_THEME]?.let { name -> WidgetTheme.entries.find { it.name == name } }
                ?: WidgetTheme.SYSTEM,
            widgetOpacity = prefs[WIDGET_OPACITY] ?: 90,
            lastRefreshAt = prefs[LAST_REFRESH]?.let(Instant::ofEpochMilli),
            lastRefreshOk = prefs[LAST_REFRESH_OK] ?: true,
            refreshStartedAt = prefs[REFRESH_STARTED]?.let(Instant::ofEpochMilli),
            notificationPromptShown = prefs[NOTIFICATION_PROMPT_SHOWN] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setAlertsEnabled(enabled: Boolean) = store.edit { it[ALERTS_ENABLED] = enabled }

    suspend fun setDefaultAlerts(upPct: BigDecimal, downPct: BigDecimal) = store.edit {
        it[ALERT_UP] = upPct.toPlainString()
        it[ALERT_DOWN] = downPct.toPlainString()
    }

    suspend fun setWidgetTheme(theme: WidgetTheme) = store.edit { it[WIDGET_THEME] = theme.name }

    suspend fun setWidgetOpacity(percent: Int) = store.edit { it[WIDGET_OPACITY] = percent.coerceIn(0, 100) }

    suspend fun markRefreshStarted(at: Instant) = store.edit { it[REFRESH_STARTED] = at.toEpochMilli() }

    suspend fun clearRefreshStarted() = store.edit { it.remove(REFRESH_STARTED) }

    suspend fun markRefreshFinished(at: Instant, ok: Boolean) = store.edit {
        it[LAST_REFRESH] = at.toEpochMilli()
        it[LAST_REFRESH_OK] = ok
    }

    suspend fun setNotificationPromptShown() = store.edit { it[NOTIFICATION_PROMPT_SHOWN] = true }

    private companion object {
        val ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")
        val ALERT_UP = stringPreferencesKey("alert_up_pct")
        val ALERT_DOWN = stringPreferencesKey("alert_down_pct")
        val WIDGET_THEME = stringPreferencesKey("widget_theme")
        val WIDGET_OPACITY = intPreferencesKey("widget_opacity")
        val LAST_REFRESH = longPreferencesKey("last_refresh")
        val LAST_REFRESH_OK = booleanPreferencesKey("last_refresh_ok")
        val REFRESH_STARTED = longPreferencesKey("refresh_started")
        val NOTIFICATION_PROMPT_SHOWN = booleanPreferencesKey("notification_prompt_shown")
    }
}
