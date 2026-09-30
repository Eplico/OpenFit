package com.eplico.openfit.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eplico.openfit.core.SetValues
import com.eplico.openfit.core.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class WorkoutRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: OpenFitDatabase
    private lateinit var repository: WorkoutRepository

    private val day1 = LocalDate.of(2026, 9, 28)
    private val day2 = LocalDate.of(2026, 9, 29)
    private val day3 = LocalDate.of(2026, 9, 30)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = OpenFitDatabase.inMemory(context)
        repository = WorkoutRepository(db, SettingsRepository(context))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun exercise(name: String): Exercise = repository.exercises.first().first { it.name == name }

    private suspend fun dayNames(date: LocalDate): List<String> =
        repository.observeDay(date).first()?.entries.orEmpty().sortedBy { it.entry.position }.map { it.exercise.name }

    @Test
    fun seedsStarterExercises() = runBlocking {
        val all = repository.exercises.first()
        assertTrue(all.size > 40)
        assertTrue(all.any { it.name == "Barbell Squat" && it.category == "Legs" })
    }

    @Test
    fun duplicateExerciseNamesAreRejected() = runBlocking {
        assertTrue(repository.createExercise("barbell squat", "Legs").isFailure)
        assertTrue(repository.createExercise("  ", "Legs").isFailure)
        val created = repository.createExercise(" Zercher Squat ", "Legs")
        assertTrue(created.isSuccess)
        assertEquals("Zercher Squat", exercise("Zercher Squat").name)
    }

    @Test
    fun newWorkoutUsesDefaultUnitAndCanBeSwitched() = runBlocking {
        val defaultUnit = SettingsRepository(context).current().defaultUnit
        repository.addExerciseToWorkout(day1, exercise("Barbell Squat").id)
        assertEquals(defaultUnit, repository.observeDay(day1).first()!!.workout.unit)

        repository.setWorkoutUnit(day1, WeightUnit.LB)
        assertEquals(WeightUnit.LB, repository.observeDay(day1).first()!!.workout.unit)
        repository.setWorkoutUnit(day1, WeightUnit.KG)
        assertEquals(WeightUnit.KG, repository.observeDay(day1).first()!!.workout.unit)
    }

    @Test
    fun addingTheSameExerciseTwiceReusesTheEntry() = runBlocking {
        val squat = exercise("Barbell Squat")
        val first = repository.addExerciseToWorkout(day1, squat.id)
        val second = repository.addExerciseToWorkout(day1, squat.id)
        assertEquals(first, second)
        assertEquals(listOf("Barbell Squat"), dayNames(day1))
    }

    @Test
    fun lastSetComesFromTheMostRecentSetOnOrBeforeTheDay() = runBlocking {
        val squat = exercise("Barbell Squat")
        val e1 = repository.addExerciseToWorkout(day1, squat.id)
        repository.addSet(e1, SetValues(100.0, WeightUnit.KG, 1.0, 5))
        repository.addSet(e1, SetValues(60.0, WeightUnit.KG, 2.0, 8))
        val e3 = repository.addExerciseToWorkout(day3, squat.id)
        repository.addSet(e3, SetValues(225.0, WeightUnit.LB, 1.0, 3))

        val forDay2 = repository.lastSet(squat.id, day2)!!
        assertEquals(day1, forDay2.date)
        assertEquals(60.0, forDay2.set.weight, 0.0)
        assertEquals(2.0, forDay2.set.ratio, 0.0)
        assertEquals(8, forDay2.set.reps)

        val forDay3 = repository.lastSet(squat.id, day3)!!
        assertEquals(225.0, forDay3.set.weight, 0.0)
        assertEquals(WeightUnit.LB, forDay3.set.unit)

        assertNull(repository.lastSet(squat.id, day1.minusDays(1)))
        assertNull(repository.lastSet(exercise("Deadlift").id, day3))
    }

    @Test
    fun updateAndDeleteSets() = runBlocking {
        val entryId = repository.addExerciseToWorkout(day1, exercise("Leg Press").id)
        repository.addSet(entryId, SetValues(100.0, WeightUnit.KG, 1.0, 10))
        val set = repository.observeEntry(entryId).first()!!.sets.single()

        repository.updateSet(set, SetValues(90.0, WeightUnit.LB, 2.0, 12))
        val updated = repository.observeEntry(entryId).first()!!.sets.single()
        assertEquals(90.0, updated.weight, 0.0)
        assertEquals(WeightUnit.LB, updated.unit)
        assertEquals(2.0, updated.ratio, 0.0)
        assertEquals(12, updated.reps)

        repository.deleteSet(set.id)
        assertTrue(repository.observeEntry(entryId).first()!!.sets.isEmpty())
    }

    @Test
    fun presetsLoadInOrderAndSkipExercisesAlreadyInTheDay() = runBlocking {
        val bench = exercise("Flat Barbell Bench Press")
        val press = exercise("Overhead Press")
        val row = exercise("Barbell Row")
        val presetId = repository.createPreset("Upper")
        repository.addExerciseToPreset(presetId, bench.id)
        repository.addExerciseToPreset(presetId, press.id)
        repository.addExerciseToPreset(presetId, row.id)

        val rowItem = repository.observePreset(presetId).first()!!.orderedItems.last()
        repository.movePresetItem(presetId, rowItem.item.id, -1)
        assertEquals(
            listOf(bench.name, row.name, press.name),
            repository.observePreset(presetId).first()!!.orderedItems.map { it.exercise.name },
        )

        repository.addExerciseToWorkout(day1, press.id)
        assertEquals(2, repository.loadPreset(day1, presetId))
        assertEquals(listOf(press.name, bench.name, row.name), dayNames(day1))
        assertEquals(0, repository.loadPreset(day1, presetId))
    }

    @Test
    fun savingADayAsAPresetKeepsItsOrder() = runBlocking {
        repository.addExerciseToWorkout(day1, exercise("Barbell Squat").id)
        repository.addExerciseToWorkout(day1, exercise("Romanian Deadlift").id)

        val presetId = repository.saveWorkoutAsPreset(day1, "  Legs ")!!
        val preset = repository.observePreset(presetId).first()!!
        assertEquals("Legs", preset.preset.name)
        assertEquals(listOf("Barbell Squat", "Romanian Deadlift"), preset.orderedItems.map { it.exercise.name })

        assertNull(repository.saveWorkoutAsPreset(day2, "Empty"))
    }

    @Test
    fun calendarCountsOnlyDaysWithSets() = runBlocking {
        val squat = exercise("Barbell Squat")
        val e1 = repository.addExerciseToWorkout(day1, squat.id)
        repository.addSet(e1, SetValues(100.0, WeightUnit.KG, 1.0, 5))
        repository.addSet(e1, SetValues(100.0, WeightUnit.KG, 1.0, 5))
        repository.addExerciseToWorkout(day2, squat.id) // planned, nothing logged
        val e3 = repository.addExerciseToWorkout(day3, squat.id)
        repository.addSet(e3, SetValues(100.0, WeightUnit.KG, 1.0, 5))

        assertEquals(mapOf(day1 to 2, day3 to 1), repository.observeSetCountsByDay().first())
    }

    @Test
    fun reorderAndRemoveWithinAWorkout() = runBlocking {
        val a = repository.addExerciseToWorkout(day1, exercise("Barbell Squat").id)
        repository.addExerciseToWorkout(day1, exercise("Leg Press").id)
        val c = repository.addExerciseToWorkout(day1, exercise("Leg Extension").id)
        val workoutId = repository.observeDay(day1).first()!!.workout.id

        repository.moveInWorkout(workoutId, c, -1)
        assertEquals(listOf("Barbell Squat", "Leg Extension", "Leg Press"), dayNames(day1))

        repository.removeFromWorkout(a)
        assertEquals(listOf("Leg Extension", "Leg Press"), dayNames(day1))
    }

    @Test
    fun historyIsNewestDayFirstWithSetsInOrder() = runBlocking {
        val curl = exercise("Barbell Curl")
        val e1 = repository.addExerciseToWorkout(day1, curl.id)
        repository.addSet(e1, SetValues(30.0, WeightUnit.KG, 1.0, 10))
        repository.addSet(e1, SetValues(32.5, WeightUnit.KG, 1.0, 8))
        val e2 = repository.addExerciseToWorkout(day2, curl.id)
        repository.addSet(e2, SetValues(35.0, WeightUnit.KG, 1.0, 6))

        val history = repository.observeHistory(curl.id).first()
        assertEquals(listOf(day2, day1, day1), history.map { it.date })
        assertEquals(listOf(35.0, 30.0, 32.5), history.map { it.set.weight })
    }

    @Test
    fun deletingAnExerciseRemovesItsLoggedSets() = runBlocking {
        val curl = exercise("Hammer Curl")
        val entry = repository.addExerciseToWorkout(day1, curl.id)
        repository.addSet(entry, SetValues(20.0, WeightUnit.KG, 1.0, 12))

        repository.deleteExercise(curl)
        assertTrue(repository.observeSetCountsByDay().first().isEmpty())
        assertTrue(dayNames(day1).isEmpty())
    }
}
