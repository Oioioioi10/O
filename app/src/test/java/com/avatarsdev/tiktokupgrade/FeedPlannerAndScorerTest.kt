package com.avatarsdev.tiktokupgrade

import com.avatarsdev.tiktokupgrade.domain.feed.ClipScorer
import com.avatarsdev.tiktokupgrade.domain.feed.FeedPlanner
import com.avatarsdev.tiktokupgrade.domain.feed.PromptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedPlannerAndScorerTest {
    private val rules = PromptParser.parse("แสดงคลิป Roblox 50%\nAI Tools 30%\nProgramming 20%\nไม่ต้องการคลิปเต้น\nไม่ต้องการคลิปตลก")

    @Test fun plan_matchesPercentages_andInterleaves() {
        val plan = FeedPlanner.plan(rules, 20)
        assertEquals(20, plan.size)
        assertEquals(mapOf("roblox" to 10, "ai_tools" to 6, "programming" to 4), FeedPlanner.counts(plan))
        // No category three times in a row.
        val ids = plan.map { it.categoryId }
        for (i in 2 until ids.size) assertTrue(!(ids[i] == ids[i - 1] && ids[i] == ids[i - 2]))
        // Keywords rotate within a category.
        val robloxQueries = plan.filter { it.categoryId == "roblox" }.map { it.query }
        assertNotEquals(robloxQueries[0], robloxQueries[1])
        assertTrue(plan.first().searchUrl.startsWith("https://www.tiktok.com/search?q="))
        assertTrue(!plan.first().searchUrl.contains(" "))
    }

    @Test fun scorer_blocksExcluded_andMatchesIncluded() {
        val dance = ClipScorer.score("New dance challenge #dance #fyp", rules)
        assertTrue(dance.blocked)
        assertEquals(-100, dance.score)

        val roblox = ClipScorer.score("สอนทำเกมใน Roblox Studio #roblox #robloxstudio", rules)
        assertEquals("roblox", roblox.categoryId)
        assertTrue(roblox.score >= 60)

        val prog = ClipScorer.score("python tips for beginners", rules)
        assertEquals("programming", prog.categoryId)
        assertTrue(roblox.score > prog.score)

        val none = ClipScorer.score("วิวทะเลสวยมาก", rules)
        assertEquals(0, none.score)
    }

    @Test fun thaiExclusion_matches() {
        assertTrue(ClipScorer.score("คลิปตลกมาก 555", rules).blocked)
    }

    @Test fun rank_putsBlockedLast_andInterleaves() {
        val items = listOf(
            "funny meme compilation",
            "roblox obby speedrun",
            "chatgpt tips ai tools",
            "roblox studio scripting",
            "random vlog",
            "learn python",
        )
        val ranked = ClipScorer.rank(items, { it }, rules).map { it.first }
        assertEquals("funny meme compilation", ranked.last())
        assertEquals("random vlog", ranked[ranked.size - 2])
        assertTrue(ranked.first().contains("roblox"))
        assertEquals(6, ranked.size)
    }
}
