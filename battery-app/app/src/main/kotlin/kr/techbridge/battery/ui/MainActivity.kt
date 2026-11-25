package kr.techbridge.battery.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.techbridge.battery.BatteryApp
import kr.techbridge.battery.service.MonitorService
import kr.techbridge.battery.ui.nav.AppNav
import kr.techbridge.battery.ui.theme.BatteryTheme
import kr.techbridge.battery.ui.vm.appViewModelFactory

class MainActivity : ComponentActivity() {
    private var pendingRoute by mutableStateOf<String?>(null)
    private val viewModelFactory by lazy { appViewModelFactory(BatteryApp.container(this)) }
    private val viewModel: MainViewModel by viewModels { viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute = intent?.getStringExtra(EXTRA_ROUTE)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            BatteryTheme(mode = settings.theme, dynamicColor = settings.dynamicColor) {
                NotificationPermissionRequest()
                AppNav(
                    viewModelFactory = viewModelFactory,
                    requestedRoute = pendingRoute,
                    onRouteConsumed = { pendingRoute = null },
                )
            }
            LaunchedEffect(settings.monitoringEnabled) {
                if (settings.monitoringEnabled) MonitorService.start(this@MainActivity)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingRoute = intent.getStringExtra(EXTRA_ROUTE)
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        const val ROUTE_CHART = "chart"
    }
}

@Composable
private fun NotificationPermissionRequest() {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val context = androidx.compose.ui.platform.LocalContext.current
    val asked = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted && !asked.value) {
            asked.value = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
