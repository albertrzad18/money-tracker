package io.github.albertrzad18.moneytracker.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Redraws every placed widget after the data behind it changed. */
@Singleton
class WidgetUpdater @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun update() {
        PortfolioWidget().updateAll(context)
    }
}
