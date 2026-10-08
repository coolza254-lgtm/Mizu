package com.example.mizu.core

import java.time.LocalDateTime
import java.time.LocalTime

enum class DrinkSource { QUICK, WEIGH, MANUAL }

enum class GoalMode { MANUAL, RECOMMENDED }

enum class AppLanguage(val tag: String) {
    TH("th"),
    EN("en"),
    JA("ja"),
}

/** [emptyWeightG] null = use [MizuSettings.defaultEmptyWeightG]. [currentWaterG] null = never weighed. */
data class Bottle(
    val id: Long = 0,
    val name: String,
    val emptyWeightG: Int? = null,
    val currentWaterG: Int? = null,
    val isActive: Boolean = true,
)

data class DrinkLog(
    val id: Long = 0,
    val timestamp: LocalDateTime,
    val amountMl: Int,
    val source: DrinkSource,
    val bottleId: Long? = null,
    val weightBeforeG: Int? = null,
    val weightAfterG: Int? = null,
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
) {
    /** The goal actually in effect for the chosen [goalMode]. */
    val goalMl: Int
        get() = when (goalMode) {
            GoalMode.MANUAL -> dailyGoalMl
            GoalMode.RECOMMENDED -> GoalCalculator.recommendedGoalMl(weightKg)
        }

    fun emptyWeightOf(bottle: Bottle): Int = bottle.emptyWeightG ?: defaultEmptyWeightG
}
