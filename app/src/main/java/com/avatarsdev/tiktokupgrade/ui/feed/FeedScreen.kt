package com.avatarsdev.tiktokupgrade.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.avatarsdev.tiktokupgrade.domain.feed.ClipScore
import com.avatarsdev.tiktokupgrade.domain.feed.FeedPlanner
import com.avatarsdev.tiktokupgrade.domain.feed.FeedRules
import com.avatarsdev.tiktokupgrade.ui.components.Pill
import com.avatarsdev.tiktokupgrade.ui.components.PremiumCard
import com.avatarsdev.tiktokupgrade.ui.components.ScreenHeader
import com.avatarsdev.tiktokupgrade.ui.components.SectionTitle
import com.avatarsdev.tiktokupgrade.ui.components.WeightBar
import com.avatarsdev.tiktokupgrade.ui.theme.Brand
import com.avatarsdev.tiktokupgrade.util.Fmt

const val EXAMPLE_PROMPT = "แสดงคลิป Roblox 50%\nAI Tools 30%\nProgramming 20%\nไม่ต้องการคลิปเต้น\nไม่ต้องการคลิปตลก"

private val quickPrompts = listOf(
    "ตัวอย่างจากโจทย์" to EXAMPLE_PROMPT,
    "เกมเมอร์" to "Roblox 40%, Minecraft 40%, Gaming 20%, no dance",
    "สายเรียนรู้" to "AI Tools 40%\nProgramming 40%\nความรู้ 20%\nไม่เอาดราม่า",
    "ชิลล์" to "สัตว์เลี้ยง 50% อาหาร 30% ท่องเที่ยว 20% ไม่ต้องการข่าว",
)

