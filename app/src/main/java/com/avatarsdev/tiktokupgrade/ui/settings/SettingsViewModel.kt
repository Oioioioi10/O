package com.avatarsdev.tiktokupgrade.ui.settings

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.avatarsdev.tiktokupgrade.TikTokUpgradeApp
import com.avatarsdev.tiktokupgrade.data.AppSettings
import com.avatarsdev.tiktokupgrade.data.ExportTimezone
import com.avatarsdev.tiktokupgrade.data.ThemeMode
import com.avatarsdev.tiktokupgrade.data.db.DataSource
import com.avatarsdev.tiktokupgrade.domain.feed.LlmException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportReport(
    val fileName: String,
    val found: Int,
    val inserted: Int,
    val duplicates: Int,
    val format: String,
    val warnings: List<String>,
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as TikTokUpgradeApp).container

    val settings: StateFlow<AppSettings> = container.settings.state
    val realCount: StateFlow<Int> = container.likes.observeCount(DataSource.REAL).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val sampleCount: StateFlow<Int> = container.likes.observeCount(DataSource.SAMPLE).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _lastImport = MutableStateFlow<ImportReport?>(null)
    val lastImport = _lastImport.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun consumeMessage() { _message.value = null }
    fun toast(msg: String) { _message.value = msg }

    fun importFile(uri: Uri) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val resolver = getApplication<Application>().contentResolver
                val (bytes, name) = withContext(Dispatchers.IO) {
                    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: uri.lastPathSegment ?: "ไฟล์"
                    val b = resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                    b to name
                }
                val outcome = container.likes.importExport(bytes, name, container.settings.state.value.exportZone())
                _lastImport.value = ImportReport(
                    fileName = name,
                    found = outcome.parse.likes.size,
                    inserted = outcome.inserted,
                    duplicates = outcome.duplicates,
                    format = outcome.parse.format,
                    warnings = outcome.parse.warnings,
                )
                if (outcome.parse.likes.isNotEmpty()) {
                    container.settings.update { it.copy(dataSource = DataSource.REAL) }
                    _message.value = "นำเข้าใหม่ ${outcome.inserted} รายการ (ซ้ำ ${outcome.duplicates})"
                } else {
                    _message.value = "ไม่พบประวัติการกด Like ในไฟล์นี้"
                }
            } catch (e: OutOfMemoryError) {
                _message.value = "ไฟล์ใหญ่เกินไป — แตกไฟล์ ZIP แล้วเลือกเฉพาะไฟล์ JSON"
            } catch (e: Exception) {
                _message.value = "นำเข้าไม่สำเร็จ: ${e.message ?: e::class.java.simpleName}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun loadSample(switchTo: Boolean = true) {
        viewModelScope.launch {
            _busy.value = true
            val n = container.likes.loadSample()
            if (switchTo) container.settings.update { it.copy(dataSource = DataSource.SAMPLE) }
            _busy.value = false
            _message.value = "สร้างข้อมูลตัวอย่าง $n รายการ (แยกจากข้อมูลจริง)"
        }
    }

    fun clear(source: String) {
        viewModelScope.launch {
            container.likes.clear(source)
            _lastImport.value = null
            _message.value = if (source == DataSource.SAMPLE) "ลบข้อมูลตัวอย่างแล้ว" else "ลบข้อมูลจริงทั้งหมดแล้ว"
        }
    }

    fun setDataSource(source: String) = container.settings.update { it.copy(dataSource = source) }
    fun setTheme(mode: ThemeMode) = container.settings.update { it.copy(theme = mode) }
    fun setExportTimezone(tz: ExportTimezone) = container.settings.update { it.copy(exportTimezone = tz) }

    fun saveAi(baseUrl: String, apiKey: String, model: String) {
        container.settings.update { it.copy(aiBaseUrl = baseUrl.trim(), aiApiKey = apiKey.trim(), aiModel = model.trim()) }
        _message.value = if (baseUrl.isBlank() && apiKey.isBlank()) "ใช้ AI ในเครื่อง (ออฟไลน์)" else "บันทึกการตั้งค่า AI แล้ว"
    }

    fun testAi(baseUrl: String, apiKey: String, model: String) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            _message.value = try {
                val rules = container.llm.parse("Roblox 60%, AI Tools 40%, no dance", baseUrl, apiKey, model)
                "เชื่อมต่อสำเร็จ: " + rules.include.joinToString { "${it.label} ${it.weight}%" }
            } catch (e: LlmException) {
                "เชื่อมต่อไม่สำเร็จ: ${e.message}"
            } catch (e: Exception) {
                "เชื่อมต่อไม่สำเร็จ: ${e.message ?: e::class.java.simpleName}"
            }
            _busy.value = false
        }
    }
}
