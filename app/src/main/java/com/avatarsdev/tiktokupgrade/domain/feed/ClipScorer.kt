package com.avatarsdev.tiktokupgrade.domain.feed

data class ClipScore(
    /** −100 (blocked) … 100. */
    val score: Int,
    val categoryId: String?,
    val categoryLabel: String?,
    val blockedBy: String?,
    val matchedKeywords: List<String>,
    val reason: String,
) {
    val blocked get() = blockedBy != null
}

/** Scores free text (caption, hashtags, link) of a clip against [FeedRules]. */
object ClipScorer {

    private fun termsFor(id: String, keywords: List<String>, custom: Boolean): List<String> {
        val base = keywords.map { it.removePrefix("#").lowercase() }.filter { it.isNotBlank() }
        if (custom) return base.distinct()
        val aliases = Categories.byId(id)?.aliases?.map { it.lowercase() } ?: emptyList()
        return (aliases + base).distinct()
    }

    private fun hits(lower: String, terms: List<String>): List<String> =
        terms.filter { Categories.aliasMatches(lower, it) }

    fun score(text: String, rules: FeedRules): ClipScore {
        val lower = text.lowercase().replace('_', ' ')
        for (ex in rules.exclude) {
            val h = hits(lower, termsFor(ex.id, ex.keywords, ex.custom))
            if (h.isNotEmpty()) {
                return ClipScore(-100, null, null, ex.label, h, "ถูกกรอง: ตรงกับ \"${ex.label}\" (${h.take(3).joinToString()})")
            }
        }
        var best: ClipScore? = null
        for (inc in rules.include) {
            val h = hits(lower, termsFor(inc.id, inc.keywords, inc.custom))
            if (h.isEmpty()) continue
            val bonus = (h.size - 1).coerceIn(0, 3) * 5
            val s = (40 + inc.weight * 0.45 + bonus).toInt().coerceAtMost(100)
            if (best == null || s > best.score) {
                best = ClipScore(s, inc.id, inc.label, null, h, "ตรงหมวด ${inc.label} (${inc.weight}%) · ${h.take(3).joinToString()}")
            }
        }
        return best ?: if (rules.include.isEmpty()) {
            ClipScore(10, null, null, null, emptyList(), "ไม่ถูกกรอง")
        } else {
            ClipScore(0, null, null, null, emptyList(), "ไม่ตรงหมวดที่ต้องการ — ลองใส่แคปชันหรือแฮชแท็กเพิ่ม")
        }
    }

    /**
     * Ranks clips: blocked ones are dropped to the bottom, matching clips are interleaved by
     * category following the feed weights, then non-matching clips.
     */
    fun <T> rank(items: List<T>, text: (T) -> String, rules: FeedRules): List<Pair<T, ClipScore>> {
        val scored = items.map { it to score(text(it), rules) }
        val matched = scored.filter { it.second.categoryId != null }
            .groupBy { it.second.categoryId!! }
            .mapValues { (_, v) -> ArrayDeque(v.sortedByDescending { it.second.score }) }
        val order = FeedPlanner.interleave(rules.include.filter { matched.containsKey(it.id) }, matched.values.sumOf { it.size })
        val out = ArrayList<Pair<T, ClipScore>>(items.size)
        for (catId in order) {
            val q = matched[catId] ?: continue
            q.removeFirstOrNull()?.let { out += it }
        }
        // Anything left (a category ran dry earlier than its weight predicted).
        matched.values.forEach { q -> while (q.isNotEmpty()) out += q.removeFirst() }
        out += scored.filter { it.second.categoryId == null && !it.second.blocked }
        out += scored.filter { it.second.blocked }
        return out
    }
}
