package kr.techbridge.battery.ui.chart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kr.techbridge.battery.core.ChargingSession
import kr.techbridge.battery.core.Sample
import kr.techbridge.battery.di.AppContainer

/** 그래프 화면. 최근 샘플 버퍼를 선택한 시간 범위로 잘라 준다. */
class ChartViewModel(c: AppContainer) : ViewModel() {

    data class UiState(
        /** 선택한 범위 안의 샘플(시간순). */
        val shown: List<Sample> = emptyList(),
        val latest: Sample? = null,
        val session: ChargingSession? = null,
        val rangeIndex: Int = 0,
    )

    private val rangeIndex = MutableStateFlow(0)

    val uiState: StateFlow<UiState> = combine(c.live.recent, c.live.session, rangeIndex) { recent, session, idx ->
        val latest = recent.lastOrNull()
        val window = RANGES_MS[idx]
        val cutoff = if (latest == null || window == Long.MAX_VALUE) Long.MIN_VALUE else latest.timestampMs - window
        UiState(shown = recent.filter { it.timestampMs >= cutoff }, latest = latest, session = session, rangeIndex = idx)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun setRange(index: Int) {
        rangeIndex.value = index.coerceIn(0, RANGES_MS.lastIndex)
    }

    companion object {
        val RANGES_MS = listOf(60_000L, 600_000L, Long.MAX_VALUE)
        val RANGE_LABELS = listOf("60초", "10분", "전체")
    }
}
