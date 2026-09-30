package com.eplico.openfit.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TrophiesTest {
    private var nextKey = 0

    private fun kg(weight: Double, reps: Int, ratio: Double = 1.0) = TrophySet(nextKey++, weight, WeightUnit.KG, ratio, reps)

    private fun lb(weight: Double, reps: Int, ratio: Double = 1.0) = TrophySet(nextKey++, weight, WeightUnit.LB, ratio, reps)

    /** Trophies in set order, null where a set earned none. */
    private fun awards(vararg sets: TrophySet<Int>): List<Trophy?> {
        val awarded = Trophies.award(sets.toList())
        return sets.map { awarded[it.key] }
    }

    @Test
    fun firstSetIsAWeightRecord() {
        assertEquals(listOf(Trophy.GOLD), awards(kg(60.0, 8)))
    }

    @Test
    fun eachTrophyType() {
        assertEquals(
            listOf(
                Trophy.GOLD, // 100 x 5: heaviest yet
                Trophy.BRONZE, // 100 x 5 again: ties weight and reps
                Trophy.SILVER, // 100 x 6: same weight, more reps
                null, // 100 x 4: nothing new
                Trophy.BLUE, // 80 x 10: lighter, but first time at 80
                null, // 80 x 9: fewer reps than before at 80
                Trophy.BLUE, // 80 x 12: most reps at 80
                Trophy.GOLD, // 102.5 x 1: heaviest again
            ),
            awards(
                kg(100.0, 5),
                kg(100.0, 5),
                kg(100.0, 6),
                kg(100.0, 4),
                kg(80.0, 10),
                kg(80.0, 9),
                kg(80.0, 12),
                kg(102.5, 1),
            ),
        )
    }

    @Test
    fun comparesTheCalculatedWeightNotTheNumberOnTheStack() {
        assertEquals(
            listOf(
                Trophy.GOLD, // 50 kg at 2x = 100 kg
                Trophy.BRONZE, // 100 kg at 1x: the same weight and reps
                Trophy.SILVER, // 200 kg at 0.5x = 100 kg with more reps
            ),
            awards(
                kg(50.0, 8, ratio = 2.0),
                kg(100.0, 8),
                kg(200.0, 9, ratio = 0.5),
            ),
        )
        // A bigger number on the machine isn't a record if the ratio makes it lighter.
        assertEquals(listOf(Trophy.GOLD, Trophy.BLUE), awards(kg(50.0, 8, ratio = 2.0), kg(150.0, 8, ratio = 0.5)))
    }

    @Test
    fun kgAndLbAreComparedTogether() {
        assertEquals(
            listOf(Trophy.GOLD, Trophy.BRONZE, Trophy.GOLD),
            awards(
                kg(100.0, 5),
                lb(220.46, 5), // 99.998 kg: the same weight once converted
                lb(225.0, 5), // 102.06 kg
            ),
        )
    }

    @Test
    fun setsWithoutRepsDontCount() {
        assertEquals(listOf(null, Trophy.GOLD), awards(kg(100.0, 0), kg(50.0, 5)))
    }
}
