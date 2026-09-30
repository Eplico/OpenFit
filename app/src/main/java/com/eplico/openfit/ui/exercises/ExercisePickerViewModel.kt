package com.eplico.openfit.ui.exercises

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eplico.openfit.data.DefaultExercises
import com.eplico.openfit.data.Exercise
import com.eplico.openfit.data.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Chooses an exercise either for a workout day (nav arg "date", an epoch day) or for a
 * preset (nav arg "presetId"). Also lets the user manage the exercise library.
 */
class ExercisePickerViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkoutRepository,
) : ViewModel() {

    private val date: LocalDate? = savedStateHandle.get<Long>("date")?.let(LocalDate::ofEpochDay)
    private val presetId: Long? = savedStateHandle.get<Long>("presetId")

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

    val categories: StateFlow<List<String>> = repository.exercises
        .map { list -> (DefaultExercises.categories + list.map { it.category }).distinctBy { it.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DefaultExercises.categories)

    var error by mutableStateOf<String?>(null)
        private set

    private var picking = false

    fun onQueryChange(text: String) {
        query = text
        queryFlow.value = text
    }

    fun errorShown() {
        error = null
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

    fun create(name: String, category: String, onCreated: (Exercise) -> Unit) {
        viewModelScope.launch {
            repository.createExercise(name, category)
                .onSuccess { id -> onCreated(Exercise(id = id, name = name.trim(), category = category.trim())) }
                .onFailure { error = it.message }
        }
    }

    fun update(exercise: Exercise, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.updateExercise(exercise)
                .onSuccess { onDone() }
                .onFailure { error = it.message }
        }
    }

    fun delete(exercise: Exercise) {
        viewModelScope.launch { repository.deleteExercise(exercise) }
    }
}
