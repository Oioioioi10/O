package com.avatarsdev.freellmapi.core.provider

import com.avatarsdev.freellmapi.core.model.ChatMessage
import com.avatarsdev.freellmapi.core.model.ChatRequest
import com.avatarsdev.freellmapi.core.model.ChatResult
import com.avatarsdev.freellmapi.core.model.RemoteModel
import com.avatarsdev.freellmapi.core.model.Role
import com.avatarsdev.freellmapi.core.model.StreamEvent
import com.avatarsdev.freellmapi.core.net.HttpEngine
import kotlinx.coroutines.flow.Flow

/** Where and how to reach one provider with one key. [apiKey] is plaintext only in memory, for one request. */
data class ProviderEndpoint(
    val type: ProviderType,
    val baseUrl: String,
    val apiKey: String,
) {
    override fun toString(): String = "ProviderEndpoint(type=$type, baseUrl=$baseUrl, apiKey=***)"
}

/** Translates the gateway's neutral chat format to one provider's wire protocol. */
interface ProviderAdapter {
    suspend fun listModels(endpoint: ProviderEndpoint): List<RemoteModel>
    suspend fun chat(endpoint: ProviderEndpoint, request: ChatRequest): ChatResult
    fun stream(endpoint: ProviderEndpoint, request: ChatRequest): Flow<StreamEvent>
}

/** Picks the adapter for an [ApiStyle]. */
class AdapterRegistry(http: HttpEngine) {
    private val openAi = OpenAiCompatAdapter(http)
    private val anthropic = AnthropicAdapter(http)
    private val gemini = GeminiAdapter(http)

    fun forType(type: ProviderType): ProviderAdapter = when (type.style) {
        ApiStyle.OPENAI -> openAi
        ApiStyle.ANTHROPIC -> anthropic
        ApiStyle.GEMINI -> gemini
    }
}

internal fun String.trimSlash(): String = trim().trimEnd('/')

/** Joins consecutive messages with the same role (Anthropic and Gemini expect alternating turns). */
internal fun List<ChatMessage>.mergeConsecutive(): List<ChatMessage> {
    val out = ArrayList<ChatMessage>(size)
    for (m in this) {
        val last = out.lastOrNull()
        if (last != null && last.role == m.role) {
            out[out.lastIndex] = ChatMessage(m.role, last.content + "\n\n" + m.content)
        } else {
            out += m
        }
    }
    return out
}

internal fun List<ChatMessage>.systemText(): String? =
    filter { it.role == Role.SYSTEM }.joinToString("\n\n") { it.content }.takeIf { it.isNotBlank() }
