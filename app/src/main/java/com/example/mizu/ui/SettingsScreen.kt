@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mizu.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mizu.BuildConfig
import com.example.mizu.R
import com.example.mizu.core.AppFont
import com.example.mizu.core.AppLanguage
import com.example.mizu.core.ReminderLevel
import com.example.mizu.core.ReminderMode
import com.example.mizu.reminder.NotificationHelper
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.NotificationImportant
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Warning
import com.example.mizu.core.Bottle
import com.example.mizu.core.GoalMode
import com.example.mizu.core.MizuSettings
import com.example.mizu.update.UpdateState
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

/** Which number is being edited in the shared number sheet. */
private enum class NumberField {
    WEIGHT, GOAL, QUICK_1, QUICK_2, QUICK_3, SNOOZE, EMPTY_WEIGHT,
    BASE_INTERVAL, MIN_INTERVAL, ESCALATION, STRONG_DEFICIT, DRY_MINUTES, ALARM_DEFICIT,
}

private enum class TimeTarget { START, END }

private fun languageLabel(l: AppLanguage) = when (l) {
    AppLanguage.TH -> "ไทย"
    AppLanguage.EN -> "English"
    AppLanguage.JA -> "日本語"
}

@Composable
fun SettingsScreen(vm: MizuViewModel, onExport: () -> Unit, bottomPadding: Dp) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val bottles by vm.bottles.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val s = settings ?: return
    val ml = stringResource(R.string.unit_ml)
    val g = stringResource(R.string.unit_g)

    var editing by remember { mutableStateOf<NumberField?>(null) }
    var editingBottle by remember { mutableStateOf<Bottle?>(null) }
    var timeTarget by remember { mutableStateOf<TimeTarget?>(null) }
    var pickLanguage by remember { mutableStateOf(false) }
    var pickFont by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var resumeTick by remember { mutableStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val fullScreenAllowed = remember(resumeTick) { NotificationHelper.canUseFullScreen(context) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            MonoKicker("mizu / settings")
            Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.settings_kicker), style = MaterialTheme.typography.bodyMedium, color = MizuColors.InkSoft)
        }

        ProfileCard(s)

        // ---- goal ----
        SectionLabel(stringResource(R.string.daily_goal))
        SettingsGroup {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                PillSelector(
                    options = listOf(
                        GoalMode.RECOMMENDED to stringResource(R.string.goal_recommended),
                        GoalMode.MANUAL to stringResource(R.string.goal_manual),
                    ),
                    selected = s.goalMode,
                    onSelect = { mode -> vm.updateSettings { it.copy(goalMode = mode) } },
                )
            }
            if (s.goalMode == GoalMode.RECOMMENDED) {
                SettingsRow(
                    Icons.Rounded.MonitorWeight, stringResource(R.string.weight_kg),
                    subtitle = stringResource(R.string.recommended_goal_is, s.goalMl.grouped()),
                    value = "${s.weightKg} ${stringResource(R.string.unit_kg)}",
                    onClick = { editing = NumberField.WEIGHT },
                )
            } else {
                SettingsRow(
                    Icons.Rounded.Flag, stringResource(R.string.daily_goal),
                    value = "${s.dailyGoalMl.grouped()} $ml",
                    onClick = { editing = NumberField.GOAL },
                )
            }
        }

        // ---- reminders ----
        SectionLabel(stringResource(R.string.reminders))
        SettingsGroup {
            SettingsRow(
                Icons.Rounded.Notifications, stringResource(R.string.reminders_enabled),
                subtitle = stringResource(R.string.reminders_subtitle),
                trailing = { MizuSwitch(s.remindersEnabled) { on -> vm.updateSettings { it.copy(remindersEnabled = on) } } },
            )
            RowDivider()
            SettingsRow(Icons.Rounded.WbTwilight, stringResource(R.string.reminder_start), value = s.reminderStart.format(HHMM), onClick = { timeTarget = TimeTarget.START })
            RowDivider()
            SettingsRow(Icons.Rounded.Schedule, stringResource(R.string.reminder_end), value = s.reminderEnd.format(HHMM), onClick = { timeTarget = TimeTarget.END })
            if (!s.reminderEnd.isAfter(s.reminderStart)) {
                Text(
                    stringResource(R.string.window_invalid),
                    color = MizuColors.Danger,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        SectionLabel(stringResource(R.string.reminder_rhythm))
        SettingsGroup {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                PillSelector(
                    options = listOf(
                        ReminderMode.ADAPTIVE to stringResource(R.string.mode_adaptive),
                        ReminderMode.FIXED to stringResource(R.string.mode_fixed),
                    ),
                    selected = s.reminderMode,
                    onSelect = { mode -> vm.updateSettings { it.copy(reminderMode = mode) } },
                )
            }
            Text(
                stringResource(if (s.reminderMode == ReminderMode.ADAPTIVE) R.string.mode_adaptive_hint else R.string.mode_fixed_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MizuColors.InkSoft,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SettingsRow(
                Icons.Rounded.Schedule, stringResource(if (s.reminderMode == ReminderMode.ADAPTIVE) R.string.base_interval else R.string.fixed_interval),
                value = "${s.baseIntervalMin} ${stringResource(R.string.unit_min)}",
                onClick = { editing = NumberField.BASE_INTERVAL },
            )
            if (s.reminderMode == ReminderMode.ADAPTIVE) {
                RowDivider()
                SettingsRow(
                    Icons.Rounded.Speed, stringResource(R.string.min_interval),
                    value = "${s.minIntervalMin} ${stringResource(R.string.unit_min)}",
                    onClick = { editing = NumberField.MIN_INTERVAL },
                )
                RowDivider()
                SettingsRow(
                    Icons.Rounded.TrendingDown, stringResource(R.string.escalation_at),
                    value = "${s.escalationMl.grouped()} $ml",
                    onClick = { editing = NumberField.ESCALATION },
                )
            }
            RowDivider()
            SettingsRow(
                Icons.Rounded.Snooze, stringResource(R.string.snooze_minutes),
                value = "${s.snoozeMinutes} ${stringResource(R.string.unit_min)}",
                onClick = { editing = NumberField.SNOOZE },
            )
        }

        SectionLabel(stringResource(R.string.reminder_strength))
        SettingsGroup {
            SettingsRow(
                Icons.Rounded.Vibration, stringResource(R.string.strong_at),
                subtitle = stringResource(R.string.strong_hint),
                value = "${s.strongDeficitMl.grouped()} $ml",
                onClick = { editing = NumberField.STRONG_DEFICIT },
            )
            RowDivider()
            SettingsRow(
                Icons.Rounded.HourglassBottom, stringResource(R.string.dry_after),
                subtitle = stringResource(R.string.dry_hint),
                value = "${s.dryMinutes} ${stringResource(R.string.unit_min)}",
                onClick = { editing = NumberField.DRY_MINUTES },
            )
            RowDivider()
            SettingsRow(
                Icons.Rounded.Alarm, stringResource(R.string.alarm_mode),
                subtitle = stringResource(R.string.alarm_mode_hint),
                trailing = { MizuSwitch(s.alarmEnabled) { on -> vm.updateSettings { it.copy(alarmEnabled = on) } } },
            )
            if (s.alarmEnabled) {
                RowDivider()
                SettingsRow(
                    Icons.Rounded.NotificationImportant, stringResource(R.string.alarm_at),
                    value = "${s.alarmDeficitMl.grouped()} $ml",
                    onClick = { editing = NumberField.ALARM_DEFICIT },
                )
                if (!fullScreenAllowed) {
                    RowDivider()
                    SettingsRow(
                        Icons.Rounded.Warning, stringResource(R.string.alarm_permission),
                        subtitle = stringResource(R.string.alarm_permission_hint),
                        onClick = { openFullScreenSettings(context) },
                    )
                }
            }
            RowDivider()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.test_reminder), style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton(stringResource(R.string.level_gentle), { vm.testReminder(ReminderLevel.GENTLE) }, Modifier.weight(1f), haptic = HapticKind.TICK)
                    SoftButton(stringResource(R.string.level_strong), { vm.testReminder(ReminderLevel.STRONG) }, Modifier.weight(1f), haptic = HapticKind.CLICK)
                    SoftButton(stringResource(R.string.level_alarm), { vm.testReminder(ReminderLevel.ALARM) }, Modifier.weight(1f), haptic = HapticKind.HEAVY)
                }
            }
        }

        // ---- bottles ----
        SectionLabel(stringResource(R.string.bottles))
        SettingsGroup {
            SettingsRow(
                Icons.Rounded.Inventory2, stringResource(R.string.default_empty_weight),
                value = "${s.defaultEmptyWeightG.grouped()} $g",
                onClick = { editing = NumberField.EMPTY_WEIGHT },
            )
            bottles.filter { it.isActive }.forEach { b ->
                RowDivider()
                SettingsRow(
                    Icons.Rounded.WaterDrop, b.name,
                    subtitle = stringResource(R.string.empty_weight_used, s.emptyWeightOf(b).grouped()) +
                        (b.capacityMl?.let { " · " + stringResource(R.string.capacity_short, it.grouped()) } ?: ""),
                    onClick = { editingBottle = b },
                )
            }
            RowDivider()
            SettingsRow(Icons.Rounded.Add, stringResource(R.string.add_bottle), onClick = { editingBottle = Bottle(name = "") })
        }

        // ---- general ----
        SectionLabel(stringResource(R.string.general))
        SettingsGroup {
            SettingsRow(Icons.Rounded.Language, stringResource(R.string.language), value = languageLabel(s.language), onClick = { pickLanguage = true })
            RowDivider()
            SettingsRow(
                Icons.Rounded.Vibration, stringResource(R.string.haptics),
                subtitle = stringResource(R.string.haptics_subtitle),
                trailing = { MizuSwitch(s.hapticsEnabled) { on -> vm.updateSettings { it.copy(hapticsEnabled = on) } } },
            )
            RowDivider()
            SettingsRow(
                Icons.Rounded.VolumeUp, stringResource(R.string.sounds),
                subtitle = stringResource(R.string.sounds_subtitle),
                trailing = { MizuSwitch(s.soundEnabled) { on -> vm.updateSettings { it.copy(soundEnabled = on) } } },
            )
            RowDivider()
            SettingsRow(Icons.Rounded.TextFields, stringResource(R.string.font), value = fontLabel(s.font), onClick = { pickFont = true })
            RowDivider()
            SettingsRow(Icons.Rounded.FileDownload, stringResource(R.string.export_csv), onClick = onExport)
        }

        // ---- updates ----
        SectionLabel(stringResource(R.string.app_update))
        UpdateCard(vm, updateState)

        Text(
            "Mizu ${BuildConfig.VERSION_NAME} · ${stringResource(R.string.made_with_care)}",
            style = MaterialTheme.typography.labelSmall,
            color = MizuColors.InkFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }

    editing?.let { field -> SettingNumberSheet(field, s, ml, g, vm) { editing = null } }

    editingBottle?.let { bottle ->
        BottleSheet(
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
    if (pickFont) {
        FontSheet(
            current = s.font,
            onPick = { f -> vm.updateSettings { it.copy(font = f) }; pickFont = false },
            onDismiss = { pickFont = false },
        )
    }
    if (pickLanguage) {
        val haptics = LocalHaptics.current
        MizuSheet(onDismiss = { pickLanguage = false }) {
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            AppLanguage.entries.forEach { lang ->
                val selected = lang == s.language
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) MizuColors.Water else Color.White)
                        .border(if (selected) 2.dp else 0.dp, if (selected) MizuColors.WaterDeep else Color.Transparent, RoundedCornerShape(20.dp))
                        .bouncyClick(haptic = HapticKind.TICK) {
                            vm.updateSettings { it.copy(language = lang) }
                            haptics.success()
                            pickLanguage = false
                        }
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(languageLabel(lang), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (selected) Icon(Icons.Rounded.Check, null, tint = MizuColors.WaterDeep)
                }
            }
        }
    }
}

