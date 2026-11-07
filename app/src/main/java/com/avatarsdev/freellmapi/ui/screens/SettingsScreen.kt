package com.avatarsdev.freellmapi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brightness2
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avatarsdev.freellmapi.BuildConfig
import com.avatarsdev.freellmapi.data.ThemeMode
import com.avatarsdev.freellmapi.ui.LocalAppContainer
import com.avatarsdev.freellmapi.ui.components.AppCard
import com.avatarsdev.freellmapi.ui.components.LabeledValue
import com.avatarsdev.freellmapi.ui.components.ScreenHeader
import com.avatarsdev.freellmapi.ui.components.SectionTitle
import com.avatarsdev.freellmapi.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val status = AppColors.status

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("screen_settings"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader("Settings", onBack = onBack) }
        item { SectionTitle("Appearance") }
        item {
            AppCard {
                listOf(
                    Triple(ThemeMode.DARK, "Dark", Icons.Outlined.Brightness2),
                    Triple(ThemeMode.LIGHT, "Light", Icons.Outlined.WbSunny),
                    Triple(ThemeMode.SYSTEM, "Follow system", Icons.Outlined.PhoneAndroid),
                ).forEach { (mode, label, icon) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = settings.themeMode == mode, role = Role.RadioButton) { container.settings.update { it.copy(themeMode = mode) } }
                            .testTag("theme_${mode.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        RadioButton(selected = settings.themeMode == mode, onClick = null)
                    }
                }
            }
        }
        item { SectionTitle("Requests") }
        item {
            AppCard {
                Text("Upstream read timeout", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Choice(listOf(30, 60, 120, 300), settings.requestTimeoutSec, { "${it}s" }) { v -> container.settings.update { it.copy(requestTimeoutSec = v) } }
                Spacer(Modifier.height(14.dp))
                Text("Keep request logs for", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Choice(listOf(1, 7, 30, 90), settings.logRetentionDays, { if (it == 1) "1 day" else "$it days" }) { v -> container.settings.update { it.copy(logRetentionDays = v) } }
            }
        }
        item { SectionTitle("Security") }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Security, contentDescription = null, tint = status.success)
                    Spacer(Modifier.width(10.dp))
                    Text("How your keys are protected", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                listOf(
                    "Provider keys and the gateway key are encrypted with AES-256-GCM using a non-exportable Android Keystore key.",
                    "Keys are decrypted in memory only for the request that needs them and are masked everywhere in the UI.",
                    "Error messages are scrubbed of anything that looks like a key before they are logged.",
                    "App backup is disabled, so encrypted data never leaves the device.",
                    "The local API requires the gateway key and refuses public-internet addresses.",
                ).forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth()) }
            }
        }
        item { SectionTitle("About") }
        item {
            AppCard {
                LabeledValue("App", "FreeLLMAPI (working title)")
                LabeledValue("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                LabeledValue("Providers", "OpenAI · Anthropic · Gemini · DeepSeek · Groq · OpenRouter · Qwen · Custom")
                LabeledValue("Endpoint", "POST /v1/chat/completions, GET /v1/models", mono = true)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Choice(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            SegmentedButton(
                selected = o == selected,
                onClick = { onSelect(o) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
            ) { Text(label(o), maxLines = 1) }
        }
    }
}
