package com.avatarsdev.tiktokupgrade.domain.analytics

import com.avatarsdev.tiktokupgrade.data.db.LikeEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CsvExporter {
    private val localFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    const val HEADER = "liked_at_local,liked_at_utc,timezone,video_id,video_url,source"

    /** UTF-8 with BOM so Excel opens Thai/Unicode correctly. */
    fun toCsv(likes: List<LikeEntity>, zone: ZoneId): String {
        val sb = StringBuilder("﻿").append(HEADER).append("\r\n")
        for (l in likes) {
            val inst = Instant.ofEpochMilli(l.likedAt)
            sb.append(escape(localFmt.format(inst.atZone(zone)))).append(',')
                .append(escape(inst.toString())).append(',')
                .append(escape(zone.id)).append(',')
                .append(escape(l.videoId ?: "")).append(',')
                .append(escape(l.videoUrl)).append(',')
                .append(escape(l.source)).append("\r\n")
        }
        return sb.toString()
    }

    fun escape(value: String): String {
        // Neutralise spreadsheet formula injection, then quote when needed.
        val safe = if (value.isNotEmpty() && value[0] in "=+-@") "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else safe
    }
}
