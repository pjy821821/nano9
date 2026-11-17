package kr.techbridge.battery

import android.app.Application
import android.content.Context
import kr.techbridge.battery.di.AppContainer
import kr.techbridge.battery.notify.Notifications

class BatteryApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.ensureChannels(this)
    }

    companion object {
        fun container(context: Context): AppContainer = (context.applicationContext as BatteryApp).container
    }
}
