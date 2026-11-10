package com.avatarsdev.tiktokupgrade.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.avatarsdev.tiktokupgrade.ui.dashboard.DashboardScreen
import com.avatarsdev.tiktokupgrade.ui.feed.FeedScreen
import com.avatarsdev.tiktokupgrade.ui.feed.FeedViewModel
import com.avatarsdev.tiktokupgrade.ui.likes.AnalyticsScreen
import com.avatarsdev.tiktokupgrade.ui.likes.HistoryScreen
import com.avatarsdev.tiktokupgrade.ui.likes.LikesViewModel
import com.avatarsdev.tiktokupgrade.ui.settings.SettingsScreen
import com.avatarsdev.tiktokupgrade.ui.settings.SettingsViewModel
import com.avatarsdev.tiktokupgrade.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow

object Routes {
    const val HOME = "home"
    const val FEED = "feed"
    const val ANALYTICS = "analytics"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "หน้าแรก", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Routes.FEED, "AI Feed", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    Tab(Routes.ANALYTICS, "สถิติ", Icons.Outlined.Insights, Icons.Filled.Insights),
    Tab(Routes.HISTORY, "ประวัติ", Icons.Outlined.History, Icons.Filled.History),
    Tab(Routes.SETTINGS, "ตั้งค่า", Icons.Outlined.Settings, Icons.Filled.Settings),
)

fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppRoot(pendingRoute: MutableStateFlow<String?>) {
    val settingsVm: SettingsViewModel = viewModel()
    val likesVm: LikesViewModel = viewModel()
    val feedVm: FeedViewModel = viewModel()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()

    AppTheme(settings.theme) {
        val nav = rememberNavController()
        val snackbar = remember { SnackbarHostState() }
        val message by settingsVm.message.collectAsStateWithLifecycle()
        LaunchedEffect(message) {
            message?.let {
                snackbar.showSnackbar(it)
                settingsVm.consumeMessage()
            }
        }
        val pending by pendingRoute.collectAsState()
        LaunchedEffect(pending) {
            pending?.let {
                nav.goTab(it)
                pendingRoute.value = null
            }
        }
        val backStack by nav.currentBackStackEntryAsState()
        val current = backStack?.destination?.route

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    tabs.forEach { tab ->
                        val selected = current == tab.route
                        NavigationBarItem(
                            modifier = Modifier.testTag("nav_${tab.route}"),
                            selected = selected,
                            onClick = { nav.goTab(tab.route) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(padding),
                enterTransition = { fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 24 } },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(160)) },
            ) {
                composable(Routes.HOME) { DashboardScreen(likesVm, feedVm, settingsVm, onNavigate = { nav.goTab(it) }) }
                composable(Routes.FEED) { FeedScreen(feedVm) }
                composable(Routes.ANALYTICS) { AnalyticsScreen(likesVm, settingsVm, onNavigate = { nav.goTab(it) }) }
                composable(Routes.HISTORY) { HistoryScreen(likesVm, settingsVm, onNavigate = { nav.goTab(it) }) }
                composable(Routes.SETTINGS) { SettingsScreen(settingsVm) }
            }
        }
    }
}
