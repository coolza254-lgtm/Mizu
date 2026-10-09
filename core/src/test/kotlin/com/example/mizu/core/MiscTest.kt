package com.example.mizu.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeighCalculatorTest {
    @Test
    fun firstWeighSetsBaseline_withoutLogging() {
        assertEquals(WeighOutcome.SetBaseline(500), WeighCalculator.evaluate(null, 700, 200))
    }

    @Test
    fun lowerWeightLogsDifference() {
        val out = WeighCalculator.evaluate(500, 450, 200)
        assertEquals(WeighOutcome.Drink(amountMl = 250, newWaterG = 250, weightBeforeG = 700, weightAfterG = 450), out)
    }

    @Test
    fun higherWeightAsksAboutRefill_neverNegative() {
        assertEquals(WeighOutcome.AskRefill(800), WeighCalculator.evaluate(500, 1000, 200))
    }

    @Test
    fun sameWeightIsNoChange() {
        assertEquals(WeighOutcome.NoChange, WeighCalculator.evaluate(500, 700, 200))
    }

    @Test
    fun waterIsFlooredAtZero() {
        assertEquals(0, WeighCalculator.waterG(150, 200))
        assertEquals(WeighOutcome.Drink(500, 0, 700, 100), WeighCalculator.evaluate(500, 100, 200))
    }

    @Test
    fun rejectsOutOfRange() {
        assertEquals(WeighOutcome.Invalid, WeighCalculator.evaluate(100, -1, 200))
        assertEquals(WeighOutcome.Invalid, WeighCalculator.evaluate(100, 10_001, 200))
        assertTrue(WeighCalculator.isValidWeight(0))
        assertTrue(WeighCalculator.isValidWeight(10_000))
    }
}

class GoalTest {
    @Test
    fun recommendedIs35PerKg() {
        assertEquals(2100, GoalCalculator.recommendedGoalMl(60))
        assertEquals(2100, MizuSettings(weightKg = 60).goalMl)
        assertEquals(1800, MizuSettings(goalMode = GoalMode.MANUAL, dailyGoalMl = 1800).goalMl)
    }

    @Test
    fun streakCountsConsecutiveGoalDays() {
        val today = LocalDate.of(2026, 10, 8)
        fun log(daysAgo: Long, ml: Int) = DrinkLog(timestamp = today.minusDays(daysAgo).atTime(10, 0), amountMl = ml, source = DrinkSource.QUICK)
        val logs = listOf(log(1, 2000), log(2, 1500), log(2, 600), log(3, 2000), log(5, 2000))
        // today not reached yet -> count from yesterday: days 1, 2, 3 (day 4 missing breaks it)
        assertEquals(3, GoalCalculator.streakDays(logs, 2000, today))
        assertEquals(4, GoalCalculator.streakDays(logs + log(0, 2000), 2000, today))
        assertEquals(0, GoalCalculator.streakDays(emptyList(), 2000, today))
    }

    @Test
    fun progressIsCappedAtOne() {
        assertEquals(0.5f, GoalCalculator.progress(1000, 2000))
        assertEquals(1f, GoalCalculator.progress(3000, 2000))
        assertEquals(0, GoalCalculator.remainingMl(3000, 2000))
    }
}

class CsvExporterTest {
    private val ts = LocalDate.of(2026, 10, 8).atTime(9, 5, 7)

    @Test
    fun startsWithBomAndHeader_andKeepsThai() {
        val logs = listOf(
            DrinkLog(timestamp = ts, amountMl = 250, source = DrinkSource.WEIGH, bottleId = 1, weightBeforeG = 700, weightAfterG = 450),
            DrinkLog(timestamp = ts.plusHours(1), amountMl = 150, source = DrinkSource.QUICK),
        )
        val csv = CsvExporter.build(logs, mapOf(1L to "กระบอกหลัก"), ts.toLocalDate(), ts.toLocalDate())
        assertTrue(csv.startsWith("﻿date,time,amount_ml,source,bottle_name,weight_before_g,weight_after_g\r\n"))
        assertTrue(csv.contains("2026-10-08,09:05:07,250,WEIGH,กระบอกหลัก,700,450\r\n"))
        assertTrue(csv.contains("2026-10-08,10:05:07,150,QUICK,,,\r\n"))
        // UTF-8 BOM bytes
        assertEquals(listOf(0xEF, 0xBB, 0xBF), csv.toByteArray(Charsets.UTF_8).take(3).map { it.toInt() and 0xFF })
    }

