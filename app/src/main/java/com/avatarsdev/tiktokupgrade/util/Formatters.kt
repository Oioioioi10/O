package com.avatarsdev.tiktokupgrade.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    private val thai = Locale("th", "TH")
    private val dateTime = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm:ss", thai)
    private val dateOnly = DateTimeFormatter.ofPattern("d MMM yyyy", thai)
    private val shortDay = DateTimeFormatter.ofPattern("d MMM", thai)
    private val dayHeader = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", thai)

    fun dateTime(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String = dateTime.format(Instant.ofEpochMilli(epochMs).atZone(zone))
    fun time(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String = DateTimeFormatter.ofPattern("HH:mm:ss").format(Instant.ofEpochMilli(epochMs).atZone(zone))
    fun date(d: LocalDate): String = dateOnly.format(d)
    fun shortDate(d: LocalDate): String = shortDay.format(d)
    fun dayHeader(d: LocalDate): String = dayHeader.format(d)
    fun number(n: Int): String = String.format(Locale.US, "%,d", n)
    fun hourLabel(h: Int): String = String.format(Locale.US, "%02d:00", h)
}
