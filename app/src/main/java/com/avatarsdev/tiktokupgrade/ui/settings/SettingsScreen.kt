package com.avatarsdev.tiktokupgrade.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avatarsdev.tiktokupgrade.BuildConfig
import com.avatarsdev.tiktokupgrade.data.ExportTimezone
import com.avatarsdev.tiktokupgrade.data.ThemeMode
import com.avatarsdev.tiktokupgrade.data.db.DataSource
import com.avatarsdev.tiktokupgrade.ui.components.PremiumCard
import com.avatarsdev.tiktokupgrade.ui.components.ScreenHeader
import com.avatarsdev.tiktokupgrade.ui.components.SectionTitle
import com.avatarsdev.tiktokupgrade.ui.theme.Brand
import com.avatarsdev.tiktokupgrade.util.Fmt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val realCount by vm.realCount.collectAsStateWithLifecycle()
    val sampleCount by vm.sampleCount.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val report by vm.lastImport.collectAsStateWithLifecycle()
    var showHowTo by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteReal by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile(uri)
    }

    LazyColumn(
        Modifier.fillMaxSize().testTag("settingsList"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("Settings", "นำเข้าข้อมูล · แหล่งข้อมูล · AI · ธีม") }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }

        // ── Import ────────────────────────────────────────────────────────────
        item {
            PremiumCard {
                SectionTitle("นำเข้า TikTok Data Export")
                Text(
                    "รองรับไฟล์ ZIP, JSON หรือ TXT ที่ดาวน์โหลดจาก TikTok — แอปอ่านเฉพาะส่วน Like List (วันที่ + ลิงก์คลิป)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { picker.launch(arrayOf("application/json", "application/zip", "text/plain", "application/octet-stream", "*/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("importButton"),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.FileUpload, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("เลือกไฟล์จากเครื่อง")
                }
                Spacer(Modifier.height(12.dp))
                Text("เวลาในไฟล์ Export", style = MaterialTheme.typography.labelLarge)
                Text(
                    "TikTok มักบันทึกเวลาเป็น UTC แอปจะแปลงเป็นเวลาเครื่องตอนแสดงผล (ตั้งก่อนนำเข้า)",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val opts = listOf(ExportTimezone.UTC to "UTC (แนะนำ)", ExportTimezone.DEVICE to "เวลาเครื่อง")
                    opts.forEachIndexed { i, (tz, label) ->
                        SegmentedButton(
                            selected = settings.exportTimezone == tz,
                            onClick = { vm.setExportTimezone(tz) },
                            shape = SegmentedButtonDefaults.itemShape(i, opts.size),
                        ) { Text(label) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { showHowTo = !showHowTo }) {
                    Text("วิธีขอไฟล์ข้อมูลจาก TikTok")
                    Icon(if (showHowTo) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                }
                AnimatedVisibility(showHowTo) {
                    Column {
                        listOf(
                            "เปิดแอป TikTok › โปรไฟล์ › เมนู ☰ › Settings and privacy",
                            "Account › Download your data",
                            "เลือกรูปแบบ JSON และเลือกข้อมูลทั้งหมด (หรืออย่างน้อย Activity / Likes)",
                            "กด Request data แล้วรอ TikTok เตรียมไฟล์ (อาจใช้เวลาหลายชั่วโมงถึงหลายวัน)",
                            "กลับมาที่หน้าเดิม › แท็บ Download data › ดาวน์โหลด ZIP",
                            "เลือกไฟล์ ZIP นั้นที่ปุ่มด้านบน",
                        ).forEachIndexed { i, s ->
                            Row(Modifier.padding(vertical = 3.dp)) {
                                Text("${i + 1}. ", color = MaterialTheme.colorScheme.primary)
                                Text(s, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(
                            "เมนูของ TikTok อาจเปลี่ยนตามเวอร์ชันแอปและประเทศ",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                report?.let { r ->
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (r.found > 0) Icons.Filled.CheckCircle else Icons.Filled.Warning, null,
                            tint = if (r.found > 0) Brand.Green else Brand.Amber, modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(r.fileName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("importFileName"))
                    }
                    Text("พบ ${Fmt.number(r.found)} · เพิ่มใหม่ ${Fmt.number(r.inserted)} · ซ้ำ ${Fmt.number(r.duplicates)}",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("importResult"))
                    Text(r.format, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    r.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = Brand.Amber) }
                }
            }
        }

        // ── Data source ──────────────────────────────────────────────────────
        item {
            PremiumCard {
                SectionTitle("แหล่งข้อมูลที่แสดง")
                Text(
                    "ข้อมูลจริงและข้อมูลตัวอย่างเก็บแยกกัน และไม่แสดงปนกัน",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = settings.dataSource == DataSource.REAL,
                        onClick = { vm.setDataSource(DataSource.REAL) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        modifier = Modifier.testTag("sourceReal"),
                    ) { Text("ข้อมูลจริง (${Fmt.number(realCount)})") }
                    SegmentedButton(
                        selected = settings.dataSource == DataSource.SAMPLE,
                        onClick = { vm.setDataSource(DataSource.SAMPLE) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        modifier = Modifier.testTag("sourceSample"),
                    ) { Text("ตัวอย่าง (${Fmt.number(sampleCount)})") }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { vm.loadSample() }, enabled = !busy, modifier = Modifier.weight(1f).testTag("loadSample")) {
                        Text(if (sampleCount > 0) "สร้างตัวอย่างใหม่" else "สร้างข้อมูลตัวอย่าง")
                    }
                    OutlinedButton(onClick = { vm.clear(DataSource.SAMPLE) }, enabled = sampleCount > 0, modifier = Modifier.weight(1f)) {
                        Text("ลบตัวอย่าง")
                    }
                }
                TextButton(onClick = { confirmDeleteReal = true }, enabled = realCount > 0) {
                    Text("ลบข้อมูลจริงทั้งหมด", color = if (realCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ── AI ───────────────────────────────────────────────────────────────
        item { AiSettingsCard(settings.aiBaseUrl, settings.aiApiKey, settings.aiModel, busy, vm) }

        // ── Theme ────────────────────────────────────────────────────────────
        item {
            PremiumCard {
                SectionTitle("ธีม")
                Spacer(Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val opts = listOf(ThemeMode.DARK to "มืด", ThemeMode.LIGHT to "สว่าง", ThemeMode.SYSTEM to "ตามระบบ")
                    opts.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = settings.theme == mode,
                            onClick = { vm.setTheme(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, opts.size),
                        ) { Text(label) }
                    }
                }
            }
        }

        // ── About ────────────────────────────────────────────────────────────
        item {
            PremiumCard {
                SectionTitle("เกี่ยวกับ & ความเป็นส่วนตัว")
                Spacer(Modifier.height(6.dp))
                listOf(
                    "เวอร์ชัน ${BuildConfig.VERSION_NAME}",
                    "ไม่ได้เข้าสู่ระบบ TikTok และไม่ดึงข้อมูลบัญชีใด ๆ — ใช้เฉพาะไฟล์ที่คุณเลือกนำเข้าเอง",
                    "ข้อมูลเก็บในเครื่องนี้เท่านั้น (ปิด Backup) · ส่งข้อความ Prompt ออกไปเฉพาะเมื่อคุณตั้งค่า AI ภายนอก",
                    "แอปนี้ไม่ได้เป็นของหรือร่วมกับ TikTok และควบคุมอัลกอริทึมของ TikTok โดยตรงไม่ได้",
                ).forEach {
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("•  ", color = MaterialTheme.colorScheme.primary)
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (confirmDeleteReal) {
        AlertDialog(
            onDismissRequest = { confirmDeleteReal = false },
            title = { Text("ลบข้อมูลจริงทั้งหมด?") },
            text = { Text("ประวัติ Like ที่นำเข้า ${Fmt.number(realCount)} รายการจะถูกลบออกจากเครื่องนี้ (นำเข้าไฟล์ใหม่ได้ภายหลัง)") },
            confirmButton = {
                TextButton(onClick = { vm.clear(DataSource.REAL); confirmDeleteReal = false }) {
                    Text("ลบ", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteReal = false }) { Text("ยกเลิก") } },
        )
    }
}

@Composable
private fun AiSettingsCard(baseUrl: String, apiKey: String, model: String, busy: Boolean, vm: SettingsViewModel) {
    var url by rememberSaveable(baseUrl) { mutableStateOf(baseUrl) }
    var key by rememberSaveable(apiKey) { mutableStateOf(apiKey) }
    var mdl by rememberSaveable(model) { mutableStateOf(model) }
    var showKey by remember { mutableStateOf(false) }
    PremiumCard {
        SectionTitle("AI สำหรับแปลง Prompt")
        Text(
            "ค่าเริ่มต้นใช้ตัววิเคราะห์ในเครื่อง (ออฟไลน์ ไทย/อังกฤษ) · ใส่ API แบบ OpenAI-compatible ถ้าต้องการให้ LLM ช่วยแปลงคำสั่งที่ซับซ้อน",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            url, { url = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Base URL") }, placeholder = { Text("https://api.openai.com/v1") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            key, { key = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("API Key") },
            leadingIcon = { Icon(Icons.Filled.Lock, null) },
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "แสดง/ซ่อน")
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            mdl, { mdl = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Model") }, placeholder = { Text("เช่น gpt-4o-mini") },
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { vm.saveAi(url, key, mdl) }, modifier = Modifier.weight(1f)) { Text("บันทึก") }
            OutlinedButton(
                onClick = { vm.testAi(url, key, mdl) },
                enabled = !busy && url.isNotBlank() && key.isNotBlank() && mdl.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) { Text("ทดสอบ") }
        }
        if (baseUrl.isNotBlank()) {
            TextButton(onClick = { url = ""; key = ""; mdl = ""; vm.saveAi("", "", "") }) { Text("กลับไปใช้ AI ในเครื่อง") }
        }
    }
}
