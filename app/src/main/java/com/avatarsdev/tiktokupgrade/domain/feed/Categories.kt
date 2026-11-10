package com.avatarsdev.tiktokupgrade.domain.feed

/**
 * Built-in content categories. `aliases` are what a user might type in a prompt (Thai/English);
 * `keywords` are search terms / hashtags used for the feed plan and for scoring clip text.
 */
data class CategoryDef(
    val id: String,
    val label: String,
    val aliases: List<String>,
    val keywords: List<String>,
)

object Categories {
    val all: List<CategoryDef> = listOf(
        CategoryDef("roblox", "Roblox", listOf("roblox", "โรบล็อกซ์", "โรบลอก", "โรบล็อก"),
            listOf("roblox", "roblox studio", "roblox obby", "roblox funny moments", "roblox update", "#roblox")),
        CategoryDef("minecraft", "Minecraft", listOf("minecraft", "มายคราฟ", "มายคราฟท์", "มายคราฟต์"),
            listOf("minecraft", "minecraft build", "minecraft bedrock", "#minecraft")),
        CategoryDef("ai_tools", "AI Tools", listOf("ai tools", "ai tool", "เครื่องมือ ai", "ai", "เอไอ", "ปัญญาประดิษฐ์", "chatgpt", "claude", "llm", "gen ai"),
            listOf("ai tools", "new ai tool", "chatgpt tips", "ai productivity", "claude ai", "#aitools")),
        CategoryDef("programming", "Programming", listOf("programming", "coding", "code", "โปรแกรมมิ่ง", "เขียนโปรแกรม", "เขียนโค้ด", "โค้ด", "developer", "dev", "python", "kotlin", "javascript", "lua", "luau"),
            listOf("programming", "coding tips", "learn to code", "python tips", "web dev", "#programming")),
        CategoryDef("gaming", "Gaming", listOf("gaming", "game", "games", "เกม", "เล่นเกม", "gamer"),
            listOf("gaming", "game clips", "new games", "#gaming")),
        CategoryDef("tech", "Tech", listOf("tech", "technology", "gadget", "เทคโนโลยี", "แกดเจ็ต", "มือถือ", "smartphone"),
            listOf("tech news", "gadget review", "smartphone tips", "#tech")),
        CategoryDef("dance", "เต้น", listOf("dance", "dancing", "เต้น", "แดนซ์", "ท่าเต้น", "challenge เต้น"),
            listOf("dance", "dance challenge", "#dance", "เต้น")),
        CategoryDef("comedy", "ตลก", listOf("comedy", "funny", "meme", "memes", "ตลก", "ขำ", "มุก", "มีม"),
            listOf("funny", "comedy", "meme", "ตลก", "#funny")),
        CategoryDef("music", "เพลง", listOf("music", "song", "songs", "เพลง", "ดนตรี", "cover"),
            listOf("music", "song cover", "#music", "เพลง")),
        CategoryDef("food", "อาหาร", listOf("food", "cooking", "recipe", "อาหาร", "ทำอาหาร", "สูตรอาหาร", "ของกิน", "mukbang"),
            listOf("food", "easy recipe", "street food", "#food", "อาหาร")),
        CategoryDef("travel", "ท่องเที่ยว", listOf("travel", "trip", "ท่องเที่ยว", "เที่ยว", "เดินทาง"),
            listOf("travel", "travel vlog", "#travel", "ที่เที่ยว")),
        CategoryDef("sports", "กีฬา", listOf("sport", "sports", "football", "soccer", "กีฬา", "ฟุตบอล", "บอล"),
            listOf("sports highlights", "football", "#sports")),
        CategoryDef("fitness", "ฟิตเนส", listOf("fitness", "workout", "gym", "ออกกำลังกาย", "ฟิตเนส", "ยิม"),
            listOf("workout", "fitness tips", "#fitness")),
        CategoryDef("beauty", "ความงาม", listOf("beauty", "makeup", "skincare", "ความงาม", "แต่งหน้า", "สกินแคร์"),
            listOf("makeup", "skincare", "#beauty")),
        CategoryDef("education", "ความรู้", listOf("education", "learn", "learning", "knowledge", "ความรู้", "การศึกษา", "สาระ"),
            listOf("learn on tiktok", "fun facts", "#learnontiktok", "ความรู้")),
        CategoryDef("anime", "อนิเมะ", listOf("anime", "manga", "อนิเมะ", "การ์ตูน", "มังงะ"),
            listOf("anime", "anime edit", "#anime")),
        CategoryDef("pets", "สัตว์เลี้ยง", listOf("pet", "pets", "cat", "cats", "dog", "dogs", "สัตว์เลี้ยง", "แมว", "หมา", "สุนัข"),
            listOf("cute cats", "dogs", "#pets")),
        CategoryDef("news", "ข่าว", listOf("news", "ข่าว", "การเมือง", "politics"),
            listOf("news", "#news", "ข่าว")),
        CategoryDef("finance", "การเงิน", listOf("finance", "money", "investing", "crypto", "การเงิน", "ลงทุน", "หุ้น", "คริปโต"),
            listOf("personal finance", "investing", "#finance")),
        CategoryDef("art", "ศิลปะ", listOf("art", "drawing", "design", "ศิลปะ", "วาดรูป", "วาดภาพ", "ดีไซน์", "3d", "blender", "pixel art"),
            listOf("digital art", "drawing", "pixel art", "#art")),
        CategoryDef("asmr", "ASMR", listOf("asmr"), listOf("asmr", "#asmr")),
        CategoryDef("drama", "ดราม่า/ซีรีส์", listOf("drama", "series", "ดราม่า", "ซีรีส์", "ละคร"),
            listOf("drama", "series clips", "#drama")),
    )

    fun byId(id: String): CategoryDef? = all.firstOrNull { it.id == id }

    private fun isAscii(s: String) = s.all { it.code < 128 }

    /** True when [alias] occurs in already-lowercased [text]; ASCII aliases need word boundaries. */
    fun aliasMatches(text: String, alias: String): Boolean {
        val a = alias.lowercase()
        if (!isAscii(a)) return text.contains(a)
        var from = 0
        while (true) {
            val i = text.indexOf(a, from)
            if (i < 0) return false
            val before = if (i == 0) ' ' else text[i - 1]
            val afterIdx = i + a.length
            val after = if (afterIdx >= text.length) ' ' else text[afterIdx]
            if (!before.isLetterOrDigit() || !isAscii(before.toString())) {
                if (!(after.isLetterOrDigit() && isAscii(after.toString()))) return true
            }
            from = i + 1
        }
    }

    /**
     * Categories mentioned in [text], longest alias match first so "ai tools" wins over "ai",
     * and each text span is claimed once.
     */
    fun detect(text: String): List<CategoryDef> {
        val lower = text.lowercase()
        val hits = mutableListOf<Pair<CategoryDef, Int>>()
        for (c in all) {
            val best = c.aliases.filter { aliasMatches(lower, it) }.maxOfOrNull { it.length } ?: continue
            hits += c to best
        }
        // "เกม" (gaming) is contained in more specific game names; drop the generic one if a specific game matched.
        val ids = hits.map { it.first.id }.toSet()
        return hits
            .filterNot { it.first.id == "gaming" && ("roblox" in ids || "minecraft" in ids) && !lower.contains("gaming") && !lower.contains("เกมอื่น") }
            .sortedByDescending { it.second }
            .map { it.first }
    }
}
