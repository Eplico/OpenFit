package com.eplico.openfit.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eplico.openfit.core.Measure
import com.eplico.openfit.core.SetValues
import com.eplico.openfit.core.WeightMode
import com.eplico.openfit.core.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Opens a database written by OpenFit 0.1 (schema version 1) with the current code. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(name)
    }

    private fun createVersion1Database(fill: SupportSQLiteDatabase.() -> Unit) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            VERSION_1_SCHEMA.forEach(db::execSQL)
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                )
                .build(),
        )
        helper.writableDatabase.fill()
        helper.close()
    }

    @Test
    fun version1DataSurvivesAndGainsCategoriesAndCardio() = runBlocking {
        val day = LocalDate.of(2026, 9, 28)
        createVersion1Database {
            execSQL("INSERT INTO exercises (id, name, category) VALUES (1, 'Barbell Squat', 'Legs'), (2, 'Sled Push', 'Strongman'), (3, 'running', 'Legs')")
            execSQL("INSERT INTO workouts (id, date, unit) VALUES (1, ${day.toEpochDay()}, 'LB')")
            execSQL("INSERT INTO workout_exercises (id, workoutId, exerciseId, position) VALUES (1, 1, 1, 0)")
            execSQL("INSERT INTO sets (id, workoutExerciseId, weight, unit, ratio, reps, position, loggedAt) VALUES (1, 1, 225.0, 'LB', 2.0, 5, 0, 0)")
            execSQL("INSERT INTO presets (id, name) VALUES (1, 'Leg day')")
            execSQL("INSERT INTO preset_exercises (id, presetId, exerciseId, position) VALUES (1, 1, 1, 0)")
        }

        // Room checks the migrated schema against the current entities when it opens, and fails if they differ.
        val db = Room.databaseBuilder(context, OpenFitDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
        try {
            val repository = WorkoutRepository(db, SettingsRepository(context))
            val exercises = repository.exercises.first()

            val squat = exercises.first { it.name == "Barbell Squat" }
            assertEquals(Measure.REPS to WeightMode.WORKOUT, squat.measure to squat.weightMode)
            // New cardio exercises arrive, but not over one the user already had with the same name.
            assertTrue(exercises.any { it.name == "Treadmill" && it.measure == Measure.DISTANCE_TIME && it.weightMode == WeightMode.NONE })
            assertTrue(exercises.any { it.name == "Plank" && it.measure == Measure.TIME })
            assertEquals(1, exercises.count { it.name.equals("running", ignoreCase = true) })
            // Starter exercises the user had deleted in version 1 are not brought back.
            assertTrue(exercises.none { it.name == "Deadlift" })

            val categories = repository.categories.first()
            assertEquals(DefaultExercises.categories + "Strongman", categories.map { it.category.name })
            assertEquals(1, categories.first { it.category.name == "Strongman" }.exerciseCount)

            val workout = repository.observeDay(day).first()!!
            assertEquals(WeightUnit.LB, workout.workout.unit)
            assertEquals(SetValues(225.0, WeightUnit.LB, 2.0, 5), workout.entries.single().sets.single().values)
            assertEquals(listOf("Barbell Squat"), repository.observePreset(1).first()!!.orderedItems.map { it.exercise.name })
        } finally {
            db.close()
        }
    }

    private companion object {
        /** The tables exactly as Room generated them for OpenFit 0.1. */
        val VERSION_1_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_exercises_name` ON `exercises` (`name`)",
            "CREATE TABLE IF NOT EXISTS `workouts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` INTEGER NOT NULL, `unit` TEXT NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_workouts_date` ON `workouts` (`date`)",
            "CREATE TABLE IF NOT EXISTS `workout_exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `workoutId` INTEGER NOT NULL, " +
                "`exerciseId` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                "FOREIGN KEY(`workoutId`) REFERENCES `workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_exercises_workoutId_exerciseId` ON `workout_exercises` (`workoutId`, `exerciseId`)",
            "CREATE INDEX IF NOT EXISTS `index_workout_exercises_exerciseId` ON `workout_exercises` (`exerciseId`)",
            "CREATE TABLE IF NOT EXISTS `sets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `workoutExerciseId` INTEGER NOT NULL, " +
                "`weight` REAL NOT NULL, `unit` TEXT NOT NULL, `ratio` REAL NOT NULL, `reps` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                "`loggedAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`workoutExerciseId`) REFERENCES `workout_exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_sets_workoutExerciseId` ON `sets` (`workoutExerciseId`)",
            "CREATE TABLE IF NOT EXISTS `presets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `preset_exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `presetId` INTEGER NOT NULL, " +
                "`exerciseId` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                "FOREIGN KEY(`presetId`) REFERENCES `presets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_preset_exercises_presetId` ON `preset_exercises` (`presetId`)",
            "CREATE INDEX IF NOT EXISTS `index_preset_exercises_exerciseId` ON `preset_exercises` (`exerciseId`)",
        )
    }
}
