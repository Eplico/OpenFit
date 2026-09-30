package com.eplico.openfit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightMathTest {

    @Test
    fun convertsBetweenUnits() {
        assertEquals(220.462, WeightUnit.KG.convert(100.0, WeightUnit.LB), 0.001)
        assertEquals(61.235, WeightUnit.LB.convert(135.0, WeightUnit.KG), 0.001)
        assertEquals(42.0, WeightUnit.KG.convert(42.0, WeightUnit.KG), 0.0)
    }

    @Test
    fun convertForEntryRoundsToTwoDecimals() {
        assertEquals(61.23, WeightMath.convertForEntry(135.0, WeightUnit.LB, WeightUnit.KG), 0.0)
        assertEquals(220.46, WeightMath.convertForEntry(100.0, WeightUnit.KG, WeightUnit.LB), 0.0)
    }

    @Test
    fun calculatedWeightAppliesRatio() {
        assertEquals(100.0, WeightMath.calculatedWeight(50.0, 2.0), 0.0)
        assertEquals(25.0, WeightMath.calculatedWeight(50.0, 0.5), 0.0)
        assertEquals(50.0, WeightMath.calculatedWeight(50.0, WeightMath.DEFAULT_RATIO), 0.0)
    }

    @Test
    fun formatStripsTrailingZeros() {
        assertEquals("100", WeightMath.format(100.0))
        assertEquals("62.5", WeightMath.format(62.50))
        assertEquals("61.24", WeightMath.format(61.235))
        assertEquals("0", WeightMath.format(0.0))
        assertEquals("0", WeightMath.format(-0.0001))
        assertEquals("1.333", WeightMath.format(1.3333, 3))
    }

    @Test
    fun parseAcceptsCommaAndDot() {
        assertEquals(62.5, WeightMath.parse("62.5")!!, 0.0)
        assertEquals(62.5, WeightMath.parse(" 62,5 ")!!, 0.0)
        assertNull(WeightMath.parse(""))
        assertNull(WeightMath.parse("abc"))
        assertNull(WeightMath.parse("NaN"))
    }

    @Test
    fun describeShowsRatioOnlyWhenNotOne() {
        assertEquals("60 kg", WeightMath.describe(60.0, 1.0, WeightUnit.KG))
        assertEquals("60 × 2 = 120 kg", WeightMath.describe(60.0, 2.0, WeightUnit.KG))
        assertEquals("90 × 0.5 = 45 lb", WeightMath.describe(90.0, 0.5, WeightUnit.LB))
    }

    @Test
    fun estimatedOneRepMax() {
        assertEquals(100.0, WeightMath.estimatedOneRepMax(100.0, 1), 0.0)
        assertEquals(133.33, WeightMath.estimatedOneRepMax(100.0, 10), 0.01)
        assertEquals(0.0, WeightMath.estimatedOneRepMax(100.0, 0), 0.0)
    }

    @Test
    fun setValuesConvertAndComputeVolume() {
        val set = SetValues(weight = 135.0, unit = WeightUnit.LB, ratio = 2.0, reps = 5)
        assertEquals(270.0, set.calculatedWeight, 0.0)

        val inKg = set.inUnit(WeightUnit.KG)
        assertEquals(61.23, inKg.weight, 0.0)
        assertEquals(2.0, inKg.ratio, 0.0)
        assertEquals(5, inKg.reps)

        val sets = listOf(
            SetValues(100.0, WeightUnit.KG, 1.0, 5),
            SetValues(50.0, WeightUnit.KG, 2.0, 5),
        )
        assertEquals(1000.0, sets.volumeIn(WeightUnit.KG), 0.0)
        assertEquals(2204.62, sets.volumeIn(WeightUnit.LB), 0.01)
    }

    @Test
    fun unitLookup() {
        assertEquals(WeightUnit.LB, WeightUnit.fromLabel("lb"))
        assertEquals(WeightUnit.KG, WeightUnit.fromLabel("KG"))
        assertNull(WeightUnit.fromLabel("stone"))
        assertEquals(WeightUnit.LB, WeightUnit.KG.other)
    }
}
