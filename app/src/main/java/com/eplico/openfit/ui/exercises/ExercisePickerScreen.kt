@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.eplico.openfit.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@Composable
fun ExercisePickerScreen(
    onBack: () -> Unit,
    onAddedToWorkout: (workoutExerciseId: Long) -> Unit,
    onAddedToPreset: () -> Unit,
    viewModel: ExercisePickerViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // null = closed; Exercise with id 0 = creating a new one.
    var editing by remember { mutableStateOf<Exercise?>(null) }
    var deleting by remember { mutableStateOf<Exercise?>(null) }

    val error = viewModel.error
    LaunchedEffect(error) {
        if (error != null) {
            snackbar.showSnackbar(error)
            viewModel.errorShown()
        }
    }

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
                onClick = { editing = Exercise(name = viewModel.query.trim(), category = "") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New exercise") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
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
                                TextButton(onClick = { editing = Exercise(name = viewModel.query.trim(), category = "") }) {
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
                            onEdit = { editing = exercise },
                            onDelete = { deleting = exercise },
                        )
                    }
                }
            }
        }
    }

    editing?.let { target ->
        ExerciseEditorDialog(
            initial = target,
            categories = categories,
            onDismiss = { editing = null },
            onSave = { name, category ->
                if (target.id == 0L) {
                    viewModel.create(name, category) { created ->
                        editing = null
                        pick(created)
                    }
                } else {
                    viewModel.update(target.copy(name = name, category = category)) { editing = null }
                }
            },
        )
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
    ListItem(
        headlineContent = { Text(exercise.name) },
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

@Composable
private fun ExerciseEditorDialog(
    initial: Exercise,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, category: String) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var category by remember { mutableStateOf(initial.category.ifEmpty { categories.firstOrNull().orEmpty() }) }
    val canSave = name.isNotBlank() && category.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New exercise" else "Edit exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { option ->
                        FilterChip(
                            selected = option.equals(category.trim(), ignoreCase = true),
                            onClick = { category = option },
                            label = { Text(option) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, category) }, enabled = canSave) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
