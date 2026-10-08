package com.example.mizu.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mizu.R
import com.example.mizu.core.DaySummary
import com.example.mizu.core.DrinkLog
import com.example.mizu.core.GoalCalculator
import com.example.mizu.core.HistoryCalculator
import com.example.mizu.util.toLocale
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private enum class Range { DAY, WEEK, MONTH }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: MizuViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val s = settings ?: return
    val locale = s.language.toLocale()

    var range by rememberSaveable { mutableStateOf(Range.WEEK) }
    var offset by rememberSaveable { mutableStateOf(0) } // periods back from today (<= 0)
    val today = now.toLocalDate()

    val (from, to) = when (range) {
        Range.DAY -> today.plusDays(offset.toLong()).let { it to it }
        Range.WEEK -> today.plusWeeks(offset.toLong()).let {
            it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) to it.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        }
        Range.MONTH -> today.plusMonths(offset.toLong()).let { it.withDayOfMonth(1) to it.withDayOfMonth(it.lengthOfMonth()) }
    }
    val totals = HistoryCalculator.dailyTotals(logs, from, to)
    val total = totals.sumOf { it.totalMl }
    val daysWithData = totals.count { it.totalMl > 0 }
    val title = when (range) {
        Range.DAY -> from.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
        Range.WEEK -> "${from.format(DateTimeFormatter.ofPattern("d MMM", locale))} – ${to.format(DateTimeFormatter.ofPattern("d MMM", locale))}"
        Range.MONTH -> from.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text(stringResource(R.string.tab_history), style = MaterialTheme.typography.headlineSmall) }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(Range.DAY to R.string.range_day, Range.WEEK to R.string.range_week, Range.MONTH to R.string.range_month)
                    .forEachIndexed { index, (r, label) ->
                        SegmentedButton(
                            selected = range == r,
                            onClick = { range = r; offset = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index, 3),
                            modifier = Modifier.heightIn(min = MinTouch),
                        ) { Text(stringResource(label)) }
                    }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { offset -= 1 }, modifier = Modifier.heightIn(min = MinTouch)) { Text("‹") }
                Text(title, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { offset += 1 }, enabled = offset < 0, modifier = Modifier.heightIn(min = MinTouch)) { Text("›") }
            }
        }
        item {
            SoftCard {
                Text("$total ${stringResource(R.string.unit_ml)}", style = MaterialTheme.typography.headlineSmall)
                if (range != Range.DAY && daysWithData > 0) {
                    Text(stringResource(R.string.daily_average, total / daysWithData), style = MaterialTheme.typography.bodyMedium)
                }
                if (range != Range.DAY) BarChart(totals, s.goalMl, labelEvery = if (range == Range.WEEK) 1 else 5, locale = locale)
            }
        }
        if (range == Range.DAY) {
            val dayLogs = GoalCalculator.logsOn(from, logs).sortedByDescending { it.timestamp }
            if (dayLogs.isEmpty()) {
                item { Text(stringResource(R.string.no_logs_day), style = MaterialTheme.typography.bodyLarge) }
            } else {
                items(dayLogs, key = { it.id }) { HistoryLogRow(it) }
            }
        } else {
            item { SectionTitle(stringResource(R.string.daily_totals)) }
            items(totals.reversed(), key = { it.date.toEpochDay() }) { day -> DayTotalRow(day, s.goalMl, locale) }
        }
    }
}

@Composable
private fun HistoryLogRow(log: DrinkLog) {
    SoftCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(log.timestamp.format(TIME_FORMAT), style = MaterialTheme.typography.titleMedium)
            Text("${log.amountMl} ${stringResource(R.string.unit_ml)}", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun DayTotalRow(day: DaySummary, goal: Int, locale: java.util.Locale) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = MinTouch),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(day.date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)), style = MaterialTheme.typography.bodyLarge)
        val reached = GoalCalculator.isGoalReached(day.totalMl, goal)
        Text(
            "${day.totalMl} ${stringResource(R.string.unit_ml)}" + if (reached) "  ✓" else "",
            style = MaterialTheme.typography.titleMedium,
            color = if (reached) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Plain bars: one per day, with a dashed-looking goal line. */
@Composable
private fun BarChart(days: List<DaySummary>, goal: Int, labelEvery: Int, locale: java.util.Locale) {
    val bar = MaterialTheme.colorScheme.primary
    val reachedBar = MaterialTheme.colorScheme.secondary
    val line = MaterialTheme.colorScheme.outline
    val max = maxOf(goal, days.maxOfOrNull { it.totalMl } ?: 0, 1).toFloat()
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val n = days.size
            val slot = size.width / n
            val barWidth = slot * 0.62f
            days.forEachIndexed { i, d ->
                val h = size.height * (d.totalMl / max)
                drawRoundRect(
                    color = if (GoalCalculator.isGoalReached(d.totalMl, goal)) reachedBar else bar,
                    topLeft = Offset(i * slot + (slot - barWidth) / 2f, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                )
            }
            val y = size.height * (1f - goal / max)
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            days.forEachIndexed { i, d ->
                Box(Modifier.weight(1f)) {
                    if (i % labelEvery == 0) {
                        Text(
                            if (labelEvery == 1) d.date.format(DateTimeFormatter.ofPattern("EEEEE", locale)) else d.date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
