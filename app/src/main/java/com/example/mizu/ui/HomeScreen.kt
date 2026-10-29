@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mizu.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mizu.R
import com.example.mizu.core.Bottle
import com.example.mizu.core.BottleForecast
import com.example.mizu.core.BottlePlanner
import com.example.mizu.core.DrinkLog
import com.example.mizu.core.DrinkSource
import com.example.mizu.core.GoalCalculator
import com.example.mizu.core.PaceAdvice
import com.example.mizu.core.PaceAdvisor
import com.example.mizu.core.PaceState
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun HomeScreen(vm: MizuViewModel, toast: ToastState, onWeigh: () -> Unit, bottomPadding: Dp) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val nextReminder by vm.nextReminderAt.collectAsStateWithLifecycle()
    val bottles by vm.bottles.collectAsStateWithLifecycle()
    val s = settings ?: return
    val unit = stringResource(R.string.unit_ml)

    val today = now.toLocalDate()
    val todayLogs = GoalCalculator.logsOn(today, logs).sortedByDescending { it.timestamp }
    val consumed = todayLogs.sumOf { it.amountMl }
    val goal = s.goalMl
    val remaining = GoalCalculator.remainingMl(consumed, goal)
    val advice = PaceAdvisor.advise(now, s, todayLogs)
    val streak = GoalCalculator.streakDays(logs, goal, today)
    val bottle = currentBottle(bottles)

    var sheet by remember { mutableStateOf<AmountSheetTarget?>(null) }
    var confirmFill by remember { mutableStateOf<Bottle?>(null) }
    val addedText = stringResource(R.string.toast_added)
    val deletedText = stringResource(R.string.toast_deleted)
    val undoText = stringResource(R.string.undo)
    val filledText = stringResource(R.string.toast_filled)

    val sounds = LocalSounds.current
    fun add(ml: Int, source: DrinkSource) {
        vm.addDrink(ml, source) { id ->
            sounds.drop()
            toast.show(String.format(addedText, ml.grouped()), undoText) { vm.deleteLog(id) }
        }
    }

    fun delete(log: DrinkLog) {
        vm.deleteLog(log.id)
        toast.show(String.format(deletedText, log.amountMl.grouped()), undoText) { vm.restoreLog(log) }
    }

    val next = nextReminder
    val reminderValue = next?.format(TIME_FORMAT) ?: "—"
    val reminderSub = when {
        next == null -> stringResource(R.string.reminder_off)
        next.toLocalDate() == today -> stringResource(R.string.home_in, durationText(Duration.between(now, next).toMinutes()))
        else -> stringResource(R.string.tomorrow_at, next.format(TIME_FORMAT))
    }

    // Far behind (with the red alert setting on): the screen edges pulse red, like a warning light.
    val alert = s.sirenEnabled && advice.state == PaceState.BEHIND_FAR
    Box(Modifier.fillMaxSize()) {
        MinimalHome(
            remaining = remaining,
            consumed = consumed,
            goal = goal,
            advice = advice,
            bottleWaterMl = bottle?.currentWaterG,
            bottleCapacityMl = bottle?.capacityMl,
            recentLogs = logs.filter { it.timestamp.isAfter(now.minusHours(3)) },
            windowStart = s.reminderStart,
            windowEnd = s.reminderEnd,
            unit = unit,
            bottomPadding = bottomPadding,
            onBottleClick = onWeigh,
        )
        if (alert) AlertGlow(Modifier.matchParentSize())
    }

    confirmFill?.let { b ->
        FillConfirmSheet(
            bottle = b,
            onConfirm = {
                vm.fillBottle(b.id)
                sounds.fill()
                toast.show(String.format(filledText, (b.capacityMl ?: 0).grouped()))
                confirmFill = null
            },
            onDismiss = { confirmFill = null },
        )
    }

    when (val target = sheet) {
        null -> Unit
        AmountSheetTarget.New -> NumberSheet(
            title = stringResource(R.string.manual_add_title),
            initial = null,
            range = 1..5000,
            unit = unit,
            presets = listOf(50, 100, 250),
            onSave = { ml -> add(ml, DrinkSource.MANUAL); sheet = null },
            onDismiss = { sheet = null },
        )
        is AmountSheetTarget.Edit -> NumberSheet(
            title = stringResource(R.string.edit_log_at, target.log.timestamp.format(TIME_FORMAT)),
            initial = target.log.amountMl,
            range = 1..5000,
            unit = unit,
            onSave = { ml -> vm.editLog(target.log.id, ml); sheet = null },
            onDelete = { delete(target.log); sheet = null },
            onDismiss = { sheet = null },
        )
    }
}

