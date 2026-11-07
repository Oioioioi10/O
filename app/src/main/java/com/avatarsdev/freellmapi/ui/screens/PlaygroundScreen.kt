package com.avatarsdev.freellmapi.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.avatarsdev.freellmapi.AppContainer
import com.avatarsdev.freellmapi.core.gateway.Attempt
import com.avatarsdev.freellmapi.core.gateway.GatewayException
import com.avatarsdev.freellmapi.core.gateway.GatewayStreamEvent
import com.avatarsdev.freellmapi.core.model.ChatMessage
import com.avatarsdev.freellmapi.core.model.ChatRequest
import com.avatarsdev.freellmapi.core.model.Role
import com.avatarsdev.freellmapi.ui.components.AppCard
import com.avatarsdev.freellmapi.ui.components.EmptyState
import com.avatarsdev.freellmapi.ui.components.Expandable
import com.avatarsdev.freellmapi.ui.components.ScreenHeader
import com.avatarsdev.freellmapi.ui.components.SelectField
import com.avatarsdev.freellmapi.ui.components.StatusChip
import com.avatarsdev.freellmapi.ui.components.formatLatency
import com.avatarsdev.freellmapi.ui.containerViewModel
import com.avatarsdev.freellmapi.ui.theme.AppColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TurnMeta(
    val provider: String,
    val model: String,
    val latencyMs: Long,
    val promptTokens: Int,
    val completionTokens: Int,
    val attempts: List<Attempt>,
)

data class ChatTurn(
    val id: Long,
    val role: Role,
    val text: String,
    val meta: TurnMeta? = null,
    val error: String? = null,
    val streaming: Boolean = false,
)

data class PlaygroundParams(
    val model: String = "auto",
    val systemPrompt: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1024,
    val stream: Boolean = true,
)

class PlaygroundViewModel(private val c: AppContainer) : ViewModel() {
    val modelOptions: StateFlow<List<String>> = combine(c.providers.providers, c.providers.models, c.activity.routes) { providers, models, routes ->
        val enabledProviders = providers.filter { it.enabled }.sortedBy { it.priority }
        listOf("auto") + routes.map { it.alias }.filter { it != "auto" } +
            enabledProviders.flatMap { p -> models.filter { it.providerId == p.id && it.enabled }.map { "${p.slug}/${it.modelId}" } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf("auto"))

    private val _params = MutableStateFlow(PlaygroundParams())
    val params: StateFlow<PlaygroundParams> = _params.asStateFlow()
    private val _turns = MutableStateFlow<List<ChatTurn>>(emptyList())
    val turns: StateFlow<List<ChatTurn>> = _turns.asStateFlow()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    private var job: Job? = null
    private var nextId = 1L

    fun updateParams(transform: (PlaygroundParams) -> PlaygroundParams) = _params.update(transform)

    fun clear() {
        job?.cancel()
        _turns.value = emptyList()
    }

    fun stop() {
        job?.cancel()
    }

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || _running.value) return
        val p = _params.value
        val history = _turns.value.filter { it.error == null && it.text.isNotBlank() }
        val messages = buildList {
            if (p.systemPrompt.isNotBlank()) add(ChatMessage(Role.SYSTEM, p.systemPrompt.trim()))
            history.forEach { add(ChatMessage(it.role, it.text)) }
            add(ChatMessage(Role.USER, prompt))
        }
        val request = ChatRequest(model = p.model, messages = messages, temperature = p.temperature.toDouble(), maxTokens = p.maxTokens, stream = p.stream)
        val replyId = nextId + 1
        _turns.update { it + ChatTurn(nextId, Role.USER, prompt) + ChatTurn(replyId, Role.ASSISTANT, "", streaming = true) }
        nextId += 2
        _running.value = true
        job = viewModelScope.launch {
            try {
                if (p.stream) {
                    c.engine.stream(request, source = "playground").collect { ev ->
                        when (ev) {
                            is GatewayStreamEvent.Started -> Unit
                            is GatewayStreamEvent.Delta -> edit(replyId) { it.copy(text = it.text + ev.text) }
                            is GatewayStreamEvent.Completed -> edit(replyId) {
                                it.copy(
                                    streaming = false,
                                    meta = TurnMeta(ev.providerName, ev.model, ev.latencyMs, ev.usage.promptTokens, ev.usage.completionTokens, ev.attempts),
                                )
                            }
                        }
                    }
                } else {
                    val res = c.engine.complete(request, source = "playground")
                    edit(replyId) {
                        it.copy(
                            text = res.result.text,
                            streaming = false,
                            meta = TurnMeta(res.providerName, res.model, res.latencyMs, res.result.usage.promptTokens, res.result.usage.completionTokens, res.attempts),
                        )
                    }
                }
            } catch (e: CancellationException) {
                edit(replyId) { it.copy(streaming = false, error = if (it.text.isEmpty()) "Stopped" else null) }
                throw e
            } catch (e: GatewayException) {
                edit(replyId) { it.copy(streaming = false, error = e.message, meta = TurnMeta("", "", 0, 0, 0, e.attempts)) }
            } catch (e: Exception) {
                edit(replyId) { it.copy(streaming = false, error = e.message ?: "Request failed") }
            } finally {
                _running.value = false
            }
        }
    }

    private fun edit(id: Long, change: (ChatTurn) -> ChatTurn) {
        _turns.update { list -> list.map { if (it.id == id) change(it) else it } }
    }
}

