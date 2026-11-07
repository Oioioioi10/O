package com.avatarsdev.freellmapi

import com.avatarsdev.freellmapi.core.model.ChatMessage
import com.avatarsdev.freellmapi.core.model.ChatRequest
import com.avatarsdev.freellmapi.core.model.FailureKind
import com.avatarsdev.freellmapi.core.model.ProviderException
import com.avatarsdev.freellmapi.core.model.Role
import com.avatarsdev.freellmapi.core.model.StreamEvent
import com.avatarsdev.freellmapi.core.net.HttpEngine
import com.avatarsdev.freellmapi.core.provider.OpenAiCompatAdapter
import com.avatarsdev.freellmapi.core.provider.ProviderEndpoint
import com.avatarsdev.freellmapi.core.provider.ProviderType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Exercises the real OkHttp path (JSON, SSE, error mapping) against MockWebServer. */
class HttpEngineTest {
    private lateinit var server: MockWebServer
    private val adapter = OpenAiCompatAdapter(HttpEngine(OkHttpClient()))
    private val req = ChatRequest("m", listOf(ChatMessage(Role.USER, "hi")))

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun ep() = ProviderEndpoint(ProviderType.OPENAI, server.url("/v1").toString(), "sk-live-abcdefghijklmnop")

    @Test
    fun postsJsonWithBearerKey(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":1}}"""))
        assertEquals("ok", adapter.chat(ep(), req).text)
        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer sk-live-abcdefghijklmnop", recorded.getHeader("Authorization"))
        assertTrue(recorded.body.readUtf8().contains("\"model\":\"m\""))
    }

    @Test
    fun mapsRateLimitWithRetryAfterAndRedactsMessage(): Unit = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "12")
                .setBody("""{"error":{"message":"Rate limit reached for key sk-live-abcdefghijklmnop","type":"rate_limit"}}"""),
        )
        try {
            adapter.chat(ep(), req)
            fail("expected 429")
        } catch (e: ProviderException) {
            assertEquals(FailureKind.RATE_LIMIT, e.kind)
            assertEquals(12_000L, e.retryAfterMs)
            assertFalse(e.message!!.contains("abcdefghijklmnop"))
            assertTrue(e.message!!.contains("Rate limit reached"))
        }
    }

    @Test
    fun streamsServerSentEvents(): Unit = runBlocking {
        val body = buildString {
            append(": keep-alive\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\"lo\"},\"finish_reason\":\"stop\"}]}\n\n")
            append("data: {\"choices\":[],\"usage\":{\"prompt_tokens\":2,\"completion_tokens\":2}}\n\n")
            append("data: [DONE]\n\n")
        }
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body))
        val events = adapter.stream(ep(), req).toList()
        assertEquals("Hello", events.filterIsInstance<StreamEvent.Delta>().joinToString("") { it.text })
        assertEquals(4, (events.last() as StreamEvent.Finish).usage!!.totalTokens)
        assertTrue(server.takeRequest().body.readUtf8().contains("\"include_usage\":true"))
    }

    @Test
    fun networkFailureIsRetryable(): Unit = runBlocking {
        val dead = ProviderEndpoint(ProviderType.OPENAI, "http://127.0.0.1:1/v1", "k")
        try {
            adapter.chat(dead, req)
            fail("expected network error")
        } catch (e: ProviderException) {
            assertEquals(FailureKind.NETWORK, e.kind)
        }
    }
}
