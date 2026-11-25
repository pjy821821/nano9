package kr.techbridge.battery.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kr.techbridge.battery.BatteryApp
import kr.techbridge.battery.notify.Notifications
import java.util.concurrent.TimeUnit

/**
 * 서비스 사망 감시. 충전 중일 때 15분마다 깨어나 [MonitorService]가 살아 있는지 본다.
 *
 * 죽어 있으면 먼저 직접 시작을 시도한다. Android 12+에서 백그라운드의 포그라운드 서비스 시작은 원칙적으로
 * 금지지만, 사용자가 이 앱의 배터리 최적화를 해제했으면 허용된다(설정 화면에서 안내). 금지 예외가 나면
 * "탭하여 다시 시작" 알림을 올린다. 알림 탭으로 보내는 PendingIntent는 예외 없이 허용된다.
 *
 * 사용자가 앱을 강제 종료했으면 안드로이드가 작업·알림·서비스를 모두 막으므로 여기서도 할 수 있는 일이 없다.
 */
class MonitorWatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val container = BatteryApp.container(ctx)
        if (!container.settings.current().monitoringEnabled) return Result.success()
        if (MonitorService.running) return Result.success()

        try {
            MonitorService.start(ctx)
            Notifications.cancel(ctx, Notifications.ID_RECOVERY)
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException(IllegalStateException의 하위)을 포함한다.
            Notifications.post(ctx, Notifications.ID_RECOVERY, Notifications.recovery(ctx))
        }
        return Result.success()
    }
}

object MonitorWatchdog {
    private const val WORK_NAME = "monitor-watchdog"
    const val PERIOD_MINUTES = 15L

    /** 모니터링을 켤 때 등록한다. 이미 있으면 그대로 둔다. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<MonitorWatchdogWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** 모니터링을 끌 때 해제한다. */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Notifications.cancel(context, Notifications.ID_RECOVERY)
    }
}
