package com.eplico.openfit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Exercise::class,
        Category::class,
        Workout::class,
        WorkoutExercise::class,
        SetEntry::class,
        Preset::class,
        PresetExercise::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class OpenFitDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun categoryDao(): CategoryDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun setDao(): SetDao
    abstract fun presetDao(): PresetDao

    companion object {
        const val NAME = "openfit.db"

        fun build(context: Context): OpenFitDatabase =
            Room.databaseBuilder(context, OpenFitDatabase::class.java, NAME)
                .addCallback(SeedCallback)
                .addMigrations(MIGRATION_1_2)
                .build()

        /** Same schema and starter exercises, kept in memory (for tests). */
        fun inMemory(context: Context): OpenFitDatabase =
            Room.inMemoryDatabaseBuilder(context, OpenFitDatabase::class.java)
                .addCallback(SeedCallback)
                .build()
    }
}

private object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        Seed.insertMissingCategories(db, DefaultExercises.categories)
        DefaultExercises.all.forEach { (category, exercises) ->
            exercises.forEach { Seed.insertExerciseIfMissing(db, category, it) }
        }
    }
}

/**
 * Version 2 adds exercise types (reps / time / distance and a weight setting), time and distance
 * on sets, and a table of user-editable categories. Existing data is kept; the new Cardio
 * exercises and Plank are added unless an exercise with the same name already exists.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `exercises` ADD COLUMN `measure` TEXT NOT NULL DEFAULT 'REPS'")
        db.execSQL("ALTER TABLE `exercises` ADD COLUMN `weightMode` TEXT NOT NULL DEFAULT 'WORKOUT'")
        db.execSQL("ALTER TABLE `sets` ADD COLUMN `durationSeconds` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `sets` ADD COLUMN `distance` REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `sets` ADD COLUMN `distanceUnit` TEXT NOT NULL DEFAULT 'KM'")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `categories` " +
                "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `position` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)")

        // The built-in categories first (in their usual order), then any the user invented in v1.
        val used = ArrayList<String>()
        db.query("SELECT DISTINCT `category` FROM `exercises` ORDER BY `category` COLLATE NOCASE").use { cursor ->
            while (cursor.moveToNext()) used += cursor.getString(0)
        }
        Seed.insertMissingCategories(db, DefaultExercises.categories + used)
        DefaultExercises.addedInVersion2.forEach { (category, exercise) ->
            Seed.insertExerciseIfMissing(db, category, exercise)
        }
    }
}

/** Raw-SQL inserts shared by first-run seeding and migrations (Room DAOs aren't available there). */
private object Seed {
    fun insertMissingCategories(db: SupportSQLiteDatabase, names: List<String>) {
        var position = db.query("SELECT COALESCE(MAX(`position`), -1) FROM `categories`").use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else -1
        }
        for (name in names.map { it.trim() }.filter { it.isNotEmpty() }) {
            val exists = db.query("SELECT 1 FROM `categories` WHERE `name` = ? COLLATE NOCASE", arrayOf<Any>(name)).use { it.moveToFirst() }
            if (!exists) {
                db.execSQL("INSERT INTO `categories` (`name`, `position`) VALUES (?, ?)", arrayOf<Any>(name, ++position))
            }
        }
    }

    fun insertExerciseIfMissing(db: SupportSQLiteDatabase, category: String, exercise: DefaultExercise) {
        db.execSQL(
            "INSERT INTO `exercises` (`name`, `category`, `measure`, `weightMode`) " +
                "SELECT ?, ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM `exercises` WHERE `name` = ? COLLATE NOCASE)",
            arrayOf<Any>(exercise.name, category, exercise.measure.name, exercise.weightMode.name, exercise.name),
        )
    }
}
