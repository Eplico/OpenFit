package com.eplico.openfit.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eplico.openfit.core.Heatmap
import com.eplico.openfit.core.HeatmapGrid
import com.eplico.openfit.core.StreakStats
import com.eplico.openfit.core.Streaks
import com.eplico.openfit.data.SettingsRepository
import com.eplico.openfit.data.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

enum class CalendarRange(val label: String) {
    MONTH("Month"),
    YEAR("Year"),
    LIFETIME("Lifetime"),
}

data class CalendarUiState(
    val range: CalendarRange = CalendarRange.MONTH,
    val month: YearMonth = YearMonth.now(),
    val year: Int = LocalDate.now().year,
    val today: LocalDate = LocalDate.now(),
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    /** One grid for month/year; one per year (newest first) for lifetime. */
    val grids: List<HeatmapGrid> = emptyList(),
    /** Days with at least one set inside the visible range. */
    val workoutsInRange: Int = 0,
    val stats: StreakStats = StreakStats(0, 0, 0, 0),
    val loading: Boolean = true,
)

class CalendarViewModel(
    repository: WorkoutRepository,
    settings: SettingsRepository,
    private val selectedDate: MutableStateFlow<LocalDate>,
) : ViewModel() {

    private val range = MutableStateFlow(CalendarRange.MONTH)
    private val month = MutableStateFlow(YearMonth.now())
    private val year = MutableStateFlow(LocalDate.now().year)

    val uiState: StateFlow<CalendarUiState> = combine(
        range,
        month,
        year,
        repository.observeSetCountsByDay(),
        settings.settings,
    ) { range, month, year, counts, prefs ->
        val today = LocalDate.now()
        val grids = when (range) {
            CalendarRange.MONTH -> listOf(Heatmap.month(month, counts, prefs.weekStart))
            CalendarRange.YEAR -> listOf(Heatmap.year(year, counts, prefs.weekStart))
            CalendarRange.LIFETIME -> Heatmap.lifetime(counts, today, prefs.weekStart)
        }
        CalendarUiState(
            range = range,
            month = month,
            year = year,
            today = today,
            weekStart = prefs.weekStart,
            grids = grids,
            workoutsInRange = grids.sumOf { it.activeDays },
            stats = Streaks.compute(counts.filterValues { it > 0 }.keys, today, prefs.weekStart),
            loading = false,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun setRange(value: CalendarRange) {
        range.value = value
    }

    /** Moves the visible month or year back (-1) or forward (+1). */
    fun shift(delta: Int) {
        when (range.value) {
            CalendarRange.MONTH -> month.update { it.plusMonths(delta.toLong()) }
            CalendarRange.YEAR -> year.update { it + delta }
            CalendarRange.LIFETIME -> Unit
        }
    }

    fun jumpToCurrent() {
        month.value = YearMonth.now()
        year.value = LocalDate.now().year
    }

    fun openDay(date: LocalDate) {
        selectedDate.value = date
    }
}
