package com.avatarsdev.freellmapi.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.DeviceHub
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.avatarsdev.freellmapi.data.db.ProviderEntity
import com.avatarsdev.freellmapi.ui.components.AppCard
import com.avatarsdev.freellmapi.ui.components.EmptyState
import com.avatarsdev.freellmapi.ui.components.ProviderAvatar
import com.avatarsdev.freellmapi.ui.components.ScreenHeader
import com.avatarsdev.freellmapi.ui.components.SecretField
import com.avatarsdev.freellmapi.ui.components.StatusChip
import com.avatarsdev.freellmapi.ui.containerViewModel
import com.avatarsdev.freellmapi.ui.theme.AppColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProviderListItem(val entity: ProviderEntity, val type: ProviderType, val keyCount: Int, val modelCount: Int)

class ProvidersViewModel(private val c: AppContainer) : ViewModel() {
    val items: StateFlow<List<ProviderListItem>?> = combine(c.providers.providers, c.providers.keys, c.providers.models) { ps, ks, ms ->
        ps.map { p ->
            ProviderListItem(p, ProviderType.fromId(p.type), ks.count { it.providerId == p.id }, ms.count { it.providerId == p.id && it.enabled })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setEnabled(p: ProviderEntity, enabled: Boolean) = viewModelScope.launch { c.providers.updateProvider(p.copy(enabled = enabled)) }
    fun move(id: Long, up: Boolean) = viewModelScope.launch { c.providers.move(id, up) }

    /** Creates the provider, stores the key encrypted, then syncs the live model list in the background. */
    suspend fun add(type: ProviderType, name: String, baseUrl: String, key: String): Long {
        val id = c.providers.addProvider(type, name, baseUrl, key.ifBlank { null })
        if (key.isNotBlank() || !type.requiresKey) {
            viewModelScope.launch { c.providers.syncModels(id) }
        }
        return id
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(onOpenProvider: (Long) -> Unit) {
    val vm = containerViewModel { ProvidersViewModel(it) }
    val items by vm.items.collectAsStateWithLifecycle()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var pickedType by rememberSaveable { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().testTag("screen_providers")) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ScreenHeader("Providers", subtitle = "Order = routing priority. Tap a provider to manage keys and models.")
            }
            val list = items
            if (list != null && list.isEmpty()) {
                item {
                    AppCard {
                        EmptyState(
                            icon = Icons.Outlined.DeviceHub,
                            title = "Connect your first provider",
                            message = "Keys are encrypted with the Android Keystore and never leave this device except to call that provider.",
                            actionLabel = "Add provider",
                            onAction = { showPicker = true },
                        )
                    }
                }
            }
            itemsIndexed(list.orEmpty(), key = { _, it -> it.entity.id }) { index, item ->
                ProviderCard(
                    item = item,
                    position = index + 1,
                    canMoveUp = index > 0,
                    canMoveDown = index < (list?.lastIndex ?: 0),
                    onOpen = { onOpenProvider(item.entity.id) },
                    onToggle = { vm.setEnabled(item.entity, it) },
                    onMove = { up -> vm.move(item.entity.id, up) },
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showPicker = true },
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text("Add provider") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("add_provider"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
    }

    if (showPicker) {
        ModalBottomSheet(onDismissRequest = { showPicker = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
                Text("Choose a provider", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text("All adapters were built from each provider's API documentation.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(420.dp),
                ) {
                    items(ProviderType.entries) { type ->
                        Surface(
                            onClick = {
                                pickedType = type.id
                                showPicker = false
                            },
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.testTag("pick_${type.id}"),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                ProviderAvatar(type, 36.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(type.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    pickedType?.let { id ->
        AddProviderDialog(
            type = ProviderType.fromId(id),
            onDismiss = { pickedType = null },
            onAdd = { type, name, url, key -> vm.add(type, name, url, key) },
            onAdded = { newId ->
                pickedType = null
                onOpenProvider(newId)
            },
        )
    }
}

@Composable
private fun ProviderCard(
    item: ProviderListItem,
    position: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onMove: (Boolean) -> Unit,
) {
    val status = AppColors.status
    AppCard(onClick = onOpen, contentPadding = PaddingValues(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("#$position", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(30.dp))
            ProviderAvatar(item.type, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.entity.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(item.entity.slug + " · " + item.type.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip(
                        "${item.keyCount} key${if (item.keyCount == 1) "" else "s"}",
                        if (item.keyCount > 0 || !item.type.requiresKey) status.success else status.warning,
                    )
                    StatusChip("${item.modelCount} models", MaterialTheme.colorScheme.primary)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(checked = item.entity.enabled, onCheckedChange = onToggle)
                Row {
                    IconButton(onClick = { onMove(true) }, enabled = canMoveUp) { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Higher priority") }
                    IconButton(onClick = { onMove(false) }, enabled = canMoveDown) { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Lower priority") }
                }
            }
        }
    }
}

@Composable
fun AddProviderDialog(
    type: ProviderType,
    onDismiss: () -> Unit,
    onAdd: suspend (ProviderType, String, String, String) -> Long,
    onAdded: (Long) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(type.displayName) }
    var url by rememberSaveable { mutableStateOf(type.defaultBaseUrl) }
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val canSave = !busy && name.isNotBlank() && url.startsWith("http") && (key.isNotBlank() || !type.requiresKey)

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { ProviderAvatar(type, 44.dp) },
        title = { Text("Add ${type.displayName}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = url, onValueChange = { url = it.trim() }, label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                SecretField(
                    value = key,
                    onValueChange = { key = it },
                    label = if (type.requiresKey) "API key" else "API key (optional)",
                    placeholder = type.keyHint,
                    modifier = Modifier.testTag("api_key_field"),
                )
                Text(
                    "Stored encrypted (AES-256-GCM, Android Keystore). It is never logged or shown again in full.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val id = onAdd(type, name, url, key)
                            key = ""
                            onAdded(id)
                        } catch (e: Exception) {
                            Toast.makeText(context, e.message ?: "Could not add provider", Toast.LENGTH_LONG).show()
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier = Modifier.testTag("save_provider"),
            ) {
                if (busy) CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp) else Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
