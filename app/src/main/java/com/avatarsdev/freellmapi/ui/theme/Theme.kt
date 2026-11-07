package com.avatarsdev.freellmapi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.avatarsdev.freellmapi.data.ThemeMode

// Brand palette: deep ink background, electric violet primary, aqua secondary.
private val Violet = Color(0xFF8B7CFF)
private val VioletDeep = Color(0xFF5B4BDB)
private val Aqua = Color(0xFF2DD4BF)
private val Pink = Color(0xFFF472B6)

private val DarkScheme = darkColorScheme(
    primary = Violet,
    onPrimary = Color(0xFF120D33),
    primaryContainer = Color(0xFF2A2357),
    onPrimaryContainer = Color(0xFFE0DBFF),
    secondary = Aqua,
    onSecondary = Color(0xFF032420),
    secondaryContainer = Color(0xFF0E3B36),
    onSecondaryContainer = Color(0xFFB6F5EA),
    tertiary = Pink,
    onTertiary = Color(0xFF3B0A22),
    background = Color(0xFF080B12),
    onBackground = Color(0xFFE6E9F2),
    surface = Color(0xFF0D121C),
    onSurface = Color(0xFFE6E9F2),
    surfaceVariant = Color(0xFF1A2232),
    onSurfaceVariant = Color(0xFF9AA4B8),
    surfaceContainerLowest = Color(0xFF070A10),
    surfaceContainerLow = Color(0xFF0F1520),
    surfaceContainer = Color(0xFF131A27),
    surfaceContainerHigh = Color(0xFF19212F),
    surfaceContainerHighest = Color(0xFF202A3B),
    outline = Color(0xFF2F3A4F),
    outlineVariant = Color(0xFF1E2738),
    error = Color(0xFFFF6B7A),
    onError = Color(0xFF3B0710),
    errorContainer = Color(0xFF4A1018),
    onErrorContainer = Color(0xFFFFD9DD),
)

private val LightScheme = lightColorScheme(
    primary = VioletDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E1FF),
    onPrimaryContainer = Color(0xFF1C1260),
    secondary = Color(0xFF0F8F80),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDF5EE),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFFC0367E),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEBEEF5),
    onSurfaceVariant = Color(0xFF566079),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F9FC),
    surfaceContainer = Color(0xFFF1F3F8),
    surfaceContainerHigh = Color(0xFFEBEEF4),
    surfaceContainerHighest = Color(0xFFE4E8F0),
    outline = Color(0xFFCBD2E0),
    outlineVariant = Color(0xFFE2E6EE),
    error = Color(0xFFD32F45),
)

/** Status colours that Material's scheme does not cover. */
@Immutable
data class StatusColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val heroStart: Color,
    val heroEnd: Color,
)

private val DarkStatus = StatusColors(
    success = Color(0xFF34D399),
    warning = Color(0xFFFBBF24),
    danger = Color(0xFFFF6B7A),
    info = Color(0xFF60A5FA),
    heroStart = Color(0xFF6D5CFF),
    heroEnd = Color(0xFF14B8A6),
)

private val LightStatus = StatusColors(
    success = Color(0xFF059669),
    warning = Color(0xFFB45309),
    danger = Color(0xFFD32F45),
    info = Color(0xFF2563EB),
    heroStart = Color(0xFF5B4BDB),
    heroEnd = Color(0xFF0D9488),
)

val LocalStatusColors = staticCompositionLocalOf { DarkStatus }

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)

@Composable
fun FreeLlmTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val scheme: ColorScheme = if (dark) DarkScheme else LightScheme
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}

object AppColors {
    val status: StatusColors
        @Composable get() = LocalStatusColors.current
}
