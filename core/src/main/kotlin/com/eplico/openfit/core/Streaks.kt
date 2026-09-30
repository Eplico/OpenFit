package com.eplico.openfit.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class StreakStats(
    /** Number of distinct days with a workout. */
    val totalDays: Int,
    /** Consecutive workout days ending today (or yesterday, if today isn't logged yet). */
    val currentStreak: Int,
    val longestStreak: Int,
    /** Consecutive weeks with at least one workout, ending this week (or last week). */
    val currentWeekStreak: Int,
)

object Streaks {

    fun compute(
        workoutDays: Collection<LocalDate>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ): StreakStats {
        val days = workoutDays.filter { !it.isAfter(today) }.toSortedSet()
        if (days.isEmpty()) return StreakStats(0, 0, 0, 0)

        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in days) {
            run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
            longest = maxOf(longest, run)
            previous = day
        }

        var current = 0
        var cursor = if (today in days) today else today.minusDays(1)
        while (cursor in days) {
            current++
            cursor = cursor.minusDays(1)
        }

        val weeks = days.map { it.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)) }.toSet()
        val thisWeek = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        var weekCursor = if (thisWeek in weeks) thisWeek else thisWeek.minusWeeks(1)
        var weekStreak = 0
        while (weekCursor in weeks) {
            weekStreak++
            weekCursor = weekCursor.minusWeeks(1)
        }

        return StreakStats(
            totalDays = days.size,
            currentStreak = current,
            longestStreak = longest,
            currentWeekStreak = weekStreak,
        )
    }
}
