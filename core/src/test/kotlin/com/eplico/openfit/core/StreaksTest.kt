package com.eplico.openfit.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreaksTest {
    private val today = LocalDate.of(2026, 9, 30) // a Wednesday

    @Test
    fun emptyHistory() {
        assertEquals(StreakStats(0, 0, 0, 0), Streaks.compute(emptyList(), today))
    }

    @Test
    fun currentStreakIncludesToday() {
        val days = listOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(5))
        val stats = Streaks.compute(days, today)
        assertEquals(4, stats.totalDays)
        assertEquals(3, stats.currentStreak)
        assertEquals(3, stats.longestStreak)
    }

    @Test
    fun streakStaysAliveUntilTodayIsOver() {
        val days = listOf(today.minusDays(1), today.minusDays(2))
        assertEquals(2, Streaks.compute(days, today).currentStreak)
    }

    @Test
    fun brokenStreak() {
        val days = (10..16).map { LocalDate.of(2026, 9, it) } + today.minusDays(3)
        val stats = Streaks.compute(days, today)
        assertEquals(0, stats.currentStreak)
        assertEquals(7, stats.longestStreak)
    }

    @Test
    fun weekStreakCountsConsecutiveWeeks() {
        // This week (Mon Sep 28), plus the two previous weeks, then a gap.
        val days = listOf(
            LocalDate.of(2026, 9, 28),
            LocalDate.of(2026, 9, 24),
            LocalDate.of(2026, 9, 14),
            LocalDate.of(2026, 8, 31),
        )
        assertEquals(3, Streaks.compute(days, today).currentWeekStreak)
    }

    @Test
    fun futureDaysAreIgnored() {
        val stats = Streaks.compute(listOf(today.plusDays(1), today), today)
        assertEquals(1, stats.totalDays)
        assertEquals(1, stats.currentStreak)
    }
}
