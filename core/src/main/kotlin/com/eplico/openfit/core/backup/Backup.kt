package com.eplico.openfit.core.backup

import com.eplico.openfit.core.SetValues
import com.eplico.openfit.core.WeightUnit
import java.time.LocalDate

/** Everything OpenFit stores, in a database-independent shape used for export and import. */
data class Backup(
    val exercises: List<BackupExercise> = emptyList(),
    val workouts: List<BackupWorkout> = emptyList(),
    val presets: List<BackupPreset> = emptyList(),
) {
    val setCount: Int get() = workouts.sumOf { workout -> workout.exercises.sumOf { it.sets.size } }
}

data class BackupExercise(
    val name: String,
    /** Blank when unknown (e.g. a hand-made spreadsheet); the importer picks a fallback. */
    val category: String,
)

data class BackupWorkout(
    val date: LocalDate,
    val unit: WeightUnit,
    val exercises: List<BackupWorkoutExercise>,
)

/** An exercise on a given day. [sets] may be empty for an exercise that was planned but not logged. */
data class BackupWorkoutExercise(
    val exercise: BackupExercise,
    val sets: List<SetValues>,
)

data class BackupPreset(
    val name: String,
    val exercises: List<BackupExercise>,
)
