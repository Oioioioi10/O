package com.avatarsdev.tiktokupgrade.domain.feed

import java.net.URLEncoder

data class PlanSlot(
    val index: Int,
    val categoryId: String,
    val label: String,
    val query: String,
) {
    val searchUrl: String get() = FeedPlanner.searchUrl(query)
}

/**
 * Turns weights into a concrete viewing session: N search slots interleaved so that the
 * category mix matches the requested percentages (e.g. 50/30/20 over 20 slots → 10/6/4).
 * Each slot opens a TikTok search for one of the category's keywords.
 */
object FeedPlanner {

    fun searchUrl(query: String): String =
        "https://www.tiktok.com/search?q=" + URLEncoder.encode(query, "UTF-8").replace("+", "%20")

    /** Smooth weighted round-robin sequence of category ids. */
    fun interleave(categories: List<WeightedCategory>, count: Int): List<String> {
        val active = categories.filter { it.weight > 0 }
        if (active.isEmpty() || count <= 0) return emptyList()
        val total = active.sumOf { it.weight }
        val current = IntArray(active.size)
        val out = ArrayList<String>(count)
        repeat(count) {
            var best = 0
            for (i in active.indices) {
                current[i] += active[i].weight
                if (current[i] > current[best]) best = i
            }
            current[best] -= total
            out += active[best].id
        }
        return out
    }

    fun plan(rules: FeedRules, slots: Int = 20): List<PlanSlot> {
        val byId = rules.include.associateBy { it.id }
        val used = HashMap<String, Int>()
        return interleave(rules.include, slots).mapIndexed { i, id ->
            val cat = byId.getValue(id)
            val terms = cat.keywords.map { it.removePrefix("#").trim() }.filter { it.isNotEmpty() }.distinct()
                .ifEmpty { listOf(cat.label) }
            val n = used[id] ?: 0
            used[id] = n + 1
            PlanSlot(i + 1, id, cat.label, terms[n % terms.size])
        }
    }

    fun counts(plan: List<PlanSlot>): Map<String, Int> = plan.groupingBy { it.categoryId }.eachCount()
}
