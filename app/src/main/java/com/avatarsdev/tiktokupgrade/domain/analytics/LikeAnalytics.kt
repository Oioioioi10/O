package com.avatarsdev.tiktokupgrade.domain.analytics

import com.avatarsdev.tiktokupgrade.data.db.LikeEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DayCount(val date: LocalDate, val count: Int)

data class LikeSummary(
    val total: Int,
    val days: Int,
    val averagePerDay: Double,
    val peakHour: Int?,
    val peakDay: DayCount?,
    val first: Long?,
    val last: Long?,
)

enum class SortOrder { NEWEST_FIRST, OLDEST_FIRST }

object LikeAnalytics {

    /** Inclusive local-date range; null bounds are open. */
    fun filter(
        likes: List<LikeEntity>,
        start: LocalDate?,
        end: LocalDate?,
        zone: ZoneId,
        query: String = "",
        order: SortOrder = SortOrder.NEWEST_FIRST,
    ): List<LikeEntity> {
        val from = start?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: Long.MIN_VALUE
        val to = end?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE
        val q = query.trim().lowercase()
        val out = likes.filter { it.likedAt >= from && it.likedAt < to && (q.isEmpty() || matches(it, q, zone)) }
        return when (order) {
            SortOrder.NEWEST_FIRST -> out.sortedByDescending { it.likedAt }
            SortOrder.OLDEST_FIRST -> out.sortedBy { it.likedAt }
        }
    }

    private fun matches(like: LikeEntity, q: String, zone: ZoneId): Boolean {
        if (like.videoUrl.lowercase().contains(q)) return true
        if (like.videoId?.lowercase()?.contains(q) == true) return true
        // Allow searching by date text such as "2024-11-05" or "21:"
        val local = Instant.ofEpochMilli(like.likedAt).atZone(zone).toLocalDateTime().toString().replace('T', ' ')
        return local.contains(q)
    }

    /** One entry per day in [start, end] (zero-filled); limited to [maxDays] most recent days. */
    fun daily(likes: List<LikeEntity>, start: LocalDate, end: LocalDate, zone: ZoneId, maxDays: Int = 400): List<DayCount> {
        if (end.isBefore(start)) return emptyList()
        val counts = HashMap<LocalDate, Int>()
        for (l in likes) {
            val d = Instant.ofEpochMilli(l.likedAt).atZone(zone).toLocalDate()
            if (!d.isBefore(start) && !d.isAfter(end)) counts[d] = (counts[d] ?: 0) + 1
        }
        val result = ArrayList<DayCount>()
        var d = end
        while (!d.isBefore(start) && result.size < maxDays) {
            result += DayCount(d, counts[d] ?: 0)
            d = d.minusDays(1)
        }
        result.reverse()
        return result
    }

    fun hourly(likes: List<LikeEntity>, zone: ZoneId): IntArray {
        val h = IntArray(24)
        for (l in likes) h[Instant.ofEpochMilli(l.likedAt).atZone(zone).hour]++
        return h
    }

    /** Natural range of the data (first..last like day), used when the user has not picked one. */
    fun dataRange(likes: List<LikeEntity>, zone: ZoneId): Pair<LocalDate, LocalDate>? {
        if (likes.isEmpty()) return null
        val min = likes.minOf { it.likedAt }
        val max = likes.maxOf { it.likedAt }
        return Instant.ofEpochMilli(min).atZone(zone).toLocalDate() to Instant.ofEpochMilli(max).atZone(zone).toLocalDate()
    }

    fun summary(filtered: List<LikeEntity>, start: LocalDate?, end: LocalDate?, zone: ZoneId): LikeSummary {
        if (filtered.isEmpty()) return LikeSummary(0, 0, 0.0, null, null, null, null)
        val range = dataRange(filtered, zone)!!
        val s = start ?: range.first
        val e = end ?: range.second
        val dayList = daily(filtered, s, e, zone)
        val days = dayList.size.coerceAtLeast(1)
        val hours = hourly(filtered, zone)
        val peakHour = hours.indices.maxByOrNull { hours[it] }
        return LikeSummary(
            total = filtered.size,
            days = days,
            averagePerDay = filtered.size.toDouble() / days,
            peakHour = peakHour,
            peakDay = dayList.maxByOrNull { it.count },
            first = filtered.minOf { it.likedAt },
            last = filtered.maxOf { it.likedAt },
        )
    }
}
