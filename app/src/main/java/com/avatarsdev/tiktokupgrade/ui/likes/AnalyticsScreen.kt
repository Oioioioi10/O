package com.avatarsdev.tiktokupgrade.ui.likes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avatarsdev.tiktokupgrade.data.db.DataSource
import com.avatarsdev.tiktokupgrade.ui.Routes
import com.avatarsdev.tiktokupgrade.ui.components.BarChart
import com.avatarsdev.tiktokupgrade.ui.components.EmptyState
import com.avatarsdev.tiktokupgrade.ui.components.PremiumCard
import com.avatarsdev.tiktokupgrade.ui.components.SampleBanner
import com.avatarsdev.tiktokupgrade.ui.components.ScreenHeader
import com.avatarsdev.tiktokupgrade.ui.components.SectionTitle
import com.avatarsdev.tiktokupgrade.ui.components.StatTile
import com.avatarsdev.tiktokupgrade.ui.components.StatTileText
import com.avatarsdev.tiktokupgrade.ui.settings.SettingsViewModel
import com.avatarsdev.tiktokupgrade.ui.theme.Brand
import com.avatarsdev.tiktokupgrade.util.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

val ScreenPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 28.dp)

private fun LocalDate.utcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.utcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangePickerDialog(initial: DateRange?, onDismiss: () -> Unit, onConfirm: (LocalDate, LocalDate) -> Unit) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial?.start?.utcMillis(),
        initialSelectedEndDateMillis = initial?.end?.utcMillis(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val s = state.selectedStartDateMillis ?: return@TextButton
                    val e = state.selectedEndDateMillis ?: s
                    onConfirm(s.utcDate(), e.utcDate())
                },
            ) { Text("ตกลง") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ยกเลิก") } },
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.weight(1f),
            title = { Text("เลือกช่วงวันที่", modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) },
        )
    }
}

/** Range summary + picker + quick ranges, shared by Analytics and History. */
@Composable
fun RangeSelector(state: LikesUiState, vm: LikesViewModel) {
    var picking by remember { mutableStateOf(false) }
    PremiumCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("ช่วงเวลา", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val r = state.range
                Text(
                    if (r == null) "—" else if (r.start == r.end) Fmt.date(r.start) else "${Fmt.date(r.start)} – ${Fmt.date(r.end)}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("rangeText"),
                )
            }
            FilledTonalButton(onClick = { picking = true }, modifier = Modifier.testTag("pickRange")) { Text("เลือก") }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val r = state.range
            val today = LocalDate.now()
            FilterChip(selected = r != null && r.start == today && r.end == today, onClick = { vm.quickRange(1) }, label = { Text("วันนี้") })
            FilterChip(selected = r != null && r.end == today && r.start == today.minusDays(6), onClick = { vm.quickRange(7) }, label = { Text("7 วัน") })
            FilterChip(selected = r != null && r.end == today && r.start == today.minusDays(29), onClick = { vm.quickRange(30) }, label = { Text("30 วัน") })
            FilterChip(selected = r != null && r == state.dataRange && !state.rangeIsAuto, onClick = { vm.allTime() }, label = { Text("ทั้งหมด") })
            FilterChip(selected = state.rangeIsAuto, onClick = { vm.autoRange() }, label = { Text("7 วันล่าสุดที่มีข้อมูล") })
        }
    }
    if (picking) {
        RangePickerDialog(
            initial = state.range,
            onDismiss = { picking = false },
            onConfirm = { s, e -> vm.setRange(s, e); picking = false },
        )
    }
}