/** The bottle the dashboard, the refill tile and the shortcut talk about: the active one weighed most recently. */
internal fun currentBottle(bottles: List<Bottle>): Bottle? =
    bottles.filter { it.isActive }.maxByOrNull { it.waterUpdatedAt ?: LocalDateTime.MIN }

/** Home: only the bottle, filling with the day's water, and how much is left to drink. Tap the bottle to weigh it. */
@Composable
private fun MinimalHome(
    remaining: Int,
    consumed: Int,
    goal: Int,
    advice: PaceAdvice,
    recentLogs: List<DrinkLog>,
    bottleWaterMl: Int?,
    bottleCapacityMl: Int?,
    windowStart: java.time.LocalTime,
    windowEnd: java.time.LocalTime,
    unit: String,
    bottomPadding: Dp,
    onBottleClick: () -> Unit,
) {
    val showPlan = advice.state in setOf(PaceState.BEHIND, PaceState.BEHIND_FAR, PaceState.ON_TRACK, PaceState.AHEAD) && goal > 0
    // The even-pace plan runs with the clock, second by second, so the gap to it is always up to date.
    val tick by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1000L)
        }
    }
    val startDt = tick.toLocalDate().atTime(windowStart)
    val endDt = tick.toLocalDate().atTime(windowEnd)
    val windowMs = Duration.between(startDt, endDt).toMillis().coerceAtLeast(1L)
    val frac = (Duration.between(startDt, tick).toMillis().toDouble() / windowMs).coerceIn(0.0, 1.0)
    val planMl = goal * frac
    val planLevel = if (goal > 0) (planMl / goal).toFloat().coerceIn(0f, 1f) else 0f
    val behind = advice.state == PaceState.BEHIND || advice.state == PaceState.BEHIND_FAR
    val planColor = if (behind) MizuColors.Warn else MizuColors.WaterDeep
    val level by animateFloatAsState(GoalCalculator.progress(consumed, goal), tween(900, easing = FastOutSlowInEasing), label = "level")
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val bottleFill = if (bottleWaterMl != null) {
        val cap = bottleCapacityMl?.takeIf { it > 0 } ?: maxOf(bottleWaterMl, 600)
        (bottleWaterMl.toFloat() / cap).coerceIn(0f, 1f)
    } else {
        0f
    }
    val bottleLevel by animateFloatAsState(bottleFill, tween(900, easing = FastOutSlowInEasing), label = "bottleLevel")
    Column(
        Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = bottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.home_drunk_today), style = MaterialTheme.typography.titleMedium, color = MizuColors.InkSoft)
        Text(consumed.grouped(), fontSize = 84.sp, lineHeight = 90.sp, fontWeight = FontWeight.Bold, color = MizuColors.Ink)
        Text(
            unit + "  " + stringResource(R.string.home_of_goal, goal.grouped(), (level * 100).roundToInt().coerceAtLeast(0)),
            style = MaterialTheme.typography.titleMedium,
            color = MizuColors.WaterDeep,
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(Modifier.fillMaxSize().bouncyClick(haptic = HapticKind.TICK) { onBottleClick() }) {
                drawBodyFigure(level, phase, if (showPlan) planLevel else null, planColor)
            }
            Column(Modifier.align(Alignment.BottomEnd), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(
                    Modifier.size(width = 76.dp, height = 128.dp).bouncyClick(haptic = HapticKind.TICK) { onBottleClick() },
                ) { drawMiniBottle(bottleLevel, phase) }
                Text(stringResource(R.string.home_bottle_left), style = MaterialTheme.typography.labelSmall, color = MizuColors.InkSoft)
                Text(
                    if (bottleWaterMl != null) bottleWaterMl.grouped() + " " + unit else "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MizuColors.Ink,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        AdvicePill(advice, if (showPlan) planMl - consumed else null)
        Spacer(Modifier.height(10.dp))
        SettlingMeter(PaceAdvisor.settlingMl(tick, recentLogs), unit)
    }
}

private fun cartoonFigure(): Path = Path().apply {
    // Big-headed little person, in a 400 x 700 space.
    addOval(androidx.compose.ui.geometry.Rect(50f, 20f, 350f, 320f))
    addRoundRect(androidx.compose.ui.geometry.RoundRect(130f, 290f, 270f, 520f, CornerRadius(62f)))
    fun limb(l: Float, t: Float, w: Float, h: Float, deg: Float, px: Float, py: Float) {
        val limb = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(l, t, l + w, t + h, CornerRadius(w / 2))) }
        val m = androidx.compose.ui.graphics.Matrix().apply { translate(px, py); rotateZ(deg); translate(-px, -py) }
        limb.transform(m)
        addPath(limb)
    }
    limb(78f, 330f, 52f, 150f, 14f, 104f, 330f)
    limb(270f, 330f, 52f, 150f, -14f, 296f, 330f)
    limb(140f, 470f, 58f, 150f, 0f, 0f, 0f)
    limb(202f, 470f, 58f, 150f, 0f, 0f, 0f)
}