fun categoryColor(index: Int): Color = Brand.series[index % Brand.series.size]

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedScreen(vm: FeedViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val aiConfigured by vm.aiConfigured.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val inbox by vm.inbox.collectAsStateWithLifecycle()
    val checker by vm.checkerText.collectAsStateWithLifecycle()
    var useAi by rememberSaveable { mutableStateOf(true) }
    var showAllPlan by rememberSaveable { mutableStateOf(false) }
    var saveDialog by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val open: (String) -> Unit = { url -> try { uri.openUri(url) } catch (_: Exception) { } }
    val rules = state.rules

    LazyColumn(
        Modifier.fillMaxSize().testTag("feedList"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("AI Feed Controller", "บอกว่าอยากดูอะไร AI จะแปลงเป็นหมวด Keyword และกติกาจัดอันดับ") }

        item {
            PremiumCard {
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = vm::setPrompt,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).testTag("promptField"),
                    placeholder = { Text("เช่น\nแสดงคลิป Roblox 50%\nAI Tools 30%\nไม่ต้องการคลิปเต้น") },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickPrompts.forEach { (label, text) ->
                        SuggestionChip(onClick = { vm.setPrompt(text) }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (aiConfigured && useAi) "AI ภายนอก (LLM)" else "AI ในเครื่อง (ออฟไลน์)", style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (aiConfigured) "สลับได้ · ตั้งค่า API ใน Settings" else "เพิ่ม API ของ LLM ได้ใน Settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (aiConfigured) Switch(checked = useAi, onCheckedChange = { useAi = it })
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { vm.analyze(useAi) },
                    enabled = !state.parsing,
                    modifier = Modifier.fillMaxWidth().height(54.dp).testTag("analyzeButton"),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (state.parsing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Filled.AutoAwesome, null, Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(if (state.parsing) "กำลังวิเคราะห์…" else "วิเคราะห์ Prompt", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        state.notice?.let { n ->
            item { NoticeCard(n, onDismiss = vm::dismissNotice) }
        }

        if (rules != null && !rules.isEmpty) {
            item { RulesCard(rules) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = { saveDialog = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Bookmark, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("บันทึกโปรไฟล์")
                    }
                }
            }
            if (state.plan.isNotEmpty()) {
                item {
                    PremiumCard(Modifier.testTag("planCard")) {
                        SectionTitle("แผนเซสชัน 20 คลิป", action = {
                            TextButton(onClick = { showAllPlan = !showAllPlan }) { Text(if (showAllPlan) "ย่อ" else "ดูทั้งหมด") }
                        })
                        Text(
                            "ระบบไม่สามารถสั่งอัลกอริทึม TikTok ได้ จึงจัดเป็นลำดับการค้นหาตามสัดส่วน — แตะเพื่อเปิดผลค้นหาใน TikTok",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        val counts = FeedPlanner.counts(state.plan)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            rules.include.forEachIndexed { i, c -> Pill("${c.label} × ${counts[c.id] ?: 0}", categoryColor(i)) }
                        }
                        Spacer(Modifier.height(8.dp))
                        val colorById = rules.include.mapIndexed { i, c -> c.id to categoryColor(i) }.toMap()
                        val shown = if (showAllPlan) state.plan else state.plan.take(6)
                        shown.forEach { slot ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(28.dp).clip(CircleShape).background((colorById[slot.categoryId] ?: Brand.Cyan).copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center,
                                ) { Text("${slot.index}", style = MaterialTheme.typography.labelMedium, color = colorById[slot.categoryId] ?: Brand.Cyan) }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(slot.query, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(slot.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { open(slot.searchUrl) }) { Icon(Icons.Filled.PlayArrow, "เปิดใน TikTok") }
                            }
                        }
                    }
                }
            }
        } else if (rules != null) {
            item { NoticeCard(rules.warnings.firstOrNull() ?: "ไม่พบหมวดในคำสั่ง", onDismiss = null) }
        }

        item {
            PremiumCard {
                SectionTitle("ตรวจคลิป / คิวจัดอันดับ")
                Text(
                    "แชร์คลิปจาก TikTok มาที่แอปนี้ (แชร์ › TikTok Upgrade AI) หรือวางแคปชัน/แฮชแท็กที่นี่ แล้วระบบจะให้คะแนนและเรียงตามกติกาด้านบน",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = checker,
                    onValueChange = vm::setChecker,
                    modifier = Modifier.fillMaxWidth().testTag("checkerField"),
                    placeholder = { Text("เช่น สอนทำเกม #roblox #robloxstudio") },
                    shape = RoundedCornerShape(14.dp),
                    maxLines = 4,
                )
                val live = if (checker.isBlank()) null else vm.score(checker)
                AnimatedVisibility(live != null, enter = fadeIn() + expandVertically()) {
                    live?.let { ScoreLine(it, Modifier.padding(top = 10.dp).testTag("checkerScore")) }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { vm.addToInbox(checker) }, enabled = checker.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text("เพิ่มเข้าคิว")
                }
            }
        }

        if (inbox.isNotEmpty()) {
            item {
                SectionTitle("คิวที่จัดอันดับแล้ว (${inbox.size})", action = {
                    TextButton(onClick = { vm.clearInbox() }) { Text("ล้าง") }
                })
            }
            items(inbox, key = { it.first.id }) { (clip, score) ->
                Surface(
                    Modifier.fillMaxWidth().animateItem(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = if (score.blocked) BorderStroke(1.dp, Brand.Pink.copy(alpha = 0.4f)) else null,
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ScoreBadge(score)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(clip.text, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            Text(score.reason, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                                color = if (score.blocked) Brand.Pink else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        clip.url?.let { u -> IconButton(onClick = { open(u) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "เปิด") } }
                        IconButton(onClick = { vm.deleteInbox(clip.id) }) { Icon(Icons.Filled.Delete, "ลบ") }
                    }
                }
            }
        }

        if (profiles.isNotEmpty()) {
            item { SectionTitle("โปรไฟล์ที่บันทึกไว้") }
            items(profiles, key = { "p_${it.id}" }) { p ->
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text(p.prompt.replace("\n", " · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(onClick = { vm.loadProfile(p) }) { Text("ใช้") }
                        IconButton(onClick = { vm.deleteProfile(p.id) }) { Icon(Icons.Filled.Delete, "ลบ") }
                    }
                }
            }
        }

        item { LimitsCard() }
    }

    if (saveDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saveDialog = false },
            title = { Text("บันทึกโปรไฟล์ฟีด") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("ชื่อโปรไฟล์ เช่น โหมดทำงาน") }) },
            confirmButton = { TextButton(onClick = { vm.saveProfile(name); saveDialog = false }) { Text("บันทึก") } },
            dismissButton = { TextButton(onClick = { saveDialog = false }) { Text("ยกเลิก") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RulesCard(rules: FeedRules) {
    PremiumCard(Modifier.testTag("rulesCard")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ผลการวิเคราะห์", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Pill(if (rules.engine == FeedRules.ENGINE_LLM) "LLM" else "ในเครื่อง", MaterialTheme.colorScheme.primary)
        }
        if (rules.include.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("สัดส่วนที่อยากดู", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            rules.include.forEachIndexed { i, c ->
                WeightBar(c.label + if (c.custom) " (กำหนดเอง)" else "", c.weight, categoryColor(i), Modifier.testTag("weight_${c.id}"))
            }
        }
        if (rules.exclude.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("ไม่แสดง", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rules.exclude.forEach { e ->
                    Surface(shape = RoundedCornerShape(50), color = Brand.Pink.copy(alpha = 0.14f), border = BorderStroke(1.dp, Brand.Pink.copy(alpha = 0.45f)), modifier = Modifier.testTag("exclude_${e.id}")) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Block, null, tint = Brand.Pink, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(e.label, color = Brand.Pink, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        if (rules.include.isNotEmpty() || rules.exclude.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Keyword", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            rules.include.forEachIndexed { i, c ->
                Text(c.label, style = MaterialTheme.typography.labelMedium, color = categoryColor(i))
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    c.keywords.take(6).forEach { k -> Pill(k, MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Spacer(Modifier.height(8.dp))
            }
            rules.exclude.forEach { e ->
                Text("${e.label} (กรองออก)", style = MaterialTheme.typography.labelMedium, color = Brand.Pink)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    e.keywords.take(6).forEach { k -> Pill(k, Brand.Pink) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (rules.rankingRules.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("เงื่อนไขการจัดอันดับ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            rules.rankingRules.forEachIndexed { i, r ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                    Text(r, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        rules.warnings.forEach { w ->
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, null, tint = Brand.Amber, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(w, style = MaterialTheme.typography.bodySmall, color = Brand.Amber)
            }
        }
    }
}

@Composable
private fun ScoreBadge(score: ClipScore) {
    val color = when {
        score.blocked -> Brand.Pink
        score.score >= 50 -> Brand.Green
        score.score > 0 -> Brand.Cyan
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        if (score.blocked) Icon(Icons.Filled.Block, "ถูกกรอง", tint = color, modifier = Modifier.size(20.dp))
        else Text("${score.score}", color = color, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ScoreLine(score: ClipScore, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        ScoreBadge(score)
        Spacer(Modifier.width(10.dp))
        Text(score.reason, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NoticeCard(text: String, onDismiss: (() -> Unit)?) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (onDismiss != null) TextButton(onClick = onDismiss) { Text("ปิด") }
        }
    }
}

@Composable
fun LimitsCard() {
    PremiumCard {
        Text("ข้อจำกัดที่ควรรู้", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        listOf(
            "TikTok ไม่มี API ให้แอปภายนอกควบคุมฟีด For You — แอปนี้จึงไม่ได้เปลี่ยนอัลกอริทึมของ TikTok โดยตรง",
            "สิ่งที่ทำได้จริง: แผนค้นหาตามสัดส่วน, ตรวจ/จัดอันดับคลิปที่แชร์เข้ามา และแนะนำให้กด \"ไม่สนใจ\" กับหมวดที่ไม่ต้องการ",
            "ประวัติ Like ใช้ไฟล์ที่คุณดาวน์โหลดเองจาก TikTok — ข้อมูลอยู่ในเครื่องนี้เท่านั้น",
        ).forEach {
            Row(Modifier.padding(vertical = 3.dp)) {
                Text("•  ", color = MaterialTheme.colorScheme.primary)
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Suppress("unused")
private fun formatCount(n: Int) = Fmt.number(n)
