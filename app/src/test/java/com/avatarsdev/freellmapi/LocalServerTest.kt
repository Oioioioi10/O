package com.avatarsdev.freellmapi

import com.avatarsdev.freellmapi.core.gateway.GatewayEngine
import com.avatarsdev.freellmapi.core.model.ProviderException
import com.avatarsdev.freellmapi.core.model.StreamEvent
import com.avatarsdev.freellmapi.core.model.TokenUsage
import com.avatarsdev.freellmapi.core.net.GatewayJson
import com.avatarsdev.freellmapi.core.server.GatewayApi
import com.avatarsdev.freellmapi.core.server.MiniHttpServer
import com.avatarsdev.freellmapi.core.server.ServerPolicy
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/** Runs the real HTTP server + gateway API over loopback sockets with a scripted provider. */
class LocalServerTest {
    private val key = "fllm_test_key_0123456789"
    private lateinit var server: MiniHttpServer
    private lateinit var store: FakeStore
    private var port = 0

    @Before
    fun setUp() {
        store = FakeStore(
            listOf(provider(1, models = listOf("llama")), provider(2, models = listOf("llama"))),
            secrets = mapOf(10L to "limited", 20L to "good"),
        )
        val adapter = ScriptedAdapter(
            onChat = { ep, r ->
                if (ep.apiKey == "limited") throw ProviderException(429, "Rate limit for sk-secretsecretsecret1234")
                ok("pong:${r.messages.last().content}", r.model)
            },
            onStream = { ep, r ->
                if (ep.apiKey == "limited") throw ProviderException(429, "slow")
                listOf(StreamEvent.Delta("po"), StreamEvent.Delta("ng"), StreamEvent.Finish("stop", TokenUsage(2, 1), r.model))
            },
        )
        val api = GatewayApi(GatewayEngine(store, { adapter }), store, { ServerPolicy(key, allowLan = false) })
        server = MiniHttpServer(api::handle)
        server.start("127.0.0.1", 0)
        port = server.boundPort
    }

    @After
    fun tearDown() = server.stop()

    private data class Resp(val code: Int, val body: String, val headers: Map<String, String>)

