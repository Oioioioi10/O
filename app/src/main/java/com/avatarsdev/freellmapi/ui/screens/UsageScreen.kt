package com.avatarsdev.freellmapi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avatarsdev.freellmapi.AppContainer
import com.avatarsdev.freellmapi.data.db.UsageDailyEntity
import com.avatarsdev.freellmapi.ui.components.AppCard
import com.avatarsdev.freellmapi.ui.components.ConfirmDialog
import com.avatarsdev.freellmapi.ui.components.ProgressBar
import com.avatarsdev.freellmapi.ui.components.ScreenHeader
import com.avatarsdev.freellmapi.ui.components.SectionTitle
import com.avatarsdev.freellmapi.ui.components.StatTile
import com.avatarsdev.freellmapi.ui.components.formatCount
import com.avatarsdev.freellmapi.ui.components.formatLatency
import com.avatarsdev.freellmapi.ui.containerViewModel
import com.avatarsdev.freellmapi.ui.theme.AppColors
import com.avatarsdev.freellmapi.ui.theme.MonoStyle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UsageGroup(
    val name: String,
    val requests: Long,
    val successes: Long,
    val promptTokens: Long,
    val completionTokens: Long,
    val totalLatency: Long,
) {
    val tokens: Long get() = promptTokens + completionTokens
    val avgLatency: Long? get() = if (successes > 0) totalLatency / successes else null
}

private fun List<UsageDailyEntity>.groupUsage(key: (UsageDailyEntity) -> String): List<UsageGroup> =
    groupBy(key).map { (name, rows) ->
        UsageGroup(name, rows.sumOf { it.requests }, rows.sumOf { it.successes }, rows.sumOf { it.promptTokens }, rows.sumOf { it.completionTokens }, rows.sumOf { it.totalLatencyMs })
    }.sortedByDescending { it.requests }

@OptIn(ExperimentalCoroutinesApi::class)
class UsageViewModel(private val c: AppContainer) : ViewModel() {
    val period = MutableStateFlow(0)
    val rows: StateFlow<List<UsageDailyEntity>> = period.flatMapLatest { p ->
        c.activity.usageSince(
            when (p) {
                0 -> 0L
                1 -> 6L
                else -> 29L
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun reset() = viewModelScope.launch { c.activity.clearUsage() }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(onBack: () -> Unit) {
    val vm = containerViewModel { UsageViewModel(it) }
    val period by vm.period.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    val status = AppColors.status
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    val requests = rows.sumOf { it.requests }
    val successes = rows.sumOf { it.successes }
    val prompt = rows.sumOf { it.promptTokens }
    val completion = rows.sumOf { it.completionTokens }
    val byProvider = rows.groupUsage { it.providerName }
    val byModel = rows.filter { it.providerId >= 0 }.groupUsage { "${it.providerName} · ${it.modelId}" }
    val maxProvider = (byProvider.maxOfOrNull { it.requests } ?: 1L).coerceAtLeast(1L)

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("screen_usage"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenHeader("Usage", subtitle = "Requests and tokens per provider and model", onBack = onBack) {
                IconButton(onClick = { confirmReset = true }, enabled = rows.isNotEmpty()) { Icon(Icons.Outlined.DeleteSweep, contentDescription = "Reset usage") }
            }
        }
        item {
            val labels = listOf("Today", "7 days", "30 days")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                labels.forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = period == i,
                        onClick = { vm.period.value = i },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = labels.size),
                    ) { Text(label) }
                }
            }
        }
        item {
            val tiles = listOf(
                Triple("Requests", formatCount(requests), Icons.Outlined.FlashOn) to status.info,
                Triple("Success", if (requests > 0) "${successes * 100 / requests}%" else "—", Icons.Outlined.CheckCircle) to status.success,
                Triple("Input tokens", formatCount(prompt), Icons.Outlined.ArrowDownward) to MaterialTheme.colorScheme.primary,
                Triple("Output tokens", formatCount(completion), Icons.Outlined.ArrowUpward) to MaterialTheme.colorScheme.tertiary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tiles.chunked(if (wide) 4 else 2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (t, color) -> StatTile(t.first, t.second, t.third, color, Modifier.weight(1f)) }
                    }
                }
            }
        }
        item { SectionTitle("By provider") }
        if (byProvider.isEmpty()) {
            item { Text("No usage in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
        }
        items(byProvider, key = { "p-" + it.name }) { g ->
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(g.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("${formatCount(g.requests)} req · ${formatCount(g.tokens)} tok", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(8.dp))
                ProgressBar(g.requests.toFloat() / maxProvider, if (g.successes == g.requests) status.success else status.warning)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${g.successes} ok · ${g.requests - g.successes} failed" + (g.avgLatency?.let { " · avg ${formatLatency(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { SectionTitle("Top models") }
        items(byModel.take(15), key = { "m-" + it.name }) { g ->
            AppCard(contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(g.name, style = MonoStyle, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${g.requests}", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    if (confirmReset) {
        ConfirmDialog("Reset usage statistics?", "All counters will be set to zero. Logs are kept.", "Reset", onConfirm = { vm.reset() }, onDismiss = { confirmReset = false })
    }
}
