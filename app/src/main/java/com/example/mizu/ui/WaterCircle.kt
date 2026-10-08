package com.example.mizu.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The home hero: a thin gradient progress ring around a glass of moving water with rising bubbles.
 * The number counts up when it changes, and the first time the goal is reached a ring of droplets bursts out.
 */
@Composable
fun HeroRing(
    progress: Float,
    consumedMl: Int,
    goalMl: Int,
    unit: String,
    modifier: Modifier = Modifier,
    size: Dp = 288.dp,
) {
    val haptics = LocalHaptics.current
    val level by animateFloatAsState(progress.coerceIn(0f, 1f), tween(1100, easing = FastOutSlowInEasing), label = "level")
    val shownMl by animateIntAsState(consumedMl, tween(900, easing = FastOutSlowInEasing), label = "count")

    val wave = rememberInfiniteTransition(label = "wave")
    val phase by wave.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val rise by wave.animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "bubbles")

    // Burst once when the goal is crossed while the screen is visible.
    val reached = goalMl in 1..consumedMl
    var wasReached by remember { mutableStateOf(reached) }
    val burst = remember { Animatable(1f) }
    LaunchedEffect(reached) {
        if (reached && !wasReached) {
            haptics.success()
            burst.snapTo(0f)
            burst.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
        }
        wasReached = reached
    }

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 12.dp.toPx()
            val outer = this.size.minDimension / 2f
            val ringRadius = outer - stroke / 2f - 6.dp.toPx()
            val inner = ringRadius - stroke / 2f - 14.dp.toPx()
            val c = center

            // soft halo
            drawCircle(Brush.radialGradient(listOf(MizuColors.Water.copy(alpha = 0.16f), Color.Transparent), c, outer), outer, c)

            // track + progress arc (rotated so the gradient seam sits at 12 o'clock)
            drawCircle(MizuColors.Line, ringRadius, c, style = Stroke(stroke))
            if (level > 0f) {
                rotate(-90f, c) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(MizuColors.Aqua, MizuColors.Water, MizuColors.WaterDeep), c),
                        startAngle = 0f,
                        sweepAngle = 360f * level,
                        useCenter = false,
                        topLeft = Offset(c.x - ringRadius, c.y - ringRadius),
                        size = Size(ringRadius * 2, ringRadius * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                // cover the rounded start cap so the sweep-gradient seam never shows
                drawCircle(MizuColors.Aqua, stroke / 2f, Offset(c.x, c.y - ringRadius))
                // knob at the end of the arc
                val a = (-90f + 360f * level) * (PI / 180f).toFloat()
                val knob = Offset(c.x + ringRadius * cos(a), c.y + ringRadius * sin(a))
                drawCircle(MizuColors.WaterDeep.copy(alpha = 0.18f), stroke * 0.95f, knob)
                drawCircle(Color.White, stroke * 0.42f, knob)
            }

            // glass of water
            val glass = Path().apply { addOval(Rect(c, inner)) }
            drawCircle(MizuColors.Mist, inner, c)
            clipPath(glass) {
                val top = c.y + inner - (2 * inner) * level
                val amp = if (level <= 0f || level >= 1f) 0f else 7.dp.toPx()
                wave(top - 4.dp.toPx(), amp, phase + 1.9f, MizuColors.Aqua.copy(alpha = 0.55f), Brush.verticalGradient(listOf(MizuColors.Aqua.copy(alpha = 0.6f), MizuColors.Water.copy(alpha = 0.5f))))
                wave(top, amp, phase, MizuColors.Water, Brush.verticalGradient(listOf(MizuColors.Water.copy(alpha = 0.85f), MizuColors.WaterDeep), startY = top, endY = c.y + inner))
                if (level > 0.04f) bubbles(c, inner, top, rise)
                // glass highlight
                drawCircle(Color.White.copy(alpha = 0.35f), inner * 0.16f, Offset(c.x - inner * 0.48f, c.y - inner * 0.46f))
            }
            drawCircle(Color.White.copy(alpha = 0.9f), inner, c, style = Stroke(2.dp.toPx()))

            // goal burst: droplets flying outward and fading
            if (burst.value < 1f) {
                val t = burst.value
                for (i in 0 until 14) {
                    val ang = (i / 14f) * 2f * PI.toFloat()
                    val dist = ringRadius * (0.55f + 0.75f * t)
                    val p = Offset(c.x + dist * cos(ang), c.y + dist * sin(ang))
                    drawCircle(MizuColors.Water.copy(alpha = (1f - t) * 0.9f), (5.dp.toPx()) * (1f - t * 0.5f), p)
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val onWater = level > 0.72f
            Text(
                shownMl.grouped(),
                style = MaterialTheme.typography.displayLarge,
                color = if (onWater) Color.White else MizuColors.Ink,
            )
            Text(
                "/ ${goalMl.grouped()} $unit",
                style = MaterialTheme.typography.bodyLarge,
                color = if (onWater) Color.White.copy(alpha = 0.9f) else MizuColors.InkSoft,
            )
            Pill(
                "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                Modifier.padding(top = 10.dp),
                background = if (onWater) Color.White.copy(alpha = 0.22f) else MizuColors.Foam,
                color = if (onWater) Color.White else MizuColors.WaterDeep,
            )
        }
    }
}

private fun DrawScope.wave(top: Float, amplitude: Float, phase: Float, @Suppress("UNUSED_PARAMETER") edge: Color, fill: Brush) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(0f, h)
        lineTo(0f, top)
        var x = 0f
        while (x <= w) {
            lineTo(x, top + amplitude * sin(x / w * 2f * PI.toFloat() * 1.4f + phase))
            x += 6f
        }
        lineTo(w, top)
        lineTo(w, h)
        close()
    }
    drawPath(path, fill)
}

private fun DrawScope.bubbles(c: Offset, inner: Float, waterTop: Float, rise: Float) {
    val bottom = c.y + inner
    val span = bottom - waterTop
    if (span <= 0f) return
    for (i in 0 until 7) {
        val seed = i * 0.137f
        val t = (rise + seed * 3.1f) % 1f
        val x = c.x + inner * (-0.6f + 1.2f * ((i * 0.618f) % 1f)) + 6.dp.toPx() * sin((t * 6f + i).toDouble()).toFloat()
        val y = bottom - span * t
        val r = (2.5f + (i % 3) * 1.5f).dp.toPx() * (0.6f + 0.4f * t)
        drawCircle(Color.White.copy(alpha = 0.45f * (1f - t)), r, Offset(x, y))
    }
}

/** Small ring used in history rows and the day view. */
@Composable
fun MiniRing(progress: Float, modifier: Modifier = Modifier, size: Dp = 40.dp, stroke: Dp = 5.dp) {
    Canvas(modifier.size(size)) {
        val s = stroke.toPx()
        val r = this.size.minDimension / 2f - s / 2f
        drawCircle(MizuColors.Line, r, style = Stroke(s))
        if (progress > 0f) {
            drawArc(
                brush = Brush.linearGradient(listOf(MizuColors.Aqua, MizuColors.WaterDeep)),
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(center.x - r, center.y - r),
                size = Size(r * 2, r * 2),
                style = Stroke(s, cap = StrokeCap.Round),
            )
        }
    }
}
