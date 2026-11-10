package com.avatarsdev.tiktokupgrade.domain.feed

/**
 * Offline (on-device) prompt → [FeedRules] parser for Thai and English.
 *
 * Understands lines such as:
 *  - "แสดงคลิป Roblox 50%"  → include Roblox with 50%
 *  - "AI Tools 30%"           → include AI Tools with 30%
 *  - "ไม่ต้องการคลิปเต้น"      → exclude Dance
 *  - "no comedy"              → exclude Comedy
 *  - "อยากดูรีวิวกล้อง"         → include a custom category "รีวิวกล้อง" (unknown topics become custom keywords)
 */
object PromptParser {

    private val percentRegex = Regex("(\\d{1,3}(?:\\.\\d+)?)\\s*(%|％|เปอร์เซ็นต์|เปอร์เซ็น|เปอร์|percent)", RegexOption.IGNORE_CASE)

    private val thaiNegations = listOf(
        "ไม่ต้องการ", "ไม่เอา", "ไม่อยากดู", "ไม่อยากได้", "ไม่อยาก", "ไม่ชอบ", "ไม่ดู", "ไม่ต้อง",
        "ไม่ขอ", "ห้าม", "งด", "ซ่อน", "บล็อก", "กรองออก", "ไม่เห็น", "ไม่รับ",
    )
    private val englishNegation = Regex("(^|[^a-z])(no|not|without|exclude|block|hide|avoid|don't|dont|never|less)([^a-z]|$)")

    private val fillers = listOf(
        "แสดงคลิป", "แสดง", "คลิปวิดีโอ", "คลิป", "วิดีโอ", "อยากดู", "อยากได้", "อยากเห็น", "ต้องการ", "เน้น",
        "เพิ่ม", "ขอ", "เอา", "หมวด", "เกี่ยวกับ", "เรื่อง", "แนว", "ประเภท", "ดู", "ฟีด",
        "show me", "show", "more", "videos", "video", "clips", "clip", "content", "about", "i want", "want",
        "please", "feed", "of",
    )

    fun parse(prompt: String): FeedRules {
        val clauses = splitClauses(prompt)
        val include = LinkedHashMap<String, Pair<CategoryDraft, Int?>>()
        val exclude = LinkedHashMap<String, CategoryDraft>()
        val warnings = mutableListOf<String>()

        for (clause in clauses) {
            val lower = clause.lowercase()
            val negative = isNegative(lower)
            val pct = percentRegex.find(lower)?.groupValues?.get(1)?.toDoubleOrNull()?.let { Math.round(it).toInt() }
            val detected = Categories.detect(clause)

            if (negative) {
                if (detected.isNotEmpty()) {
                    detected.forEach { exclude[it.id] = CategoryDraft.of(it) }
                } else {
                    customDraft(clause, negationAware = true)?.let { exclude[it.id] = it }
                }
                continue
            }

            if (detected.isNotEmpty()) {
                if (pct != null) {
                    val primary = detected.first()
                    val prev = include[primary.id]?.second
                    include[primary.id] = CategoryDraft.of(primary) to ((prev ?: 0) + pct)
                } else {
                    detected.forEach { if (it.id !in include) include[it.id] = CategoryDraft.of(it) to null }
                }
            } else {
                val custom = customDraft(clause, negationAware = false)
                if (custom != null) {
                    val prev = include[custom.id]?.second
                    include[custom.id] = custom to (if (pct == null) prev else (prev ?: 0) + pct)
                } else if (pct != null) {
                    warnings += "\"${clause.trim()}\" มี % แต่ไม่รู้ว่าเป็นหมวดอะไร"
                }
            }
        }

        // Excluding wins over including the same category.
        for (id in exclude.keys) {
            if (include.remove(id) != null) warnings += "${exclude[id]!!.label} ถูกระบุทั้งให้แสดงและไม่ให้แสดง — ใช้ \"ไม่แสดง\""
        }

        val entries = include.values.toList()
        val (weights, notes) = Weights.normalize(entries.map { it.second })
        warnings += notes
        val included = entries.mapIndexed { i, (d, _) ->
            WeightedCategory(d.id, d.label, weights[i], d.keywords, d.custom)
        }.sortedByDescending { it.weight }
        val excluded = exclude.values.map { ExcludedCategory(it.id, it.label, it.keywords, it.custom) }

        if (included.isEmpty() && excluded.isEmpty()) {
            warnings += "ยังไม่เข้าใจคำสั่ง ลองพิมพ์เช่น \"Roblox 50%\" หรือ \"ไม่ต้องการคลิปเต้น\""
        } else if (included.isEmpty()) {
            warnings += "ไม่ได้ระบุหมวดที่อยากดู — จะใช้กรองสิ่งที่ไม่ต้องการอย่างเดียว"
        }

        return FeedRules(
            include = included,
            exclude = excluded,
            rankingRules = buildRankingRules(included, excluded),
            warnings = warnings.distinct(),
            engine = FeedRules.ENGINE_LOCAL,
        )
    }

