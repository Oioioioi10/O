package com.avatarsdev.freellmapi.core.gateway

import com.avatarsdev.freellmapi.core.model.ChatRequest
import com.avatarsdev.freellmapi.core.model.ChatResult
import com.avatarsdev.freellmapi.core.model.FailureKind
import com.avatarsdev.freellmapi.core.model.ProviderException
import com.avatarsdev.freellmapi.core.model.StreamEvent
import com.avatarsdev.freellmapi.core.model.TokenUsage
import com.avatarsdev.freellmapi.core.provider.ProviderAdapter
import com.avatarsdev.freellmapi.core.provider.ProviderEndpoint
import com.avatarsdev.freellmapi.core.provider.ProviderType
import com.avatarsdev.freellmapi.core.security.Redactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Raised when every candidate failed (or none matched). [statusCode] is suitable for the HTTP response. */
class GatewayException(
    val statusCode: Int,
    message: String,
    val attempts: List<Attempt> = emptyList(),
) : Exception(message)

data class GatewayResponse(
    val result: ChatResult,
    val providerId: Long,
    val providerName: String,
    val providerType: ProviderType,
    val model: String,
    val attempts: List<Attempt>,
    val latencyMs: Long,
)

sealed interface GatewayStreamEvent {
    data class Started(val providerName: String, val providerType: ProviderType, val model: String) : GatewayStreamEvent
    data class Delta(val text: String) : GatewayStreamEvent
    data class Completed(
        val finishReason: String,
        val usage: TokenUsage,
        val providerName: String,
        val model: String,
        val attempts: List<Attempt>,
        val latencyMs: Long,
    ) : GatewayStreamEvent
}

/**
 * Executes a chat request with model routing, key rotation and automatic fallback.
 *
 * Per candidate it rotates through the provider's healthy keys. Failure handling:
 *  - 429 rate limit → that key cools down (Retry-After or 60 s), next key, then next candidate
 *  - 401/403 → key cools down for 10 min, next key
 *  - 5xx / timeout / network → short cooldown, next candidate
 *  - other 4xx (bad model, bad params) → next candidate
 * Streaming can only fall back before the first token has been sent to the client.
 */
