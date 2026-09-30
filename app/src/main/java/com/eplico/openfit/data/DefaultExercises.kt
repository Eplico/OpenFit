package com.eplico.openfit.data

/** Starter library inserted when the database is first created. Users can edit or delete any of it. */
object DefaultExercises {
    val all: Map<String, List<String>> = linkedMapOf(
        "Chest" to listOf(
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
        "Back" to listOf(
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
        "Shoulders" to listOf(
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
        "Biceps" to listOf(
            "Barbell Curl",
            "Dumbbell Curl",
            "Hammer Curl",
            "Preacher Curl",
            "Cable Curl",
            "Incline Dumbbell Curl",
        ),
        "Triceps" to listOf(
            "Tricep Pushdown",
            "Overhead Tricep Extension",
            "Skull Crusher",
            "Close Grip Bench Press",
            "Cable Overhead Extension",
        ),
        "Legs" to listOf(
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
        "Core" to listOf(
            "Cable Crunch",
            "Hanging Leg Raise",
            "Ab Wheel Rollout",
            "Decline Crunch",
        ),
    )

    val categories: List<String> get() = all.keys.toList()
}
