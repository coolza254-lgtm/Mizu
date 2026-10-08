package com.example.mizu.ui

import androidx.compose.animation.core.FastOutSlowInEasing
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
import com.example.mizu.core.WeighCalculator
import com.example.mizu.core.WeighOutcome

@Composable
fun WeighScreen(vm: MizuViewModel, toast: ToastState, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val allBottles by vm.bottles.collectAsStateWithLifecycle()
    val s = settings ?: return
    val bottles = allBottles.filter { it.isActive }
    val haptics = LocalHaptics.current
    val ml = stringResource(R.string.unit_ml)
    val g = stringResource(R.string.unit_g)

    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var weightText by rememberSaveable { mutableStateOf("") }
    var askRefill by remember { mutableStateOf<WeighOutcome.AskRefill?>(null) }

    val bottle = bottles.firstOrNull { it.id == selectedId } ?: bottles.firstOrNull()
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
                        outcome is WeighOutcome.Drink -> stringResource(R.string.preview_drink, outcome.amountMl.grouped())
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
                    modifier = Modifier.size(width = 70.dp, height = 116.dp),
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
            .border(if (selected) 1.5.dp else 1.dp, if (selected) MizuColors.Water else MizuColors.Line, shape)
            .bouncyClick(haptic = HapticKind.TICK, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(bottle.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(
            bottle.currentWaterG?.let { "≈ ${it.grouped()} $ml" } ?: stringResource(R.string.not_weighed),
            style = MaterialTheme.typography.bodyMedium,
            color = MizuColors.InkSoft,
        )
    }
}

/** A little bottle that fills to the new water level (or the last known one). */
@Composable
private fun BottleGauge(previousWater: Int?, newWater: Int?, modifier: Modifier = Modifier) {
    val capacity = maxOf(previousWater ?: 0, newWater ?: 0, 1000).toFloat()
    val target = ((newWater ?: previousWater ?: 0) / capacity).coerceIn(0f, 1f)
    val level by animateFloatAsState(target, tween(600, easing = FastOutSlowInEasing), label = "bottle")
    val prevMark = previousWater?.let { (it / capacity).coerceIn(0f, 1f) }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val neckW = w * 0.42f
        val neckH = h * 0.12f
        val body = RoundRect(0f, neckH, w, h, CornerRadius(w * 0.28f, w * 0.28f))
        val bodyPath = Path().apply { addRoundRect(body) }
        // cap
        drawRoundRect(MizuColors.WaterDeep, Offset((w - neckW) / 2f, 0f), Size(neckW, neckH * 0.9f), CornerRadius(8.dp.toPx(), 8.dp.toPx()))
        drawPath(bodyPath, MizuColors.Mist)
        clipPath(bodyPath) {
            val top = h - (h - neckH) * level
            drawRect(
                Brush.verticalGradient(listOf(MizuColors.Aqua, MizuColors.Water, MizuColors.WaterDeep), startY = top, endY = h),
                topLeft = Offset(0f, top),
                size = Size(w, h - top),
            )
            if (prevMark != null && newWater != null) {
                val y = h - (h - neckH) * prevMark
                drawLine(MizuColors.Ink.copy(alpha = 0.35f), Offset(0f, y), Offset(w, y), strokeWidth = 2.dp.toPx())
            }
            drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(w * 0.16f, neckH + 12.dp.toPx()), Size(w * 0.12f, (h - neckH) * 0.6f), CornerRadius(6.dp.toPx(), 6.dp.toPx()))
        }
        drawPath(bodyPath, MizuColors.Line, style = Stroke(2.dp.toPx()))
    }
}

