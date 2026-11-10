package com.avatarsdev.tiktokupgrade

import com.avatarsdev.tiktokupgrade.data.db.DataSource
import com.avatarsdev.tiktokupgrade.data.db.LikeEntity
import com.avatarsdev.tiktokupgrade.data.sample.SampleData
import com.avatarsdev.tiktokupgrade.domain.analytics.CsvExporter
import com.avatarsdev.tiktokupgrade.domain.analytics.LikeAnalytics
import com.avatarsdev.tiktokupgrade.domain.analytics.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class LikeAnalyticsTest {
    private val bkk = ZoneId.of("Asia/Bangkok")

    private fun like(iso: String, id: Int) = LikeEntity(
        id = id.toLong(), videoUrl = "https://www.tiktok.com/@u/video/74340000000000000$id", videoId = "74340000000000000$id",
        likedAt = Instant.parse(iso).toEpochMilli(), source = DataSource.REAL, importedAt = 0,
    )

    // 5 Nov 23:30 BKK = 16:30Z ; 6 Nov 00:10 BKK = 5 Nov 17:10Z ; 6 Nov 21:00 BKK ; 7 Nov 08:00 BKK
    private val likes = listOf(
        like("2024-11-05T16:30:00Z", 10),
        like("2024-11-05T17:10:00Z", 11),
        like("2024-11-06T14:00:00Z", 12),
        like("2024-11-07T01:00:00Z", 13),
        like("2024-11-04T10:00:00Z", 14),
    )

    @Test fun filter_5to6Nov_inLocalTime_newestFirst() {
        val r = LikeAnalytics.filter(likes, LocalDate.of(2024, 11, 5), LocalDate.of(2024, 11, 6), bkk)
        assertEquals(listOf(12L, 11L, 10L), r.map { it.id })
        val old = LikeAnalytics.filter(likes, LocalDate.of(2024, 11, 5), LocalDate.of(2024, 11, 6), bkk, order = SortOrder.OLDEST_FIRST)
        assertEquals(listOf(10L, 11L, 12L), old.map { it.id })
    }

    @Test fun search_byIdAndTime() {
        assertEquals(1, LikeAnalytics.filter(likes, null, null, bkk, "0000013").size)
        assertEquals(listOf(12L), LikeAnalytics.filter(likes, null, null, bkk, "21:00").map { it.id })
        assertEquals(2, LikeAnalytics.filter(likes, null, null, bkk, "2024-11-06").size)
    }

    @Test fun dailyAndHourly() {
        val start = LocalDate.of(2024, 11, 4)
        val end = LocalDate.of(2024, 11, 7)
        val d = LikeAnalytics.daily(likes, start, end, bkk)
        assertEquals(listOf(1, 1, 2, 1), d.map { it.count })
        val h = LikeAnalytics.hourly(likes, bkk)
        assertEquals(1, h[23]); assertEquals(1, h[0]); assertEquals(1, h[21]); assertEquals(1, h[8]); assertEquals(1, h[17])
        assertEquals(5, h.sum())
        val s = LikeAnalytics.summary(likes, start, end, bkk)
        assertEquals(5, s.total)
        assertEquals(4, s.days)
        assertEquals(LocalDate.of(2024, 11, 6), s.peakDay!!.date)
    }

    @Test fun csv_escapesAndHasHeader() {
        val weird = like("2024-11-05T16:30:00Z", 10).copy(videoUrl = "https://x/\"a\",b", videoId = "=cmd()")
        val csv = CsvExporter.toCsv(listOf(weird), bkk)
        val lines = csv.removePrefix("﻿").split("\r\n")
        assertEquals(CsvExporter.HEADER, lines[0])
        assertTrue(lines[1].startsWith("2024-11-05 23:30:00,2024-11-05T16:30:00Z,Asia/Bangkok,'=cmd(),"))
        assertTrue(lines[1].contains("\"https://x/\"\"a\"\",b\""))
    }

    @Test fun sampleData_isTaggedAndFake() {
        val now = Instant.parse("2026-11-10T07:00:00Z")
        val rows = SampleData.generate(now, bkk)
        assertTrue(rows.size > 300)
        assertTrue(rows.all { it.source == DataSource.SAMPLE && SampleData.isSampleUrl(it.videoUrl) && it.likedAt <= now.toEpochMilli() })
        assertEquals(rows.size, rows.map { it.videoUrl }.toSet().size)
        assertEquals(rows, SampleData.generate(now, bkk)) // deterministic
    }
}
