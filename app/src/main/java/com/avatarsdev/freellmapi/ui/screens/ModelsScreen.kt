package com.avatarsdev.freellmapi.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avatarsdev.freellmapi.AppContainer
import com.avatarsdev.freellmapi.core.provider.ProviderType
import com.avatarsdev.freellmapi.data.db.ModelEntity
import com.avatarsdev.freellmapi.data.db.ProviderEntity
import com.avatarsdev.freellmapi.ui.components.AppCard
import com.avatarsdev.freellmapi.ui.components.EmptyState
import com.avatarsdev.freellmapi.ui.components.ProviderAvatar
import com.avatarsdev.freellmapi.ui.components.ScreenHeader
import com.avatarsdev.freellmapi.ui.components.StatusChip
import com.avatarsdev.freellmapi.ui.containerViewModel
import com.avatarsdev.freellmapi.ui.theme.AppColors
import com.avatarsdev.freellmapi.ui.theme.MonoStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ModelRow(val model: ModelEntity, val provider: ProviderEntity, val type: ProviderType) {
    val publicId: String get() = "${provider.slug}/${model.modelId}"
}

class ModelsViewModel(private val c: AppContainer) : ViewModel() {
    val providers: StateFlow<List<ProviderEntity>> = c.providers.providers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val rows: StateFlow<List<ModelRow>?> = combine(c.providers.models, c.providers.providers) { models, providers ->
        val byId = providers.associateBy { it.id }
        models.mapNotNull { m -> byId[m.providerId]?.let { ModelRow(m, it, ProviderType.fromId(it.type)) } }
            .sortedWith(compareBy({ it.provider.priority }, { !it.model.enabled }, { it.model.modelId }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    fun setEnabled(id: Long, enabled: Boolean) = viewModelScope.launch { c.providers.setModelEnabled(id, enabled) }

    fun syncAll(onDone: (String) -> Unit) = viewModelScope.launch {
        _syncing.value = true
        val results = providers.value.filter { it.enabled }.map { it.name to c.providers.syncModels(it.id) }
        _syncing.value = false
        val ok = results.count { it.second.ok }
        onDone(if (results.isEmpty()) "No providers to sync" else "Synced $ok of ${results.size} providers")
    }
}

@Composable
fun ModelsScreen() {
    val vm = containerViewModel { ModelsViewModel(it) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var providerFilter by rememberSaveable { mutableStateOf(-1L) }
    var enabledOnly by rememberSaveable { mutableStateOf(false) }
    var freeOnly by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val filtered = rows.orEmpty().filter { r ->
        (providerFilter < 0 || r.provider.id == providerFilter) &&
            (!enabledOnly || r.model.enabled) &&
            (!freeOnly || r.model.isFree) &&
            (query.isBlank() || r.model.modelId.contains(query, true) || r.model.displayName.contains(query, true))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("screen_models"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenHeader("Models", subtitle = "${rows.orEmpty().count { it.model.enabled }} enabled · tap a model to copy its API id") {
                IconButton(onClick = { vm.syncAll { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }, enabled = !syncing) {
                    if (syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Sync, contentDescription = "Sync all")
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("Search models") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = providerFilter < 0, onClick = { providerFilter = -1 }, label = { Text("All") }) }
                item { FilterChip(selected = enabledOnly, onClick = { enabledOnly = !enabledOnly }, label = { Text("Enabled") }) }
                item { FilterChip(selected = freeOnly, onClick = { freeOnly = !freeOnly }, label = { Text("Free") }) }
                items(providers, key = { it.id }) { p ->
                    FilterChip(selected = providerFilter == p.id, onClick = { providerFilter = if (providerFilter == p.id) -1 else p.id }, label = { Text(p.name) })
                }
            }
        }
        if (rows != null && filtered.isEmpty()) {
            item {
                AppCard {
                    EmptyState(
                        icon = Icons.Outlined.Memory,
                        title = if (rows.orEmpty().isEmpty()) "No models yet" else "Nothing matches",
                        message = if (rows.orEmpty().isEmpty()) "Add a provider, then tap sync to pull its live model list." else "Try another search or filter.",
                    )
                }
            }
        }
        items(filtered, key = { it.model.id }) { r ->
            ModelRowCard(
                row = r,
                onToggle = { vm.setEnabled(r.model.id, it) },
                onCopy = {
                    clipboard.setText(AnnotatedString(r.publicId))
                    Toast.makeText(context, "Copied ${r.publicId}", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

@Composable
private fun ModelRowCard(row: ModelRow, onToggle: (Boolean) -> Unit, onCopy: () -> Unit) {
    val status = AppColors.status
    AppCard(onClick = onCopy, contentPadding = PaddingValues(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProviderAvatar(row.type, 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.model.modelId, style = MonoStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (row.model.displayName != row.model.modelId) {
                    Text(row.model.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip(row.provider.name, androidx.compose.ui.graphics.Color(row.type.brandColor))
                    if (row.model.isFree) StatusChip("FREE", status.success)
                    row.model.contextWindow?.let { StatusChip("${it / 1000}K ctx", MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (row.provider.defaultModel == row.model.modelId) StatusChip("default", MaterialTheme.colorScheme.primary)
                }
            }
            Switch(checked = row.model.enabled, onCheckedChange = onToggle, modifier = Modifier.padding(start = 4.dp))
        }
    }
}
