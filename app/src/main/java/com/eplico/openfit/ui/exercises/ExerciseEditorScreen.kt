@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.eplico.openfit.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eplico.openfit.core.Measure
import com.eplico.openfit.core.WeightMode
import com.eplico.openfit.ui.AppViewModels
import com.eplico.openfit.ui.common.TextInputDialog
import com.eplico.openfit.ui.common.relativeLabel
import java.time.LocalDate

@Composable
fun ExerciseEditorScreen(
    onBack: () -> Unit,
    onDone: (EditorResult) -> Unit,
    viewModel: ExerciseEditorViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var creatingCategory by rememberSaveable { mutableStateOf(false) }
    val save = { viewModel.save(onDone) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text(if (viewModel.isNew) "New exercise" else "Edit exercise") },
                actions = { TextButton(onClick = save, enabled = viewModel.ready) { Text("Save") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )

            Section("Category") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { item ->
                        FilterChip(
                            selected = item.category.name.equals(viewModel.category, ignoreCase = true),
                            onClick = { viewModel.selectCategory(item.category.name) },
                            label = { Text(item.category.name) },
                        )
                    }
                    AssistChip(
                        onClick = { creatingCategory = true },
                        label = { Text("New category") },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    )
                }
            }

            Section("Track", "What you record for each set.") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Measure.entries.forEach { measure ->
                        FilterChip(
                            selected = viewModel.measure == measure,
                            onClick = { viewModel.selectMeasure(measure) },
                            label = { Text(measure.label) },
                        )
                    }
                }
            }

            Section(
                "Weight",
                when (viewModel.weightMode) {
                    WeightMode.DEFAULT -> "New sets start in your default unit (Settings). You can switch any set between kg and lb."
                    WeightMode.KG, WeightMode.LB -> "New sets start in ${viewModel.weightMode.label}. You can still switch any set."
                    WeightMode.NONE -> "No weight field, e.g. for running or planks."
                },
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WeightMode.entries.forEach { mode ->
                        FilterChip(
                            selected = viewModel.weightMode == mode,
                            onClick = { viewModel.selectWeightMode(mode) },
                            label = { Text(if (mode == WeightMode.NONE) "None" else mode.label) },
                        )
                    }
                }
            }

            if (viewModel.targetsWorkout || viewModel.targetsPreset) {
                val label = when {
                    viewModel.targetsPreset -> "Add to this preset"
                    viewModel.date == LocalDate.now() -> "Add to today's workout"
                    else -> "Add to the workout on ${viewModel.date?.relativeLabel()}"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = viewModel.addToTarget, onCheckedChange = viewModel::onAddToTargetChange)
                }
            }

            viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(onClick = save, enabled = viewModel.ready, modifier = Modifier.fillMaxWidth()) {
                Text("Save exercise")
            }
        }
    }

    if (creatingCategory) {
        TextInputDialog(
            title = "New category",
            initialValue = "",
            confirmLabel = "Create",
            onConfirm = { name ->
                creatingCategory = false
                viewModel.createCategory(name)
            },
            onDismiss = { creatingCategory = false },
        )
    }
}

@Composable
private fun Section(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}
