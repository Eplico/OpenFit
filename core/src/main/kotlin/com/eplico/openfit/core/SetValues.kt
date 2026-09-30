package com.eplico.openfit.core

/**
 * The numbers that make up one logged set. [weight] is what was entered, in [unit];
 * [calculatedWeight] applies the [ratio] on top of it. Fields an exercise doesn't use stay 0.
 */
data class SetValues(
    val weight: Double,
    val unit: WeightUnit,
    val ratio: Double = WeightMath.DEFAULT_RATIO,
    val reps: Int = 0,
    val durationSeconds: Int = 0,
    val distance: Double = 0.0,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
) {
    val calculatedWeight: Double get() = WeightMath.calculatedWeight(weight, ratio)

    /** Same set expressed in [target] units (everything but the weight carries over). */
    fun inUnit(target: WeightUnit): SetValues =
        if (target == unit) this else copy(weight = WeightMath.convertForEntry(weight, unit, target), unit = target)

    fun calculatedWeightIn(target: WeightUnit): Double = unit.convert(calculatedWeight, target)

    fun estimatedOneRepMaxIn(target: WeightUnit): Double =
        WeightMath.estimatedOneRepMax(calculatedWeightIn(target), reps)

    companion object {
        /** Starting values for an exercise that has never been logged. */
        fun blank(unit: WeightUnit, distanceUnit: DistanceUnit = DistanceUnit.KM) =
            SetValues(weight = 0.0, unit = unit, distanceUnit = distanceUnit)
    }
}

/** Total volume (calculated weight × reps) of [sets], expressed in [unit]. Timed/distance sets add nothing. */
fun List<SetValues>.volumeIn(unit: WeightUnit): Double = sumOf { it.calculatedWeightIn(unit) * it.reps }

/** Human-readable descriptions of a set, shared by every screen and the tests. */
object SetFormat {
    fun reps(count: Int): String = if (count == 1) "1 rep" else "$count reps"

    fun distance(value: Double, unit: DistanceUnit): String = "${WeightMath.format(value)} ${unit.label}"

    /**
     * "120 kg × 8 reps", "8 reps", "5 km in 25:30", "1:30", "20 kg × 50 m". The weight is the
     * calculated weight (weight × ratio) in the unit the set was logged in. Whatever the set actually
     * recorded is shown, so sets survive an exercise changing type.
     */
    fun primary(set: SetValues, measure: Measure): String {
        val weight = if (set.weight > 0.0) WeightMath.formatWeight(set.calculatedWeight, set.unit) else null
        val activity = buildList {
            if (set.reps > 0) add(reps(set.reps))
            val distance = set.distance.takeIf { it > 0.0 }?.let { distance(it, set.distanceUnit) }
            val time = set.durationSeconds.takeIf { it > 0 }?.let { Durations.format(it) }
            when {
                distance != null && time != null -> add("$distance in $time")
                distance != null -> add(distance)
                time != null -> add(time)
            }
        }.joinToString(" · ").ifEmpty {
            when (measure) {
                Measure.REPS -> reps(0)
                Measure.TIME -> Durations.format(0)
                Measure.DISTANCE, Measure.DISTANCE_TIME -> distance(0.0, set.distanceUnit)
            }
        }
        return if (weight != null) "$weight × $activity" else activity
    }

    /** Extra detail: the pace of a distance + time set, like "5:06 /km". Null when there's nothing to add. */
    fun secondary(set: SetValues): String? = Durations.pace(set.durationSeconds, set.distance, set.distanceUnit)
}
