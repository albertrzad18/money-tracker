package io.github.albertrzad18.moneytracker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.albertrzad18.moneytracker.ui.InstrumentRoute
import io.github.albertrzad18.moneytracker.ui.MoneyTrackerNavHost
import io.github.albertrzad18.moneytracker.ui.theme.MoneyTrackerTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    /** Instrument screens requested by the widget or a notification. */
    private val deepLinks = Channel<InstrumentRoute>(Channel.BUFFERED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            MoneyTrackerTheme {
                MoneyTrackerNavHost(deepLinks = deepLinks.receiveAsFlow())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val instrumentId = intent?.getLongExtra(EXTRA_INSTRUMENT_ID, 0L) ?: 0L
        val currency = intent?.getStringExtra(EXTRA_CURRENCY)
        if (instrumentId != 0L && currency != null) deepLinks.trySend(InstrumentRoute(instrumentId, currency))
    }

    companion object {
        private const val EXTRA_INSTRUMENT_ID = "instrument_id"
        private const val EXTRA_CURRENCY = "currency"

        fun launchIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        fun instrumentIntent(context: Context, instrumentId: Long, currency: String): Intent =
            Intent(context, MainActivity::class.java)
                // A distinct URI per instrument keeps the PendingIntents from replacing each other.
                .setData(Uri.parse("moneytracker://instrument/$instrumentId/$currency"))
                .putExtra(EXTRA_INSTRUMENT_ID, instrumentId)
                .putExtra(EXTRA_CURRENCY, currency)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
