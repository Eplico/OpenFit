package com.eplico.openfit.core

import kotlin.math.roundToLong

/** Personal-record badges shown beside a set. [defaultColor] is ARGB; users can pick their own in settings. */
enum class Trophy(val label: String, val meaning: String, val defaultColor: Int) {
    GOLD("Gold", "Heaviest weight yet", 0xFFFFB300.toInt()),
    SILVER("Silver", "Ties your heaviest weight with more reps than before", 0xFF9EA7AD.toInt()),
    BRONZE("Bronze", "Matches your best weight and reps", 0xFFCD7F32.toInt()),
    BLUE("Blue", "Most reps yet at this weight", 0xFF1E88E5.toInt()),
}

/** One set as the trophy rules see it: an id of the caller's choosing, the weight actually moved, and reps. */
data class TrophySet<K>(val key: K, val weight: Double, val unit: WeightUnit, val ratio: Double, val reps: Int) {
    /** Calculated weight (weight × ratio) in kg, so 50 kg at 2× ties 100 kg at 1× and 220.46 lb. */
    val calculatedKg: Double get() = unit.convert(WeightMath.calculatedWeight(weight, ratio), WeightUnit.KG)
}

object Trophies {
    /** Weights within this many kg count as the same weight (absorbs kg/lb rounding). */
    private const val RESOLUTION_KG = 0.05

    private fun bucket(kg: Double): Long = (kg / RESOLUTION_KG).roundToLong()

    /**
     * Awards trophies to [sets], which must be in the order they were done (oldest first). Each set is
     * judged only against the sets before it, so a trophy stays on the set that earned it even after
     * the record is beaten later. Sets without reps get nothing and don't count.
     *
     * - Gold: heavier than every earlier set.
     * - Silver: equals the heaviest weight, with more reps than any earlier set at that weight.
     * - Bronze: equals the heaviest weight and the most reps done at it.
     * - Blue: lighter than the heaviest weight, but the most reps ever done at this weight.
     */
    fun <K> award(sets: List<TrophySet<K>>): Map<K, Trophy> {
        val result = LinkedHashMap<K, Trophy>()
        var heaviest: Long? = null
        val bestReps = HashMap<Long, Int>()
        for (set in sets) {
            if (set.reps <= 0) continue
            val weight = bucket(set.calculatedKg)
            val previousBest = bestReps[weight]
            val trophy = when {
                heaviest == null || weight > heaviest -> Trophy.GOLD
                weight == heaviest -> when {
                    previousBest == null || set.reps > previousBest -> Trophy.SILVER
                    set.reps == previousBest -> Trophy.BRONZE
                    else -> null
                }
                previousBest == null || set.reps > previousBest -> Trophy.BLUE
                else -> null
            }
            if (trophy != null) result[set.key] = trophy
            heaviest = if (heaviest == null) weight else maxOf(heaviest, weight)
            bestReps[weight] = maxOf(previousBest ?: 0, set.reps)
        }
        return result
    }
}