@Composable
fun PlaygroundScreen() {
    val vm = containerViewModel { PlaygroundViewModel(it) }
    val options by vm.modelOptions.collectAsStateWithLifecycle()
    val params by vm.params.collectAsStateWithLifecycle()
    val turns by vm.turns.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var showParams by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(turns.size, turns.lastOrNull()?.text?.length) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.lastIndex)
    }

    Column(Modifier.fillMaxSize().imePadding().testTag("screen_playground")) {
        ScreenHeader("Playground", subtitle = "Test routing, fallback and streaming") {
            IconButton(onClick = { showParams = !showParams }) { Icon(Icons.Outlined.Tune, contentDescription = "Parameters") }
            IconButton(onClick = { vm.clear() }, enabled = turns.isNotEmpty()) { Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear chat") }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            SelectField(
                label = "Model",
                options = options,
                selected = params.model,
                optionLabel = { it },
                onSelect = { m -> vm.updateParams { it.copy(model = m) } },
            )
            Expandable(showParams) {
                AppCard(Modifier.padding(top = 10.dp)) {
                    OutlinedTextField(
                        value = params.systemPrompt,
                        onValueChange = { v -> vm.updateParams { it.copy(systemPrompt = v) } },
                        label = { Text("System prompt") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Temperature ${"%.1f".format(params.temperature)}", style = MaterialTheme.typography.labelLarge)
                    Slider(value = params.temperature, onValueChange = { v -> vm.updateParams { it.copy(temperature = v) } }, valueRange = 0f..2f, steps = 19)
                    Text("Max tokens ${params.maxTokens}", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = params.maxTokens.toFloat(),
                        onValueChange = { v -> vm.updateParams { it.copy(maxTokens = (v / 64).toInt().coerceAtLeast(1) * 64) } },
                        valueRange = 64f..8192f,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Stream tokens", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = params.stream, onCheckedChange = { v -> vm.updateParams { it.copy(stream = v) } })
                    }
                    Text(
                        "Anthropic's current API ignores temperature; Gemini 3.6+ ignores custom temperature.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (turns.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        title = "Send a test prompt",
                        message = "Requests go through the same router, key rotation and fallback as the local API, and appear in Logs.",
                    )
                }
            }
            items(turns, key = { it.id }) { turn -> ChatBubble(turn) }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, bottom = 10.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Message ${params.model}") },
                maxLines = 5,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.weight(1f).testTag("prompt_input"),
            )
            Spacer(Modifier.width(8.dp))
            if (running) {
                FilledIconButton(onClick = { vm.stop() }, modifier = Modifier.size(52.dp)) { Icon(Icons.Outlined.Stop, contentDescription = "Stop") }
            } else {
                FilledIconButton(
                    onClick = {
                        vm.send(input)
                        input = ""
                    },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.size(52.dp).testTag("send_prompt"),
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
            }
        }
    }
}

@Composable
private fun ChatBubble(turn: ChatTurn) {
    val isUser = turn.role == Role.USER
    val status = AppColors.status
    Box(Modifier.fillMaxWidth(), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 20.dp,
            ),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 560.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                when {
                    turn.streaming && turn.text.isEmpty() -> TypingDots()
                    turn.text.isNotEmpty() -> SelectionContainer { Text(turn.text, style = MaterialTheme.typography.bodyLarge) }
                }
                turn.error?.let {
                    Text(it, color = status.danger, style = MaterialTheme.typography.bodyMedium)
                }
                turn.meta?.let { m ->
                    Spacer(Modifier.height(8.dp))
                    if (m.provider.isNotEmpty()) {
                        Text(
                            "${m.provider} · ${m.model} · ${formatLatency(m.latencyMs)} · ${m.promptTokens}→${m.completionTokens} tok",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (m.attempts.size > 1 || m.attempts.any { !it.ok }) {
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            m.attempts.forEach { a ->
                                StatusChip(
                                    (if (a.ok) "✓ " else "✗ ") + "${a.providerName}/${a.model}" + (a.statusCode?.let { " · $it" } ?: ""),
                                    if (a.ok) status.success else status.warning,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingDots() {
    val t = rememberInfiniteTransition(label = "typing")
    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            val a by t.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(600, delayMillis = i * 150), RepeatMode.Reverse),
                label = "dot$i",
            )
            Box(Modifier.size(8.dp).alpha(a).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}
