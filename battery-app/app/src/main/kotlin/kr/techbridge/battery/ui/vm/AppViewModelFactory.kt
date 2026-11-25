package kr.techbridge.battery.ui.vm

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kr.techbridge.battery.di.AppContainer
import kr.techbridge.battery.ui.MainViewModel
import kr.techbridge.battery.ui.chart.ChartViewModel
import kr.techbridge.battery.ui.home.HomeViewModel
import kr.techbridge.battery.ui.sessions.SessionDetailViewModel
import kr.techbridge.battery.ui.sessions.SessionsViewModel
import kr.techbridge.battery.ui.settings.SettingsViewModel

/**
 * 모든 ViewModel을 [AppContainer]로 만드는 팩토리. DI 프레임워크 없이 수동 주입한다.
 * 내비게이션 인자(세션 id)는 [createSavedStateHandle]로 받는다.
 */
fun appViewModelFactory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
    initializer { MainViewModel(container) }
    initializer { HomeViewModel(container) }
    initializer { ChartViewModel(container) }
    initializer { SessionsViewModel(container) }
    initializer { SessionDetailViewModel(container, createSavedStateHandle()) }
    initializer { SettingsViewModel(container) }
}
