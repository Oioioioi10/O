package com.avatarsdev.freellmapi.core.provider

import com.avatarsdev.freellmapi.core.model.ChatRequest
import com.avatarsdev.freellmapi.core.model.ChatResult
import com.avatarsdev.freellmapi.core.model.ProviderException
import com.avatarsdev.freellmapi.core.model.RemoteModel
import com.avatarsdev.freellmapi.core.model.StreamEvent
import com.avatarsdev.freellmapi.core.model.TokenUsage
import com.avatarsdev.freellmapi.core.net.GatewayJson
import com.avatarsdev.freellmapi.core.net.HttpEngine
import com.avatarsdev.freellmapi.core.net.arr
import com.avatarsdev.freellmapi.core.net.asArr
import com.avatarsdev.freellmapi.core.net.asObj
import com.avatarsdev.freellmapi.core.net.asString
import com.avatarsdev.freellmapi.core.net.int
import com.avatarsdev.freellmapi.core.net.obj
import com.avatarsdev.freellmapi.core.net.str
import com.avatarsdev.freellmapi.core.security.Redactor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * OpenAI Chat Completions protocol. Used for OpenAI, DeepSeek, Groq, OpenRouter, Qwen (DashScope compatible-mode)
 * and any custom OpenAI-compatible server (Ollama, LM Studio, vLLM…).
 *
 * Provider quirks handled here (from each provider's docs):
 *  - OpenAI: `max_completion_tokens` replaces the deprecated `max_tokens`.
 *  - Groq: rejects `logprobs`, `logit_bias`, `top_logprobs`, `messages[].name`, `n != 1` — never sent.
 *    Usage arrives in `x_groq.usage` on the last stream chunk.
 *  - OpenRouter: attribution header `X-OpenRouter-Title`; SSE comment keep-alives are skipped by the parser.
 *  - DeepSeek: `reasoning_content` is not part of the answer text.
 */
class OpenAiCompatAdapter(private val http: HttpEngine) : ProviderAdapter {

    private fun headers(ep: ProviderEndpoint): Map<String, String> {
        val h = linkedMapOf<String, String>()
        if (ep.apiKey.isNotBlank()) h["Authorization"] = "Bearer ${ep.apiKey.trim()}"
        if (ep.type == ProviderType.OPENROUTER) {
            h["X-OpenRouter-Title"] = "FreeLLMAPI Android"
            h["X-Title"] = "FreeLLMAPI Android"
        }
        return h
    }

    override suspend fun listModels(endpoint: ProviderEndpoint): List<RemoteModel> {
        val root = http.getJson(endpoint.baseUrl.trimSlash() + "/models", headers(endpoint))
        val items = root.asObj()?.arr("data") ?: root.asObj()?.arr("models") ?: root.asArr()
            ?: throw ProviderException(502, "Unexpected models response")
        return items.mapNotNull { el ->
            val m = el.asObj() ?: return@mapNotNull null
            val id = m.str("id") ?: m.str("name") ?: return@mapNotNull null
            val pricing = m.obj("pricing")
            val free = endpoint.type == ProviderType.OPENROUTER && (
                id.endsWith(":free") || id == "openrouter/free" ||
                    (pricing?.str("prompt") == "0" && pricing?.str("completion") == "0")
                )
            RemoteModel(
                id = id,
                displayName = m.str("name")?.takeIf { endpoint.type == ProviderType.OPENROUTER } ?: id,
                contextWindow = m.int("context_length") ?: m.int("context_window"),
                isFree = free,
            )
        }.sortedBy { it.id }
    }

    fun buildBody(ep: ProviderEndpoint, req: ChatRequest, stream: Boolean): JsonObject = buildJsonObject {
        put("model", req.model)
        putJsonArray("messages") {
            for (m in req.messages) addJsonObject {
                put("role", m.role.wire)
                put("content", m.content)
            }
        }
        req.temperature?.let { put("temperature", it) }
        req.topP?.let { put("top_p", it) }
        req.maxTokens?.let {
            if (ep.type == ProviderType.OPENAI) put("max_completion_tokens", it) else put("max_tokens", it)
        }
        if (req.stop.isNotEmpty()) putJsonArray("stop") { req.stop.forEach { add(it) } }
        if (stream) {
            put("stream", true)
            if (ep.type != ProviderType.GROQ) putJsonObject("stream_options") { put("include_usage", true) }
        }
    }

    override suspend fun chat(endpoint: ProviderEndpoint, request: ChatRequest): ChatResult {
        val root = http.postJson(endpoint.baseUrl.trimSlash() + "/chat/completions", headers(endpoint), buildBody(endpoint, request, false))
        return parseCompletion(root, request.model)
    }

    override fun stream(endpoint: ProviderEndpoint, request: ChatRequest): Flow<StreamEvent> = flow {
        var finishReason: String? = null
        var usage: TokenUsage? = null
        var model: String? = null
        http.postSse(endpoint.baseUrl.trimSlash() + "/chat/completions", headers(endpoint), buildBody(endpoint, request, true))
            .collect { ev ->
                val data = ev.data.trim()
                if (data == "[DONE]" || data.isEmpty()) return@collect
                val chunk = try {
                    GatewayJson.parseToJsonElement(data).asObj()
                } catch (_: Exception) {
                    null
                } ?: return@collect
                chunk.obj("error")?.let { err ->
                    val code = err.int("code") ?: 502
                    throw ProviderException(if (code in 400..599) code else 502, Redactor.redact(err.str("message") ?: "Stream error"))
                }
                chunk.str("model")?.let { model = it }
                val choice = chunk.arr("choices")?.firstOrNull()?.asObj()
                val text = choice?.obj("delta")?.let { contentText(it["content"]) }
                if (!text.isNullOrEmpty()) emit(StreamEvent.Delta(text))
                choice?.str("finish_reason")?.let { finishReason = it }
                (chunk.obj("usage") ?: chunk.obj("x_groq")?.obj("usage"))?.let { usage = parseUsage(it) }
            }
        emit(StreamEvent.Finish(finishReason ?: "stop", usage, model))
    }

    companion object {
        fun parseCompletion(root: JsonElement, requestedModel: String): ChatResult {
            val obj = root.asObj() ?: throw ProviderException(502, "Unexpected response")
            obj.obj("error")?.let { throw ProviderException(502, Redactor.redact(it.str("message") ?: "Upstream error")) }
            val choice = obj.arr("choices")?.firstOrNull()?.asObj()
                ?: throw ProviderException(502, "Response has no choices")
            val message = choice.obj("message")
            return ChatResult(
                text = contentText(message?.get("content")).orEmpty(),
                finishReason = choice.str("finish_reason") ?: "stop",
                usage = obj.obj("usage")?.let(::parseUsage) ?: TokenUsage(),
                model = obj.str("model") ?: requestedModel,
            )
        }

        fun parseUsage(u: JsonObject): TokenUsage = TokenUsage(
            promptTokens = u.int("prompt_tokens") ?: u.int("input_tokens") ?: 0,
            completionTokens = u.int("completion_tokens") ?: u.int("output_tokens") ?: 0,
        )

        /** Content may be a string, null, or an array of content parts. */
        fun contentText(el: JsonElement?): String? = when {
            el == null -> null
            el is JsonPrimitive -> el.asString()
            el.asArr() != null -> el.asArr()!!.mapNotNull { p ->
                val o = p.asObj()
                if (o == null) p.asString() else if (o.str("type") == null || o.str("type") == "text") o.str("text") else null
            }.joinToString("")
            else -> null
        }
    }
}
