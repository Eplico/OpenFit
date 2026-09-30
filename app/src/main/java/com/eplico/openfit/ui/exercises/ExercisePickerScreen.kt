@file:OptIn(ExperimentalMaterial3Api::class)

package com.eplico.openfit.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eplico.openfit.data.Exercise
import com.eplico.openfit.ui.AppViewModels
import com.eplico.openfit.ui.common.ConfirmDialog
import com.eplico.openfit.ui.common.typeSummary

@Composable
fun ExercisePickerScreen(
    onBack: () -> Unit,
    onAddedToWorkout: (workoutExerciseId: Long) -> Unit,
    onAddedToPreset: () -> Unit,
    onNewExercise: (name: String) -> Unit,
    onEditExercise: (exerciseId: Long) -> Unit,
    viewModel: ExercisePickerViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Exercise?>(null) }

    val pick: (Exercise) -> Unit = { exercise ->
        viewModel.pick(exercise, onAddedToWorkout, onAddedToPreset)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text(if (viewModel.forPreset) "Add to preset" else "Add exercise") },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNewExercise(viewModel.query.trim()) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New exercise") },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = viewModel.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Search exercises") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (viewModel.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            LazyColumn(
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (groups.isEmpty()) {
                    item {
                        Column(Modifier.padding(24.dp)) {
                            Text(
                                if (viewModel.query.isBlank()) "No exercises yet." else "No exercise matches \"${viewModel.query.trim()}\".",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (viewModel.query.isNotBlank()) {
                                TextButton(onClick = { onNewExercise(viewModel.query.trim()) }) {
                                    Text("Create \"${viewModel.query.trim()}\"")
                                }
                            }
                        }
                    }
                }
                groups.forEach { (category, exercises) ->
                    item(key = "header-$category") {
                        Text(
                            category,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(exercises, key = { it.id }) { exercise ->
                        ExerciseRow(
                            exercise = exercise,
                            onClick = { pick(exercise) },
                            onEdit = { onEditExercise(exercise.id) },
                            onDelete = { deleting = exercise },
                        )
                    }
                }
            }
        }
    }

    deleting?.let { target ->
        ConfirmDialog(
            title = "Delete ${target.name}?",
            message = "This removes the exercise and every set you've ever logged for it. This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.delete(target)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun ExerciseRow(
    exercise: Exercise,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val type = exercise.typeSummary()
    ListItem(
        headlineContent = { Text(exercise.name) },
        supportingContent = if (type != null) {
            { Text(type) }
        } else {
            null
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Options for ${exercise.name}")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