@Composable
fun NoDataState(isSample: Boolean, settingsVm: SettingsViewModel, onNavigate: (String) -> Unit) {
    EmptyState(
        icon = Icons.Outlined.Insights,
        title = if (isSample) "ยังไม่มีข้อมูลตัวอย่าง" else "ยังไม่มีประวัติ Like",
        body = "นำเข้าไฟล์จาก TikTok (Settings › Account › Download your data) หรือทดลองด้วยข้อมูลตัวอย่างที่แยกจากข้อมูลจริง",
    ) {
        Button(onClick = { onNavigate(Routes.SETTINGS) }, modifier = Modifier.fillMaxWidth()) { Text("นำเข้าไฟล์ TikTok") }
        OutlinedButton(onClick = { settingsVm.loadSample() }, modifier = Modifier.fillMaxWidth().testTag("trySample")) { Text("ลองด้วยข้อมูลตัวอย่าง") }
    }
}

@Composable
fun AnalyticsScreen(vm: LikesViewModel, settingsVm: SettingsViewModel, onNavigate: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            vm.exportCsv(uri) { r ->
                settingsVm.toast(r.fold({ "Export CSV สำเร็จ $it รายการ" }, { "Export ไม่สำเร็จ: ${it.message}" }))
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize().testTag("analyticsList"), contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenHeader("Like Analytics", "สถิติการกด Like ตามช่วงวันที่ที่เลือก") }
        if (state.settings.isSample) item { SampleBanner(onSwitchToReal = { settingsVm.setDataSource(DataSource.REAL) }) }
        if (state.loaded && state.allCount == 0) {
            item { NoDataState(state.settings.isSample, settingsVm, onNavigate) }
            return@LazyColumn
        }
        item { RangeSelector(state, vm) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Likes รวม", state.summary.total, Modifier.weight(1f).testTag("totalLikes"), accent = Brand.Cyan,
                    sub = "${state.summary.days} วัน")
                StatTileText("เฉลี่ยต่อวัน", String.format(java.util.Locale.US, "%.1f", state.summary.averagePerDay), Modifier.weight(1f), accent = Brand.Pink,
                    sub = "ในช่วงที่เลือก")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTileText("วันที่กดมากสุด", state.summary.peakDay?.let { Fmt.shortDate(it.date) } ?: "—", Modifier.weight(1f), accent = Brand.Amber,
                    sub = state.summary.peakDay?.let { "${it.count} ไลก์" })
                StatTileText("ชั่วโมงที่กดบ่อย", state.summary.peakHour?.let { Fmt.hourLabel(it) } ?: "—", Modifier.weight(1f), accent = Brand.Green,
                    sub = state.summary.peakHour?.let { "${state.hourly[it]} ไลก์" })
            }
        }
        item {
            PremiumCard {
                SectionTitle("Likes รายวัน")
                Spacer(Modifier.height(12.dp))
                BarChart(
                    values = state.daily.map { it.count },
                    labels = state.daily.map { Fmt.shortDate(it.date) },
                    modifier = Modifier.testTag("dailyChart"),
                    valueFormatter = { v, l -> "$l · $v ไลก์" },
                )
            }
        }
        item {
            PremiumCard {
                SectionTitle("Likes รายชั่วโมง")
                Text("รวมทุกวันในช่วงที่เลือก (เวลาเครื่อง)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                BarChart(
                    values = state.hourly.toList(),
                    labels = (0..23).map { String.format(java.util.Locale.US, "%02d", it) },
                    modifier = Modifier.testTag("hourlyChart"),
                    height = 150.dp,
                    labelEvery = 3,
                    barBrush = Brand.accentH,
                    valueFormatter = { v, l -> "$l:00–$l:59 · $v ไลก์" },
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        val r = state.range
                        csvLauncher.launch(if (r == null) "tiktok-likes.csv" else "tiktok-likes_${r.start}_${r.end}.csv")
                    },
                    enabled = state.filtered.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(52.dp).testTag("exportCsv"),
                ) {
                    Icon(Icons.Filled.FileDownload, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Export CSV")
                }
                OutlinedButton(onClick = { onNavigate(Routes.HISTORY) }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.AutoMirrored.Filled.List, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ดูรายการคลิป", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
