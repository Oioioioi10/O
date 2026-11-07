package com.avatarsdev.freellmapi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avatarsdev.freellmapi.AppContainer
import com.avatarsdev.freellmapi.data.db.RequestLogEntity
import com.avatarsdev.freellmapi.ui.components.AppCard
import com.avatarsdev.freellmapi.ui.components.ConfirmDialog
import com.avatarsdev.freellmapi.ui.components.EmptyState
import com.avatarsdev.freellmapi.ui.components.Expandable
import com.avatarsdev.freellmapi.ui.components.LabeledValue
import com.avatarsdev.freellmapi.ui.components.ScreenHeader
import com.avatarsdev.freellmapi.ui.components.StatusChip
import com.avatarsdev.freellmapi.ui.components.formatLatency
import com.avatarsdev.freellmapi.ui.containerViewModel
import com.avatarsdev.freellmapi.ui.theme.AppColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogsViewModel(private val c: AppContainer) : ViewModel() {
    val logs: StateFlow<List<RequestLogEntity>?> = c.activity.recentLogs(500).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun clear() = viewModelScope.launch { c.activity.clearLogs() }
}

private enum class LogFilter(val label: String) { ALL("All"), OK("Success"), ERRORS("Errors"), API("Local API"), PLAYGROUND("Playground") }

@Composable
fun LogsScreen(onBack: () -> Unit) {
    val vm = containerViewModel { LogsViewModel(it) }
    val logs by vm.logs.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    var expanded by remember { mutableStateOf<Long?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val shown = logs.orEmpty().filter {
        when (filter) {
            LogFilter.ALL -> true
            LogFilter.OK -> it.success
            LogFilter.ERRORS -> !it.success
            LogFilter.API -> it.source == "api"
            LogFilter.PLAYGROUND -> it.source == "playground"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("screen_logs"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenHeader("Request logs", subtitle = "Metadata only — prompts, replies and keys are never stored", onBack = onBack) {
                IconButton(onClick = { confirmClear = true }, enabled = !logs.isNullOrEmpty()) { Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear logs") }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LogFilter.entries) { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) }) }
            }
        }
        if (logs != null && shown.isEmpty()) {
            item {
                AppCard {
                    EmptyState(Icons.Outlined.Description, "No requests", "Requests from the Playground and the local API show up here.")
                }
            }
        }
        items(shown, key = { it.id }) { log ->
            RequestLogRow(log, expanded = expanded == log.id, onClick = { expanded = if (expanded == log.id) null else log.id })
        }
    }

    if (confirmClear) {
        ConfirmDialog("Clear all logs?", "Usage statistics are kept.", "Clear", onConfirm = { vm.clear() }, onDismiss = { confirmClear = false })
    }
}

private val timeFormat = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())

@Composable
fun RequestLogRow(log: RequestLogEntity, expanded: Boolean, onClick: () -> Unit) {
    val status = AppColors.status
    val color = when {
        log.success -> status.success
        log.statusCode == 429 -> status.warning
        else -> status.danger
    }
    AppCard(onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip(log.statusCode.toString(), color)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    log.model?.let { m -> (log.providerName?.let { "$it · " } ?: "") + m } ?: log.requestedModel,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${synchronized(timeFormat) { timeFormat.format(Date(log.timestamp)) }} · ${if (log.source == "api") "API" else "Playground"}" +
                        (if (log.stream) " · stream" else "") + (if (log.attempts > 1) " · ${log.attempts} attempts" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatLatency(log.latencyMs), style = MaterialTheme.typography.labelLarge)
                if (log.success) {
                    Text("${log.promptTokens + log.completionTokens} tok", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Expandable(expanded) {
            Column {
                Spacer(Modifier.height(10.dp))
                LabeledValue("Requested", log.requestedModel, mono = true)
                LabeledValue("Endpoint", log.endpoint, mono = true)
                LabeledValue("Tokens", "${log.promptTokens} in · ${log.completionTokens} out")
                if (log.fallbackPath.isNotBlank()) LabeledValue("Route", log.fallbackPath)
                log.errorMessage?.let { LabeledValue("Error", it) }
            }
        }
    }
}

