package kr.techbridge.battery.ui.sessions

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.techbridge.battery.R
import kr.techbridge.battery.core.CsvExport
import kr.techbridge.battery.core.SessionExportRow
import kr.techbridge.battery.data.db.SessionEntity
import kr.techbridge.battery.di.AppContainer
import kr.techbridge.battery.util.Format
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** 기록 화면. 세션 목록을 날짜별로 묶고 이번 주 요약을 만든다. */
class SessionsViewModel(private val c: AppContainer) : ViewModel() {

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** 화면이 토스트로 보여줄 한 줄 메시지. */
    val events: SharedFlow<String> = _events

    /** 모든 세션을 CSV로 [uri]에 쓴다. uri는 저장소 접근 프레임워크(파일 만들기 대화상자)에서 온다. */
    fun exportSessions(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val sessions = c.repository.allSessions()
                    val names = c.repository.chargerNames()
                    val csv = CsvExport.sessions(sessions.map { SessionExportRow(it.id, it.toCore(), names[it.chargerId], it.note) })
                    val out = c.appContext.contentResolver.openOutputStream(uri) ?: error("출력 스트림을 열 수 없다")
                    out.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                    sessions.size
                }
            }
            _events.tryEmit(
                result.fold(
                    { c.appContext.getString(R.string.export_done_sessions, it) },
                    { c.appContext.getString(R.string.export_failed) },
                ),
            )
        }
    }

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