private fun fontLabel(f: AppFont) = when (f) {
    AppFont.EDITORIAL -> "Editorial"
    AppFont.PLEX -> "IBM Plex Sans Thai"
    AppFont.PROMPT -> "Prompt"
    AppFont.SARABUN -> "Sarabun"
    AppFont.MALI -> "Mali"
}

private fun openFullScreenSettings(context: android.content.Context) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, android.net.Uri.parse("package:${context.packageName}"))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Font picker: each option previews itself in Thai and English. */
@Composable
private fun FontSheet(current: AppFont, onPick: (AppFont) -> Unit, onDismiss: () -> Unit) {
    MizuSheet(onDismiss) {
        Text(stringResource(R.string.font), style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        AppFont.entries.forEach { f ->
            val selected = f == current
            val (display, body) = f.families()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (selected) MizuColors.Water else Color.White)
                    .border(if (selected) 2.dp else 0.dp, if (selected) MizuColors.WaterDeep else Color.Transparent, RoundedCornerShape(18.dp))
                    .bouncyClick(haptic = HapticKind.TICK) { onPick(f) }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(fontLabel(f), style = MonoLabel, color = MizuColors.InkSoft)
                    Text("ดื่มน้ำ 2,100 ml", style = MaterialTheme.typography.titleLarge.copy(fontFamily = display))
                    Text("สดชื่นทั้งวัน · Stay hydrated", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = body))
                }
                if (selected) Icon(Icons.Rounded.Check, null, tint = MizuColors.Ink)
            }
        }
    }
}

