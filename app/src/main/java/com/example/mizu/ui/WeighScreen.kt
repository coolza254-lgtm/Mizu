package com.example.mizu.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mizu.R
import com.example.mizu.core.Bottle
import com.example.mizu.core.DrinkTimeEstimator
import com.example.mizu.core.WeighCalculator
import com.example.mizu.core.WeighOutcome

@Composable
fun WeighScreen(vm: MizuViewModel, toast: ToastState, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val allBottles by vm.bottles.collectAsStateWithLifecycle()
    val s = settings ?: return
    val bottles = allBottles.filter { it.isActive }
    val haptics = LocalHaptics.current
    val sounds = LocalSounds.current
    val ml = stringResource(R.string.unit_ml)
    val g = stringResource(R.string.unit_g)

    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var weightText by rememberSaveable { mutableStateOf("") }
    var askRefill by remember { mutableStateOf<WeighOutcome.AskRefill?>(null) }

    val bottle = bottles.firstOrNull { it.id == selectedId }
        ?: bottles.maxByOrNull { it.waterUpdatedAt ?: java.time.LocalDateTime.MIN }
    val weight = weightText.toIntOrNull()
    val validWeight = WeighCalculator.isValidWeight(weight)
    val empty = bottle?.let { s.emptyWeightOf(it) }
    val newWater = if (validWeight && weight != null && empty != null) WeighCalculator.waterG(weight, empty) else null
    val outcome = if (bottle != null && validWeight && weight != null) vm.evaluateWeigh(bottle, weight) else null

    val savedBaseline = stringResource(R.string.weigh_saved_baseline)
    val savedDrink = stringResource(R.string.weigh_saved_drink)

    fun finish(result: WeighOutcome) {
        val b = bottle ?: return
        when (result) {
            is WeighOutcome.SetBaseline -> toast.show(String.format(savedBaseline, result.waterG.grouped()))
            is WeighOutcome.AskRefill -> toast.show(String.format(savedBaseline, result.newWaterG.grouped()))
            is WeighOutcome.Drink -> toast.show(String.format(savedDrink, result.amountMl.grouped()))
            WeighOutcome.NoChange, WeighOutcome.Invalid -> return
        }
        haptics.success()
        if (result is WeighOutcome.Drink) sounds.drop() else sounds.fill()
        vm.commitWeigh(b.id, result)
        weightText = ""
    }

    Column(Modifier.fillMaxSize()) {
        MizuTopBar(stringResource(R.string.weigh_title), onBack)
        if (bottle == null) {
            Text(stringResource(R.string.no_bottles), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(24.dp))
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(bottles, key = { it.id }) { b ->
                    BottleChip(b, selected = b.id == bottle.id, ml = ml) {
                        selectedId = b.id
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.total_weight), style = MaterialTheme.typography.labelMedium, color = MizuColors.InkSoft)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            if (weightText.isEmpty()) "0" else (weight ?: 0).grouped(),
                            style = MaterialTheme.typography.displayLarge,
                            color = if (weightText.isEmpty()) MizuColors.InkFaint else MizuColors.Ink,
                        )
                        Text(" $g", style = MaterialTheme.typography.titleMedium, color = MizuColors.InkSoft, modifier = Modifier.padding(bottom = 12.dp))
                    }
                    val rangeError = weightText.isNotEmpty() && !validWeight
                    val info = when {
                        rangeError -> stringResource(R.string.weight_range_error)
                        outcome is WeighOutcome.Drink -> {
                            val estimate = DrinkTimeEstimator.estimate(bottle.waterUpdatedAt, java.time.LocalDateTime.now(), s)
                            val from = estimate.from
                            if (from != null) {
                                stringResource(R.string.preview_drink_estimated, outcome.amountMl.grouped(), from.format(TIME_FORMAT), java.time.LocalDateTime.now().format(TIME_FORMAT))
                            } else {
                                stringResource(R.string.preview_drink, outcome.amountMl.grouped())
                            }
                        }
                        outcome is WeighOutcome.SetBaseline -> stringResource(R.string.preview_baseline)
                        outcome is WeighOutcome.AskRefill -> stringResource(R.string.preview_refill)
                        outcome == WeighOutcome.NoChange -> stringResource(R.string.weigh_no_change)
                        else -> bottle.currentWaterG?.let { stringResource(R.string.last_water, it.grouped()) }
                            ?: stringResource(R.string.no_baseline_yet)
                    }
                    if (newWater != null) {
                        Text(stringResource(R.string.water_in_bottle, newWater.grouped()), style = MaterialTheme.typography.titleMedium, color = MizuColors.Ink)
                    } else {
                        Text(stringResource(R.string.empty_weight_used, (empty ?: 0).grouped()), style = MaterialTheme.typography.bodyMedium, color = MizuColors.InkSoft)
                    }
                    Text(
                        info,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            rangeError -> MizuColors.Danger
                            outcome is WeighOutcome.Drink -> MizuColors.WaterDeep
                            else -> MizuColors.InkSoft
                        },
                    )
                }
                BottleGauge(
                    previousWater = bottle.currentWaterG,
                    newWater = newWater,
                    modifier = Modifier.size(width = 62.dp, height = 128.dp),
                    capacityMl = bottle.capacityMl?.let { maxOf(it, newWater ?: 0) },
                )
            }

            NumberPad(
                onDigit = { d -> weightText = weightText.pushDigit(d, 5) },
                onBackspace = { weightText = weightText.dropLast(1) },
                modifier = Modifier.padding(horizontal = 20.dp),
                keyHeight = 50.dp,
            )
        }

        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SoftButton(
                stringResource(R.string.refilled),
                onClick = { if (newWater != null) finish(WeighOutcome.SetBaseline(newWater)) },
                enabled = newWater != null,
                icon = Icons.Rounded.Autorenew,
                haptic = HapticKind.NONE,
            )
            PrimaryButton(
                stringResource(R.string.save),
                onClick = {
                    when (outcome) {
                        is WeighOutcome.AskRefill -> askRefill = outcome
                        null, WeighOutcome.NoChange, WeighOutcome.Invalid -> haptics.heavy()
                        else -> finish(outcome)
                    }
                },
                enabled = outcome != null && outcome != WeighOutcome.NoChange,
                icon = Icons.Rounded.Check,
                modifier = Modifier.weight(1f),
                haptic = HapticKind.NONE,
            )
        }
    }

    askRefill?.let { refill ->
        ConfirmDialog(
            title = stringResource(R.string.refill_title),
            message = stringResource(R.string.refill_message),
            confirmText = stringResource(R.string.refill_yes),
            dismissText = stringResource(R.string.refill_no),
            onConfirm = { askRefill = null; finish(refill) },
            onDismiss = { askRefill = null },
        )
    }
}

