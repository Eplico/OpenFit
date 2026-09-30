package com.eplico.openfit.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class StreakStats(
    /** Number of distinct days with a workout. */
    val totalDays: Int,
    /** Workouts in the current streak. Rest days never add to it. */
    val current: Int,
    /** The longest the streak has ever been. */
    val best: Int,
    val goalPerWeek: Int,
    /** Workouts so far in the current week. */
    val thisWeek: Int,
    /** Rest days that can still be taken this week without resetting the streak. */
    val restDaysLeft: Int,
)

/**
 * A weekly-goal streak. With a goal of N workouts a week you may rest up to 7 − N days in each
 * week (weeks start on the chosen day). The streak counts workouts; it resets only when you rest
 * more days in a week than that. Today isn't a rest day until it's over, and days before your
 * first workout don't count against you.
 */
object Streaks {
    const val DEFAULT_GOAL = 3

    fun compute(
        workoutDays: Collection<LocalDate>,
        today: LocalDate,
        goalPerWeek: Int = DEFAULT_GOAL,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ): StreakStats {
        val goal = goalPerWeek.coerceIn(1, 7)
        val allowance = 7 - goal
        val days = workoutDays.filter { !it.isAfter(today) }.toSortedSet()
        val thisWeekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        val thisWeek = days.count { !it.isBefore(thisWeekStart) }
        if (days.isEmpty()) return StreakStats(0, 0, 0, goal, 0, allowance)

        var current = 0
        var best = 0
        var restUsed = 0
        var week: LocalDate? = null
        var day = days.first()
        while (!day.isAfter(today)) {
            val weekStart = day.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            if (weekStart != week) {
                week = weekStart
                restUsed = 0
            }
            if (day in days) {
                current++
                best = maxOf(best, current)
            } else if (day != today) {
                restUsed++
                if (restUsed > allowance) {
                    // Too many rest days this week: the streak starts over from the next workout.
                    current = 0
                    restUsed = 0
                }
            }
            day = day.plusDays(1)
        }
        val restLeft = if (week == thisWeekStart) allowance - restUsed else allowance
        return StreakStats(
            totalDays = days.size,
            current = current,
            best = best,
            goalPerWeek = goal,
            thisWeek = thisWeek,
            restDaysLeft = restLeft.coerceAtLeast(0),
        )
    }
}
