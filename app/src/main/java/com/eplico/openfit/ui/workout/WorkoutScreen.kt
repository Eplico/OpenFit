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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eplico.openfit.core.WeightMath
import com.eplico.openfit.core.WeightUnit
import com.eplico.openfit.data.PresetWithItems
import com.eplico.openfit.data.WorkoutEntry
import com.eplico.openfit.data.orderedItems
import com.eplico.openfit.data.values
import com.eplico.openfit.ui.AppViewModels
import com.eplico.openfit.ui.common.ConfirmDialog
import com.eplico.openfit.ui.common.TextInputDialog
import com.eplico.openfit.ui.common.UnitToggle
import com.eplico.openfit.ui.common.longLabel
import com.eplico.openfit.ui.common.describe
import com.eplico.openfit.ui.common.detail
import com.eplico.openfit.ui.common.relativeLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun WorkoutScreen(
    onAddExercise: (LocalDate) -> Unit,
    onOpenEntry: (Long) -> Unit,
    viewModel: WorkoutViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var menuOpen by remember { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showPresetPicker by rememberSaveable { mutableStateOf(false) }
    var showSavePreset by rememberSaveable { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<WorkoutEntry?>(null) }

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
                    IconButton(onClick = viewModel::previousDay) {
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
                        Text(state.date.relativeLabel(), style = MaterialTheme.typography.titleMedium)
                        Text(
                            state.date.longLabel(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::nextDay) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (state.date != LocalDate.now()) {
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
                                    showPresetPicker = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Save day as preset") },
                                enabled = state.entries.isNotEmpty(),
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
                onClick = { onAddExercise(state.date) },
                icon = { Icon(Icons.Filled.Add, contentDescription = "Add exercise") },
                text = { Text("Add exercise") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = summary(state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                UnitToggle(unit = state.unit, onUnitChange = viewModel::setUnit, modifier = Modifier.width(132.dp))
            }

            if (!state.loading && state.entries.isEmpty()) {
                EmptyWorkout(
                    hasPresets = presets.isNotEmpty(),
                    onAddExercise = { onAddExercise(state.date) },
                    onLoadPreset = { showPresetPicker = true },
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(state.entries, key = { _, entry -> entry.entry.id }) { index, entry ->
                        ExerciseCard(
                            entry = entry,
                            unit = state.unit,
                            canMoveUp = index > 0,
                            canMoveDown = index < state.entries.lastIndex,
                            onClick = { onOpenEntry(entry.entry.id) },
                            onMove = { delta -> viewModel.move(entry.entry.id, delta) },
                            onRemove = {
                                if (entry.sets.isEmpty()) viewModel.remove(entry.entry.id) else pendingRemoval = entry
                            },
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
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

    if (showPresetPicker) {
        PresetPickerDialog(
            presets = presets,
            onPick = { preset ->
                showPresetPicker = false
                viewModel.loadPreset(preset)
            },
            onDismiss = { showPresetPicker = false },
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

private fun summary(state: WorkoutUiState): String {
    if (state.entries.isEmpty()) return "Rest day so far"
    val exercises = state.entries.size
    val parts = mutableListOf(
        if (exercises == 1) "1 exercise" else "$exercises exercises",
        if (state.totalSets == 1) "1 set" else "${state.totalSets} sets",
    )
    if (state.volume > 0) parts += "${WeightMath.format(state.volume, 0)} ${state.unit.label} volume"
    return parts.joinToString(" · ")
}

@Composable
private fun ExerciseCard(
    entry: WorkoutEntry,
    unit: WeightUnit,
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
                        primary = entry.exercise.describe(set.values, unit),
                        secondary = entry.exercise.detail(set.values, unit),
                    )
                }
            }
        }
    }
}

@Composable
private fun SetSummaryRow(number: Int, primary: String, secondary: String?) {
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
