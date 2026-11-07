package com.avatarsdev.freellmapi.core.provider

import com.avatarsdev.freellmapi.core.model.ChatRequest
import com.avatarsdev.freellmapi.core.model.ChatResult
import com.avatarsdev.freellmapi.core.model.ProviderException
import com.avatarsdev.freellmapi.core.model.RemoteModel
import com.avatarsdev.freellmapi.core.model.Role
import com.avatarsdev.freellmapi.core.model.StreamEvent
import com.avatarsdev.freellmapi.core.model.TokenUsage
import com.avatarsdev.freellmapi.core.net.GatewayJson
import com.avatarsdev.freellmapi.core.net.HttpEngine
import com.avatarsdev.freellmapi.core.net.arr
import com.avatarsdev.freellmapi.core.net.asObj
import com.avatarsdev.freellmapi.core.net.bool
import com.avatarsdev.freellmapi.core.net.int
import com.avatarsdev.freellmapi.core.net.obj
import com.avatarsdev.freellmapi.core.net.str
import com.avatarsdev.freellmapi.core.security.Redactor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.URLEncoder

/**
 * Anthropic Messages API (POST /v1/messages), per platform.claude.com/docs/en/api.
 *  - Auth: `x-api-key` + required `anthropic-version` header.
 *  - API version 2026-10-21 removed `temperature`, `top_p` and `top_k` (sending them is a 400), so they are never sent.
 *  - `max_tokens` is always sent; `system` is a top-level string.
 *  - Streaming uses named SSE events: message_start, content_block_delta (text_delta), message_delta, message_stop, error.
 */
class AnthropicAdapter(private val http: HttpEngine) : ProviderAdapter {

    private fun base(ep: ProviderEndpoint) = ep.baseUrl.trimSlash().removeSuffix("/v1")

    private fun headers(ep: ProviderEndpoint) = mapOf(
        "x-api-key" to ep.apiKey.trim(),
        "anthropic-version" to API_VERSION,
    )

    override suspend fun listModels(endpoint: ProviderEndpoint): List<RemoteModel> {
        val out = mutableListOf<RemoteModel>()
        var afterId: String? = null
        repeat(10) {
            val url = buildString {
                append(base(endpoint)).append("/v1/models?limit=1000")
                afterId?.let { append("&after_id=").append(URLEncoder.encode(it, "UTF-8")) }
            }
            val root = http.getJson(url, headers(endpoint)).asObj() ?: throw ProviderException(502, "Unexpected models response")
            root.arr("data")?.forEach { el ->
                val m = el.asObj() ?: return@forEach
                val id = m.str("id") ?: return@forEach
                out += RemoteModel(id = id, displayName = m.str("display_name") ?: id)
            }
            if (root.bool("has_more") != true) return out
            afterId = root.str("last_id") ?: return out
        }
        return out
    }

    fun buildBody(req: ChatRequest, stream: Boolean): JsonObject {
        val turns = req.messages.filter { it.role != Role.SYSTEM }.mergeConsecutive()
        if (turns.isEmpty()) throw ProviderException(400, "At least one user message is required")
        return buildJsonObject {
            put("model", req.model)
            put("max_tokens", req.maxTokens ?: DEFAULT_MAX_TOKENS)
            req.messages.systemText()?.let { put("system", it) }
            putJsonArray("messages") {
                for (m in turns) addJsonObject {
                    put("role", if (m.role == Role.ASSISTANT) "assistant" else "user")
                    put("content", m.content)
                }
            }
            if (req.stop.isNotEmpty()) putJsonArray("stop_sequences") { req.stop.forEach { add(it) } }
            if (stream) put("stream", true)
        }
    }

    override suspend fun chat(endpoint: ProviderEndpoint, request: ChatRequest): ChatResult {
        val root = http.postJson(base(endpoint) + "/v1/messages", headers(endpoint), buildBody(request, false))
        return parseMessage(root, request.model)
    }

    override fun stream(endpoint: ProviderEndpoint, request: ChatRequest): Flow<StreamEvent> = flow {
        var inputTokens = 0
        var outputTokens = 0
        var stopReason: String? = null
        var model: String? = null
        http.postSse(base(endpoint) + "/v1/messages", headers(endpoint), buildBody(request, true)).collect { ev ->
            val data = try {
                GatewayJson.parseToJsonElement(ev.data).asObj()
            } catch (_: Exception) {
                null
            } ?: return@collect
            when (ev.event ?: data.str("type")) {
                "message_start" -> {
                    val msg = data.obj("message")
                    model = msg?.str("model")
                    msg?.obj("usage")?.let { u ->
                        inputTokens = u.int("input_tokens") ?: 0
                        outputTokens = u.int("output_tokens") ?: outputTokens
                    }
                }
                "content_block_delta" -> {
                    val delta = data.obj("delta")
                    if (delta?.str("type") == "text_delta") {
                        val text = delta.str("text").orEmpty()
                        if (text.isNotEmpty()) emit(StreamEvent.Delta(text))
                    }
                }
                "message_delta" -> {
                    data.obj("delta")?.str("stop_reason")?.let { stopReason = it }
                    data.obj("usage")?.int("output_tokens")?.let { outputTokens = it }
                    data.obj("usage")?.int("input_tokens")?.let { if (it > 0) inputTokens = it }
                }
                "error" -> {
                    val err = data.obj("error")
                    throw ProviderException(statusForErrorType(err?.str("type")), Redactor.redact(err?.str("message") ?: "Stream error"))
                }
            }
        }
        emit(StreamEvent.Finish(mapStopReason(stopReason), TokenUsage(inputTokens, outputTokens), model))
    }

    companion object {
        const val API_VERSION = "2026-10-21"
        const val DEFAULT_MAX_TOKENS = 4096

        fun parseMessage(root: JsonElement, requestedModel: String): ChatResult {
            val obj = root.asObj() ?: throw ProviderException(502, "Unexpected response")
            if (obj.str("type") == "error") {
                val err = obj.obj("error")
                throw ProviderException(statusForErrorType(err?.str("type")), Redactor.redact(err?.str("message") ?: "Upstream error"))
            }
            val text = obj.arr("content")?.mapNotNull { b ->
                val block = b.asObj() ?: return@mapNotNull null
                if (block.str("type") == "text") block.str("text") else null
            }?.joinToString("").orEmpty()
            val usage = obj.obj("usage")
            return ChatResult(
                text = text,
                finishReason = mapStopReason(obj.str("stop_reason")),
                usage = TokenUsage(usage?.int("input_tokens") ?: 0, usage?.int("output_tokens") ?: 0),
                model = obj.str("model") ?: requestedModel,
            )
        }

        fun mapStopReason(reason: String?): String = when (reason) {
            null, "end_turn", "stop_sequence" -> "stop"
            "max_tokens" -> "length"
            "tool_use" -> "tool_calls"
            "refusal" -> "content_filter"
            else -> reason
        }

        fun statusForErrorType(type: String?): Int = when (type) {
            "rate_limit_error" -> 429
            "overloaded_error" -> 529
            "api_error" -> 500
            "authentication_error" -> 401
            "permission_error" -> 403
            "not_found_error" -> 404
            "request_too_large" -> 413
            else -> 400
        }
    }
}
