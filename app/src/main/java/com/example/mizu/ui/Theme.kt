package com.example.mizu.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object MizuColors {
    val Water = Color(0xFF5BB8F0)
    val DeepBlue = Color(0xFF0A2A5E)
    val Blue = Color(0xFF1565C0)
    val Mist = Color(0xFFF2F8FD)
    val Foam = Color(0xFFE3F3FD)
    val Outline = Color(0xFFCFE6F7)
    val Warn = Color(0xFFB3261E)
}

private val Scheme = lightColorScheme(
    primary = MizuColors.Water,
    onPrimary = MizuColors.DeepBlue,
    primaryContainer = MizuColors.Foam,
    onPrimaryContainer = MizuColors.DeepBlue,
    secondary = MizuColors.Blue,
    onSecondary = Color.White,
    secondaryContainer = MizuColors.Foam,
    onSecondaryContainer = MizuColors.DeepBlue,
    background = Color.White,
    onBackground = MizuColors.DeepBlue,
    surface = Color.White,
    onSurface = MizuColors.DeepBlue,
    surfaceVariant = MizuColors.Mist,
    onSurfaceVariant = Color(0xFF3F5B80),
    outline = MizuColors.Outline,
    outlineVariant = MizuColors.Outline,
    error = MizuColors.Warn,
)

private val Shape = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

// Thai, Japanese and Latin text use the system sans-serif, which on Android is Noto Sans / Noto Sans Thai /
// Noto Sans CJK JP, so each language already renders in the Noto family without bundling the font files.
private val Type = Typography(
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 48.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 23.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
)

@Composable
fun MizuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, shapes = Shape, typography = Type, content = content)
}
