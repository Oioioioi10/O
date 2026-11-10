package com.avatarsdev.tiktokupgrade.domain.feed

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class WeightedCategory(
    val id: String,
    val label: String,
    /** Percent of the feed, 0..100. All included categories sum to 100. */
    val weight: Int,
    val keywords: List<String>,
    val custom: Boolean = false,
)

@Serializable
data class ExcludedCategory(
    val id: String,
    val label: String,
    val keywords: List<String>,
    val custom: Boolean = false,
)

@Serializable
data class FeedRules(
    val include: List<WeightedCategory>,
    val exclude: List<ExcludedCategory>,
    /** Plain-language ranking conditions shown to the user. */
    val rankingRules: List<String>,
    val warnings: List<String> = emptyList(),
    val engine: String = ENGINE_LOCAL,
) {
    val isEmpty get() = include.isEmpty() && exclude.isEmpty()

    companion object {
        const val ENGINE_LOCAL = "local"
        const val ENGINE_LLM = "llm"
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun toJson(rules: FeedRules): String = json.encodeToString(rules)
        fun fromJson(s: String): FeedRules? = try { json.decodeFromString<FeedRules>(s) } catch (_: Exception) { null }
    }
}

object Weights {
    /**
     * Turns raw weights (null = unspecified) into integer percentages that sum to exactly 100,
     * using largest-remainder rounding. Unspecified entries share whatever the specified ones leave.
     */
    fun normalize(raw: List<Int?>): Pair<List<Int>, List<String>> {
        if (raw.isEmpty()) return emptyList<Int>() to emptyList()
        val notes = mutableListOf<String>()
        val specifiedSum = raw.filterNotNull().sum()
        val unspecified = raw.count { it == null }
        val values: List<Double> = when {
            unspecified == 0 -> {
                if (specifiedSum != 100 && specifiedSum > 0) notes += "สัดส่วนรวม $specifiedSum% — ปรับเทียบให้รวมเป็น 100%"
                raw.map { it!!.toDouble() }
            }
            specifiedSum >= 100 -> {
                notes += "สัดส่วนที่ระบุรวมเกิน 100% — หมวดที่ไม่ระบุ % จะได้ส่วนแบ่งเล็กน้อย"
                val share = specifiedSum * 0.1 / unspecified
                raw.map { it?.toDouble() ?: share }
            }
            else -> {
                val share = (100 - specifiedSum).toDouble() / unspecified
                raw.map { it?.toDouble() ?: share }
            }
        }
        val total = values.sum()
        if (total <= 0.0) {
            val eq = values.map { 100.0 / values.size }
            return largestRemainder(eq) to notes
        }
        return largestRemainder(values.map { it * 100.0 / total }) to notes
    }

    private fun largestRemainder(parts: List<Double>): List<Int> {
        val floors = parts.map { kotlin.math.floor(it).toInt() }.toMutableList()
        var remaining = 100 - floors.sum()
        val order = parts.indices.sortedByDescending { parts[it] - kotlin.math.floor(parts[it]) }
        var i = 0
        while (remaining > 0 && order.isNotEmpty()) {
            floors[order[i % order.size]]++
            remaining--
            i++
        }
        return floors
    }
}
