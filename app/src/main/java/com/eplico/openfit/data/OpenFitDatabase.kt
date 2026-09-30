package com.eplico.openfit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Exercise::class,
        Workout::class,
        WorkoutExercise::class,
        SetEntry::class,
        Preset::class,
        PresetExercise::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class OpenFitDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun setDao(): SetDao
    abstract fun presetDao(): PresetDao

    companion object {
        fun build(context: Context): OpenFitDatabase =
            Room.databaseBuilder(context, OpenFitDatabase::class.java, "openfit.db")
                .addCallback(SeedCallback)
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
        DefaultExercises.all.forEach { (category, names) ->
            names.forEach { name ->
                db.execSQL(
                    "INSERT OR IGNORE INTO exercises (name, category) VALUES (?, ?)",
                    arrayOf<Any>(name, category),
                )
            }
        }
    }
}
