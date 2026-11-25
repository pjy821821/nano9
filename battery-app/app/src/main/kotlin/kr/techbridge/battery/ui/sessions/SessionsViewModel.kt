package kr.techbridge.battery.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kr.techbridge.battery.data.db.SessionEntity
import kr.techbridge.battery.di.AppContainer
import kr.techbridge.battery.util.Format
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** 기록 화면. 세션 목록을 날짜별로 묶고 이번 주 요약을 만든다. */
class SessionsViewModel(c: AppContainer) : ViewModel() {

    data class UiState(
        val sessions: List<SessionEntity> = emptyList(),
        val chargerNames: Map<Long, String> = emptyMap(),
        val thisWeek: List<SessionEntity> = emptyList(),
        /** 시작 날짜별 세션(최근 날짜부터). */
        val byDate: Map<LocalDate, List<SessionEntity>> = emptyMap(),
        val today: LocalDate = LocalDate.now(),
    )

    val uiState: StateFlow<UiState> = combine(c.repository.observeSessions(), c.repository.observeChargers()) { sessions, chargers ->
        val today = LocalDate.now()
        val weekStart = today.with(DayOfWeek.MONDAY).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        UiState(
            sessions = sessions,
            chargerNames = chargers.associate { it.id to it.name },
            thisWeek = sessions.filter { it.startMs >= weekStart },
            byDate = sessions.groupBy { Format.localDate(it.startMs) },
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())
}