/** The cartoon person, filled with water up to [level] (today's progress); a dashed line marks the even-pace [planLevel]. */
private fun DrawScope.drawBodyFigure(level: Float, phase: Float, planLevel: Float?, planColor: Color) {
    val sc = minOf(size.width / 300f, size.height / 640f)
    val ox = (size.width - 300f * sc) / 2f - 50f * sc
    val oy = (size.height - 640f * sc) / 2f
    translate(left = ox, top = oy) {
        scale(sc, sc, pivot = Offset.Zero) {
            val fig = cartoonFigure()
            val top = 20f
            val bottom = 620f
            val waterY = bottom - (bottom - top) * level
            drawPath(fig, MizuColors.Ink, style = Stroke(width = 22f, join = StrokeJoin.Round))
            drawPath(fig, Color.White)
            clipPath(fig) {
                fun wave(offset: Float, amp: Float) = Path().apply {
                    moveTo(0f, waterY)
                    var x = 0f
                    while (x <= 400f) {
                        lineTo(x, waterY + amp * sin(x / 400f * 4f * PI.toFloat() + (phase + offset) * 2f * PI.toFloat()))
                        x += 6f
                    }
                    lineTo(400f, 700f)
                    lineTo(0f, 700f)
                    close()
                }
                drawPath(wave(0.3f, 7f), MizuColors.Aqua)
                drawPath(wave(0f, 9f), MizuColors.Water)
            }
            // face
            drawCircle(MizuColors.Ink, 14f, Offset(150f, 170f))
            drawCircle(MizuColors.Ink, 14f, Offset(250f, 170f))
            drawCircle(Color.White, 4.5f, Offset(155f, 164f))
            drawCircle(Color.White, 4.5f, Offset(255f, 164f))
            drawArc(
                MizuColors.Ink, startAngle = 20f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(172f, 190f), size = Size(56f, 34f), style = Stroke(width = 9f, cap = StrokeCap.Round),
            )
            drawCircle(Color(0xFFF4A6A6).copy(alpha = 0.75f), 22f, Offset(118f, 214f))
            drawCircle(Color(0xFFF4A6A6).copy(alpha = 0.75f), 22f, Offset(282f, 214f))
            if (planLevel != null) {
                val planY = bottom - (bottom - top) * planLevel
                drawLine(
                    planColor, Offset(20f, planY), Offset(380f, planY), strokeWidth = 7f, cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)),
                )
                drawCircle(planColor, 9f, Offset(380f, planY))
            }
        }
    }
}

