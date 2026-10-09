package com.example.mizu.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import com.example.mizu.R
import com.example.mizu.core.AppFont
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

// Type families bundled in res/font (SIL Open Font License, see third_party/fonts). Japanese text falls back
// to the system Noto CJK font. EDITORIAL keeps the system serif/sans pairing.
private val Plex = FontFamily(Font(R.font.plex_regular, FontWeight.Normal), Font(R.font.plex_semibold, FontWeight.SemiBold), Font(R.font.plex_semibold, FontWeight.Bold))
private val Prompt = FontFamily(Font(R.font.prompt_regular, FontWeight.Normal), Font(R.font.prompt_semibold, FontWeight.SemiBold), Font(R.font.prompt_semibold, FontWeight.Bold))
private val Sarabun = FontFamily(Font(R.font.sarabun_regular, FontWeight.Normal), Font(R.font.sarabun_bold, FontWeight.SemiBold), Font(R.font.sarabun_bold, FontWeight.Bold))
private val Mali = FontFamily(Font(R.font.mali_regular, FontWeight.Normal), Font(R.font.mali_semibold, FontWeight.SemiBold), Font(R.font.mali_semibold, FontWeight.Bold))
private const val TABULAR = "tnum"

/** Display (numbers, headings) and body families for each choice. */
fun AppFont.families(): Pair<FontFamily, FontFamily> = when (this) {
    AppFont.EDITORIAL -> FontFamily.Serif to FontFamily.SansSerif
    AppFont.PLEX -> Plex to Plex
    AppFont.PROMPT -> Prompt to Prompt
    AppFont.SARABUN -> Sarabun to Sarabun
    AppFont.MALI -> Mali to Mali
}

/** Small monospace meta label ("mizu / today"). */
val MonoLabel = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp)

private fun typography(font: AppFont): Typography {
    val (display, body) = font.families()
    val displayWeight = if (font == AppFont.EDITORIAL) FontWeight.Normal else FontWeight.SemiBold
    return Typography(
        displayLarge = TextStyle(fontFamily = display, fontWeight = displayWeight, fontSize = 56.sp, lineHeight = 64.sp, letterSpacing = (-1).sp, fontFeatureSettings = TABULAR),
        displayMedium = TextStyle(fontFamily = display, fontWeight = displayWeight, fontSize = 42.sp, lineHeight = 52.sp, letterSpacing = (-0.6).sp, fontFeatureSettings = TABULAR),
        headlineMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 40.sp, letterSpacing = (-0.4).sp),
        headlineSmall = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp, fontFeatureSettings = TABULAR),
        titleLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp, fontFeatureSettings = TABULAR),
        titleMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, fontFeatureSettings = TABULAR),
        bodyLarge = TextStyle(fontFamily = body, fontSize = 16.sp, lineHeight = 25.sp),
        bodyMedium = TextStyle(fontFamily = body, fontSize = 14.sp, lineHeight = 21.sp),
        labelLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.4.sp),
        labelSmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp),
    )
}

@Composable
fun MizuTheme(font: AppFont = AppFont.EDITORIAL, content: @Composable () -> Unit) {
    val type = remember(font) { typography(font) }
    MaterialTheme(colorScheme = Scheme, shapes = Shape, typography = type, content = content)
}
