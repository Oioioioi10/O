package com.avatarsdev.tiktokupgrade.data

import android.content.Context
import com.avatarsdev.tiktokupgrade.data.db.DataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZoneId

enum class ThemeMode { DARK, LIGHT, SYSTEM }

/** How zone-less timestamps in the TikTok export are interpreted at import time. */
enum class ExportTimezone { UTC, DEVICE }

data class AppSettings(
    /** Which data set the analytics screens show: [DataSource.REAL] or [DataSource.SAMPLE]. Never both. */
    val dataSource: String = DataSource.REAL,
    val theme: ThemeMode = ThemeMode.DARK,
    val exportTimezone: ExportTimezone = ExportTimezone.UTC,
    val aiBaseUrl: String = "",
    val aiApiKey: String = "",
    val aiModel: String = "",
    val lastPrompt: String = "",
    val activeProfileId: Long = -1,
) {
    val aiConfigured get() = aiBaseUrl.isNotBlank() && aiApiKey.isNotBlank() && aiModel.isNotBlank()
    val isSample get() = dataSource == DataSource.SAMPLE
    fun exportZone(): ZoneId = if (exportTimezone == ExportTimezone.UTC) ZoneId.of("UTC") else ZoneId.systemDefault()
}

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private fun load() = AppSettings(
        dataSource = prefs.getString("dataSource", DataSource.REAL) ?: DataSource.REAL,
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", "DARK")!!) }.getOrDefault(ThemeMode.DARK),
        exportTimezone = runCatching { ExportTimezone.valueOf(prefs.getString("exportTz", "UTC")!!) }.getOrDefault(ExportTimezone.UTC),
        aiBaseUrl = prefs.getString("aiBaseUrl", "") ?: "",
        aiApiKey = prefs.getString("aiApiKey", "") ?: "",
        aiModel = prefs.getString("aiModel", "") ?: "",
        lastPrompt = prefs.getString("lastPrompt", "") ?: "",
        activeProfileId = prefs.getLong("activeProfileId", -1),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        prefs.edit()
            .putString("dataSource", next.dataSource)
            .putString("theme", next.theme.name)
            .putString("exportTz", next.exportTimezone.name)
            .putString("aiBaseUrl", next.aiBaseUrl)
            .putString("aiApiKey", next.aiApiKey)
            .putString("aiModel", next.aiModel)
            .putString("lastPrompt", next.lastPrompt)
            .putLong("activeProfileId", next.activeProfileId)
            .apply()
        _state.value = next
    }
}
