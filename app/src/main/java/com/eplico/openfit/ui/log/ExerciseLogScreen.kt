@file:OptIn(ExperimentalMaterial3Api::class)

package com.eplico.openfit.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.eplico.openfit.core.DistanceUnit
import com.eplico.openfit.core.Durations
import com.eplico.openfit.core.Measure
import com.eplico.openfit.core.SetFormat
import com.eplico.openfit.core.SetValues
import com.eplico.openfit.core.Trophy
import com.eplico.openfit.core.WeightMath
import com.eplico.openfit.core.WeightUnit
import com.eplico.openfit.data.Exercise
import com.eplico.openfit.data.HistorySet
import com.eplico.openfit.data.SetEntry
import com.eplico.openfit.data.values
import com.eplico.openfit.ui.AppViewModels
import com.eplico.openfit.ui.common.ChoiceToggle
import com.eplico.openfit.ui.common.StepperField
import com.eplico.openfit.ui.common.TimeStepperField
import com.eplico.openfit.ui.common.TrophyBadge
import com.eplico.openfit.ui.common.UnitToggle
import com.eplico.openfit.ui.common.describe
import com.eplico.openfit.ui.common.detail
import com.eplico.openfit.ui.common.relativeLabel
import com.eplico.openfit.ui.common.shortLabel

@Composable
fun ExerciseLogScreen(
    onBack: () -> Unit,
    viewModel: ExerciseLogViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val entry = viewModel.entry
    val history by viewModel.history.collectAsStateWithLifecycle()
    val trophies by viewModel.trophies.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(viewModel.closed) {
        if (viewModel.closed) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            entry?.exercise?.name ?: "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (entry != null) {
                            Text(
                                entry.workout.date.relativeLabel(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Track") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("History") })
            }
            if (entry == null) return@Column
            when (tab) {
                0 -> TrackTab(viewModel, entry.exercise, entry.sets, trophies)
                else -> HistoryTab(entry.exercise, history, trophies)
            }
        }
    }
}

