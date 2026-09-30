package com.eplico.openfit.ui.log

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eplico.openfit.core.DistanceUnit
import com.eplico.openfit.core.Durations
import com.eplico.openfit.core.Measure
import com.eplico.openfit.core.SetValues
import com.eplico.openfit.core.Trophy
import com.eplico.openfit.core.WeightMath
import com.eplico.openfit.core.WeightMode
import com.eplico.openfit.core.WeightUnit
import com.eplico.openfit.data.Exercise
import com.eplico.openfit.data.HistorySet
import com.eplico.openfit.data.SettingsRepository
import com.eplico.openfit.data.UserSettings
import com.eplico.openfit.data.WorkoutEntryWithWorkout
import com.eplico.openfit.data.WorkoutRepository
import com.eplico.openfit.data.values
import com.eplico.openfit.ui.common.describe
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

    private val exerciseId = repository.observeEntry(entryId)
        .map { it?.exercise?.id }
        .filterNotNull()
        .distinctUntilChanged()

    val history: StateFlow<List<HistorySet>> = exerciseId
        .flatMapLatest { repository.observeHistory(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Trophies for every set of this exercise (today's and the history's), keyed by set id. */
    val trophies: StateFlow<Map<Long, Trophy>> = exerciseId
        .flatMapLatest { repository.observeTrophies(setOf(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // ---- Entry form ----

    var weightText by mutableStateOf("")
        private set
    var ratioText by mutableStateOf(WeightMath.format(WeightMath.DEFAULT_RATIO))
        private set
    var repsText by mutableStateOf("")
        private set
    var minutesText by mutableStateOf("")
        private set
    var secondsText by mutableStateOf("")
        private set
    var distanceText by mutableStateOf("")
        private set
    var distanceUnit by mutableStateOf(DistanceUnit.KM)
        private set

    /** Set currently loaded into the form for editing, or null when adding a new set. */
    var editingSetId by mutableStateOf<Long?>(null)
        private set

    /** Explains where the prefilled numbers came from, e.g. "From Sep 28: 60 kg × 8 reps". */
    var prefillNote by mutableStateOf<String?>(null)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    /** Unit the weight field is currently expressed in (tracks the workout unit for "Match workout"). */
    private var formUnit: WeightUnit? = null

    val exercise: Exercise? get() = entry?.exercise
    val measure: Measure get() = exercise?.measure ?: Measure.REPS
    val weightMode: WeightMode get() = exercise?.weightMode ?: WeightMode.WORKOUT
    val workoutUnit: WeightUnit get() = entry?.workout?.unit ?: settings.value.defaultUnit

    /** Unit the weight is entered in, or null when this exercise has no weight. */
    val unit: WeightUnit? get() = weightMode.unitFor(workoutUnit)

    val weight: Double? get() = if (weightText.isBlank()) 0.0 else WeightMath.parse(weightText)?.takeIf { it >= 0.0 }
    val ratio: Double? get() = WeightMath.parse(ratioText)?.takeIf { it > 0.0 }
    val reps: Int? get() = if (repsText.isBlank()) 0 else repsText.toIntOrNull()?.takeIf { it >= 0 }
    val durationSeconds: Int?
        get() {
            val minutes = if (minutesText.isBlank()) 0 else minutesText.toIntOrNull()?.takeIf { it >= 0 } ?: return null
            val seconds = if (secondsText.isBlank()) 0 else secondsText.toIntOrNull()?.takeIf { it >= 0 } ?: return null
            return minutes * 60 + seconds
        }
    val distance: Double? get() = if (distanceText.isBlank()) 0.0 else WeightMath.parse(distanceText)?.takeIf { it >= 0.0 }

    /** weight × ratio, or null while either field is invalid. */
    val calculatedWeight: Double?
        get() {
            val w = weight ?: return null
            val r = ratio ?: return null
            return WeightMath.calculatedWeight(w, r)
        }

    /** "5:06 /km" while both distance and time are filled in. */
    val pace: String?
        get() = Durations.pace(durationSeconds ?: 0, distance ?: 0.0, distanceUnit)

    init {
        viewModelScope.launch {
            repository.observeEntry(entryId).collect { loaded ->
                if (loaded == null) {
                    if (entry != null) closed = true
                    return@collect
                }
                val firstLoad = entry == null
                entry = loaded.copy(sets = loaded.sets.sortedBy { it.position })
                val newUnit = loaded.exercise.weightMode.unitFor(loaded.workout.unit)
                if (firstLoad) {
                    formUnit = newUnit
                    distanceUnit = settings.value.distanceUnit
                    prefill(loaded)
                } else if (newUnit != null && formUnit != null && newUnit != formUnit) {
                    convertWeight(formUnit!!, newUnit)
                    formUnit = newUnit
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
        fillForm(last.set.values)
        prefillNote = if (last.date == loaded.workout.date) {
            null
        } else {
            "From ${last.date.shortLabel()}: ${loaded.exercise.describe(last.set.values, last.workoutUnit)}"
        }
    }

    private fun fillForm(values: SetValues) {
        val shown = unit?.let { values.inUnit(it) } ?: values
        weightText = if (shown.weight > 0.0) WeightMath.format(shown.weight) else ""
        ratioText = WeightMath.format(shown.ratio, 3)
        repsText = shown.reps.takeIf { it > 0 }?.toString() ?: ""
        setTime(shown.durationSeconds)
        distanceText = shown.distance.takeIf { it > 0.0 }?.let { WeightMath.format(it) } ?: ""
        if (measure.usesDistance) distanceUnit = shown.distanceUnit
        error = null
    }

    private fun convertWeight(from: WeightUnit, to: WeightUnit) {
        val current = WeightMath.parse(weightText) ?: return
        weightText = WeightMath.format(WeightMath.convertForEntry(current, from, to))
    }

    fun onWeightChange(text: String) = edit { weightText = text }

    fun onRatioChange(text: String) = edit { ratioText = text }

    fun onRepsChange(text: String) = edit { repsText = text }

    fun onMinutesChange(text: String) = edit { minutesText = text }

    fun onSecondsChange(text: String) = edit { secondsText = text.take(2) }

    private fun setTime(totalSeconds: Int) {
        minutesText = if (totalSeconds > 0) (totalSeconds / 60).toString() else ""
        secondsText = if (totalSeconds > 0) (totalSeconds % 60).toString().padStart(2, '0') else ""
    }

    fun onDistanceChange(text: String) = edit { distanceText = text }

    fun onDistanceUnitChange(target: DistanceUnit) = edit {
        val current = WeightMath.parse(distanceText)
        if (current != null && target != distanceUnit) {
            distanceText = WeightMath.format(distanceUnit.convert(current, target))
        }
        distanceUnit = target
    }

    private inline fun edit(block: () -> Unit) {
        block()
        error = null
    }

    fun stepWeight(direction: Int) = edit {
        val step = settings.value.increment(unit ?: return@edit)
        val next = ((WeightMath.parse(weightText) ?: 0.0) + direction * step).coerceAtLeast(0.0)
        weightText = WeightMath.format(WeightMath.round(next, 3))
    }

    fun stepRatio(direction: Int) = edit {
        val next = (WeightMath.parse(ratioText) ?: WeightMath.DEFAULT_RATIO) + direction * RATIO_STEP
        if (next > 0.0) ratioText = WeightMath.format(next, 3)
    }

    fun setRatio(value: Double) = edit { ratioText = WeightMath.format(value, 3) }

    fun stepReps(direction: Int) = edit {
        val next = ((repsText.toIntOrNull() ?: 0) + direction).coerceAtLeast(0)
        repsText = if (next == 0) "" else next.toString()
    }

    fun stepTime(direction: Int) = edit {
        val step = if (measure == Measure.TIME) 15 else 30
        setTime(((durationSeconds ?: 0) + direction * step).coerceAtLeast(0))
    }

    fun stepDistance(direction: Int) = edit {
        val current = WeightMath.parse(distanceText) ?: 0.0
        // Metres go up by 10 m to 100 m, then 50 m; km and miles by 0.1 to 1, then 0.5.
        val (fine, coarse, switchAt) = if (distanceUnit == DistanceUnit.M) Triple(10.0, 50.0, 100.0) else Triple(0.1, 0.5, 1.0)
        val step = if (current < switchAt || (current == switchAt && direction < 0)) fine else coarse
        val next = WeightMath.round((current + direction * step).coerceAtLeast(0.0), 2)
        distanceText = if (next == 0.0) "" else WeightMath.format(next)
    }

    fun clearForm() {
        weightText = ""
        ratioText = WeightMath.format(WeightMath.DEFAULT_RATIO)
        repsText = ""
        minutesText = ""
        secondsText = ""
        distanceText = ""
        editingSetId = null
        prefillNote = null
        error = null
    }

    fun save() {
        val current = entry ?: return
        val values = buildSet() ?: return
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

    /** Validates the form for this exercise's type; sets [error] and returns null when something is missing. */
    private fun buildSet(): SetValues? {
        val tracksWeight = weightMode.tracksWeight
        val w = if (tracksWeight) weight else 0.0
        val r = if (tracksWeight) ratio else WeightMath.DEFAULT_RATIO
        val n = if (measure.usesReps) reps else 0
        val t = if (measure.usesTime) durationSeconds else 0
        val d = if (measure.usesDistance) distance else 0.0
        error = when {
            w == null -> "Enter a valid weight"
            r == null -> "Ratio must be greater than 0"
            n == null -> "Reps must be a whole number"
            t == null -> "Enter the time in minutes and seconds"
            d == null -> "Enter a valid distance"
            measure == Measure.REPS && n == 0 -> "Enter how many reps"
            measure == Measure.TIME && t == 0 -> "Enter a time"
            measure == Measure.DISTANCE && d == 0.0 -> "Enter a distance"
            measure == Measure.DISTANCE_TIME && d == 0.0 && t == 0 -> "Enter a distance or a time"
            else -> null
        }
        if (w == null || r == null || n == null || t == null || d == null || error != null) return null
        return SetValues(
            weight = w,
            unit = unit ?: workoutUnit,
            ratio = r,
            reps = n,
            durationSeconds = t,
            distance = d,
            distanceUnit = if (measure.usesDistance) distanceUnit else DistanceUnit.KM,
        )
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
        fillForm(set.values)
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
