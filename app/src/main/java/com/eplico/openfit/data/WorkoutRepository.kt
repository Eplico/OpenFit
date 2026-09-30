package com.eplico.openfit.data

import androidx.room.withTransaction
import com.eplico.openfit.core.Measure
import com.eplico.openfit.core.SetValues
import com.eplico.openfit.core.Trophies
import com.eplico.openfit.core.Trophy
import com.eplico.openfit.core.TrophySet
import com.eplico.openfit.core.WeightMode
import com.eplico.openfit.core.WeightUnit
import com.eplico.openfit.core.backup.Backup
import com.eplico.openfit.core.backup.BackupExercise
import com.eplico.openfit.core.backup.BackupPreset
import com.eplico.openfit.core.backup.BackupWorkout
import com.eplico.openfit.core.backup.BackupWorkoutExercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class WorkoutRepository(
    private val db: OpenFitDatabase,
    private val settings: SettingsRepository,
) {
    private val exerciseDao = db.exerciseDao()
    private val categoryDao = db.categoryDao()
    private val workoutDao = db.workoutDao()
    private val setDao = db.setDao()
    private val presetDao = db.presetDao()

    // ---- Exercises ----

    val exercises: Flow<List<Exercise>> = exerciseDao.observeAll()

    suspend fun getExercise(id: Long): Exercise? = exerciseDao.get(id)

    suspend fun createExercise(
        name: String,
        category: String,
        measure: Measure = Measure.REPS,
        weightMode: WeightMode = WeightMode.WORKOUT,
    ): Result<Long> = db.withTransaction {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return@withTransaction Result.failure(IllegalArgumentException("Name can't be empty"))
        if (exerciseDao.findByName(cleanName) != null) {
            return@withTransaction Result.failure(IllegalArgumentException("\"$cleanName\" already exists"))
        }
        val exercise = Exercise(name = cleanName, category = ensureCategory(category), measure = measure, weightMode = weightMode)
        Result.success(exerciseDao.insert(exercise))
    }

    suspend fun updateExercise(exercise: Exercise): Result<Unit> = db.withTransaction {
        val cleanName = exercise.name.trim()
        if (cleanName.isEmpty()) return@withTransaction Result.failure(IllegalArgumentException("Name can't be empty"))
        val clash = exerciseDao.findByName(cleanName)
        if (clash != null && clash.id != exercise.id) {
            return@withTransaction Result.failure(IllegalArgumentException("\"$cleanName\" already exists"))
        }
        exerciseDao.update(exercise.copy(name = cleanName, category = ensureCategory(exercise.category)))
        Result.success(Unit)
    }

    /** Deletes the exercise along with every set ever logged for it. */
    suspend fun deleteExercise(exercise: Exercise) = exerciseDao.delete(exercise)

    // ---- Categories ----

    val categories: Flow<List<CategoryWithCount>> = categoryDao.observeWithCounts()

    /** Returns the stored spelling of [name], creating the category if it doesn't exist. Blank means "Other". */
    private suspend fun ensureCategory(name: String): String {
        val clean = name.trim().ifEmpty { DefaultExercises.OTHER }
        categoryDao.findByName(clean)?.let { return it.name }
        categoryDao.insert(Category(name = clean, position = categoryDao.maxPosition() + 1))
        return clean
    }

    suspend fun createCategory(name: String): Result<Long> = db.withTransaction {
        val clean = name.trim()
        when {
            clean.isEmpty() -> Result.failure(IllegalArgumentException("Name can't be empty"))
            categoryDao.findByName(clean) != null -> Result.failure(IllegalArgumentException("\"$clean\" already exists"))
            else -> Result.success(categoryDao.insert(Category(name = clean, position = categoryDao.maxPosition() + 1)))
        }
    }

    /** Renames a category and moves its exercises along with it. */
    suspend fun renameCategory(id: Long, name: String): Result<Unit> = db.withTransaction {
        val clean = name.trim()
        val category = categoryDao.get(id) ?: return@withTransaction Result.failure(IllegalArgumentException("Category not found"))
        val clash = categoryDao.findByName(clean)
        when {
            clean.isEmpty() -> Result.failure(IllegalArgumentException("Name can't be empty"))
            clash != null && clash.id != id -> Result.failure(IllegalArgumentException("\"$clean\" already exists"))
            else -> {
                categoryDao.rename(id, clean)
                exerciseDao.moveCategory(category.name, clean)
                Result.success(Unit)
            }
        }
    }

    /** Deletes a category; its exercises move to "Other". "Other" itself can't be deleted. */
    suspend fun deleteCategory(id: Long): Result<Unit> = db.withTransaction {
        val category = categoryDao.get(id) ?: return@withTransaction Result.success(Unit)
        if (category.name.equals(DefaultExercises.OTHER, ignoreCase = true)) {
            return@withTransaction Result.failure(IllegalArgumentException("\"${category.name}\" can't be deleted"))
        }
        val other = ensureCategory(DefaultExercises.OTHER)
        exerciseDao.moveCategory(category.name, other)
        categoryDao.delete(id)
        Result.success(Unit)
    }

    suspend fun moveCategory(id: Long, delta: Int) = db.withTransaction {
        val ordered = categoryDao.getAll().map { it.id }.toMutableList()
        if (reorder(ordered, id, delta)) {
            ordered.forEachIndexed { index, categoryId -> categoryDao.setPosition(categoryId, index) }
        }
    }

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
                durationSeconds = values.durationSeconds,
                distance = values.distance,
                distanceUnit = values.distanceUnit,
            ),
        )
    }

    suspend fun updateSet(set: SetEntry, values: SetValues) = setDao.update(
        set.copy(
            weight = values.weight,
            unit = values.unit,
            ratio = values.ratio,
            reps = values.reps,
            durationSeconds = values.durationSeconds,
            distance = values.distance,
            distanceUnit = values.distanceUnit,
        ),
    )

    suspend fun deleteSet(setId: Long) = setDao.delete(setId)

    /** The set used to prefill the entry form: the latest one logged on or before [date]. */
    suspend fun lastSet(exerciseId: Long, date: LocalDate): HistorySet? = setDao.lastSetOnOrBefore(exerciseId, date)

    fun observeHistory(exerciseId: Long): Flow<List<HistorySet>> = setDao.observeHistory(exerciseId)

    /**
     * Trophies for every set of the given exercises, keyed by set id. Each set is judged against the
     * exercise's earlier sets by calculated weight (weight × ratio, in kg), so it keeps the trophy it
     * earned. Only rep-based exercises get trophies.
     */
    fun observeTrophies(exerciseIds: Set<Long>): Flow<Map<Long, Trophy>> {
        if (exerciseIds.isEmpty()) return flowOf(emptyMap())
        return setDao.observeTrophyRows(exerciseIds.toList()).map { rows ->
            val awarded = HashMap<Long, Trophy>()
            rows.groupBy { it.exerciseId }.values.forEach { sets ->
                awarded += Trophies.award(sets.map { TrophySet(it.setId, it.weight, it.unit, it.ratio, it.reps) })
            }
            awarded
        }
    }

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
            categories = categoryDao.getAll().map { it.name },
        )
    }

    /**
     * Categories the import would introduce: ones named in the file's Categories sheet or used by
     * exercises the app doesn't have yet, that don't exist in the app. Exercises the app already
     * has keep their own category, so theirs don't count.
     */
    suspend fun unknownCategories(backup: Backup): List<String> {
        val known = categoryDao.getAll().map { it.name.lowercase() }.toSet()
        val existing = exerciseDao.getAll().map { it.name.lowercase() }.toSet()
        val existingExercises = existing + renamedStarterAliases(existing, backup).keys
        val newExercises = backup.allExercises().filter { it.name.trim().lowercase() !in existingExercises }
        return (backup.categories + newExercises.map { it.category })
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.lowercase() !in known }
            .distinctBy { it.lowercase() }
    }

    /**
     * Merges [backup] into the database without deleting anything, so importing the same file twice
     * is harmless. Exercises and presets are matched by name (ignoring case). For each exercise on
     * each day, sets are only added if the app has no sets logged for it yet.
     *
     * Categories the app doesn't have (see [unknownCategories]) are created when [addUnknownCategories]
     * is true; otherwise new exercises in them go to "Other".
     */
    private fun Backup.allExercises(): List<BackupExercise> =
        exercises + workouts.flatMap { day -> day.exercises.map { it.exercise } } + presets.flatMap { it.exercises }

    /**
     * Spreadsheets saved before some starter exercises were renamed (see
     * [DefaultExercises.renamedInVersion3]) still use the old names. Maps each old name (lowercase)
     * to the new one, so its sets land on the renamed exercise instead of a duplicate. Only applies
     * when the app has the new name but not the old one, and the spreadsheet doesn't use the new name.
     */
    private fun renamedStarterAliases(existing: Set<String>, backup: Backup): Map<String, String> {
        val incoming = backup.allExercises().map { it.name.trim().lowercase() }.toSet()
        return DefaultExercises.renamedInVersion3
            .map { (oldName, newName) -> oldName.lowercase() to newName.lowercase() }
            .filter { (oldName, newName) -> oldName !in existing && newName in existing && newName !in incoming }
            .toMap()
    }

    suspend fun importBackup(backup: Backup, addUnknownCategories: Boolean = true): ImportSummary = db.withTransaction {
        val knownCategories = HashMap<String, String>()
        categoryDao.getAll().forEach { knownCategories[it.name.lowercase()] = it.name }
        var categoriesAdded = 0

        suspend fun categoryFor(raw: String): String {
            val name = raw.trim()
            if (name.isNotEmpty()) knownCategories[name.lowercase()]?.let { return it }
            if (name.isEmpty() || !addUnknownCategories) return ensureCategory(DefaultExercises.OTHER)
            val created = ensureCategory(name)
            knownCategories[name.lowercase()] = created
            categoriesAdded++
            return created
        }

        if (addUnknownCategories) backup.categories.filter { it.isNotBlank() }.forEach { categoryFor(it) }

        val exerciseIds = HashMap<String, Long>()
        exerciseDao.getAll().forEach { exerciseIds[it.name.lowercase()] = it.id }
        renamedStarterAliases(exerciseIds.keys.toSet(), backup).forEach { (oldName, newName) ->
            exerciseIds[oldName] = exerciseIds.getValue(newName)
        }
        var exercisesAdded = 0

        suspend fun exerciseId(exercise: BackupExercise): Long {
            val name = exercise.name.trim()
            exerciseIds[name.lowercase()]?.let { return it }
            val id = exerciseDao.insert(
                Exercise(
                    name = name,
                    category = categoryFor(exercise.category),
                    measure = exercise.measure,
                    weightMode = exercise.weightMode,
                ),
            )
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
                            durationSeconds = set.durationSeconds,
                            distance = set.distance,
                            distanceUnit = set.distanceUnit,
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
            categoriesAdded = categoriesAdded,
        )
    }

    private fun Exercise.toBackup() =
        BackupExercise(name = name, category = category, measure = measure, weightMode = weightMode)

    private fun reorder(ids: MutableList<Long>, id: Long, delta: Int): Boolean {
        val from = ids.indexOf(id)
        if (from < 0) return false
        val to = (from + delta).coerceIn(0, ids.lastIndex)
        if (to == from) return false
        ids.add(to, ids.removeAt(from))
        return true
    }
}
