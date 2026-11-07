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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avatarsdev.freellmapi.core.security.Redactor
import com.avatarsdev.freellmapi.ui.LocalAppContainer
import com.avatarsdev.freellmapi.ui.components.AppCard
import com.avatarsdev.freellmapi.ui.components.CodeBlock
import com.avatarsdev.freellmapi.ui.components.ConfirmDialog
import com.avatarsdev.freellmapi.ui.components.PulseDot
import com.avatarsdev.freellmapi.ui.components.ScreenHeader
import com.avatarsdev.freellmapi.ui.components.SectionTitle
import com.avatarsdev.freellmapi.ui.rememberServerToggle
import com.avatarsdev.freellmapi.ui.theme.AppColors
import com.avatarsdev.freellmapi.ui.theme.MonoStyle

@Composable
fun ServerScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val state by container.server.state.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val toggle = rememberServerToggle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val status = AppColors.status

    var gatewayKey by remember { mutableStateOf(container.settings.gatewayKey()) }
    var showKey by remember { mutableStateOf(false) }
    var portText by remember(settings.serverPort) { mutableStateOf(settings.serverPort.toString()) }
    var confirmLan by remember { mutableStateOf(false) }
    var confirmRegen by remember { mutableStateOf(false) }
    val port = if (state.running) state.port else settings.serverPort
    val baseUrl = "http://127.0.0.1:$port/v1"

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("screen_server"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader("Local API server", subtitle = "OpenAI-compatible endpoint on this device", onBack = onBack) }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulseDot(if (state.running) status.success else MaterialTheme.colorScheme.onSurfaceVariant, active = state.running, size = 12.dp)
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (state.running) "Running" else "Stopped", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (state.running) (if (state.lanEnabled) "Loopback + private LAN" else "Loopback only (127.0.0.1)") else "Start to accept requests",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = { toggle(!state.running) },
                        colors = if (state.running) ButtonDefaults.buttonColors(containerColor = status.danger) else ButtonDefaults.buttonColors(),
                        modifier = Modifier.testTag("server_screen_toggle"),
                    ) {
                        Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.running) "Stop" else "Start")
                    }
                }
                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = status.danger, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                CodeBlock(baseUrl, copyLabel = "Base URL copied")
                state.lanUrls.forEach {
                    Spacer(Modifier.height(6.dp))
                    CodeBlock(it, copyLabel = "LAN URL copied")
                }
            }
        }
        item { SectionTitle("Gateway API key") }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (showKey) gatewayKey else Redactor.mask(gatewayKey),
                        style = MonoStyle,
                        modifier = Modifier.weight(1f).testTag("gateway_key_text"),
                    )
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = if (showKey) "Hide key" else "Show key")
                    }
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(gatewayKey))
                        Toast.makeText(context, "Gateway key copied", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy key") }
                }
                Text(
                    "Clients send it as Authorization: Bearer <key>. Requests without it get 401; 10 wrong keys from one address lock it out for a minute.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { confirmRegen = true }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Regenerate key")
                }
            }
        }
        item { SectionTitle("Network") }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { v -> portText = v.filter { it.isDigit() }.take(5) },
                        label = { Text("Port") },
                        singleLine = true,
                        enabled = !state.running,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(140.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    val parsed = portText.toIntOrNull()
                    TextButton(
                        enabled = !state.running && parsed != null && parsed in 1024..65535 && parsed != settings.serverPort,
                        onClick = { container.settings.update { it.copy(serverPort = parsed ?: it.serverPort) } },
                    ) { Text("Apply") }
                }
                Text("Ports 1024–65535. Stop the server to change it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Wifi, contentDescription = null, tint = if (settings.allowLan) status.warning else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Allow private LAN clients", style = MaterialTheme.typography.titleSmall)
                        Text("Other devices on your Wi-Fi (192.168.x.x, 10.x.x.x). Public internet addresses are always refused.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.allowLan,
                        onCheckedChange = { on ->
                            if (on) {
                                confirmLan = true
                            } else {
                                container.settings.update { it.copy(allowLan = false) }
                                container.server.restartIfRunning()
                            }
                        },
                    )
                }
            }
        }
        item { SectionTitle("Quick start") }
        item {
            AppCard {
                Text("curl", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                CodeBlock(
                    """
                    curl $baseUrl/chat/completions \
                      -H "Authorization: Bearer ${'$'}FREELLMAPI_KEY" \
                      -H "Content-Type: application/json" \
                      -d '{"model":"auto","messages":[{"role":"user","content":"Hello"}]}'
                    """.trimIndent(),
                )
                Spacer(Modifier.height(12.dp))
                Text("OpenAI SDK (Python)", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                CodeBlock(
                    """
                    from openai import OpenAI
                    client = OpenAI(base_url="$baseUrl", api_key="<gateway key>")
                    r = client.chat.completions.create(model="auto", messages=[{"role": "user", "content": "Hi"}])
                    print(r.choices[0].message.content)
                    """.trimIndent(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "model can be: auto · a route alias · an enabled model id · <provider>/<model> (see Models tab)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Security, contentDescription = null, tint = status.success)
                    Spacer(Modifier.width(10.dp))
                    Text("Security", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                listOf(
                    "Binds to 127.0.0.1 unless LAN access is on",
                    "Only loopback and private network addresses are accepted",
                    "Every /v1 request needs the gateway key (constant-time check)",
                    "Provider keys stay encrypted; logs store no prompts or keys",
                    "No CORS headers, so web pages cannot call the API",
                ).forEach {
                    Text("•  $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (confirmLan) {
        AlertDialog(
            onDismissRequest = { confirmLan = false },
            icon = { Icon(Icons.Outlined.Wifi, contentDescription = null) },
            title = { Text("Allow LAN access?") },
            text = {
                Text(
                    "Devices on the same private network will be able to reach the gateway. They still need the gateway key. " +
                        "Use only on networks you trust and never forward this port on your router.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    container.settings.update { it.copy(allowLan = true) }
                    container.server.restartIfRunning()
                    confirmLan = false
                }) { Text("Allow") }
            },
            dismissButton = { TextButton(onClick = { confirmLan = false }) { Text("Cancel") } },
        )
    }
    if (confirmRegen) {
        ConfirmDialog(
            title = "Regenerate gateway key?",
            message = "Apps using the current key will get 401 until you update them.",
            confirmLabel = "Regenerate",
            onConfirm = {
                gatewayKey = container.settings.regenerateGatewayKey()
                container.server.reloadKey()
            },
            onDismiss = { confirmRegen = false },
        )
    }
}
