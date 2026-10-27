package com.example.mizu.core

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Where today's drinking stands against an even pace through the reminder window. */
enum class PaceState {
    /** Before the window opens and nothing important to say yet. */
    BEFORE_START,
    /** Behind by at least the strong-reminder deficit. */
    BEHIND_FAR,
    BEHIND,
    ON_TRACK,
    /** Ahead of the even pace: can rest for a while. */
    AHEAD,
    /** Drank more in the last hour than kidneys comfortably clear. */
    TOO_FAST,
    REACHED,
    /** Well over the goal. */
    OVER_GOAL,
    /** The window is over and the goal was not reached. */
    DAY_OVER_SHORT,
}

/**
 * Dashboard advice.
 *
 * @param gapMl consumed minus what an even pace expects by now: negative = behind, positive = ahead
 * @param drinkNowMl how much to drink now (0 = nothing needed)
 * @param byTime BEHIND*: drink [drinkNowMl] by then. AHEAD / TOO_FAST: rest until then. ON_TRACK: next sip time.
 * @param ratePerHourMl steady pace needed for the rest of the window to reach the goal
 * @param restFirst BEHIND*: a big drink was just had, so [byTime] is when to have the next one (not "now")
 * @param lastDrinkMl the latest drink today (0 if none)
 */
data class PaceAdvice(
    val state: PaceState,
    val consumedMl: Int,
    val goalMl: Int,
    val expectedMl: Int,
    val gapMl: Int,
    val drinkNowMl: Int,
    val byTime: LocalDateTime?,
    val ratePerHourMl: Int,
    val lastHourMl: Int,
    val restFirst: Boolean = false,
    val lastDrinkMl: Int = 0,
) {
    val remainingMl: Int get() = max(goalMl - consumedMl, 0)
    val overMl: Int get() = max(consumedMl - goalMl, 0)
}

object PaceAdvisor {
    /**
     * Healthy kidneys clear roughly 0.8-1 L of water per hour; drinking more than that for a while risks
     * hyponatremia (symptoms usually after 3-4 L within a few hours). The low end is used, to stay on the safe side.
     */
    const val MAX_ML_PER_HOUR = 800

    /** Within any 3 hours: well under the 3-4 L at which symptoms begin. */
    const val MAX_ML_PER_3_HOURS = 2000

    /** When the drinks in the last [window] fall below [limit] again, or null if they are already under it at [now]. */
    private fun restUntil(now: LocalDateTime, logs: List<DrinkLog>, window: Duration, limit: Int): LocalDateTime? {
        val from = now.minus(window)
        val recent = logs.filter { it.timestamp.isAfter(from) && !it.timestamp.isAfter(now) }.sortedBy { it.timestamp }
        var sum = recent.sumOf { it.amountMl }
        if (sum < limit) return null
        var until = now
        for (log in recent) {
            if (sum < limit) break
            sum -= log.amountMl
            until = log.timestamp.plus(window)
        }
        return until
    }

    /** The body takes up water comfortably at about this rate; recent drinks "settle" at this speed. */
    const val COMFORT_ML_PER_HOUR = 600

    /** Not yet settled from recent drinks + the next sip should stay under this. */
    const val BUCKET_ML = 500

    /** After a big drink, wait until no more than this is still settling before suggesting another. */
    const val SETTLED_ML = 150

    /** A first glass after waking / for the end of the day. */
    const val GLASS_ML = 250

    /** Over the goal by this factor = OVER_GOAL. */
    const val OVER_GOAL_FACTOR = 1.5

    /** Gap within this band counts as on track: 5 % of the goal, at least 100 ml. */
    fun toleranceMl(goalMl: Int): Int = max(100, (goalMl * 0.05).roundToInt())

    /**
     * How much of the drinks up to [now] is still "settling": a leaky bucket that drains at
     * [COMFORT_ML_PER_HOUR]. A glass just drunk counts fully; one from an hour ago at most 600 ml less.
     */
    fun settlingMl(now: LocalDateTime, logs: List<DrinkLog>): Double {
        var level = 0.0
        var at: LocalDateTime? = null
        for (d in logs.filter { !it.timestamp.isAfter(now) }.sortedBy { it.timestamp }) {
            if (at != null) level = drain(level, at, d.timestamp)
            level += d.amountMl
            at = d.timestamp
        }
        return if (at != null) drain(level, at, now) else 0.0
    }

    private fun drain(level: Double, from: LocalDateTime, to: LocalDateTime): Double =
        max(0.0, level - COMFORT_ML_PER_HOUR * Duration.between(from, to).seconds / 3600.0)

