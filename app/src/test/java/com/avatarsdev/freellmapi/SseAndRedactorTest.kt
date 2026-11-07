package com.avatarsdev.freellmapi

import com.avatarsdev.freellmapi.core.net.SseEvent
import com.avatarsdev.freellmapi.core.net.SseParser
import com.avatarsdev.freellmapi.core.security.Redactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SseAndRedactorTest {

    private fun parse(text: String): List<SseEvent> {
        val p = SseParser()
        val out = text.split("\n").mapNotNull { p.feed(it) }.toMutableList()
        p.finish()?.let { out += it }
        return out
    }

    @Test
    fun parsesOpenAiDataOnlyStream() {
        val events = parse("data: {\"a\":1}\n\ndata: [DONE]\n\n")
        assertEquals(listOf(SseEvent(null, "{\"a\":1}"), SseEvent(null, "[DONE]")), events)
    }

    @Test
    fun parsesNamedEventsAndSkipsComments() {
        val events = parse(": OPENROUTER PROCESSING\n\nevent: message_start\ndata: {\"x\":1}\n\nevent: ping\ndata: {}\n")
        assertEquals(2, events.size)
        assertEquals("message_start", events[0].event)
        assertEquals("ping", events[1].event)
    }

    @Test
    fun joinsMultiLineDataAndHandlesCrlf() {
        val events = parse("data: line1\r\ndata: line2\r\n\r\n")
        assertEquals("line1\nline2", events.single().data)
    }

    @Test
    fun blankLineWithoutDataProducesNothing() {
        val p = SseParser()
        assertNull(p.feed(""))
        assertNull(p.feed("event: x"))
        assertNull(p.feed(""))
    }

    @Test
    fun redactsCommonKeyFormats() {
        val samples = listOf(
            "Incorrect API key provided: sk-proj-abcdefghijklmnopqrstuvwx",
            "bad key sk-ant-api03-ABCDEFGHIJKLMNOP",
            "gsk_ABCDEFGHIJKLMNOPQRSTUV invalid",
            "key=AIzaSyA1234567890abcdefghijklmnop",
            "Authorization: Bearer abcdefghijklmnop123",
            "fllm_0123456789abcdefXYZ leaked",
        )
        for (s in samples) {
            val r = Redactor.redact(s)
            assertTrue("not redacted: $r", r.contains("***"))
            assertFalse(r.contains("abcdefghijklmnop") || r.contains("ABCDEFGHIJKLMNOP") || r.contains("0123456789abcdef"))
        }
        assertEquals("plain message", Redactor.redact("plain message"))
    }

    @Test
    fun masksKeepingPrefixAndLastFour() {
        assertEquals("sk-ant-••••••wxyz", Redactor.mask("sk-ant-api03-0000000000wxyz"))
        assertEquals("gsk_••••••1234", Redactor.mask("gsk_abcdefghijk1234"))
        assertFalse(Redactor.mask("sk-secretsecretsecret9999").contains("secret"))
    }
}