    @Test
    fun filtersByRange() {
        val logs = listOf(DrinkLog(timestamp = ts, amountMl = 1, source = DrinkSource.MANUAL))
        val csv = CsvExporter.build(logs, emptyMap(), ts.toLocalDate().plusDays(1), ts.toLocalDate().plusDays(2))
        assertEquals(1, csv.trim().lines().size)
    }

    @Test
    fun escapesCommasQuotesAndFormulas() {
        assertEquals("\"a,b\"", CsvExporter.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.escape("say \"hi\""))
        assertEquals("'=SUM(A1)", CsvExporter.escape("=SUM(A1)"))
    }
}

class VersionComparatorTest {
    @Test
    fun comparesNumerically() {
        assertTrue(VersionComparator.isNewer("v1.10.0", "1.9.9"))
        assertTrue(VersionComparator.isNewer("1.0.1", "1.0.0"))
        assertTrue(VersionComparator.isNewer("2.0", "1.9.9"))
        assertTrue(!VersionComparator.isNewer("1.0.0", "1.0.0"))
        assertTrue(!VersionComparator.isNewer("v1.0.0", "1.0.1"))
        assertTrue(!VersionComparator.isNewer("1.0", "1.0.0"))
    }
}

class HistoryCalculatorTest {
    @Test
    fun zeroFillsDays() {
        val d = LocalDate.of(2026, 10, 8)
        val logs = listOf(DrinkLog(timestamp = d.atTime(9, 0), amountMl = 300, source = DrinkSource.QUICK))
        val out = HistoryCalculator.dailyTotals(logs, d.minusDays(1), d.plusDays(1))
        assertEquals(listOf(0, 300, 0), out.map { it.totalMl })
    }
}

class DrinkTimeEstimatorTest {
    private val day = LocalDate.of(2026, 10, 9)
    private val settings = MizuSettings(reminderStart = java.time.LocalTime.of(8, 0), reminderEnd = java.time.LocalTime.of(22, 0))

    @Test
    fun midpointOfTheGapSinceLastWeighing() {
        val e = DrinkTimeEstimator.estimate(day.atTime(10, 0), day.atTime(12, 0), settings)
        assertEquals(day.atTime(11, 0), e.at)
        assertEquals(day.atTime(10, 0), e.from)
    }

    @Test
    fun overnightGapStartsAtTheWindowStart() {
        val e = DrinkTimeEstimator.estimate(day.minusDays(1).atTime(22, 0), day.atTime(9, 0), settings)
        assertEquals(day.atTime(8, 30), e.at)
        assertEquals(day.atTime(8, 0), e.from)
    }

    @Test
    fun shortGapOrUnknownStartUsesNow() {
        assertEquals(DrinkTimeEstimate(day.atTime(12, 0), null), DrinkTimeEstimator.estimate(day.atTime(11, 55), day.atTime(12, 0), settings))
        assertEquals(DrinkTimeEstimate(day.atTime(12, 0), null), DrinkTimeEstimator.estimate(null, day.atTime(12, 0), settings))
    }

    @Test
    fun lookbackIsCappedAt12Hours() {
        // Before the window opens: 02:00 weigh after a 3-day gap -> period 14:00 yesterday .. 02:00
        val e = DrinkTimeEstimator.estimate(day.minusDays(3).atTime(9, 0), day.atTime(2, 0), settings)
        assertEquals(day.minusDays(1).atTime(14, 0), e.from)
        assertEquals(day.minusDays(1).atTime(20, 0), e.at)
    }
}
