package com.avatarsdev.tiktokupgrade.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avatarsdev.tiktokupgrade.data.db.DataSource
import com.avatarsdev.tiktokupgrade.ui.Routes
import com.avatarsdev.tiktokupgrade.ui.components.BarChart
import com.avatarsdev.tiktokupgrade.ui.components.Pill
import com.avatarsdev.tiktokupgrade.ui.components.PremiumCard
import com.avatarsdev.tiktokupgrade.ui.components.SampleBanner
import com.avatarsdev.tiktokupgrade.ui.components.SectionTitle
import com.avatarsdev.tiktokupgrade.ui.components.StatTile
import com.avatarsdev.tiktokupgrade.ui.components.StatTileText
import com.avatarsdev.tiktokupgrade.ui.components.WeightBar
import com.avatarsdev.tiktokupgrade.ui.feed.FeedViewModel
import com.avatarsdev.tiktokupgrade.ui.feed.categoryColor
import com.avatarsdev.tiktokupgrade.ui.likes.LikesViewModel
import com.avatarsdev.tiktokupgrade.ui.likes.NoDataState
import com.avatarsdev.tiktokupgrade.ui.settings.SettingsViewModel
import com.avatarsdev.tiktokupgrade.ui.theme.Brand
import com.avatarsdev.tiktokupgrade.util.Fmt

@Composable
fun DashboardScreen(
    likesVm: LikesViewModel,
    feedVm: FeedViewModel,
    settingsVm: SettingsViewModel,
    onNavigate: (String) -> Unit,
) {
    val likes by likesVm.state.collectAsStateWithLifecycle()
    val feed by feedVm.state.collectAsStateWithLifecycle()
    val ov = likes.overview
    val isSample = likes.settings.isSample

    LazyColumn(
        Modifier.fillMaxSize().testTag("dashboardList"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Hero(isSample) }
        if (isSample) item { SampleBanner(onSwitchToReal = { settingsVm.setDataSource(DataSource.REAL) }) }

        if (likes.loaded && ov.total == 0) {
            item { NoDataState(isSample, settingsVm, onNavigate) }
        } else if (likes.loaded) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Likes ทั้งหมด", ov.total, Modifier.weight(1f).testTag("dashTotal"), accent = Brand.Cyan)
                    StatTile("7 วันล่าสุด", ov.last7Days, Modifier.weight(1f), accent = Brand.Pink)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("วันนี้", ov.today, Modifier.weight(1f), accent = Brand.Amber)
                    StatTileText("ช่วงที่กดบ่อย", ov.peakHour?.let { Fmt.hourLabel(it) } ?: "—", Modifier.weight(1f), accent = Brand.Green,
                        sub = ov.lastLikeAt?.let { "ล่าสุด " + Fmt.dateTime(it).substringBefore(" ·") })
                }
            }
            item {
                PremiumCard(Modifier.clickable { onNavigate(Routes.ANALYTICS) }) {
                    SectionTitle("14 วันล่าสุด", action = {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    })
                    Spacer(Modifier.height(10.dp))
                    BarChart(
                        values = ov.sparkline.map { it.count },
                        labels = ov.sparkline.map { Fmt.shortDate(it.date) },
                        height = 110.dp,
                        labelEvery = 4,
                    )
                }
            }
        }

        item {
            PremiumCard {
                SectionTitle("AI Feed ปัจจุบัน", action = {
                    TextButton(onClick = { onNavigate(Routes.FEED) }) { Text(if (feed.rules == null) "ตั้งค่า" else "แก้ไข") }
                })
                val rules = feed.rules
                if (rules == null || rules.isEmpty) {
                    Text("ยังไม่ได้บอก AI ว่าอยากดูอะไร — ลองพิมพ์ \"Roblox 50%, AI Tools 30%, ไม่ต้องการคลิปเต้น\"",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    rules.include.take(4).forEachIndexed { i, c -> WeightBar(c.label, c.weight, categoryColor(i)) }
                    if (rules.exclude.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rules.exclude.take(3).forEach { Pill("ไม่เอา ${it.label}", Brand.Pink) }
                        }
                    }
                }
            }
        }

        item { SectionTitle("เมนู") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NavTile("AI Feed Controller", "กำหนดสัดส่วนคลิป", Icons.Outlined.AutoAwesome, Brand.Cyan, Modifier.weight(1f)) { onNavigate(Routes.FEED) }
                NavTile("Like Analytics", "กราฟรายวัน/ชั่วโมง", Icons.Outlined.Insights, Brand.Pink, Modifier.weight(1f)) { onNavigate(Routes.ANALYTICS) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NavTile("History", "ค้นหาคลิปที่กด Like", Icons.Outlined.History, Brand.Amber, Modifier.weight(1f)) { onNavigate(Routes.HISTORY) }
                NavTile("Settings", "นำเข้าไฟล์ · AI · ธีม", Icons.Outlined.Settings, Brand.Green, Modifier.weight(1f)) { onNavigate(Routes.SETTINGS) }
            }
        }
    }
}

@Composable
private fun Hero(isSample: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(Brand.Cyan.copy(alpha = 0.28f), Color(0x00000000), Brand.Pink.copy(alpha = 0.30f)),
                ),
            )
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f))
            .padding(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(Brand.accent))
                Spacer(Modifier.width(8.dp))
                Text("TikTok Upgrade AI", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Pill(if (isSample) "ข้อมูลตัวอย่าง" else "ข้อมูลจริง", if (isSample) Brand.Amber else Brand.Green)
            }
            Spacer(Modifier.height(14.dp))
            Text("ฟีดของคุณ\nในแบบที่คุณเลือก", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("กำหนดสัดส่วนคอนเทนต์ด้วย AI และดูสถิติการกด Like จากไฟล์ข้อมูลของคุณเอง",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NavTile(title: String, sub: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(116.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
