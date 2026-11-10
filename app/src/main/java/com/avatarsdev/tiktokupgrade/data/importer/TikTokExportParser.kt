package com.avatarsdev.tiktokupgrade.data.importer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream

data class ParsedLike(
    val videoUrl: String,
    val videoId: String?,
    /** Epoch millis. */
    val likedAt: Long,
)

data class ParseResult(
    val likes: List<ParsedLike>,
    /** Human readable description of what was found, e.g. "JSON (Activity › Like List)". */
    val format: String,
    val skipped: Int,
    val warnings: List<String>,
)

/**
 * Reads the "Like List" from the file TikTok gives you under
 * Settings › Account › Download your data (JSON or TXT, optionally still zipped).
 *
 * The export layout has changed over time (e.g. `Activity › Like List › ItemFavoriteList`, a newer
 * `Likes and Favorites` wrapper, and `Date`/`date`, `Link`/`link` casing), so instead of one fixed path
 * the parser walks the whole JSON tree and takes every list found under a "Like List" key.
 * Only the like list is read; favourites, watch history, messages etc. are ignored.
 */
object TikTokExportParser {

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    private val likeListKeys = setOf("likelist", "itemfavoritelist", "likedvideos", "likes")
    private val dateKeys = setOf("date", "time", "timestamp", "likedat", "createtime")
    private val linkKeys = setOf("link", "videolink", "url", "videourl", "link_url")

    fun parse(bytes: ByteArray, fileName: String?, exportZone: ZoneId): ParseResult {
        if (bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            return parseZip(bytes, exportZone)
        }
        val text = decode(bytes)
        return parseText(text, fileName, exportZone)
    }

