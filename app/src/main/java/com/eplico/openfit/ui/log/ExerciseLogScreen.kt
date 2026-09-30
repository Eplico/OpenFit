@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.eplico.openfit.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.eplico.openfit.core.WeightMath
import com.eplico.openfit.core.WeightUnit
import com.eplico.openfit.data.HistorySet
import com.eplico.openfit.data.SetEntry
import com.eplico.openfit.data.values
import com.eplico.openfit.ui.AppViewModels
import com.eplico.openfit.ui.common.StepperField
import com.eplico.openfit.ui.common.UnitToggle
import com.eplico.openfit.ui.common.primaryLine
import com.eplico.openfit.ui.common.ratioLine
import com.eplico.openfit.ui.common.relativeLabel
import com.eplico.openfit.ui.common.shortLabel

@Composable
fun ExerciseLogScreen(
    onBack: () -> Unit,
    viewModel: ExerciseLogViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val entry = viewModel.entry
    val history by viewModel.history.collectAsStateWithLifecycle()
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
                0 -> TrackTab(viewModel, entry.sets, entry.workout.unit)
                else -> HistoryTab(history)
            }
        }
    }
}

@Composable
private fun TrackTab(viewModel: ExerciseLogViewModel, sets: List<SetEntry>, unit: WeightUnit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { EntryCard(viewModel, unit) }
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
                set = set,
                unit = unit,
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
private fun EntryCard(viewModel: ExerciseLogViewModel, unit: WeightUnit) {
    val editing = viewModel.editingSetId != null
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
                UnitToggle(unit = unit, onUnitChange = viewModel::setUnit, modifier = Modifier.width(132.dp))
            }

            StepperField(
                label = "Weight (${unit.label})",
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
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExerciseLogViewModel.QUICK_RATIOS.forEach { quick ->
                    FilterChip(
                        selected = viewModel.ratio == quick,
                        onClick = { viewModel.setRatio(quick) },
                        label = { Text("${WeightMath.format(quick)}×") },
                    )
                }
            }

            CalculatedWeight(viewModel.calculatedWeight, viewModel.ratio, unit)

            StepperField(
                label = "Reps",
                value = viewModel.repsText,
                onValueChange = viewModel::onRepsChange,
                onDecrement = { viewModel.stepReps(-1) },
                onIncrement = { viewModel.stepReps(1) },
                decimal = false,
            )

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
                    if (ratio != null && ratio != WeightMath.DEFAULT_RATIO) {
                        "weight × ${WeightMath.format(ratio, 3)}"
                    } else {
                        "weight × 1"
                    },
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
    set: SetEntry,
    unit: WeightUnit,
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
            Text(set.values.primaryLine(unit), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            set.values.ratioLine(unit)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (set.unit != unit) {
            Text(
                "logged in ${set.unit.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryTab(history: List<HistorySet>) {
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
                val unit = sets.first().workoutUnit
                val values = sets.map { it.set.values }
                val best = values.maxOf { it.estimatedOneRepMaxIn(unit) }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(date.shortLabel(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (best > 0) {
                                Text(
                                    "est. 1RM ${WeightMath.formatWeight(WeightMath.round(best, 1), unit)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                        values.forEachIndexed { index, set ->
                            Row(Modifier.padding(vertical = 3.dp)) {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(24.dp),
                                )
                                Text(set.primaryLine(unit), style = MaterialTheme.typography.bodyMedium)
                                set.ratioLine(unit)?.let {
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
