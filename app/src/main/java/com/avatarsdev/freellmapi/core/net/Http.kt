package com.avatarsdev.freellmapi.core.net

import com.avatarsdev.freellmapi.core.model.ProviderException
import com.avatarsdev.freellmapi.core.security.Redactor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val GatewayJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
}

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

/**
 * Thin coroutine wrapper around OkHttp used by every provider adapter. Never logs headers or bodies.
 * Open so tests can substitute canned responses.
 */
open class HttpEngine(private val clientProvider: () -> OkHttpClient) {
    constructor(client: OkHttpClient) : this({ client })

    val client: OkHttpClient get() = clientProvider()

    open suspend fun getJson(url: String, headers: Map<String, String>): JsonElement {
        val request = Request.Builder().url(url).headers(headers.toOkHeaders()).get().build()
        return execute(request)
    }

    open suspend fun postJson(url: String, headers: Map<String, String>, body: JsonObject): JsonElement {
        val request = Request.Builder()
            .url(url)
            .headers(headers.toOkHeaders())
            .post(GatewayJson.encodeToString(JsonObject.serializer(), body).toRequestBody(JSON_MEDIA))
            .build()
        return execute(request)
    }

    /** Opens a streaming POST and emits parsed server-sent events. Cancelling the collector cancels the HTTP call. */
    open fun postSse(url: String, headers: Map<String, String>, body: JsonObject): Flow<SseEvent> = callbackFlow {
        val request = Request.Builder()
            .url(url)
            .headers((headers + ("Accept" to "text/event-stream")).toOkHeaders())
            .post(GatewayJson.encodeToString(JsonObject.serializer(), body).toRequestBody(JSON_MEDIA))
            .build()
        val call = client.newCall(request)
        val job = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) throw errorFrom(response)
                    val source = response.body?.source() ?: throw ProviderException(502, "Empty response body")
                    val parser = SseParser()
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        val event = parser.feed(line)
                        if (event != null) send(event)
                    }
                    parser.finish()?.let { send(it) }
                }
                close()
            } catch (e: ProviderException) {
                close(e)
            } catch (e: IOException) {
                close(if (call.isCanceled()) e else networkError(e))
            }
        }
        awaitClose {
            call.cancel()
            job.cancel()
        }
    }

    private suspend fun execute(request: Request): JsonElement {
        val response = try {
            client.newCall(request).await()
        } catch (e: IOException) {
            throw networkError(e)
        }
        response.use {
            if (!it.isSuccessful) throw errorFrom(it)
            val text = try {
                it.body?.string().orEmpty()
            } catch (e: IOException) {
                throw networkError(e)
            }
            return try {
                GatewayJson.parseToJsonElement(text)
            } catch (e: Exception) {
                throw ProviderException(502, "Upstream returned invalid JSON")
            }
        }
    }

    companion object {
        fun networkError(e: IOException): ProviderException =
            ProviderException(0, "Network error: " + Redactor.redact(e.message ?: e.javaClass.simpleName), cause = e)

        /** Builds a [ProviderException] from a non-2xx response, extracting the provider's error message. */
        fun errorFrom(response: Response): ProviderException {
            val raw = try {
                response.body?.string().orEmpty().take(4000)
            } catch (_: IOException) {
                ""
            }
            val message = extractErrorMessage(raw) ?: response.message.ifBlank { "HTTP ${response.code}" }
            val retryAfter = response.header("retry-after")?.let(::parseRetryAfter)
            return ProviderException(response.code, Redactor.redact("HTTP ${response.code}: $message").take(500), retryAfter)
        }

        fun extractErrorMessage(raw: String): String? {
            if (raw.isBlank()) return null
            return try {
                val root = GatewayJson.parseToJsonElement(raw)
                val obj = root.asObj()
                val err = obj?.get("error")
                err?.asObj()?.str("message")
                    ?: err?.asString()
                    ?: obj?.str("message")
                    ?: (root.asArr()?.firstOrNull()?.asObj()?.get("error")?.asObj()?.str("message"))
            } catch (_: Exception) {
                raw.take(200)
            }
        }

        fun parseRetryAfter(value: String): Long? {
            val seconds = value.trim().toDoubleOrNull() ?: return null
            return (seconds * 1000).toLong().coerceIn(0, 15 * 60_000L)
        }
    }
}

private fun Map<String, String>.toOkHeaders(): Headers {
    val b = Headers.Builder()
    for ((k, v) in this) b.add(k, v)
    return b.build()
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }

        override fun onFailure(call: Call, e: IOException) {
            cont.resumeWithException(e)
        }
    })
    cont.invokeOnCancellation {
        try {
            cancel()
        } catch (_: Throwable) {
        }
    }
}
