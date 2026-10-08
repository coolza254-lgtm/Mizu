package com.example.mizu.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Feasibility { REACHABLE, TIGHT, NOT_REACHABLE }

data class ReminderContent(
    val remainingMl: Int,
    val suggestedMl: Int,
    val feasibility: Feasibility,
    /** True for the end-of-day "drink more now" push. */
    val urgent: Boolean,
    val deficitMl: Int,
)

data class ReminderPlan(val at: LocalDateTime, val content: ReminderContent)

/** Pure reminder maths. Everything is a function of (now, settings, logs, reminder state). */
object ReminderEngine {
    const val BASE_INTERVAL_MIN = 60
    const val MIN_INTERVAL_MIN = 15

    /** Needed steady pace above this (ml per hour) = end-of-day push / not reachable. */
    const val URGENT_RATE_ML_PER_H = 500

    /** Needed steady pace above this (but not urgent) = goal is "tight". */
    const val TIGHT_RATE_ML_PER_H = 350

    fun intervalMinutes(deficitMl: Int): Int = when {
        deficitMl <= 0 -> BASE_INTERVAL_MIN
        deficitMl < 250 -> 45
        deficitMl <= 500 -> 30
        else -> MIN_INTERVAL_MIN
    }

    fun elapsedFraction(now: LocalDateTime, start: LocalDateTime, end: LocalDateTime): Double {
        val total = Duration.between(start, end).toMinutes()
        if (total <= 0) return 1.0
        val elapsed = Duration.between(start, now).toMinutes().toDouble()
        return (elapsed / total).coerceIn(0.0, 1.0)
    }

    fun expectedMl(goalMl: Int, elapsedFraction: Double): Int = (goalMl * elapsedFraction).roundToInt()

    /** clamp(round(deficit, 50 ml), 100 ml, remaining). If remaining < 100 the remaining amount itself is suggested. */
    fun suggestedAmountMl(deficitMl: Int, remainingMl: Int): Int {
        if (remainingMl <= 0) return 0
        val rounded = ((max(deficitMl, 0) / 50.0).roundToInt()) * 50
        return rounded.coerceIn(min(100, remainingMl), remainingMl)
    }

    fun feasibility(remainingMl: Int, hoursLeft: Double): Feasibility {
        if (remainingMl <= 0) return Feasibility.REACHABLE
        if (hoursLeft <= 0.0) return Feasibility.NOT_REACHABLE
        val rate = remainingMl / hoursLeft
        return when {
            rate > URGENT_RATE_ML_PER_H -> Feasibility.NOT_REACHABLE
            rate > TIGHT_RATE_ML_PER_H -> Feasibility.TIGHT
            else -> Feasibility.REACHABLE
        }
    }

    fun isWithinWindow(now: LocalDateTime, settings: MizuSettings): Boolean {
        val date = now.toLocalDate()
        val start = date.atTime(settings.reminderStart)
        val end = date.atTime(settings.reminderEnd)
        return end.isAfter(start) && !now.isBefore(start) && !now.isAfter(end)
    }

    /**
     * What a reminder fired at [at] would say, given [consumedMl] so far today.
     * Null when the goal is already reached (no reminders for the rest of the day).
     * Does not check the reminder window; see [isWithinWindow].
     */
    fun contentAt(at: LocalDateTime, settings: MizuSettings, consumedMl: Int): ReminderContent? {
        val goal = settings.goalMl
        if (goal <= 0 || consumedMl >= goal) return null
        val date = at.toLocalDate()
        val start = date.atTime(settings.reminderStart)
        val end = date.atTime(settings.reminderEnd)
        val remaining = goal - consumedMl
        val deficit = expectedMl(goal, elapsedFraction(at, start, end)) - consumedMl
        val hoursLeft = max(Duration.between(at, end).toMinutes() / 60.0, 0.0)
        val feasibility = feasibility(remaining, hoursLeft)
        return ReminderContent(
            remainingMl = remaining,
            suggestedMl = suggestedAmountMl(deficit, remaining),
            feasibility = feasibility,
            urgent = feasibility == Feasibility.NOT_REACHABLE,
            deficitMl = deficit,
        )
    }

    /**
     * The next reminder after [now].
     *
     * @param lastReminderAt when the previous reminder fired (interval counts from the latest of this, the last log and window start)
     * @param snoozeUntil if later than [now], the next reminder is exactly this time (a snooze never counts as drinking)
     * @return null when reminders are off or the window is invalid. When the goal is reached or the window is over,
     *         the plan is tomorrow's first reminder (window start + base interval).
     */
    fun nextReminder(
        now: LocalDateTime,
        settings: MizuSettings,
        logs: List<DrinkLog>,
        lastReminderAt: LocalDateTime? = null,
        snoozeUntil: LocalDateTime? = null,
    ): ReminderPlan? {
        if (!settings.remindersEnabled || settings.goalMl <= 0) return null
        val date = now.toLocalDate()
        val start = date.atTime(settings.reminderStart)
        val end = date.atTime(settings.reminderEnd)
        if (!end.isAfter(start)) return null

        val todayLogs = GoalCalculator.logsOn(date, logs)
        val consumed = todayLogs.sumOf { it.amountMl }
        if (consumed >= settings.goalMl || !now.isBefore(end)) return tomorrowPlan(date, settings)

        val effectiveNow = if (now.isBefore(start)) start else now
        val current = contentAt(effectiveNow, settings, consumed) ?: return tomorrowPlan(date, settings)

        val reference = listOfNotNull(start, lastReminderAt, todayLogs.maxOfOrNull { it.timestamp })
            .filter { !it.isBefore(start) }
            .reduce { a, b -> if (b.isAfter(a)) b else a }
        val interval = if (current.urgent) MIN_INTERVAL_MIN else intervalMinutes(current.deficitMl)

        var at = reference.plusMinutes(interval.toLong())
        if (snoozeUntil != null && snoozeUntil.isAfter(now)) at = snoozeUntil
        if (at.isBefore(effectiveNow)) at = effectiveNow
        if (!at.isBefore(end)) return tomorrowPlan(date, settings)

        val content = contentAt(at, settings, consumed) ?: return tomorrowPlan(date, settings)
        return ReminderPlan(at, content)
    }

    private fun tomorrowPlan(today: LocalDate, settings: MizuSettings): ReminderPlan? {
        val next = today.plusDays(1)
        val start = next.atTime(settings.reminderStart)
        val end = next.atTime(settings.reminderEnd)
        var at = start.plusMinutes(BASE_INTERVAL_MIN.toLong())
        if (at.isAfter(end)) at = end
        val content = contentAt(at, settings, 0) ?: return null
        return ReminderPlan(at, content)
    }
}
