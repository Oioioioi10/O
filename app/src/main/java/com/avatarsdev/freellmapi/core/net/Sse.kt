package com.avatarsdev.freellmapi.core.net

/** One server-sent event. [event] is null when the stream uses data-only events (OpenAI style). */
data class SseEvent(val event: String?, val data: String)

/**
 * Incremental SSE parser (https://html.spec.whatwg.org/multipage/server-sent-events.html).
 * Feed it one line at a time (without the trailing newline); it returns an event when a blank line ends one.
 * Comment lines (starting with ':'), such as OpenRouter keep-alives, are ignored.
 */
class SseParser {
    private var eventName: String? = null
    private val data = StringBuilder()
    private var hasData = false

    fun feed(rawLine: String): SseEvent? {
        val line = rawLine.removeSuffix("\r")
        if (line.isEmpty()) return dispatch()
        if (line.startsWith(":")) return null
        val colon = line.indexOf(':')
        val field: String
        var value: String
        if (colon == -1) {
            field = line
            value = ""
        } else {
            field = line.substring(0, colon)
            value = line.substring(colon + 1)
            if (value.startsWith(" ")) value = value.substring(1)
        }
        when (field) {
            "event" -> eventName = value
            "data" -> {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
        }
        return null
    }

    /** Flushes a final event when the stream ends without a trailing blank line. */
    fun finish(): SseEvent? = dispatch()

    private fun dispatch(): SseEvent? {
        val result = if (hasData) SseEvent(eventName, data.toString()) else null
        eventName = null
        data.setLength(0)
        hasData = false
        return result
    }
}
