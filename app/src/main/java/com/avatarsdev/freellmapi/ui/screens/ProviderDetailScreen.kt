package com.avatarsdev.freellmapi.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avatarsdev.freellmapi.AppContainer
import com.avatarsdev.freellmapi.core.provider.ProviderType
import com.avatarsdev.freellmapi.data.ConnectionTest
import com.avatarsdev.freellmapi.data.db.ApiKeyEntity
import com.avatarsdev.freellmapi.data.db.ModelEntity
import com.avatarsdev.freellmapi.data.db.ProviderEntity
import com.avatarsdev.freellmapi.ui.components.AppCard
import com.avatarsdev.freellmapi.ui.components.ConfirmDialog
import com.avatarsdev.freellmapi.ui.components.LabeledValue
import com.avatarsdev.freellmapi.ui.components.ProviderAvatar
import com.avatarsdev.freellmapi.ui.components.ScreenHeader
import com.avatarsdev.freellmapi.ui.components.SecretField
import com.avatarsdev.freellmapi.ui.components.SectionTitle
import com.avatarsdev.freellmapi.ui.components.SelectField
import com.avatarsdev.freellmapi.ui.components.StatusChip
import com.avatarsdev.freellmapi.ui.components.formatLatency
import com.avatarsdev.freellmapi.ui.containerViewModel
import com.avatarsdev.freellmapi.ui.theme.AppColors
import com.avatarsdev.freellmapi.ui.theme.MonoStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class ProviderDetailViewModel(private val c: AppContainer, val providerId: Long) : ViewModel() {
    val provider: StateFlow<ProviderEntity?> = c.providers.provider(providerId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val keys: StateFlow<List<ApiKeyEntity>> = c.providers.keysFor(providerId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val models: StateFlow<List<ModelEntity>> = c.providers.modelsFor(providerId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _test = MutableStateFlow<ConnectionTest?>(null)
    val test: StateFlow<ConnectionTest?> = _test.asStateFlow()

    fun sync() = viewModelScope.launch {
        _busy.value = true
        _test.value = c.providers.syncModels(providerId)
        _busy.value = false
    }

    fun update(p: ProviderEntity) = viewModelScope.launch { c.providers.updateProvider(p) }
    /** Runs in the app scope so it completes even though this screen (and its ViewModel) closes right away. */
    fun delete(p: ProviderEntity) {
        c.scope.launch { c.providers.deleteProvider(p) }
    }

    fun addKey(label: String, key: String) = viewModelScope.launch { c.providers.addKey(providerId, label, key) }
    fun deleteKey(id: Long) = viewModelScope.launch { c.providers.deleteKey(id) }
    fun setKeyEnabled(id: Long, enabled: Boolean) = viewModelScope.launch { c.providers.setKeyEnabled(id, enabled) }
    fun setDefaultModel(model: String) = viewModelScope.launch { c.providers.setDefaultModel(providerId, model) }
    fun addModel(model: String) = viewModelScope.launch { c.providers.addManualModel(providerId, model) }
}

@Composable
fun ProviderDetailScreen(providerId: Long, onBack: () -> Unit) {
    val vm = containerViewModel { ProviderDetailViewModel(it, providerId) }
    val provider by vm.provider.collectAsStateWithLifecycle()
    val keys by vm.keys.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val test by vm.test.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val status = AppColors.status

    var showAddKey by remember { mutableStateOf(false) }
    var showEditUrl by remember { mutableStateOf(false) }
    var showAddModel by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var keyToDelete by remember { mutableStateOf<ApiKeyEntity?>(null) }

    val p = provider ?: return
    val type = ProviderType.fromId(p.type)

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("screen_provider_detail"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenHeader(p.name, subtitle = type.displayName, onBack = onBack) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete provider") }
            }
        }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProviderAvatar(type, 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (p.enabled) "Enabled for routing" else "Disabled", style = MaterialTheme.typography.titleMedium)
                        Text("Model prefix: ${p.slug}/", style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = p.enabled, onCheckedChange = { vm.update(p.copy(enabled = it)) })
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { LabeledValue("Base URL", p.baseUrl, mono = true) }
                    IconButton(onClick = { showEditUrl = true }) { Icon(Icons.Outlined.Edit, contentDescription = "Edit base URL") }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { vm.sync() }, enabled = !busy, modifier = Modifier.testTag("sync_models")) {
                        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.NetworkCheck, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Test & sync models")
                    }
                    if (type.docsUrl.isNotEmpty()) {
                        OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(type.docsUrl))) }) {
                            Text("Docs")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                test?.let { t ->
                    Spacer(Modifier.height(10.dp))
                    StatusChip(
                        (if (t.ok) "✓ " else "✗ ") + t.message + if (t.ok && t.latencyMs > 0) " · ${formatLatency(t.latencyMs)}" else "",
                        if (t.ok) status.success else status.danger,
                    )
                }
            }
        }
        item {
            SectionTitle("API keys") {
                TextButton(onClick = { showAddKey = true }, modifier = Modifier.testTag("add_key")) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add key")
                }
            }
        }
        if (keys.isEmpty()) {
            item {
                AppCard {
                    Text(
                        if (type.requiresKey) "No keys yet. Add one or more keys — the gateway rotates between them and skips keys that hit rate limits."
                        else "This provider works without a key (e.g. a local Ollama server). You can still add one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (type.keyUrl.isNotEmpty()) {
                        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(type.keyUrl))) }) { Text("Get a ${type.displayName} key") }
                    }
                }
            }
        }
        items(keys, key = { it.id }) { k -> KeyRow(k, onToggle = { vm.setKeyEnabled(k.id, it) }, onDelete = { keyToDelete = k }) }
        item {
            SectionTitle("Default model") {
                TextButton(onClick = { showAddModel = true }) { Text("Add model id") }
            }
        }
        item {
            val enabledModels = models.filter { it.enabled }
            AppCard {
                SelectField(
                    label = "Used for 'auto' and fallback",
                    options = enabledModels.map { it.modelId },
                    selected = p.defaultModel,
                    optionLabel = { it },
                    onSelect = { vm.setDefaultModel(it) },
                    placeholder = "No models — sync or add one",
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${enabledModels.size} enabled of ${models.size} models. Manage them in the Models tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showAddKey) {
        AddKeyDialog(type, onDismiss = { showAddKey = false }, onSave = { label, key -> vm.addKey(label, key) })
    }
    if (showEditUrl) {
        TextInputDialog(
            title = "Base URL",
            initial = p.baseUrl,
            hint = "Default: ${type.defaultBaseUrl}",
            onDismiss = { showEditUrl = false },
            onSave = { vm.update(p.copy(baseUrl = it.ifBlank { type.defaultBaseUrl })) },
        )
    }
    if (showAddModel) {
        TextInputDialog(
            title = "Add model id",
            initial = "",
            hint = "Exactly as the provider names it, e.g. ${type.builtinModels.firstOrNull() ?: "llama3.2"}",
            onDismiss = { showAddModel = false },
            onSave = { if (it.isNotBlank()) vm.addModel(it) },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete ${p.name}?",
            message = "Its keys, models and routes to it will be removed from this device.",
            confirmLabel = "Delete",
            onConfirm = {
                vm.delete(p)
                Toast.makeText(context, "Provider deleted", Toast.LENGTH_SHORT).show()
                onBack()
            },
            onDismiss = { confirmDelete = false },
        )
    }
    keyToDelete?.let { k ->
        ConfirmDialog(
            title = "Delete key \"${k.label}\"?",
            message = "The encrypted key ${k.maskedKey} will be erased.",
            confirmLabel = "Delete",
            onConfirm = { vm.deleteKey(k.id) },
            onDismiss = { keyToDelete = null },
        )
    }
}

@Composable
private fun KeyRow(k: ApiKeyEntity, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val status = AppColors.status
    val now = System.currentTimeMillis()
    val cooling = k.cooldownUntil > now
    AppCard(contentPadding = PaddingValues(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(k.label, style = MaterialTheme.typography.titleSmall)
                Text(k.maskedKey, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        !k.enabled -> StatusChip("Off", MaterialTheme.colorScheme.onSurfaceVariant)
                        cooling -> StatusChip("Cooling until ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(k.cooldownUntil))}", status.warning)
                        k.lastError != null -> StatusChip("Last call failed", status.danger)
                        else -> StatusChip("Active", status.success)
                    }
                    StatusChip("${k.successCount} ok · ${k.failureCount} err", MaterialTheme.colorScheme.onSurfaceVariant)
                }
                k.lastError?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = status.danger, maxLines = 2)
                }
            }
            Switch(checked = k.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Delete key") }
        }
    }
}

@Composable
private fun AddKeyDialog(type: ProviderType, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var label by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${type.displayName} key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                SecretField(value = key, onValueChange = { key = it }, label = "API key", placeholder = type.keyHint)
            }
        },
        confirmButton = {
            Button(enabled = key.isNotBlank(), onClick = {
                onSave(label, key)
                key = ""
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun TextInputDialog(title: String, initial: String, hint: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(value.trim())
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
