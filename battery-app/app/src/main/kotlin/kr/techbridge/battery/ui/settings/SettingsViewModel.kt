package kr.techbridge.battery.ui.settings

import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.techbridge.battery.core.HealthEstimator
import kr.techbridge.battery.data.battery.DeviceBatteryInfo
import kr.techbridge.battery.data.settings.Settings
import kr.techbridge.battery.data.settings.ThemeMode
import kr.techbridge.battery.di.AppContainer
import kr.techbridge.battery.service.MonitorService

/** 설정 화면. 설정 저장과 서비스 시작·중지 같은 부수 효과를 여기서 처리한다. */
class SettingsViewModel(private val c: AppContainer) : ViewModel() {

    val settings: StateFlow<Settings> = c.settings.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    /** OS가 주는 기기 배터리 정보. 화면을 열 때 한 번 읽는다. */
    val device: StateFlow<DeviceBatteryInfo?> = flow { emit(withContext(Dispatchers.IO) { c.batteryReader.deviceInfo() }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 건강도 추정. 설계 용량이 바뀌거나 세션이 추가·삭제되면 다시 계산한다. */
    val health: StateFlow<HealthEstimator.Estimate?> = combine(
        c.settings.flow.map { it.designCapacityMah }.distinctUntilChanged(),
        c.repository.observeSessions(),
    ) { capacity, _ -> capacity }
        .map { capacity -> HealthEstimator.estimate(c.repository.recentCompleted(50), capacity) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _batteryOptimizationIgnored = MutableStateFlow(readBatteryOptimization())
    /** 이 앱이 배터리 최적화에서 제외됐는지. 제외돼야 감시 작업이 백그라운드에서 서비스를 되살릴 수 있다. */
    val batteryOptimizationIgnored: StateFlow<Boolean> = _batteryOptimizationIgnored.asStateFlow()

    /** 시스템 설정에서 돌아올 때 다시 읽는다. */
    fun refreshBatteryOptimization() {
        _batteryOptimizationIgnored.value = readBatteryOptimization()
    }

    private fun readBatteryOptimization(): Boolean =
        c.appContext.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.appContext.packageName)

    fun setChargeLimit(percent: Int) = launch { c.settings.setChargeLimit(percent) }
    fun setChargeLimitEnabled(v: Boolean) = launch { c.settings.setChargeLimitEnabled(v) }
    fun setTempWarnEnabled(v: Boolean) = launch { c.settings.setTempWarnEnabled(v) }
    fun setOngoingNotification(v: Boolean) = launch { c.settings.setOngoingNotification(v) }
    fun setTheme(mode: ThemeMode) = launch { c.settings.setTheme(mode) }
    fun setDynamicColor(v: Boolean) = launch { c.settings.setDynamicColor(v) }
    fun setDesignCapacity(mah: Int?) = launch { c.settings.setDesignCapacity(mah) }

    /** 저장된 보정값을 지워 서비스가 다시 추론하게 한다. */
    fun recalibrate() = launch { c.settings.setCalibration(null) }

    fun setMonitoringEnabled(v: Boolean) = launch {
        c.settings.setMonitoringEnabled(v)
        if (v) MonitorService.start(c.appContext) else MonitorService.stop(c.appContext)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
