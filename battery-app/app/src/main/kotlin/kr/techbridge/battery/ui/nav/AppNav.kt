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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kr.techbridge.battery.R
import kr.techbridge.battery.di.AppContainer
import kr.techbridge.battery.ui.chart.ChartScreen
import kr.techbridge.battery.ui.home.HomeScreen
import kr.techbridge.battery.ui.sessions.SessionDetailScreen
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
fun AppNav(container: AppContainer, requestedRoute: String?, onRouteConsumed: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = topLevel.any { it.route == currentRoute }

    LaunchedEffect(requestedRoute) {
        if (requestedRoute != null) {
            nav.navigate(requestedRoute) { launchSingleTop = true }
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
                            onClick = {
                                nav.navigate(item.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) {
                HomeScreen(
                    container = container,
                    onOpenChart = { nav.navigate(Routes.CHART) { launchSingleTop = true } },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                )
            }
            composable(Routes.CHART) { ChartScreen(container) }
            composable(Routes.SESSIONS) {
                SessionsScreen(container, onOpenSession = { id -> nav.navigate(Routes.session(id)) })
            }
            composable(Routes.SESSION, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                SessionDetailScreen(container, sessionId = id, onBack = { nav.popBackStack() })
            }
            composable(Routes.SETTINGS) { SettingsScreen(container) }
        }
    }
}