@Composable
private fun ProfileCard(s: MizuSettings) {
    GlassCard(background = Color.White) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(MizuColors.Foam),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.mizu_mascot), contentDescription = null, modifier = Modifier.size(54.dp))
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(stringResource(R.string.todays_goal), style = MaterialTheme.typography.labelMedium, color = MizuColors.InkSoft)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(s.goalMl.grouped(), style = MaterialTheme.typography.headlineSmall)
                    Text(" ${stringResource(R.string.unit_ml)}", style = MaterialTheme.typography.bodyMedium, color = MizuColors.InkSoft, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(
                    stringResource(R.string.window_summary, s.reminderStart.format(HHMM), s.reminderEnd.format(HHMM)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MizuColors.InkSoft,
                )
            }
        }
    }
}

@Composable
private fun SettingNumberSheet(field: NumberField, s: MizuSettings, ml: String, g: String, vm: MizuViewModel, close: () -> Unit) {
    val quickIndex = when (field) {
        NumberField.QUICK_1 -> 0
        NumberField.QUICK_2 -> 1
        NumberField.QUICK_3 -> 2
        else -> -1
    }
    val (title, initial, range, unit) = when (field) {
        NumberField.WEIGHT -> Quad(stringResource(R.string.weight_kg), s.weightKg, 20..300, stringResource(R.string.unit_kg))
        NumberField.GOAL -> Quad(stringResource(R.string.daily_goal), s.dailyGoalMl, 200..10_000, ml)
        NumberField.SNOOZE -> Quad(stringResource(R.string.snooze_minutes), s.snoozeMinutes, 1..120, stringResource(R.string.unit_min))
        NumberField.EMPTY_WEIGHT -> Quad(stringResource(R.string.default_empty_weight), s.defaultEmptyWeightG, 0..5000, g)
        NumberField.BASE_INTERVAL -> Quad(stringResource(R.string.base_interval), s.baseIntervalMin, 10..240, stringResource(R.string.unit_min))
        NumberField.MIN_INTERVAL -> Quad(stringResource(R.string.min_interval), s.minIntervalMin, 5..120, stringResource(R.string.unit_min))
        NumberField.ESCALATION -> Quad(stringResource(R.string.escalation_at), s.escalationMl, 100..3000, ml)
        NumberField.STRONG_DEFICIT -> Quad(stringResource(R.string.strong_at), s.strongDeficitMl, 100..5000, ml)
        NumberField.DRY_MINUTES -> Quad(stringResource(R.string.dry_after), s.dryMinutes, 30..480, stringResource(R.string.unit_min))
        NumberField.ALARM_DEFICIT -> Quad(stringResource(R.string.alarm_at), s.alarmDeficitMl, 200..5000, ml)
        else -> Quad(stringResource(R.string.quick_button_n, quickIndex + 1), s.quickAddSizes.getOrElse(quickIndex) { 250 }, 10..2000, ml)
    }
    NumberSheet(
        title = title,
        initial = initial,
        range = range,
        unit = unit,
        onDismiss = close,
        onSave = { v ->
            vm.updateSettings { cur ->
                when (field) {
                    NumberField.WEIGHT -> cur.copy(weightKg = v)
                    NumberField.GOAL -> cur.copy(dailyGoalMl = v, goalMode = GoalMode.MANUAL)
                    NumberField.SNOOZE -> cur.copy(snoozeMinutes = v)
                    NumberField.EMPTY_WEIGHT -> cur.copy(defaultEmptyWeightG = v)
                    NumberField.BASE_INTERVAL -> cur.copy(baseIntervalMin = v, minIntervalMin = cur.minIntervalMin.coerceAtMost(v))
                    NumberField.MIN_INTERVAL -> cur.copy(minIntervalMin = v.coerceAtMost(cur.baseIntervalMin))
                    NumberField.ESCALATION -> cur.copy(escalationMl = v)
                    NumberField.STRONG_DEFICIT -> cur.copy(strongDeficitMl = v)
                    NumberField.DRY_MINUTES -> cur.copy(dryMinutes = v)
                    NumberField.ALARM_DEFICIT -> cur.copy(alarmDeficitMl = v)
                    else -> {
                        val list = cur.quickAddSizes.toMutableList()
                        while (list.size < 3) list += 250
                        list[quickIndex] = v
                        cur.copy(quickAddSizes = list)
                    }
                }
            }
            close()
        },
    )
}

