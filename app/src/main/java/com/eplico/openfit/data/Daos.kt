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
    /** Grouped the way the picker shows them: by category order, then name. */
    @Query(
        """
        SELECT e.* FROM exercises e
        LEFT JOIN categories c ON c.name = e.category COLLATE NOCASE
        ORDER BY COALESCE(c.position, 1000000), e.category COLLATE NOCASE, e.name COLLATE NOCASE
        """,
    )
    fun observeAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun get(id: Long): Exercise?

    @Query("UPDATE exercises SET category = :newName WHERE category = :oldName COLLATE NOCASE")
    suspend fun moveCategory(oldName: String, newName: String)

    @Query("SELECT * FROM exercises WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Exercise?

    @Query("SELECT * FROM exercises")
    suspend fun getAll(): List<Exercise>

    @Insert
    suspend fun insert(exercise: Exercise): Long

    @Update
    suspend fun update(exercise: Exercise)

    @Delete
    suspend fun delete(exercise: Exercise)
}

@Dao
interface CategoryDao {
    @Query(
        """
        SELECT c.*, (SELECT COUNT(*) FROM exercises e WHERE e.category = c.name COLLATE NOCASE) AS exerciseCount
        FROM categories c
        ORDER BY c.position, c.name COLLATE NOCASE
        """,
    )
    fun observeWithCounts(): Flow<List<CategoryWithCount>>

    @Query("SELECT * FROM categories ORDER BY position, name COLLATE NOCASE")
    suspend fun getAll(): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: Long): Category?

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Category?

    @Query("SELECT COALESCE(MAX(position), -1) FROM categories")
    suspend fun maxPosition(): Int

    @Insert
    suspend fun insert(category: Category): Long

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE categories SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: LocalDate): Workout?

    @Transaction
    @Query("SELECT * FROM workouts WHERE date = :date LIMIT 1")
    fun observeDay(date: LocalDate): Flow<WorkoutWithEntries?>

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY date")
    suspend fun getAllWithEntries(): List<WorkoutWithEntries>

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

    @Query("SELECT COUNT(*) FROM sets WHERE workoutExerciseId = :workoutExerciseId")
    suspend fun countFor(workoutExerciseId: Long): Int

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

    /** Every set of the given rep-based exercises, oldest first, for working out trophies. */
    @Query(
        """
        SELECT s.id AS setId, we.exerciseId AS exerciseId, s.weight AS weight, s.unit AS unit, s.ratio AS ratio, s.reps AS reps
        FROM sets s
        JOIN workout_exercises we ON s.workoutExerciseId = we.id
        JOIN workouts w ON we.workoutId = w.id
        JOIN exercises e ON e.id = we.exerciseId
        WHERE we.exerciseId IN (:exerciseIds) AND e.measure = 'REPS'
        ORDER BY we.exerciseId, w.date, we.position, s.position
        """,
    )
    fun observeTrophyRows(exerciseIds: List<Long>): Flow<List<TrophyRow>>
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

    @Transaction
    @Query("SELECT * FROM presets ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<PresetWithItems>

    @Query("SELECT * FROM presets WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Preset?

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
