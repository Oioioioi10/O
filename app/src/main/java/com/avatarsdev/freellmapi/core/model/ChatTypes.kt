package com.avatarsdev.freellmapi.core.model

/** Provider-neutral chat types used inside the gateway. Adapters translate to/from each wire format. */
enum class Role(val wire: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant");

    companion object {
        fun fromWire(value: String?): Role? = when (value?.lowercase()) {
            "system", "developer" -> SYSTEM
            "user" -> USER
            "assistant", "model" -> ASSISTANT
            else -> null
        }
    }
}

data class ChatMessage(val role: Role, val content: String)

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxTokens: Int? = null,
    val stop: List<String> = emptyList(),
    val stream: Boolean = false,
)

data class TokenUsage(val promptTokens: Int = 0, val completionTokens: Int = 0) {
    val totalTokens: Int get() = promptTokens + completionTokens
}

data class ChatResult(
    val text: String,
    val finishReason: String,
    val usage: TokenUsage,
    val model: String,
)

/** Events produced by a streaming adapter call. */
sealed interface StreamEvent {
    data class Delta(val text: String) : StreamEvent
    data class Finish(val finishReason: String?, val usage: TokenUsage?, val model: String?) : StreamEvent
}

data class RemoteModel(
    val id: String,
    val displayName: String = id,
    val contextWindow: Int? = null,
    val isFree: Boolean = false,
)

enum class FailureKind { NETWORK, RATE_LIMIT, AUTH, SERVER, REQUEST }

/**
 * Error returned by an upstream provider. [statusCode] is 0 for network failures.
 * The message is always passed through the redactor before it is created, so it never carries secrets.
 */
class ProviderException(
    val statusCode: Int,
    message: String,
    val retryAfterMs: Long? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    val kind: FailureKind
        get() = when (statusCode) {
            0 -> FailureKind.NETWORK
            429 -> FailureKind.RATE_LIMIT
            401, 403 -> FailureKind.AUTH
            408, 409, 425, 529 -> FailureKind.SERVER
            in 500..599 -> FailureKind.SERVER
            else -> FailureKind.REQUEST
        }
}
