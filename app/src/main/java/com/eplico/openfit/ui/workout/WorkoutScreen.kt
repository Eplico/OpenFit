@file:OptIn(ExperimentalMaterial3Api::class)

package com.eplico.openfit.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eplico.openfit.core.Trophy
import com.eplico.openfit.data.PresetWithItems
import com.eplico.openfit.data.WorkoutEntry
import com.eplico.openfit.data.orderedItems
import com.eplico.openfit.data.values
import com.eplico.openfit.ui.AppViewModels
import com.eplico.openfit.ui.common.ConfirmDialog
import com.eplico.openfit.ui.common.TextInputDialog
import com.eplico.openfit.ui.common.TrophyBadge
import com.eplico.openfit.ui.common.longLabel
import com.eplico.openfit.ui.common.describe
import com.eplico.openfit.ui.common.detail
import com.eplico.openfit.ui.common.formatTotalWeight
import com.eplico.openfit.ui.common.relativeLabel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

/** Pages of the day pager are days since 1970-01-01, up to the year 2200. */
private val DAY_PAGE_COUNT = LocalDate.of(2200, 1, 1).toEpochDay().toInt()

private fun pageOf(date: LocalDate): Int = date.toEpochDay().toInt().coerceIn(0, DAY_PAGE_COUNT - 1)

private fun dateOf(page: Int): LocalDate = LocalDate.ofEpochDay(page.toLong())

