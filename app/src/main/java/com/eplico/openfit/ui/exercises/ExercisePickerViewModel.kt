package com.eplico.openfit.ui.exercises

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eplico.openfit.data.Exercise
import com.eplico.openfit.data.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Chooses an exercise either for a workout day (nav arg "date", an epoch day) or for a
 * preset (nav arg "presetId"). Also lets the user delete exercises from the library.
 */
class ExercisePickerViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkoutRepository,
) : ViewModel() {

    val date: LocalDate? = savedStateHandle.get<Long>("date")?.let(LocalDate::ofEpochDay)
    val presetId: Long? = savedStateHandle.get<Long>("presetId")

    val forPreset: Boolean get() = presetId != null

    private val queryFlow = MutableStateFlow("")
    var query by mutableStateOf("")
        private set

    val groups: StateFlow<List<Pair<String, List<Exercise>>>> =
        combine(repository.exercises, queryFlow) { exercises, query ->
            val q = query.trim()
            exercises
                .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) || it.category.contains(q, ignoreCase = true) }
                .groupBy { it.category }
                .toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var picking = false

    fun onQueryChange(text: String) {
        query = text
        queryFlow.value = text
    }

    /**
     * Adds [exercise] to the target. For a workout, [onAddedToWorkout] receives the new
     * workout-exercise id so the caller can open the logging screen; for a preset, [onAddedToPreset] runs.
     */
    fun pick(exercise: Exercise, onAddedToWorkout: (Long) -> Unit, onAddedToPreset: () -> Unit) {
        // Ignore double taps: the first pick navigates away.
        if (picking) return
        picking = true
        viewModelScope.launch {
            when {
                date != null -> onAddedToWorkout(repository.addExerciseToWorkout(date, exercise.id))
                presetId != null -> {
                    repository.addExerciseToPreset(presetId, exercise.id)
                    onAddedToPreset()
                }
            }
        }
    }

    fun delete(exercise: Exercise) {
        viewModelScope.launch { repository.deleteExercise(exercise) }
    }
}
