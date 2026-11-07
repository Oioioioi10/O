package com.avatarsdev.freellmapi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avatarsdev.freellmapi.BuildConfig
import com.avatarsdev.freellmapi.ui.LocalAppContainer
import com.avatarsdev.freellmapi.ui.Routes
import com.avatarsdev.freellmapi.ui.components.AppCard
import com.avatarsdev.freellmapi.ui.components.ScreenHeader
import com.avatarsdev.freellmapi.ui.theme.AppColors

private data class MoreItem(val route: String, val title: String, val subtitle: String, val icon: ImageVector)

@Composable
fun MoreScreen(onNavigate: (String) -> Unit) {
    val container = LocalAppContainer.current
    val server by container.server.state.collectAsStateWithLifecycle()
    val status = AppColors.status
    val entries = listOf(
        MoreItem(Routes.SERVER, "Local API server", if (server.running) "Running on port ${server.port}" else "Stopped · OpenAI-compatible endpoint", Icons.Outlined.Dns) to status.success,
        MoreItem(Routes.ROUTING, "Routing & fallback", "Strategy, aliases and automatic fallback", Icons.Outlined.AccountTree) to MaterialTheme.colorScheme.primary,
        MoreItem(Routes.USAGE, "Usage", "Requests and tokens per provider", Icons.Outlined.BarChart) to status.info,
        MoreItem(Routes.LOGS, "Request logs", "Status, latency and fallback path of every call", Icons.Outlined.Description) to status.warning,
        MoreItem(Routes.SETTINGS, "Settings", "Dark mode, timeouts, retention and security", Icons.Outlined.Settings) to MaterialTheme.colorScheme.tertiary,
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("screen_more"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader("More", subtitle = "FreeLLMAPI ${BuildConfig.VERSION_NAME}") }
        items(entries, key = { it.first.route }) { (item, color) -> MoreRow(item, color) { onNavigate(item.route) } }
    }
}

@Composable
private fun MoreRow(item: MoreItem, color: Color, onClick: () -> Unit) {
    AppCard(onClick = onClick, contentPadding = PaddingValues(14.dp), modifier = Modifier.testTag("more_${item.route}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(item.icon, contentDescription = null, tint = color)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
