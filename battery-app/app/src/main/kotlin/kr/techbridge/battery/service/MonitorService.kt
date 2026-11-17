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
            lifecycleScope.launch { recorder.sampleNow() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val container = BatteryApp.container(this)
        recorder = SessionRecorder(this, container)

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
        loop?.cancel()
        runCatching { unregisterReceiver(powerReceiver) }
        recorder.flushBlocking()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "kr.techbridge.battery.action.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MonitorService::class.java).setAction(ACTION_STOP))
        }
    }
}