@Composable
private fun TrackTab(
    viewModel: ExerciseLogViewModel,
    exercise: Exercise,
    sets: List<SetEntry>,
    trophies: Map<Long, Trophy>,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { EntryCard(viewModel, exercise) }
        item {
            Text(
                if (sets.isEmpty()) "No sets logged yet" else "Sets",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
        }
        itemsIndexed(sets, key = { _, set -> set.id }) { index, set ->
            LoggedSetRow(
                number = index + 1,
                exercise = exercise,
                set = set,
                trophy = trophies[set.id],
                selected = viewModel.editingSetId == set.id,
                onClick = { viewModel.toggleSelect(set.id) },
            )
        }
        if (sets.isNotEmpty()) {
            item {
                Text(
                    "Tap a set to edit or delete it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun EntryCard(viewModel: ExerciseLogViewModel, exercise: Exercise) {
    val editing = viewModel.editingSetId != null
    val measure = exercise.measure
    val weightUnit = viewModel.unit
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (editing) "Edit set" else "New set",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (weightUnit != null) {
                    UnitToggle(unit = weightUnit, onUnitChange = viewModel::setUnit, modifier = Modifier.width(132.dp))
                }
            }

            if (weightUnit != null) {
                StepperField(
                    label = "Weight (${weightUnit.label})",
                    value = viewModel.weightText,
                    onValueChange = viewModel::onWeightChange,
                    onDecrement = { viewModel.stepWeight(-1) },
                    onIncrement = { viewModel.stepWeight(1) },
                    isError = viewModel.weight == null,
                )
                StepperField(
                    label = "Ratio",
                    value = viewModel.ratioText,
                    onValueChange = viewModel::onRatioChange,
                    onDecrement = { viewModel.stepRatio(-1) },
                    onIncrement = { viewModel.stepRatio(1) },
                    isError = viewModel.ratio == null,
                    labelActions = {
                        ExerciseLogViewModel.QUICK_RATIOS.forEach { quick ->
                            FilterChip(
                                selected = viewModel.ratio == quick,
                                onClick = { viewModel.setRatio(quick) },
                                label = { Text("${WeightMath.format(quick)}×") },
                            )
                        }
                    },
                )
                CalculatedWeight(viewModel.calculatedWeight, viewModel.ratio, weightUnit)
            }

            if (measure.usesReps) {
                StepperField(
                    label = "Reps",
                    value = viewModel.repsText,
                    onValueChange = viewModel::onRepsChange,
                    onDecrement = { viewModel.stepReps(-1) },
                    onIncrement = { viewModel.stepReps(1) },
                    decimal = false,
                )
            }

            if (measure.usesDistance) {
                StepperField(
                    label = "Distance (${viewModel.distanceUnit.label})",
                    value = viewModel.distanceText,
                    onValueChange = viewModel::onDistanceChange,
                    onDecrement = { viewModel.stepDistance(-1) },
                    onIncrement = { viewModel.stepDistance(1) },
                    isError = viewModel.distance == null,
                    labelActions = {
                        ChoiceToggle(
                            options = DistanceUnit.entries,
                            selected = viewModel.distanceUnit,
                            label = { it.label },
                            onSelect = viewModel::onDistanceUnitChange,
                            modifier = Modifier.width(156.dp),
                        )
                    },
                )
            }

            if (measure.usesTime) {
                TimeStepperField(
                    label = "Time",
                    minutes = viewModel.minutesText,
                    seconds = viewModel.secondsText,
                    onMinutesChange = viewModel::onMinutesChange,
                    onSecondsChange = viewModel::onSecondsChange,
                    onDecrement = { viewModel.stepTime(-1) },
                    onIncrement = { viewModel.stepTime(1) },
                    isError = viewModel.durationSeconds == null,
                )
            }

            viewModel.pace?.let { pace ->
                Text("Pace $pace", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            viewModel.prefillNote?.let { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            viewModel.error?.let { error ->
                Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            if (editing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::save, modifier = Modifier.weight(1f)) { Text("Update") }
                    OutlinedButton(onClick = viewModel::deleteEditingSet, modifier = Modifier.weight(1f)) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = viewModel::cancelEdit) { Text("Cancel") }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::save, modifier = Modifier.weight(1f)) { Text("Save set") }
                    OutlinedButton(onClick = viewModel::clearForm) { Text("Clear") }
                }
            }
        }
    }
}

@Composable
private fun CalculatedWeight(calculated: Double?, ratio: Double?, unit: WeightUnit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Calculated weight",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "weight × ${WeightMath.format(ratio ?: WeightMath.DEFAULT_RATIO, 3)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                calculated?.let { WeightMath.formatWeight(it, unit) } ?: "–",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun LoggedSetRow(
    number: Int,
    exercise: Exercise,
    set: SetEntry,
    trophy: Trophy?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            "$number",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(exercise.describe(set.values), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                TrophyBadge(trophy, Modifier.padding(start = 6.dp))
            }
            exercise.detail(set.values)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HistoryTab(exercise: Exercise, history: List<HistorySet>, trophies: Map<Long, Trophy>) {
    if (history.isEmpty()) {
        Text(
            "No history yet. Sets you log for this exercise will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
        )
        return
    }
    val days = history.groupBy { it.date }.toList()
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        days.forEach { (date, sets) ->
            item(key = date.toEpochDay()) {
                val values = sets.map { it.set.values }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(date.shortLabel(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            daySummary(exercise, values)?.let { summary ->
                                Text(summary, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                        values.forEachIndexed { index, set ->
                            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(24.dp),
                                )
                                Text(exercise.describe(set), style = MaterialTheme.typography.bodyMedium)
                                TrophyBadge(trophies[sets[index].set.id], Modifier.padding(start = 6.dp), size = 16.dp)
                                exercise.detail(set)?.let {
                                    Spacer(Modifier.width(8.dp))
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Headline for a day in the history: best estimated 1RM for lifts (in the unit the day's sets used), totals for cardio. */
private fun daySummary(exercise: Exercise, sets: List<SetValues>): String? {
    val unit = sets.firstOrNull { it.weight > 0.0 }?.unit
    if (exercise.measure == Measure.REPS) {
        if (unit == null) return "${sets.sumOf { it.reps }} reps total"
        val best = sets.maxOf { it.estimatedOneRepMaxIn(unit) }
        return if (best > 0) "est. 1RM ${WeightMath.formatWeight(WeightMath.round(best, 1), unit)}" else null
    }
    val parts = buildList {
        if (exercise.measure.usesDistance) {
            val distanceUnit = sets.firstOrNull { it.distance > 0.0 }?.distanceUnit ?: DistanceUnit.KM
            val total = sets.sumOf { it.distanceUnit.convert(it.distance, distanceUnit) }
            if (total > 0.0) add(SetFormat.distance(WeightMath.round(total, 2), distanceUnit))
        }
        val seconds = sets.sumOf { it.durationSeconds }
        if (seconds > 0) add(Durations.format(seconds))
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ", prefix = "Total ")
}
