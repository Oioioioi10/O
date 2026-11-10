package com.avatarsdev.tiktokupgrade.ui.likes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avatarsdev.tiktokupgrade.data.db.DataSource
import com.avatarsdev.tiktokupgrade.data.db.LikeEntity
import com.avatarsdev.tiktokupgrade.domain.analytics.SortOrder
import com.avatarsdev.tiktokupgrade.ui.components.Pill
import com.avatarsdev.tiktokupgrade.ui.components.SampleBanner
import com.avatarsdev.tiktokupgrade.ui.components.ScreenHeader
import com.avatarsdev.tiktokupgrade.ui.settings.SettingsViewModel
import com.avatarsdev.tiktokupgrade.ui.theme.Brand
import com.avatarsdev.tiktokupgrade.util.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private sealed interface HistoryRow {
    data class Header(val date: LocalDate, val count: Int) : HistoryRow
    data class Item(val like: LikeEntity) : HistoryRow
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(vm: LikesViewModel, settingsVm: SettingsViewModel, onNavigate: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val rows = remember(state.filtered) {
        val out = ArrayList<HistoryRow>(state.filtered.size + 32)
        val groups = state.filtered.groupBy { Instant.ofEpochMilli(it.likedAt).atZone(zone).toLocalDate() }
        for ((d, list) in groups) {
            out += HistoryRow.Header(d, list.size)
            list.forEach { out += HistoryRow.Item(it) }
        }
        out
    }
    val uri = LocalUriHandler.current

    LazyColumn(
        Modifier.fillMaxSize().testTag("historyList"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ScreenHeader("History", "คลิปที่กด Like พร้อมวันและเวลา") }
        if (state.settings.isSample) item { SampleBanner(onSwitchToReal = { settingsVm.setDataSource(DataSource.REAL) }) }
        if (state.loaded && state.allCount == 0) {
            item { NoDataState(state.settings.isSample, settingsVm, onNavigate) }
            return@LazyColumn
        }
        item { RangeSelector(state, vm) }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth().testTag("searchField"),
                singleLine = true,
                placeholder = { Text("ค้นหา Video ID, ลิงก์ หรือเวลา (เช่น 21:)") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Clear, "ล้าง") }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${Fmt.number(state.filtered.size)} คลิป",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).testTag("historyCount"),
                )
                AssistChip(
                    onClick = vm::toggleOrder,
                    modifier = Modifier.testTag("sortToggle"),
                    label = { Text(if (state.order == SortOrder.NEWEST_FIRST) "ใหม่ → เก่า" else "เก่า → ใหม่") },
                    leadingIcon = {
                        Icon(if (state.order == SortOrder.NEWEST_FIRST) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward, null, Modifier.size(18.dp))
                    },
                )
            }
        }
        if (rows.isEmpty()) {
            item {
                Text(
                    if (state.query.isNotBlank()) "ไม่พบผลลัพธ์สำหรับ \"${state.query}\"" else "ไม่มีการกด Like ในช่วงนี้",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        items(rows, key = { r -> if (r is HistoryRow.Header) "h_${r.date}" else "i_${(r as HistoryRow.Item).like.id}" }) { row ->
            when (row) {
                is HistoryRow.Header -> Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp).animateItem(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(Fmt.dayHeader(row.date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    Text("${row.count} ไลก์", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is HistoryRow.Item -> LikeRow(row.like, zone, Modifier.animateItem()) { url ->
                    try { uri.openUri(url) } catch (_: Exception) { settingsVm.toast("เปิดลิงก์ไม่ได้") }
                }
            }
        }
    }
}

@Composable
private fun LikeRow(like: LikeEntity, zone: ZoneId, modifier: Modifier = Modifier, onOpen: (String) -> Unit) {
    val sample = like.source == DataSource.SAMPLE
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Brand.Pink.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Favorite, null, tint = Brand.Pink, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.time(like.likedAt, zone), style = MaterialTheme.typography.titleMedium)
                    if (sample) {
                        Spacer(Modifier.width(8.dp))
                        Pill("ตัวอย่าง", Brand.Amber)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    like.videoId?.let { "Video ID $it" } ?: like.videoUrl,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    Fmt.dateTime(like.likedAt, zone),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onOpen(like.videoUrl) }, enabled = !sample) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "เปิดคลิป")
            }
        }
    }
}
