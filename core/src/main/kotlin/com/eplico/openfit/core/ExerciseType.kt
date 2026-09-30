package com.eplico.openfit.core

import java.util.Locale

/** What a set of an exercise records besides weight. */
enum class Measure(val label: String) {
    REPS("Reps"),
    TIME("Time"),
    DISTANCE("Distance"),
    DISTANCE_TIME("Distance + time");

    val usesReps: Boolean get() = this == REPS
    val usesTime: Boolean get() = this == TIME || this == DISTANCE_TIME
    val usesDistance: Boolean get() = this == DISTANCE || this == DISTANCE_TIME

    companion object {
        fun parse(text: String): Measure? {
            val t = text.trim().lowercase().replace(Regex("[^a-z]"), "")
            return when {
                t.isEmpty() -> null
                "distance" in t && ("time" in t || "duration" in t) -> DISTANCE_TIME
                t == "reps" || t == "rep" || t == "repetitions" -> REPS
                t == "time" || t == "duration" -> TIME
                t == "distance" -> DISTANCE
                else -> entries.firstOrNull { it.name.lowercase().replace("_", "") == t }
            }
        }
    }
}

/** Whether an exercise tracks weight, and in which unit. */
enum class WeightMode(val label: String) {
    /** Follows the workout's kg/lb switch. */
    WORKOUT("Match workout"),
    KG("kg"),
    LB("lb"),
    NONE("No weight");

    val tracksWeight: Boolean get() = this != NONE

    /** The unit this exercise's sets are entered and shown in, or null when it has no weight. */
    fun unitFor(workoutUnit: WeightUnit): WeightUnit? = when (this) {
        WORKOUT -> workoutUnit
        KG -> WeightUnit.KG
        LB -> WeightUnit.LB
        NONE -> null
    }

    companion object {
        fun parse(text: String): WeightMode? = when (text.trim().lowercase()) {
            "" -> null
            "match workout", "workout", "default" -> WORKOUT
            "kg", "kgs", "kilograms" -> KG
            "lb", "lbs", "pounds" -> LB
            "none", "no weight", "n/a", "na", "bodyweight" -> NONE
            else -> entries.firstOrNull { it.name.equals(text.trim(), ignoreCase = true) }
        }
    }
}

enum class DistanceUnit(val label: String, val meters: Double) {
    M("m", 1.0),
    KM("km", 1000.0),
    MI("mi", 1609.344);

    fun convert(value: Double, target: DistanceUnit): Double =
        if (this == target) value else value * meters / target.meters

    /** How far pace is quoted over: per 500 m for meters (rowing-machine style), otherwise per km / mi. */
    val paceDistance: Double get() = if (this == M) 500.0 else 1.0

    val paceLabel: String get() = if (this == M) "500m" else label

    companion object {
        fun parse(text: String): DistanceUnit? = when (text.trim().lowercase()) {
            "m", "meter", "meters", "metre", "metres" -> M
            "km", "kms", "kilometer", "kilometers", "kilometre", "kilometres" -> KM
            "mi", "mile", "miles" -> MI
            else -> null
        }
    }
}

object Durations {
    /** 45 -> "0:45", 1530 -> "25:30", 3723 -> "1:02:03". */
    fun format(seconds: Int): String {
        val total = seconds.coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    /**
     * Reads "h:mm:ss" or "m:ss". A plain number is minutes (so "30" is half an hour and
     * "1.5" is 1:30). Returns null for anything else.
     */
    fun parse(text: String): Int? {
        val t = text.trim()
        if (t.isEmpty()) return null
        if (':' !in t) {
            val minutes = WeightMath.parse(t) ?: return null
            if (minutes < 0) return null
            return Math.round(minutes * 60).toInt()
        }
        val parts = t.split(':').map { it.trim() }
        if (parts.size !in 2..3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
        val numbers = parts.map { it.toLong() }
        if (numbers.drop(1).any { it >= 60 }) return null
        val seconds = numbers.fold(0L) { acc, n -> acc * 60 + n }
        return if (seconds > Int.MAX_VALUE) null else seconds.toInt()
    }

    /** "5:06 /km", "8:00 /mi" or "1:52 /500m" for a set with both distance and time, else null. */
    fun pace(seconds: Int, distance: Double, unit: DistanceUnit): String? {
        if (seconds <= 0 || distance <= 0.0) return null
        return "${format(Math.round(seconds * unit.paceDistance / distance).toInt())} /${unit.paceLabel}"
    }
}
