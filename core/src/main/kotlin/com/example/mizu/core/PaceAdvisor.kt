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
) {
    val remainingMl: Int get() = max(goalMl - consumedMl, 0)
    val overMl: Int get() = max(consumedMl - goalMl, 0)
}

object PaceAdvisor {
    /** Roughly what kidneys clear per hour; more than this within an hour is "too fast". */
    const val MAX_ML_PER_HOUR = 1000

    /** Never suggest more than this in one go. */
    const val MAX_SINGLE_DRINK_ML = 500

    /** A first glass after waking / for the end of the day. */
    const val GLASS_ML = 250

    /** Over the goal by this factor = OVER_GOAL. */
    const val OVER_GOAL_FACTOR = 1.5

    /** Gap within this band counts as on track: 5 % of the goal, at least 100 ml. */
    fun toleranceMl(goalMl: Int): Int = max(100, (goalMl * 0.05).roundToInt())

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

        fun advice(state: PaceState, drinkNow: Int = 0, by: LocalDateTime? = null) =
            PaceAdvice(state, consumed, goal, expected, gap, drinkNow, by, rate, lastHourMl)

        if (lastHourMl >= MAX_ML_PER_HOUR) {
            // Rest until enough of the last hour's drinks fall out of the window to be under the limit.
            var sum = lastHourMl
            var until = now
            for (log in lastHour.sortedBy { it.timestamp }) {
                if (sum < MAX_ML_PER_HOUR) break
                sum -= log.amountMl
                until = log.timestamp.plusHours(1)
            }
            return advice(PaceState.TOO_FAST, by = until)
        }
        if (goal <= 0) return advice(PaceState.ON_TRACK)
        if (consumed >= goal * OVER_GOAL_FACTOR) return advice(PaceState.OVER_GOAL)
        if (consumed >= goal) return advice(PaceState.REACHED)
        if (windowMin <= 0) return advice(PaceState.ON_TRACK)
        if (now.isBefore(start)) return advice(PaceState.BEFORE_START, drinkNow = min(GLASS_ML, remaining), by = start)
        if (!now.isBefore(end)) return advice(PaceState.DAY_OVER_SHORT, drinkNow = min(GLASS_ML, remaining))

        val tolerance = toleranceMl(goal)
        return when {
            -gap >= max(settings.strongDeficitMl, tolerance) -> advice(
                PaceState.BEHIND_FAR,
                drinkNow = min(ReminderEngine.suggestedAmountMl(-gap, remaining), MAX_SINGLE_DRINK_ML),
                by = minOf(now.plusMinutes(15), end),
            )
            -gap >= tolerance -> advice(
                PaceState.BEHIND,
                drinkNow = min(ReminderEngine.suggestedAmountMl(-gap, remaining), MAX_SINGLE_DRINK_ML),
                by = minOf(now.plusMinutes(30), end),
            )
            gap >= tolerance -> {
                // The even pace catches up with what was drunk at start + (consumed / goal) of the window.
                val catchUp = start.plusMinutes((windowMin * consumed.toDouble() / goal).roundToInt().toLong())
                advice(PaceState.AHEAD, by = minOf(catchUp, end))
            }
            else -> {
                val interval = settings.baseIntervalMin.coerceAtLeast(ReminderEngine.MIN_INTERVAL_MIN)
                val perSip = ((goal * interval.toDouble() / windowMin) / 50.0).roundToInt() * 50
                advice(PaceState.ON_TRACK, drinkNow = perSip.coerceIn(min(100, remaining), remaining), by = minOf(now.plusMinutes(interval.toLong()), end))
            }
        }
    }
}
