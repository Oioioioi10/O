package com.avatarsdev.freellmapi.core.server

import com.avatarsdev.freellmapi.core.gateway.GatewayEngine
import com.avatarsdev.freellmapi.core.gateway.GatewayException
import com.avatarsdev.freellmapi.core.gateway.GatewayStore
import com.avatarsdev.freellmapi.core.gateway.GatewayStreamEvent
import com.avatarsdev.freellmapi.core.security.Redactor
import kotlinx.coroutines.CancellationException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Access policy for the local API. */
data class ServerPolicy(
    val gatewayKey: String,
    val allowLan: Boolean,
)

/**
 * HTTP routes of the local OpenAI-compatible endpoint.
 *
 * Security model:
 *  - Every `/v1/` route requires the gateway key (`Authorization: Bearer …` or `x-api-key`), compared in constant time.
 *  - Clients outside loopback are rejected unless LAN access is on, and even then only private (RFC 1918 / link-local /
 *    ULA) addresses are accepted, so the server is never reachable from the public internet.
 *  - Repeated bad keys from one address are locked out for a minute.
 *  - No CORS headers are sent, so web pages in a browser cannot call the API.
 */
class GatewayApi(
    private val engine: GatewayEngine,
    private val store: GatewayStore,
    private val policy: () -> ServerPolicy,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val failures = ConcurrentHashMap<String, Pair<Int, Long>>()

    suspend fun handle(req: HttpRequest): HttpResponse {
        val p = policy()
        if (!isAllowedClient(req.remote, p.allowLan)) {
            return error(403, "Connections from this address are not allowed")
        }
        val path = req.path.trimEnd('/').ifEmpty { "/" }
        if (req.method == "OPTIONS") return HttpResponse.Fixed(204, "text/plain", ByteArray(0))
        if (path == "/" || path == "/health") {
            return HttpResponse.json(200, """{"status":"ok","service":"FreeLLMAPI"}""")
        }
        if (!path.startsWith("/v1")) return error(404, "Not found")

        val ip = req.remote.hostAddress ?: "?"
        lockedOutUntil(ip)?.let { return error(429, "Too many failed authentication attempts. Try again later.") }
        if (!isAuthorized(req, p.gatewayKey)) {
            registerFailure(ip)
            return error(401, "Invalid or missing API key. Use the gateway key shown in the app (Authorization: Bearer …).")
        }
        failures.remove(ip)

        return when {
            path == "/v1/models" && req.method == "GET" -> listModels()
            path == "/v1/chat/completions" && req.method == "POST" -> chatCompletions(req)
            path == "/v1/models" || path == "/v1/chat/completions" -> error(405, "Method not allowed")
            else -> error(404, "Unknown endpoint ${Redactor.redact(path).take(80)}")
        }
    }

    private suspend fun listModels(): HttpResponse {
        val snap = store.snapshot()
        val now = clock() / 1000
        val ids = mutableListOf("auto" to "freellmapi")
        snap.routes.forEach { ids += it.alias to "freellmapi-route" }
        snap.providers.filter { it.isUsable }.sortedBy { it.priority }.forEach { p ->
            p.models.forEach { m -> ids += "${p.slug}/$m" to p.type.id }
        }
        return HttpResponse.json(200, OpenAiProtocol.modelList(ids.distinctBy { it.first }, now))
    }

    private suspend fun chatCompletions(req: HttpRequest): HttpResponse {
        val parsed = OpenAiProtocol.parseChatRequest(req.bodyText)
        val request = parsed.request
        val id = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").take(24)
        val created = clock() / 1000

        if (!request.stream) {
            return try {
                val res = engine.complete(request, source = "api")
                HttpResponse.json(
                    200,
                    OpenAiProtocol.completion(id, created, "${res.providerType.id}/${res.model}", res.result.text, res.result.finishReason, res.result.usage),
                    routingHeaders(res.providerName, res.model, res.attempts.size),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: GatewayException) {
                error(e.statusCode, e.message ?: "Gateway error", mapOf("X-FreeLLMAPI-Attempts" to e.attempts.size.toString()))
            }
        }

        return HttpResponse.Stream(200) { send ->
            var model = request.model
            var sentRole = false
            try {
                engine.stream(request, source = "api").collect { ev ->
                    when (ev) {
                        is GatewayStreamEvent.Started -> {
                            model = "${ev.providerType.id}/${ev.model}"
                            send(sse(OpenAiProtocol.chunk(id, created, model, content = "", role = true)))
                            sentRole = true
                        }
                        is GatewayStreamEvent.Delta -> send(sse(OpenAiProtocol.chunk(id, created, model, content = ev.text)))
                        is GatewayStreamEvent.Completed -> {
                            send(sse(OpenAiProtocol.chunk(id, created, model, content = null, finishReason = ev.finishReason)))
                            if (parsed.includeUsage) send(sse(OpenAiProtocol.usageChunk(id, created, model, ev.usage)))
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: GatewayException) {
                send(sse(OpenAiProtocol.error(e.message ?: "Gateway error", OpenAiProtocol.errorType(e.statusCode), e.statusCode.toString())))
            } catch (e: Exception) {
                send(sse(OpenAiProtocol.error(Redactor.redact(e.message ?: "Stream failed"), "api_error")))
            }
            if (!sentRole) {
                // keep clients that expect at least one chunk happy
                send(sse(OpenAiProtocol.chunk(id, created, model, content = null, finishReason = "error")))
            }
            send("data: [DONE]\n\n")
        }
    }

    private fun sse(json: String) = "data: $json\n\n"

    private fun routingHeaders(provider: String, model: String, attempts: Int) = mapOf(
        "X-FreeLLMAPI-Provider" to provider.filter { it.code in 32..126 },
        "X-FreeLLMAPI-Model" to model.filter { it.code in 32..126 },
        "X-FreeLLMAPI-Attempts" to attempts.toString(),
    )

    private fun error(status: Int, message: String, headers: Map<String, String> = emptyMap()): HttpResponse =
        HttpResponse.json(status, OpenAiProtocol.error(Redactor.redact(message), OpenAiProtocol.errorType(status), status.toString()), headers)

    private fun isAuthorized(req: HttpRequest, expected: String): Boolean {
        if (expected.isBlank()) return false
        val auth = req.header("authorization")
        val presented = when {
            auth != null && auth.startsWith("Bearer ", ignoreCase = true) -> auth.substring(7).trim()
            req.header("x-api-key") != null -> req.header("x-api-key")!!.trim()
            else -> return false
        }
        return MessageDigest.isEqual(presented.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))
    }

    private fun lockedOutUntil(ip: String): Long? {
        val (count, since) = failures[ip] ?: return null
        val now = clock()
        if (now - since > LOCKOUT_WINDOW_MS) {
            failures.remove(ip)
            return null
        }
        return if (count >= MAX_FAILURES) since + LOCKOUT_WINDOW_MS else null
    }

    private fun registerFailure(ip: String) {
        val now = clock()
        failures.compute(ip) { _, old ->
            if (old == null || now - old.second > LOCKOUT_WINDOW_MS) 1 to now else (old.first + 1) to old.second
        }
    }

    companion object {
        const val MAX_FAILURES = 10
        const val LOCKOUT_WINDOW_MS = 60_000L

        fun isAllowedClient(addr: InetAddress, allowLan: Boolean): Boolean {
            if (addr.isLoopbackAddress) return true
            if (!allowLan) return false
            return isPrivate(addr)
        }

        fun isPrivate(addr: InetAddress): Boolean = when (addr) {
            is Inet4Address -> addr.isSiteLocalAddress || addr.isLinkLocalAddress
            is Inet6Address -> {
                val first = addr.address[0].toInt() and 0xFF
                addr.isLinkLocalAddress || addr.isSiteLocalAddress || (first and 0xFE) == 0xFC ||
                    (addr.isIPv4CompatibleAddress && InetAddress.getByAddress(addr.address.copyOfRange(12, 16)).let { it.isSiteLocalAddress })
            }
            else -> false
        }
    }
}

