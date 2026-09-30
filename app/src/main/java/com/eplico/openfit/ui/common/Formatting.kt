package com.eplico.openfit.ui.common

import com.eplico.openfit.core.Measure
import com.eplico.openfit.core.SetFormat
import com.eplico.openfit.core.SetValues
import com.eplico.openfit.core.WeightMode
import com.eplico.openfit.core.WeightUnit
import com.eplico.openfit.data.Exercise
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

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

/** "120 kg × 8 reps" (calculated weight, in the unit it was logged in), "5 km in 25:30", "1:30"… */
fun Exercise.describe(set: SetValues): String = SetFormat.primary(set, measure)

/** The pace of a distance + time set, or null. */
fun Exercise.detail(set: SetValues): String? = SetFormat.secondary(set)

/** "1,234,567 kg": a big total, rounded to whole units, with thousands separators. */
fun formatTotalWeight(value: Double, unit: WeightUnit): String =
    "${NumberFormat.getIntegerInstance().format(value.roundToLong())} ${unit.label}"

/** Short description of how an exercise is tracked, e.g. "Distance + time · No weight". Null for plain lifts. */
fun Exercise.typeSummary(): String? {
    if (measure == Measure.REPS && weightMode == WeightMode.DEFAULT) return null
    val weight = when (weightMode) {
        WeightMode.DEFAULT -> null
        WeightMode.NONE -> "No weight"
        else -> "In ${weightMode.label}"
    }
    return listOfNotNull(measure.label, weight).joinToString(" · ")
}
