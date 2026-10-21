package com.example.mizu.core

import java.time.LocalDate
import java.time.LocalTime

/**
 * Today's drinking window from the wake-up and expected bed times entered when the app is first opened each day:
 * reminders start at wake-up and stop [QUIET_BEFORE_BED_MIN] before bed, so the goal is not pushed right before sleep.
 */
object DayPlan {
    const val QUIET_BEFORE_BED_MIN = 60L

    /** Shortest window that still makes sense; a later bed time is pushed out to give at least this. */
    const val MIN_WINDOW_MIN = 120L

    fun needsPlan(settings: MizuSettings, today: LocalDate): Boolean = settings.planDate != today

    /**
     * Applies the plan. A bed time at or before wake-up is taken as after midnight; the window then runs to
     * 23:59 at the latest (the engine works within one calendar day).
     */
    fun apply(settings: MizuSettings, today: LocalDate, wake: LocalTime, bed: LocalTime): MizuSettings {
        val wakeMin = wake.toSecondOfDay() / 60L
        val bedMin = (bed.toSecondOfDay() / 60L).let { if (it <= wakeMin) it + 24 * 60 else it }
        val lastMin = 23 * 60L + 59
        val endMin = (bedMin - QUIET_BEFORE_BED_MIN).coerceAtLeast(wakeMin + MIN_WINDOW_MIN).coerceAtMost(lastMin)
        val startMin = wakeMin.coerceAtMost(endMin - 1)
        return settings.copy(
            reminderStart = LocalTime.of((startMin / 60).toInt(), (startMin % 60).toInt()),
            reminderEnd = LocalTime.of((endMin / 60).toInt(), (endMin % 60).toInt()),
            bedTime = bed,
            planDate = today,
        )
    }
}