    fun splitClauses(prompt: String): List<String> {
        // Break after every "NN%" so "Roblox 50% AI 30%" on one line becomes two clauses.
        val withBreaks = percentRegex.replace(prompt) { it.value + "\n" }
        return withBreaks
            .split('\n', ',', ';', '|', '•', '、', '，', '·')
            .flatMap { it.split(" และ ", " and ", " แล้วก็ ", " กับ ") }
            .map { it.trim().trimStart('-', '*', '+', '>', '.', ' ').trim() }
            .filter { it.isNotEmpty() }
    }

    private fun isNegative(lower: String): Boolean =
        thaiNegations.any { lower.contains(it) } || englishNegation.containsMatchIn(lower)

    private fun customDraft(clause: String, negationAware: Boolean): CategoryDraft? {
        var text = percentRegex.replace(clause.lowercase(), " ")
        if (negationAware) {
            for (n in thaiNegations) text = text.replace(n, " ")
            text = englishNegation.replace(text) { m -> m.groupValues[1] + " " + m.groupValues[3] }
        }
        for (f in fillers.sortedByDescending { it.length }) {
            text = if (f.all { it.code < 128 }) {
                text.replace(Regex("(^|[^a-z])" + Regex.escape(f) + "([^a-z]|$)")) { m -> m.groupValues[1] + " " + m.groupValues[2] }
            } else {
                text.replace(f, " ")
            }
        }
        val cleaned = text.replace(Regex("[\"'“”‘’!?()\\[\\]{}:]"), " ").replace(Regex("\\s+"), " ").trim()
        if (cleaned.length < 2) return null
        val label = cleaned.split(' ').joinToString(" ") { w -> w.replaceFirstChar { c -> c.titlecase() } }.take(40)
        return CategoryDraft("custom:$cleaned", label, listOf(cleaned), custom = true)
    }

    fun buildRankingRules(include: List<WeightedCategory>, exclude: List<ExcludedCategory>): List<String> {
        val r = mutableListOf<String>()
        if (exclude.isNotEmpty()) {
            r += "ตัดคลิปที่ตรงกับ " + exclude.joinToString(", ") { it.label } + " ออกก่อนเสมอ (คะแนน −100)"
        }
        if (include.isNotEmpty()) {
            r += "ให้คะแนนตามสัดส่วนหมวด: " + include.joinToString(" · ") { "${it.label} ${it.weight}%" }
            r += "คลิปที่ตรงหลาย keyword ในหมวดเดียวกันได้คะแนนเพิ่ม (สูงสุด +15)"
            r += "เรียงฟีดแบบสลับหมวดตามน้ำหนัก (Smooth Weighted Round-Robin) ไม่ให้หมวดเดียวติดกันยาว"
            r += "คลิปที่ไม่ตรงหมวดใดเลยอยู่ท้ายรายการ"
        } else if (exclude.isNotEmpty()) {
            r += "คลิปอื่นที่ไม่ถูกกรองคงลำดับเดิม"
        }
        return r
    }

    internal data class CategoryDraft(val id: String, val label: String, val keywords: List<String>, val custom: Boolean) {
        companion object {
            fun of(c: CategoryDef) = CategoryDraft(c.id, c.label, c.keywords, custom = false)
        }
    }
}
