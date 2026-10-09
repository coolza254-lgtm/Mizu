package com.example.mizu.core

import java.time.LocalDateTime
import java.time.LocalTime

enum class DrinkSource { QUICK, WEIGH, MANUAL }

enum class GoalMode { MANUAL, RECOMMENDED }

/** ADAPTIVE: the further behind, the more often. FIXED: every [MizuSettings.baseIntervalMin] minutes. */
enum class ReminderMode { ADAPTIVE, FIXED }

/** In-app type families (Thai + Latin; Japanese falls back to the system Noto font). */
enum class AppFont { EDITORIAL, PLEX, PROMPT, SARABUN, MALI }

enum class AppLanguage(val tag: String) {
    TH("th"),
    EN("en"),
    JA("ja"),
}

/**
 * [emptyWeightG] null = use [MizuSettings.defaultEmptyWeightG]. [currentWaterG] null = never weighed.
 * [capacityMl] null = unknown (learned from the fullest fill). [waterUpdatedAt] = when [currentWaterG] was last set.
 */
data class Bottle(
    val id: Long = 0,
    val name: String,
    val emptyWeightG: Int? = null,
    val currentWaterG: Int? = null,
    val isActive: Boolean = true,
    val capacityMl: Int? = null,
    val waterUpdatedAt: LocalDateTime? = null,
)

data class DrinkLog(
    val id: Long = 0,
    val timestamp: LocalDateTime,
    val amountMl: Int,
    val source: DrinkSource,
    val bottleId: Long? = null,
    val weightBeforeG: Int? = null,
    val weightAfterG: Int? = null,
    /** For weighed drinks: start of the period the water was drunk in ([timestamp] is its estimated midpoint). */
    val estimatedFrom: LocalDateTime? = null,
)

data class MizuSettings(
    val dailyGoalMl: Int = 2000,
    val goalMode: GoalMode = GoalMode.RECOMMENDED,
    val weightKg: Int = 60,
    val quickAddSizes: List<Int> = listOf(150, 250, 500),
    val defaultEmptyWeightG: Int = 200,
    val reminderStart: LocalTime = LocalTime.of(8, 0),
    val reminderEnd: LocalTime = LocalTime.of(22, 0),
    val snoozeMinutes: Int = 10,
    val language: AppLanguage = AppLanguage.TH,
    val remindersEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val font: AppFont = AppFont.EDITORIAL,
    // ---- reminder tuning ----
    val reminderMode: ReminderMode = ReminderMode.ADAPTIVE,
    /** Interval when on track (and the only interval in FIXED mode). */
    val baseIntervalMin: Int = 60,
    /** Shortest interval, reached when the deficit is [escalationMl] or more. */
    val minIntervalMin: Int = 15,
    val escalationMl: Int = 600,
    /** Deficit at which reminders become strong (heads-up + strong vibration). */
    val strongDeficitMl: Int = 400,
    /** Minutes without a drink (inside the window) after which reminders become strong. */
    val dryMinutes: Int = 120,
    /** Full-screen, alarm-style reminder when the deficit reaches [alarmDeficitMl]. */
    val alarmEnabled: Boolean = true,
    val alarmDeficitMl: Int = 800,
) {
    /** The goal actually in effect for the chosen [goalMode]. */
    val goalMl: Int
        get() = when (goalMode) {
            GoalMode.MANUAL -> dailyGoalMl
            GoalMode.RECOMMENDED -> GoalCalculator.recommendedGoalMl(weightKg)
        }

    fun emptyWeightOf(bottle: Bottle): Int = bottle.emptyWeightG ?: defaultEmptyWeightG
}
