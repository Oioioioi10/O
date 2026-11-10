package com.avatarsdev.tiktokupgrade

import com.avatarsdev.tiktokupgrade.data.importer.TikTokExportParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TikTokExportParserTest {
    private val utc = ZoneId.of("UTC")

    // Classic layout: Activity › Like List › ItemFavoriteList with Date/Link.
    private val classic = """
        {"Activity": {
          "Like List": {"ItemFavoriteList": [
            {"Date": "2024-11-05 21:14:03", "Link": "https://www.tiktokv.com/share/video/7434000000000000001/"},
            {"Date": "2024-11-06 01:02:03", "Link": "https://www.tiktokv.com/share/video/7434000000000000002/"},
            {"Date": "2024-11-06 01:02:03", "Link": "https://www.tiktokv.com/share/video/7434000000000000002/"}
          ]},
          "Favorite Videos": {"FavoriteVideoList": [
            {"Date": "2024-11-01 10:00:00", "Link": "https://www.tiktokv.com/share/video/9999999999999999/"}
          ]},
          "Video Browsing History": {"VideoList": [
            {"Date": "2024-11-01 10:00:00", "Link": "https://www.tiktokv.com/share/video/8888888888888888/"}
          ]}
        }}
    """.trimIndent()

    // Newer layout: wrapper section, lowercase keys.
    private val newer = """
        {"Your Activity": {"Watch History": {"VideoList": [{"date": "2025-01-01 00:00:00", "link": "https://x/video/1111111111"}]}},
         "Likes and Favorites": {
           "Like List": {"ItemFavoriteList": [
             {"date": "2025-03-02 09:30:00", "link": "https://www.tiktok.com/@a/video/7470000000000000009"}
           ]},
           "Favorite Videos": {"FavoriteVideoList": [{"date": "2025-03-02 09:30:00", "link": "https://www.tiktok.com/@b/video/1"}]}
         }}
    """.trimIndent()

    @Test fun classicLayout_readsOnlyLikes_dedupes_andKeepsUtc() {
        val r = TikTokExportParser.parse(classic.toByteArray(), "user_data_tiktok.json", utc)
        assertEquals(2, r.likes.size)
        assertEquals("7434000000000000002", r.likes[0].videoId) // newest first
        assertEquals(Instant.parse("2024-11-05T21:14:03Z").toEpochMilli(), r.likes[1].likedAt)
        assertTrue(r.format.contains("Like List"))
    }

    @Test fun newerLayout_lowercaseKeys_ignoresFavouritesAndWatchHistory() {
        val r = TikTokExportParser.parse(newer.toByteArray(), "user_data.json", utc)
        assertEquals(1, r.likes.size)
        assertEquals("7470000000000000009", r.likes[0].videoId)
        assertEquals(Instant.parse("2025-03-02T09:30:00Z").toEpochMilli(), r.likes[0].likedAt)
    }

    @Test fun deviceZone_shiftsZonelessTimestamps() {
        val bkk = ZoneId.of("Asia/Bangkok")
        val r = TikTokExportParser.parse(classic.toByteArray(), null, bkk)
        assertEquals(Instant.parse("2024-11-05T14:14:03Z").toEpochMilli(), r.likes.last().likedAt)
    }

    @Test fun bomAndBareArray_areAccepted() {
        val text = "﻿[{\"Date\":\"2024-01-01 12:00:00\",\"Link\":\"https://www.tiktok.com/@x/video/1234567890\"}]"
        val r = TikTokExportParser.parse(text.toByteArray(), "likes.json", utc)
        assertEquals(1, r.likes.size)
    }

    @Test fun txtExport_pairsDateAndLink() {
        val txt = """
            Date: 2024-11-05 21:14:03
            Link: https://www.tiktokv.com/share/video/7434000000000000001/

            Date: 2024-11-06 08:00:00
            Link: https://www.tiktokv.com/share/video/7434000000000000003/

            Date: broken
            Link: https://www.tiktokv.com/share/video/7434000000000000004/
        """.trimIndent()
        val r = TikTokExportParser.parse(txt.toByteArray(), "Like List.txt", utc)
        assertEquals(2, r.likes.size)
        assertEquals(1, r.skipped)
    }

    @Test fun zip_findsJsonInside() {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry("TikTok_Data/readme.html")); z.write("<html/>".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("TikTok_Data/user_data_tiktok.json")); z.write(classic.toByteArray()); z.closeEntry()
        }
        val r = TikTokExportParser.parse(bos.toByteArray(), "export.zip", utc)
        assertEquals(2, r.likes.size)
        assertTrue(r.format.startsWith("ZIP"))
    }

    @Test fun zip_txtLikeListFile() {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry("Activity/Like List.txt"))
            z.write("Date: 2024-11-05 21:14:03\nLink: https://www.tiktokv.com/share/video/7434000000000000001/\n".toByteArray())
            z.closeEntry()
            z.putNextEntry(ZipEntry("Activity/Browsing History.txt"))
            z.write("Date: 2024-11-05 21:14:03\nLink: https://www.tiktokv.com/share/video/7434000000000000009/\n".toByteArray())
            z.closeEntry()
        }
        val r = TikTokExportParser.parse(bos.toByteArray(), "export.zip", utc)
        assertEquals(1, r.likes.size)
    }

    @Test fun invalidAndUnrelatedFiles_giveWarnings() {
        val bad = TikTokExportParser.parse("{not json".toByteArray(), "x.json", utc)
        assertEquals(0, bad.likes.size)
        assertTrue(bad.warnings.isNotEmpty())
        val unrelated = TikTokExportParser.parse("{\"Profile\":{\"name\":\"x\"}}".toByteArray(), "x.json", utc)
        assertEquals(0, unrelated.likes.size)
        assertTrue(unrelated.warnings.first().contains("Like List"))
    }

    @Test fun dateFormats() {
        val p = TikTokExportParser
        assertEquals(Instant.parse("2024-11-05T21:14:03Z").toEpochMilli(), p.parseDate("2024-11-05T21:14:03Z", utc))
        assertEquals(Instant.parse("2024-11-05T14:14:03Z").toEpochMilli(), p.parseDate("2024-11-05T21:14:03+07:00", utc))
        assertEquals(1730841243000L, p.parseDate("1730841243", utc))
        assertEquals(Instant.parse("2024-11-05T21:14:03Z").toEpochMilli(), p.parseDate("2024-11-05 21:14:03 UTC", ZoneId.of("Asia/Bangkok")))
        assertNull(p.parseDate("yesterday", utc))
    }

    @Test fun videoIdExtraction() {
        assertEquals("7434000000000000001", TikTokExportParser.extractVideoId("https://www.tiktokv.com/share/video/7434000000000000001/"))
        assertEquals("7470000000000000009", TikTokExportParser.extractVideoId("https://www.tiktok.com/@a/video/7470000000000000009?lang=th"))
        assertNull(TikTokExportParser.extractVideoId("https://vm.tiktok.com/ZSabc/"))
    }
}
