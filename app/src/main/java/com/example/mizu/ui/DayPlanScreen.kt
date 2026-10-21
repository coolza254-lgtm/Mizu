package com.example.mizu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mizu.R
import com.example.mizu.core.DayPlan
import com.example.mizu.core.MizuSettings
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * First open of the day: when did you wake up, when do you expect to sleep. Today's reminder window and pace
 * are planned from it. It must be answered (back does nothing) and covers the app until then.
 */
@Composable
fun DayPlanScreen(settings: MizuSettings, now: LocalDateTime, onSave: (wake: LocalTime, bed: LocalTime) -> Unit) {
    var wake by remember { mutableStateOf(defaultWake(now, settings)) }
    var bed by remember { mutableStateOf(settings.bedTime) }
    var editing by remember { mutableStateOf<Int?>(null) }
    BackHandler { }

    val plan = DayPlan.apply(settings, now.toLocalDate(), wake, bed)
    val hours = Duration.between(plan.reminderStart, plan.reminderEnd).toMinutes() / 60.0
    val perHour = if (hours > 0) (settings.goalMl / hours / 10).roundToInt() * 10 else settings.goalMl

    Column(
        Modifier
            .fillMaxSize()
            .background(MizuColors.Paper)
            // Swallow taps so nothing underneath reacts while the plan is open.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Image(
            painterResource(R.drawable.mizu_mascot),
            contentDescription = null,
            modifier = Modifier.size(76.dp).align(Alignment.CenterHorizontally),
        )
        FitText(stringResource(R.string.plan_title), MaterialTheme.typography.headlineMedium, MizuColors.Ink, Modifier.align(Alignment.CenterHorizontally))
        Text(
            stringResource(R.string.plan_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MizuColors.InkSoft,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        TimeTile(Icons.Rounded.WbSunny, stringResource(R.string.plan_wake), wake) { editing = 0 }
        TimeTile(Icons.Rounded.Bedtime, stringResource(R.string.plan_bed), bed) { editing = 1 }
        Column(
            Modifier.fillMaxWidth().background(MizuColors.Foam, MaterialTheme.shapes.medium).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.plan_window, plan.reminderStart.format(TIME_FORMAT), plan.reminderEnd.format(TIME_FORMAT)),
                style = MaterialTheme.typography.titleMedium,
                color = MizuColors.WaterDeep,
            )
            Text(
                stringResource(R.string.plan_pace, settings.goalMl.grouped(), perHour.grouped()),
                style = MaterialTheme.typography.bodyMedium,
                color = MizuColors.InkSoft,
            )
        }
        PrimaryButton(stringResource(R.string.plan_start), { onSave(wake, bed) }, Modifier.fillMaxWidth(), haptic = HapticKind.SUCCESS)
    }

    when (editing) {
        0 -> TimeDialog(wake, onConfirm = { wake = it; editing = null }, onDismiss = { editing = null })
        1 -> TimeDialog(bed, onConfirm = { bed = it; editing = null }, onDismiss = { editing = null })
    }
}

@Composable
private fun TimeTile(icon: ImageVector, label: String, time: LocalTime, onClick: () -> Unit) {
    GlassCard(onClick = onClick, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, size = 44.dp)
            FitText(label, MaterialTheme.typography.bodyLarge, MizuColors.InkSoft, Modifier.weight(1f).padding(horizontal = 14.dp))
            Text(time.format(TIME_FORMAT), style = MaterialTheme.typography.headlineMedium, color = MizuColors.Ink)
        }
    }
}

/** Opening the app in the morning: probably just woke up (rounded down to 5 min). Later: yesterday's start. */
private fun defaultWake(now: LocalDateTime, settings: MizuSettings): LocalTime =
    if (now.hour in 4..12) LocalTime.of(now.hour, now.minute / 5 * 5) else settings.reminderStart
