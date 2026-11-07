package com.avatarsdev.freellmapi.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.DeviceHub
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.avatarsdev.freellmapi.AppContainer
import com.avatarsdev.freellmapi.ui.screens.DashboardScreen
import com.avatarsdev.freellmapi.ui.screens.LogsScreen
import com.avatarsdev.freellmapi.ui.screens.ModelsScreen
import com.avatarsdev.freellmapi.ui.screens.MoreScreen
import com.avatarsdev.freellmapi.ui.screens.PlaygroundScreen
import com.avatarsdev.freellmapi.ui.screens.ProviderDetailScreen
import com.avatarsdev.freellmapi.ui.screens.ProvidersScreen
import com.avatarsdev.freellmapi.ui.screens.RoutingScreen
import com.avatarsdev.freellmapi.ui.screens.ServerScreen
import com.avatarsdev.freellmapi.ui.screens.SettingsScreen
import com.avatarsdev.freellmapi.ui.screens.UsageScreen

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** Creates a ViewModel scoped to the current navigation entry, built from the app container. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = LocalAppContainer.current
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}

enum class TopDestination(val route: String, val label: String, val icon: ImageVector) {
    DASHBOARD("dashboard", "Dashboard", Icons.Outlined.Dashboard),
    PROVIDERS("providers", "Providers", Icons.Outlined.DeviceHub),
    MODELS("models", "Models", Icons.Outlined.Memory),
    PLAYGROUND("playground", "Playground", Icons.Outlined.PlayCircleOutline),
    MORE("more", "More", Icons.Outlined.Apps),
}

object Routes {
    const val SERVER = "server"
    const val ROUTING = "routing"
    const val USAGE = "usage"
    const val LOGS = "logs"
    const val SETTINGS = "settings"
    fun provider(id: Long) = "provider/$id"
}

private fun topFor(route: String?): TopDestination = when {
    route == null -> TopDestination.DASHBOARD
    route.startsWith("provider") -> TopDestination.PROVIDERS
    route in setOf(Routes.SERVER, Routes.ROUTING, Routes.USAGE, Routes.LOGS, Routes.SETTINGS) -> TopDestination.MORE
    else -> TopDestination.entries.firstOrNull { it.route == route } ?: TopDestination.DASHBOARD
}

@Composable
fun FreeLlmAppRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = topFor(entry?.destination?.route)
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    fun go(dest: TopDestination) {
        nav.navigate(dest.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (wide) {
            NavigationRail(
                modifier = Modifier.statusBarsPadding(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                header = { BrandMark(); Spacer(Modifier.height(12.dp)) },
            ) {
                TopDestination.entries.forEach { d ->
                    NavigationRailItem(
                        selected = d == current,
                        onClick = { go(d) },
                        icon = { Icon(d.icon, contentDescription = d.label) },
                        label = { Text(d.label) },
                        colors = NavigationRailItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.testTag("nav_${d.route}"),
                    )
                }
            }
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!wide) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                        TopDestination.entries.forEach { d ->
                            NavigationBarItem(
                                selected = d == current,
                                onClick = { go(d) },
                                icon = { Icon(d.icon, contentDescription = d.label) },
                                label = { Text(d.label, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                                modifier = Modifier.testTag("nav_${d.route}"),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            AppNavHost(nav, Modifier.padding(padding).consumeWindowInsets(padding))
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, modifier: Modifier) {
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> androidx.compose.animation.EnterTransition = {
        fadeIn(tween(240)) + slideInVertically(tween(280)) { it / 28 }
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> androidx.compose.animation.ExitTransition = {
        fadeOut(tween(160))
    }
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(
        navController = nav,
        startDestination = TopDestination.DASHBOARD.route,
        modifier = modifier,
        enterTransition = enter,
        exitTransition = exit,
        popEnterTransition = enter,
        popExitTransition = exit,
    ) {
        composable(TopDestination.DASHBOARD.route) { DashboardScreen(onNavigate = { nav.navigate(it) }) }
        composable(TopDestination.PROVIDERS.route) { ProvidersScreen(onOpenProvider = { nav.navigate(Routes.provider(it)) }) }
        composable(
            "provider/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { e -> ProviderDetailScreen(providerId = e.arguments?.getLong("id") ?: 0L, onBack = back) }
        composable(TopDestination.MODELS.route) { ModelsScreen() }
        composable(TopDestination.PLAYGROUND.route) { PlaygroundScreen() }
        composable(TopDestination.MORE.route) { MoreScreen(onNavigate = { nav.navigate(it) }) }
        composable(Routes.SERVER) { ServerScreen(onBack = back) }
        composable(Routes.ROUTING) { RoutingScreen(onBack = back) }
        composable(Routes.USAGE) { UsageScreen(onBack = back) }
        composable(Routes.LOGS) { LogsScreen(onBack = back) }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = back) }
    }
}

@Composable
fun BrandMark(size: androidx.compose.ui.unit.Dp = 40.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.linearGradient(listOf(Color(0xFF6D5CFF), Color(0xFF14B8A6)))),
        contentAlignment = Alignment.Center,
    ) {
        Text("F", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
    }
}

/**
 * Returns a callback that starts or stops the local server. On Android 13+ it first asks for notification
 * permission so the foreground-service notification (with its Stop button) is visible.
 */
@Composable
fun rememberServerToggle(): (Boolean) -> Unit {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val start = {
        val r = container.server.start()
        r.exceptionOrNull()?.let { Toast.makeText(context, container.server.state.value.error ?: "Could not start", Toast.LENGTH_LONG).show() }
        Unit
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { start() }
    return { run ->
        if (!run) {
            container.server.stop()
        } else if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            start()
        }
    }
}
