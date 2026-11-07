package com.avatarsdev.freellmapi.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.DeviceHub
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avatarsdev.freellmapi.AppContainer
import com.avatarsdev.freellmapi.core.provider.ProviderType
import com.avatarsdev.freellmapi.data.db.RequestLogEntity
import com.avatarsdev.freellmapi.service.ServerState
import com.avatarsdev.freellmapi.ui.BrandMark
import com.avatarsdev.freellmapi.ui.Routes
import com.avatarsdev.freellmapi.ui.TopDestination
import com.avatarsdev.freellmapi.ui.components.AppCard
import com.avatarsdev.freellmapi.ui.components.BarChart
import com.avatarsdev.freellmapi.ui.components.CodeBlock
import com.avatarsdev.freellmapi.ui.components.EmptyState
import com.avatarsdev.freellmapi.ui.components.ProviderAvatar
import com.avatarsdev.freellmapi.ui.components.PulseDot
import com.avatarsdev.freellmapi.ui.components.SectionTitle
import com.avatarsdev.freellmapi.ui.components.StatTile
import com.avatarsdev.freellmapi.ui.components.StatusChip
import com.avatarsdev.freellmapi.ui.components.formatCount
import com.avatarsdev.freellmapi.ui.components.formatLatency
import com.avatarsdev.freellmapi.ui.containerViewModel
import com.avatarsdev.freellmapi.ui.rememberServerToggle
import com.avatarsdev.freellmapi.ui.theme.AppColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class ProviderHealth(
    val id: Long,
    val name: String,
    val type: ProviderType,
    val enabled: Boolean,
    val keys: Int,
    val activeKeys: Int,
    val coolingKeys: Int,
    val models: Int,
    val defaultModel: String?,
)

data class DashboardUi(
    val loaded: Boolean = false,
    val providers: List<ProviderHealth> = emptyList(),
    val requestsToday: Long = 0,
    val successRate: Float? = null,
    val tokensToday: Long = 0,
    val avgLatencyMs: Long? = null,
    val week: List<Pair<String, Long>> = emptyList(),
    val recent: List<RequestLogEntity> = emptyList(),
    val server: ServerState = ServerState(),
)

class DashboardViewModel(c: AppContainer) : ViewModel() {
    private val health = combine(c.providers.providers, c.providers.keys, c.providers.models) { providers, keys, models ->
        val now = System.currentTimeMillis()
        providers.map { p ->
            val pk = keys.filter { it.providerId == p.id }
            ProviderHealth(
                id = p.id,
                name = p.name,
                type = ProviderType.fromId(p.type),
                enabled = p.enabled,
                keys = pk.size,
                activeKeys = pk.count { it.enabled && it.cooldownUntil <= now },
                coolingKeys = pk.count { it.enabled && it.cooldownUntil > now },
                models = models.count { it.providerId == p.id && it.enabled },
                defaultModel = p.defaultModel,
            )
        }
    }

    val ui: StateFlow<DashboardUi> = combine(
        health,
        c.activity.usageSince(6),
        c.activity.recentLogs(5),
        c.server.state,
    ) { providers, usage, recent, server ->
        val today = LocalDate.now()
        val todayRows = usage.filter { it.day == today.toString() }
        val requests = todayRows.sumOf { it.requests }
        val successes = todayRows.sumOf { it.successes }
        val latencyTotal = todayRows.sumOf { it.totalLatencyMs }
        val week = (6 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3) to usage.filter { it.day == d.toString() }.sumOf { it.requests }
        }
        DashboardUi(
            loaded = true,
            providers = providers,
            requestsToday = requests,
            successRate = if (requests > 0) successes.toFloat() / requests else null,
            tokensToday = todayRows.sumOf { it.promptTokens + it.completionTokens },
            avgLatencyMs = if (successes > 0) latencyTotal / successes else null,
            week = week,
            recent = recent,
            server = server,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUi())
}

