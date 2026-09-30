@file:OptIn(ExperimentalMaterial3Api::class)

package com.eplico.openfit.ui.calendar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eplico.openfit.core.HeatmapGrid
import com.eplico.openfit.core.StreakStats
import com.eplico.openfit.ui.AppViewModels
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val monthTitleFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

@Composable
fun CalendarScreen(
    onOpenDay: () -> Unit,
    viewModel: CalendarViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val openDay: (LocalDate) -> Unit = { date ->
        viewModel.openDay(date)
        onOpenDay()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Calendar") }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                CalendarRange.entries.forEachIndexed { index, range ->
                    SegmentedButton(
                        selected = state.range == range,
                        onClick = { viewModel.setRange(range) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = CalendarRange.entries.size),
                    ) {
                        Text(range.label)
                    }
                }
            }

            RangeHeader(
                state = state,
                onPrevious = { viewModel.shift(-1) },
                onNext = { viewModel.shift(1) },
                onJumpToCurrent = viewModel::jumpToCurrent,
            )

            StatsRow(state)

            when (state.range) {
                CalendarRange.MONTH -> state.grids.firstOrNull()?.let { grid ->
                    MonthHeatmap(grid = grid, today = state.today, onDayClick = openDay)
                }
                CalendarRange.YEAR -> state.grids.firstOrNull()?.let { grid ->
                    ScrollingYear(grid = grid, today = state.today, onDayClick = openDay)
                }
                CalendarRange.LIFETIME -> LifetimeYears(grids = state.grids, today = state.today, onDayClick = openDay)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Tap a day to open it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                HeatmapLegend()
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RangeHeader(
    state: CalendarUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onJumpToCurrent: () -> Unit,
) {
    val title = when (state.range) {
        CalendarRange.MONTH -> state.month.format(monthTitleFormat)
        CalendarRange.YEAR -> state.year.toString()
        CalendarRange.LIFETIME -> "All time"
    }
    val isCurrent = when (state.range) {
        CalendarRange.MONTH -> state.month == YearMonth.from(state.today)
        CalendarRange.YEAR -> state.year == state.today.year
        CalendarRange.LIFETIME -> true
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (state.range != CalendarRange.LIFETIME) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = if (state.range == CalendarRange.LIFETIME) TextAlign.Start else TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        if (state.range != CalendarRange.LIFETIME) {
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
            }
        }
    }
    if (!isCurrent) {
        TextButton(onClick = onJumpToCurrent) {
            Text(if (state.range == CalendarRange.MONTH) "Back to this month" else "Back to this year")
        }
    }
}

@Composable
private fun StatsRow(state: CalendarUiState) {
    val rangeLabel = when (state.range) {
        CalendarRange.MONTH -> "This month"
        CalendarRange.YEAR -> "This year"
        CalendarRange.LIFETIME -> "Workouts"
    }
    val stats = state.stats
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile(value = "${state.workoutsInRange}", label = rangeLabel, modifier = Modifier.weight(1f))
            StatTile(value = "${stats.current}", label = "Streak", modifier = Modifier.weight(1f))
            StatTile(value = "${stats.best}", label = "Best streak", modifier = Modifier.weight(1f))
            StatTile(value = "${stats.thisWeek}/${stats.goalPerWeek}", label = "This week", modifier = Modifier.weight(1f))
        }
        Text(
            streakHint(stats),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Explains the weekly-goal streak in one line. */
private fun streakHint(stats: StreakStats): String {
    val goal = if (stats.goalPerWeek == 1) "1 workout a week" else "${stats.goalPerWeek} workouts a week"
    val rest = when {
        stats.goalPerWeek == 7 -> "Every day counts toward the streak."
        stats.restDaysLeft == 0 -> "No rest days left this week: skip another day and the streak resets."
        stats.restDaysLeft == 1 -> "1 rest day left this week without breaking the streak."
        else -> "${stats.restDaysLeft} rest days left this week without breaking the streak."
    }
    return "Streak counts workouts toward your goal of $goal (change it in Settings). $rest"
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/** A single year with comfortably sized squares, scrolled so today's week is in view. */
@Composable
private fun ScrollingYear(grid: HeatmapGrid, today: LocalDate, onDayClick: (LocalDate) -> Unit) {
    val cell = 14.dp
    val gap = 3.dp
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val viewportPx = with(density) { maxWidth.toPx() }
        LaunchedEffect(grid.start, viewportPx) {
            val column = grid.weeks.indexOfFirst { week -> week.any { it?.date == today } }
            val stepPx = with(density) { (cell + gap).toPx() }
            val target = if (column >= 0) (column * stepPx - viewportPx / 2).roundToInt() else 0
            scroll.scrollTo(target.coerceAtLeast(0))
        }
        Row {
            WeekdayLabels(weekdays = grid.weekdays, cellSize = cell, gap = gap, modifier = Modifier.width(32.dp))
            Row(Modifier.horizontalScroll(scroll)) {
                YearHeatmap(grid = grid, today = today, cellSize = cell, gap = gap, onDayClick = onDayClick)
            }
        }
    }
}

/** Every year since the first workout, each squeezed to the screen width for an at-a-glance overview. */
@Composable
private fun LifetimeYears(grids: List<HeatmapGrid>, today: LocalDate, onDayClick: (LocalDate) -> Unit) {
    val gap = 2.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxWeeks = grids.maxOfOrNull { it.weeks.size } ?: 53
        val cell = ((maxWidth + gap) / maxWeeks - gap).coerceIn(3.dp, 16.dp)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            grids.forEach { grid ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${grid.start.year}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            if (grid.activeDays == 1) "1 workout" else "${grid.activeDays} workouts",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    YearHeatmap(grid = grid, today = today, cellSize = cell, gap = gap, onDayClick = onDayClick)
                }
            }
        }
    }
}
