package com.avatarsdev.freellmapi.core.server

import com.avatarsdev.freellmapi.core.model.ChatMessage
import com.avatarsdev.freellmapi.core.model.ChatRequest
import com.avatarsdev.freellmapi.core.model.Role
import com.avatarsdev.freellmapi.core.model.TokenUsage
import com.avatarsdev.freellmapi.core.net.GatewayJson
import com.avatarsdev.freellmapi.core.net.arr
import com.avatarsdev.freellmapi.core.net.asObj
import com.avatarsdev.freellmapi.core.net.asString
import com.avatarsdev.freellmapi.core.net.bool
import com.avatarsdev.freellmapi.core.net.double
import com.avatarsdev.freellmapi.core.net.int
import com.avatarsdev.freellmapi.core.net.obj
import com.avatarsdev.freellmapi.core.net.str
import com.avatarsdev.freellmapi.core.provider.OpenAiCompatAdapter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Parsed client request plus OpenAI-only options the gateway needs to honour. */
data class ParsedChatRequest(val request: ChatRequest, val includeUsage: Boolean)

/** Converts between the OpenAI Chat Completions wire format and the gateway's neutral types. */
object OpenAiProtocol {

    fun parseChatRequest(body: String): ParsedChatRequest {
        val root = try {
            GatewayJson.parseToJsonElement(body).asObj()
        } catch (_: Exception) {
            null
        } ?: throw HttpParseException(400, "Body must be a JSON object")
        val messagesJson = root.arr("messages") ?: throw HttpParseException(400, "'messages' is required")
        val messages = messagesJson.mapNotNull { el ->
            val m = el.asObj() ?: return@mapNotNull null
            val role = Role.fromWire(m.str("role")) ?: return@mapNotNull null
            val text = OpenAiCompatAdapter.contentText(m["content"]) ?: ""
            ChatMessage(role, text)
        }
        if (messages.none { it.role != Role.SYSTEM }) throw HttpParseException(400, "'messages' must contain at least one user message")
        val stop = when (val s = root["stop"]) {
            is JsonPrimitive -> listOfNotNull(s.asString())
            is JsonArray -> s.mapNotNull { it.asString() }
            else -> emptyList()
        }
        val request = ChatRequest(
            model = root.str("model")?.trim().orEmpty().ifEmpty { "auto" },
            messages = messages,
            temperature = root.double("temperature"),
            topP = root.double("top_p"),
            maxTokens = root.int("max_completion_tokens") ?: root.int("max_tokens"),
            stop = stop.take(4),
            stream = root.bool("stream") == true,
        )
        return ParsedChatRequest(request, root.obj("stream_options")?.bool("include_usage") == true)
    }

    fun completion(id: String, created: Long, model: String, text: String, finishReason: String, usage: TokenUsage): String =
        buildJsonObject {
            put("id", id)
            put("object", "chat.completion")
            put("created", created)
            put("model", model)
            putJsonArray("choices") {
                addJsonObject {
                    put("index", 0)
                    putJsonObject("message") {
                        put("role", "assistant")
                        put("content", text)
                    }
                    put("finish_reason", finishReason)
                }
            }
            putUsage(usage)
        }.toString()

    fun chunk(id: String, created: Long, model: String, content: String?, role: Boolean = false, finishReason: String? = null): String =
        buildJsonObject {
            put("id", id)
            put("object", "chat.completion.chunk")
            put("created", created)
            put("model", model)
            putJsonArray("choices") {
                addJsonObject {
                    put("index", 0)
                    putJsonObject("delta") {
                        if (role) put("role", "assistant")
                        if (content != null) put("content", content)
                    }
                    if (finishReason != null) put("finish_reason", finishReason) else put("finish_reason", JsonNull)
                }
            }
        }.toString()

    fun usageChunk(id: String, created: Long, model: String, usage: TokenUsage): String =
        buildJsonObject {
            put("id", id)
            put("object", "chat.completion.chunk")
            put("created", created)
            put("model", model)
            putJsonArray("choices") {}
            putUsage(usage)
        }.toString()

    fun error(message: String, type: String, code: String? = null): String =
        buildJsonObject {
            putJsonObject("error") {
                put("message", message)
                put("type", type)
                if (code != null) put("code", code) else put("code", JsonNull)
            }
        }.toString()

    fun errorType(status: Int): String = when (status) {
        400, 404, 411, 413 -> "invalid_request_error"
        401 -> "authentication_error"
        403 -> "permission_error"
        429 -> "rate_limit_error"
        else -> "api_error"
    }

    fun modelList(ids: List<Pair<String, String>>, created: Long): String = buildJsonObject {
        put("object", "list")
        putJsonArray("data") {
            for ((id, owner) in ids) addJsonObject {
                put("id", id)
                put("object", "model")
                put("created", created)
                put("owned_by", owner)
            }
        }
    }.toString()

    private fun kotlinx.serialization.json.JsonObjectBuilder.putUsage(usage: TokenUsage) {
        putJsonObject("usage") {
            put("prompt_tokens", usage.promptTokens)
            put("completion_tokens", usage.completionTokens)
            put("total_tokens", usage.totalTokens)
        }
    }
}
