package com.example.mizu.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Feasibility { REACHABLE, TIGHT, NOT_REACHABLE }

/** How hard a reminder pushes: normal notification, heads-up with strong vibration, or full-screen alarm. */
enum class ReminderLevel { GENTLE, STRONG, ALARM }

data class ReminderContent(
    val remainingMl: Int,
    val suggestedMl: Int,
    val feasibility: Feasibility,
    /** True for the end-of-day "drink more now" push. */
    val urgent: Boolean,
    val deficitMl: Int,
    val level: ReminderLevel = ReminderLevel.GENTLE,
)

data class ReminderPlan(val at: LocalDateTime, val content: ReminderContent)

/** Pure reminder maths. Everything is a function of (now, settings, logs, reminder state). */
object ReminderEngine {
    /** Hard floor whatever the user sets. */
    const val MIN_INTERVAL_MIN = 5

    /** Needed steady pace above this (ml per hour) = end-of-day push / not reachable. */
    const val URGENT_RATE_ML_PER_H = 500

    /** Needed steady pace above this (but not urgent) = goal is "tight". */
    const val TIGHT_RATE_ML_PER_H = 350

    /**
     * FIXED: always the base interval. ADAPTIVE: the base interval when on track, shrinking linearly with the
     * deficit down to the minimum interval once the deficit reaches the escalation amount.
     */
    fun intervalMinutes(deficitMl: Int, settings: MizuSettings): Int {
        val base = settings.baseIntervalMin.coerceAtLeast(MIN_INTERVAL_MIN)
        val min = settings.minIntervalMin.coerceIn(MIN_INTERVAL_MIN, base)
        if (settings.reminderMode == ReminderMode.FIXED || deficitMl <= 0) return base
        val t = (deficitMl.toDouble() / settings.escalationMl.coerceAtLeast(1)).coerceAtMost(1.0)
        return (base - (base - min) * t).roundToInt().coerceIn(min, base)
    }

    /** ALARM when enabled and far behind; STRONG when behind, dry for too long, or the end-of-day push; else GENTLE. */
    fun level(deficitMl: Int, urgent: Boolean, minutesWithoutDrink: Long, settings: MizuSettings): ReminderLevel = when {
        settings.alarmEnabled && deficitMl >= settings.alarmDeficitMl -> ReminderLevel.ALARM
        urgent || deficitMl >= settings.strongDeficitMl || minutesWithoutDrink >= settings.dryMinutes -> ReminderLevel.STRONG
        else -> ReminderLevel.GENTLE
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
    fun contentAt(at: LocalDateTime, settings: MizuSettings, consumedMl: Int, lastDrinkAt: LocalDateTime? = null): ReminderContent? {
        val goal = settings.goalMl
        if (goal <= 0 || consumedMl >= goal) return null
        val date = at.toLocalDate()
        val start = date.atTime(settings.reminderStart)
        val end = date.atTime(settings.reminderEnd)
        val remaining = goal - consumedMl
        val deficit = expectedMl(goal, elapsedFraction(at, start, end)) - consumedMl
        val hoursLeft = max(Duration.between(at, end).toMinutes() / 60.0, 0.0)
        val feasibility = feasibility(remaining, hoursLeft)
        val urgent = feasibility == Feasibility.NOT_REACHABLE
        // Dry time counts from the last drink today, or from the window start if nothing was drunk yet.
        val dryFrom = listOfNotNull(lastDrinkAt?.takeIf { it.toLocalDate() == date }, start).reduce { a, b -> if (b.isAfter(a)) b else a }
        val dryMinutes = Duration.between(dryFrom, at).toMinutes().coerceAtLeast(0)
        return ReminderContent(
            remainingMl = remaining,
            suggestedMl = suggestedAmountMl(deficit, remaining),
            feasibility = feasibility,
            urgent = urgent,
            deficitMl = deficit,
            level = level(deficit, urgent, dryMinutes, settings),
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
        val lastDrink = todayLogs.maxOfOrNull { it.timestamp }
        val current = contentAt(effectiveNow, settings, consumed, lastDrink) ?: return tomorrowPlan(date, settings)

        val reference = listOfNotNull(start, lastReminderAt, todayLogs.maxOfOrNull { it.timestamp })
            .filter { !it.isBefore(start) }
            .reduce { a, b -> if (b.isAfter(a)) b else a }
        val interval = if (current.urgent) {
            settings.minIntervalMin.coerceIn(MIN_INTERVAL_MIN, settings.baseIntervalMin.coerceAtLeast(MIN_INTERVAL_MIN))
        } else {
            intervalMinutes(current.deficitMl, settings)
        }

        var at = reference.plusMinutes(interval.toLong())
        if (snoozeUntil != null && snoozeUntil.isAfter(now)) at = snoozeUntil
        if (at.isBefore(effectiveNow)) at = effectiveNow
        if (!at.isBefore(end)) return tomorrowPlan(date, settings)

        val content = contentAt(at, settings, consumed, lastDrink) ?: return tomorrowPlan(date, settings)
        return ReminderPlan(at, content)
    }

    private fun tomorrowPlan(today: LocalDate, settings: MizuSettings): ReminderPlan? {
        val next = today.plusDays(1)
        val start = next.atTime(settings.reminderStart)
        val end = next.atTime(settings.reminderEnd)
        var at = start.plusMinutes(settings.baseIntervalMin.coerceAtLeast(MIN_INTERVAL_MIN).toLong())
        if (at.isAfter(end)) at = end
        val content = contentAt(at, settings, 0) ?: return null
        return ReminderPlan(at, content)
    }
}
