package com.example.mizu.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mizu.R
import com.example.mizu.core.DrinkLog
import com.example.mizu.core.DrinkSource
import com.example.mizu.core.GoalCalculator
import com.example.mizu.core.ReminderEngine
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: MizuViewModel, onWeigh: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val s = settings ?: return

    val today = GoalCalculator.logsOn(now.toLocalDate(), logs).sortedByDescending { it.timestamp }
    val consumed = today.sumOf { it.amountMl }
    val goal = s.goalMl
    val remaining = GoalCalculator.remainingMl(consumed, goal)
    val reached = GoalCalculator.isGoalReached(consumed, goal)

    var showManual by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DrinkLog?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Image(painterResource(R.drawable.mizu_mascot), contentDescription = null, modifier = Modifier.size(44.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 10.dp))
            }
        }
        item {
            WaterCircle(
                progress = GoalCalculator.progress(consumed, goal),
                primaryText = "$consumed ${stringResource(R.string.unit_ml)}",
                secondaryText = stringResource(R.string.of_goal, goal),
            )
        }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (reached) {
                    Text(stringResource(R.string.goal_reached), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
                } else {
                    Text(stringResource(R.string.remaining_ml, remaining), style = MaterialTheme.typography.titleMedium)
                    val deficit = ReminderEngine.contentAt(now, s, consumed)?.suggestedMl ?: 0
                    Text(stringResource(R.string.should_drink_more, deficit), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                s.quickAddSizes.forEach { size ->
                    AssistChip(
                        onClick = { vm.quickAdd(size) },
                        label = { Text("+$size ${stringResource(R.string.unit_ml)}", style = MaterialTheme.typography.labelLarge) },
                        modifier = Modifier.heightIn(min = 56.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        border = null,
                    )
                }
                AssistChip(
                    onClick = { showManual = true },
                    label = { Text(stringResource(R.string.manual_add), style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.heightIn(min = 56.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                )
            }
        }
        item { BigButton(stringResource(R.string.weigh_button), onWeigh, Modifier.fillMaxWidth()) }
        item { SectionTitle(stringResource(R.string.today_log), Modifier.fillMaxWidth()) }
        if (today.isEmpty()) {
            item { Text(stringResource(R.string.no_logs_today), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(today, key = { it.id }) { log -> LogRow(log, onClick = { editing = log }) }
        }
    }

    if (showManual) {
        AmountDialog(
            title = stringResource(R.string.manual_add),
            initial = null,
            onConfirm = { vm.manualAdd(it); showManual = false },
            onDismiss = { showManual = false },
        )
    }
    editing?.let { log ->
        AmountDialog(
            title = stringResource(R.string.edit_log),
            initial = log.amountMl,
            onConfirm = { vm.editLog(log.id, it); editing = null },
            onDismiss = { editing = null },
            onDelete = { vm.deleteLog(log.id); editing = null },
        )
    }
}

@Composable
fun LogRow(log: DrinkLog, onClick: () -> Unit) {
    val sourceLabel = stringResource(
        when (log.source) {
            DrinkSource.QUICK -> R.string.source_quick
            DrinkSource.WEIGH -> R.string.source_weigh
            DrinkSource.MANUAL -> R.string.source_manual
        },
    )
    SoftCardRow(onClick) {
        Text(log.timestamp.format(TIME_FORMAT), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 16.dp))
        Column(Modifier.weight(1f)) {
            Text("${log.amountMl} ${stringResource(R.string.unit_ml)}", style = MaterialTheme.typography.titleMedium)
            Text(sourceLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.edit), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SoftCardRow(onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .clickable(onClick = onClick),
    ) {
        androidx.compose.material3.Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp).heightIn(min = MinTouch), verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}

val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

