package com.example.mizu.core

import java.time.Duration
import java.time.LocalDateTime

sealed interface WeighOutcome {
    /** Input rejected (outside 0..10000 g). */
    data object Invalid : WeighOutcome

    /** No previous baseline: just store [waterG]; nothing is logged. */
    data class SetBaseline(val waterG: Int) : WeighOutcome

    /** Water went down: log [amountMl] and store [newWaterG]. */
    data class Drink(val amountMl: Int, val newWaterG: Int, val weightBeforeG: Int, val weightAfterG: Int) : WeighOutcome

    /** Water went up: ask whether the bottle was refilled. Never logs a negative amount. */
    data class AskRefill(val newWaterG: Int) : WeighOutcome

    /** Same amount as before; nothing to log. */
    data object NoChange : WeighOutcome
}

object WeighCalculator {
    const val MAX_WEIGHT_G = 10_000

    fun isValidWeight(totalWeightG: Int?): Boolean = totalWeightG != null && totalWeightG in 0..MAX_WEIGHT_G

    /** Water in the bottle = total - empty, floored at 0. 1 g ~ 1 ml. */
    fun waterG(totalWeightG: Int, emptyWeightG: Int): Int = (totalWeightG - emptyWeightG).coerceAtLeast(0)

    fun evaluate(previousWaterG: Int?, totalWeightG: Int, emptyWeightG: Int): WeighOutcome {
        if (!isValidWeight(totalWeightG)) return WeighOutcome.Invalid
        val newWater = waterG(totalWeightG, emptyWeightG)
        val previous = previousWaterG ?: return WeighOutcome.SetBaseline(newWater)
        return when {
            newWater < previous -> WeighOutcome.Drink(
                amountMl = previous - newWater,
                newWaterG = newWater,
                weightBeforeG = previous + emptyWeightG,
                weightAfterG = totalWeightG,
            )
            newWater > previous -> WeighOutcome.AskRefill(newWater)
            else -> WeighOutcome.NoChange
        }
    }
}

/** When a weighed drink most likely happened. */
data class DrinkTimeEstimate(val at: LocalDateTime, val from: LocalDateTime?)

object DrinkTimeEstimator {
    /** Shorter gaps than this are logged at the weighing time. */
    const val MIN_GAP_MINUTES = 10L

    /** Never look further back than this. */
    const val MAX_LOOKBACK_HOURS = 12L

    /**
     * The water went down some time between the last weighing ([since]) and [now]; log it at the midpoint.
     * Once today's reminder window has opened, the period starts no earlier than the window start, so an
     * overnight gap is not treated as overnight drinking.
     */
    fun estimate(since: LocalDateTime?, now: LocalDateTime, settings: MizuSettings): DrinkTimeEstimate {
        if (since == null || !since.isBefore(now)) return DrinkTimeEstimate(now, null)
        val windowStart = now.toLocalDate().atTime(settings.reminderStart)
        var from = maxOf(since, now.minusHours(MAX_LOOKBACK_HOURS))
        if (!now.isBefore(windowStart) && from.isBefore(windowStart)) from = windowStart
        val gap = Duration.between(from, now)
        if (gap.toMinutes() < MIN_GAP_MINUTES) return DrinkTimeEstimate(now, null)
        return DrinkTimeEstimate(from.plus(gap.dividedBy(2)), from)
    }
}
