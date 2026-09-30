package com.eplico.openfit.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.eplico.openfit.core.WeightUnit
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY category COLLATE NOCASE, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Exercise?

    @Insert
    suspend fun insert(exercise: Exercise): Long

    @Update
    suspend fun update(exercise: Exercise)

    @Delete
    suspend fun delete(exercise: Exercise)
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: LocalDate): Workout?

    @Transaction
    @Query("SELECT * FROM workouts WHERE date = :date LIMIT 1")
    fun observeDay(date: LocalDate): Flow<WorkoutWithEntries?>

    @Insert
    suspend fun insert(workout: Workout): Long

    @Query("UPDATE workouts SET unit = :unit WHERE id = :workoutId")
    suspend fun setUnit(workoutId: Long, unit: WeightUnit)

    @Transaction
    @Query("SELECT * FROM workout_exercises WHERE id = :workoutExerciseId")
    fun observeEntry(workoutExerciseId: Long): Flow<WorkoutEntryWithWorkout?>

    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId AND exerciseId = :exerciseId LIMIT 1")
    suspend fun findEntry(workoutId: Long, exerciseId: Long): WorkoutExercise?

    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY position")
    suspend fun entriesFor(workoutId: Long): List<WorkoutExercise>

    @Query("SELECT COALESCE(MAX(position), -1) FROM workout_exercises WHERE workoutId = :workoutId")
    suspend fun maxEntryPosition(workoutId: Long): Int

    @Insert
    suspend fun insertEntry(entry: WorkoutExercise): Long

    @Query("DELETE FROM workout_exercises WHERE id = :workoutExerciseId")
    suspend fun deleteEntry(workoutExerciseId: Long)

    @Query("UPDATE workout_exercises SET position = :position WHERE id = :workoutExerciseId")
    suspend fun setEntryPosition(workoutExerciseId: Long, position: Int)

    /** Number of sets logged per day, for the heatmap. Days without sets are left out. */
    @Query(
        """
        SELECT w.date AS date, COUNT(s.id) AS count
        FROM workouts w
        JOIN workout_exercises we ON we.workoutId = w.id
        JOIN sets s ON s.workoutExerciseId = we.id
        GROUP BY w.date
        """,
    )
    fun observeSetCountsByDay(): Flow<List<DayCount>>
}

@Dao
interface SetDao {
    @Query("SELECT COALESCE(MAX(position), -1) FROM sets WHERE workoutExerciseId = :workoutExerciseId")
    suspend fun maxPosition(workoutExerciseId: Long): Int

    @Insert
    suspend fun insert(set: SetEntry): Long

    @Update
    suspend fun update(set: SetEntry)

    @Query("DELETE FROM sets WHERE id = :setId")
    suspend fun delete(setId: Long)

    /**
     * The most recent set of [exerciseId] logged on or before [onOrBefore]
     * (later days first, then the last set within that day).
     */
    @Query(
        """
        SELECT s.*, w.date AS date, w.unit AS workoutUnit FROM sets s
        JOIN workout_exercises we ON s.workoutExerciseId = we.id
        JOIN workouts w ON we.workoutId = w.id
        WHERE we.exerciseId = :exerciseId AND w.date <= :onOrBefore
        ORDER BY w.date DESC, s.position DESC
        LIMIT 1
        """,
    )
    suspend fun lastSetOnOrBefore(exerciseId: Long, onOrBefore: LocalDate): HistorySet?

    @Query(
        """
        SELECT s.*, w.date AS date, w.unit AS workoutUnit FROM sets s
        JOIN workout_exercises we ON s.workoutExerciseId = we.id
        JOIN workouts w ON we.workoutId = w.id
        WHERE we.exerciseId = :exerciseId
        ORDER BY w.date DESC, s.position ASC
        """,
    )
    fun observeHistory(exerciseId: Long): Flow<List<HistorySet>>
}

@Dao
interface PresetDao {
    @Transaction
    @Query("SELECT * FROM presets ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<PresetWithItems>>

    @Transaction
    @Query("SELECT * FROM presets WHERE id = :presetId")
    fun observe(presetId: Long): Flow<PresetWithItems?>

    @Transaction
    @Query("SELECT * FROM presets WHERE id = :presetId")
    suspend fun get(presetId: Long): PresetWithItems?

    @Insert
    suspend fun insert(preset: Preset): Long

    @Query("UPDATE presets SET name = :name WHERE id = :presetId")
    suspend fun rename(presetId: Long, name: String)

    @Query("DELETE FROM presets WHERE id = :presetId")
    suspend fun delete(presetId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM preset_exercises WHERE presetId = :presetId")
    suspend fun maxItemPosition(presetId: Long): Int

    @Insert
    suspend fun insertItem(item: PresetExercise): Long

    @Insert
    suspend fun insertItems(items: List<PresetExercise>)

    @Query("DELETE FROM preset_exercises WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long)

    @Query("UPDATE preset_exercises SET position = :position WHERE id = :itemId")
    suspend fun setItemPosition(itemId: Long, position: Int)
}
