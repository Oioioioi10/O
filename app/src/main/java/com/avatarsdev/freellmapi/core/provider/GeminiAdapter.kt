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
import com.avatarsdev.freellmapi.core.net.asString
import com.avatarsdev.freellmapi.core.net.bool
import com.avatarsdev.freellmapi.core.net.int
import com.avatarsdev.freellmapi.core.net.obj
import com.avatarsdev.freellmapi.core.net.str
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.URLEncoder

/**
 * Google Gemini API, native `generateContent` / `streamGenerateContent?alt=sse` (v1beta) with the
 * `x-goog-api-key` header. Google still fully supports generateContent alongside the newer Interactions API.
 * Roles: user / model. System prompts go in `systemInstruction`. Thought parts (`thought: true`) are not answer text.
 */
class GeminiAdapter(private val http: HttpEngine) : ProviderAdapter {

    private fun base(ep: ProviderEndpoint) = ep.baseUrl.trimSlash().removeSuffix("/v1beta").removeSuffix("/v1")

    private fun headers(ep: ProviderEndpoint) = mapOf("x-goog-api-key" to ep.apiKey.trim())

    private fun modelPath(model: String) = model.removePrefix("models/")

    override suspend fun listModels(endpoint: ProviderEndpoint): List<RemoteModel> {
        val out = mutableListOf<RemoteModel>()
        var pageToken: String? = null
        repeat(10) {
            val url = buildString {
                append(base(endpoint)).append("/v1beta/models?pageSize=1000")
                pageToken?.let { append("&pageToken=").append(URLEncoder.encode(it, "UTF-8")) }
            }
            val root = http.getJson(url, headers(endpoint)).asObj() ?: throw ProviderException(502, "Unexpected models response")
            root.arr("models")?.forEach { el ->
                val m = el.asObj() ?: return@forEach
                val methods = m.arr("supportedGenerationMethods")?.mapNotNull { it.asString() }.orEmpty()
                if (methods.isNotEmpty() && "generateContent" !in methods) return@forEach
                val id = modelPath(m.str("name") ?: return@forEach)
                out += RemoteModel(id = id, displayName = m.str("displayName") ?: id, contextWindow = m.int("inputTokenLimit"))
            }
            pageToken = root.str("nextPageToken")
            if (pageToken.isNullOrBlank()) return out
        }
        return out
    }

    fun buildBody(req: ChatRequest): JsonObject {
        val turns = req.messages.filter { it.role != Role.SYSTEM }.mergeConsecutive()
        if (turns.isEmpty()) throw ProviderException(400, "At least one user message is required")
        return buildJsonObject {
            putJsonArray("contents") {
                for (m in turns) addJsonObject {
                    put("role", if (m.role == Role.ASSISTANT) "model" else "user")
                    putJsonArray("parts") { addJsonObject { put("text", m.content) } }
                }
            }
            req.messages.systemText()?.let { sys ->
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") { addJsonObject { put("text", sys) } }
                }
            }
            val hasConfig = req.temperature != null || req.topP != null || req.maxTokens != null || req.stop.isNotEmpty()
            if (hasConfig) putJsonObject("generationConfig") {
                req.temperature?.let { put("temperature", it) }
                req.topP?.let { put("topP", it) }
                req.maxTokens?.let { put("maxOutputTokens", it) }
                if (req.stop.isNotEmpty()) putJsonArray("stopSequences") { req.stop.forEach { add(it) } }
            }
        }
    }

    override suspend fun chat(endpoint: ProviderEndpoint, request: ChatRequest): ChatResult {
        val url = "${base(endpoint)}/v1beta/models/${modelPath(request.model)}:generateContent"
        val root = http.postJson(url, headers(endpoint), buildBody(request)).asObj()
            ?: throw ProviderException(502, "Unexpected response")
        val chunk = parseChunk(root)
        if (chunk.text.isEmpty() && chunk.blockReason != null) {
            throw ProviderException(400, "Prompt blocked by Gemini safety filter: ${chunk.blockReason}")
        }
        return ChatResult(
            text = chunk.text,
            finishReason = mapFinish(chunk.finishReason),
            usage = chunk.usage ?: TokenUsage(),
            model = chunk.model ?: request.model,
        )
    }

    override fun stream(endpoint: ProviderEndpoint, request: ChatRequest): Flow<StreamEvent> = flow {
        val url = "${base(endpoint)}/v1beta/models/${modelPath(request.model)}:streamGenerateContent?alt=sse"
        var finish: String? = null
        var usage: TokenUsage? = null
        var model: String? = null
        var blocked: String? = null
        var any = false
        http.postSse(url, headers(endpoint), buildBody(request)).collect { ev ->
            val obj = try {
                GatewayJson.parseToJsonElement(ev.data).asObj()
            } catch (_: Exception) {
                null
            } ?: return@collect
            obj.obj("error")?.let { err ->
                throw ProviderException(err.int("code") ?: 502, err.str("message") ?: "Stream error")
            }
            val chunk = parseChunk(obj)
            if (chunk.text.isNotEmpty()) {
                any = true
                emit(StreamEvent.Delta(chunk.text))
            }
            chunk.finishReason?.let { finish = it }
            chunk.usage?.let { usage = it }
            chunk.model?.let { model = it }
            chunk.blockReason?.let { blocked = it }
        }
        if (!any && blocked != null) throw ProviderException(400, "Prompt blocked by Gemini safety filter: $blocked")
        emit(StreamEvent.Finish(mapFinish(finish), usage, model))
    }

    data class Chunk(
        val text: String,
        val finishReason: String?,
        val usage: TokenUsage?,
        val model: String?,
        val blockReason: String?,
    )

    companion object {
        fun parseChunk(obj: JsonObject): Chunk {
            val candidate = obj.arr("candidates")?.firstOrNull()?.asObj()
            val text = candidate?.obj("content")?.arr("parts")?.mapNotNull { p ->
                val part = p.asObj() ?: return@mapNotNull null
                if (part.bool("thought") == true) null else part.str("text")
            }?.joinToString("").orEmpty()
            val meta = obj.obj("usageMetadata")
            val usage = meta?.let {
                TokenUsage(
                    promptTokens = it.int("promptTokenCount") ?: 0,
                    completionTokens = (it.int("candidatesTokenCount") ?: 0) + (it.int("thoughtsTokenCount") ?: 0),
                )
            }
            return Chunk(
                text = text,
                finishReason = candidate?.str("finishReason"),
                usage = usage,
                model = obj.str("modelVersion"),
                blockReason = obj.obj("promptFeedback")?.str("blockReason"),
            )
        }

        fun mapFinish(reason: String?): String = when (reason) {
            null, "STOP", "FINISH_REASON_UNSPECIFIED" -> "stop"
            "MAX_TOKENS" -> "length"
            "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII" -> "content_filter"
            else -> reason.lowercase()
        }
    }
}
