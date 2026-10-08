package com.example.mizu.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import com.example.mizu.BuildConfig
import com.example.mizu.R
import com.example.mizu.core.AppLanguage
import com.example.mizu.core.Bottle
import com.example.mizu.core.GoalMode
import com.example.mizu.update.UpdateState
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MizuViewModel, onExport: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val bottles by vm.bottles.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val s = settings ?: return

    var editingBottle by remember { mutableStateOf<Bottle?>(null) }
    var timeTarget by remember { mutableStateOf<TimeTarget?>(null) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineSmall)

        // ---- language ----
        SectionTitle(stringResource(R.string.language))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(AppLanguage.TH to "ไทย", AppLanguage.EN to "English", AppLanguage.JA to "日本語").forEach { (lang, label) ->
                FilterChip(
                    selected = s.language == lang,
                    onClick = { vm.updateSettings { it.copy(language = lang) } },
                    label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.heightIn(min = 56.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                )
            }
        }

        // ---- goal ----
        SectionTitle(stringResource(R.string.daily_goal))
        SoftCard {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = s.goalMode == GoalMode.RECOMMENDED,
                    onClick = { vm.updateSettings { it.copy(goalMode = GoalMode.RECOMMENDED) } },
                    label = { Text(stringResource(R.string.goal_recommended), style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.heightIn(min = 56.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                )
                FilterChip(
                    selected = s.goalMode == GoalMode.MANUAL,
                    onClick = { vm.updateSettings { it.copy(goalMode = GoalMode.MANUAL) } },
                    label = { Text(stringResource(R.string.goal_manual), style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.heightIn(min = 56.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                )
            }
            if (s.goalMode == GoalMode.RECOMMENDED) {
                SettingNumber(stringResource(R.string.weight_kg), s.weightKg, 20..300, stringResource(R.string.unit_kg)) { v ->
                    vm.updateSettings { it.copy(weightKg = v) }
                }
                Text(stringResource(R.string.recommended_goal_is, s.goalMl), style = MaterialTheme.typography.bodyLarge)
            } else {
                SettingNumber(stringResource(R.string.daily_goal), s.dailyGoalMl, 200..10_000, stringResource(R.string.unit_ml)) { v ->
                    vm.updateSettings { it.copy(dailyGoalMl = v) }
                }
            }
        }

        // ---- quick add ----
        SectionTitle(stringResource(R.string.quick_add_sizes))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            for (i in 0 until 3) {
                SettingNumber(
                    label = "${i + 1}",
                    value = s.quickAddSizes.getOrElse(i) { 250 },
                    range = 10..2000,
                    suffix = null,
                    modifier = Modifier.weight(1f),
                ) { v ->
                    vm.updateSettings { cur ->
                        val list = cur.quickAddSizes.toMutableList()
                        while (list.size < 3) list += 250
                        list[i] = v
                        cur.copy(quickAddSizes = list)
                    }
                }
            }
        }

        // ---- reminders ----
        SectionTitle(stringResource(R.string.reminders))
        SoftCard {
            Row(Modifier.fillMaxWidth().heightIn(min = MinTouch), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reminders_enabled), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = s.remindersEnabled, onCheckedChange = { on -> vm.updateSettings { it.copy(remindersEnabled = on) } })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                BigOutlinedButton(
                    "${stringResource(R.string.reminder_start)}  ${s.reminderStart.format(HHMM)}",
                    { timeTarget = TimeTarget.START }, Modifier.weight(1f),
                )
                BigOutlinedButton(
                    "${stringResource(R.string.reminder_end)}  ${s.reminderEnd.format(HHMM)}",
                    { timeTarget = TimeTarget.END }, Modifier.weight(1f),
                )
            }
            if (!s.reminderEnd.isAfter(s.reminderStart)) {
                Text(stringResource(R.string.window_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            SettingNumber(stringResource(R.string.snooze_minutes), s.snoozeMinutes, 1..120, stringResource(R.string.unit_min)) { v ->
                vm.updateSettings { it.copy(snoozeMinutes = v) }
            }
        }

        // ---- bottles ----
        SectionTitle(stringResource(R.string.bottles))
        SoftCard {
            SettingNumber(stringResource(R.string.default_empty_weight), s.defaultEmptyWeightG, 0..5000, stringResource(R.string.unit_g)) { v ->
                vm.updateSettings { it.copy(defaultEmptyWeightG = v) }
            }
            bottles.filter { it.isActive }.forEach { b ->
                Row(Modifier.fillMaxWidth().heightIn(min = MinTouch), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(b.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.empty_weight_used, s.emptyWeightOf(b)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { editingBottle = b }, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.edit)) }
                }
            }
            BigOutlinedButton(stringResource(R.string.add_bottle), { editingBottle = Bottle(name = "") }, Modifier.fillMaxWidth())
        }

        // ---- export ----
        SectionTitle(stringResource(R.string.export_csv))
        BigOutlinedButton(stringResource(R.string.export_csv), onExport, Modifier.fillMaxWidth())

        // ---- updates ----
        SectionTitle(stringResource(R.string.app_update))
        UpdateSection(vm, updateState)

        // ---- about ----
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Image(painterResource(R.drawable.mizu_mascot), contentDescription = null, modifier = Modifier.size(40.dp))
            Text(
                "  ${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    editingBottle?.let { bottle ->
        BottleDialog(
            bottle = bottle,
            defaultEmpty = s.defaultEmptyWeightG,
            canArchive = bottle.id != 0L && bottles.count { it.isActive } > 1,
            onSave = { vm.saveBottle(it); editingBottle = null },
            onArchive = { vm.archiveBottle(bottle.id); editingBottle = null },
            onDismiss = { editingBottle = null },
        )
    }
    timeTarget?.let { target ->
        TimeDialog(
            initial = if (target == TimeTarget.START) s.reminderStart else s.reminderEnd,
            onConfirm = { t ->
                vm.updateSettings { if (target == TimeTarget.START) it.copy(reminderStart = t) else it.copy(reminderEnd = t) }
                timeTarget = null
            },
            onDismiss = { timeTarget = null },
        )
    }
}

private enum class TimeTarget { START, END }

/** Number field that commits to settings only while the text is a valid value inside [range]. */
@Composable
private fun SettingNumber(
    label: String,
    value: Int,
    range: IntRange,
    suffix: String?,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onCommit: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val parsed = text.toIntOrNull()
    NumberField(
        label = label,
        value = text,
        onValueChange = { new ->
            text = new
            new.toIntOrNull()?.takeIf { it in range }?.let(onCommit)
        },
        suffix = suffix,
        maxDigits = range.last.toString().length,
        isError = parsed == null || parsed !in range,
        modifier = modifier,
    )
}

@Composable
private fun BottleDialog(
    bottle: Bottle,
    defaultEmpty: Int,
    canArchive: Boolean,
    onSave: (Bottle) -> Unit,
    onArchive: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(bottle.name) }
    var emptyText by remember { mutableStateOf(bottle.emptyWeightG?.toString() ?: "") }
    val empty = emptyText.toIntOrNull()
    val emptyOk = emptyText.isEmpty() || (empty != null && empty in 0..5000)
    val valid = name.isNotBlank() && emptyOk
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text(stringResource(if (bottle.id == 0L) R.string.add_bottle else R.string.edit_bottle)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.bottle_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                NumberField(
                    label = stringResource(R.string.bottle_empty_weight, defaultEmpty),
                    value = emptyText,
                    onValueChange = { emptyText = it },
                    suffix = stringResource(R.string.unit_g),
                    maxDigits = 4,
                    isError = !emptyOk,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(bottle.copy(name = name.trim(), emptyWeightG = empty)) },
                enabled = valid,
                modifier = Modifier.heightIn(min = MinTouch),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (canArchive) {
                    TextButton(onClick = onArchive, modifier = Modifier.heightIn(min = MinTouch)) {
                        Text(stringResource(R.string.archive_bottle), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.background,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }, modifier = Modifier.heightIn(min = MinTouch)) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouch)) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun UpdateSection(vm: MizuViewModel, state: UpdateState) {
    SoftCard {
        Text(
            stringResource(R.string.current_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyLarge,
        )
        when (state) {
            UpdateState.Idle -> BigButton(stringResource(R.string.check_update), vm::checkForUpdate, Modifier.fillMaxWidth())
            UpdateState.Checking -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.checking_update), style = MaterialTheme.typography.bodyMedium)
            }
            UpdateState.UpToDate -> {
                Text(stringResource(R.string.up_to_date), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                BigOutlinedButton(stringResource(R.string.check_update), vm::checkForUpdate, Modifier.fillMaxWidth())
            }
            is UpdateState.Available -> {
                val mb = state.info.sizeBytes / 1_048_576f
                Text(
                    stringResource(R.string.update_available, state.info.versionName) + if (mb > 0) "  (%.1f MB)".format(mb) else "",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.info.notes.isNotBlank()) Text(state.info.notes.take(600), style = MaterialTheme.typography.bodyMedium)
                BigButton(stringResource(R.string.download_update), vm::downloadUpdate, Modifier.fillMaxWidth())
            }
            is UpdateState.Downloading -> {
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.downloading, (state.progress * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
            }
            is UpdateState.ReadyToInstall -> {
                Text(stringResource(R.string.download_done, state.info.versionName), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.install_hint), style = MaterialTheme.typography.bodyMedium)
                BigButton(stringResource(R.string.install_update), vm::installUpdate, Modifier.fillMaxWidth())
            }
            is UpdateState.Failed -> {
                Text(stringResource(R.string.update_failed, state.message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                BigOutlinedButton(stringResource(R.string.try_again), vm::checkForUpdate, Modifier.fillMaxWidth())
            }
        }
    }
}

