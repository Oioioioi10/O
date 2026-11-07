package com.avatarsdev.freellmapi

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.avatarsdev.freellmapi.core.model.ChatMessage
import com.avatarsdev.freellmapi.core.model.ChatRequest
import com.avatarsdev.freellmapi.core.model.Role
import com.avatarsdev.freellmapi.core.provider.ProviderType
import com.avatarsdev.freellmapi.ui.MainActivity
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Drives the real UI. Providers point at an on-device mock server (OpenAI protocol) so the dashboard, logs, usage and
 * playground show real gateway traffic, including a rate-limited provider that triggers fallback. Each screen is
 * captured to the app's external files dir (screens/) for review.
 */
@RunWith(AndroidJUnit4::class)
class AppUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private lateinit var upstream: MockWebServer
    private val app get() = rule.activity.application as FreeLlmApp
    private val demoNames = setOf("Groq (demo)", "OpenAI (demo)", "OpenRouter (demo)", "DeepSeek (demo)", "UI Test Provider")

    @Before
    fun setUp() {
        upstream = MockWebServer()
        upstream.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val vendor = path.split('/').getOrElse(1) { "" }
                if (path.endsWith("/models")) {
                    val ids = when (vendor) {
                        "groq" -> listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "openai/gpt-oss-120b")
                        "openai" -> listOf("gpt-6.1-terra", "gpt-6-luna", "gpt-5.4-mini")
                        "openrouter" -> listOf("openrouter/free", "meta-llama/llama-4-scout:free")
                        else -> listOf("deepseek-flash", "deepseek-v4-pro")
                    }
                    return MockResponse().setBody("""{"data":[${ids.joinToString(",") { "{\"id\":\"$it\"}" }}]}""")
                }
                if (vendor == "groq") {
                    return MockResponse().setResponseCode(429).setHeader("Retry-After", "20").setBody("""{"error":{"message":"Rate limit reached (demo)"}}""")
                }
                val body = request.body.readUtf8()
                return if (body.contains("\"stream\":true")) {
                    MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                        listOf("FreeLLMAPI ", "routed this ", "request after ", "the first provider ", "returned 429.")
                            .joinToString("") { "data: {\"choices\":[{\"delta\":{\"content\":\"$it\"}}]}\n\n" } +
                            "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":18,\"completion_tokens\":14}}\n\ndata: [DONE]\n\n",
                    )
                } else {
                    MockResponse().setBody("""{"model":"mock","choices":[{"message":{"content":"ok"},"finish_reason":"stop"}],"usage":{"prompt_tokens":42,"completion_tokens":128}}""")
                }
            }
        }
        upstream.start()
    }

    @After
    fun cleanUp() = runBlocking {
        val c = app.container
        c.db.providers().getAll().filter { it.name in demoNames }.forEach { c.providers.deleteProvider(it) }
        c.engine.clearCooldowns()
        upstream.shutdown()
    }

    private fun shot(name: String, provider: SemanticsNodeInteractionsProvider? = null) {
        rule.waitForIdle()
        Thread.sleep(400)
        rule.waitForIdle()
        val node = provider?.onAllNodes(isRoot())?.onLast() ?: rule.onRoot()
        val bmp = node.captureToImage().asAndroidBitmap()
        val dir = File(rule.activity.getExternalFilesDir(null), "screens").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tab(route: String, screenTag: String) {
        rule.onNodeWithTag("nav_$route").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(screenTag).assertIsDisplayed()
    }

    private fun seedDemoTraffic() = runBlocking {
        val c = app.container
        fun url(v: String) = upstream.url("/$v/v1").toString().trimEnd('/')
        val groq = c.providers.addProvider(ProviderType.GROQ, "Groq (demo)", url("groq"), "gsk_demo_rate_limited_key_000001")
        val openai = c.providers.addProvider(ProviderType.OPENAI, "OpenAI (demo)", url("openai"), "sk-demo-openai-key-0000000000002")
        val or = c.providers.addProvider(ProviderType.OPENROUTER, "OpenRouter (demo)", url("openrouter"), "sk-or-demo-key-00000000000003")
        val ds = c.providers.addProvider(ProviderType.DEEPSEEK, "DeepSeek (demo)", url("deepseek"), "sk-demo-deepseek-00000000000004")
        listOf(groq, openai, or, ds).forEach { c.providers.syncModels(it) }
        repeat(6) { i ->
            runCatching {
                c.engine.complete(ChatRequest("auto", listOf(ChatMessage(Role.USER, "hello $i"))), source = if (i % 2 == 0) "api" else "playground")
            }
            c.engine.clearCooldowns()
            c.providers.clearCooldowns()
        }
    }

    @Test
    fun everyScreenRendersWithLiveGatewayData() {
        seedDemoTraffic()
        rule.onNodeWithTag("nav_dashboard").performClick()
        rule.onNodeWithTag("screen_dashboard").assertIsDisplayed()
        shot("01_dashboard")
        tab("providers", "screen_providers")
        shot("02_providers")
        rule.onNodeWithText("OpenAI (demo)").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("screen_provider_detail")).fetchSemanticsNodes().isNotEmpty() }
        shot("03_provider_detail")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        tab("models", "screen_models")
        shot("04_models")
        tab("playground", "screen_playground")
        rule.onNodeWithTag("prompt_input").performTextInput("Which provider answered?")
        rule.onNodeWithTag("send_prompt").performClick()
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("returned 429.", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        shot("05_playground")
        tab("more", "screen_more")
        shot("06_more")
        for ((i, pair) in listOf("server" to "screen_server", "routing" to "screen_routing", "usage" to "screen_usage", "logs" to "screen_logs", "settings" to "screen_settings").withIndex()) {
            val (route, tag) = pair
            rule.onNodeWithTag("more_$route").performClick()
            rule.waitForIdle()
            rule.onNodeWithTag(tag).assertIsDisplayed()
            shot("%02d_%s".format(7 + i, route))
            rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            rule.waitForIdle()
            rule.onNodeWithTag("screen_more").assertIsDisplayed()
        }
        tab("dashboard", "screen_dashboard")
        shot("12_dashboard_after")
    }

    @Test
    fun addProviderThroughDialog() {
        tab("providers", "screen_providers")
        rule.onNodeWithTag("add_provider").performClick()
        rule.waitForIdle()
        shot("13_add_provider_sheet", rule)
        rule.onNodeWithTag("pick_custom").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Display name").performTextReplacement("UI Test Provider")
        shot("14_add_provider_dialog", rule)
        rule.onNodeWithTag("save_provider").performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("screen_provider_detail")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("UI Test Provider").assertIsDisplayed()
    }
}
