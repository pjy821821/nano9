package kr.techbridge.battery

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kr.techbridge.battery.di.AppContainer
import kr.techbridge.battery.notify.Notifications
import kr.techbridge.battery.service.MonitorWatchdog

class BatteryApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.ensureChannels(this)
        CoroutineScope(Dispatchers.IO).launch {
            if (container.settings.current().monitoringEnabled) MonitorWatchdog.schedule(this@BatteryApp)
        }
    }

    companion object {
        fun container(context: Context): AppContainer = (context.applicationContext as BatteryApp).container
    }
}