/** A small bottle showing how much water is left in the one in use. */
private fun DrawScope.drawMiniBottle(level: Float, phase: Float) {
    val w = size.width
    val h = size.height
    val body = Path().apply {
        moveTo(0.14f * w, 0.20f * h)
        lineTo(0.86f * w, 0.20f * h)
        lineTo(0.82f * w, 0.93f * h)
        quadraticBezierTo(0.81f * w, 0.99f * h, 0.72f * w, 0.99f * h)
        lineTo(0.28f * w, 0.99f * h)
        quadraticBezierTo(0.19f * w, 0.99f * h, 0.18f * w, 0.93f * h)
        close()
    }
    val top = 0.20f * h
    val bottom = 0.99f * h
    val waterY = bottom - (bottom - top) * level
    drawPath(body, Color.White)
    clipPath(body) {
        val wave = Path().apply {
            moveTo(0f, waterY)
            var x = 0f
            while (x <= w) {
                lineTo(x, waterY + 0.02f * h * sin(x / w * 3f * PI.toFloat() + phase * 2f * PI.toFloat()))
                x += 4f
            }
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(wave, MizuColors.Water)
    }
    drawPath(body, MizuColors.Ink, style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round))
    drawRoundRect(Color(0xFF2B3440), Offset(0.36f * w, 0.0f), Size(0.28f * w, 0.10f * h), CornerRadius(0.03f * h))
    drawRoundRect(Color(0xFF3A414B), Offset(0.08f * w, 0.08f * h), Size(0.84f * w, 0.14f * h), CornerRadius(0.03f * h))
}

private fun ml1(v: Double): String = String.format(java.util.Locale.US, "%,.1f", v)

/** One line of advice under the bottle: what to drink now (or to rest), and by when. */
@Composable
private fun AdvicePill(a: PaceAdvice, liveGapMl: Double?) {
    val by = a.byTime?.format(TIME_FORMAT) ?: "—"
    val done = a.state == PaceState.REACHED || a.state == PaceState.OVER_GOAL
    // Always one clear call: drink now, or rest (and when to drink again, and how much).
    val drinkNow = !done && a.drinkNowMl > 0 && !a.restFirst &&
        (a.state == PaceState.BEHIND || a.state == PaceState.BEHIND_FAR ||
            a.state == PaceState.BEFORE_START || a.state == PaceState.DAY_OVER_SHORT)
    val headline = when {
        done -> stringResource(R.string.goal_reached)
        drinkNow -> stringResource(R.string.advice_pill_drink_now, a.drinkNowMl.grouped())
        else -> stringResource(R.string.advice_pill_rest)
    }
    val sub = when {
        done -> null
        drinkNow && (a.state == PaceState.BEHIND || a.state == PaceState.BEHIND_FAR) ->
            stringResource(R.string.advice_behind_t, (-a.gapMl).grouped()) + (a.byTime?.let { " · " + stringResource(R.string.advice_pill_by, by) } ?: "")
        drinkNow -> a.byTime?.let { stringResource(R.string.advice_pill_by, by) }
        a.byTime == null -> null
        a.drinkNowMl > 0 -> stringResource(R.string.advice_pill_resume_ml, a.drinkNowMl.grouped(), by)
        else -> stringResource(R.string.advice_pill_resume, by)
    }
    val (background, accent) = when (a.state) {
        PaceState.BEHIND, PaceState.BEHIND_FAR, PaceState.TOO_FAST, PaceState.OVER_GOAL -> MizuColors.WarnSoft to MizuColors.Warn
        PaceState.ON_TRACK, PaceState.REACHED -> MizuColors.GoodSoft to MizuColors.Good
        else -> MizuColors.Foam to MizuColors.WaterDeep
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(background).padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accent)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MizuColors.InkSoft)
        if (liveGapMl != null) {
            // Exact gap to the even pace, running with the clock.
            val text = if (liveGapMl >= 0) {
                stringResource(R.string.live_gap_behind, ml1(liveGapMl))
            } else {
                stringResource(R.string.live_gap_ahead, ml1(-liveGapMl))
            }
            Text(
                text,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = accent,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun durationText(minutes: Long): String {
    val m = minutes.coerceAtLeast(0).toInt()
    return if (m >= 60) stringResource(R.string.duration_hm, m / 60, m % 60) else stringResource(R.string.duration_m, m)
}

private sealed interface AmountSheetTarget {
    data object New : AmountSheetTarget
    data class Edit(val log: DrinkLog) : AmountSheetTarget
}

/** Red glow along the screen edges that slowly pulses; draws only, never takes touches. */
@Composable
private fun AlertGlow(modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "alertGlow")
    val t by pulse.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alertGlowPulse",
    )
    Box(
        modifier.drawBehind {
            val red = Color(0xFFE53935)
            val depth = 64.dp.toPx()
            val edge = listOf(red.copy(alpha = t), Color.Transparent)
            drawRect(Brush.verticalGradient(edge, startY = 0f, endY = depth), size = Size(size.width, depth))
            drawRect(
                Brush.verticalGradient(edge.reversed(), startY = size.height - depth, endY = size.height),
                topLeft = Offset(0f, size.height - depth),
                size = Size(size.width, depth),
            )
            drawRect(Brush.horizontalGradient(edge, startX = 0f, endX = depth), size = Size(depth, size.height))
            drawRect(
                Brush.horizontalGradient(edge.reversed(), startX = size.width - depth, endX = size.width),
                topLeft = Offset(size.width - depth, 0f),
                size = Size(depth, size.height),
            )
        },
    )
}

