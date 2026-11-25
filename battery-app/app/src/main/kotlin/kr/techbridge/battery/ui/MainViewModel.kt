package kr.techbridge.battery.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kr.techbridge.battery.data.settings.Settings
import kr.techbridge.battery.di.AppContainer

/** 액티비티 범위. 테마와 모니터링 설정만 본다. */
class MainViewModel(c: AppContainer) : ViewModel() {
    val settings: StateFlow<Settings> = c.settings.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())
}
