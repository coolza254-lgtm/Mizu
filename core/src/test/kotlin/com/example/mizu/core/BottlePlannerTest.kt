package com.example.mizu.core

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BottlePlannerTest {
    private val day = LocalDate.of(2026, 10, 9)
    private val settings = MizuSettings(
        dailyGoalMl = 2000, goalMode = GoalMode.MANUAL,
        reminderStart = LocalTime.of(8, 0), reminderEnd = LocalTime.of(22, 0),
    )
    private fun log(h: Int, ml: Int) = DrinkLog(timestamp = day.atTime(h, 0), amountMl = ml, source = DrinkSource.WEIGH)

    @Test
    fun rateIsPaceNeededToReachGoal() {
        // 12:00, 600 drunk -> 1400 left over 10 h = 140 ml/h
        assertEquals(140.0, BottlePlanner.rateMlPerHour(day.atTime(12, 0), settings, listOf(log(10, 600))), 0.01)
    }

    @Test
    fun afterGoalUsesActualPace() {
        // 18:00, 2100 drunk since 08:00 -> 210 ml/h
        assertEquals(210.0, BottlePlanner.rateMlPerHour(day.atTime(18, 0), settings, listOf(log(17, 2100))), 0.01)
    }

    @Test
    fun forecastsWhenTheBottleRunsDry() {
        val bottle = Bottle(name = "b", currentWaterG = 350, capacityMl = 700)
        val f = assertNotNull(BottlePlanner.forecast(bottle, day.atTime(12, 0), settings, listOf(log(10, 600))))
        // 350 ml at 140 ml/h = 2.5 h -> 14:30
        assertEquals(day.atTime(14, 30), f.emptyAt)
        assertEquals(0.5f, f.fullness)
        // 1400 left - 350 in bottle = 1050 -> 2 refills of 700
        assertEquals(2, f.refillsNeeded)
        assertTrue(!f.coversGoal)
    }

    @Test
    fun bottleThatCoversTheGoalNeedsNoRefill() {
        val bottle = Bottle(name = "b", currentWaterG = 800, capacityMl = 1000)
        val f = assertNotNull(BottlePlanner.forecast(bottle, day.atTime(20, 0), settings, listOf(log(10, 1500))))
        assertTrue(f.coversGoal)
        assertEquals(0, f.refillsNeeded)
    }

    @Test
    fun unknownCapacityStillForecastsTime() {
        val f = assertNotNull(BottlePlanner.forecast(Bottle(name = "b", currentWaterG = 280), day.atTime(12, 0), settings, listOf(log(10, 600))))
        assertNull(f.fullness)
        assertEquals(0, f.refillsNeeded)
        assertEquals(day.atTime(14, 0), f.emptyAt)
    }

    @Test
    fun neverWeighedHasNoForecast() {
        assertNull(BottlePlanner.forecast(Bottle(name = "b"), day.atTime(12, 0), settings, emptyList()))
    }

    @Test
    fun capacityLearnsTheFullestFill() {
        assertEquals(750, BottlePlanner.learnCapacity(null, 750))
        assertEquals(750, BottlePlanner.learnCapacity(750, 600))
        assertEquals(900, BottlePlanner.learnCapacity(750, 900))
    }
}
