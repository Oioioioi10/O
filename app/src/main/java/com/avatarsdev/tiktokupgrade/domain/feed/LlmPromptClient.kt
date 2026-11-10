package com.avatarsdev.tiktokupgrade.domain.feed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class LlmException(message: String) : Exception(message)

/**
 * Optional: lets an OpenAI-compatible chat-completions endpoint (OpenAI, OpenRouter, Groq, a local
 * gateway …) turn the prompt into rules. The model only returns JSON; weights are re-normalised and
 * categories re-mapped on-device, so a bad answer can never break the app (callers fall back to
 * [PromptParser]).
 */
class LlmPromptClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        val SYSTEM_PROMPT = """
            You convert a user's description of the short-video feed they want into JSON rules.
            Reply with ONLY a JSON object, no prose, in this exact shape:
            {"include":[{"name":"Roblox","weight":50,"keywords":["roblox","roblox studio"]}],
             "exclude":[{"name":"Dance","keywords":["dance","เต้น"]}]}
            - weight is a percentage; include weights should sum to 100 (omit weight if the user gave none).
            - keywords: 3-6 short search terms or hashtags, in the user's language plus English.
            - Things the user does NOT want go in exclude.
        """.trimIndent()

        fun endpoint(baseUrl: String): String {
            val b = baseUrl.trim().trimEnd('/')
            return if (b.endsWith("/chat/completions")) b else "$b/chat/completions"
        }

        /** Pulls the first {...} block out of a model reply (handles ```json fences and chatter). */
        fun extractJsonObject(content: String): String? {
            val start = content.indexOf('{')
            val end = content.lastIndexOf('}')
            return if (start in 0 until end) content.substring(start, end + 1) else null
        }
    }

    suspend fun parse(prompt: String, baseUrl: String, apiKey: String, model: String): FeedRules = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", model)
            put("temperature", 0)
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", SYSTEM_PROMPT) }
                addJsonObject { put("role", "user"); put("content", prompt) }
            }
        }.toString()
        val req = Request.Builder()
            .url(endpoint(baseUrl))
            .header("Authorization", "Bearer $apiKey")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val text = http.newCall(req).execute().use { resp ->
            val t = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw LlmException("HTTP ${resp.code}: ${t.take(160)}")
            t
        }
        val content = try {
            json.parseToJsonElement(text).jsonObject["choices"]!!.jsonArray[0]
                .jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
        } catch (e: Exception) {
            throw LlmException("รูปแบบคำตอบไม่ใช่ chat completions")
        }
        rulesFromModelJson(content)
    }

    fun rulesFromModelJson(content: String): FeedRules {
        val raw = extractJsonObject(content) ?: throw LlmException("โมเดลไม่ได้ตอบเป็น JSON")
        val obj = try { json.parseToJsonElement(raw).jsonObject } catch (e: Exception) { throw LlmException("JSON จากโมเดลไม่ถูกต้อง") }

        data class Item(val id: String, val label: String, val keywords: List<String>, val custom: Boolean, val weight: Int?)

        fun items(key: String): List<Item> = (obj[key] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val name = (o["name"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val kws = (o["keywords"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
                .filter { it.isNotEmpty() }.take(8)
            val weight = (o["weight"] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt() }
            if (name.isEmpty() && kws.isEmpty()) return@mapNotNull null
            val known = Categories.detect(name).firstOrNull()
            if (known != null) {
                Item(known.id, known.label, (known.keywords + kws).distinct(), false, weight)
            } else {
                val n = name.ifEmpty { kws.first() }
                Item("custom:" + n.lowercase(), n.take(40), (kws.ifEmpty { listOf(n) }).distinct(), true, weight)
            }
        }

        val exclude = items("exclude").distinctBy { it.id }
        val excludeIds = exclude.map { it.id }.toSet()
        val include = items("include").distinctBy { it.id }.filter { it.id !in excludeIds }
        if (include.isEmpty() && exclude.isEmpty()) throw LlmException("โมเดลไม่ได้ให้หมวดใดเลย")
        val (weights, notes) = Weights.normalize(include.map { it.weight?.coerceAtLeast(0) })
        val inc = include.mapIndexed { i, it -> WeightedCategory(it.id, it.label, weights[i], it.keywords, it.custom) }
            .sortedByDescending { it.weight }
        val exc = exclude.map { ExcludedCategory(it.id, it.label, it.keywords, it.custom) }
        return FeedRules(inc, exc, PromptParser.buildRankingRules(inc, exc), notes, FeedRules.ENGINE_LLM)
    }
}
