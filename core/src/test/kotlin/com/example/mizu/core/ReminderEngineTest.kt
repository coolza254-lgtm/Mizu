package com.example.mizu.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderEngineTest {
    private val day = LocalDate.of(2026, 10, 8)
    private val tomorrow = day.plusDays(1)

    // Goal 2000 ml, window 08:00-22:00 (14 h).
    private val settings = MizuSettings(
        dailyGoalMl = 2000,
        goalMode = GoalMode.MANUAL,
        reminderStart = LocalTime.of(8, 0),
        reminderEnd = LocalTime.of(22, 0),
        snoozeMinutes = 10,
    )

    private fun at(h: Int, m: Int = 0, d: LocalDate = day): LocalDateTime = d.atTime(h, m)

    private fun log(time: LocalDateTime, ml: Int) = DrinkLog(timestamp = time, amountMl = ml, source = DrinkSource.QUICK)

    @Test
    fun goalReached_noMoreRemindersToday_nextIsTomorrow() {
        val plan = assertNotNull(
            ReminderEngine.nextReminder(at(12), settings, listOf(log(at(11), 2000))),
        )
        assertEquals(at(9, 0, tomorrow), plan.at)
        assertNull(ReminderEngine.contentAt(at(12), settings, 2100))
    }

    @Test
    fun beforeWindow_firstReminderIsWindowStartPlusBaseInterval() {
        val plan = assertNotNull(ReminderEngine.nextReminder(at(6, 30), settings, emptyList()))
        assertEquals(at(9), plan.at)
        assertEquals(2000, plan.content.remainingMl)
    }

    @Test
    fun afterWindow_nextIsTomorrow() {
        val plan = assertNotNull(ReminderEngine.nextReminder(at(22, 30), settings, listOf(log(at(10), 500))))
        assertEquals(at(9, 0, tomorrow), plan.at)
        assertEquals(2000, plan.content.remainingMl)
    }

    @Test
    fun remindersDisabled_returnsNull() {
        assertNull(ReminderEngine.nextReminder(at(12), settings.copy(remindersEnabled = false), emptyList()))
    }

    @Test
    fun deficitShrinksTheInterval() {
        // 12:00 -> expected = 2000 * 4/14 = 571 ml. Defaults: 60 min on track, 15 min at a 600 ml deficit.
        val last = at(12)
        fun next(consumed: Int) = assertNotNull(
            ReminderEngine.nextReminder(at(12), settings, listOf(log(at(11), consumed)), lastReminderAt = last),
        ).at

        assertEquals(at(13, 0), next(700)) // no deficit -> 60 min
        assertEquals(at(12, 47), next(400)) // deficit 171 -> 47 min
        assertEquals(at(12, 25), next(100)) // deficit 471 -> 25 min
        assertEquals(at(12, 17), next(0)) // deficit 571 -> 17 min
    }

    @Test
    fun intervalCurveAndFloor() {
        assertEquals(60, ReminderEngine.intervalMinutes(0, settings))
        assertEquals(38, ReminderEngine.intervalMinutes(300, settings)) // half way: 60 - 45 * 0.5
        assertEquals(15, ReminderEngine.intervalMinutes(600, settings))
        assertEquals(15, ReminderEngine.intervalMinutes(5000, settings))
        // user-tuned: every 45 min, as often as every 10 min from a 300 ml deficit
        val tuned = settings.copy(baseIntervalMin = 45, minIntervalMin = 10, escalationMl = 300)
        assertEquals(10, ReminderEngine.intervalMinutes(300, tuned))
        // never below the hard floor
        assertEquals(ReminderEngine.MIN_INTERVAL_MIN, ReminderEngine.intervalMinutes(900, tuned.copy(minIntervalMin = 1)))
    }

    @Test
    fun fixedModeIgnoresTheDeficit() {
        val fixed = settings.copy(reminderMode = ReminderMode.FIXED, baseIntervalMin = 40)
        assertEquals(40, ReminderEngine.intervalMinutes(900, fixed))
        val plan = assertNotNull(ReminderEngine.nextReminder(at(12), fixed, emptyList(), lastReminderAt = at(12)))
        assertEquals(at(12, 40), plan.at)
    }

    @Test
    fun levelsEscalateWithDeficitAndDryTime() {
        val s = settings.copy(strongDeficitMl = 400, alarmDeficitMl = 800, dryMinutes = 120)
        assertEquals(ReminderLevel.GENTLE, ReminderEngine.level(100, false, 30, s))
        assertEquals(ReminderLevel.STRONG, ReminderEngine.level(450, false, 30, s))
        assertEquals(ReminderLevel.STRONG, ReminderEngine.level(100, false, 130, s)) // dry for too long
        assertEquals(ReminderLevel.STRONG, ReminderEngine.level(100, true, 30, s)) // end-of-day push
        assertEquals(ReminderLevel.ALARM, ReminderEngine.level(850, false, 30, s))
        assertEquals(ReminderLevel.STRONG, ReminderEngine.level(850, false, 30, s.copy(alarmEnabled = false)))
    }

    @Test
    fun contentCarriesTheLevel() {
        // 15:00, nothing since 08:00: deficit 1000 -> alarm
        val c = assertNotNull(ReminderEngine.contentAt(at(15), settings, 0, lastDrinkAt = null))
        assertEquals(ReminderLevel.ALARM, c.level)
        // 09:30 after a drink at 09:00, on track -> gentle
        val g = assertNotNull(ReminderEngine.contentAt(at(9, 30), settings, 300, lastDrinkAt = at(9)))
        assertEquals(ReminderLevel.GENTLE, g.level)
    }

    @Test
    fun endOfDayPush_firesEvenThoughTierWouldWaitLonger() {
        // 21:00, 1400 ml drunk -> deficit 457 (medium = 30 min), but 600 ml in 1 h = 600 ml/h > 500.
        val logs = listOf(log(at(21), 1400))
        val plan = assertNotNull(ReminderEngine.nextReminder(at(21), settings, logs))
        assertEquals(at(21, 15), plan.at)
        assertTrue(plan.content.urgent)
        assertEquals(Feasibility.NOT_REACHABLE, plan.content.feasibility)
        assertEquals(600, plan.content.remainingMl)
    }

    @Test
    fun notUrgentWhenSteadyPaceIsEnough() {
        val content = assertNotNull(ReminderEngine.contentAt(at(12), settings, 400))
        assertEquals(false, content.urgent)
        assertEquals(Feasibility.REACHABLE, content.feasibility)
    }

    @Test
    fun feasibilityBands() {
        assertEquals(Feasibility.REACHABLE, ReminderEngine.feasibility(300, 1.0))
        assertEquals(Feasibility.TIGHT, ReminderEngine.feasibility(400, 1.0))
        assertEquals(Feasibility.NOT_REACHABLE, ReminderEngine.feasibility(501, 1.0))
        assertEquals(Feasibility.NOT_REACHABLE, ReminderEngine.feasibility(100, 0.0))
    }

    @Test
    fun snooze_postponesByExactlySnoozeMinutes_andDoesNotCountAsDrinking() {
        val now = at(12)
        val snoozeUntil = now.plusMinutes(settings.snoozeMinutes.toLong())
        val logs = listOf(log(at(11), 100))
        val plan = assertNotNull(
            ReminderEngine.nextReminder(now, settings, logs, lastReminderAt = now, snoozeUntil = snoozeUntil),
        )
        assertEquals(at(12, 10), plan.at)
        // Snooze logged nothing: remaining is unchanged.
        assertEquals(1900, plan.content.remainingMl)
    }

    @Test
    fun expiredSnooze_isIgnored() {
        val plan = assertNotNull(
            ReminderEngine.nextReminder(at(13), settings, emptyList(), lastReminderAt = at(13), snoozeUntil = at(12, 10)),
        )
        assertEquals(at(13, 15), plan.at) // deficit 714 -> 15 min from last reminder
    }

    @Test
    fun snoozeBeyondWindowEnd_rollsToTomorrow() {
        val plan = assertNotNull(
            ReminderEngine.nextReminder(at(21, 55), settings, listOf(log(at(21, 0), 1900)), snoozeUntil = at(22, 5)),
        )
        assertEquals(tomorrow, plan.at.toLocalDate())
    }

    @Test
    fun logResetsIntervalReference() {
        val logs = listOf(log(at(12, 20), 100))
        val plan = assertNotNull(ReminderEngine.nextReminder(at(12, 20), settings, logs, lastReminderAt = at(12)))
        assertEquals(at(12, 41), plan.at) // at 12:20 expected 619, drunk 100 -> deficit 519 -> 21 min after the 12:20 log
    }

    @Test
    fun suggestedAmount_roundsTo50_andClamps() {
        assertEquals(100, ReminderEngine.suggestedAmountMl(-300, 1000)) // min 100
        assertEquals(100, ReminderEngine.suggestedAmountMl(60, 1000)) // 50 -> clamped up to 100
        assertEquals(250, ReminderEngine.suggestedAmountMl(240, 1000))
        assertEquals(400, ReminderEngine.suggestedAmountMl(900, 400)) // max remaining
        assertEquals(60, ReminderEngine.suggestedAmountMl(500, 60)) // remaining below 100
        assertEquals(0, ReminderEngine.suggestedAmountMl(500, 0))
    }

    @Test
    fun invalidWindow_returnsNull() {
        val bad = settings.copy(reminderStart = LocalTime.of(22, 0), reminderEnd = LocalTime.of(8, 0))
        assertNull(ReminderEngine.nextReminder(at(12), bad, emptyList()))
    }

    @Test
    fun windowCheck() {
        assertTrue(ReminderEngine.isWithinWindow(at(8), settings))
        assertTrue(!ReminderEngine.isWithinWindow(at(7, 59), settings))
        assertTrue(!ReminderEngine.isWithinWindow(at(22, 1), settings))
    }
}
