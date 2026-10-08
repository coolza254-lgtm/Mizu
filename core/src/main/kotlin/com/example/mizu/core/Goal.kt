package com.example.mizu.core

import java.time.LocalDate

object GoalCalculator {
    const val ML_PER_KG = 35

    fun recommendedGoalMl(weightKg: Int): Int = weightKg.coerceAtLeast(0) * ML_PER_KG

    /** min(consumed / goal, 1.0). Overdrinking never breaks anything. */
    fun progress(consumedMl: Int, goalMl: Int): Float =
        if (goalMl <= 0) 0f else minOf(consumedMl.toFloat() / goalMl, 1f)

    fun remainingMl(consumedMl: Int, goalMl: Int): Int = (goalMl - consumedMl).coerceAtLeast(0)

    fun isGoalReached(consumedMl: Int, goalMl: Int): Boolean = goalMl > 0 && consumedMl >= goalMl

    fun logsOn(date: LocalDate, logs: List<DrinkLog>): List<DrinkLog> =
        logs.filter { it.timestamp.toLocalDate() == date }

    fun consumedOn(date: LocalDate, logs: List<DrinkLog>): Int = logsOn(date, logs).sumOf { it.amountMl }
}

data class DaySummary(val date: LocalDate, val totalMl: Int)

object HistoryCalculator {
    /** One entry per day in [from]..[to] inclusive, zero-filled. */
    fun dailyTotals(logs: List<DrinkLog>, from: LocalDate, to: LocalDate): List<DaySummary> {
        val byDay = logs.groupBy { it.timestamp.toLocalDate() }.mapValues { (_, v) -> v.sumOf { it.amountMl } }
        val out = ArrayList<DaySummary>()
        var d = from
        while (!d.isAfter(to)) {
            out += DaySummary(d, byDay[d] ?: 0)
            d = d.plusDays(1)
        }
        return out
    }
}