class GatewayEngine(
    private val store: GatewayStore,
    private val adapterFor: (ProviderType) -> ProviderAdapter,
    private val router: ModelRouter = ModelRouter(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val cooldowns = ConcurrentHashMap<Long, Long>()
    private val keyCursor = ConcurrentHashMap<Long, AtomicInteger>()

    fun isCoolingDown(keyId: Long): Boolean = (cooldowns[keyId] ?: 0L) > clock()

    fun clearCooldowns() = cooldowns.clear()

    suspend fun preview(model: String): List<Candidate> = router.resolve(model, store.snapshot())

    suspend fun complete(request: ChatRequest, source: String, endpoint: String = "/v1/chat/completions"): GatewayResponse {
        val started = clock()
        val snap = store.snapshot()
        val candidates = router.resolve(request.model, snap)
        if (candidates.isEmpty()) {
            val ex = noCandidates(request.model, snap)
            record(source, endpoint, request, null, null, ex.statusCode, false, started, TokenUsage(), emptyList(), ex.message, false)
            throw ex
        }
        val attempts = mutableListOf<Attempt>()
        val budget = snap.config.maxAttempts.coerceIn(1, 20)

        for (candidate in candidates) {
            if (attempts.size >= budget) break
            for (key in keysFor(candidate.provider)) {
                if (attempts.size >= budget) break
                val apiKey = resolveKey(key) ?: continue
                val attemptStart = clock()
                try {
                    val endpointCfg = ProviderEndpoint(candidate.provider.type, candidate.provider.baseUrl, apiKey)
                    val result = adapterFor(candidate.provider.type).chat(endpointCfg, request.copy(model = candidate.model, stream = false))
                    val now = clock()
                    attempts += Attempt(candidate.provider.name, candidate.model, key?.label, 200, null, now - attemptStart)
                    key?.let { store.markKeySuccess(it.id, now) }
                    record(source, endpoint, request, candidate, key, 200, true, started, result.usage, attempts, null, false)
                    return GatewayResponse(result, candidate.provider.id, candidate.provider.name, candidate.provider.type, candidate.model, attempts.toList(), now - started)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val pe = e.asProviderException()
                    attempts += Attempt(candidate.provider.name, candidate.model, key?.label, pe.statusCode.takeIf { it > 0 }, Redactor.redact(pe.message), clock() - attemptStart)
                    val nextKey = onFailure(key, pe)
                    if (!snap.config.fallbackEnabled) throw failAndThrow(source, endpoint, request, started, attempts, pe, false)
                    if (!nextKey) break
                }
            }
        }
        throw failAndThrow(source, endpoint, request, started, attempts, null, false)
    }

    fun stream(request: ChatRequest, source: String, endpoint: String = "/v1/chat/completions"): Flow<GatewayStreamEvent> = flow {
        val started = clock()
        val snap = store.snapshot()
        val candidates = router.resolve(request.model, snap)
        if (candidates.isEmpty()) {
            val ex = noCandidates(request.model, snap)
            record(source, endpoint, request, null, null, ex.statusCode, false, started, TokenUsage(), emptyList(), ex.message, true)
            throw ex
        }
        val attempts = mutableListOf<Attempt>()
        val budget = snap.config.maxAttempts.coerceIn(1, 20)

        for (candidate in candidates) {
            if (attempts.size >= budget) break
            for (key in keysFor(candidate.provider)) {
                if (attempts.size >= budget) break
                val apiKey = resolveKey(key) ?: continue
                val attemptStart = clock()
                var emittedText = false
                var finish: StreamEvent.Finish? = null
                val textLength = intArrayOf(0)
                try {
                    val endpointCfg = ProviderEndpoint(candidate.provider.type, candidate.provider.baseUrl, apiKey)
                    adapterFor(candidate.provider.type).stream(endpointCfg, request.copy(model = candidate.model, stream = true)).collect { ev ->
                        when (ev) {
                            is StreamEvent.Delta -> {
                                if (!emittedText) {
                                    emittedText = true
                                    emit(GatewayStreamEvent.Started(candidate.provider.name, candidate.provider.type, candidate.model))
                                }
                                textLength[0] += ev.text.length
                                emit(GatewayStreamEvent.Delta(ev.text))
                            }
                            is StreamEvent.Finish -> finish = ev
                        }
                    }
                    if (!emittedText) emit(GatewayStreamEvent.Started(candidate.provider.name, candidate.provider.type, candidate.model))
                    val now = clock()
                    attempts += Attempt(candidate.provider.name, candidate.model, key?.label, 200, null, now - attemptStart)
                    key?.let { store.markKeySuccess(it.id, now) }
                    val usage = finish?.usage ?: TokenUsage(completionTokens = textLength[0] / 4)
                    record(source, endpoint, request, candidate, key, 200, true, started, usage, attempts, null, true)
                    emit(
                        GatewayStreamEvent.Completed(
                            finishReason = finish?.finishReason ?: "stop",
                            usage = usage,
                            providerName = candidate.provider.name,
                            model = candidate.model,
                            attempts = attempts.toList(),
                            latencyMs = now - started,
                        ),
                    )
                    return@flow
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val pe = e.asProviderException()
                    attempts += Attempt(candidate.provider.name, candidate.model, key?.label, pe.statusCode.takeIf { it > 0 }, Redactor.redact(pe.message), clock() - attemptStart)
                    val nextKey = onFailure(key, pe)
                    // Once tokens reached the client we cannot switch providers mid-answer.
                    if (emittedText || !snap.config.fallbackEnabled) throw failAndThrow(source, endpoint, request, started, attempts, pe, true)
                    if (!nextKey) break
                }
            }
        }
        throw failAndThrow(source, endpoint, request, started, attempts, null, true)
    }

    // --- internals ---

    /** Keys to try for a provider: healthy enabled keys, rotated so load spreads across them. `null` = keyless provider. */
    private fun keysFor(provider: ProviderSnapshot): List<KeySnapshot?> {
        val usable = provider.usableKeys
        if (usable.isEmpty()) return if (provider.type.requiresKey) emptyList() else listOf(null)
        val now = clock()
        val healthy = usable.filterNot { isCoolingDown(it.id) || it.cooldownUntil > now }
        if (healthy.isEmpty()) return emptyList()
        val cursor = keyCursor.getOrPut(provider.id) { AtomicInteger(0) }
        val start = Math.floorMod(cursor.getAndIncrement(), healthy.size)
        return healthy.drop(start) + healthy.take(start)
    }

    private suspend fun resolveKey(key: KeySnapshot?): String? = if (key == null) "" else store.decryptKey(key.id)

    /** Applies cooldowns. Returns true when trying the next key of the same provider makes sense. */
    private suspend fun onFailure(key: KeySnapshot?, e: ProviderException): Boolean {
        val now = clock()
        val (cooldownMs, tryNextKey) = when (e.kind) {
            FailureKind.RATE_LIMIT -> (e.retryAfterMs ?: 60_000L).coerceAtLeast(5_000L) to true
            FailureKind.AUTH -> 10 * 60_000L to true
            FailureKind.SERVER -> 15_000L to false
            FailureKind.NETWORK -> 10_000L to false
            FailureKind.REQUEST -> 0L to false
        }
        if (key != null) {
            if (cooldownMs > 0) cooldowns[key.id] = now + cooldownMs
            store.markKeyFailure(key.id, e.kind, e.message ?: "error", if (cooldownMs > 0) now + cooldownMs else 0L)
        }
        return tryNextKey
    }

    private suspend fun failAndThrow(
        source: String,
        endpoint: String,
        request: ChatRequest,
        started: Long,
        attempts: List<Attempt>,
        last: ProviderException?,
        stream: Boolean,
    ): GatewayException {
        val status = when {
            attempts.isEmpty() -> 503
            last != null && last.kind == FailureKind.REQUEST -> last.statusCode
            attempts.all { it.statusCode == 429 } -> 429
            else -> 502
        }
        val message = when {
            attempts.isEmpty() -> "All matching keys are cooling down after rate limits or errors. Try again shortly or add more keys."
            attempts.size == 1 -> attempts.first().error ?: "Upstream error"
            else -> "All ${attempts.size} attempts failed. Last error: ${attempts.last().error}"
        }
        val lastOk = attempts.lastOrNull()
        record(source, endpoint, request, null, null, status, false, started, TokenUsage(), attempts, message, stream, lastOk?.providerName, lastOk?.model)
        return GatewayException(status, message, attempts.toList())
    }

    private fun noCandidates(model: String, snap: GatewaySnapshot): GatewayException {
        val anyUsable = snap.providers.any { it.isUsable }
        return if (!anyUsable) {
            GatewayException(503, "No provider is ready. Add a provider with an API key in the FreeLLMAPI app.")
        } else {
            GatewayException(404, "Model '$model' is not available. Use 'auto', a route alias, an enabled model id, or '<provider>/<model>'.")
        }
    }

    private suspend fun record(
        source: String,
        endpoint: String,
        request: ChatRequest,
        candidate: Candidate?,
        key: KeySnapshot?,
        status: Int,
        success: Boolean,
        started: Long,
        usage: TokenUsage,
        attempts: List<Attempt>,
        error: String?,
        stream: Boolean,
        fallbackProviderName: String? = null,
        fallbackModel: String? = null,
    ) {
        try {
            store.record(
                RequestOutcome(
                    timestamp = started,
                    source = source,
                    endpoint = endpoint,
                    requestedModel = request.model.ifBlank { "auto" },
                    providerId = candidate?.provider?.id,
                    providerName = candidate?.provider?.name ?: fallbackProviderName,
                    providerType = candidate?.provider?.type?.id,
                    model = candidate?.model ?: fallbackModel,
                    keyId = key?.id,
                    statusCode = status,
                    success = success,
                    latencyMs = clock() - started,
                    usage = usage,
                    attempts = attempts.toList(),
                    error = error?.let { Redactor.redact(it) },
                    stream = stream,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Logging must never break a request.
        }
    }

    private fun Exception.asProviderException(): ProviderException = when (this) {
        is ProviderException -> this
        is java.io.IOException -> ProviderException(0, "Network error: " + Redactor.redact(message ?: javaClass.simpleName), cause = this)
        else -> ProviderException(502, Redactor.redact(message ?: javaClass.simpleName), cause = this)
    }
}
