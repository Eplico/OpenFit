package com.eplico.openfit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseTypeTest {

    @Test
    fun formatsDurations() {
        assertEquals("0:00", Durations.format(0))
        assertEquals("0:45", Durations.format(45))
        assertEquals("25:30", Durations.format(1530))
        assertEquals("1:02:03", Durations.format(3723))
    }

    @Test
    fun parsesDurations() {
        assertEquals(1530, Durations.parse("25:30"))
        assertEquals(45, Durations.parse("0:45"))
        assertEquals(3723, Durations.parse(" 1:02:03 "))
        assertEquals(1800, Durations.parse("30")) // plain numbers are minutes
        assertEquals(90, Durations.parse("1,5"))
        assertNull(Durations.parse(""))
        assertNull(Durations.parse("1:75"))
        assertNull(Durations.parse("1:2:3:4"))
        assertNull(Durations.parse("ten"))
        assertNull(Durations.parse("-5"))
    }

    @Test
    fun paceForDistanceAndTime() {
        assertEquals("5:06 /km", Durations.pace(1530, 5.0, DistanceUnit.KM))
        assertEquals("8:00 /mi", Durations.pace(1440, 3.0, DistanceUnit.MI))
        assertNull(Durations.pace(0, 5.0, DistanceUnit.KM))
        assertNull(Durations.pace(600, 0.0, DistanceUnit.KM))
    }

    @Test
    fun parsesMeasuresAndWeightModes() {
        assertEquals(Measure.DISTANCE_TIME, Measure.parse("Distance + time"))
        assertEquals(Measure.DISTANCE_TIME, Measure.parse("time and distance"))
        assertEquals(Measure.REPS, Measure.parse("reps"))
        assertEquals(Measure.TIME, Measure.parse("Duration"))
        assertNull(Measure.parse("speed"))
        assertEquals(WeightMode.NONE, WeightMode.parse("n/a"))
        assertEquals(WeightMode.LB, WeightMode.parse("LBS"))
        assertEquals(WeightMode.WORKOUT, WeightMode.parse("Match workout"))
        assertNull(WeightMode.parse("stone"))
    }

    @Test
    fun weightModeDecidesTheUnit() {
        assertEquals(WeightUnit.LB, WeightMode.WORKOUT.unitFor(WeightUnit.LB))
        assertEquals(WeightUnit.KG, WeightMode.KG.unitFor(WeightUnit.LB))
        assertNull(WeightMode.NONE.unitFor(WeightUnit.KG))
    }

    @Test
    fun convertsDistances() {
        assertEquals(3.107, DistanceUnit.KM.convert(5.0, DistanceUnit.MI), 0.001)
        assertEquals(1.609, DistanceUnit.MI.convert(1.0, DistanceUnit.KM), 0.001)
    }

    @Test
    fun describesSets() {
        val strength = SetValues(60.0, WeightUnit.KG, 2.0, reps = 8)
        assertEquals("60 kg × 8 reps", SetFormat.primary(strength, Measure.REPS, WeightUnit.KG))
        assertEquals("Ratio 2 → 120 kg", SetFormat.secondary(strength, WeightUnit.KG))
        assertEquals("8 reps", SetFormat.primary(strength.copy(weight = 0.0), Measure.REPS, WeightUnit.KG))

        val run = SetValues(0.0, WeightUnit.KG, durationSeconds = 1530, distance = 5.0)
        assertEquals("5 km in 25:30", SetFormat.primary(run, Measure.DISTANCE_TIME, null))
        assertEquals("5:06 /km", SetFormat.secondary(run, null))

        val plank = SetValues(0.0, WeightUnit.KG, durationSeconds = 90)
        assertEquals("1:30", SetFormat.primary(plank, Measure.TIME, null))
        assertNull(SetFormat.secondary(plank, null))

        val carry = SetValues(40.0, WeightUnit.KG, distance = 0.05)
        assertEquals("88.18 lb × 0.05 km", SetFormat.primary(carry, Measure.DISTANCE, WeightUnit.LB))

        assertEquals("0 reps", SetFormat.primary(SetValues(0.0, WeightUnit.KG), Measure.REPS, null))
        assertEquals("0:00", SetFormat.primary(SetValues(0.0, WeightUnit.KG), Measure.TIME, null))
    }

    @Test
    fun volumeIgnoresTimedAndDistanceSets() {
        val sets = listOf(
            SetValues(100.0, WeightUnit.KG, reps = 5),
            SetValues(0.0, WeightUnit.KG, durationSeconds = 600, distance = 2.0),
        )
        assertEquals(500.0, sets.volumeIn(WeightUnit.KG), 0.0)
    }
}