private data class Quad(val title: String, val initial: Int, val range: IntRange, val unit: String)

@Composable
private fun BottleSheet(
    bottle: Bottle,
    defaultEmpty: Int,
    canArchive: Boolean,
    onSave: (Bottle) -> Unit,
    onArchive: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(bottle.name) }
    var emptyText by remember { mutableStateOf(bottle.emptyWeightG?.toString() ?: "") }
    var capacityText by remember { mutableStateOf(bottle.capacityMl?.toString() ?: "") }
    val empty = emptyText.toIntOrNull()
    val emptyOk = emptyText.isEmpty() || (empty != null && empty in 0..5000)
    val capacity = capacityText.toIntOrNull()
    val capacityOk = capacityText.isEmpty() || (capacity != null && capacity in 50..5000)
    val valid = name.isNotBlank() && emptyOk && capacityOk
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MizuColors.Ink,
        unfocusedBorderColor = MizuColors.Ink,
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
    )
    MizuSheet(onDismiss) {
        Text(
            stringResource(if (bottle.id == 0L) R.string.add_bottle else R.string.edit_bottle),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = { Text(stringResource(R.string.bottle_name)) },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = emptyText,
            onValueChange = { raw -> emptyText = raw.filter(Char::isDigit).take(4) },
            label = { Text(stringResource(R.string.bottle_empty_weight, defaultEmpty.grouped())) },
            suffix = { Text(stringResource(R.string.unit_g)) },
            singleLine = true,
            isError = !emptyOk,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(20.dp),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = capacityText,
            onValueChange = { raw -> capacityText = raw.filter(Char::isDigit).take(4) },
            label = { Text(stringResource(R.string.bottle_capacity_label)) },
            suffix = { Text(stringResource(R.string.unit_ml)) },
            singleLine = true,
            isError = !capacityOk,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(20.dp),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (canArchive) SoftButton(stringResource(R.string.archive_bottle), onArchive, color = MizuColors.Danger, haptic = HapticKind.HEAVY)
            PrimaryButton(
                stringResource(R.string.save),
                onClick = { onSave(bottle.copy(name = name.trim(), emptyWeightG = empty, capacityMl = capacity)) },
                enabled = valid,
                modifier = Modifier.weight(1f),
                haptic = HapticKind.SUCCESS,
            )
        }
    }
}

