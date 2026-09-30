package com.eplico.openfit.data

import com.eplico.openfit.core.Measure
import com.eplico.openfit.core.WeightMode

data class DefaultExercise(
    val name: String,
    val measure: Measure = Measure.REPS,
    val weightMode: WeightMode = WeightMode.WORKOUT,
)

/** Starter library inserted when the database is first created. Users can edit or delete any of it. */
object DefaultExercises {
    /** Where exercises go when their category is deleted or unknown. */
    const val OTHER = "Other"

    private fun lifts(vararg names: String) = names.map { DefaultExercise(it) }

    private fun cardio(name: String, measure: Measure = Measure.DISTANCE_TIME) =
        DefaultExercise(name, measure, WeightMode.NONE)

    /** Categories in display order, each with its starter exercises. */
    val all: Map<String, List<DefaultExercise>> = linkedMapOf(
        "Chest" to lifts(
            "Flat Barbell Bench Press",
            "Incline Barbell Bench Press",
            "Decline Barbell Bench Press",
            "Flat Dumbbell Bench Press",
            "Incline Dumbbell Bench Press",
            "Dumbbell Fly",
            "Cable Crossover",
            "Machine Chest Press",
            "Pec Deck",
            "Dips",
        ),
        "Back" to lifts(
            "Deadlift",
            "Barbell Row",
            "Dumbbell Row",
            "Pull Up",
            "Chin Up",
            "Lat Pulldown",
            "Seated Cable Row",
            "T-Bar Row",
            "Face Pull",
            "Rack Pull",
        ),
        "Shoulders" to lifts(
            "Overhead Press",
            "Seated Dumbbell Press",
            "Arnold Press",
            "Lateral Raise",
            "Cable Lateral Raise",
            "Front Raise",
            "Rear Delt Fly",
            "Upright Row",
            "Shrug",
        ),
        "Biceps" to lifts(
            "Barbell Curl",
            "Dumbbell Curl",
            "Hammer Curl",
            "Preacher Curl",
            "Cable Curl",
            "Incline Dumbbell Curl",
        ),
        "Triceps" to lifts(
            "Tricep Pushdown",
            "Overhead Tricep Extension",
            "Skull Crusher",
            "Close Grip Bench Press",
            "Cable Overhead Extension",
        ),
        "Legs" to lifts(
            "Barbell Squat",
            "Front Squat",
            "Leg Press",
            "Hack Squat",
            "Romanian Deadlift",
            "Leg Extension",
            "Lying Leg Curl",
            "Seated Leg Curl",
            "Bulgarian Split Squat",
            "Walking Lunge",
            "Hip Thrust",
            "Standing Calf Raise",
            "Seated Calf Raise",
        ),
        "Core" to lifts(
            "Cable Crunch",
            "Hanging Leg Raise",
            "Ab Wheel Rollout",
            "Decline Crunch",
        ) + DefaultExercise("Plank", Measure.TIME, WeightMode.NONE),
        "Cardio" to listOf(
            cardio("Running"),
            cardio("Treadmill"),
            cardio("Walking"),
            cardio("Cycling"),
            cardio("Stationary Bike"),
            cardio("Rowing Machine"),
            cardio("Swimming"),
            cardio("Elliptical", Measure.TIME),
            cardio("Stair Climber", Measure.TIME),
            cardio("Jump Rope", Measure.TIME),
        ),
        OTHER to emptyList(),
    )

    val categories: List<String> get() = all.keys.toList()

    /** Exercises that were added to the starter library in database version 2. */
    val addedInVersion2: List<Pair<String, DefaultExercise>>
        get() = all.getValue("Cardio").map { "Cardio" to it } + ("Core" to all.getValue("Core").first { it.name == "Plank" })
}
