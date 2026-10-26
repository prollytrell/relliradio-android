package com.relli.reels.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.relli.reels.R

// Dark cocoa, cream type, metallic gold. Same palette as RelliRadio.
object Relli {
    val bg = Color(0xFF1B1410)
    val bgDeep = Color(0xFF140F0C)
    val surface = Color(0xFF241B16)
    val raise = Color(0xFF30251E)
    val line = Color(0xFF3F3128)
    val cream = Color(0xFFF3E7D3)
    val muted = Color(0xFFBBA892)
    val faint = Color(0xFF86735F)
    val gold = Color(0xFFD4AF6A)
    val goldSoft = Color(0x29D4AF6A)
    /** Text and icons on gold fills. Cream, never dark, so everything stays readable. */
    val onGold = Color(0xFFF3E7D3)
    /** Selected chips, the active tab, primary buttons: a deep bronze-gold that keeps cream text readable. */
    val goldMetal = Brush.linearGradient(
        0f to Color(0xFF7A5C2C), 0.4f to Color(0xFF5C4420), 0.75f to Color(0xFF4A3619), 1f to Color(0xFF6E5228),
    )
}

@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: Int) = Font(
    R.font.manrope, weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
val Manrope = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))

private fun style(size: Int, weight: Int, line: Int = (size * 1.35).toInt(), tracking: Double = 0.0) =
    TextStyle(fontFamily = Manrope, fontSize = size.sp, fontWeight = FontWeight(weight), lineHeight = line.sp, letterSpacing = tracking.sp)

private val type = Typography(
    displaySmall = style(34, 800, 40, -0.8),
    headlineMedium = style(28, 800, 34, -0.6),
    headlineSmall = style(24, 800, 30, -0.4),
    titleLarge = style(20, 800, 26, -0.3),
    titleMedium = style(16, 700, 22),
    titleSmall = style(14, 700, 20),
    bodyLarge = style(16, 500, 23),
    bodyMedium = style(14, 500, 20),
    bodySmall = style(13, 500, 18),
    labelLarge = style(14, 700, 20),
    labelMedium = style(13, 700, 18),
    labelSmall = style(11, 700, 16),
)

@Composable
fun RelliTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Relli.gold,
            onPrimary = Relli.onGold,
            primaryContainer = Relli.goldSoft,
            onPrimaryContainer = Relli.cream,
            secondary = Relli.gold,
            onSecondary = Relli.onGold,
            secondaryContainer = Relli.raise,
            onSecondaryContainer = Relli.cream,
            background = Relli.bg,
            onBackground = Relli.cream,
            surface = Relli.surface,
            onSurface = Relli.cream,
            surfaceVariant = Relli.raise,
            onSurfaceVariant = Relli.muted,
            surfaceContainer = Relli.surface,
            surfaceContainerLow = Relli.surface,
            surfaceContainerHigh = Relli.raise,
            surfaceContainerHighest = Relli.raise,
            outline = Relli.line,
            outlineVariant = Relli.line,
        ),
        typography = type,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
