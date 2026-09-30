@file:OptIn(ExperimentalMaterial3Api::class)

package com.eplico.openfit.ui.exercises

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eplico.openfit.data.CategoryWithCount
import com.eplico.openfit.data.DefaultExercises
import com.eplico.openfit.data.WorkoutRepository
import com.eplico.openfit.ui.AppViewModels
import com.eplico.openfit.ui.common.ConfirmDialog
import com.eplico.openfit.ui.common.TextInputDialog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(private val repository: WorkoutRepository) : ViewModel() {
    val categories: StateFlow<List<CategoryWithCount>> =
        repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var message by mutableStateOf<String?>(null)
        private set

    fun messageShown() {
        message = null
    }

    fun create(name: String) = launchReporting { repository.createCategory(name).exceptionOrNull() }

    fun rename(id: Long, name: String) = launchReporting { repository.renameCategory(id, name).exceptionOrNull() }

    fun delete(id: Long) = launchReporting { repository.deleteCategory(id).exceptionOrNull() }

    fun move(id: Long, delta: Int) = launchReporting {
        repository.moveCategory(id, delta)
        null
    }

    /** Runs [block] and shows the message of any failure it returns. */
    private fun launchReporting(block: suspend () -> Throwable?) {
        viewModelScope.launch { block()?.let { message = it.message } }
    }
}

/** True for the fallback category that deleted categories empty into. */
private val CategoryWithCount.isFallback: Boolean
    get() = category.name.equals(DefaultExercises.OTHER, ignoreCase = true)

@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var creating by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<CategoryWithCount?>(null) }
    var deleting by remember { mutableStateOf<CategoryWithCount?>(null) }

    val message = viewModel.message
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Categories") },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = "New category") },
                text = { Text("New category") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 96.dp),
            modifier = Modifier.padding(padding),
        ) {
            itemsIndexed(categories, key = { _, item -> item.category.id }) { index, item ->
                CategoryRow(
                    item = item,
                    canMoveUp = index > 0,
                    canMoveDown = index < categories.lastIndex,
                    onMove = { delta -> viewModel.move(item.category.id, delta) },
                    onRename = { renaming = item },
                    onDelete = { deleting = item },
                )
                HorizontalDivider()
            }
        }
    }

    if (creating) {
        TextInputDialog(
            title = "New category",
            initialValue = "",
            confirmLabel = "Create",
            onConfirm = { name ->
                creating = false
                viewModel.create(name)
            },
            onDismiss = { creating = false },
        )
    }

    renaming?.let { item ->
        TextInputDialog(
            title = "Rename category",
            initialValue = item.category.name,
            confirmLabel = "Rename",
            onConfirm = { name ->
                renaming = null
                viewModel.rename(item.category.id, name)
            },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { item ->
        ConfirmDialog(
            title = "Delete ${item.category.name}?",
            message = if (item.exerciseCount == 0) {
                "It has no exercises."
            } else {
                "Its ${item.exerciseCount} exercise(s) will move to \"${DefaultExercises.OTHER}\". Logged sets are kept."
            },
            confirmLabel = "Delete",
            onConfirm = {
                deleting = null
                viewModel.delete(item.category.id)
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun CategoryRow(
    item: CategoryWithCount,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(item.category.name) },
        supportingContent = {
            val count = if (item.exerciseCount == 1) "1 exercise" else "${item.exerciseCount} exercises"
            Text(if (item.isFallback) "$count · where exercises go when their category is deleted" else count)
        },
        trailingContent = {
            Row {
                IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                }
                if (!item.isFallback) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Options for ${item.category.name}")
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
            }
        },
    )
}