/** Today's total as a ring + number, with the pace advice underneath. */
@Composable
private fun HeroTile(consumed: Int, goal: Int, remaining: Int, advice: PaceAdvice) {
    val progress = GoalCalculator.progress(consumed, goal)
    val animated by animateFloatAsState(progress, tween(700), label = "hero")
    GlassCard(padding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                MiniRing(animated, size = 96.dp, stroke = 10.dp)
                Text("${(progress * 100).roundToInt()}%", style = MaterialTheme.typography.titleLarge, color = MizuColors.Ink)
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                FitText(stringResource(R.string.home_drunk_today), MaterialTheme.typography.labelMedium, MizuColors.InkFaint)
                FitText(consumed.grouped(), MaterialTheme.typography.displayMedium, MizuColors.Ink)
                FitText(stringResource(R.string.home_goal_of, goal.grouped()), MaterialTheme.typography.bodyMedium, MizuColors.InkSoft)
                FitText(stringResource(R.string.home_left, remaining.grouped()), MaterialTheme.typography.bodyMedium, MizuColors.InkSoft)
            }
        }
        AdviceBlock(advice)
    }
}

/** Plain-language pace advice: how far behind / ahead, how much to drink by when, or to slow down. */
@Composable
private fun AdviceBlock(a: PaceAdvice) {
    val by = a.byTime?.format(TIME_FORMAT) ?: "—"
    val title: String
    val detail: String
    when (a.state) {
        PaceState.BEFORE_START -> {
            title = stringResource(R.string.advice_before_start_t)
            detail = stringResource(R.string.advice_before_start_d, by, a.drinkNowMl.grouped())
        }
        PaceState.BEHIND, PaceState.BEHIND_FAR -> {
            title = if (a.state == PaceState.BEHIND) {
                stringResource(R.string.advice_behind_t, (-a.gapMl).grouped())
            } else {
                stringResource(R.string.advice_behind_far_t, (-a.gapMl).grouped())
            }
            detail = when {
                a.restFirst -> stringResource(R.string.advice_rest_first_d, a.lastDrinkMl.grouped(), a.drinkNowMl.grouped(), by, a.ratePerHourMl.grouped())
                a.state == PaceState.BEHIND -> stringResource(R.string.advice_behind_d, a.drinkNowMl.grouped(), by, a.ratePerHourMl.grouped())
                else -> stringResource(R.string.advice_behind_far_d, a.drinkNowMl.grouped(), a.ratePerHourMl.grouped())
            }
        }
        PaceState.ON_TRACK -> {
            title = stringResource(R.string.advice_on_track_t)
            detail = if (a.byTime == null || a.drinkNowMl <= 0) {
                stringResource(R.string.advice_reached_d)
            } else {
                stringResource(R.string.advice_on_track_d, a.drinkNowMl.grouped(), by, a.remainingMl.grouped())
            }
        }
        PaceState.AHEAD -> {
            title = stringResource(R.string.advice_ahead_t, a.gapMl.grouped())
            detail = stringResource(R.string.advice_ahead_d, by, a.remainingMl.grouped())
        }
        PaceState.TOO_FAST -> {
            title = stringResource(R.string.advice_too_fast_t)
            detail = stringResource(R.string.advice_too_fast_d, a.lastHourMl.grouped(), by)
        }
        PaceState.REACHED -> {
            title = stringResource(R.string.goal_reached)
            detail = if (a.overMl > 0) stringResource(R.string.advice_reached_over_d, a.overMl.grouped()) else stringResource(R.string.advice_reached_d)
        }
        PaceState.OVER_GOAL -> {
            title = stringResource(R.string.advice_over_t)
            detail = stringResource(R.string.advice_over_d, a.overMl.grouped())
        }
        PaceState.DAY_OVER_SHORT -> {
            title = stringResource(R.string.advice_day_over_t)
            detail = stringResource(R.string.advice_day_over_d, a.remainingMl.grouped(), a.drinkNowMl.grouped())
        }
    }
    val (background, accent) = when (a.state) {
        PaceState.BEHIND_FAR, PaceState.TOO_FAST, PaceState.OVER_GOAL -> MizuColors.WarnSoft to MizuColors.Warn
        PaceState.AHEAD, PaceState.REACHED -> MizuColors.GoodSoft to MizuColors.Good
        else -> MizuColors.Foam to MizuColors.WaterDeep
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(background).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = accent)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MizuColors.InkSoft)
        // Catching up would need more than the body comfortably takes: say so instead of pushing.
        if ((a.state == PaceState.BEHIND || a.state == PaceState.BEHIND_FAR) && a.ratePerHourMl > PaceAdvisor.COMFORT_ML_PER_HOUR) {
            Text(stringResource(R.string.advice_pace_cap), style = MaterialTheme.typography.bodyMedium, color = MizuColors.Warn)
        }
    }
}

