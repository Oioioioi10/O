package com.avatarsdev.freellmapi

import com.avatarsdev.freellmapi.core.gateway.GatewayConfig
import com.avatarsdev.freellmapi.core.gateway.GatewaySnapshot
import com.avatarsdev.freellmapi.core.gateway.GatewayStore
import com.avatarsdev.freellmapi.core.gateway.KeySnapshot
import com.avatarsdev.freellmapi.core.gateway.ProviderSnapshot
import com.avatarsdev.freellmapi.core.gateway.RequestOutcome
import com.avatarsdev.freellmapi.core.gateway.RouteSnapshot
import com.avatarsdev.freellmapi.core.model.ChatRequest
import com.avatarsdev.freellmapi.core.model.ChatResult
import com.avatarsdev.freellmapi.core.model.FailureKind
import com.avatarsdev.freellmapi.core.model.RemoteModel
import com.avatarsdev.freellmapi.core.model.StreamEvent
import com.avatarsdev.freellmapi.core.model.TokenUsage
import com.avatarsdev.freellmapi.core.net.HttpEngine
import com.avatarsdev.freellmapi.core.net.SseEvent
import com.avatarsdev.freellmapi.core.provider.ProviderAdapter
import com.avatarsdev.freellmapi.core.provider.ProviderEndpoint
import com.avatarsdev.freellmapi.core.provider.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.util.Collections

/** HTTP engine that records requests and returns canned JSON / SSE events instead of touching the network. */
class FakeHttp(
    private val json: (url: String) -> JsonElement = { error("no json response for $it") },
    private val sse: (url: String) -> List<SseEvent> = { emptyList() },
) : HttpEngine({ error("network disabled in tests") }) {
    data class Call(val url: String, val headers: Map<String, String>, val body: JsonObject?)

    val calls: MutableList<Call> = Collections.synchronizedList(mutableListOf())

    override suspend fun getJson(url: String, headers: Map<String, String>): JsonElement {
        calls += Call(url, headers, null)
        return json(url)
    }

    override suspend fun postJson(url: String, headers: Map<String, String>, body: JsonObject): JsonElement {
        calls += Call(url, headers, body)
        return json(url)
    }

    override fun postSse(url: String, headers: Map<String, String>, body: JsonObject): Flow<SseEvent> {
        calls += Call(url, headers, body)
        return sse(url).asFlow()
    }
}

/** In-memory [GatewayStore]. Keys are "decrypted" from a plain map. */
class FakeStore(
    var providers: List<ProviderSnapshot>,
    var routes: List<RouteSnapshot> = emptyList(),
    var config: GatewayConfig = GatewayConfig(),
    private val secrets: Map<Long, String> = emptyMap(),
) : GatewayStore {
    val outcomes: MutableList<RequestOutcome> = Collections.synchronizedList(mutableListOf())
    val failures: MutableList<Pair<Long, FailureKind>> = Collections.synchronizedList(mutableListOf())
    val successes: MutableList<Long> = Collections.synchronizedList(mutableListOf())

    override suspend fun snapshot() = GatewaySnapshot(providers, routes, config)
    override suspend fun decryptKey(keyId: Long): String? = secrets[keyId]
    override suspend fun record(outcome: RequestOutcome) {
        outcomes += outcome
    }

    override suspend fun markKeyFailure(keyId: Long, kind: FailureKind, message: String, cooldownUntil: Long) {
        failures += keyId to kind
    }

    override suspend fun markKeySuccess(keyId: Long, at: Long) {
        successes += keyId
    }
}

/** Adapter whose behaviour is decided per call from the endpoint (base URL + key) and request. */
class ScriptedAdapter(
    private val onChat: (ProviderEndpoint, ChatRequest) -> ChatResult,
    private val onStream: ((ProviderEndpoint, ChatRequest) -> List<StreamEvent>)? = null,
) : ProviderAdapter {
    val seen: MutableList<Pair<String, String>> = Collections.synchronizedList(mutableListOf())

    override suspend fun listModels(endpoint: ProviderEndpoint): List<RemoteModel> = emptyList()

    override suspend fun chat(endpoint: ProviderEndpoint, request: ChatRequest): ChatResult {
        seen += endpoint.baseUrl to endpoint.apiKey
        return onChat(endpoint, request)
    }

    override fun stream(endpoint: ProviderEndpoint, request: ChatRequest): Flow<StreamEvent> = flow {
        seen += endpoint.baseUrl to endpoint.apiKey
        val events = onStream?.invoke(endpoint, request) ?: run {
            val r = onChat(endpoint, request)
            listOf(StreamEvent.Delta(r.text), StreamEvent.Finish(r.finishReason, r.usage, r.model))
        }
        events.forEach { emit(it) }
    }
}

fun provider(
    id: Long,
    type: ProviderType = ProviderType.OPENAI,
    priority: Int = id.toInt(),
    models: List<String> = listOf("m$id"),
    keys: List<Long> = listOf(id * 10),
    enabled: Boolean = true,
    slug: String = "p$id",
    defaultModel: String? = models.firstOrNull(),
) = ProviderSnapshot(
    id = id,
    type = type,
    name = "Provider$id",
    slug = slug,
    baseUrl = "https://p$id.example",
    enabled = enabled,
    priority = priority,
    defaultModel = defaultModel,
    keys = keys.map { KeySnapshot(it, "key$it", true) },
    models = models,
)

fun ok(text: String, model: String = "m") = ChatResult(text, "stop", TokenUsage(3, 5), model)
