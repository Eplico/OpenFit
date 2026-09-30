package com.eplico.openfit.ui.common

import com.eplico.openfit.core.Measure
import com.eplico.openfit.core.SetFormat
import com.eplico.openfit.core.SetValues
import com.eplico.openfit.core.WeightMode
import com.eplico.openfit.core.WeightUnit
import com.eplico.openfit.data.Exercise
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
private val dayWithYearFormat = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())
private val longDayFormat = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())

fun LocalDate.relativeLabel(today: LocalDate = LocalDate.now()): String = when (this) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    today.plusDays(1) -> "Tomorrow"
    else -> shortLabel(today)
}

fun LocalDate.shortLabel(today: LocalDate = LocalDate.now()): String =
    format(if (year == today.year) dayFormat else dayWithYearFormat)

fun LocalDate.longLabel(): String = format(longDayFormat)

/** The unit this exercise's weights are shown in on a day whose workout is in [workoutUnit]; null = no weight. */
fun Exercise.weightUnitOn(workoutUnit: WeightUnit): WeightUnit? = weightMode.unitFor(workoutUnit)

/** "60 kg × 8 reps", "5 km in 25:30", "1:30"… */
fun Exercise.describe(set: SetValues, workoutUnit: WeightUnit): String =
    SetFormat.primary(set, measure, weightUnitOn(workoutUnit))

/** Ratio and/or pace, or null. */
fun Exercise.detail(set: SetValues, workoutUnit: WeightUnit): String? =
    SetFormat.secondary(set, weightUnitOn(workoutUnit))

/** Short description of how an exercise is tracked, e.g. "Distance + time · No weight". Null for plain lifts. */
fun Exercise.typeSummary(): String? {
    if (measure == Measure.REPS && weightMode == WeightMode.WORKOUT) return null
    val weight = when (weightMode) {
        WeightMode.WORKOUT -> null
        WeightMode.NONE -> "No weight"
        else -> "Always ${weightMode.label}"
    }
    return listOfNotNull(measure.label, weight).joinToString(" · ")
}
