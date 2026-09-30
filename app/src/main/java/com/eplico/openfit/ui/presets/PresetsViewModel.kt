package com.eplico.openfit.ui.presets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eplico.openfit.data.PresetWithItems
import com.eplico.openfit.data.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class PresetsViewModel(
    private val repository: WorkoutRepository,
    private val selectedDate: MutableStateFlow<LocalDate>,
) : ViewModel() {

    val presets: StateFlow<List<PresetWithItems>?> =
        repository.presets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(repository.createPreset(name)) }
    }

    fun rename(presetId: Long, name: String) {
        viewModelScope.launch { repository.renamePreset(presetId, name) }
    }

    fun delete(presetId: Long) {
        viewModelScope.launch { repository.deletePreset(presetId) }
    }

    /** Loads the preset into today's workout and makes today the selected day. */
    fun loadIntoToday(presetId: Long, onLoaded: () -> Unit) {
        val today = LocalDate.now()
        viewModelScope.launch {
            repository.loadPreset(today, presetId)
            selectedDate.value = today
            onLoaded()
        }
    }
}

class PresetEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkoutRepository,
) : ViewModel() {

    val presetId: Long = checkNotNull(savedStateHandle.get<Long>("presetId"))

    val preset: StateFlow<PresetWithItems?> =
        repository.observePreset(presetId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rename(name: String) {
        viewModelScope.launch { repository.renamePreset(presetId, name) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deletePreset(presetId)
            onDeleted()
        }
    }

    fun move(itemId: Long, delta: Int) {
        viewModelScope.launch { repository.movePresetItem(presetId, itemId, delta) }
    }

    fun remove(itemId: Long) {
        viewModelScope.launch { repository.removePresetItem(itemId) }
    }
}
