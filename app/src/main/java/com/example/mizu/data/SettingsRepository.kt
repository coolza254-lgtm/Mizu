package com.example.mizu.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mizu.core.AppLanguage
import com.example.mizu.core.GoalMode
import com.example.mizu.core.MizuSettings
import com.example.mizu.util.toEpochMs
import com.example.mizu.util.toLocalDateTime
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "mizu_settings")

/** When the last reminder fired and until when a snoozed reminder is postponed. */
data class ReminderState(val lastReminderAt: LocalDateTime?, val snoozeUntil: LocalDateTime?)

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val goal = intPreferencesKey("daily_goal_ml")
        val goalMode = stringPreferencesKey("goal_mode")
        val weight = intPreferencesKey("weight_kg")
        val quick = stringPreferencesKey("quick_sizes")
        val emptyWeight = intPreferencesKey("default_empty_weight_g")
        val start = intPreferencesKey("reminder_start_min")
        val end = intPreferencesKey("reminder_end_min")
        val snooze = intPreferencesKey("snooze_min")
        val language = stringPreferencesKey("language")
        val remindersEnabled = booleanPreferencesKey("reminders_enabled")
        val haptics = booleanPreferencesKey("haptics_enabled")
        val lastReminder = longPreferencesKey("last_reminder_ms")
        val snoozeUntil = longPreferencesKey("snooze_until_ms")
        val notifPrompted = booleanPreferencesKey("notification_prompted")
    }

    val settings: Flow<MizuSettings> = store.data.map { it.toSettings() }

    val reminderState: Flow<ReminderState> = store.data.map {
        ReminderState(
            lastReminderAt = it[Keys.lastReminder]?.toLocalDateTime(),
            snoozeUntil = it[Keys.snoozeUntil]?.toLocalDateTime(),
        )
    }

    val notificationPrompted: Flow<Boolean> = store.data.map { it[Keys.notifPrompted] ?: false }

    suspend fun update(transform: (MizuSettings) -> MizuSettings) {
        store.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.goal] = next.dailyGoalMl
            prefs[Keys.goalMode] = next.goalMode.name
            prefs[Keys.weight] = next.weightKg
            prefs[Keys.quick] = next.quickAddSizes.joinToString(",")
            prefs[Keys.emptyWeight] = next.defaultEmptyWeightG
            prefs[Keys.start] = next.reminderStart.toSecondOfDay() / 60
            prefs[Keys.end] = next.reminderEnd.toSecondOfDay() / 60
            prefs[Keys.snooze] = next.snoozeMinutes
            prefs[Keys.language] = next.language.name
            prefs[Keys.remindersEnabled] = next.remindersEnabled
            prefs[Keys.haptics] = next.hapticsEnabled
        }
    }

    suspend fun setLastReminder(at: LocalDateTime?) {
        store.edit { if (at == null) it.remove(Keys.lastReminder) else it[Keys.lastReminder] = at.toEpochMs() }
    }

    suspend fun setSnoozeUntil(at: LocalDateTime?) {
        store.edit { if (at == null) it.remove(Keys.snoozeUntil) else it[Keys.snoozeUntil] = at.toEpochMs() }
    }

    suspend fun snooze(now: LocalDateTime, until: LocalDateTime) {
        store.edit {
            it[Keys.lastReminder] = now.toEpochMs()
            it[Keys.snoozeUntil] = until.toEpochMs()
        }
    }

    suspend fun markNotificationPrompted() {
        store.edit { it[Keys.notifPrompted] = true }
    }

    private fun Preferences.toSettings(): MizuSettings {
        val d = MizuSettings()
        return MizuSettings(
            dailyGoalMl = this[Keys.goal] ?: d.dailyGoalMl,
            goalMode = this[Keys.goalMode]?.let { runCatching { GoalMode.valueOf(it) }.getOrNull() } ?: d.goalMode,
            weightKg = this[Keys.weight] ?: d.weightKg,
            quickAddSizes = this[Keys.quick]?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
                ?.takeIf { it.isNotEmpty() } ?: d.quickAddSizes,
            defaultEmptyWeightG = this[Keys.emptyWeight] ?: d.defaultEmptyWeightG,
            reminderStart = this[Keys.start]?.let { LocalTime.of(it / 60, it % 60) } ?: d.reminderStart,
            reminderEnd = this[Keys.end]?.let { LocalTime.of(it / 60, it % 60) } ?: d.reminderEnd,
            snoozeMinutes = this[Keys.snooze] ?: d.snoozeMinutes,
            language = this[Keys.language]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() } ?: d.language,
            remindersEnabled = this[Keys.remindersEnabled] ?: d.remindersEnabled,
            hapticsEnabled = this[Keys.haptics] ?: d.hapticsEnabled,
        )
    }
}
