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

/** Opens databases written by older versions of OpenFit with the current code. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(name)
    }

    private fun openCurrent(): OpenFitDatabase =
        Room.databaseBuilder(context, OpenFitDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

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
            execSQL(
                "INSERT INTO exercises (id, name, category) VALUES (1, 'Barbell Squat', 'Legs'), (2, 'Sled Push', 'Strongman'), " +
                    "(3, 'running', 'Legs'), (4, 'Tricep Pushdown', 'Triceps')",
            )
            execSQL("INSERT INTO workouts (id, date, unit) VALUES (1, ${day.toEpochDay()}, 'LB')")
            execSQL("INSERT INTO workout_exercises (id, workoutId, exerciseId, position) VALUES (1, 1, 1, 0), (2, 1, 4, 1)")
            execSQL(
                "INSERT INTO sets (id, workoutExerciseId, weight, unit, ratio, reps, position, loggedAt) " +
                    "VALUES (1, 1, 225.0, 'LB', 2.0, 5, 0, 0), (2, 2, 50.0, 'LB', 1.0, 12, 0, 0)",
            )
            execSQL("INSERT INTO presets (id, name) VALUES (1, 'Leg day')")
            execSQL("INSERT INTO preset_exercises (id, presetId, exerciseId, position) VALUES (1, 1, 1, 0)")
        }

        // Room checks the migrated schema against the current entities when it opens, and fails if they differ.
        val db = openCurrent()
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
            // Version 3 renames starter exercises (keeping their sets) and adds new ones.
            assertTrue(exercises.none { it.name == "Tricep Pushdown" })
            assertEquals("Triceps", exercises.single { it.name == "Triceps Pushdown" }.category)
            assertTrue(exercises.any { it.name == "Bayesian Curl" && it.category == "Biceps" })

            val categories = repository.categories.first()
            assertEquals(DefaultExercises.categories + "Strongman", categories.map { it.category.name })
            assertEquals(1, categories.first { it.category.name == "Strongman" }.exerciseCount)

            val workout = repository.observeDay(day).first()!!
            assertEquals(WeightUnit.LB, workout.workout.unit)
            val sets = workout.entries.associate { it.exercise.name to it.sets.single().values }
            assertEquals(SetValues(225.0, WeightUnit.LB, 2.0, 5), sets["Barbell Squat"])
            assertEquals(SetValues(50.0, WeightUnit.LB, 1.0, 12), sets["Triceps Pushdown"])
            assertEquals(listOf("Barbell Squat"), repository.observePreset(1).first()!!.orderedItems.map { it.exercise.name })
        } finally {
            db.close()
        }
    }

    @Test
    fun version2UpgradeRespectsTheUsersOwnNamesAndCategories() = runBlocking {
        createVersion1Database {
            execSQL(
                "INSERT INTO exercises (id, name, category) VALUES (1, 'Face Pull', 'Back'), (2, 'cable face pull', 'Shoulders'), " +
                    "(3, 'Leg Extension', 'Legs'), (4, 'Barbell Curl', 'Biceps')",
            )
            // Bring it to version 2, then delete the Biceps category the way the app does (its exercises move to Other).
            MIGRATION_1_2.migrate(this)
            execSQL("UPDATE exercises SET category = 'Other' WHERE category = 'Biceps'")
            execSQL("DELETE FROM categories WHERE name = 'Biceps'")
            version = 2
        }

        val db = openCurrent()
        try {
            val repository = WorkoutRepository(db, SettingsRepository(context))
            val exercises = repository.exercises.first().associateBy { it.name }
            // "Cable Face Pull" was already taken (in any case), so "Face Pull" keeps its name.
            assertTrue("Face Pull" in exercises)
            assertEquals("Shoulders", exercises.getValue("cable face pull").category)
            assertTrue("Cable Face Pull" !in exercises)
            // A free new name is used.
            assertTrue("Leg Extension Machine" in exercises && "Leg Extension" !in exercises)
            // New curls don't bring back the deleted Biceps category.
            assertEquals("Other", exercises.getValue("Bayesian Curl").category)
            assertTrue(repository.categories.first().none { it.category.name == "Biceps" })
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