@Composable
fun WorkoutScreen(
    onAddExercise: (LocalDate) -> Unit,
    onOpenEntry: (Long) -> Unit,
    viewModel: WorkoutViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val selectedDate by viewModel.selectedDay.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var menuOpen by remember { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var presetPickerDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var showSavePreset by rememberSaveable { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<WorkoutEntry?>(null) }

    // One page per day: swipe left for the next day, right for the previous one.
    val pagerState = rememberPagerState(initialPage = pageOf(selectedDate), pageCount = { DAY_PAGE_COUNT })
    // Follow day changes made elsewhere (the calendar, the date picker, "Go to today").
    LaunchedEffect(selectedDate) {
        val page = pageOf(selectedDate)
        if (page != pagerState.settledPage && page != pagerState.targetPage) {
            if (abs(page - pagerState.currentPage) == 1) pagerState.animateScrollToPage(page) else pagerState.scrollToPage(page)
        }
    }
    // When a swipe (or an arrow) settles on another day, that day becomes the selected one.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { viewModel.selectDate(dateOf(it)) }
    }
    val stepDay: (Int) -> Unit = { delta ->
        scope.launch { pagerState.animateScrollToPage(pagerState.targetPage + delta) }
    }

    val message = viewModel.message
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { stepDay(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                    }
                },
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showDatePicker = true }
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                    ) {
                        Text(selectedDate.relativeLabel(), style = MaterialTheme.typography.titleMedium)
                        Text(
                            selectedDate.longLabel(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { stepDay(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (selectedDate != LocalDate.now()) {
                                DropdownMenuItem(
                                    text = { Text("Go to today") },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.goToToday()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Load preset") },
                                onClick = {
                                    menuOpen = false
                                    presetPickerDate = selectedDate
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Save day as preset") },
                                enabled = state.date == selectedDate && state.entries.isNotEmpty(),
                                onClick = {
                                    menuOpen = false
                                    showSavePreset = true
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAddExercise(selectedDate) },
                icon = { Icon(Icons.Filled.Add, contentDescription = "Add exercise") },
                text = { Text("Add exercise") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            // Keep the days on either side ready, so they slide in already filled.
            beyondViewportPageCount = 1,
            key = { it },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .testTag(DAY_PAGER_TAG),
        ) { page ->
            DayPage(
                date = dateOf(page),
                viewModel = viewModel,
                hasPresets = presets.isNotEmpty(),
                onAddExercise = onAddExercise,
                onLoadPreset = { date -> presetPickerDate = date },
                onOpenEntry = onOpenEntry,
                onRemoveWithSets = { pendingRemoval = it },
            )
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        viewModel.selectDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }

    presetPickerDate?.let { date ->
        PresetPickerDialog(
            presets = presets,
            onPick = { preset ->
                presetPickerDate = null
                viewModel.loadPreset(preset, date)
            },
            onDismiss = { presetPickerDate = null },
        )
    }

    if (showSavePreset) {
        TextInputDialog(
            title = "Save day as preset",
            initialValue = "",
            confirmLabel = "Save",
            onConfirm = { name ->
                showSavePreset = false
                viewModel.saveAsPreset(name)
            },
            onDismiss = { showSavePreset = false },
        )
    }

    pendingRemoval?.let { entry ->
        ConfirmDialog(
            title = "Remove ${entry.exercise.name}?",
            message = "This deletes the ${entry.sets.size} set(s) logged for it on this day.",
            confirmLabel = "Remove",
            onConfirm = {
                viewModel.remove(entry.entry.id)
                pendingRemoval = null
            },
            onDismiss = { pendingRemoval = null },
        )
    }
}

/** Test tag of the swipeable day pager. */
const val DAY_PAGER_TAG = "day-pager"

/** One day in the pager: its summary line, then its exercises (or the empty-day prompt). */
@Composable
private fun DayPage(
    date: LocalDate,
    viewModel: WorkoutViewModel,
    hasPresets: Boolean,
    onAddExercise: (LocalDate) -> Unit,
    onLoadPreset: (LocalDate) -> Unit,
    onOpenEntry: (Long) -> Unit,
    onRemoveWithSets: (WorkoutEntry) -> Unit,
) {
    val state by remember(date) { viewModel.day(date) }.collectAsStateWithLifecycle(initialValue = WorkoutUiState(date = date))
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = if (state.loading) "" else summary(state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }

        if (state.loading) return@Column
        if (state.entries.isEmpty()) {
            EmptyWorkout(
                hasPresets = hasPresets,
                onAddExercise = { onAddExercise(date) },
                onLoadPreset = { onLoadPreset(date) },
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(state.entries, key = { _, entry -> entry.entry.id }) { index, entry ->
                    ExerciseCard(
                        entry = entry,
                        trophies = state.trophies,
                        canMoveUp = index > 0,
                        canMoveDown = index < state.entries.lastIndex,
                        onClick = { onOpenEntry(entry.entry.id) },
                        onMove = { delta -> state.workoutId?.let { viewModel.move(it, entry.entry.id, delta) } },
                        onRemove = {
                            if (entry.sets.isEmpty()) viewModel.remove(entry.entry.id) else onRemoveWithSets(entry)
                        },
                    )
                }
            }
        }
    }
}

private fun summary(state: WorkoutUiState): String {
    if (state.entries.isEmpty()) return "Rest day so far"
    val exercises = state.entries.size
    val parts = mutableListOf(
        if (exercises == 1) "1 exercise" else "$exercises exercises",
        if (state.totalSets == 1) "1 set" else "${state.totalSets} sets",
    )
    if (state.volume > 0) parts += "${formatTotalWeight(state.volume, state.unit)} volume"
    return parts.joinToString(" · ")
}

@Composable
private fun ExerciseCard(
    entry: WorkoutEntry,
    trophies: Map<Long, Trophy>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(top = 8.dp)) {
                    Text(
                        entry.exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        entry.exercise.category,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Exercise options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Move up") },
                            enabled = canMoveUp,
                            leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onMove(-1)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Move down") },
                            enabled = canMoveDown,
                            leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onMove(1)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove from day") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onRemove()
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            if (entry.sets.isEmpty()) {
                Text(
                    "No sets yet. Tap to log.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                entry.sets.forEachIndexed { index, set ->
                    SetSummaryRow(
                        number = index + 1,
                        primary = entry.exercise.describe(set.values),
                        secondary = entry.exercise.detail(set.values),
                        trophy = trophies[set.id],
                    )
                }
            }
        }
    }
}

@Composable
private fun SetSummaryRow(number: Int, primary: String, secondary: String?, trophy: Trophy?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp, horizontal = 0.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.width(28.dp),
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(primary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        TrophyBadge(trophy, Modifier.padding(start = 6.dp))
        if (secondary != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                secondary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyWorkout(
    hasPresets: Boolean,
    onAddExercise: () -> Unit,
    onLoadPreset: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Text("Nothing logged for this day", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Add an exercise, or load one of your presets to set up the whole day at once.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(onClick = onAddExercise) { Text("Add exercise") }
        if (hasPresets) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onLoadPreset) { Text("Load preset") }
        }
    }
}

@Composable
private fun PresetPickerDialog(
    presets: List<PresetWithItems>,
    onPick: (PresetWithItems) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Load preset") },
        text = {
            if (presets.isEmpty()) {
                Text("You don't have any presets yet. Create one in the Presets tab, or use \"Save day as preset\".")
            } else {
                LazyColumn {
                    items(presets, key = { it.preset.id }) { preset ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onPick(preset) }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                        ) {
                            Text(preset.preset.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                preset.orderedItems.joinToString { it.exercise.name }.ifEmpty { "No exercises" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
