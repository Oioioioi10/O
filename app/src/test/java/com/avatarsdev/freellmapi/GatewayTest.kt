package com.avatarsdev.freellmapi

import com.avatarsdev.freellmapi.core.gateway.GatewayConfig
import com.avatarsdev.freellmapi.core.gateway.GatewayEngine
import com.avatarsdev.freellmapi.core.gateway.GatewayException
import com.avatarsdev.freellmapi.core.gateway.GatewayStreamEvent
import com.avatarsdev.freellmapi.core.gateway.ModelRouter
import com.avatarsdev.freellmapi.core.gateway.RouteSnapshot
import com.avatarsdev.freellmapi.core.gateway.RouteTarget
import com.avatarsdev.freellmapi.core.gateway.RoutingStrategy
import com.avatarsdev.freellmapi.core.model.ChatMessage
import com.avatarsdev.freellmapi.core.model.ChatRequest
import com.avatarsdev.freellmapi.core.model.FailureKind
import com.avatarsdev.freellmapi.core.model.ProviderException
import com.avatarsdev.freellmapi.core.model.Role
import com.avatarsdev.freellmapi.core.model.StreamEvent
import com.avatarsdev.freellmapi.core.provider.ProviderType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GatewayTest {
    private fun req(model: String) = ChatRequest(model, listOf(ChatMessage(Role.USER, "hi")))
    private val router = ModelRouter()

    @Test
    fun autoUsesDefaultModelsInPriorityOrder(): Unit = runBlocking {
        val store = FakeStore(listOf(provider(2, priority = 1), provider(1, priority = 0), provider(3, enabled = false)))
        val ids = router.resolve("auto", store.snapshot()).map { it.publicId }
        assertEquals(listOf("p1/m1", "p2/m2"), ids)
    }

    @Test
    fun providersWithoutKeysAreSkippedExceptKeyless(): Unit = runBlocking {
        val store = FakeStore(listOf(provider(1, keys = emptyList()), provider(2, type = ProviderType.CUSTOM, keys = emptyList())))
        assertEquals(listOf("p2/m2"), router.resolve("", store.snapshot()).map { it.publicId })
    }

    @Test
    fun exactModelMatchesEveryProviderServingIt(): Unit = runBlocking {
        val store = FakeStore(
            listOf(provider(1, models = listOf("llama-3.3-70b", "x")), provider(2, models = listOf("llama-3.3-70b")), provider(3)),
            config = GatewayConfig(crossModelFallback = false),
        )
        assertEquals(listOf("p1/llama-3.3-70b", "p2/llama-3.3-70b"), router.resolve("llama-3.3-70b", store.snapshot()).map { it.publicId })
    }

    @Test
    fun slugPrefixAllowsModelIdsWithSlashes(): Unit = runBlocking {
        val store = FakeStore(listOf(provider(1, slug = "groq", models = listOf("a"))), config = GatewayConfig(crossModelFallback = false))
        assertEquals(listOf("groq/openai/gpt-oss-120b"), router.resolve("groq/openai/gpt-oss-120b", store.snapshot()).map { it.publicId })
        assertTrue(router.resolve("nope/model", store.snapshot()).isEmpty())
    }

    @Test
    fun aliasesAndCrossModelFallbackAreDeduplicated(): Unit = runBlocking {
        val store = FakeStore(
            listOf(provider(1), provider(2)),
            routes = listOf(RouteSnapshot("fast", listOf(RouteTarget(2, "m2"), RouteTarget(1, "special")))),
        )
        assertEquals(listOf("p2/m2", "p1/special", "p1/m1"), router.resolve("FAST", store.snapshot()).map { it.publicId })
    }

    @Test
    fun roundRobinRotatesAndFastestSortsByLatency(): Unit = runBlocking {
        val providers = listOf(provider(1, models = listOf("m")), provider(2, models = listOf("m")), provider(3, models = listOf("m")))
        val rr = FakeStore(providers, config = GatewayConfig(strategy = RoutingStrategy.ROUND_ROBIN, crossModelFallback = false))
        val r = ModelRouter()
        val first = r.resolve("m", rr.snapshot()).first().provider.id
        val second = r.resolve("m", rr.snapshot()).first().provider.id
        assertTrue(first != second)
        val snap = rr.snapshot().copy(config = GatewayConfig(strategy = RoutingStrategy.FASTEST, crossModelFallback = false), latency = mapOf("3:m" to 100L, "1:m" to 900L))
        assertEquals(listOf(3L, 1L, 2L), r.resolve("m", snap).map { it.provider.id })
    }

    @Test
    fun fallsBackAcrossKeysAndProviders(): Unit = runBlocking {
        val p1 = provider(1, keys = listOf(11, 12))
        val p2 = provider(2)
        val store = FakeStore(listOf(p1, p2), secrets = mapOf(11L to "k-rate", 12L to "k-bad", 20L to "k-good"))
        val adapter = ScriptedAdapter(onChat = { ep, r ->
            when (ep.apiKey) {
                "k-rate" -> throw ProviderException(429, "rate limited", retryAfterMs = 30_000)
                "k-bad" -> throw ProviderException(401, "invalid key")
                else -> ok("from ${ep.baseUrl} ${r.model}")
            }
        })
        val engine = GatewayEngine(store, { adapter })
        val res = engine.complete(req("auto"), "test")
        assertEquals("from https://p2.example m2", res.result.text)
        assertEquals(3, res.attempts.size)
        assertEquals(listOf(429, 401, 200), res.attempts.map { it.statusCode })
        assertTrue(engine.isCoolingDown(11))
        assertTrue(engine.isCoolingDown(12))
        assertEquals(listOf(11L to FailureKind.RATE_LIMIT, 12L to FailureKind.AUTH), store.failures.toList())
        assertEquals(listOf(20L), store.successes.toList())
        val outcome = store.outcomes.single()
        assertTrue(outcome.success)
        assertEquals("Provider2", outcome.providerName)
        assertTrue(outcome.fallbackPath.contains("✗ 429"))

        // Next request skips the cooling keys entirely and goes straight to provider 2.
        adapter.seen.clear()
        engine.complete(req("auto"), "test")
        assertEquals(listOf("https://p2.example" to "k-good"), adapter.seen.toList())
    }

    @Test
    fun badRequestMovesToNextCandidateAndAllFailuresReport502(): Unit = runBlocking {
        val store = FakeStore(listOf(provider(1), provider(2)), secrets = mapOf(10L to "a", 20L to "b"))
        val engine = GatewayEngine(store, { ScriptedAdapter(onChat = { ep, _ -> throw ProviderException(if (ep.apiKey == "a") 400 else 503, "nope") }) })
        try {
            engine.complete(req("auto"), "test")
            fail("expected failure")
        } catch (e: GatewayException) {
            assertEquals(502, e.statusCode)
            assertEquals(2, e.attempts.size)
        }
        assertFalse(store.outcomes.single().success)
    }

    @Test
    fun noProvidersGives503AndUnknownModel404(): Unit = runBlocking {
        val empty = GatewayEngine(FakeStore(emptyList()), { error("unused") })
        try {
            empty.complete(req("auto"), "t")
            fail()
        } catch (e: GatewayException) {
            assertEquals(503, e.statusCode)
        }
        val store = FakeStore(listOf(provider(1)), config = GatewayConfig(crossModelFallback = false), secrets = mapOf(10L to "k"))
        try {
            GatewayEngine(store, { error("unused") }).complete(req("missing-model"), "t")
            fail()
        } catch (e: GatewayException) {
            assertEquals(404, e.statusCode)
        }
    }

    @Test
    fun fallbackDisabledStopsAfterFirstError(): Unit = runBlocking {
        val store = FakeStore(listOf(provider(1), provider(2)), config = GatewayConfig(fallbackEnabled = false), secrets = mapOf(10L to "a", 20L to "b"))
        val adapter = ScriptedAdapter(onChat = { _, _ -> throw ProviderException(500, "boom") })
        try {
            GatewayEngine(store, { adapter }).complete(req("auto"), "t")
            fail()
        } catch (e: GatewayException) {
            assertEquals(1, e.attempts.size)
        }
    }

    @Test
    fun maxAttemptsIsRespected(): Unit = runBlocking {
        val providers = (1L..6L).map { provider(it) }
        val store = FakeStore(providers, config = GatewayConfig(maxAttempts = 3), secrets = providers.associate { it.id * 10 to "k${it.id}" })
        val adapter = ScriptedAdapter(onChat = { _, _ -> throw ProviderException(500, "down") })
        try {
            GatewayEngine(store, { adapter }).complete(req("auto"), "t")
            fail()
        } catch (e: GatewayException) {
            assertEquals(3, e.attempts.size)
        }
    }

    @Test
    fun streamFallsBackBeforeFirstToken(): Unit = runBlocking {
        val store = FakeStore(listOf(provider(1), provider(2)), secrets = mapOf(10L to "a", 20L to "b"))
        val adapter = ScriptedAdapter(
            onChat = { _, _ -> ok("unused") },
            onStream = { ep, _ ->
                if (ep.apiKey == "a") throw ProviderException(429, "slow down")
                listOf(StreamEvent.Delta("Hel"), StreamEvent.Delta("lo"), StreamEvent.Finish("stop", null, null))
            },
        )
        val events = GatewayEngine(store, { adapter }).stream(req("auto"), "t").toList()
        val started = events.first() as GatewayStreamEvent.Started
        assertEquals("Provider2", started.providerName)
        assertEquals("Hello", events.filterIsInstance<GatewayStreamEvent.Delta>().joinToString("") { it.text })
        val done = events.last() as GatewayStreamEvent.Completed
        assertEquals(2, done.attempts.size)
        assertTrue(store.outcomes.single().stream)
    }

    @Test
    fun streamErrorAfterTokensDoesNotSwitchProvider(): Unit = runBlocking {
        val store = FakeStore(listOf(provider(1), provider(2)), secrets = mapOf(10L to "a", 20L to "b"))
        val adapter = object : com.avatarsdev.freellmapi.core.provider.ProviderAdapter {
            override suspend fun listModels(endpoint: com.avatarsdev.freellmapi.core.provider.ProviderEndpoint) = emptyList<com.avatarsdev.freellmapi.core.model.RemoteModel>()
            override suspend fun chat(endpoint: com.avatarsdev.freellmapi.core.provider.ProviderEndpoint, request: ChatRequest) = ok("x")
            override fun stream(endpoint: com.avatarsdev.freellmapi.core.provider.ProviderEndpoint, request: ChatRequest) = kotlinx.coroutines.flow.flow {
                emit(StreamEvent.Delta("partial"))
                throw ProviderException(502, "connection dropped")
            }
        }
        val seen = mutableListOf<GatewayStreamEvent>()
        try {
            GatewayEngine(store, { adapter }).stream(req("auto"), "t").collect { seen += it }
            fail("expected error")
        } catch (e: GatewayException) {
            assertEquals(1, e.attempts.size)
        }
        assertEquals("Provider1", (seen.first() as GatewayStreamEvent.Started).providerName)
    }
}
