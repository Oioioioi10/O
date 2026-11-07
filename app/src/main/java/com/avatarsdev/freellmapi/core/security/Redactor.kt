package com.avatarsdev.freellmapi.core.security

/**
 * Removes anything that looks like a credential before text is logged, stored or shown.
 * Used on every upstream error message and on every log line written by the gateway.
 */
object Redactor {
    private val patterns = listOf(
        Regex("""sk-ant-[A-Za-z0-9_\-]{8,}"""),
        Regex("""sk-or-[A-Za-z0-9_\-]{8,}"""),
        Regex("""sk-[A-Za-z0-9_\-]{12,}"""),
        Regex("""gsk_[A-Za-z0-9]{12,}"""),
        Regex("""AIza[0-9A-Za-z_\-]{20,}"""),
        Regex("""fllm_[A-Za-z0-9_\-]{12,}"""),
        Regex("""(?i)(bearer\s+)[A-Za-z0-9._\-]{8,}"""),
        Regex("""(?i)((?:api[_-]?key|x-api-key|x-goog-api-key|key|token)\s*[=:]\s*["']?)[A-Za-z0-9._\-]{8,}"""),
    )

    fun redact(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        var out: String = text
        for (p in patterns) {
            out = p.replace(out) { m ->
                val prefix = if (m.groups.size > 1) m.groups[1]?.value else null
                if (prefix != null) "$prefix***" else "***"
            }
        }
        return out
    }

    /** Masks a key for display: keeps a short prefix and the last 4 characters. */
    fun mask(secret: String): String {
        val s = secret.trim()
        if (s.length <= 8) return "•".repeat(s.length.coerceAtLeast(4))
        val prefixLen = when {
            s.startsWith("sk-ant-") -> 7
            s.startsWith("sk-or-") -> 6
            s.startsWith("sk-") -> 3
            s.startsWith("gsk_") -> 4
            s.startsWith("AIza") -> 4
            s.startsWith("fllm_") -> 5
            else -> 2
        }
        return s.take(prefixLen) + "••••••" + s.takeLast(4)
    }
}
