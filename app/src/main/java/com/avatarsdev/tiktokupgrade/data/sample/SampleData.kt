package com.avatarsdev.tiktokupgrade.data.sample

import com.avatarsdev.tiktokupgrade.data.db.DataSource
import com.avatarsdev.tiktokupgrade.data.db.LikeEntity
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * Demo likes so every screen can be tried before importing a real export.
 * Rows are tagged [DataSource.SAMPLE], use an obviously fake host (example.com) and are never
 * shown together with real data.
 */
object SampleData {
    const val SAMPLE_HOST = "https://example.com/sample-video/"

    fun isSampleUrl(url: String) = url.startsWith(SAMPLE_HOST)

    fun generate(now: Instant, zone: ZoneId, days: Int = 30, seed: Int = 42): List<LikeEntity> {
        val rnd = Random(seed)
        // Evening-heavy hourly profile, like a typical phone user.
        val hourWeights = intArrayOf(2, 1, 1, 0, 0, 0, 1, 2, 3, 3, 3, 4, 6, 5, 4, 4, 5, 7, 8, 10, 12, 13, 11, 6)
        val totalWeight = hourWeights.sum()
        val today = now.atZone(zone).toLocalDate()
        val rows = mutableListOf<LikeEntity>()
        var counter = 1000
        for (d in 0 until days) {
            val date = today.minusDays(d.toLong())
            val weekend = date.dayOfWeek.value >= 6
            val perDay = rnd.nextInt(8, 26) + if (weekend) 10 else 0
            repeat(perDay) {
                var pick = rnd.nextInt(totalWeight)
                var hour = 0
                while (pick >= hourWeights[hour]) { pick -= hourWeights[hour]; hour++ }
                val at = date.atTime(hour, rnd.nextInt(60), rnd.nextInt(60)).atZone(zone).toInstant()
                if (!at.isAfter(now)) {
                    counter++
                    rows += LikeEntity(
                        videoUrl = SAMPLE_HOST + counter,
                        videoId = "SAMPLE-$counter",
                        likedAt = at.truncatedTo(ChronoUnit.SECONDS).toEpochMilli(),
                        source = DataSource.SAMPLE,
                        importedAt = now.toEpochMilli(),
                    )
                }
            }
        }
        return rows
    }
}
