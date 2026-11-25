package kr.techbridge.battery.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kr.techbridge.battery.R
import kr.techbridge.battery.core.ChargingSession
import kr.techbridge.battery.core.Sample
import kr.techbridge.battery.data.battery.Remaining
import kr.techbridge.battery.data.battery.RemainingKind
import kr.techbridge.battery.service.MonitorService
import kr.techbridge.battery.ui.MainActivity
import kr.techbridge.battery.util.Format

object Notifications {
    const val CHANNEL_MONITOR = "monitor"
    const val CHANNEL_ALERTS = "alerts"
    const val ID_ONGOING = 1
    const val ID_LIMIT = 2
    const val ID_TEMP = 3
    const val ID_RECOVERY = 4

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITOR, context.getString(R.string.channel_monitor), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_monitor_desc)
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_alerts_desc)
            },
        )
    }

    fun canPost(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun post(context: Context, id: Int, notification: Notification) {
        if (!canPost(context)) return
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    /** 첫 샘플 전에 startForeground에 넣는 자리 표시 알림. */
    fun monitoring(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setContentTitle(context.getString(R.string.notif_monitoring))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp(context, null))
            .build()

    fun ongoing(context: Context, s: Sample, session: ChargingSession?, remaining: Remaining?, chargerName: String?): Notification {
        val b = NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context, null))
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        if (s.plugged) {
            val parts = ArrayList<String>(4)
            remaining?.takeIf { it.kind == RemainingKind.TO_FULL }?.let { parts += context.getString(R.string.time_to_full, Format.duration(it.ms)) }
            parts += "${Format.milliamps(s.currentUa)} mA"
            parts += "${Format.celsius(s.temperatureDeciC)} °C"
            chargerName?.let { parts += it }
            // PC USB처럼 공급이 소비보다 작으면 꽂혀 있어도 순전류가 음수다. "-1.6 W로 충전 중"이라 쓰지 않는다.
            val title = if (s.currentUa < 0) {
                context.getString(R.string.notif_deficit_title, Format.watts(-s.powerMw), s.levelPercent)
            } else {
                context.getString(R.string.notif_charging_title, Format.watts(s.powerMw), s.levelPercent)
            }
            b.setContentTitle(title)
                .setContentText(parts.joinToString(" · "))
                .setProgress(100, s.levelPercent, false)
                .addAction(0, context.getString(R.string.action_open_chart), openApp(context, MainActivity.ROUTE_CHART))
            session?.let {
                b.setSubText("${Format.time(it.startMs)} ~ · ${Format.mah(it.chargedMicroAmpHours)} mAh")
            }
        } else {
            val parts = ArrayList<String>(3)
            remaining?.takeIf { it.kind == RemainingKind.TO_EMPTY }?.let { parts += context.getString(R.string.time_to_empty, Format.duration(it.ms)) }
            parts += "${Format.milliamps(s.currentUa)} mA"
            parts += "${Format.celsius(s.temperatureDeciC)} °C"
            b.setContentTitle(context.getString(R.string.notif_discharging_title, s.levelPercent, context.getString(R.string.state_discharging)))
                .setContentText(parts.joinToString(" · "))
        }
        return b.build()
    }

    fun chargeLimit(context: Context, percent: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setContentTitle(context.getString(R.string.notif_limit_title, percent))
            .setContentText(context.getString(R.string.notif_limit_text))
            .setAutoCancel(true)
            .setContentIntent(openApp(context, null))
            .build()

    /**
     * 서비스가 죽어 있고 백그라운드에서 되살릴 수 없을 때. 알림을 탭하면 서비스를 직접 시작한다.
     * 알림 탭으로 보내는 PendingIntent는 백그라운드 FGS 시작 제한의 예외다.
     */
    fun recovery(context: Context): Notification {
        val restart = PendingIntent.getForegroundService(
            context, 0, Intent(context, MonitorService::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setContentTitle(context.getString(R.string.notif_recovery_title))
            .setContentText(context.getString(R.string.notif_recovery_text))
            .setAutoCancel(true)
            .setContentIntent(restart)
            .build()
    }

    fun temperature(context: Context, deciC: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setContentTitle(context.getString(R.string.notif_temp_title, Format.celsius(deciC)))
            .setContentText(context.getString(R.string.notif_temp_text))
            .setAutoCancel(true)
            .setContentIntent(openApp(context, null))
            .build()

    private fun openApp(context: Context, route: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            route?.let { putExtra(MainActivity.EXTRA_ROUTE, it) }
        }
        val requestCode = route?.hashCode() ?: 0
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
