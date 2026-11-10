package com.avatarsdev.tiktokupgrade.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.avatarsdev.tiktokupgrade.data.ThemeMode

object Brand {
    val Cyan = Color(0xFF25F4EE)
    val Pink = Color(0xFFFE2C55)
    val Amber = Color(0xFFFFB547)
    val Green = Color(0xFF3DDC97)
    val accent = Brush.linearGradient(listOf(Cyan, Pink))
    val accentH = Brush.horizontalGradient(listOf(Cyan, Pink))

    /** Distinct series colours for category bars. */
    val series = listOf(Cyan, Pink, Color(0xFF8B7CFF), Amber, Green, Color(0xFF4FC3F7), Color(0xFFFF8A65), Color(0xFFE57CFF))
}

private val Dark = darkColorScheme(
    primary = Brand.Cyan,
    onPrimary = Color(0xFF00201F),
    secondary = Brand.Pink,
    onSecondary = Color.White,
    tertiary = Brand.Amber,
    background = Color(0xFF0A0A0F),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF13131B),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF1C1C27),
    onSurfaceVariant = Color(0xFFA3A3B5),
    surfaceContainer = Color(0xFF15151E),
    surfaceContainerHigh = Color(0xFF1C1C27),
    surfaceContainerHighest = Color(0xFF242432),
    outline = Color(0xFF34344A),
    outlineVariant = Color(0xFF26263A),
    error = Color(0xFFFF6B81),
)

private val Light = lightColorScheme(
    primary = Color(0xFF00897F),
    onPrimary = Color.White,
    secondary = Color(0xFFE0103F),
    onSecondary = Color.White,
    tertiary = Color(0xFFB26B00),
    background = Color(0xFFF7F7FA),
    onBackground = Color(0xFF111118),
    surface = Color.White,
    onSurface = Color(0xFF111118),
    surfaceVariant = Color(0xFFEDEDF3),
    onSurfaceVariant = Color(0xFF5A5A6E),
    surfaceContainer = Color(0xFFF1F1F6),
    surfaceContainerHigh = Color(0xFFEAEAF1),
    surfaceContainerHighest = Color(0xFFE2E2EB),
    outline = Color(0xFFCACAD8),
    outlineVariant = Color(0xFFDDDDE8),
)

private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

val NumberStyle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
}
