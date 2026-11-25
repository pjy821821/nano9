package kr.techbridge.battery.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kr.techbridge.battery.R
import kr.techbridge.battery.ui.chart.ChartScreen
import kr.techbridge.battery.ui.home.HomeScreen
import kr.techbridge.battery.ui.sessions.SessionDetailScreen
import kr.techbridge.battery.ui.sessions.SessionDetailViewModel
import kr.techbridge.battery.ui.sessions.SessionsScreen
import kr.techbridge.battery.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val CHART = "chart"
    const val SESSIONS = "sessions"
    const val SESSION = "session/{id}"
    const val SETTINGS = "settings"
    fun session(id: Long) = "session/$id"
}

private data class TopLevel(val route: String, val labelRes: Int, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    TopLevel(Routes.CHART, R.string.nav_chart, Icons.AutoMirrored.Filled.ShowChart),
    TopLevel(Routes.SESSIONS, R.string.nav_sessions, Icons.Filled.History),
    TopLevel(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

@Composable
fun AppNav(viewModelFactory: ViewModelProvider.Factory, requestedRoute: String?, onRouteConsumed: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = topLevel.any { it.route == currentRoute }

    // 탭, 홈의 톱니바퀴·그래프 카드, 알림 라우트 모두 같은 "탭 전환" 규칙으로 다룬다. 시작 목적지 위에 그냥
    // 쌓으면(launchSingleTop만) 백스택과 saveState/restoreState가 꼬여 탭을 눌러도 화면이 안 바뀌는 경우가 생긴다.
    val navigateTopLevel: (String) -> Unit = { route ->
        if (currentRoute != route) {
            nav.navigate(route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    LaunchedEffect(requestedRoute) {
        if (requestedRoute != null) {
            navigateTopLevel(requestedRoute)
            onRouteConsumed()
        }
    }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    topLevel.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = { navigateTopLevel(item.route) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            // 각 목적지의 ViewModel은 그 백스택 항목에 묶인다. 탭을 오가도 상태가 유지되고, 화면이 사라지면 정리된다.
            composable(Routes.HOME) {
                HomeScreen(
                    vm = viewModel(factory = viewModelFactory),
                    onOpenChart = { navigateTopLevel(Routes.CHART) },
                    onOpenSettings = { navigateTopLevel(Routes.SETTINGS) },
                )
            }
            composable(Routes.CHART) { ChartScreen(vm = viewModel(factory = viewModelFactory)) }
            composable(Routes.SESSIONS) {
                SessionsScreen(vm = viewModel(factory = viewModelFactory), onOpenSession = { id -> nav.navigate(Routes.session(id)) })
            }
            composable(Routes.SESSION, arguments = listOf(navArgument(SessionDetailViewModel.ARG_ID) { type = NavType.LongType })) {
                // 세션 id는 SessionDetailViewModel이 SavedStateHandle에서 읽는다.
                SessionDetailScreen(vm = viewModel(factory = viewModelFactory), onBack = { nav.popBackStack() })
            }
            composable(Routes.SETTINGS) { SettingsScreen(vm = viewModel(factory = viewModelFactory)) }
        }
    }
}
