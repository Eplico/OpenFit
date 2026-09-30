package com.eplico.openfit.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil

/** One square of the heatmap. [level] is 0 (no workout) to [Heatmap.MAX_LEVEL]. */
data class HeatmapDay(
    val date: LocalDate,
    val count: Int,
    val level: Int,
)

/**
 * A GitHub-style contribution grid. Each entry in [weeks] is one week (7 slots, starting on
 * [firstDayOfWeek]); a slot is null when that day falls outside [start]..[end].
 * Render weeks as columns for the year strip, or as rows for a month calendar.
 */
data class HeatmapGrid(
    val start: LocalDate,
    val end: LocalDate,
    val firstDayOfWeek: DayOfWeek,
    val weeks: List<List<HeatmapDay?>>,
) {
    val days: List<HeatmapDay> get() = weeks.flatMap { it.filterNotNull() }

    val activeDays: Int get() = days.count { it.count > 0 }

    val totalCount: Int get() = days.sumOf { it.count }

    /** Week index at which each month begins, used to place month labels above the columns. */
    val monthStarts: List<Pair<Int, YearMonth>>
        get() = weeks.mapIndexedNotNull { index, week ->
            val first = week.firstOrNull { it != null && (it.date.dayOfMonth == 1 || it.date == start) }
            first?.let { index to YearMonth.from(it.date) }
        }

    /** Day-of-week order of the 7 slots in every week. */
    val weekdays: List<DayOfWeek> get() = (0 until 7).map { firstDayOfWeek.plus(it.toLong()) }
}

object Heatmap {
    const val MAX_LEVEL = 4

    /** Buckets [count] into 0..[MAX_LEVEL] relative to [maxCount], GitHub-style. */
    fun level(count: Int, maxCount: Int): Int {
        if (count <= 0) return 0
        if (maxCount <= 0) return MAX_LEVEL
        val scaled = ceil(MAX_LEVEL * count.toDouble() / maxCount).toInt()
        return scaled.coerceIn(1, MAX_LEVEL)
    }

    /**
     * Builds the grid for [start]..[end]. Levels are scaled against [scaleMax] when given
     * (so several grids can share a scale), otherwise against the busiest day in the range.
     */
    fun grid(
        start: LocalDate,
        end: LocalDate,
        counts: Map<LocalDate, Int>,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        scaleMax: Int? = null,
    ): HeatmapGrid {
        require(!end.isBefore(start)) { "end ($end) is before start ($start)" }
        val max = scaleMax ?: counts.filterKeys { !it.isBefore(start) && !it.isAfter(end) }.values.maxOrNull() ?: 0
        val gridStart = start.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        val lastDayOfWeek = firstDayOfWeek.plus(6)
        val gridEnd = end.with(TemporalAdjusters.nextOrSame(lastDayOfWeek))
        val weekCount = (ChronoUnit.DAYS.between(gridStart, gridEnd).toInt() + 1) / 7

        val weeks = (0 until weekCount).map { w ->
            (0 until 7).map { d ->
                val date = gridStart.plusDays((w * 7 + d).toLong())
                if (date.isBefore(start) || date.isAfter(end)) {
                    null
                } else {
                    val count = counts[date] ?: 0
                    HeatmapDay(date, count, level(count, max))
                }
            }
        }
        return HeatmapGrid(start, end, firstDayOfWeek, weeks)
    }

    fun month(
        month: YearMonth,
        counts: Map<LocalDate, Int>,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        scaleMax: Int? = null,
    ): HeatmapGrid = grid(month.atDay(1), month.atEndOfMonth(), counts, firstDayOfWeek, scaleMax)

    fun year(
        year: Int,
        counts: Map<LocalDate, Int>,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        scaleMax: Int? = null,
    ): HeatmapGrid = grid(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), counts, firstDayOfWeek, scaleMax)

    /**
     * One full-year grid per year from the first logged workout up to [today]'s year, newest
     * first. All years share one colour scale so they can be compared at a glance.
     */
    fun lifetime(
        counts: Map<LocalDate, Int>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ): List<HeatmapGrid> {
        val active = counts.filterValues { it > 0 }
        val firstYear = active.keys.minOrNull()?.year?.coerceAtMost(today.year) ?: today.year
        val scale = active.values.maxOrNull() ?: 0
        return (today.year downTo firstYear).map { year(it, counts, firstDayOfWeek, scale) }
    }
}
