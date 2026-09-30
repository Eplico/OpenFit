package com.eplico.openfit.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eplico.openfit.core.SetValues
import com.eplico.openfit.core.WeightUnit
import com.eplico.openfit.core.backup.Backup
import com.eplico.openfit.core.backup.BackupSpreadsheet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BackupTest {
    private lateinit var context: Context
    private lateinit var sourceDb: OpenFitDatabase
    private lateinit var targetDb: OpenFitDatabase
    private lateinit var source: WorkoutRepository
    private lateinit var target: WorkoutRepository

    private val day1 = LocalDate.of(2026, 9, 28)
    private val day2 = LocalDate.of(2026, 9, 30)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sourceDb = OpenFitDatabase.inMemory(context)
        targetDb = OpenFitDatabase.inMemory(context)
        source = WorkoutRepository(sourceDb, SettingsRepository(context))
        target = WorkoutRepository(targetDb, SettingsRepository(context))
    }

    @After
    fun tearDown() {
        sourceDb.close()
        targetDb.close()
    }

    private suspend fun WorkoutRepository.exerciseId(name: String): Long = exercises.first().first { it.name == name }.id

    /** Squat on day 1 (2 sets, day shown in lb), a custom exercise + a planned one on day 2, two presets. */
    private suspend fun logSampleData() {
        val squat = source.exerciseId("Barbell Squat")
        val belt = source.createExercise("Belt Squat", "Legs").getOrThrow()
        val e1 = source.addExerciseToWorkout(day1, squat)
        source.addSet(e1, SetValues(100.0, WeightUnit.KG, 1.0, 5))
        source.addSet(e1, SetValues(60.0, WeightUnit.KG, 2.0, 8))
        source.setWorkoutUnit(day1, WeightUnit.LB)
        val e2 = source.addExerciseToWorkout(day2, belt)
        source.addSet(e2, SetValues(135.0, WeightUnit.LB, 1.0, 10))
        source.addExerciseToWorkout(day2, source.exerciseId("Leg Press"))
        val legs = source.createPreset("Legs")
        source.addExerciseToPreset(legs, squat)
        source.addExerciseToPreset(legs, belt)
        source.createPreset("Empty")
    }

    private fun Backup.normalized() = copy(exercises = exercises.sortedBy { it.name })

    private fun spreadsheetBytes(backup: Backup): ByteArray =
        ByteArrayOutputStream().also { BackupSpreadsheet.write(backup, it) }.toByteArray()

    @Test
    fun exportThenImportIntoAFreshAppRestoresEverything() = runBlocking {
        logSampleData()
        val parsed = BackupSpreadsheet.read(ByteArrayInputStream(spreadsheetBytes(source.exportBackup())))
        assertEquals(emptyList<String>(), parsed.warnings)

        val summary = target.importBackup(parsed.backup)
        assertEquals(
            ImportSummary(
                setsAdded = 3,
                daysWithNewSets = 2,
                exercisesAdded = 1,
                presetsAdded = 2,
                presetsSkipped = 0,
                exerciseDaysSkipped = 0,
            ),
            summary,
        )
        assertEquals(source.exportBackup().normalized(), target.exportBackup().normalized())
        assertEquals(WeightUnit.LB, target.observeDay(day1).first()!!.workout.unit)

        // Importing the same file again is harmless.
        val again = target.importBackup(parsed.backup)
        assertEquals(ImportSummary(0, 0, 0, 0, presetsSkipped = 2, exerciseDaysSkipped = 2), again)
        assertEquals(3, target.exportBackup().setCount)
    }

    @Test
    fun importKeepsWhatIsAlreadyInTheApp() = runBlocking {
        logSampleData()
        val bench = target.exerciseId("Flat Barbell Bench Press")
        val benchEntry = target.addExerciseToWorkout(day1, bench)
        target.addSet(benchEntry, SetValues(80.0, WeightUnit.KG, 1.0, 5))
        val squatEntry = target.addExerciseToWorkout(day1, target.exerciseId("Barbell Squat"))
        target.addSet(squatEntry, SetValues(1.0, WeightUnit.KG, 1.0, 1))
        val legs = target.createPreset("legs")
        target.addExerciseToPreset(legs, target.exerciseId("Deadlift"))

        val summary = target.importBackup(source.exportBackup())

        assertEquals(1, summary.setsAdded) // only Belt Squat on day 2
        assertEquals(1, summary.exerciseDaysSkipped) // squat on day 1 was already logged here
        assertEquals(1, summary.presetsSkipped)
        val day1Sets = target.observeDay(day1).first()!!.entries.sortedBy { it.entry.position }
            .map { entry -> entry.exercise.name to entry.sets.map { it.weight } }
        assertEquals(listOf("Flat Barbell Bench Press" to listOf(80.0), "Barbell Squat" to listOf(1.0)), day1Sets)
        val keptPreset = target.observePreset(legs).first()!!
        assertEquals(listOf("Deadlift"), keptPreset.orderedItems.map { it.exercise.name })
    }

    @Test
    fun backupManagerSavesAndImportsThroughContentUris() = runBlocking {
        logSampleData()
        val uri = Uri.parse("content://com.example.documents/OpenFit.xlsx")
        val written = ByteArrayOutputStream()
        shadowOf(context.contentResolver).registerOutputStream(uri, written)
        assertEquals(3, BackupManager(context, source).exportTo(uri))

        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(written.toByteArray()))
        val result = BackupManager(context, target).importFrom(uri)
        assertEquals(3, result.summary.setsAdded)
        assertEquals(emptyList<String>(), result.warnings)
    }

    @Test
    fun sharedExportIsServedThroughTheFileProvider() = runBlocking {
        logSampleData()
        val manager = BackupManager(context, source)
        val uri = manager.exportForSharing()
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.fileprovider", uri.authority)

        val file = File(File(context.cacheDir, "exports"), manager.suggestedFileName())
        assertTrue(file.exists())
        assertEquals(3, file.inputStream().use { BackupSpreadsheet.read(it) }.backup.setCount)
    }
}
