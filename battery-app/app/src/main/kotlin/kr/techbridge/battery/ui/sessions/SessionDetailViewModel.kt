package kr.techbridge.battery.ui.sessions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kr.techbridge.battery.data.db.SampleEntity
import kr.techbridge.battery.data.db.SessionEntity
import kr.techbridge.battery.di.AppContainer

/** 세션 상세. 세션 id는 내비게이션 인자 "id"에서 받는다. */
class SessionDetailViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val sessionId: Long = checkNotNull(handle[ARG_ID]) { "세션 id 인자가 없다" }

    data class UiState(
        val session: SessionEntity? = null,
        val samples: List<SampleEntity> = emptyList(),
        val chargerName: String? = null,
    )

    val uiState: StateFlow<UiState> = combine(
        c.repository.observeSession(sessionId),
        c.repository.observeSamples(sessionId),
        c.repository.observeChargers(),
    ) { session, samples, chargers ->
        UiState(session = session, samples = samples, chargerName = chargers.firstOrNull { it.id == session?.chargerId }?.name)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private val _deleted = MutableStateFlow(false)
    /** 삭제가 끝나면 true. 화면은 이것을 보고 뒤로 간다. */
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun nameCharger(name: String) {
        viewModelScope.launch {
            c.repository.nameChargerForSession(sessionId, name)?.let { c.settings.setLastCharger(it) }
        }
    }

    fun delete() {
        viewModelScope.launch {
            c.repository.deleteSession(sessionId)
            _deleted.value = true
        }
    }

    companion object {
        const val ARG_ID = "id"
    }
}
