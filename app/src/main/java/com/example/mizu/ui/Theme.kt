package com.example.mizu.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Mizu palette, editorial style: warm paper, near-black ink outlines, flat pastel water blues.
 * (Names are kept from the first theme so every screen picks up the new look.)
 */
object MizuColors {
    val Paper = Color(0xFFF5F4EF)
    val Dot = Color(0xFFD6D3CA)
    val Water = Color(0xFF8CC4EA)
    val WaterDeep = Color(0xFF2F6FA8)
    val Aqua = Color(0xFFBFDDF3)
    val Highlight = Color(0xFFBFDDF3)
    val Ink = Color(0xFF1C2430)
    val InkSoft = Color(0xFF5B6472)
    val InkFaint = Color(0xFF9AA0A8)
    val Mist = Color(0xFFFFFFFF)
    val Foam = Color(0xFFDCEBF7)
    val Line = Color(0xFFD8D5CD)
    val Danger = Color(0xFFC2504A)
    val White = Color.White

    val ButtonGradient = Brush.linearGradient(listOf(Water, Water))
    val WaterGradient = Brush.verticalGradient(listOf(Aqua, Water))
    val BackdropGradient = Brush.verticalGradient(listOf(Paper, Paper))
}

/** Outline weight used on every card, button and chart. */
val InkStroke = 1.5.dp

private val Scheme = lightColorScheme(
    primary = MizuColors.Water,
    onPrimary = MizuColors.Ink,
    primaryContainer = MizuColors.Foam,
    onPrimaryContainer = MizuColors.Ink,
    secondary = MizuColors.WaterDeep,
    onSecondary = Color.White,
    secondaryContainer = MizuColors.Foam,
    onSecondaryContainer = MizuColors.Ink,
    background = MizuColors.Paper,
    onBackground = MizuColors.Ink,
    surface = Color.White,
    onSurface = MizuColors.Ink,
    surfaceVariant = MizuColors.Mist,
    onSurfaceVariant = MizuColors.InkSoft,
    surfaceContainerHigh = Color.White,
    surfaceContainerLow = MizuColors.Mist,
    outline = MizuColors.Line,
    outlineVariant = MizuColors.Line,
    error = MizuColors.Danger,
)

private val Shape = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

// System fonts are the Noto family. Display text is serif (Noto Serif; Thai/Japanese fall back to the
// matching Noto face), body text sans, and small meta labels monospace, like an editorial layout.
private val Sans = FontFamily.SansSerif
private val Serif = FontFamily.Serif
private const val TABULAR = "tnum"

/** Small monospace meta label ("mizu / today"). */
val MonoLabel = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp)

private val Type = Typography(
    displayLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 56.sp, lineHeight = 62.sp, letterSpacing = (-1).sp, fontFeatureSettings = TABULAR),
    displayMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 42.sp, lineHeight = 50.sp, letterSpacing = (-0.6).sp, fontFeatureSettings = TABULAR),
    headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp, fontFeatureSettings = TABULAR),
    titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp, fontFeatureSettings = TABULAR),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, fontFeatureSettings = TABULAR),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp),
)

@Composable
fun MizuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, shapes = Shape, typography = Type, content = content)
}
