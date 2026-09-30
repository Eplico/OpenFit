package com.eplico.openfit.core

/**
 * The numbers that make up one logged set. [weight] is what was entered, in [unit];
 * [calculatedWeight] applies the [ratio] on top of it.
 */
data class SetValues(
    val weight: Double,
    val unit: WeightUnit,
    val ratio: Double = WeightMath.DEFAULT_RATIO,
    val reps: Int,
) {
    val calculatedWeight: Double get() = WeightMath.calculatedWeight(weight, ratio)

    /** Same set expressed in [target] units (ratio and reps are unit-less and carry over). */
    fun inUnit(target: WeightUnit): SetValues =
        if (target == unit) this else copy(weight = WeightMath.convertForEntry(weight, unit, target), unit = target)

    fun calculatedWeightIn(target: WeightUnit): Double = unit.convert(calculatedWeight, target)

    fun estimatedOneRepMaxIn(target: WeightUnit): Double =
        WeightMath.estimatedOneRepMax(calculatedWeightIn(target), reps)

    companion object {
        /** Starting values for an exercise that has never been logged. */
        fun blank(unit: WeightUnit) = SetValues(weight = 0.0, unit = unit, ratio = WeightMath.DEFAULT_RATIO, reps = 0)
    }
}

/** Total volume (calculated weight × reps) of [sets], expressed in [unit]. */
fun List<SetValues>.volumeIn(unit: WeightUnit): Double = sumOf { it.calculatedWeightIn(unit) * it.reps }
