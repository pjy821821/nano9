package kr.techbridge.battery.ui.sessions

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.techbridge.battery.R
import kr.techbridge.battery.core.CsvExport
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

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** 화면이 토스트로 보여줄 한 줄 메시지. */
    val events: SharedFlow<String> = _events

    /** 이 세션의 샘플(종료된 세션은 10초 간격으로 줄어든 것)을 CSV로 [uri]에 쓴다. */
    fun exportSamples(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val samples = c.repository.samplesOf(sessionId).map { it.toCore() }
                    val csv = CsvExport.samples(samples)
                    val out = c.appContext.contentResolver.openOutputStream(uri) ?: error("출력 스트림을 열 수 없다")
                    out.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                    samples.size
                }
            }
            _events.tryEmit(
                result.fold(
                    { c.appContext.getString(R.string.export_done_samples, it) },
                    { c.appContext.getString(R.string.export_failed) },
                ),
            )
        }
    }

    companion object {
        const val ARG_ID = "id"
    }
}
