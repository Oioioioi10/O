package com.avatarsdev.freellmapi.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avatarsdev.freellmapi.AppContainer
import com.avatarsdev.freellmapi.core.gateway.RouteTarget
import com.avatarsdev.freellmapi.core.gateway.RoutingStrategy
import com.avatarsdev.freellmapi.data.RoomGatewayStore
import com.avatarsdev.freellmapi.data.db.RouteEntity
import com.avatarsdev.freellmapi.ui.components.AppCard
import com.avatarsdev.freellmapi.ui.components.ConfirmDialog
import com.avatarsdev.freellmapi.ui.components.ScreenHeader
import com.avatarsdev.freellmapi.ui.components.SectionTitle
import com.avatarsdev.freellmapi.ui.components.StatusChip
import com.avatarsdev.freellmapi.ui.containerViewModel
import com.avatarsdev.freellmapi.ui.theme.MonoStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ModelChoice(val providerId: Long, val providerName: String, val slug: String, val modelId: String) {
    val label: String get() = "$slug/$modelId"
}

data class RouteView(val entity: RouteEntity, val targets: List<String>)

class RoutingViewModel(private val c: AppContainer) : ViewModel() {
    val choices: StateFlow<List<ModelChoice>> = combine(c.providers.providers, c.providers.models) { ps, ms ->
        ps.filter { it.enabled }.sortedBy { it.priority }.flatMap { p ->
            ms.filter { it.providerId == p.id && it.enabled }.map { ModelChoice(p.id, p.name, p.slug, it.modelId) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val routes: StateFlow<List<RouteView>> = combine(c.activity.routes, c.providers.providers) { rs, ps ->
        val slug = ps.associate { it.id to it.slug }
        rs.map { r ->
            RouteView(r, RoomGatewayStore.parseTargets(r.targets).map { t -> "${slug[t.providerId] ?: "missing"}/${t.modelId}" })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _preview = MutableStateFlow<List<String>?>(null)
    val preview: StateFlow<List<String>?> = _preview.asStateFlow()

    fun previewRoute(model: String) = viewModelScope.launch {
        _preview.value = c.engine.preview(model).map { it.publicId }
    }

    fun save(alias: String, description: String, targets: List<ModelChoice>, onResult: (String?) -> Unit) = viewModelScope.launch {
        try {
            c.activity.saveRoute(alias, description, targets.map { RouteTarget(it.providerId, it.modelId) })
            onResult(null)
        } catch (e: Exception) {
            onResult(e.message ?: "Could not save")
        }
    }

    fun delete(id: Long) = viewModelScope.launch { c.activity.deleteRoute(id) }

    fun resetCooldowns() {
        c.engine.clearCooldowns()
        viewModelScope.launch { c.providers.clearCooldowns() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingScreen(onBack: () -> Unit) {
    val vm = containerViewModel { RoutingViewModel(it) }
    val container = com.avatarsdev.freellmapi.ui.LocalAppContainer.current
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val routes by vm.routes.collectAsStateWithLifecycle()
    val choices by vm.choices.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showNew by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<RouteView?>(null) }
    var previewModel by remember { mutableStateOf("auto") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("screen_routing"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader("Routing & fallback", subtitle = "How the gateway picks a provider for each request", onBack = onBack) }
        item { SectionTitle("Strategy") }
        item {
            AppCard {
                val strategies = RoutingStrategy.entries
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    strategies.forEachIndexed { i, s ->
                        SegmentedButton(
                            selected = settings.strategy == s,
                            onClick = { container.settings.update { it.copy(strategy = s) } },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = strategies.size),
                        ) {
                            Text(
                                when (s) {
                                    RoutingStrategy.PRIORITY -> "Priority"
                                    RoutingStrategy.ROUND_ROBIN -> "Rotate"
                                    RoutingStrategy.FASTEST -> "Fastest"
                                },
                                maxLines = 1,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("${settings.strategy.label}: ${settings.strategy.description}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { SectionTitle("Automatic fallback") }
        item {
            AppCard {
                ToggleRow(
                    "Fallback on errors",
                    "Retry with the next key or provider after 429, 5xx, timeouts or invalid keys",
                    settings.fallbackEnabled,
                ) { v -> container.settings.update { it.copy(fallbackEnabled = v) } }
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    "Cross-model fallback",
                    "If every provider for the requested model fails, try each provider's default model",
                    settings.crossModelFallback,
                    enabled = settings.fallbackEnabled,
                ) { v -> container.settings.update { it.copy(crossModelFallback = v) } }
                Spacer(Modifier.height(12.dp))
                Text("Max attempts per request: ${settings.maxAttempts}", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = settings.maxAttempts.toFloat(),
                    onValueChange = { v -> container.settings.update { it.copy(maxAttempts = v.toInt().coerceIn(1, 10)) } },
                    valueRange = 1f..10f,
                    steps = 8,
                )
                FilledTonalButton(onClick = {
                    vm.resetCooldowns()
                    Toast.makeText(context, "All key cooldowns cleared", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset key cooldowns")
                }
            }
        }
        item { SectionTitle("Test a model name") }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = previewModel,
                        onValueChange = { previewModel = it },
                        singleLine = true,
                        label = { Text("model") },
                        textStyle = MonoStyle,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { vm.previewRoute(previewModel) }) { Text("Resolve") }
                }
                preview?.let { list ->
                    Spacer(Modifier.height(10.dp))
                    if (list.isEmpty()) {
                        Text("No candidate — this request would fail with 404/503.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        list.forEachIndexed { i, id ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                                OrderBadge(i + 1)
                                Spacer(Modifier.width(10.dp))
                                Text(id, style = MonoStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionTitle("Route aliases") {
                TextButton(onClick = { showNew = true }, enabled = choices.isNotEmpty()) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New alias")
                }
            }
        }
        if (routes.isEmpty()) {
            item {
                AppCard {
                    Text(
                        "Create an alias like \"fast\" or \"coder\" with an ordered chain of models. Clients call it as the model name. An alias named \"auto\" replaces the default chain.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(routes, key = { it.entity.id }) { r ->
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AccountTree, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.entity.alias, style = MaterialTheme.typography.titleMedium.merge(MonoStyle))
                        if (r.entity.description.isNotBlank()) {
                            Text(r.entity.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { toDelete = r }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete alias") }
                }
                Spacer(Modifier.height(6.dp))
                r.targets.forEachIndexed { i, t ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        OrderBadge(i + 1)
                        Spacer(Modifier.width(10.dp))
                        Text(t, style = MonoStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    if (showNew) {
        NewRouteDialog(
            choices = choices,
            onDismiss = { showNew = false },
            onSave = { alias, desc, targets ->
                vm.save(alias, desc, targets) { err ->
                    if (err == null) showNew = false else Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                }
            },
        )
    }
    toDelete?.let { r ->
        ConfirmDialog("Delete alias \"${r.entity.alias}\"?", "Clients using it will get 404.", "Delete", onConfirm = { vm.delete(r.entity.id) }, onDismiss = { toDelete = null })
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun OrderBadge(n: Int) {
    Box(
        Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) { Text("$n", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer) }
}

@Composable
private fun NewRouteDialog(choices: List<ModelChoice>, onDismiss: () -> Unit, onSave: (String, String, List<ModelChoice>) -> Unit) {
    var alias by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<ModelChoice>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New route alias") },
        text = {
            Column {
                OutlinedTextField(value = alias, onValueChange = { v -> alias = v.filter { !it.isWhitespace() && it != '/' } }, label = { Text("Alias (e.g. fast)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Text("Tap models in fallback order", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                // Plain scrolling column: lazy lists cannot be measured inside dialogs that use intrinsic sizes.
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    choices.forEach { c ->
                        val index = picked.indexOf(c)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (index >= 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { if (index >= 0) picked.remove(c) else picked.add(c) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (index >= 0) OrderBadge(index + 1) else Spacer(Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(c.label, style = MonoStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (picked.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    StatusChip("${picked.size} in chain", MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            Button(enabled = alias.isNotBlank() && picked.isNotEmpty(), onClick = { onSave(alias, description, picked.toList()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
