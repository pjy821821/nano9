package kr.techbridge.battery.di

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.techbridge.battery.core.Calibration
import kr.techbridge.battery.core.ChargingSession
import kr.techbridge.battery.core.Sample
import kr.techbridge.battery.data.battery.BatteryReader
import kr.techbridge.battery.data.db.BatteryDatabase
import kr.techbridge.battery.data.repo.SessionRepository
import kr.techbridge.battery.data.settings.SettingsStore

/** 수동 DI. 앱 규모가 작아 DI 프레임워크를 쓰지 않는다. */
class AppContainer(context: Context) {
    /** 서비스 시작처럼 ViewModel이 일으키는 부수 효과용. 액티비티 컨텍스트를 잡지 않도록 application 컨텍스트만 둔다. */
    val appContext: Context = context.applicationContext
    val db: BatteryDatabase = BatteryDatabase.build(context)
    val settings = SettingsStore(context)
    val batteryReader = BatteryReader(context)
    val repository = SessionRepository(db.sessionDao(), db.sampleDao(), db.chargerDao())
    val live = LiveBatteryState()
}

/** 서비스가 갱신하고 UI와 위젯이 구독하는 실시간 상태. 프로세스 안에서만 산다. */
class LiveBatteryState {
    private val _sample = MutableStateFlow<Sample?>(null)
    val sample: StateFlow<Sample?> = _sample.asStateFlow()

    private val _session = MutableStateFlow<ChargingSession?>(null)
    val session: StateFlow<ChargingSession?> = _session.asStateFlow()

    private val _sessionId = MutableStateFlow<Long?>(null)
    val sessionId: StateFlow<Long?> = _sessionId.asStateFlow()

    private val _calibration = MutableStateFlow(Calibration.SPEC)
    val calibration: StateFlow<Calibration> = _calibration.asStateFlow()

    /** 최근 10분 샘플 링 버퍼(그래프용). 1초 간격이면 600개. */
    private val _recent = MutableStateFlow<List<Sample>>(emptyList())
    val recent: StateFlow<List<Sample>> = _recent.asStateFlow()

    fun publish(sample: Sample, session: ChargingSession?, sessionId: Long?) {
        _sample.value = sample
        _session.value = session
        _sessionId.value = sessionId
        _recent.value = (_recent.value + sample).takeLast(RECENT_CAPACITY)
    }

    fun setCalibration(c: Calibration) { _calibration.value = c }

    /** 화면(홈 ViewModel 등)이 실시간 샘플을 구독 중인가. 뺀 상태의 샘플링 간격을 정할 때 쓴다. */
    val uiWatching: Boolean get() = _sample.subscriptionCount.value > 0

    companion object { const val RECENT_CAPACITY = 600 }
}