@Composable
private fun TimeDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = Color.White,
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = MizuColors.Mist,
                    selectorColor = MizuColors.WaterDeep,
                    timeSelectorSelectedContainerColor = MizuColors.Foam,
                    timeSelectorUnselectedContainerColor = MizuColors.Mist,
                    timeSelectorSelectedContentColor = MizuColors.Ink,
                ),
            )
        },
        confirmButton = { PrimaryButton(stringResource(R.string.save), { onConfirm(LocalTime.of(state.hour, state.minute)) }, haptic = HapticKind.SUCCESS) },
        dismissButton = { SoftButton(stringResource(R.string.cancel), onDismiss, haptic = HapticKind.TICK) },
    )
}

@Composable
private fun UpdateCard(vm: MizuViewModel, state: UpdateState) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.SystemUpdate)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(stringResource(R.string.current_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.titleMedium)
                val status = when (state) {
                    UpdateState.Idle -> stringResource(R.string.update_idle)
                    UpdateState.Checking -> stringResource(R.string.checking_update)
                    UpdateState.UpToDate -> stringResource(R.string.up_to_date)
                    is UpdateState.Available -> stringResource(R.string.update_available, state.info.versionName)
                    is UpdateState.Downloading -> stringResource(R.string.downloading, (state.progress * 100).toInt())
                    is UpdateState.ReadyToInstall -> stringResource(R.string.download_done, state.info.versionName)
                    is UpdateState.Failed -> stringResource(R.string.update_failed, state.message)
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (state) {
                        is UpdateState.Failed -> MizuColors.Danger
                        is UpdateState.Available, UpdateState.UpToDate -> MizuColors.WaterDeep
                        else -> MizuColors.InkSoft
                    },
                )
            }
        }
        when (state) {
            UpdateState.Checking -> LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape), color = MizuColors.Water, trackColor = MizuColors.Line)
            is UpdateState.Downloading -> LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().clip(CircleShape),
                color = MizuColors.Water,
                trackColor = MizuColors.Line,
            )
            else -> Unit
        }
        if (state is UpdateState.Available && state.info.notes.isNotBlank()) {
            Text(state.info.notes.take(600), style = MaterialTheme.typography.bodyMedium, color = MizuColors.InkSoft)
        }
        if (state is UpdateState.ReadyToInstall) {
            Text(stringResource(R.string.install_hint), style = MaterialTheme.typography.bodyMedium, color = MizuColors.InkSoft)
        }
        when (state) {
            UpdateState.Idle, UpdateState.UpToDate -> SoftButton(stringResource(R.string.check_update), vm::checkForUpdate, Modifier.fillMaxWidth())
            is UpdateState.Failed -> SoftButton(stringResource(R.string.try_again), vm::checkForUpdate, Modifier.fillMaxWidth())
            is UpdateState.Available -> PrimaryButton(stringResource(R.string.download_update), vm::downloadUpdate, Modifier.fillMaxWidth())
            is UpdateState.ReadyToInstall -> PrimaryButton(stringResource(R.string.install_update), vm::installUpdate, Modifier.fillMaxWidth(), haptic = HapticKind.SUCCESS)
            else -> Unit
        }
    }
}

