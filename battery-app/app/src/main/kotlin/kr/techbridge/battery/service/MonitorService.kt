package kr.techbridge.battery.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kr.techbridge.battery.BatteryApp
import kr.techbridge.battery.notify.Notifications

/**
 * 배터리 샘플링 포그라운드 서비스(specialUse).
 * 꽂기/뺄 때 즉시 샘플링하도록 POWER_CONNECTED/DISCONNECTED를 런타임 등록으로 받는다
 * (Android 8 이후 매니페스트 등록은 불가).
 */
class MonitorService : LifecycleService() {
    private lateinit var recorder: SessionRecorder
    private var loop: Job? = null

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // 루프를 깨워 즉시 샘플링하게 한다. 직접 sampleNow()를 부르면 루프와 겹쳐 두 번 샘플링하고 간격도 어긋난다.
            recorder.kick()
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        val container = BatteryApp.container(this)
        recorder = SessionRecorder(this, container)
        Notifications.cancel(this, Notifications.ID_RECOVERY)
        MonitorWatchdog.schedule(this)

        ServiceCompat.startForeground(
            this,
            Notifications.ID_ONGOING,
            Notifications.monitoring(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )

        ContextCompat.registerReceiver(
            this,
            powerReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        loop = lifecycleScope.launch { recorder.run() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        loop?.cancel()
        runCatching { unregisterReceiver(powerReceiver) }
        recorder.flushBlocking()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "kr.techbridge.battery.action.STOP"

        /** 같은 프로세스 안의 감시 작업이 서비스 생존을 확인하는 데 쓴다. 프로세스가 죽으면 당연히 false로 시작한다. */
        @Volatile
        var running: Boolean = false
            private set

        /** 백그라운드에서 부르면 Android 12+에서 ForegroundServiceStartNotAllowedException이 날 수 있다. 호출자가 처리한다. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
        }

        fun stop(context: Context) {
            MonitorWatchdog.cancel(context)
            context.startService(Intent(context, MonitorService::class.java).setAction(ACTION_STOP))
        }
    }
}
