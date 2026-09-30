package com.eplico.openfit.ui.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eplico.openfit.core.WeightUnit
import com.eplico.openfit.core.volumeIn
import com.eplico.openfit.data.PresetWithItems
import com.eplico.openfit.data.SettingsRepository
import com.eplico.openfit.data.WorkoutEntry
import com.eplico.openfit.data.WorkoutRepository
import com.eplico.openfit.data.values
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class WorkoutUiState(
    val date: LocalDate,
    val unit: WeightUnit = WeightUnit.KG,
    val workoutId: Long? = null,
    val entries: List<WorkoutEntry> = emptyList(),
    val loading: Boolean = true,
) {
    val totalSets: Int get() = entries.sumOf { it.sets.size }
    val volume: Double get() = entries.flatMap { entry -> entry.sets.map { it.values } }.volumeIn(unit)
}

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModel(
    private val repository: WorkoutRepository,
    settings: SettingsRepository,
    private val selectedDate: MutableStateFlow<LocalDate>,
) : ViewModel() {

    val uiState: StateFlow<WorkoutUiState> = selectedDate
        .flatMapLatest { date ->
            combine(repository.observeDay(date), settings.settings) { day, prefs ->
                WorkoutUiState(
                    date = date,
                    unit = day?.workout?.unit ?: prefs.defaultUnit,
                    workoutId = day?.workout?.id,
                    entries = day?.entries.orEmpty()
                        .sortedBy { it.entry.position }
                        .map { entry -> entry.copy(sets = entry.sets.sortedBy { it.position }) },
                    loading = false,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState(date = selectedDate.value))

    val presets: StateFlow<List<PresetWithItems>> =
        repository.presets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** One-off message for the snackbar; the screen clears it once shown. */
    var message by mutableStateOf<String?>(null)
        private set

    fun messageShown() {
        message = null
    }

    fun previousDay() = selectedDate.update { it.minusDays(1) }

    fun nextDay() = selectedDate.update { it.plusDays(1) }

    fun goToToday() {
        selectedDate.value = LocalDate.now()
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun setUnit(unit: WeightUnit) {
        val date = selectedDate.value
        viewModelScope.launch { repository.setWorkoutUnit(date, unit) }
    }

    fun remove(workoutExerciseId: Long) {
        viewModelScope.launch { repository.removeFromWorkout(workoutExerciseId) }
    }

    fun move(workoutExerciseId: Long, delta: Int) {
        val workoutId = uiState.value.workoutId ?: return
        viewModelScope.launch { repository.moveInWorkout(workoutId, workoutExerciseId, delta) }
    }

    fun loadPreset(preset: PresetWithItems) {
        val date = selectedDate.value
        viewModelScope.launch {
            val added = repository.loadPreset(date, preset.preset.id)
            message = when {
                preset.items.isEmpty() -> "\"${preset.preset.name}\" has no exercises yet"
                added == 0 -> "Everything in \"${preset.preset.name}\" is already here"
                added == 1 -> "Added 1 exercise from \"${preset.preset.name}\""
                else -> "Added $added exercises from \"${preset.preset.name}\""
            }
        }
    }

    fun saveAsPreset(name: String) {
        val date = selectedDate.value
        viewModelScope.launch {
            val id = repository.saveWorkoutAsPreset(date, name)
            message = if (id == null) "Add some exercises before saving a preset" else "Saved preset \"${name.trim()}\""
        }
    }
}
