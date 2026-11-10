package com.avatarsdev.tiktokupgrade

import com.avatarsdev.tiktokupgrade.domain.feed.FeedRules
import com.avatarsdev.tiktokupgrade.domain.feed.LlmException
import com.avatarsdev.tiktokupgrade.domain.feed.LlmPromptClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LlmPromptClientTest {

    private fun reply(content: String) =
        """{"choices":[{"message":{"role":"assistant","content":${kotlinx.serialization.json.JsonPrimitive(content)}}}]}"""

    @Test fun parsesModelJson_mapsKnownCategories_normalises() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(reply("```json\n{\"include\":[{\"name\":\"Roblox\",\"weight\":5,\"keywords\":[\"roblox obby\"]},{\"name\":\"Camera reviews\",\"weight\":5,\"keywords\":[\"camera review\"]}],\"exclude\":[{\"name\":\"Dance\",\"keywords\":[\"dance\"]}]}\n```")))
        server.start()
        val rules = LlmPromptClient().parse("whatever", server.url("/v1/").toString(), "sk-test", "test-model")
        val req = server.takeRequest()
        assertEquals("/v1/chat/completions", req.path)
        assertEquals("Bearer sk-test", req.getHeader("Authorization"))
        assertTrue(req.body.readUtf8().contains("\"model\":\"test-model\""))
        assertEquals(FeedRules.ENGINE_LLM, rules.engine)
        assertEquals(listOf(50, 50), rules.include.map { it.weight })
        assertEquals("roblox", rules.include.first { !it.custom }.id)
        assertTrue(rules.include.first { !it.custom }.keywords.contains("roblox obby"))
        assertEquals("dance", rules.exclude.single().id)
        server.shutdown()
    }

    @Test fun httpError_throwsLlmException() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("{\"error\":\"bad key\"}"))
        server.start()
        try {
            LlmPromptClient().parse("x", server.url("/v1").toString(), "bad", "m")
            fail("expected exception")
        } catch (e: LlmException) {
            assertTrue(e.message!!.contains("401"))
        }
        server.shutdown()
    }

    @Test fun nonJsonAnswer_throws() {
        try {
            LlmPromptClient().rulesFromModelJson("Sorry, I can't help with that.")
            fail("expected exception")
        } catch (e: LlmException) { }
    }

    @Test fun endpointBuilding() {
        assertEquals("https://api.openai.com/v1/chat/completions", LlmPromptClient.endpoint("https://api.openai.com/v1/"))
        assertEquals("https://x/v1/chat/completions", LlmPromptClient.endpoint("https://x/v1/chat/completions"))
    }
}
