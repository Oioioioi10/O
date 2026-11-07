package com.avatarsdev.freellmapi

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.avatarsdev.freellmapi.core.provider.ProviderType
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * End-to-end on a real device/emulator: Room + Android Keystore + OkHttp + the local API server.
 * A MockWebServer on the device plays two upstream providers: one rate-limited, one healthy.
 */
@RunWith(AndroidJUnit4::class)
class GatewayDeviceTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FreeLlmApp
    private val c = app.container
    private lateinit var upstream: MockWebServer
    private val createdProviders = mutableListOf<Long>()
    private val client = OkHttpClient.Builder().readTimeout(20, TimeUnit.SECONDS).build()
    private val port = 18787

    private val limitedKey = "sk-test-limited-0000000000001111"
    private val goodKey = "sk-test-healthy-0000000000002222"

    @Before
    fun setUp() {
        upstream = MockWebServer()
        upstream.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val auth = request.getHeader("Authorization").orEmpty()
                val path = request.path.orEmpty()
                return when {
                    path.endsWith("/models") -> MockResponse().setBody("""{"object":"list","data":[{"id":"mock-model"},{"id":"mock-mini"}]}""")
                    auth.endsWith(limitedKey) -> MockResponse().setResponseCode(429).setHeader("Retry-After", "30")
                        .setBody("""{"error":{"message":"Rate limit for $limitedKey"}}""")
                    request.body.readUtf8().contains("\"stream\":true") -> MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                        "data: {\"choices\":[{\"delta\":{\"content\":\"stre\"}}]}\n\n" +
                            "data: {\"choices\":[{\"delta\":{\"content\":\"amed\"},\"finish_reason\":\"stop\"}]}\n\n" +
                            "data: [DONE]\n\n",
                    )
                    else -> MockResponse().setBody(
                        """{"model":"mock-model","choices":[{"index":0,"message":{"role":"assistant","content":"hello from mock"},"finish_reason":"stop"}],"usage":{"prompt_tokens":5,"completion_tokens":3}}""",
                    )
                }
            }
        }
        upstream.start()
        c.engine.clearCooldowns()
    }

    @After
    fun tearDown() = runBlocking {
        c.server.stopListener()
        val all = c.db.providers().getAll()
        all.filter { it.id in createdProviders }.forEach { c.providers.deleteProvider(it) }
        upstream.shutdown()
    }

    @Test
    fun keysAreEncryptedAndGatewayFallsBackThroughLocalApi(): Unit = runBlocking {
        val base = upstream.url("/v1").toString().trimEnd('/')
        // Provider A (higher priority) is rate limited, provider B answers.
        val a = c.providers.addProvider(ProviderType.CUSTOM, "Limited", base, limitedKey).also { createdProviders += it }
        val b = c.providers.addProvider(ProviderType.CUSTOM, "Healthy", base, goodKey).also { createdProviders += it }
        assertEquals("Found 2 models", c.providers.syncModels(b).message)
        c.providers.addManualModel(a, "mock-model")
        c.providers.setDefaultModel(a, "mock-model")
        c.providers.setDefaultModel(b, "mock-model")

        // Keys at rest are AES-GCM ciphertext, never plaintext.
        val stored = c.db.keys().getAll().filter { it.providerId == a || it.providerId == b }
        assertEquals(2, stored.size)
        stored.forEach {
            assertTrue(it.encryptedKey.startsWith("v1:"))
            assertFalse(it.encryptedKey.contains("sk-test"))
            assertFalse(it.maskedKey.contains("0000000000"))
        }
        assertEquals(goodKey, c.cipher.decrypt(stored.first { it.providerId == b }.encryptedKey))

        assertTrue(c.server.startListener(port).isSuccess)
        val gatewayKey = c.settings.gatewayKey()
        val url = "http://127.0.0.1:$port"

        // Health is open; the API needs the gateway key.
        assertEquals(200, client.newCall(Request.Builder().url("$url/health").build()).execute().use { it.code })
        assertEquals(401, client.newCall(Request.Builder().url("$url/v1/models").build()).execute().use { it.code })
        val models = client.newCall(Request.Builder().url("$url/v1/models").header("Authorization", "Bearer $gatewayKey").build()).execute().use { it.body!!.string() }
        assertTrue(models, models.contains("custom-2/mock-model") || models.contains("/mock-model"))

        val slugA = c.db.providers().get(a)!!.slug
        val body = """{"model":"mock-model","messages":[{"role":"user","content":"hi"}]}""".toRequestBody("application/json".toMediaType())
        client.newCall(Request.Builder().url("$url/v1/chat/completions").header("Authorization", "Bearer $gatewayKey").post(body).build()).execute().use { r ->
            val text = r.body!!.string()
            assertEquals(text, 200, r.code)
            assertTrue(text, text.contains("hello from mock"))
            assertEquals("Healthy", r.header("X-FreeLLMAPI-Provider"))
            assertEquals("2", r.header("X-FreeLLMAPI-Attempts"))
        }

        // The rate-limited key is now cooling down, so the next call goes straight to B.
        val streamBody = """{"model":"$slugA/mock-model","stream":true,"messages":[{"role":"user","content":"hi"}]}""".toRequestBody("application/json".toMediaType())
        client.newCall(Request.Builder().url("$url/v1/chat/completions").header("Authorization", "Bearer $gatewayKey").post(streamBody).build()).execute().use { r ->
            val text = r.body!!.string()
            assertEquals(200, r.code)
            assertTrue(text, text.contains("data: [DONE]"))
        }

        // Logs record the route but never a key.
        val logs = c.db.logs().count()
        assertTrue(logs >= 2)
        val logText = c.db.query("SELECT * FROM request_logs", null).use { cur ->
            buildString { while (cur.moveToNext()) for (i in 0 until cur.columnCount) append(cur.getString(i)).append('|') }
        }
        assertFalse(logText.contains("sk-test"))
        assertFalse(logText.contains(gatewayKey))
        assertTrue(logText.contains("Limited/mock-model ✗ 429"))
    }
}