    /** Earliest time from [now] at which a drink of [sipMl] is comfortable after the recent ones. */
    fun readyAt(now: LocalDateTime, logs: List<DrinkLog>, sipMl: Int): LocalDateTime {
        val threshold = min(SETTLED_ML, BUCKET_ML - sipMl).coerceAtLeast(0)
        val over = settlingMl(now, logs) - threshold
        if (over <= 0) return now
        return now.plusMinutes(kotlin.math.ceil(over / COMFORT_ML_PER_HOUR * 60).toLong())
    }

    fun advise(now: LocalDateTime, settings: MizuSettings, todayLogs: List<DrinkLog>): PaceAdvice {
        val goal = settings.goalMl
        val consumed = todayLogs.sumOf { it.amountMl }
        val date = now.toLocalDate()
        val start = date.atTime(settings.reminderStart)
        val end = date.atTime(settings.reminderEnd)
        val windowMin = Duration.between(start, end).toMinutes()
        val expected = ReminderEngine.expectedMl(goal, ReminderEngine.elapsedFraction(now, start, end))
        val gap = consumed - expected
        val remaining = max(goal - consumed, 0)
        val hoursLeft = Duration.between(if (now.isBefore(start)) start else now, end).toMinutes() / 60.0
        val rate = if (remaining > 0 && hoursLeft > 0) (remaining / hoursLeft).roundToInt() else 0

        val hourAgo = now.minusHours(1)
        val lastHour = todayLogs.filter { it.timestamp.isAfter(hourAgo) && !it.timestamp.isAfter(now) }
        val lastHourMl = lastHour.sumOf { it.amountMl }

        val lastDrinkMl = todayLogs.filter { !it.timestamp.isAfter(now) }.maxByOrNull { it.timestamp }?.amountMl ?: 0

        fun advice(state: PaceState, drinkNow: Int = 0, by: LocalDateTime? = null, restFirst: Boolean = false) =
            PaceAdvice(state, consumed, goal, expected, gap, drinkNow, by, rate, lastHourMl, restFirst, lastDrinkMl)

        // Behind: one comfortable sip, now or once the last big drink has settled.
        fun behind(state: PaceState, soon: Long): PaceAdvice {
            val sip = ReminderEngine.suggestedAmountMl(-gap, remaining)
            val ready = readyAt(now, todayLogs, sip)
            return if (ready.isAfter(now)) {
                advice(state, drinkNow = sip, by = minOf(ready, end), restFirst = true)
            } else {
                advice(state, drinkNow = sip, by = minOf(now.plusMinutes(soon), end))
            }
        }

        // Too much in a short time (the water kidneys can clear is limited): rest until enough drinks fall out of the window.
        val rest = listOfNotNull(
            restUntil(now, todayLogs, Duration.ofHours(1), MAX_ML_PER_HOUR),
            restUntil(now, todayLogs, Duration.ofHours(3), MAX_ML_PER_3_HOURS),
        ).maxOrNull()
        if (rest != null) return advice(PaceState.TOO_FAST, by = rest)
        if (goal <= 0) return advice(PaceState.ON_TRACK)
        if (consumed >= goal * OVER_GOAL_FACTOR) return advice(PaceState.OVER_GOAL)
        if (consumed >= goal) return advice(PaceState.REACHED)
        if (windowMin <= 0) return advice(PaceState.ON_TRACK)
        if (now.isBefore(start)) return advice(PaceState.BEFORE_START, drinkNow = min(GLASS_ML, remaining), by = start)
        if (!now.isBefore(end)) return advice(PaceState.DAY_OVER_SHORT, drinkNow = min(GLASS_ML, remaining))

        val tolerance = toleranceMl(goal)
        return when {
            -gap >= max(settings.strongDeficitMl, tolerance) -> behind(PaceState.BEHIND_FAR, soon = 15)
            -gap >= tolerance -> behind(PaceState.BEHIND, soon = 30)
            gap >= tolerance -> {
                // The even pace catches up with what was drunk at start + (consumed / goal) of the window.
                val catchUp = start.plusMinutes((windowMin * consumed.toDouble() / goal).roundToInt().toLong())
                advice(PaceState.AHEAD, by = minOf(catchUp, end))
            }
            else -> {
                val interval = settings.baseIntervalMin.coerceAtLeast(ReminderEngine.MIN_INTERVAL_MIN)
                val perSip = ((goal * interval.toDouble() / windowMin) / 50.0).roundToInt() * 50
                val sip = perSip.coerceIn(min(100, remaining), remaining)
                val next = maxOf(now.plusMinutes(interval.toLong()), readyAt(now, todayLogs, sip))
                advice(PaceState.ON_TRACK, drinkNow = sip, by = minOf(next, end))
            }
        }
    }
}
