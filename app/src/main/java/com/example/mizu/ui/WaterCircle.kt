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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

            // track + progress arc (rotated so the gradient seam sits at 12 o'clock)
            drawCircle(MizuColors.Line, ringRadius, c, style = Stroke(stroke))
            if (level > 0f) {
                rotate(-90f, c) {
                    drawArc(
                        color = MizuColors.WaterDeep,
                        startAngle = 0f,
                        sweepAngle = 360f * level,
                        useCenter = false,
                        topLeft = Offset(c.x - ringRadius, c.y - ringRadius),
                        size = Size(ringRadius * 2, ringRadius * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                // knob at the end of the arc
                val a = (-90f + 360f * level) * (PI / 180f).toFloat()
                val knob = Offset(c.x + ringRadius * cos(a), c.y + ringRadius * sin(a))
                drawCircle(Color.White, stroke * 0.8f, knob)
                drawCircle(MizuColors.Ink, stroke * 0.8f, knob, style = Stroke(InkStroke.toPx()))
            }

            // glass of water
            val glass = Path().apply { addOval(Rect(c, inner)) }
            drawCircle(Color.White, inner, c)
            clipPath(glass) {
                val top = c.y + inner - (2 * inner) * level
                val amp = if (level <= 0f || level >= 1f) 0f else 7.dp.toPx()
                wave(top - 4.dp.toPx(), amp, phase + 1.9f, MizuColors.Aqua, Brush.linearGradient(listOf(MizuColors.Aqua, MizuColors.Aqua)))
                wave(top, amp, phase, MizuColors.Water, Brush.linearGradient(listOf(MizuColors.Water, MizuColors.Water)))
                if (level > 0.04f) bubbles(c, inner, top, rise)
            }
            drawCircle(MizuColors.Ink, inner, c, style = Stroke(2.dp.toPx()))

            // goal burst: droplets flying outward and fading
            if (burst.value < 1f) {
                val t = burst.value
                for (i in 0 until 14) {
                    val ang = (i / 14f) * 2f * PI.toFloat()
                    val dist = ringRadius * (0.55f + 0.75f * t)
                    val p = Offset(c.x + dist * cos(ang), c.y + dist * sin(ang))
                    drawCircle(MizuColors.Water.copy(alpha = 1f - t), (6.dp.toPx()) * (1f - t * 0.5f), p)
                    drawCircle(MizuColors.Ink.copy(alpha = 1f - t), (6.dp.toPx()) * (1f - t * 0.5f), p, style = Stroke(1.dp.toPx()))
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val onWater = false // flat light water: ink text reads on both paper and water
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
                color = MizuColors.WaterDeep,
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

/**
 * Today as a cumulative chart over the reminder window: the dashed line is the even pace to the goal, the blue
 * steps are what was actually drunk, and at "now" a short bar shows the gap (green ahead, orange behind).
 */
@Composable
fun PaceChart(
    logs: List<com.example.mizu.core.DrinkLog>,
    start: java.time.LocalTime,
    end: java.time.LocalTime,
    now: java.time.LocalDateTime,
    goalMl: Int,
    goalLabel: String,
    modifier: Modifier = Modifier,
) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MizuColors.InkFaint)
    val startMin = start.toSecondOfDay() / 60f
    val endMin = end.toSecondOfDay() / 60f
    val today = now.toLocalDate()
    val drinks = logs.filter { it.timestamp.toLocalDate() == today }.sortedBy { it.timestamp }
    Canvas(modifier) {
        if (endMin <= startMin || goalMl <= 0) return@Canvas
        val left = 6.dp.toPx()
        val right = size.width - 6.dp.toPx()
        val top = 18.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        val total = drinks.sumOf { it.amountMl }
        val maxY = maxOf(goalMl, total) * 1.06f
        fun x(minute: Float) = left + (right - left) * ((minute - startMin) / (endMin - startMin)).coerceIn(0f, 1f)
        fun y(ml: Float) = bottom - (bottom - top) * (ml / maxY)
        fun minuteOf(t: java.time.LocalDateTime) = (t.toLocalTime().toSecondOfDay() / 60f).coerceIn(startMin, endMin)
        val nowMin = minuteOf(now)

        // goal line + label, baseline, hour labels
        val goalY = y(goalMl.toFloat())
        drawLine(MizuColors.Line, Offset(left, goalY), Offset(right, goalY), strokeWidth = 1.dp.toPx())
        val goalText = measurer.measure(goalLabel, labelStyle)
        drawText(goalText, topLeft = Offset(left, goalY - goalText.size.height - 2.dp.toPx()))
        drawLine(MizuColors.Line, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        var h = kotlin.math.ceil(startMin / 60f).toInt()
        while (h * 60 <= endMin) {
            if (h % 2 == 0) {
                val hx = x(h * 60f)
                drawLine(MizuColors.Line, Offset(hx, bottom), Offset(hx, bottom + 4.dp.toPx()), strokeWidth = 1.dp.toPx())
                val label = measurer.measure("%02d".format(h), labelStyle)
                drawText(label, topLeft = Offset((hx - label.size.width / 2f).coerceIn(0f, size.width - label.size.width), bottom + 5.dp.toPx()))
            }
            h++
        }

        // even pace to the goal
        drawLine(
            MizuColors.InkFaint,
            Offset(x(startMin), y(0f)),
            Offset(x(endMin), goalY),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        )

        // what was actually drunk, as steps up to now
        val steps = ArrayList<Offset>()
        var acc = 0f
        steps += Offset(x(startMin), y(0f))
        drinks.forEach { d ->
            val dx = x(minuteOf(d.timestamp))
            steps += Offset(dx, y(acc))
            acc += d.amountMl
            steps += Offset(dx, y(acc))
        }
        steps += Offset(x(nowMin), y(acc))
        val line = Path().apply {
            moveTo(steps[0].x, steps[0].y)
            steps.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val area = Path().apply {
            moveTo(steps[0].x, steps[0].y)
            steps.drop(1).forEach { lineTo(it.x, it.y) }
            lineTo(x(nowMin), y(0f))
            close()
        }
        drawPath(area, MizuColors.Water.copy(alpha = 0.35f))
        drawPath(line, MizuColors.WaterDeep, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))

        // now: actual vs expected
        val nowLocal = now.toLocalTime().toSecondOfDay() / 60f
        if (nowLocal in startMin..endMin) {
            val expected = goalMl * (nowMin - startMin) / (endMin - startMin)
            val nx = x(nowMin)
            val gapColor = if (total >= expected) MizuColors.Good else MizuColors.Warn
            drawLine(gapColor, Offset(nx, y(total.toFloat())), Offset(nx, y(expected)), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(Color.White, 4.dp.toPx(), Offset(nx, y(expected)))
            drawCircle(MizuColors.InkFaint, 4.dp.toPx(), Offset(nx, y(expected)), style = Stroke(1.5.dp.toPx()))
            drawCircle(Color.White, 6.dp.toPx(), Offset(nx, y(total.toFloat())))
            drawCircle(MizuColors.WaterDeep, 4.5.dp.toPx(), Offset(nx, y(total.toFloat())))
        }
    }
}

/** Legend for [PaceChart]: a blue swatch for drunk, a dashed line for the pace. */
@Composable
fun PaceLegend(actual: String, pace: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 14.dp, height = 10.dp)) {
            drawRoundRect(MizuColors.Water.copy(alpha = 0.5f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            drawLine(MizuColors.WaterDeep, Offset(0f, 1.dp.toPx()), Offset(size.width, 1.dp.toPx()), strokeWidth = 2.dp.toPx())
        }
        Text(actual, style = MaterialTheme.typography.labelSmall, color = MizuColors.InkSoft, modifier = Modifier.padding(start = 5.dp, end = 12.dp))
        Canvas(Modifier.size(width = 16.dp, height = 10.dp)) {
            drawLine(
                MizuColors.InkFaint,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
            )
        }
        Text(pace, style = MaterialTheme.typography.labelSmall, color = MizuColors.InkSoft, modifier = Modifier.padding(start = 5.dp))
    }
}
