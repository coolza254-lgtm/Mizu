package com.example.mizu.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class PaceAdvisorTest {
    private val day = LocalDate.of(2026, 10, 10)

    // Goal 2000 ml, window 08:00-22:00 (14 h), strong deficit 400 ml.
    private val settings = MizuSettings(
        dailyGoalMl = 2000,
        goalMode = GoalMode.MANUAL,
        reminderStart = LocalTime.of(8, 0),
        reminderEnd = LocalTime.of(22, 0),
    )

    private fun at(h: Int, m: Int = 0): LocalDateTime = day.atTime(h, m)

    private fun log(time: LocalDateTime, ml: Int) = DrinkLog(timestamp = time, amountMl = ml, source = DrinkSource.QUICK)

    private fun advise(now: LocalDateTime, vararg logs: DrinkLog, s: MizuSettings = settings) = PaceAdvisor.advise(now, s, logs.toList())

    @Test
    fun slightlyBehind_drinkTheGapWithinHalfAnHour() {
        // 11:30 expects 500 ml.
        val a = advise(at(11, 30), log(at(9), 250))
        assertEquals(PaceState.BEHIND, a.state)
        assertEquals(-250, a.gapMl)
        assertEquals(250, a.drinkNowMl)
        assertEquals(at(12), a.byTime)
        assertEquals(167, a.ratePerHourMl) // 1750 ml over 10.5 h
    }

    @Test
    fun farBehind_capsOneDrinkAt500AndAsksSoon() {
        // 15:00 expects 1000 ml.
        val a = advise(at(15), log(at(9), 300))
        assertEquals(PaceState.BEHIND_FAR, a.state)
        assertEquals(-700, a.gapMl)
        assertEquals(500, a.drinkNowMl)
        assertEquals(at(15, 15), a.byTime)
    }

    @Test
    fun smallGap_isOnTrack_withNextSipAmountAndTime() {
        val a = advise(at(15), log(at(9), 500), log(at(13), 450))
        assertEquals(PaceState.ON_TRACK, a.state)
        assertEquals(150, a.drinkNowMl) // 2000 ml * 60 / 840 min, rounded to 50
        assertEquals(at(16), a.byTime)
    }

    @Test
    fun userReport_540of2200At1140_isOnTrackNotBehind() {
        val a = advise(at(11, 40), log(at(9), 290), log(at(11, 20), 250), s = settings.copy(dailyGoalMl = 2200))
        assertEquals(PaceState.ON_TRACK, a.state)
        assertEquals(1660, a.remainingMl)
    }

    @Test
    fun ahead_restUntilThePaceCatchesUp() {
        // 900 ml = 45 % of the goal, reached by an even pace 378 min after 08:00.
        val a = advise(at(11), log(at(9), 450), log(at(10), 450))
        assertEquals(PaceState.AHEAD, a.state)
        assertEquals(at(14, 18), a.byTime)
        assertEquals(0, a.drinkNowMl)
    }

    @Test
    fun tooMuchInAnHour_warnsAndRestsUntilUnderTheLimit() {
        val a = advise(at(15), log(at(14, 20), 600), log(at(14, 50), 500))
        assertEquals(PaceState.TOO_FAST, a.state)
        assertEquals(1100, a.lastHourMl)
        assertEquals(at(15, 20), a.byTime)
    }

    @Test
    fun tooFast_winsEvenAfterTheGoal() {
        val a = advise(at(20), log(at(12), 1200), log(at(19, 30), 1000))
        assertEquals(PaceState.TOO_FAST, a.state)
    }

    @Test
    fun reachedAndWellOver() {
        assertEquals(PaceState.REACHED, advise(at(18), log(at(12), 2100)).state)
        val over = advise(at(18), log(at(12), 1500), log(at(15), 1500))
        assertEquals(PaceState.OVER_GOAL, over.state)
        assertEquals(1000, over.overMl)
    }

    @Test
    fun beforeStart_suggestsAFirstGlass() {
        val a = advise(at(6, 30))
        assertEquals(PaceState.BEFORE_START, a.state)
        assertEquals(250, a.drinkNowMl)
        assertEquals(at(8), a.byTime)
    }

    @Test
    fun afterTheWindow_shortOfGoal_oneGlassAtMost() {
        val a = advise(at(22, 30), log(at(12), 1500))
        assertEquals(PaceState.DAY_OVER_SHORT, a.state)
        assertEquals(250, a.drinkNowMl)
        assertEquals(500, a.remainingMl)
    }
}
