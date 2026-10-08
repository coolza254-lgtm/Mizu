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

/** Mizu palette: white space, soft water blues, deep ink for text. */
object MizuColors {
    val Water = Color(0xFF5BB8F0)
    val WaterDeep = Color(0xFF2479C7)
    val Aqua = Color(0xFF9BDBFA)
    val Ink = Color(0xFF0A2A5E)
    val InkSoft = Color(0xFF5A6F8F)
    val InkFaint = Color(0xFF9AAAC0)
    val Mist = Color(0xFFF4F9FD)
    val Foam = Color(0xFFE6F4FD)
    val Line = Color(0xFFE3EEF7)
    val Danger = Color(0xFFD64545)
    val White = Color.White

    val ButtonGradient = Brush.horizontalGradient(listOf(Color(0xFF4AAEEB), WaterDeep))
    val WaterGradient = Brush.verticalGradient(listOf(Aqua, Water, WaterDeep))
    val BackdropGradient = Brush.verticalGradient(listOf(Foam, Color.White), endY = 1400f)
}

private val Scheme = lightColorScheme(
    primary = MizuColors.Water,
    onPrimary = MizuColors.Ink,
    primaryContainer = MizuColors.Foam,
    onPrimaryContainer = MizuColors.Ink,
    secondary = MizuColors.WaterDeep,
    onSecondary = Color.White,
    secondaryContainer = MizuColors.Foam,
    onSecondaryContainer = MizuColors.Ink,
    background = Color.White,
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

// System sans-serif on Android is the Noto family (Noto Sans, Noto Sans Thai, Noto Sans CJK JP).
private val Sans = FontFamily.SansSerif
private const val TABULAR = "tnum"

private val Type = Typography(
    displayLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Light, fontSize = 58.sp, lineHeight = 64.sp, letterSpacing = (-1.5).sp, fontFeatureSettings = TABULAR),
    displayMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Light, fontSize = 44.sp, lineHeight = 52.sp, letterSpacing = (-1).sp, fontFeatureSettings = TABULAR),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, fontFeatureSettings = TABULAR),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp, fontFeatureSettings = TABULAR),
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
