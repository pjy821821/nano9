package kr.techbridge.battery.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.techbridge.battery.core.ChargingSession
import kr.techbridge.battery.core.Sample
import kr.techbridge.battery.di.AppContainer

/** 홈 화면. 서비스가 올리는 실시간 상태를 화면용 상태로 묶는다. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer) : ViewModel() {

    data class UiState(
        val sample: Sample? = null,
        val session: ChargingSession? = null,
        val sessionId: Long? = null,
        val recent: List<Sample> = emptyList(),
        /** OS가 예상한 완충까지 남은 시간. 꽂혀 있지 않거나 모르면 null. */
        val remainingMs: Long? = null,
        val chargerName: String? = null,
    )

    /** 서비스가 아직 샘플을 내놓지 않았으면 직접 한 번 읽는다. 보정이 바뀌면 다시 읽는다. */
    private val fallback: Flow<Sample?> = c.live.calibration.map { cal ->
        withContext(Dispatchers.IO) { c.batteryReader.read()?.let(cal::normalize) }
    }

    private val sample: Flow<Sample?> = combine(c.live.sample, fallback) { live, fb -> live ?: fb }

    /** 꽂혀 있는 동안 30초마다 OS의 완충 예상 시간을 다시 묻는다. */
    private val remaining: Flow<Long?> = sample.map { it?.plugged == true }.distinctUntilChanged()
        .flatMapLatest { plugged ->
            if (!plugged) flowOf(null)
            else flow {
                while (true) {
                    emit(withContext(Dispatchers.IO) { c.batteryReader.deviceInfo().chargeTimeRemainingMs })
                    delay(REMAINING_REFRESH_MS)
                }
            }
        }

    /** 진행 중 세션의 충전기 이름. 세션에 충전기가 연결되거나 이름이 바뀌면 바로 따라온다. */
    private val chargerName: Flow<String?> = c.live.sessionId.flatMapLatest { id ->
        if (id == null) flowOf(null)
        else combine(c.repository.observeSession(id), c.repository.observeChargers()) { s, chargers ->
            chargers.firstOrNull { it.id == s?.chargerId }?.name
        }
    }

    val uiState: StateFlow<UiState> = combine(
        sample, c.live.session, c.live.sessionId, c.live.recent,
        combine(remaining, chargerName) { r, n -> r to n },
    ) { s, session, id, recent, (remainingMs, name) ->
        UiState(sample = s, session = session, sessionId = id, recent = recent, remainingMs = remainingMs, chargerName = name)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    /** 진행 중 세션의 충전기에 이름을 붙인다(없으면 새로 만든다). 세션이 없으면 무시. */
    fun nameCharger(name: String) {
        val sessionId = uiState.value.sessionId ?: return
        viewModelScope.launch {
            c.repository.nameChargerForSession(sessionId, name)?.let { c.settings.setLastCharger(it) }
        }
    }

    companion object {
        const val REMAINING_REFRESH_MS = 30_000L
    }
}
