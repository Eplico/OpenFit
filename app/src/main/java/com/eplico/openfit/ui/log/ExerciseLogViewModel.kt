package com.eplico.openfit.ui.log

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eplico.openfit.core.SetValues
import com.eplico.openfit.core.WeightMath
import com.eplico.openfit.core.WeightUnit
import com.eplico.openfit.data.HistorySet
import com.eplico.openfit.data.SettingsRepository
import com.eplico.openfit.data.UserSettings
import com.eplico.openfit.data.WorkoutEntryWithWorkout
import com.eplico.openfit.data.WorkoutRepository
import com.eplico.openfit.data.values
import com.eplico.openfit.ui.common.primaryLine
import com.eplico.openfit.ui.common.shortLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseLogViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkoutRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val entryId: Long = checkNotNull(savedStateHandle.get<Long>("workoutExerciseId"))

    /** The exercise-in-workout being logged, with its workout (date + unit) and sets. */
    var entry by mutableStateOf<WorkoutEntryWithWorkout?>(null)
        private set

    /** True once the entry has been removed elsewhere; the screen should close. */
    var closed by mutableStateOf(false)
        private set

    val settings: StateFlow<UserSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    val history: StateFlow<List<HistorySet>> = repository.observeEntry(entryId)
        .map { it?.exercise?.id }
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { repository.observeHistory(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- Entry form (always expressed in the workout's unit) ----

    var weightText by mutableStateOf("")
        private set
    var ratioText by mutableStateOf(WeightMath.format(WeightMath.DEFAULT_RATIO))
        private set
    var repsText by mutableStateOf("")
        private set

    /** Set currently loaded into the form for editing, or null when adding a new set. */
    var editingSetId by mutableStateOf<Long?>(null)
        private set

    /** Explains where the prefilled numbers came from, e.g. "From Sep 28: 60 kg × 8 reps". */
    var prefillNote by mutableStateOf<String?>(null)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    private var formUnit: WeightUnit? = null

    val unit: WeightUnit get() = entry?.workout?.unit ?: formUnit ?: settings.value.defaultUnit

    val weight: Double? get() = if (weightText.isBlank()) 0.0 else WeightMath.parse(weightText)
    val ratio: Double? get() = WeightMath.parse(ratioText)?.takeIf { it > 0.0 }
    val reps: Int? get() = repsText.toIntOrNull()?.takeIf { it > 0 }

    /** weight × ratio, or null while either field is invalid. */
    val calculatedWeight: Double?
        get() {
            val w = weight ?: return null
            val r = ratio ?: return null
            return WeightMath.calculatedWeight(w, r)
        }

    init {
        viewModelScope.launch {
            repository.observeEntry(entryId).collect { loaded ->
                if (loaded == null) {
                    if (entry != null) closed = true
                    return@collect
                }
                val previousUnit = formUnit
                entry = loaded.copy(sets = loaded.sets.sortedBy { it.position })
                val newUnit = loaded.workout.unit
                when {
                    previousUnit == null -> {
                        formUnit = newUnit
                        prefill(loaded)
                    }
                    previousUnit != newUnit -> {
                        convertForm(previousUnit, newUnit)
                        formUnit = newUnit
                    }
                }
                if (editingSetId != null && loaded.sets.none { it.id == editingSetId }) editingSetId = null
            }
        }
    }

    private suspend fun prefill(loaded: WorkoutEntryWithWorkout) {
        val last = repository.lastSet(loaded.exercise.id, loaded.workout.date)
        if (last == null) {
            prefillNote = null
            return
        }
        fillForm(last.set.values.inUnit(loaded.workout.unit))
        prefillNote = if (last.date == loaded.workout.date) {
            null
        } else {
            "From ${last.date.shortLabel()}: ${last.set.values.primaryLine(last.workoutUnit)}"
        }
    }

    private fun fillForm(values: SetValues) {
        weightText = WeightMath.format(values.weight)
        ratioText = WeightMath.format(values.ratio, 3)
        repsText = values.reps.takeIf { it > 0 }?.toString() ?: ""
        error = null
    }

    private fun convertForm(from: WeightUnit, to: WeightUnit) {
        val current = WeightMath.parse(weightText) ?: return
        weightText = WeightMath.format(WeightMath.convertForEntry(current, from, to))
    }

    fun onWeightChange(text: String) {
        weightText = text
        error = null
    }

    fun onRatioChange(text: String) {
        ratioText = text
        error = null
    }

    fun onRepsChange(text: String) {
        repsText = text
        error = null
    }

    fun stepWeight(direction: Int) {
        val step = settings.value.increment(unit)
        val next = ((WeightMath.parse(weightText) ?: 0.0) + direction * step).coerceAtLeast(0.0)
        weightText = WeightMath.format(WeightMath.round(next, 3))
        error = null
    }

    fun stepRatio(direction: Int) {
        val next = (WeightMath.parse(ratioText) ?: WeightMath.DEFAULT_RATIO) + direction * RATIO_STEP
        if (next > 0.0) ratioText = WeightMath.format(next, 3)
        error = null
    }

    fun setRatio(value: Double) {
        ratioText = WeightMath.format(value, 3)
        error = null
    }

    fun stepReps(direction: Int) {
        val next = ((repsText.toIntOrNull() ?: 0) + direction).coerceAtLeast(0)
        repsText = if (next == 0) "" else next.toString()
        error = null
    }

    fun clearForm() {
        weightText = ""
        ratioText = WeightMath.format(WeightMath.DEFAULT_RATIO)
        repsText = ""
        editingSetId = null
        prefillNote = null
        error = null
    }

    fun save() {
        val current = entry ?: return
        val w = weight
        val r = ratio
        val n = reps
        error = when {
            w == null || w < 0 -> "Enter a valid weight"
            r == null -> "Ratio must be greater than 0"
            n == null -> "Enter how many reps"
            else -> null
        }
        if (w == null || r == null || n == null || w < 0) return

        val values = SetValues(weight = w, unit = current.workout.unit, ratio = r, reps = n)
        val editing = editingSetId?.let { id -> current.sets.firstOrNull { it.id == id } }
        viewModelScope.launch {
            if (editing != null) {
                repository.updateSet(editing, values)
                editingSetId = null
            } else {
                repository.addSet(entryId, values)
            }
        }
        prefillNote = null
    }

    /** Tapping a set loads it into the form for editing; tapping it again goes back to adding. */
    fun toggleSelect(setId: Long) {
        val current = entry ?: return
        if (editingSetId == setId) {
            editingSetId = null
            return
        }
        val set = current.sets.firstOrNull { it.id == setId } ?: return
        editingSetId = setId
        fillForm(set.values.inUnit(current.workout.unit))
        prefillNote = null
    }

    fun cancelEdit() {
        editingSetId = null
    }

    fun deleteEditingSet() {
        val id = editingSetId ?: return
        editingSetId = null
        viewModelScope.launch { repository.deleteSet(id) }
    }

    fun setUnit(unit: WeightUnit) {
        val workoutId = entry?.workout?.id ?: return
        viewModelScope.launch { repository.setWorkoutUnit(workoutId, unit) }
    }

    companion object {
        const val RATIO_STEP = 0.25
        val QUICK_RATIOS = listOf(0.5, 1.0, 2.0)
    }
}
