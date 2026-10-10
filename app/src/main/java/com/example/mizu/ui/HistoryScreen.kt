package com.example.mizu.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mizu.R
import com.example.mizu.core.DaySummary
import com.example.mizu.core.GoalCalculator
import com.example.mizu.core.HistoryCalculator
import com.example.mizu.util.toLocale
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private enum class Range { DAY, WEEK, MONTH }

@Composable
fun HistoryScreen(vm: MizuViewModel, bottomPadding: Dp) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val s = settings ?: return
    val locale = s.language.toLocale()
    val unit = stringResource(R.string.unit_ml)
    val goal = s.goalMl

    var range by rememberSaveable { mutableStateOf(Range.WEEK) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val today = now.toLocalDate()

    val (from, to) = when (range) {
        Range.DAY -> today.plusDays(offset.toLong()).let { it to it }
        Range.WEEK -> today.plusWeeks(offset.toLong()).let {
            it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) to it.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        }
        Range.MONTH -> today.plusMonths(offset.toLong()).let { it.withDayOfMonth(1) to it.withDayOfMonth(it.lengthOfMonth()) }
    }
    val totals = HistoryCalculator.dailyTotals(logs, from, to)
    val counted = totals.filter { !it.date.isAfter(today) }
    val total = counted.sumOf { it.totalMl }
    val activeDays = counted.count { it.totalMl > 0 }
    val best = counted.maxByOrNull { it.totalMl }
    val hitDays = counted.count { GoalCalculator.isGoalReached(it.totalMl, goal) }
    val title = when (range) {
        Range.DAY -> from.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
        Range.WEEK -> "${from.format(DateTimeFormatter.ofPattern("d MMM", locale))} – ${to.format(DateTimeFormatter.ofPattern("d MMM", locale))}"
        Range.MONTH -> from.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                MonoKicker("mizu / history")
                Text(stringResource(R.string.tab_history), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.history_kicker), style = MaterialTheme.typography.bodyMedium, color = MizuColors.InkSoft)
            }
        }
        item {
            PillSelector(
                options = listOf(
                    Range.DAY to stringResource(R.string.range_day),
                    Range.WEEK to stringResource(R.string.range_week),
                    Range.MONTH to stringResource(R.string.range_month),
                ),
                selected = range,
                onSelect = { range = it; offset = 0 },
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.previous), { offset -= 1 })
                Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                CircleIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.next), { offset += 1 }, enabled = offset < 0)
            }
        }

        if (range == Range.DAY) {
            val dayLogs = GoalCalculator.logsOn(from, logs).sortedByDescending { it.timestamp }
            val dayTotal = dayLogs.sumOf { it.amountMl }
            item {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.total), style = MaterialTheme.typography.labelMedium, color = MizuColors.InkSoft)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(dayTotal.grouped(), style = MaterialTheme.typography.displayMedium)
                                Text(" / ${goal.grouped()} $unit", style = MaterialTheme.typography.bodyMedium, color = MizuColors.InkSoft, modifier = Modifier.padding(bottom = 8.dp))
                            }
                        }
                        MiniRing(GoalCalculator.progress(dayTotal, goal), size = 64.dp, stroke = 7.dp)
                    }
                }
            }
            if (dayLogs.isEmpty()) {
                item { Text(stringResource(R.string.no_logs_day), style = MaterialTheme.typography.bodyLarge, color = MizuColors.InkSoft, modifier = Modifier.padding(8.dp)) }
            } else {
                itemsIndexed(dayLogs, key = { _, l -> l.id }) { i, log -> TimelineRow(log, i == 0, i == dayLogs.lastIndex, null) }
            }
        } else {
            item {
                GlassCard {
                    Text(stringResource(R.string.total), style = MaterialTheme.typography.labelMedium, color = MizuColors.InkSoft)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(total.grouped(), style = MaterialTheme.typography.displayMedium)
                        Text(" $unit", style = MaterialTheme.typography.bodyLarge, color = MizuColors.InkSoft, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    BarChart(totals, goal, range == Range.WEEK, locale, unit, key = "$range$offset")
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniStat(Icons.Rounded.Insights, (if (activeDays > 0) total / activeDays else 0).grouped(), stringResource(R.string.daily_average_label), Modifier.weight(1f))
                    MiniStat(Icons.Rounded.CalendarMonth, (best?.totalMl ?: 0).grouped(), stringResource(R.string.best_day), Modifier.weight(1f))
                    MiniStat(Icons.Rounded.EmojiEvents, "$hitDays/${counted.size}", stringResource(R.string.goal_days), Modifier.weight(1f))
                }
            }
            item { SectionLabel(stringResource(R.string.daily_totals)) }
            item {
                GlassCard(padding = PaddingValues(vertical = 6.dp)) {
                    Column {
                        val rows = counted.reversed()
                        rows.forEachIndexed { i, day ->
                            DayTotalRow(day, goal, locale, unit)
                            if (i != rows.lastIndex) RowDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    GlassCard(modifier, padding = PaddingValues(horizontal = 14.dp, vertical = 16.dp)) {
        IconBadge(icon, size = 32.dp)
        Column {
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MizuColors.InkSoft, maxLines = 1)
        }
    }
}

@Composable
private fun DayTotalRow(day: DaySummary, goal: Int, locale: Locale, unit: String) {
    val progress = GoalCalculator.progress(day.totalMl, goal)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniRing(progress, size = 36.dp, stroke = 4.dp)
        Text(
            day.date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(start = 14.dp),
        )
        Text("${day.totalMl.grouped()} $unit", style = MaterialTheme.typography.titleMedium, color = if (progress >= 1f) MizuColors.WaterDeep else MizuColors.Ink)
    }
}

/** Gradient bars that grow in, a dashed goal line, and a tap-to-read value bubble (with a tick). */
@Composable
private fun BarChart(days: List<DaySummary>, goal: Int, weekLabels: Boolean, locale: Locale, unit: String, key: String) {
    val haptics = LocalHaptics.current
    val grow = remember { Animatable(0f) }
    var selected by remember(key) { mutableIntStateOf(-1) }
    LaunchedEffect(key) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val max = maxOf(goal, days.maxOfOrNull { it.totalMl } ?: 0, 1).toFloat()

    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
            val sel = days.getOrNull(selected)
            if (sel != null) {
                Pill("${sel.date.format(DateTimeFormatter.ofPattern("d MMM", locale))} · ${sel.totalMl.grouped()} $unit")
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .padding(top = 6.dp)
                .pointerInput(days) {
                    detectTapGestures { pos ->
                        val idx = (pos.x / (size.width / days.size.toFloat())).toInt().coerceIn(0, days.lastIndex)
                        selected = if (selected == idx) -1 else idx
                        haptics.tick()
                    }
                },
        ) {
            val n = days.size
            val slot = size.width / n
            val barWidth = (slot * if (n > 10) 0.58f else 0.46f).coerceAtMost(28.dp.toPx())
            val goalY = size.height * (1f - goal / max)
            days.forEachIndexed { i, d ->
                val h = (size.height * (d.totalMl / max) * grow.value).coerceAtLeast(if (d.totalMl > 0) 4.dp.toPx() else 0f)
                val left = i * slot + (slot - barWidth) / 2f
                if (h > 0f) {
                    val reached = GoalCalculator.isGoalReached(d.totalMl, goal)
                    val alpha = if (selected == -1 || selected == i) 1f else 0.35f
                    val corner = CornerRadius(barWidth / 2.5f, barWidth / 2.5f)
                    drawRoundRect(
                        color = if (reached) MizuColors.Water else MizuColors.Aqua,
                        topLeft = Offset(left, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = corner,
                        alpha = alpha,
                    )
                    drawRoundRect(
                        color = MizuColors.Ink,
                        topLeft = Offset(left, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = corner,
                        alpha = alpha,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()),
                    )
                }
            }
            drawLine(MizuColors.Ink, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2.dp.toPx())
            drawLine(
                color = MizuColors.WaterDeep,
                start = Offset(0f, goalY),
                end = Offset(size.width, goalY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            days.forEachIndexed { i, d ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val show = weekLabels || i % 5 == 0 || i == days.lastIndex
                    if (show) {
                        Text(
                            if (weekLabels) d.date.format(DateTimeFormatter.ofPattern("EEEEE", locale)) else d.date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (i == selected) MizuColors.WaterDeep else MizuColors.InkFaint,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

