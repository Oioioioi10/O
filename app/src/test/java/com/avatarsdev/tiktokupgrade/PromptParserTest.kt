package com.avatarsdev.tiktokupgrade

import com.avatarsdev.tiktokupgrade.domain.feed.Categories
import com.avatarsdev.tiktokupgrade.domain.feed.FeedRules
import com.avatarsdev.tiktokupgrade.domain.feed.PromptParser
import com.avatarsdev.tiktokupgrade.domain.feed.Weights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptParserTest {

    @Test fun specExample_isParsedExactly() {
        val rules = PromptParser.parse("แสดงคลิป Roblox 50%\nAI Tools 30%\nProgramming 20%\nไม่ต้องการคลิปเต้น\nไม่ต้องการคลิปตลก")
        assertEquals(listOf("roblox" to 50, "ai_tools" to 30, "programming" to 20), rules.include.map { it.id to it.weight })
        assertEquals(setOf("dance", "comedy"), rules.exclude.map { it.id }.toSet())
        assertTrue(rules.warnings.isEmpty())
        assertTrue(rules.rankingRules.isNotEmpty())
        assertTrue(rules.include.first().keywords.contains("roblox"))
    }

    @Test fun oneLine_commaAndEnglishNegation() {
        val rules = PromptParser.parse("Roblox 40%, Minecraft 40%, Gaming 20%, no dance")
        assertEquals(listOf(40, 40, 20), rules.include.map { it.weight })
        assertEquals(listOf("roblox", "minecraft", "gaming"), rules.include.map { it.id })
        assertEquals(listOf("dance"), rules.exclude.map { it.id })
    }

    @Test fun percentsWithoutSeparators() {
        val rules = PromptParser.parse("Roblox 50% AI 30% Programming 20%")
        assertEquals(mapOf("roblox" to 50, "ai_tools" to 30, "programming" to 20), rules.include.associate { it.id to it.weight })
    }

    @Test fun unspecifiedWeights_shareRemainder() {
        val rules = PromptParser.parse("Roblox 60%\nแมว\nอาหาร")
        assertEquals(100, rules.include.sumOf { it.weight })
        assertEquals(60, rules.include.first { it.id == "roblox" }.weight)
        assertEquals(20, rules.include.first { it.id == "pets" }.weight)
    }

    @Test fun weightsNotSummingTo100_areNormalised() {
        val rules = PromptParser.parse("Roblox 30%\nMinecraft 30%")
        assertEquals(listOf(50, 50), rules.include.map { it.weight })
        assertTrue(rules.warnings.any { it.contains("100%") })
    }

    @Test fun unknownTopic_becomesCustomCategory() {
        val rules = PromptParser.parse("อยากดูรีวิวกล้อง 70%\nRoblox 30%")
        val custom = rules.include.first { it.custom }
        assertEquals(70, custom.weight)
        assertTrue(custom.keywords.first().contains("รีวิวกล้อง"))
        assertFalse(custom.keywords.first().contains("อยากดู"))
    }

    @Test fun excludeWinsOverInclude() {
        val rules = PromptParser.parse("Roblox 50%\nเต้น 50%\nไม่เอาคลิปเต้น")
        assertFalse(rules.include.any { it.id == "dance" })
        assertEquals(100, rules.include.single().weight)
        assertTrue(rules.exclude.any { it.id == "dance" })
    }

    @Test fun gibberish_givesWarning() {
        val rules = PromptParser.parse("   ")
        assertTrue(rules.isEmpty)
        assertTrue(rules.warnings.isNotEmpty())
    }

    @Test fun asciiAliasesNeedWordBoundaries() {
        assertFalse(Categories.aliasMatches("painting tutorial", "ai"))
        assertTrue(Categories.aliasMatches("new ai tool", "ai"))
        assertTrue(Categories.aliasMatches("#roblox #obby", "roblox"))
        assertTrue(Categories.aliasMatches("คลิปaiใหม่", "ai"))
    }

    @Test fun rulesRoundTripJson() {
        val rules = PromptParser.parse("Roblox 50%, AI Tools 50%, no comedy")
        val back = FeedRules.fromJson(FeedRules.toJson(rules))
        assertEquals(rules, back)
    }

    @Test fun weightNormalizer_largestRemainder() {
        assertEquals(listOf(34, 33, 33), Weights.normalize(listOf(null, null, null)).first)
        assertEquals(100, Weights.normalize(listOf(10, 10, 10, null)).first.sum())
    }
}
