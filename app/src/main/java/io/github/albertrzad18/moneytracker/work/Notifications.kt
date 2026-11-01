package io.github.albertrzad18.moneytracker.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.albertrzad18.moneytracker.MainActivity
import io.github.albertrzad18.moneytracker.R
import io.github.albertrzad18.moneytracker.data.db.Lot
import io.github.albertrzad18.moneytracker.domain.Position
import io.github.albertrzad18.moneytracker.util.Formatters
import java.math.BigDecimal

object Notifications {
    private const val CHANNEL_ALERTS = "alerts"

    fun createChannels(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ALERTS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notification_channel_alerts))
                .setDescription(context.getString(R.string.notification_channel_alerts_description))
                .build(),
        )
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // Checked by canNotify().
    fun showAlert(
        context: Context,
        position: Position,
        lot: Lot,
        pct: BigDecimal,
        threshold: BigDecimal,
        rising: Boolean,
    ) {
        if (!canNotify(context)) return
        val thresholdText = Formatters.quantity(threshold)
        val text = context.getString(
            if (rising) R.string.alert_up_text else R.string.alert_down_text,
            Formatters.date(lot.buyDate),
            Formatters.money(lot.buyPrice, lot.currency),
            thresholdText,
        )
        val intent = MainActivity.instrumentIntent(context, position.instrument.id, position.currency)
        val pendingIntent = PendingIntent.getActivity(
            context,
            lot.id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_trend)
            .setContentTitle("${position.instrument.symbol}  ${Formatters.pct(pct)}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        // Separate ids per lot and direction, so a rise and a fall alert never replace each other.
        val id = (lot.id * 2 + if (rising) 0 else 1).toInt()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
