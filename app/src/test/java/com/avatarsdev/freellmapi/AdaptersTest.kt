package com.avatarsdev.freellmapi

import com.avatarsdev.freellmapi.core.model.ChatMessage
import com.avatarsdev.freellmapi.core.model.ChatRequest
import com.avatarsdev.freellmapi.core.model.ProviderException
import com.avatarsdev.freellmapi.core.model.Role
import com.avatarsdev.freellmapi.core.model.StreamEvent
import com.avatarsdev.freellmapi.core.model.TokenUsage
import com.avatarsdev.freellmapi.core.net.GatewayJson
import com.avatarsdev.freellmapi.core.net.SseEvent
import com.avatarsdev.freellmapi.core.provider.AnthropicAdapter
import com.avatarsdev.freellmapi.core.provider.GeminiAdapter
import com.avatarsdev.freellmapi.core.provider.OpenAiCompatAdapter
import com.avatarsdev.freellmapi.core.provider.ProviderEndpoint
import com.avatarsdev.freellmapi.core.provider.ProviderType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AdaptersTest {
    private val req = ChatRequest(
        model = "test-model",
        messages = listOf(
            ChatMessage(Role.SYSTEM, "be brief"),
            ChatMessage(Role.USER, "hi"),
            ChatMessage(Role.USER, "again"),
        ),
        temperature = 0.5,
        maxTokens = 100,
        stop = listOf("END"),
    )

    private fun json(s: String) = GatewayJson.parseToJsonElement(s)

    @Test
    fun openAiBuildsBodyAndParsesCompletion(): Unit = runBlocking {
        val http = FakeHttp(json = {
            json("""{"id":"x","model":"gpt-x","choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"},"finish_reason":"stop"}],"usage":{"prompt_tokens":7,"completion_tokens":2,"total_tokens":9}}""")
        })
        val adapter = OpenAiCompatAdapter(http)
        val res = adapter.chat(ProviderEndpoint(ProviderType.OPENAI, "https://api.openai.com/v1/", "sk-test"), req)
        assertEquals("Hello!", res.text)
        assertEquals(TokenUsage(7, 2), res.usage)
        val call = http.calls.single()
        assertEquals("https://api.openai.com/v1/chat/completions", call.url)
        assertEquals("Bearer sk-test", call.headers["Authorization"])
        val body = call.body!!
        assertEquals(100, body["max_completion_tokens"]!!.jsonPrimitive.content.toInt())
        assertFalse("OpenAI must not get deprecated max_tokens", body.containsKey("max_tokens"))
        assertEquals(3, body["messages"]!!.jsonArray.size)
    }

    @Test
    fun groqUsesMaxTokensAndNoUnsupportedFields(): Unit = runBlocking {
        val http = FakeHttp(sse = {
            listOf(
                SseEvent(null, """{"choices":[{"delta":{"role":"assistant","content":""}}]}"""),
                SseEvent(null, """{"choices":[{"delta":{"content":"Hel"}}]}"""),
                SseEvent(null, """{"choices":[{"delta":{"content":"lo"},"finish_reason":"stop"}],"x_groq":{"usage":{"prompt_tokens":4,"completion_tokens":2}}}"""),
                SseEvent(null, "[DONE]"),
            )
        })
        val events = OpenAiCompatAdapter(http).stream(ProviderEndpoint(ProviderType.GROQ, "https://api.groq.com/openai/v1", "gsk_x"), req).toList()
        assertEquals("Hello", events.filterIsInstance<StreamEvent.Delta>().joinToString("") { it.text })
        val finish = events.last() as StreamEvent.Finish
        assertEquals(TokenUsage(4, 2), finish.usage)
        val body = http.calls.single().body!!
        assertEquals("100", body["max_tokens"]!!.jsonPrimitive.content)
        for (k in listOf("logprobs", "logit_bias", "top_logprobs", "n", "stream_options")) assertFalse(k, body.containsKey(k))
    }

    @Test
    fun openRouterAddsAttributionAndSurfacesStreamErrors(): Unit = runBlocking {
        val http = FakeHttp(sse = {
            listOf(
                SseEvent(null, """{"choices":[{"delta":{"content":"a"}}]}"""),
                SseEvent(null, """{"error":{"code":429,"message":"Rate limit exceeded"}}"""),
            )
        })
        try {
            OpenAiCompatAdapter(http).stream(ProviderEndpoint(ProviderType.OPENROUTER, "https://openrouter.ai/api/v1", "sk-or-x"), req).toList()
            fail("expected error")
        } catch (e: ProviderException) {
            assertEquals(429, e.statusCode)
        }
        assertEquals("FreeLLMAPI Android", http.calls.single().headers["X-OpenRouter-Title"])
    }

    @Test
    fun openRouterModelListMarksFreeModels(): Unit = runBlocking {
        val http = FakeHttp(json = {
            json("""{"data":[{"id":"meta/llama:free","name":"Llama","context_length":8192,"pricing":{"prompt":"0","completion":"0"}},{"id":"openai/gpt","pricing":{"prompt":"0.1","completion":"0.2"}}]}""")
        })
        val models = OpenAiCompatAdapter(http).listModels(ProviderEndpoint(ProviderType.OPENROUTER, "https://openrouter.ai/api/v1", "k"))
        assertEquals(listOf(true, false), models.map { it.isFree })
        assertEquals(8192, models.first().contextWindow)
    }

    @Test
    fun anthropicBodyFollowsApiVersion20261021(): Unit = runBlocking {
        val http = FakeHttp(json = {
            json("""{"type":"message","model":"claude-haiku-5-5","content":[{"type":"thinking","thinking":"..."},{"type":"text","text":"Hi there"}],"stop_reason":"max_tokens","usage":{"input_tokens":12,"output_tokens":3}}""")
        })
        val res = AnthropicAdapter(http).chat(ProviderEndpoint(ProviderType.ANTHROPIC, "https://api.anthropic.com/v1", "sk-ant-x"), req)
        assertEquals("Hi there", res.text)
        assertEquals("length", res.finishReason)
        assertEquals(TokenUsage(12, 3), res.usage)
        val call = http.calls.single()
        assertEquals("https://api.anthropic.com/v1/messages", call.url)
        assertEquals("2026-10-21", call.headers["anthropic-version"])
        assertEquals("sk-ant-x", call.headers["x-api-key"])
        val body = call.body!!
        assertFalse("temperature was removed in 2026-10-21", body.containsKey("temperature"))
        assertFalse(body.containsKey("top_p"))
        assertEquals("be brief", body["system"]!!.jsonPrimitive.content)
        val messages = body["messages"]!!.jsonArray
        assertEquals("consecutive user turns are merged", 1, messages.size)
        assertEquals("hi\n\nagain", messages[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive("END"), body["stop_sequences"]!!.jsonArray[0])
    }

    @Test
    fun anthropicStreamEvents(): Unit = runBlocking {
        val http = FakeHttp(sse = {
            listOf(
                SseEvent("message_start", """{"type":"message_start","message":{"model":"claude-sonnet-5-5","usage":{"input_tokens":25,"output_tokens":1}}}"""),
                SseEvent("content_block_start", """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}"""),
                SseEvent("ping", """{"type":"ping"}"""),
                SseEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}"""),
                SseEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" world"}}"""),
                SseEvent("message_delta", """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":15}}"""),
                SseEvent("message_stop", """{"type":"message_stop"}"""),
            )
        })
        val events = AnthropicAdapter(http).stream(ProviderEndpoint(ProviderType.ANTHROPIC, "https://api.anthropic.com", "k"), req).toList()
        assertEquals("Hello world", events.filterIsInstance<StreamEvent.Delta>().joinToString("") { it.text })
        val finish = events.last() as StreamEvent.Finish
        assertEquals("stop", finish.finishReason)
        assertEquals(TokenUsage(25, 15), finish.usage)
    }

    @Test
    fun anthropicOverloadedErrorIsRetryable(): Unit = runBlocking {
        val http = FakeHttp(sse = { listOf(SseEvent("error", """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""")) })
        try {
            AnthropicAdapter(http).stream(ProviderEndpoint(ProviderType.ANTHROPIC, "https://api.anthropic.com", "k"), req).toList()
            fail("expected error")
        } catch (e: ProviderException) {
            assertEquals(529, e.statusCode)
            assertEquals(com.avatarsdev.freellmapi.core.model.FailureKind.SERVER, e.kind)
        }
    }

    @Test
    fun geminiBuildsNativeRequest(): Unit = runBlocking {
        val http = FakeHttp(json = {
            json("""{"candidates":[{"content":{"role":"model","parts":[{"text":"thinking...","thought":true},{"text":"Answer"}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":9,"candidatesTokenCount":4,"thoughtsTokenCount":6},"modelVersion":"gemini-3.8-flash"}""")
        })
        val res = GeminiAdapter(http).chat(ProviderEndpoint(ProviderType.GEMINI, "https://generativelanguage.googleapis.com/v1beta", "AIzaX"), req.copy(model = "models/gemini-3.8-flash"))
        assertEquals("Answer", res.text)
        assertEquals(TokenUsage(9, 10), res.usage)
        val call = http.calls.single()
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent", call.url)
        assertEquals("AIzaX", call.headers["x-goog-api-key"])
        val body = call.body!!
        assertEquals("be brief", body["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("user", body["contents"]!!.jsonArray[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("100", body["generationConfig"]!!.jsonObject["maxOutputTokens"]!!.jsonPrimitive.content)
    }

    @Test
    fun geminiStreamsAndListsModels(): Unit = runBlocking {
        val http = FakeHttp(
            json = {
                json("""{"models":[{"name":"models/gemini-3.8-flash","displayName":"Gemini 3.8 Flash","inputTokenLimit":1048576,"supportedGenerationMethods":["generateContent","countTokens"]},{"name":"models/embedding-001","supportedGenerationMethods":["embedContent"]}]}""")
            },
            sse = {
                listOf(
                    SseEvent(null, """{"candidates":[{"content":{"parts":[{"text":"Hel"}]}}]}"""),
                    SseEvent(null, """{"candidates":[{"content":{"parts":[{"text":"lo"}]},"finishReason":"MAX_TOKENS"}],"usageMetadata":{"promptTokenCount":3,"candidatesTokenCount":2}}"""),
                )
            },
        )
        val ep = ProviderEndpoint(ProviderType.GEMINI, "https://generativelanguage.googleapis.com", "k")
        val events = GeminiAdapter(http).stream(ep, req).toList()
        assertEquals("Hello", events.filterIsInstance<StreamEvent.Delta>().joinToString("") { it.text })
        assertEquals("length", (events.last() as StreamEvent.Finish).finishReason)
        assertTrue(http.calls.first().url.endsWith(":streamGenerateContent?alt=sse"))
        val models = GeminiAdapter(http).listModels(ep)
        assertEquals(listOf("gemini-3.8-flash"), models.map { it.id })
    }
}
