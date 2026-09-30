package com.eplico.openfit.ui.exercises

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eplico.openfit.core.Measure
import com.eplico.openfit.core.WeightMode
import com.eplico.openfit.data.CategoryWithCount
import com.eplico.openfit.data.DefaultExercises
import com.eplico.openfit.data.Exercise
import com.eplico.openfit.data.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What happened when the editor saved, so the screen knows where to go next. */
sealed interface EditorResult {
    data class AddedToWorkout(val workoutExerciseId: Long) : EditorResult
    data object AddedToPreset : EditorResult
    data object Saved : EditorResult
}

/**
 * Creates or edits an exercise. Nav args: "exerciseId" (edit), or "date"/"presetId" (the workout or
 * preset a new exercise can be added to) plus an optional "name" to start from.
 */
class ExerciseEditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkoutRepository,
) : ViewModel() {

    private val exerciseId: Long? = savedStateHandle.get<Long>("exerciseId")?.takeIf { it > 0 }
    val date: LocalDate? = savedStateHandle.get<Long>("date")?.takeIf { it != NO_DATE }?.let(LocalDate::ofEpochDay)
    private val presetId: Long? = savedStateHandle.get<Long>("presetId")?.takeIf { it > 0 }

    val isNew: Boolean get() = exerciseId == null
    val targetsWorkout: Boolean get() = isNew && date != null
    val targetsPreset: Boolean get() = isNew && date == null && presetId != null

    var name by mutableStateOf(savedStateHandle.get<String>("name").orEmpty())
        private set
    var category by mutableStateOf(DefaultExercises.OTHER)
        private set
    var measure by mutableStateOf(Measure.REPS)
        private set
    var weightMode by mutableStateOf(WeightMode.WORKOUT)
        private set
    var addToTarget by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** True once an existing exercise has been loaded into the form (always true for a new one). */
    var ready by mutableStateOf(exerciseId == null)
        private set

    private var original: Exercise? = null
    private var weightTouched = false
    private var saving = false

    val categories: StateFlow<List<CategoryWithCount>> =
        repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (exerciseId != null) {
            viewModelScope.launch {
                repository.getExercise(exerciseId)?.let { exercise ->
                    original = exercise
                    name = exercise.name
                    category = exercise.category
                    measure = exercise.measure
                    weightMode = exercise.weightMode
                    weightTouched = true
                }
                ready = true
            }
        }
    }

    fun onNameChange(text: String) {
        name = text
        error = null
    }

    fun selectCategory(value: String) {
        category = value
    }

    /** Picking a cardio-style measure switches weight to "No weight" unless the user already chose one. */
    fun selectMeasure(value: Measure) {
        measure = value
        if (!weightTouched) weightMode = if (value == Measure.REPS) WeightMode.WORKOUT else WeightMode.NONE
    }

    fun selectWeightMode(value: WeightMode) {
        weightMode = value
        weightTouched = true
    }

    fun onAddToTargetChange(value: Boolean) {
        addToTarget = value
    }

    fun createCategory(newName: String) {
        viewModelScope.launch {
            repository.createCategory(newName)
                .onSuccess { category = newName.trim() }
                .onFailure { error = it.message }
        }
    }

    fun save(onDone: (EditorResult) -> Unit) {
        if (saving) return
        if (name.isBlank()) {
            error = "Give the exercise a name"
            return
        }
        saving = true
        viewModelScope.launch {
            val existing = original
            if (existing != null) {
                repository.updateExercise(existing.copy(name = name, category = category, measure = measure, weightMode = weightMode))
                    .onSuccess { onDone(EditorResult.Saved) }
                    .onFailure {
                        error = it.message
                        saving = false
                    }
                return@launch
            }
            repository.createExercise(name, category, measure, weightMode)
                .onSuccess { id ->
                    when {
                        addToTarget && date != null -> onDone(EditorResult.AddedToWorkout(repository.addExerciseToWorkout(date, id)))
                        addToTarget && presetId != null -> {
                            repository.addExerciseToPreset(presetId, id)
                            onDone(EditorResult.AddedToPreset)
                        }
                        else -> onDone(EditorResult.Saved)
                    }
                }
                .onFailure {
                    error = it.message
                    saving = false
                }
        }
    }

    companion object {
        /** Nav-arg value meaning "no workout date". */
        const val NO_DATE = Long.MIN_VALUE
    }
}