    private fun call(method: String, path: String, body: String? = null, auth: String? = key): Resp {
        val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 5000
        c.readTimeout = 10000
        if (auth != null) c.setRequestProperty("Authorization", "Bearer $auth")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code >= 400) c.errorStream else c.inputStream)?.bufferedReader()?.readText().orEmpty()
        val headers = c.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() }.mapValues { it.value.joinToString(",") }
        c.disconnect()
        return Resp(code, text, headers)
    }

    @Test
    fun healthIsPublicButV1NeedsKey() {
        assertEquals(200, call("GET", "/health", auth = null).code)
        val noKey = call("GET", "/v1/models", auth = null)
        assertEquals(401, noKey.code)
        assertTrue(noKey.body.contains("authentication_error"))
        assertEquals(401, call("GET", "/v1/models", auth = "wrong").code)
        val ok = call("GET", "/v1/models")
        assertEquals(200, ok.code)
        val ids = GatewayJson.parseToJsonElement(ok.body).jsonObject["data"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
        assertEquals(listOf("auto", "p1/llama", "p2/llama"), ids)
        assertFalse("no CORS", ok.headers.containsKey("access-control-allow-origin"))
    }

    @Test
    fun chatCompletionFallsBackAndReportsRoute() {
        val r = call("POST", "/v1/chat/completions", """{"model":"llama","messages":[{"role":"user","content":[{"type":"text","text":"ping"}]}]}""")
        assertEquals(r.body, 200, r.code)
        val json = GatewayJson.parseToJsonElement(r.body).jsonObject
        assertEquals("chat.completion", json["object"]!!.jsonPrimitive.content)
        assertEquals("pong:ping", json["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("Provider2", r.headers["x-freellmapi-provider"])
        assertEquals("2", r.headers["x-freellmapi-attempts"])
        assertTrue(store.outcomes.single().fallbackPath.contains("Provider1/llama ✗ 429"))
        assertFalse("keys never logged", store.outcomes.toString().contains("secretsecret"))
    }

    @Test
    fun streamingUsesOpenAiChunks() {
        val r = call("POST", "/v1/chat/completions", """{"model":"auto","stream":true,"stream_options":{"include_usage":true},"messages":[{"role":"user","content":"x"}]}""")
        assertEquals(200, r.code)
        assertTrue(r.headers["content-type"]!!.startsWith("text/event-stream"))
        val data = r.body.lines().filter { it.startsWith("data: ") }.map { it.removePrefix("data: ") }
        assertEquals("[DONE]", data.last())
        val chunks = data.dropLast(1).map { GatewayJson.parseToJsonElement(it).jsonObject }
        val text = chunks.mapNotNull { c ->
            c["choices"]!!.jsonArray.firstOrNull()?.jsonObject?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.content
        }.joinToString("")
        assertEquals("pong", text)
        assertTrue(chunks.any { c -> c["choices"]!!.jsonArray.firstOrNull()?.jsonObject?.get("finish_reason")?.jsonPrimitive?.content == "stop" })
        assertEquals(3, chunks.last()["usage"]!!.jsonObject["total_tokens"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun badRequestsAreRejectedCleanly() {
        assertEquals(400, call("POST", "/v1/chat/completions", "not json").code)
        assertEquals(400, call("POST", "/v1/chat/completions", """{"messages":[{"role":"system","content":"only system"}]}""").code)
        assertEquals(404, call("GET", "/v1/unknown").code)
        assertEquals(405, call("GET", "/v1/chat/completions").code)
        assertEquals("unknown models are not silently rerouted", 404, call("POST", "/v1/chat/completions", """{"model":"nonexistent","messages":[{"role":"user","content":"x"}]}""").code)
    }

    @Test
    fun repeatedBadKeysAreLockedOut() {
        repeat(GatewayApi.MAX_FAILURES) { call("GET", "/v1/models", auth = "guess$it") }
        assertEquals(429, call("GET", "/v1/models", auth = "guess-again").code)
        assertEquals("even the right key waits out the lockout", 429, call("GET", "/v1/models").code)
    }

    @Test
    fun onlyLoopbackOrPrivateLanClientsAreAllowed() {
        fun ip(s: String) = InetAddress.getByName(s)
        assertTrue(GatewayApi.isAllowedClient(ip("127.0.0.1"), allowLan = false))
        assertFalse(GatewayApi.isAllowedClient(ip("192.168.1.20"), allowLan = false))
        assertTrue(GatewayApi.isAllowedClient(ip("192.168.1.20"), allowLan = true))
        assertTrue(GatewayApi.isAllowedClient(ip("10.0.0.5"), allowLan = true))
        assertTrue(GatewayApi.isAllowedClient(ip("172.16.4.2"), allowLan = true))
        assertFalse(GatewayApi.isAllowedClient(ip("8.8.8.8"), allowLan = true))
        assertFalse(GatewayApi.isAllowedClient(ip("100.100.1.1"), allowLan = true))
        assertTrue(GatewayApi.isAllowedClient(ip("fd12:3456::1"), allowLan = true))
        assertFalse(GatewayApi.isAllowedClient(ip("2001:4860:4860::8888"), allowLan = true))
    }

    @Test
    fun parserRejectsChunkedAndOversizedBodies() {
        val chunked = "POST /v1/chat/completions HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n".byteInputStream()
        try {
            MiniHttpServer.parseRequest(chunked, InetAddress.getLoopbackAddress(), 1024)
            org.junit.Assert.fail()
        } catch (e: com.avatarsdev.freellmapi.core.server.HttpParseException) {
            assertEquals(411, e.status)
        }
        val big = ByteArrayInputStream("POST / HTTP/1.1\r\nContent-Length: 999999\r\n\r\n".toByteArray())
        try {
            MiniHttpServer.parseRequest(big, InetAddress.getLoopbackAddress(), 1024)
            org.junit.Assert.fail()
        } catch (e: com.avatarsdev.freellmapi.core.server.HttpParseException) {
            assertEquals(413, e.status)
        }
        val req = MiniHttpServer.parseRequest("GET /v1/models?x=1%202 HTTP/1.1\r\nAuthorization: Bearer abc\r\n\r\n".byteInputStream(), InetAddress.getLoopbackAddress(), 1024)
        assertEquals("/v1/models", req.path)
        assertEquals("1 2", req.query["x"])
        assertEquals("Bearer abc", req.header("Authorization"))
    }
}