/** The bottle in use: cartoon cup with its water level, amount, when it runs dry, weigh / refill. */
@Composable
private fun BottleTile(bottle: Bottle?, forecast: BottleForecast?, onWeigh: () -> Unit, onFill: (Bottle) -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier, padding = PaddingValues(16.dp)) {
        Text(
            bottle?.name ?: "—",
            style = MaterialTheme.typography.labelMedium,
            color = MizuColors.InkFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BottleGauge(
            previousWater = bottle?.currentWaterG,
            newWater = null,
            capacityMl = bottle?.capacityMl,
            modifier = Modifier.align(Alignment.CenterHorizontally).size(width = 56.dp, height = 112.dp),
        )
        Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            if (forecast == null) {
                Text(
                    stringResource(R.string.bottle_status_not_weighed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MizuColors.InkSoft,
                    textAlign = TextAlign.Center,
                )
            } else {
                FitText(
                    forecast.waterMl.grouped() + (bottle?.capacityMl?.let { " / ${it.grouped()}" } ?: ""),
                    MaterialTheme.typography.titleLarge,
                    MizuColors.Ink,
                )
                val emptyAt = forecast.emptyAt
                val status = when {
                    forecast.waterMl <= 0 -> stringResource(R.string.bottle_status_empty)
                    forecast.coversGoal -> stringResource(R.string.bottle_status_covers_goal)
                    emptyAt != null -> stringResource(R.string.home_empty_at, emptyAt.format(TIME_FORMAT))
                    else -> stringResource(R.string.bottle_status_lasts_today)
                }
                FitText(
                    status,
                    MaterialTheme.typography.labelMedium,
                    if (forecast.waterMl <= 0) MizuColors.Danger else MizuColors.InkSoft,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChipButton(stringResource(R.string.weigh_short), MizuColors.Ink, Color.White, Modifier.weight(1f), onClick = onWeigh)
            ChipButton(
                stringResource(R.string.fill_short),
                MizuColors.Foam,
                MizuColors.WaterDeep,
                Modifier.weight(1f),
                enabled = bottle?.capacityMl != null,
                onClick = { if (bottle != null) onFill(bottle) },
            )
        }
    }
}

@Composable
private fun ChipButton(text: String, background: Color, color: Color, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(if (enabled) background else MizuColors.Line)
            .bouncyClick(enabled = enabled, haptic = HapticKind.TICK, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (enabled) color else MizuColors.InkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InfoTile(label: String, value: String, sub: String, modifier: Modifier = Modifier, suffix: String? = null) {
    GlassCard(modifier, padding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FitText(label, MaterialTheme.typography.labelMedium, MizuColors.InkFaint)
            Row(verticalAlignment = Alignment.Bottom) {
                FitText(value, MaterialTheme.typography.headlineSmall, MizuColors.Ink, Modifier.weight(1f, fill = false))
                if (suffix != null) {
                    Text(" $suffix", style = MaterialTheme.typography.bodyMedium, color = MizuColors.InkFaint, maxLines = 1, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            FitText(sub, MaterialTheme.typography.labelMedium, MizuColors.InkSoft)
        }
    }
}

/** Asks before refilling: the button sits where a stray tap lands easily. */
@Composable
internal fun FillConfirmSheet(bottle: Bottle, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    MizuSheet(onDismiss) {
        BottleGauge(
            previousWater = bottle.capacityMl,
            newWater = null,
            capacityMl = bottle.capacityMl,
            modifier = Modifier.align(Alignment.CenterHorizontally).size(width = 54.dp, height = 108.dp),
        )
        Text(
            stringResource(R.string.fill_confirm_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MizuColors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.fill_confirm_body, bottle.name, (bottle.capacityMl ?: 0).grouped()),
            style = MaterialTheme.typography.bodyMedium,
            color = MizuColors.InkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(stringResource(R.string.fill_confirm_ok), onConfirm, Modifier.fillMaxWidth(), haptic = HapticKind.SUCCESS)
        SoftButton(stringResource(R.string.cancel), onDismiss, Modifier.fillMaxWidth())
    }
}

@Composable
private fun EmptyToday() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(painterResource(R.drawable.mizu_mascot), contentDescription = null, modifier = Modifier.size(96.dp))
        Text(stringResource(R.string.no_logs_today), style = MaterialTheme.typography.bodyLarge, color = MizuColors.InkSoft, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SwipeableLogRow(log: DrinkLog, isFirst: Boolean, isLast: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    val haptics = LocalHaptics.current
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                haptics.heavy()
                onDelete()
                true
            } else {
                false
            }
        },
        positionalThreshold = { it * 0.35f },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                if (state.targetValue == SwipeToDismissBoxValue.EndToStart) MizuColors.Danger else MizuColors.Danger.copy(alpha = 0.6f),
                label = "swipe",
            )
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(color).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Rounded.Delete, stringResource(R.string.delete), tint = Color.White)
            }
        },
    ) {
        TimelineRow(log, isFirst, isLast, onClick)
    }
}

@Composable
fun TimelineRow(log: DrinkLog, isFirst: Boolean, isLast: Boolean, onClick: (() -> Unit)?) {
    val sourceLabel = stringResource(
        when (log.source) {
            DrinkSource.QUICK -> R.string.source_quick
            DrinkSource.WEIGH -> R.string.source_weigh
            DrinkSource.MANUAL -> R.string.source_manual
        },
    )
    val icon = when (log.source) {
        DrinkSource.QUICK -> Icons.Rounded.WaterDrop
        DrinkSource.WEIGH -> Icons.Rounded.Scale
        DrinkSource.MANUAL -> Icons.Rounded.Tune
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .then(if (onClick != null) Modifier.bouncyClick(pressedScale = 0.98f, haptic = HapticKind.TICK, onClick = onClick) else Modifier)
            .heightIn(min = 68.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val estimatedFrom = log.estimatedFrom
        Text(
            (if (estimatedFrom != null) "≈" else "") + log.timestamp.format(TIME_FORMAT),
            style = MonoLabel.copy(fontSize = androidx.compose.ui.unit.TextUnit.Unspecified),
            color = MizuColors.InkSoft,
            modifier = Modifier.width(56.dp).padding(start = 12.dp),
        )
        // timeline rail with a dot
        Box(Modifier.width(28.dp).height(68.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(2.dp).weight(1f).background(if (isFirst) Color.Transparent else MizuColors.Line))
                Box(Modifier.width(2.dp).weight(1f).background(if (isLast) Color.Transparent else MizuColors.Line))
            }
            Box(Modifier.size(12.dp).clip(CircleShape).background(MizuColors.Water))
            Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White))
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text("${log.amountMl.grouped()} ${stringResource(R.string.unit_ml)}", style = MaterialTheme.typography.titleMedium, color = MizuColors.Ink)
            val detail = if (estimatedFrom != null) {
                val until = log.timestamp.plus(java.time.Duration.between(estimatedFrom, log.timestamp))
                stringResource(R.string.log_estimated_range, estimatedFrom.format(TIME_FORMAT), until.format(TIME_FORMAT)) + " · " + sourceLabel
            } else {
                sourceLabel
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MizuColors.InkSoft)
        }
        IconBadge(icon, size = 34.dp, modifier = Modifier.padding(end = 10.dp))
    }
}

val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
