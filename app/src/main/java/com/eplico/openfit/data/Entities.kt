package com.eplico.openfit.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.eplico.openfit.core.DistanceUnit
import com.eplico.openfit.core.Measure
import com.eplico.openfit.core.SetValues
import com.eplico.openfit.core.WeightMode
import com.eplico.openfit.core.WeightUnit
import java.time.LocalDate

@Entity(
    tableName = "exercises",
    indices = [Index(value = ["name"], unique = true)],
)
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Name of a row in [Category]; the repository keeps the two in step. */
    val category: String,
    /** What each set records besides weight: reps, time, distance, or distance + time. */
    val measure: Measure = Measure.REPS,
    /** Whether the exercise has a weight, and whether it follows the workout's kg/lb or is fixed. */
    val weightMode: WeightMode = WeightMode.WORKOUT,
)

/** A user-editable exercise category. [position] orders the picker and the categories screen. */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)],
)
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int,
)

/** One training day. Every day has at most one workout, and each workout has its own unit. */
@Entity(
    tableName = "workouts",
    indices = [Index(value = ["date"], unique = true)],
)
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val unit: WeightUnit,
)

/** An exercise placed in a workout; its sets hang off this row. */
@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["workoutId", "exerciseId"], unique = true),
        Index(value = ["exerciseId"]),
    ],
)
data class WorkoutExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val position: Int,
)

/**
 * A logged set. [weight] is stored exactly as typed, in [unit]; the calculated weight is
 * [weight] × [ratio]. Displaying in a different unit converts on the fly. Fields the exercise
 * doesn't use (e.g. reps for a run) are 0.
 */
@Entity(
    tableName = "sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExercise::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["workoutExerciseId"])],
)
data class SetEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val weight: Double,
    val unit: WeightUnit,
    val ratio: Double,
    val reps: Int,
    val position: Int,
    val loggedAt: Long,
    val durationSeconds: Int = 0,
    val distance: Double = 0.0,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
)

val SetEntry.values: SetValues
    get() = SetValues(
        weight = weight,
        unit = unit,
        ratio = ratio,
        reps = reps,
        durationSeconds = durationSeconds,
        distance = distance,
        distanceUnit = distanceUnit,
    )

@Entity(tableName = "presets")
data class Preset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "preset_exercises",
    foreignKeys = [
        ForeignKey(
            entity = Preset::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["presetId"]), Index(value = ["exerciseId"])],
)
data class PresetExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val presetId: Long,
    val exerciseId: Long,
    val position: Int,
)

// ---- Query result shapes ----

data class WorkoutEntry(
    @Embedded val entry: WorkoutExercise,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: Exercise,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val sets: List<SetEntry>,
)

data class WorkoutWithEntries(
    @Embedded val workout: Workout,
    @Relation(entity = WorkoutExercise::class, parentColumn = "id", entityColumn = "workoutId")
    val entries: List<WorkoutEntry>,
)

/** A workout exercise together with the workout it belongs to (for the logging screen). */
data class WorkoutEntryWithWorkout(
    @Embedded val entry: WorkoutExercise,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: Exercise,
    @Relation(parentColumn = "workoutId", entityColumn = "id")
    val workout: Workout,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val sets: List<SetEntry>,
)

data class PresetItem(
    @Embedded val item: PresetExercise,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: Exercise,
)

data class PresetWithItems(
    @Embedded val preset: Preset,
    @Relation(entity = PresetExercise::class, parentColumn = "id", entityColumn = "presetId")
    val items: List<PresetItem>,
)

val PresetWithItems.orderedItems: List<PresetItem>
    get() = items.sortedBy { it.item.position }

data class CategoryWithCount(
    @Embedded val category: Category,
    val exerciseCount: Int,
)

data class DayCount(
    val date: LocalDate,
    val count: Int,
)

/** A logged set plus the day it was logged on and that day's display unit. */
data class HistorySet(
    @Embedded val set: SetEntry,
    val date: LocalDate,
    val workoutUnit: WeightUnit,
)
