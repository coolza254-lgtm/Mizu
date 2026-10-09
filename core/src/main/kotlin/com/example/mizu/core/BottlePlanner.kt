package com.example.mizu.core

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.ceil
import kotlin.math.max

/** What the water left in a bottle means for the rest of the day. */
data class BottleForecast(
    val waterMl: Int,
    /** 0..1 when the capacity is known. */
    val fullness: Float?,
    /** Pace used for the estimate, ml per hour. */
    val rateMlPerHour: Double,
    /** When the bottle is expected to run dry at that pace; null if it lasts past the reminder window or the pace is zero. */
    val emptyAt: LocalDateTime?,
    /** True when the water in the bottle already covers what is left of today's goal. */
    val coversGoal: Boolean,
    /** Further full refills needed today to reach the goal (0 when covered or capacity unknown). */
    val refillsNeeded: Int,
)

object BottlePlanner {
    /**
     * Pace to plan with: the steady rate still needed to reach today's goal within the reminder window;
     * once the goal is met (or the window is over) the actual average pace today, and failing that
     * the plan's average (goal spread over the window).
     */
    fun rateMlPerHour(now: LocalDateTime, settings: MizuSettings, logs: List<DrinkLog>): Double {
        val date = now.toLocalDate()
        val start = date.atTime(settings.reminderStart)
        val end = date.atTime(settings.reminderEnd)
        val windowHours = Duration.between(start, end).toMinutes() / 60.0
        val consumed = GoalCalculator.consumedOn(date, logs)
        val remaining = GoalCalculator.remainingMl(consumed, settings.goalMl)
        val from = if (now.isBefore(start)) start else now
        val hoursLeft = Duration.between(from, end).toMinutes() / 60.0
        if (remaining > 0 && hoursLeft > 0.25) return remaining / hoursLeft
        val elapsed = Duration.between(start, now).toMinutes() / 60.0
        if (consumed > 0 && elapsed > 0.25) return consumed / elapsed
        return if (windowHours > 0) settings.goalMl / windowHours else 0.0
    }

    fun forecast(bottle: Bottle, now: LocalDateTime, settings: MizuSettings, logs: List<DrinkLog>): BottleForecast? {
        val water = bottle.currentWaterG ?: return null
        val capacity = bottle.capacityMl?.takeIf { it > 0 }
        val consumed = GoalCalculator.consumedOn(now.toLocalDate(), logs)
        val remainingGoal = GoalCalculator.remainingMl(consumed, settings.goalMl)
        val rate = rateMlPerHour(now, settings, logs)
        val end = now.toLocalDate().atTime(settings.reminderEnd)

        val emptyAt = if (rate > 0 && water > 0) {
            now.plusMinutes((water / rate * 60).toLong()).takeIf { it.isBefore(end) }
        } else if (water <= 0) {
            now
        } else {
            null
        }
        val shortfall = max(remainingGoal - water, 0)
        val refills = if (capacity != null && shortfall > 0) ceil(shortfall / capacity.toDouble()).toInt() else 0
        return BottleForecast(
            waterMl = water,
            fullness = capacity?.let { (water.toFloat() / it).coerceIn(0f, 1f) },
            rateMlPerHour = rate,
            emptyAt = emptyAt,
            coversGoal = remainingGoal in 1..water || (remainingGoal == 0),
            refillsNeeded = refills,
        )
    }

    /** Capacity learned from fills: the fullest the bottle has been, unless the user set one. */
    fun learnCapacity(current: Int?, filledWaterMl: Int): Int = max(current ?: 0, filledWaterMl)
}
