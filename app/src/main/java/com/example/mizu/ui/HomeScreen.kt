@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mizu.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.mizu.util.toLocale
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(vm: MizuViewModel, toast: ToastState, onWeigh: () -> Unit, bottomPadding: Dp) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val nextReminder by vm.nextReminderAt.collectAsStateWithLifecycle()
    val bottles by vm.bottles.collectAsStateWithLifecycle()
    val s = settings ?: return
    val locale = s.language.toLocale()
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Greeting(now, locale) }
        item { HeroTile(consumed, goal, remaining, advice) }
        item {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BottleTile(
                    bottle = bottle,
                    forecast = bottle?.let { BottlePlanner.forecast(it, now, s, logs) },
                    onWeigh = onWeigh,
                    onFill = { confirmFill = it },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoTile(stringResource(R.string.next_reminder), reminderValue, reminderSub, Modifier.weight(1f).fillMaxWidth())
                    InfoTile(
                        stringResource(R.string.stat_streak),
                        streak.toString(),
                        stringResource(R.string.home_drinks_today, todayLogs.size),
                        Modifier.weight(1f).fillMaxWidth(),
                        suffix = stringResource(R.string.unit_days),
                    )
                }
            }
        }
        item {
            GlassCard(padding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
                Text(stringResource(R.string.home_timeline, todayLogs.size), style = MaterialTheme.typography.labelMedium, color = MizuColors.InkFaint)
                DayTimeline(
                    logs = todayLogs,
                    start = s.reminderStart,
                    end = s.reminderEnd,
                    now = now,
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                )
            }
        }
        item {
            SectionLabel(stringResource(R.string.today_log)) {
                Pill(
                    "+ " + stringResource(R.string.manual_add),
                    Modifier.bouncyClick(haptic = HapticKind.TICK) { sheet = AmountSheetTarget.New },
                    background = Color.White,
                    color = MizuColors.WaterDeep,
                )
            }
            if (todayLogs.isNotEmpty()) {
                Text(stringResource(R.string.swipe_hint), style = MaterialTheme.typography.labelSmall, color = MizuColors.InkFaint, modifier = Modifier.padding(start = 4.dp))
            }
        }
        if (todayLogs.isEmpty()) {
            item { EmptyToday() }
        } else {
            itemsIndexed(todayLogs, key = { _, log -> log.id }) { index, log ->
                SwipeableLogRow(
                    log = log,
                    isFirst = index == 0,
                    isLast = index == todayLogs.lastIndex,
                    onClick = { sheet = AmountSheetTarget.Edit(log) },
                    onDelete = { delete(log) },
                )
            }
        }
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

@Composable
private fun durationText(minutes: Long): String {
    val m = minutes.coerceAtLeast(0).toInt()
    return if (m >= 60) stringResource(R.string.duration_hm, m / 60, m % 60) else stringResource(R.string.duration_m, m)
}

private sealed interface AmountSheetTarget {
    data object New : AmountSheetTarget
    data class Edit(val log: DrinkLog) : AmountSheetTarget
}

@Composable
private fun Greeting(now: LocalDateTime, locale: java.util.Locale) {
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val wiggle = remember { Animatable(0f) }
    val greeting = stringResource(
        when (now.hour) {
            in 5..11 -> R.string.greeting_morning
            in 12..16 -> R.string.greeting_afternoon
            in 17..20 -> R.string.greeting_evening
            else -> R.string.greeting_night
        },
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale)), style = MaterialTheme.typography.labelMedium, color = MizuColors.InkSoft)
            Text(greeting, style = MaterialTheme.typography.headlineMedium, color = MizuColors.Ink)
        }
        // Tap the mascot: it wiggles.
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White)
                .bouncyClick(haptic = HapticKind.TICK) {
                    scope.launch {
                        haptics.tick()
                        wiggle.snapTo(0f)
                        wiggle.animateTo(0f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow), initialVelocity = 900f)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(R.drawable.mizu_mascot),
                contentDescription = null,
                modifier = Modifier.size(46.dp).graphicsLayer { rotationZ = wiggle.value },
            )
        }
    }
}

/** Today's total as a ring + number, with the pace advice underneath. */
@Composable
private fun HeroTile(consumed: Int, goal: Int, remaining: Int, advice: PaceAdvice) {
    val progress = GoalCalculator.progress(consumed, goal)
    val animated by animateFloatAsState(progress, tween(700), label = "hero")
    GlassCard(padding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                MiniRing(animated, size = 104.dp, stroke = 11.dp)
                Text("${(progress * 100).roundToInt()}%", style = MaterialTheme.typography.titleLarge, color = MizuColors.Ink)
            }
            Column(Modifier.weight(1f).padding(start = 18.dp)) {
                Text(stringResource(R.string.home_drunk_today), style = MaterialTheme.typography.labelMedium, color = MizuColors.InkFaint)
                Text(consumed.grouped(), style = MaterialTheme.typography.displayMedium, color = MizuColors.Ink, maxLines = 1)
                Text(
                    stringResource(R.string.home_of_goal, goal.grouped(), remaining.grouped()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MizuColors.InkSoft,
                )
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
        PaceState.BEHIND -> {
            title = stringResource(R.string.advice_behind_t, (-a.gapMl).grouped())
            detail = stringResource(R.string.advice_behind_d, a.drinkNowMl.grouped(), by, a.ratePerHourMl.grouped())
        }
        PaceState.BEHIND_FAR -> {
            title = stringResource(R.string.advice_behind_far_t, (-a.gapMl).grouped())
            detail = stringResource(R.string.advice_behind_far_d, a.drinkNowMl.grouped(), a.ratePerHourMl.grouped())
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
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(forecast.waterMl.grouped(), style = MaterialTheme.typography.headlineSmall, color = MizuColors.Ink)
                    bottle?.capacityMl?.let {
                        Text(" / ${it.grouped()}", style = MaterialTheme.typography.bodyMedium, color = MizuColors.InkFaint, modifier = Modifier.padding(bottom = 3.dp))
                    }
                }
                val emptyAt = forecast.emptyAt
                val status = when {
                    forecast.waterMl <= 0 -> stringResource(R.string.bottle_status_empty)
                    forecast.coversGoal -> stringResource(R.string.bottle_status_covers_goal)
                    emptyAt != null -> stringResource(R.string.home_empty_at, emptyAt.format(TIME_FORMAT))
                    else -> stringResource(R.string.bottle_status_lasts_today)
                }
                Text(
                    status,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (forecast.waterMl <= 0) MizuColors.Danger else MizuColors.InkSoft,
                    textAlign = TextAlign.Center,
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
            Text(label, style = MaterialTheme.typography.labelMedium, color = MizuColors.InkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineSmall, color = MizuColors.Ink, maxLines = 1)
                if (suffix != null) {
                    Text(" $suffix", style = MaterialTheme.typography.bodyMedium, color = MizuColors.InkFaint, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            Text(sub, style = MaterialTheme.typography.labelMedium, color = MizuColors.InkSoft, maxLines = 2)
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
