@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mizu.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Opacity
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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
import com.example.mizu.core.ReminderEngine
import com.example.mizu.util.toLocale
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
    val reached = GoalCalculator.isGoalReached(consumed, goal)
    val suggestion = ReminderEngine.contentAt(now, s, consumed)?.suggestedMl ?: 0
    val streak = GoalCalculator.streakDays(logs, goal, today)

    var sheet by remember { mutableStateOf<AmountSheetTarget?>(null) }
    val addedText = stringResource(R.string.toast_added)
    val deletedText = stringResource(R.string.toast_deleted)
    val undoText = stringResource(R.string.undo)
    val filledText = stringResource(R.string.toast_filled)

    fun add(ml: Int, source: DrinkSource) {
        vm.addDrink(ml, source) { id ->
            toast.show(String.format(addedText, ml.grouped()), undoText) { vm.deleteLog(id) }
        }
    }

    fun delete(log: DrinkLog) {
        vm.deleteLog(log.id)
        toast.show(String.format(deletedText, log.amountMl.grouped()), undoText) { vm.restoreLog(log) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Greeting(now, locale) }
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                HeroRing(GoalCalculator.progress(consumed, goal), consumed, goal, unit)
            }
        }
        item { StatusLine(reached, suggestion, unit) }
        item {
            Column {
                MonoKicker("mizu / timeline · ${today}")
                GlassCard(Modifier.padding(top = 6.dp), padding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)) {
                    DayTimeline(
                        logs = todayLogs,
                        start = s.reminderStart,
                        end = s.reminderEnd,
                        now = now,
                        modifier = Modifier.fillMaxWidth().height(78.dp),
                    )
                }
            }
        }
        item { MonoKicker("mizu / bottle") }
        item {
            val bottle = bottles.filter { it.isActive }.maxByOrNull { it.waterUpdatedAt ?: LocalDateTime.MIN }
            BottleCard(
                bottle = bottle,
                forecast = bottle?.let { BottlePlanner.forecast(it, now, s, logs) },
                unit = unit,
                onWeigh = onWeigh,
                onFill = { b ->
                    vm.fillBottle(b.id)
                    toast.show(String.format(filledText, (b.capacityMl ?: 0).grouped()))
                },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(Icons.Rounded.Opacity, remaining.grouped(), stringResource(R.string.stat_remaining, unit), Modifier.weight(1f))
                StatTile(Icons.Rounded.LocalDrink, todayLogs.size.toString(), stringResource(R.string.stat_drinks), Modifier.weight(1f))
                StatTile(Icons.Rounded.LocalFireDepartment, streak.toString(), stringResource(R.string.stat_streak), Modifier.weight(1f))
            }
        }
        item {
            SectionLabel(stringResource(R.string.quick_add)) {
                Pill(
                    stringResource(R.string.manual_add),
                    Modifier.bouncyClick(haptic = HapticKind.TICK) { sheet = AmountSheetTarget.New },
                    icon = Icons.Rounded.Tune,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                s.quickAddSizes.take(3).forEachIndexed { i, size ->
                    QuickAddTile(size, unit, iconSize = (20 + i * 6).dp, Modifier.weight(1f)) { add(size, DrinkSource.QUICK) }
                }
            }
        }
        item {
            val reminderText = when {
                nextReminder == null -> stringResource(R.string.reminder_off)
                nextReminder!!.toLocalDate() == today -> nextReminder!!.format(TIME_FORMAT)
                else -> stringResource(R.string.tomorrow_at, nextReminder!!.format(TIME_FORMAT))
            }
            GlassCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(if (nextReminder == null) Icons.Rounded.NotificationsOff else Icons.Rounded.NotificationsActive, size = 40.dp)
                    Text(
                        stringResource(R.string.next_reminder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MizuColors.InkSoft,
                        modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                    )
                    Text(reminderText, style = MaterialTheme.typography.titleLarge, color = MizuColors.Ink)
                }
            }
        }
        item {
            SectionLabel(stringResource(R.string.today_log)) {
                if (todayLogs.isNotEmpty()) {
                    Text(stringResource(R.string.swipe_hint), style = MaterialTheme.typography.labelSmall, color = MizuColors.InkFaint)
                }
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
                .background(MizuColors.Foam)
                .border(InkStroke, MizuColors.Ink, CircleShape)
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

@Composable
private fun StatusLine(reached: Boolean, suggestion: Int, unit: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (reached) {
            Pill(stringResource(R.string.goal_reached), icon = Icons.Rounded.EmojiEvents)
        } else {
            val template = stringResource(R.string.should_drink_more, -1)
            val amount = "${suggestion.grouped()} $unit"
            val parts = template.split("-1 ml", "-1")
            Text(
                buildAnnotatedString {
                    append(parts.getOrElse(0) { "" })
                    withStyle(
                        SpanStyle(
                            color = MizuColors.Ink,
                            background = MizuColors.Highlight,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    ) { append(" $amount ") }
                    append(parts.getOrElse(1) { "" })
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MizuColors.InkSoft,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    GlassCard(modifier, padding = PaddingValues(horizontal = 14.dp, vertical = 16.dp)) {
        Icon(icon, null, tint = MizuColors.WaterDeep, modifier = Modifier.size(20.dp))
        Column {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MizuColors.Ink, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MizuColors.InkSoft, maxLines = 1)
        }
    }
}

@Composable
private fun QuickAddTile(ml: Int, unit: String, iconSize: Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .heightIn(min = 120.dp)
            .clip(shape)
            .background(MizuColors.Foam)
            .border(InkStroke, MizuColors.Ink, shape)
            .bouncyClick(haptic = HapticKind.SUCCESS, pressedScale = 0.92f, onClick = onClick)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.height(36.dp), contentAlignment = Alignment.BottomCenter) {
            Icon(Icons.Rounded.WaterDrop, null, tint = MizuColors.WaterDeep, modifier = Modifier.size(iconSize))
        }
        Spacer(Modifier.height(10.dp))
        Text("+${ml.grouped()}", style = MaterialTheme.typography.titleLarge, color = MizuColors.Ink)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = MizuColors.InkSoft)
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    emphasizeSubtitle: Boolean = false,
    onClick: (() -> Unit)?,
) {
    GlassCard(modifier, onClick = onClick, padding = PaddingValues(16.dp)) {
        IconBadge(icon, size = 40.dp)
        Column {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MizuColors.InkSoft, maxLines = 1)
            Text(
                subtitle,
                style = if (emphasizeSubtitle) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                color = MizuColors.Ink,
                maxLines = 1,
            )
        }
    }
}

/** Water left in the bottle, when it will run dry at the pace the goal needs, and quick weigh / fill. */
@Composable
private fun BottleCard(bottle: Bottle?, forecast: BottleForecast?, unit: String, onWeigh: () -> Unit, onFill: (Bottle) -> Unit) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BottleGauge(
                previousWater = bottle?.currentWaterG,
                newWater = null,
                capacityMl = bottle?.capacityMl,
                modifier = Modifier.size(width = 56.dp, height = 92.dp),
            )
            Column(Modifier.weight(1f).padding(start = 18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.bottle_card_label, bottle?.name ?: "—"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MizuColors.InkSoft,
                    maxLines = 1,
                )
                if (forecast == null) {
                    Text(stringResource(R.string.bottle_status_not_weighed), style = MaterialTheme.typography.bodyLarge, color = MizuColors.Ink)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(forecast.waterMl.grouped(), style = MaterialTheme.typography.headlineSmall, color = MizuColors.Ink)
                        Text(
                            " " + (bottle?.capacityMl?.let { "/ ${it.grouped()} " } ?: "") + unit,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MizuColors.InkSoft,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                    val status = when {
                        forecast.waterMl <= 0 -> stringResource(R.string.bottle_status_empty)
                        forecast.coversGoal -> stringResource(R.string.bottle_status_covers_goal)
                        forecast.emptyAt != null -> stringResource(R.string.bottle_status_empty_at, forecast.emptyAt!!.format(TIME_FORMAT))
                        else -> stringResource(R.string.bottle_status_lasts_today)
                    }
                    Text(
                        status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (forecast.waterMl <= 0) MizuColors.Danger else MizuColors.WaterDeep,
                    )
                    val extra = buildList {
                        if (forecast.refillsNeeded > 0) add(stringResource(R.string.bottle_refills_needed, forecast.refillsNeeded))
                        bottle?.waterUpdatedAt?.let { add(stringResource(R.string.bottle_last_weighed, it.format(TIME_FORMAT))) }
                    }
                    if (extra.isNotEmpty()) {
                        Text(extra.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MizuColors.InkFaint)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton(stringResource(R.string.weigh_short), onWeigh, Modifier.weight(1f))
            PrimaryButton(
                stringResource(R.string.fill_full),
                onClick = { if (bottle != null) onFill(bottle) },
                modifier = Modifier.weight(1f),
                enabled = bottle?.capacityMl != null,
                haptic = HapticKind.SUCCESS,
            )
        }
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
            .border(1.dp, MizuColors.Ink, RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.bouncyClick(pressedScale = 0.98f, haptic = HapticKind.TICK, onClick = onClick) else Modifier)
            .heightIn(min = 68.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            log.timestamp.format(TIME_FORMAT),
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
            Box(Modifier.size(12.dp).clip(CircleShape).background(MizuColors.Water).border(1.dp, MizuColors.Ink, CircleShape))
            Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White))
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text("${log.amountMl.grouped()} ${stringResource(R.string.unit_ml)}", style = MaterialTheme.typography.titleMedium, color = MizuColors.Ink)
            Text(sourceLabel, style = MaterialTheme.typography.bodyMedium, color = MizuColors.InkSoft)
        }
        IconBadge(icon, size = 34.dp, modifier = Modifier.padding(end = 10.dp))
    }
}

val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
