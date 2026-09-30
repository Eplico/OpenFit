package com.eplico.openfit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class HeatmapTest {

    @Test
    fun levelBuckets() {
        assertEquals(0, Heatmap.level(0, 20))
        assertEquals(1, Heatmap.level(1, 20))
        assertEquals(1, Heatmap.level(5, 20))
        assertEquals(2, Heatmap.level(6, 20))
        assertEquals(4, Heatmap.level(20, 20))
        assertEquals(4, Heatmap.level(3, 0))
    }

    @Test
    fun monthGridPadsToWholeWeeks() {
        // September 2026 starts on a Tuesday and has 30 days.
        val counts = mapOf(LocalDate.of(2026, 9, 1) to 10, LocalDate.of(2026, 9, 30) to 5)
        val grid = Heatmap.month(YearMonth.of(2026, 9), counts, DayOfWeek.MONDAY)

        assertEquals(5, grid.weeks.size)
        assertTrue(grid.weeks.all { it.size == 7 })
        assertNull(grid.weeks[0][0]) // Monday Aug 31 is outside the month
        assertEquals(LocalDate.of(2026, 9, 1), grid.weeks[0][1]!!.date)
        assertEquals(4, grid.weeks[0][1]!!.level)
        assertEquals(2, grid.weeks[4][2]!!.level)
        assertEquals(30, grid.days.size)
        assertEquals(2, grid.activeDays)
        assertEquals(15, grid.totalCount)
    }

    @Test
    fun sundayFirstWeek() {
        val grid = Heatmap.month(YearMonth.of(2026, 9), emptyMap(), DayOfWeek.SUNDAY)
        assertEquals(DayOfWeek.SUNDAY, grid.weekdays.first())
        assertEquals(DayOfWeek.SATURDAY, grid.weekdays.last())
        assertEquals(LocalDate.of(2026, 9, 1), grid.weeks[0][2]!!.date)
    }

    @Test
    fun yearGridHasAllDaysAndMonthLabels() {
        val grid = Heatmap.year(2024, emptyMap()) // leap year
        assertEquals(366, grid.days.size)
        assertTrue(grid.weeks.size in 53..54)
        val labels = grid.monthStarts
        assertEquals(12, labels.size)
        assertEquals(YearMonth.of(2024, 1), labels.first().second)
        assertEquals(0, labels.first().first)
        assertTrue(labels.zipWithNext().all { (a, b) -> b.first > a.first })
    }

    @Test
    fun lifetimeCoversEveryYearNewestFirstWithSharedScale() {
        val counts = mapOf(
            LocalDate.of(2024, 3, 2) to 20,
            LocalDate.of(2026, 1, 5) to 5,
        )
        val years = Heatmap.lifetime(counts, today = LocalDate.of(2026, 9, 30))
        assertEquals(listOf(2026, 2025, 2024), years.map { it.start.year })
        val jan5 = years[0].days.first { it.date == LocalDate.of(2026, 1, 5) }
        assertEquals(1, jan5.level) // scaled against the 20-set day in 2024
    }

    @Test
    fun lifetimeWithNoDataShowsCurrentYear() {
        val years = Heatmap.lifetime(emptyMap(), today = LocalDate.of(2026, 9, 30))
        assertEquals(listOf(2026), years.map { it.start.year })
    }
}
