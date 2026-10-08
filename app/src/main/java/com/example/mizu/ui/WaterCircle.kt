package com.example.mizu.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** Circular progress filled with a gently moving water wave. [progress] is 0..1. */
@Composable
fun WaterCircle(
    progress: Float,
    primaryText: String,
    secondaryText: String,
    modifier: Modifier = Modifier,
    size: Dp = 260.dp,
) {
    val level by animateFloatAsState(progress.coerceIn(0f, 1f), animationSpec = tween(900), label = "level")
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val back = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val front = MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)
    val base = MaterialTheme.colorScheme.surfaceVariant
    val ring = MaterialTheme.colorScheme.primary

    Box(modifier.size(size).wrapContentSize(Alignment.Center), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val radius = this.size.minDimension / 2f
            val circle = Path().apply { addOval(Rect(Offset(this@Canvas.size.width / 2f - radius, this@Canvas.size.height / 2f - radius), Size(radius * 2, radius * 2))) }
            drawCircle(base, radius)
            clipPath(circle) {
                val top = this.size.height * (1f - level)
                val amplitude = if (level <= 0f || level >= 1f) 0f else 9.dp.toPx()
                drawWave(top, amplitude, phase + 1.6f, back)
                drawWave(top + 6.dp.toPx(), amplitude, phase, front)
            }
            drawCircle(ring, radius - 2.dp.toPx(), style = Stroke(width = 4.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(primaryText, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            Text(secondaryText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWave(top: Float, amplitude: Float, phase: Float, color: Color) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(0f, h)
        lineTo(0f, top)
        var x = 0f
        while (x <= w) {
            lineTo(x, top + amplitude * sin(x / w * 2f * PI.toFloat() * 1.5f + phase))
            x += 4f
        }
        lineTo(w, top)
        lineTo(w, h)
        close()
    }
    drawPath(path, color)
}
