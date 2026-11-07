package com.avatarsdev.freellmapi.core.provider

enum class ApiStyle { OPENAI, ANTHROPIC, GEMINI }

/**
 * Supported providers. Base URLs and model names were checked against each provider's API documentation
 * (November 2026). Built-in models are only a starting point: "Sync models" pulls the live list.
 */
enum class ProviderType(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val style: ApiStyle,
    val docsUrl: String,
    val keyUrl: String,
    val keyHint: String,
    val brandColor: Long,
    val builtinModels: List<String>,
    val requiresKey: Boolean = true,
) {
    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        style = ApiStyle.OPENAI,
        docsUrl = "https://developers.openai.com/api/docs",
        keyUrl = "https://platform.openai.com/api-keys",
        keyHint = "sk-…",
        brandColor = 0xFF10A37F,
        builtinModels = listOf("gpt-6.1-terra", "gpt-6-luna", "gpt-5.4-mini"),
    ),
    ANTHROPIC(
        id = "anthropic",
        displayName = "Anthropic",
        defaultBaseUrl = "https://api.anthropic.com",
        style = ApiStyle.ANTHROPIC,
        docsUrl = "https://platform.claude.com/docs/en/api",
        keyUrl = "https://platform.claude.com/settings/keys",
        keyHint = "sk-ant-…",
        brandColor = 0xFFD97757,
        builtinModels = listOf("claude-haiku-5-5", "claude-sonnet-5-5", "claude-opus-5-5"),
    ),
    GEMINI(
        id = "gemini",
        displayName = "Google Gemini",
        defaultBaseUrl = "https://generativelanguage.googleapis.com",
        style = ApiStyle.GEMINI,
        docsUrl = "https://ai.google.dev/gemini-api/docs",
        keyUrl = "https://aistudio.google.com/apikey",
        keyHint = "AIza…",
        brandColor = 0xFF4C8DF6,
        builtinModels = listOf("gemini-3.8-flash"),
    ),
    DEEPSEEK(
        id = "deepseek",
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com",
        style = ApiStyle.OPENAI,
        docsUrl = "https://api-docs.deepseek.com/",
        keyUrl = "https://platform.deepseek.com/api_keys",
        keyHint = "sk-…",
        brandColor = 0xFF4D6BFE,
        builtinModels = listOf("deepseek-flash", "deepseek-v4-pro"),
    ),
    GROQ(
        id = "groq",
        displayName = "Groq",
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        style = ApiStyle.OPENAI,
        docsUrl = "https://console.groq.com/docs/openai",
        keyUrl = "https://console.groq.com/keys",
        keyHint = "gsk_…",
        brandColor = 0xFFF55036,
        builtinModels = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "openai/gpt-oss-120b", "openai/gpt-oss-20b"),
    ),
    OPENROUTER(
        id = "openrouter",
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        style = ApiStyle.OPENAI,
        docsUrl = "https://openrouter.ai/docs/api/reference/overview",
        keyUrl = "https://openrouter.ai/settings/keys",
        keyHint = "sk-or-…",
        brandColor = 0xFF7C7FF5,
        builtinModels = listOf("openrouter/free"),
    ),
    QWEN(
        id = "qwen",
        displayName = "Qwen (Alibaba Cloud)",
        defaultBaseUrl = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
        style = ApiStyle.OPENAI,
        docsUrl = "https://www.alibabacloud.com/help/en/model-studio/compatibility-of-openai-with-dashscope",
        keyUrl = "https://modelstudio.console.alibabacloud.com/",
        keyHint = "sk-…",
        brandColor = 0xFF8B5CF6,
        builtinModels = listOf("qwen3.8-max", "qwen3.7-plus", "qwen-plus", "qwen-max"),
    ),
    CUSTOM(
        id = "custom",
        displayName = "Custom (OpenAI-compatible)",
        defaultBaseUrl = "http://127.0.0.1:11434/v1",
        style = ApiStyle.OPENAI,
        docsUrl = "",
        keyUrl = "",
        keyHint = "optional",
        brandColor = 0xFF94A3B8,
        builtinModels = emptyList(),
        requiresKey = false,
    );

    val shortLabel: String
        get() = when (this) {
            OPENAI -> "AI"
            ANTHROPIC -> "A\\"
            GEMINI -> "G"
            DEEPSEEK -> "DS"
            GROQ -> "GQ"
            OPENROUTER -> "OR"
            QWEN -> "QW"
            CUSTOM -> "{ }"
        }

    companion object {
        fun fromId(id: String): ProviderType = entries.firstOrNull { it.id == id } ?: CUSTOM
    }
}
