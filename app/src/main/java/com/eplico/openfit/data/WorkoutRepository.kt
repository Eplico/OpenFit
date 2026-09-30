package com.eplico.openfit.data

import androidx.room.withTransaction
import com.eplico.openfit.core.SetValues
import com.eplico.openfit.core.WeightUnit
import com.eplico.openfit.core.backup.Backup
import com.eplico.openfit.core.backup.BackupExercise
import com.eplico.openfit.core.backup.BackupPreset
import com.eplico.openfit.core.backup.BackupWorkout
import com.eplico.openfit.core.backup.BackupWorkoutExercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class WorkoutRepository(
    private val db: OpenFitDatabase,
    private val settings: SettingsRepository,
) {
    private val exerciseDao = db.exerciseDao()
    private val workoutDao = db.workoutDao()
    private val setDao = db.setDao()
    private val presetDao = db.presetDao()

    // ---- Exercises ----

    val exercises: Flow<List<Exercise>> = exerciseDao.observeAll()

    suspend fun createExercise(name: String, category: String): Result<Long> {
        val cleanName = name.trim()
        val cleanCategory = category.trim().ifEmpty { "Other" }
        if (cleanName.isEmpty()) return Result.failure(IllegalArgumentException("Name can't be empty"))
        if (exerciseDao.findByName(cleanName) != null) {
            return Result.failure(IllegalArgumentException("\"$cleanName\" already exists"))
        }
        return Result.success(exerciseDao.insert(Exercise(name = cleanName, category = cleanCategory)))
    }

    suspend fun updateExercise(exercise: Exercise): Result<Unit> {
        val clean = exercise.copy(name = exercise.name.trim(), category = exercise.category.trim().ifEmpty { "Other" })
        if (clean.name.isEmpty()) return Result.failure(IllegalArgumentException("Name can't be empty"))
        val clash = exerciseDao.findByName(clean.name)
        if (clash != null && clash.id != clean.id) {
            return Result.failure(IllegalArgumentException("\"${clean.name}\" already exists"))
        }
        exerciseDao.update(clean)
        return Result.success(Unit)
    }

    /** Deletes the exercise along with every set ever logged for it. */
    suspend fun deleteExercise(exercise: Exercise) = exerciseDao.delete(exercise)

    // ---- Workouts ----

    fun observeDay(date: LocalDate): Flow<WorkoutWithEntries?> = workoutDao.observeDay(date)

    suspend fun getOrCreateWorkout(date: LocalDate): Workout {
        workoutDao.getByDate(date)?.let { return it }
        val defaultUnit = settings.current().defaultUnit
        return db.withTransaction {
            workoutDao.getByDate(date) ?: Workout(date = date, unit = defaultUnit).let { it.copy(id = workoutDao.insert(it)) }
        }
    }

    suspend fun setWorkoutUnit(date: LocalDate, unit: WeightUnit) {
        val workout = getOrCreateWorkout(date)
        workoutDao.setUnit(workout.id, unit)
    }

    suspend fun setWorkoutUnit(workoutId: Long, unit: WeightUnit) = workoutDao.setUnit(workoutId, unit)

    /** Adds [exerciseId] to the workout on [date] (creating the workout if needed) and returns its entry id. */
    suspend fun addExerciseToWorkout(date: LocalDate, exerciseId: Long): Long {
        val workout = getOrCreateWorkout(date)
        return db.withTransaction {
            workoutDao.findEntry(workout.id, exerciseId)?.id
                ?: workoutDao.insertEntry(
                    WorkoutExercise(
                        workoutId = workout.id,
                        exerciseId = exerciseId,
                        position = workoutDao.maxEntryPosition(workout.id) + 1,
                    ),
                )
        }
    }

    suspend fun removeFromWorkout(workoutExerciseId: Long) = workoutDao.deleteEntry(workoutExerciseId)

    /** Moves an exercise up ([delta] < 0) or down within its workout. */
    suspend fun moveInWorkout(workoutId: Long, workoutExerciseId: Long, delta: Int) = db.withTransaction {
        val ordered = workoutDao.entriesFor(workoutId).map { it.id }.toMutableList()
        if (reorder(ordered, workoutExerciseId, delta)) {
            ordered.forEachIndexed { index, id -> workoutDao.setEntryPosition(id, index) }
        }
    }

    /** Adds the preset's exercises (in order) to the workout on [date]. Returns how many were new. */
    suspend fun loadPreset(date: LocalDate, presetId: Long): Int {
        val preset = presetDao.get(presetId) ?: return 0
        val workout = getOrCreateWorkout(date)
        return db.withTransaction {
            var position = workoutDao.maxEntryPosition(workout.id) + 1
            var added = 0
            preset.orderedItems.forEach { item ->
                if (workoutDao.findEntry(workout.id, item.exercise.id) == null) {
                    workoutDao.insertEntry(
                        WorkoutExercise(workoutId = workout.id, exerciseId = item.exercise.id, position = position++),
                    )
                    added++
                }
            }
            added
        }
    }

    /** Saves the exercises of the workout on [date] as a new preset. Returns null if the day is empty. */
    suspend fun saveWorkoutAsPreset(date: LocalDate, name: String): Long? {
        val workout = workoutDao.getByDate(date) ?: return null
        return db.withTransaction {
            val entries = workoutDao.entriesFor(workout.id)
            if (entries.isEmpty()) return@withTransaction null
            val presetId = presetDao.insert(Preset(name = name.trim()))
            presetDao.insertItems(
                entries.mapIndexed { index, entry ->
                    PresetExercise(presetId = presetId, exerciseId = entry.exerciseId, position = index)
                },
            )
            presetId
        }
    }

    /** Set counts per day, used for the calendar heatmap. */
    fun observeSetCountsByDay(): Flow<Map<LocalDate, Int>> =
        workoutDao.observeSetCountsByDay().map { rows -> rows.associate { it.date to it.count } }

    // ---- Sets ----

    fun observeEntry(workoutExerciseId: Long): Flow<WorkoutEntryWithWorkout?> =
        workoutDao.observeEntry(workoutExerciseId)

    suspend fun addSet(workoutExerciseId: Long, values: SetValues): Long = db.withTransaction {
        setDao.insert(
            SetEntry(
                workoutExerciseId = workoutExerciseId,
                weight = values.weight,
                unit = values.unit,
                ratio = values.ratio,
                reps = values.reps,
                position = setDao.maxPosition(workoutExerciseId) + 1,
                loggedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun updateSet(set: SetEntry, values: SetValues) = setDao.update(
        set.copy(weight = values.weight, unit = values.unit, ratio = values.ratio, reps = values.reps),
    )

    suspend fun deleteSet(setId: Long) = setDao.delete(setId)

    /** The set used to prefill the entry form: the latest one logged on or before [date]. */
    suspend fun lastSet(exerciseId: Long, date: LocalDate): HistorySet? = setDao.lastSetOnOrBefore(exerciseId, date)

    fun observeHistory(exerciseId: Long): Flow<List<HistorySet>> = setDao.observeHistory(exerciseId)

    // ---- Presets ----

    val presets: Flow<List<PresetWithItems>> = presetDao.observeAll()

    fun observePreset(presetId: Long): Flow<PresetWithItems?> = presetDao.observe(presetId)

    suspend fun createPreset(name: String): Long = presetDao.insert(Preset(name = name.trim()))

    suspend fun renamePreset(presetId: Long, name: String) = presetDao.rename(presetId, name.trim())

    suspend fun deletePreset(presetId: Long) = presetDao.delete(presetId)

    suspend fun addExerciseToPreset(presetId: Long, exerciseId: Long) = db.withTransaction {
        presetDao.insertItem(
            PresetExercise(presetId = presetId, exerciseId = exerciseId, position = presetDao.maxItemPosition(presetId) + 1),
        )
    }

    suspend fun removePresetItem(itemId: Long) = presetDao.deleteItem(itemId)

    suspend fun movePresetItem(presetId: Long, itemId: Long, delta: Int) = db.withTransaction {
        val ordered = presetDao.get(presetId)?.orderedItems?.map { it.item.id }?.toMutableList() ?: return@withTransaction
        if (reorder(ordered, itemId, delta)) {
            ordered.forEachIndexed { index, id -> presetDao.setItemPosition(id, index) }
        }
    }

    // ---- Export / import ----

    /** A consistent snapshot of everything in the database. */
    suspend fun exportBackup(): Backup = db.withTransaction {
        Backup(
            exercises = exerciseDao.getAll().map { it.toBackup() },
            workouts = workoutDao.getAllWithEntries().mapNotNull { day ->
                val entries = day.entries.sortedBy { it.entry.position }
                if (entries.isEmpty()) return@mapNotNull null
                BackupWorkout(
                    date = day.workout.date,
                    unit = day.workout.unit,
                    exercises = entries.map { entry ->
                        BackupWorkoutExercise(entry.exercise.toBackup(), entry.sets.sortedBy { it.position }.map { it.values })
                    },
                )
            },
            presets = presetDao.getAll().map { preset ->
                BackupPreset(preset.preset.name, preset.orderedItems.map { it.exercise.toBackup() })
            },
        )
    }

    /**
     * Merges [backup] into the database without deleting anything, so importing the same file twice
     * is harmless. Exercises and presets are matched by name (ignoring case). For each exercise on
     * each day, sets are only added if the app has no sets logged for it yet.
     */
    suspend fun importBackup(backup: Backup): ImportSummary = db.withTransaction {
        val exerciseIds = HashMap<String, Long>()
        exerciseDao.getAll().forEach { exerciseIds[it.name.lowercase()] = it.id }
        var exercisesAdded = 0

        suspend fun exerciseId(exercise: BackupExercise): Long {
            val name = exercise.name.trim()
            exerciseIds[name.lowercase()]?.let { return it }
            val id = exerciseDao.insert(Exercise(name = name, category = exercise.category.trim().ifEmpty { "Other" }))
            exerciseIds[name.lowercase()] = id
            exercisesAdded++
            return id
        }

        backup.exercises.filter { it.name.isNotBlank() }.forEach { exerciseId(it) }

        var setsAdded = 0
        var skipped = 0
        val daysWithNewSets = HashSet<LocalDate>()
        val now = System.currentTimeMillis()
        for (day in backup.workouts) {
            val workout = workoutDao.getByDate(day.date)
                ?: Workout(date = day.date, unit = day.unit).let { it.copy(id = workoutDao.insert(it)) }
            var nextPosition = workoutDao.maxEntryPosition(workout.id) + 1
            for (item in day.exercises) {
                if (item.exercise.name.isBlank()) continue
                val exId = exerciseId(item.exercise)
                val entryId = workoutDao.findEntry(workout.id, exId)?.id
                    ?: workoutDao.insertEntry(WorkoutExercise(workoutId = workout.id, exerciseId = exId, position = nextPosition++))
                if (item.sets.isEmpty()) continue
                if (setDao.countFor(entryId) > 0) {
                    skipped++
                    continue
                }
                item.sets.forEachIndexed { index, set ->
                    setDao.insert(
                        SetEntry(
                            workoutExerciseId = entryId,
                            weight = set.weight,
                            unit = set.unit,
                            ratio = set.ratio,
                            reps = set.reps,
                            position = index,
                            loggedAt = now,
                        ),
                    )
                }
                setsAdded += item.sets.size
                daysWithNewSets += day.date
            }
        }

        var presetsAdded = 0
        var presetsSkipped = 0
        for (preset in backup.presets) {
            val name = preset.name.trim()
            if (name.isEmpty()) continue
            if (presetDao.findByName(name) != null) {
                presetsSkipped++
                continue
            }
            val presetId = presetDao.insert(Preset(name = name))
            presetDao.insertItems(
                preset.exercises.filter { it.name.isNotBlank() }.mapIndexed { index, exercise ->
                    PresetExercise(presetId = presetId, exerciseId = exerciseId(exercise), position = index)
                },
            )
            presetsAdded++
        }

        ImportSummary(
            setsAdded = setsAdded,
            daysWithNewSets = daysWithNewSets.size,
            exercisesAdded = exercisesAdded,
            presetsAdded = presetsAdded,
            presetsSkipped = presetsSkipped,
            exerciseDaysSkipped = skipped,
        )
    }

    private fun Exercise.toBackup() = BackupExercise(name = name, category = category)

    private fun reorder(ids: MutableList<Long>, id: Long, delta: Int): Boolean {
        val from = ids.indexOf(id)
        if (from < 0) return false
        val to = (from + delta).coerceIn(0, ids.lastIndex)
        if (to == from) return false
        ids.add(to, ids.removeAt(from))
        return true
    }
}
