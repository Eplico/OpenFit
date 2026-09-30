package com.eplico.openfit.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StreaksTest {
    // Wednesday; with Monday-first weeks, this week started on Mon Sep 28.
    private val today = LocalDate.of(2026, 9, 30)

    private fun sep(day: Int) = LocalDate.of(2026, 9, day)

    @Test
    fun emptyHistory() {
        assertEquals(StreakStats(0, 0, 0, 4, 0, 3), Streaks.compute(emptyList(), today, goalPerWeek = 4))
    }

    @Test
    fun restDaysWithinTheWeeklyAllowanceKeepTheStreak() {
        // Goal 4 a week = up to 3 rest days a week.
        // Week of Sep 21: Mon, Wed, Fri, Sat (rest Tue, Thu, Sun). This week: Mon. Tue rest; today not yet.
        val days = listOf(sep(21), sep(23), sep(25), sep(26), sep(28))
        val stats = Streaks.compute(days, today, goalPerWeek = 4)
        assertEquals(5, stats.current) // counts workouts, not calendar days
        assertEquals(5, stats.best)
        assertEquals(1, stats.thisWeek)
        assertEquals(2, stats.restDaysLeft) // Tue used one of three
    }

    @Test
    fun oneRestDayTooManyResetsTheStreak() {
        // Week of Sep 14: only Mon, then Tue-Fri off (4 rest days > 3) -> reset on Friday.
        // Sat Sep 19 starts a new streak, which carries through the next week.
        val days = listOf(sep(14), sep(19), sep(21), sep(23), sep(25), sep(26), sep(28))
        val stats = Streaks.compute(days, today, goalPerWeek = 4)
        assertEquals(6, stats.current)
        assertEquals(6, stats.best)
    }

    @Test
    fun bestRemembersTheLongestRun() {
        // Goal 7 = every day. Five days in a row, a break, then two.
        val days = (1..5).map(::sep) + listOf(sep(28), sep(29))
        val stats = Streaks.compute(days, today, goalPerWeek = 7)
        assertEquals(2, stats.current) // today (30th) isn't over, so it doesn't break the streak
        assertEquals(5, stats.best)
        assertEquals(0, stats.restDaysLeft)
    }

    @Test
    fun aWholeWeekOffBreaksEvenAOncePerWeekGoal() {
        val days = listOf(sep(7), sep(14), sep(28)) // nothing in the week of Sep 21
        val stats = Streaks.compute(days, today, goalPerWeek = 1)
        assertEquals(1, stats.current)
        assertEquals(2, stats.best)
        assertEquals(5, stats.restDaysLeft) // Tue 29 used one of six
    }

    @Test
    fun daysBeforeTheFirstWorkoutDontCount() {
        // First ever workout on Saturday Sep 26 with a 5-a-week goal (2 rest days a week).
        val days = listOf(sep(26), sep(28), sep(29))
        val stats = Streaks.compute(days, today, goalPerWeek = 5)
        assertEquals(3, stats.current)
        assertEquals(2, stats.restDaysLeft)
    }

    @Test
    fun sundayFirstWeeks() {
        // With Sunday-first weeks, Sun Sep 27 already belongs to this week.
        val days = listOf(sep(22), sep(24), sep(25), sep(29))
        val stats = Streaks.compute(days, today, goalPerWeek = 5, firstDayOfWeek = DayOfWeek.SUNDAY)
        // Week of Sep 20: Tue, Thu, Fri worked; Wed + Sat rest (2 allowed) -> ok.
        // This week: Sun + Mon rest (2) -> ok, Tue worked.
        assertEquals(4, stats.current)
        assertEquals(1, stats.thisWeek)
        assertEquals(0, stats.restDaysLeft)
    }

    @Test
    fun futureDaysAreIgnoredAndGoalIsClamped() {
        val stats = Streaks.compute(listOf(today.plusDays(1), today), today, goalPerWeek = 12)
        assertEquals(1, stats.totalDays)
        assertEquals(1, stats.current)
        assertEquals(7, stats.goalPerWeek)
    }
}