@Composable
fun DashboardScreen(onNavigate: (String) -> Unit) {
    val vm = containerViewModel { DashboardViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val toggle = rememberServerToggle()
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val status = AppColors.status

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("screen_dashboard"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                BrandMark(42.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("FreeLLMAPI", style = MaterialTheme.typography.headlineSmall)
                    Text("AI API Gateway", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusChip(
                    if (ui.server.running) "Online" else "Offline",
                    if (ui.server.running) status.success else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { ServerHero(ui.server, onToggle = toggle, onOpen = { onNavigate(Routes.SERVER) }) }
        item {
            val tiles = listOf(
                Triple("Requests today", formatCount(ui.requestsToday), Icons.Outlined.FlashOn) to status.info,
                Triple("Success rate", ui.successRate?.let { "${(it * 100).toInt()}%" } ?: "—", Icons.Outlined.CheckCircle) to status.success,
                Triple("Tokens today", formatCount(ui.tokensToday), Icons.Outlined.DataUsage) to MaterialTheme.colorScheme.primary,
                Triple("Avg latency", ui.avgLatencyMs?.let { formatLatency(it) } ?: "—", Icons.Outlined.Speed) to status.warning,
            )
            val perRow = if (wide) 4 else 2
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tiles.chunked(perRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { (t, color) -> StatTile(t.first, t.second, t.third, color, Modifier.weight(1f)) }
                    }
                }
            }
        }
        item {
            AppCard {
                Text("Requests · last 7 days", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(14.dp))
                BarChart(
                    values = ui.week.map { it.second.toFloat() },
                    labels = ui.week.map { it.first },
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        item {
            SectionTitle("Providers") {
                TextButton(onClick = { onNavigate(TopDestination.PROVIDERS.route) }) { Text("Manage") }
            }
        }
        if (ui.loaded && ui.providers.isEmpty()) {
            item {
                AppCard {
                    EmptyState(
                        icon = Icons.Outlined.DeviceHub,
                        title = "No providers yet",
                        message = "Add OpenAI, Anthropic, Gemini, DeepSeek, Groq, OpenRouter or Qwen with your API key to start routing requests.",
                        actionLabel = "Add provider",
                        onAction = { onNavigate(TopDestination.PROVIDERS.route) },
                    )
                }
            }
        }
        items(ui.providers, key = { it.id }) { p -> ProviderHealthRow(p, onClick = { onNavigate(Routes.provider(p.id)) }) }
        item {
            SectionTitle("Recent requests") {
                TextButton(onClick = { onNavigate(Routes.LOGS) }) { Text("View all") }
            }
        }
        if (ui.recent.isEmpty()) {
            item {
                Text(
                    "No requests yet. Try the Playground or call the local API.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
        items(ui.recent, key = { "log-${it.id}" }) { log -> RequestLogRow(log, expanded = false, onClick = { onNavigate(Routes.LOGS) }) }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ServerHero(state: ServerState, onToggle: (Boolean) -> Unit, onOpen: () -> Unit) {
    val status = AppColors.status
    val glow by animateFloatAsState(if (state.running) 1f else 0.55f, label = "glow")
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(status.heroStart, status.heroEnd)))
            .graphicsLayer { alpha = 0.98f },
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulseDot(if (state.running) Color(0xFFB9FBC0) else Color.White.copy(alpha = 0.6f), active = state.running)
                Spacer(Modifier.width(4.dp))
                AnimatedContent(
                    targetState = state.running,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "server-status",
                ) { running ->
                    Text(
                        if (running) "Local API running" else "Local API stopped",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Unified endpoint", color = Color.White.copy(alpha = 0.75f * glow + 0.25f), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            CodeBlock(if (state.running) state.localUrl else "http://127.0.0.1:${if (state.port > 0) state.port else 8787}/v1", copyLabel = "Endpoint copied")
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { onToggle(!state.running) }, modifier = Modifier.testTag("server_toggle")) {
                    Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.running) "Stop server" else "Start server")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onOpen) {
                    Text("Details", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White)
                }
            }
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ProviderHealthRow(p: ProviderHealth, onClick: () -> Unit) {
    val status = AppColors.status
    val (label, color) = when {
        !p.enabled -> "Disabled" to MaterialTheme.colorScheme.onSurfaceVariant
        p.keys == 0 && p.type.requiresKey -> "No key" to status.warning
        p.activeKeys == 0 && p.coolingKeys > 0 -> "Cooling down" to status.warning
        p.activeKeys == 0 && p.type.requiresKey -> "Keys off" to status.danger
        else -> "Ready" to status.success
    }
    AppCard(onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProviderAvatar(p.type, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    "${p.keys} key${if (p.keys == 1) "" else "s"} · ${p.models} models" + (p.defaultModel?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            StatusChip(label, color)
        }
    }
}
