package com.eplico.openfit.data

import com.eplico.openfit.core.Measure
import com.eplico.openfit.core.WeightMode

data class DefaultExercise(
    val name: String,
    val measure: Measure = Measure.REPS,
    val weightMode: WeightMode = WeightMode.DEFAULT,
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
            "Seated Machine Fly",
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
            "Wide Grip Cable Rows",
            "T-Bar Row",
            "Cable Face Pull",
            "Back Extensions",
            "Rack Pull",
        ),
        "Shoulders" to lifts(
            "Overhead Press",
            "Seated Dumbbell Press",
            "Arnold Press",
            "Lateral Raise",
            "Lateral Machine Raise",
            "Cable Lateral Raise",
            "Front Raise",
            "Rear Delt Machine Fly",
            "Upright Row",
            "Shrug",
        ),
        "Biceps" to lifts(
            "Barbell Curl",
            "Dumbbell Curl",
            "Hammer Curl",
            "Cable Hammer Curl",
            "Bayesian Curl",
            "Preacher Curl",
            "EZ-Bar Preacher Curl",
            "Wide Grip Preacher Curl",
            "Reverse Preacher Curl",
            "Partial Reverse Preacher Curl",
            "Cable Curl",
            "Incline Dumbbell Curl",
        ),
        "Triceps" to lifts(
            "Triceps Pushdown",
            "Overhead Tricep Extension",
            "Cable Overhead Triceps Extension",
            "Skull Crusher",
            "Close Grip Bench Press",
            "Parallel Bar Triceps Dip",
        ),
        "Legs" to lifts(
            "Barbell Squat",
            "Front Squat",
            "Leg Press",
            "Hack Squat",
            "Romanian Deadlift",
            "Leg Extension Machine",
            "Lying Leg Curl",
            "Seated Leg Curl Machine",
            "Standing Leg Curl",
            "Bulgarian Split Squat",
            "Walking Lunge",
            "Hip Thrust",
            "Hyperextension (Glutes)",
            "Hip Abductors",
            "Hip Adductors",
            "Standing Calf Raise Machine",
            "Seated Calf Raise",
        ),
        "Core" to lifts(
            "Cable Crunch",
            "Crunch Machine",
            "Rotary Torso",
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

    private fun withCategory(names: Set<String>): List<Pair<String, DefaultExercise>> =
        all.flatMap { (category, exercises) -> exercises.filter { it.name in names }.map { category to it } }

    /** Exercises that were added to the starter library in database version 2. */
    val addedInVersion2: List<Pair<String, DefaultExercise>>
        get() = all.getValue("Cardio").map { "Cardio" to it } + withCategory(setOf("Plank"))

    /** Starter exercises renamed in database version 3 (old name to new name). */
    val renamedInVersion3: List<Pair<String, String>> = listOf(
        "Tricep Pushdown" to "Triceps Pushdown",
        "Face Pull" to "Cable Face Pull",
        "Cable Overhead Extension" to "Cable Overhead Triceps Extension",
        "Leg Extension" to "Leg Extension Machine",
        "Seated Leg Curl" to "Seated Leg Curl Machine",
        "Standing Calf Raise" to "Standing Calf Raise Machine",
        "Pec Deck" to "Seated Machine Fly",
        "Rear Delt Fly" to "Rear Delt Machine Fly",
    )

    /** Exercises that were added to the starter library in database version 3. */
    val addedInVersion3: List<Pair<String, DefaultExercise>>
        get() = withCategory(
            setOf(
                "Back Extensions", "Wide Grip Cable Rows",
                "Bayesian Curl", "Cable Hammer Curl", "EZ-Bar Preacher Curl", "Wide Grip Preacher Curl",
                "Reverse Preacher Curl", "Partial Reverse Preacher Curl",
                "Parallel Bar Triceps Dip",
                "Crunch Machine", "Rotary Torso",
                "Hip Abductors", "Hip Adductors", "Hyperextension (Glutes)", "Standing Leg Curl",
                "Lateral Machine Raise",
            ),
        )
}
