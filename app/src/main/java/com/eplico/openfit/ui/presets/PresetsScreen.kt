@file:OptIn(ExperimentalMaterial3Api::class)

package com.eplico.openfit.ui.presets

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eplico.openfit.data.PresetWithItems
import com.eplico.openfit.data.orderedItems
import com.eplico.openfit.ui.AppViewModels
import com.eplico.openfit.ui.common.ConfirmDialog
import com.eplico.openfit.ui.common.TextInputDialog

@Composable
fun PresetsScreen(
    onOpenPreset: (Long) -> Unit,
    onLoadedIntoToday: () -> Unit,
    viewModel: PresetsViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<PresetWithItems?>(null) }
    var deleting by remember { mutableStateOf<PresetWithItems?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Presets") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = "New preset") },
                text = { Text("New preset") },
            )
        },
    ) { padding ->
        val list = presets
        when {
            list == null -> Box(Modifier.padding(padding))
            list.isEmpty() -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(32.dp),
            ) {
                Spacer(Modifier.height(48.dp))
                Text("No presets yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "A preset is a saved workout day, like \"Push\", \"Pull\" or \"Legs\". " +
                        "Load one to add all of its exercises to today in one tap.\n\n" +
                        "Create one here, or log a day and use \"Save day as preset\" from the Workout tab's menu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding),
            ) {
                items(list, key = { it.preset.id }) { preset ->
                    PresetCard(
                        preset = preset,
                        onOpen = { onOpenPreset(preset.preset.id) },
                        onLoad = { viewModel.loadIntoToday(preset.preset.id, onLoadedIntoToday) },
                        onRename = { renaming = preset },
                        onDelete = { deleting = preset },
                    )
                }
            }
        }
    }

    if (creating) {
        TextInputDialog(
            title = "New preset",
            initialValue = "",
            confirmLabel = "Create",
            onConfirm = { name ->
                creating = false
                viewModel.create(name, onOpenPreset)
            },
            onDismiss = { creating = false },
        )
    }

    renaming?.let { preset ->
        TextInputDialog(
            title = "Rename preset",
            initialValue = preset.preset.name,
            confirmLabel = "Rename",
            onConfirm = { name ->
                viewModel.rename(preset.preset.id, name)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { preset ->
        ConfirmDialog(
            title = "Delete \"${preset.preset.name}\"?",
            message = "Workouts you already logged from this preset are kept.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.delete(preset.preset.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun PresetCard(
    preset: PresetWithItems,
    onOpen: () -> Unit,
    onLoad: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val items = preset.orderedItems
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 16.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(top = 8.dp)) {
                    Text(preset.preset.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (items.size == 1) "1 exercise" else "${items.size} exercises",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Preset options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onRename()
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
            }
            if (items.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    items.joinToString(" · ") { it.exercise.name },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onLoad, enabled = items.isNotEmpty()) {
                Text("Load into today")
            }
        }
    }
}
