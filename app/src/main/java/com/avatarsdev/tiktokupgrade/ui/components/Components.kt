package com.avatarsdev.tiktokupgrade.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.avatarsdev.tiktokupgrade.ui.theme.Brand
import com.avatarsdev.tiktokupgrade.ui.theme.NumberStyle
import com.avatarsdev.tiktokupgrade.util.Fmt

@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Always-visible marker that the screen is showing demo data, never the user's real likes. */
@Composable
fun SampleBanner(onSwitchToReal: (() -> Unit)?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("sampleBanner"),
        shape = RoundedCornerShape(16.dp),
        color = Brand.Amber.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, Brand.Amber.copy(alpha = 0.45f)),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Science, contentDescription = null, tint = Brand.Amber, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("ข้อมูลตัวอย่าง", style = MaterialTheme.typography.labelLarge, color = Brand.Amber)
                Text("ไม่ใช่ประวัติ Like จริงของคุณ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onSwitchToReal != null) {
                TextButton(onClick = onSwitchToReal) { Text("ดูข้อมูลจริง", color = Brand.Amber) }
            }
        }
    }
}

@Composable
fun StatTile(label: String, value: Int, modifier: Modifier = Modifier, suffix: String = "", sub: String? = null, accent: Color = MaterialTheme.colorScheme.primary) {
    val animated by animateIntAsState(value, animationSpec = tween(700, easing = FastOutSlowInEasing), label = "stat")
    StatTileText(label, Fmt.number(animated) + suffix, modifier, sub, accent)
}

@Composable
fun StatTileText(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, accent: Color = MaterialTheme.colorScheme.primary) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Text(value, style = NumberStyle, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Animated bar chart drawn on a Canvas. Tap a bar to see its exact value.
 * [labels] has one entry per bar; only a few are drawn so they never overlap.
 */
@Composable
fun BarChart(
    values: List<Int>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    barBrush: Brush = Brand.accent,
    labelEvery: Int = 0,
    valueFormatter: (Int, String) -> String = { v, l -> "$l · $v ไลก์" },
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(values) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val grid = MaterialTheme.colorScheme.outlineVariant
    val highlight = MaterialTheme.colorScheme.onSurface

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            val sel = selected
            Text(
                if (sel != null && sel in values.indices) valueFormatter(values[sel], labels.getOrElse(sel) { "" }) else "สูงสุด ${Fmt.number(max)} · แตะแท่งเพื่อดูค่า",
                style = MaterialTheme.typography.labelMedium,
                color = if (sel != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(values) {
                    detectTapGestures { pos ->
                        if (values.isNotEmpty()) {
                            val slot = size.width.toFloat() / values.size
                            val i = (pos.x / slot).toInt().coerceIn(0, values.size - 1)
                            selected = if (selected == i) null else i
                        }
                    }
                },
        ) {
            if (values.isEmpty()) return@Canvas
            val slot = size.width / values.size
            val gap = (slot * 0.28f).coerceAtMost(10f)
            val barW = (slot - gap).coerceAtLeast(1.5f)
            val r = CornerRadius((barW / 2f).coerceAtMost(10f))
            for (g in 1..3) {
                val y = size.height * g / 4f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            values.forEachIndexed { i, v ->
                val x = i * slot + gap / 2f
                drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), r, alpha = 0.35f)
                val h = size.height * (v.toFloat() / max) * progress.value
                if (h > 0f) {
                    val top = size.height - h
                    if (selected == i) {
                        drawRoundRect(highlight, Offset(x, top), Size(barW, h), r)
                    } else {
                        drawRoundRect(barBrush, Offset(x, top), Size(barW, h), r)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        val every = if (labelEvery > 0) labelEvery else ((labels.size + 3) / 4).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, l ->
                Text(
                    if (i % every == 0) l else "",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

@Composable
fun WeightBar(label: String, weight: Int, color: Color, modifier: Modifier = Modifier, trailing: String = "$weight%") {
    val animated by animateFloatAsState(weight / 100f, tween(700, easing = FastOutSlowInEasing), label = "weight")
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(trailing, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(Modifier.fillMaxWidth(animated.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    PremiumCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally, content = actions)
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f), border = BorderStroke(1.dp, color.copy(alpha = 0.4f))) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}
