package com.g2012.wirelessprint

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object StudioColors {
    val BgDeep = Color(0xFF070B14)
    val SurfaceCard = Color(0xFF111827)
    val SurfaceElevated = Color(0xFF1A2234)
    val BorderSubtle = Color(0xFF243047)
    val CyanGlow = Color(0xFF06B6D4)
    val IndigoGlow = Color(0xFF6366F1)
    val VioletGlow = Color(0xFF8B5CF6)
    val EmeraldGlow = Color(0xFF10B981)
    val AmberGlow = Color(0xFFF59E0B)
    val RoseGlow = Color(0xFFF43F5E)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val DeskBg = Color(0xFF0B1120)
}

private val StudioDarkScheme = darkColorScheme(
    primary = StudioColors.CyanGlow,
    secondary = StudioColors.IndigoGlow,
    tertiary = StudioColors.EmeraldGlow,
    background = StudioColors.BgDeep,
    surface = StudioColors.SurfaceCard,
    surfaceVariant = StudioColors.SurfaceElevated,
    onPrimary = Color(0xFF042F2E),
    onBackground = StudioColors.TextPrimary,
    onSurface = StudioColors.TextPrimary,
    onSurfaceVariant = StudioColors.TextSecondary,
    outline = StudioColors.BorderSubtle
)

@Composable
fun G2012StudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StudioDarkScheme,
        content = content
    )
}
