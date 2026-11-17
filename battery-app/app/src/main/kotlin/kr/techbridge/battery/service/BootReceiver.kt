package kr.techbridge.battery.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kr.techbridge.battery.BatteryApp

/** 부팅 후 사용자가 모니터링을 켜 두었으면 서비스를 다시 올린다. specialUse 타입은 BOOT_COMPLETED에서 시작이 허용된다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (BatteryApp.container(context).settings.current().monitoringEnabled) {
                    MonitorService.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