@Composable
private fun BottleChip(bottle: Bottle, selected: Boolean, ml: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .width(150.dp)
            .clip(shape)
            .background(if (selected) MizuColors.Foam else Color.White)
            .border(if (selected) 2.dp else 0.dp, if (selected) MizuColors.WaterDeep else Color.Transparent, shape)
            .bouncyClick(haptic = HapticKind.TICK, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(bottle.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(
            bottle.currentWaterG?.let { w ->
                "≈ ${w.grouped()}" + (bottle.capacityMl?.let { " / ${it.grouped()}" } ?: "") + " $ml"
            } ?: stringResource(R.string.not_weighed),
            style = MaterialTheme.typography.bodyMedium,
            color = MizuColors.InkSoft,
        )
    }
}

/**
 * The user's tumbler as a cartoon: tapered charcoal cup with a flip lid, a see-through body showing the water
 * level (with a gently moving surface) and a small happy face. Scaled to [capacityMl] when known.
 */
@Composable
fun BottleGauge(previousWater: Int?, newWater: Int?, modifier: Modifier = Modifier, capacityMl: Int? = null) {
    val capacity = (capacityMl?.takeIf { it > 0 } ?: maxOf(previousWater ?: 0, newWater ?: 0, 1000)).toFloat()
    val target = ((newWater ?: previousWater ?: 0) / capacity).coerceIn(0f, 1f)
    val level by animateFloatAsState(target, tween(700, easing = FastOutSlowInEasing), label = "cup")
    val prevMark = previousWater?.let { (it / capacity).coerceIn(0f, 1f) }
    val wave = androidx.compose.animation.core.rememberInfiniteTransition(label = "cupWave")
    val phase by wave.animateFloat(
        0f, (2 * Math.PI).toFloat(),
        androidx.compose.animation.core.infiniteRepeatable(tween(2600, easing = androidx.compose.animation.core.LinearEasing)),
        label = "cupPhase",
    )
    val charcoal = Color(0xFF3B4048)
    val glass = Color(0xFF5A616B)

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        val lidTop = h * 0.07f
        val lidBottom = h * 0.19f
        val topW = w * 0.90f
        val botW = w * 0.74f
        val r = w * 0.12f
        val left = (w - topW) / 2f
        val right = left + topW
        val bl = (w - botW) / 2f
        val br = bl + botW

        // body: a tapered cup with rounded bottom corners
        val body = Path().apply {
            moveTo(left, lidBottom)
            lineTo(right, lidBottom)
            lineTo(br, h - r)
            quadraticBezierTo(br, h, br - r, h)
            lineTo(bl + r, h)
            quadraticBezierTo(bl, h, bl, h - r)
            close()
        }
        drawPath(body, glass)
        clipPath(body) {
            val fillTop = h - (h - lidBottom) * level
            val amp = if (level <= 0f || level >= 0.99f) 0f else 2.5.dp.toPx()
            val water = Path().apply {
                moveTo(0f, h)
                lineTo(0f, fillTop)
                var x = 0f
                while (x <= w) {
                    lineTo(x, fillTop + amp * kotlin.math.sin(x / w * 2f * Math.PI.toFloat() * 1.3f + phase))
                    x += 3f
                }
                lineTo(w, h)
                close()
            }
            drawPath(water, MizuColors.Water)
            // highlight stripe
            drawRoundRect(
                Color.White.copy(alpha = 0.28f),
                Offset(left + w * 0.10f, lidBottom + h * 0.06f),
                Size(w * 0.07f, (h - lidBottom) * 0.62f),
                CornerRadius(w * 0.04f, w * 0.04f),
            )
            if (prevMark != null && newWater != null) {
                val y = h - (h - lidBottom) * prevMark
                drawLine(
                    Color.White, Offset(0f, y), Offset(w, y), strokeWidth = 1.5.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                )
            }
        }
        drawPath(body, MizuColors.Ink, style = Stroke(stroke))

        // lid with the flip nub
        val lidW = w * 0.96f
        val lidLeft = (w - lidW) / 2f
        drawRoundRect(charcoal, Offset(lidLeft, lidTop), Size(lidW, lidBottom - lidTop), CornerRadius(w * 0.08f, w * 0.08f))
        drawRoundRect(MizuColors.Ink, Offset(lidLeft, lidTop), Size(lidW, lidBottom - lidTop), CornerRadius(w * 0.08f, w * 0.08f), style = Stroke(stroke))
        val nubW = w * 0.30f
        drawRoundRect(charcoal, Offset(w * 0.40f, 0f), Size(nubW, lidTop + stroke), CornerRadius(w * 0.05f, w * 0.05f))
        drawRoundRect(MizuColors.Ink, Offset(w * 0.40f, 0f), Size(nubW, lidTop + stroke), CornerRadius(w * 0.05f, w * 0.05f), style = Stroke(stroke))
        drawLine(Color.White.copy(alpha = 0.35f), Offset(lidLeft + w * 0.08f, lidTop + (lidBottom - lidTop) * 0.35f), Offset(lidLeft + w * 0.32f, lidTop + (lidBottom - lidTop) * 0.35f), strokeWidth = 1.5.dp.toPx())

        // happy face
        val faceY = lidBottom + (h - lidBottom) * 0.30f
        val eyeDx = w * 0.13f
        val eyeR = w * 0.045f
        listOf(w / 2f - eyeDx, w / 2f + eyeDx).forEach { ex ->
            drawCircle(MizuColors.Ink, eyeR, Offset(ex, faceY))
            drawCircle(Color.White, eyeR * 0.38f, Offset(ex + eyeR * 0.3f, faceY - eyeR * 0.3f))
        }
        drawArc(
            MizuColors.Ink, startAngle = 20f, sweepAngle = 140f, useCenter = false,
            topLeft = Offset(w / 2f - w * 0.09f, faceY - w * 0.02f), size = Size(w * 0.18f, w * 0.14f),
            style = Stroke(1.6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
        drawCircle(Color(0xFFF4A6A6).copy(alpha = 0.8f), w * 0.045f, Offset(w / 2f - eyeDx * 1.55f, faceY + w * 0.07f))
        drawCircle(Color(0xFFF4A6A6).copy(alpha = 0.8f), w * 0.045f, Offset(w / 2f + eyeDx * 1.55f, faceY + w * 0.07f))
    }
}