    fun parseText(text: String, fileName: String?, exportZone: ZoneId): ParseResult {
        val trimmed = text.trimStart()
        return if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            parseJson(trimmed, exportZone)
        } else {
            parseTxt(text, fileName, exportZone)
        }
    }

    private fun decode(bytes: ByteArray): String {
        val s = String(bytes, Charsets.UTF_8)
        return if (s.startsWith("﻿")) s.substring(1) else s
    }

    private fun parseZip(bytes: ByteArray, zone: ZoneId): ParseResult {
        val all = mutableListOf<ParsedLike>()
        val warnings = mutableListOf<String>()
        val formats = mutableListOf<String>()
        var skipped = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                val lower = name.lowercase()
                val wanted = !entry.isDirectory && (
                    lower.endsWith(".json") ||
                        (lower.endsWith(".txt") && lower.substringAfterLast('/').replace(" ", "").contains("likelist"))
                    )
                if (wanted) {
                    val content = decode(zip.readBytes())
                    val r = parseText(content, name, zone)
                    if (r.likes.isNotEmpty()) {
                        all += r.likes
                        formats += "${r.format} ใน ${name.substringAfterLast('/')}"
                    }
                    skipped += r.skipped
                    warnings += r.warnings
                }
                zip.closeEntry()
            }
        }
        val deduped = dedupe(all)
        return ParseResult(
            likes = deduped,
            format = if (formats.isEmpty()) "ZIP (ไม่พบ Like List)" else "ZIP: " + formats.joinToString(", "),
            skipped = skipped,
            warnings = warnings.distinct(),
        )
    }

    private fun parseJson(text: String, zone: ZoneId): ParseResult {
        val root: JsonElement = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            return ParseResult(emptyList(), "JSON (อ่านไม่ได้)", 0, listOf("ไฟล์ JSON ไม่ถูกต้อง: ${e.message?.take(120)}"))
        }
        val found = mutableListOf<Pair<String, JsonArray>>()
        collectLikeLists(root, path = "", insideLikeList = false, out = found)

        // A bare array of {Date, Link} objects (e.g. a hand-made or older partial export).
        if (found.isEmpty() && root is JsonArray) found += "root" to root

        val likes = mutableListOf<ParsedLike>()
        var skipped = 0
        for ((_, arr) in found) {
            for (item in arr) {
                val obj = item as? JsonObject
                val parsed = obj?.let { toLike(it, zone) }
                if (parsed == null) skipped++ else likes += parsed
            }
        }
        val warnings = mutableListOf<String>()
        if (found.isEmpty()) warnings += "ไม่พบส่วน \"Like List\" ในไฟล์นี้ — ตรวจว่าเลือก Activity/Likes ตอนขอข้อมูลจาก TikTok"
        if (skipped > 0) warnings += "ข้ามรายการที่ไม่มีวันที่หรือลิงก์ $skipped รายการ"
        val format = if (found.isEmpty()) "JSON (ไม่พบ Like List)" else "JSON (" + found.joinToString(", ") { it.first.ifEmpty { "root" } } + ")"
        return ParseResult(dedupe(likes), format, skipped, warnings)
    }

    private fun normalizeKey(key: String) = key.lowercase().replace(" ", "").replace("_", "")

    private fun collectLikeLists(
        element: JsonElement,
        path: String,
        insideLikeList: Boolean,
        out: MutableList<Pair<String, JsonArray>>,
    ) {
        when (element) {
            is JsonObject -> for ((key, value) in element) {
                val isLikeKey = normalizeKey(key) in likeListKeys
                val childPath = if (path.isEmpty()) key else "$path › $key"
                if (value is JsonArray && (isLikeKey || insideLikeList)) {
                    if (value.any { it is JsonObject && hasDateAndLink(it) }) {
                        out += childPath to value
                        continue
                    }
                }
                collectLikeLists(value, childPath, insideLikeList || isLikeKey, out)
            }
            is JsonArray -> Unit // Arrays outside a like list (watch history, comments …) are not likes.
            else -> Unit
        }
    }

    private fun hasDateAndLink(obj: JsonObject): Boolean {
        val keys = obj.keys.map { normalizeKey(it) }
        return keys.any { it in dateKeys } && keys.any { it in linkKeys }
    }

    private fun toLike(obj: JsonObject, zone: ZoneId): ParsedLike? {
        var date: String? = null
        var link: String? = null
        for ((k, v) in obj) {
            val nk = normalizeKey(k)
            val prim = v as? JsonPrimitive ?: continue
            if (date == null && nk in dateKeys) date = prim.content
            if (link == null && nk in linkKeys) link = prim.content
        }
        val url = link?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val at = parseDate(date ?: return null, zone) ?: return null
        return ParsedLike(url, extractVideoId(url), at)
    }

    private fun parseTxt(text: String, fileName: String?, zone: ZoneId): ParseResult {
        val likes = mutableListOf<ParsedLike>()
        var pendingDate: String? = null
        var skipped = 0
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            val key = normalizeKey(line.substring(0, idx))
            val value = line.substring(idx + 1).trim()
            when {
                key in dateKeys -> {
                    if (pendingDate != null) skipped++
                    pendingDate = value
                }
                key in linkKeys || key == "videolink" -> {
                    val d = pendingDate
                    val at = d?.let { parseDate(it, zone) }
                    if (at != null && value.isNotEmpty()) likes += ParsedLike(value, extractVideoId(value), at) else skipped++
                    pendingDate = null
                }
            }
        }
        val warnings = mutableListOf<String>()
        if (likes.isEmpty()) warnings += "ไม่พบคู่ Date/Link ในไฟล์ข้อความ ${fileName ?: ""}".trim()
        if (skipped > 0) warnings += "ข้ามรายการที่ไม่สมบูรณ์ $skipped รายการ"
        return ParseResult(dedupe(likes), "TXT", skipped, warnings)
    }

    private fun dedupe(list: List<ParsedLike>): List<ParsedLike> =
        list.distinctBy { it.videoUrl to it.likedAt }.sortedByDescending { it.likedAt }

    private val localPatterns = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
    )

    /**
     * TikTok export dates look like `2024-11-05 21:14:03` with no zone. They are interpreted in
     * [zone] (UTC by default; the user can switch in Settings). Explicit offsets and epoch
     * seconds/millis are honoured as-is.
     */
    fun parseDate(raw: String, zone: ZoneId): Long? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        s.toLongOrNull()?.let { n -> return if (n > 100_000_000_000L) n else n * 1000 }
        if (s.endsWith(" UTC", ignoreCase = true)) {
            s = s.dropLast(4).trim()
            return parseLocal(s, ZoneId.of("UTC"))
        }
        val hasZone = s.contains('T') && (s.endsWith("Z") || s.lastIndexOf('+') > 10 || s.lastIndexOf('-') > 10)
        if (hasZone) {
            try {
                return OffsetDateTime.parse(s).toInstant().toEpochMilli()
            } catch (_: Exception) { }
            try {
                return Instant.parse(s).toEpochMilli()
            } catch (_: Exception) { }
        }
        return parseLocal(s, zone)
    }

    private fun parseLocal(s: String, zone: ZoneId): Long? {
        for (p in localPatterns) {
            try {
                return LocalDateTime.parse(s, p).atZone(zone).toInstant().toEpochMilli()
            } catch (_: Exception) { }
        }
        return null
    }

    private val idPatterns = listOf(
        Regex("/video/(\\d{6,})"),
        Regex("/v/(\\d{6,})"),
        Regex("[?&]item_id=(\\d{6,})"),
        Regex("/share/video/(\\d{6,})"),
    )

    fun extractVideoId(url: String): String? {
        for (r in idPatterns) r.find(url)?.let { return it.groupValues[1] }
        return null
    }
}
