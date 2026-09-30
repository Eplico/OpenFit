@file:OptIn(ExperimentalMaterial3Api::class)

package com.eplico.openfit.ui.presets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eplico.openfit.data.orderedItems
import com.eplico.openfit.ui.AppViewModels
import com.eplico.openfit.ui.common.ConfirmDialog
import com.eplico.openfit.ui.common.TextInputDialog

@Composable
fun PresetEditScreen(
    onBack: () -> Unit,
    onAddExercise: (presetId: Long) -> Unit,
    viewModel: PresetEditViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val preset by viewModel.preset.collectAsStateWithLifecycle()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val items = preset?.orderedItems.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(preset?.preset?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                actions = {
                    IconButton(onClick = { renaming = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Rename preset")
                    }
                    IconButton(onClick = { deleting = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete preset")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAddExercise(viewModel.presetId) },
                icon = { Icon(Icons.Filled.Add, contentDescription = "Add exercise") },
                text = { Text("Add exercise") },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(32.dp),
            ) {
                Text("No exercises yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Add the exercises you do on this day, in order. Loading the preset adds them all to a workout.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.padding(padding),
            ) {
                itemsIndexed(items, key = { _, item -> item.item.id }) { index, item ->
                    ListItem(
                        leadingContent = {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        headlineContent = { Text(item.exercise.name) },
                        supportingContent = { Text(item.exercise.category) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { viewModel.move(item.item.id, -1) }, enabled = index > 0) {
                                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                                }
                                IconButton(onClick = { viewModel.move(item.item.id, 1) }, enabled = index < items.lastIndex) {
                                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                                }
                                IconButton(onClick = { viewModel.remove(item.item.id) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove")
                                }
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (renaming) {
        TextInputDialog(
            title = "Rename preset",
            initialValue = preset?.preset?.name.orEmpty(),
            confirmLabel = "Rename",
            onConfirm = { name ->
                viewModel.rename(name)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }

    if (deleting) {
        ConfirmDialog(
            title = "Delete \"${preset?.preset?.name.orEmpty()}\"?",
            message = "Workouts you already logged from this preset are kept.",
            confirmLabel = "Delete",
            onConfirm = {
                deleting = false
                viewModel.delete(onBack)
            },
            onDismiss = { deleting = false },
        )
    }
}
