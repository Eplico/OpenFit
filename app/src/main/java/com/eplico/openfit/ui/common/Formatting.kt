package com.eplico.openfit.ui.common

import com.eplico.openfit.core.SetValues
import com.eplico.openfit.core.WeightMath
import com.eplico.openfit.core.WeightUnit
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

fun repsLabel(reps: Int): String = if (reps == 1) "1 rep" else "$reps reps"

/** Main line for a set shown in [unit]: "60 kg × 8 reps". */
fun SetValues.primaryLine(unit: WeightUnit): String {
    val shown = inUnit(unit)
    return "${WeightMath.formatWeight(shown.weight, unit)} × ${repsLabel(shown.reps)}"
}

/** Secondary line when a ratio is in play: "Ratio 2 → 120 kg". Null when the ratio is 1. */
fun SetValues.ratioLine(unit: WeightUnit): String? {
    if (ratio == WeightMath.DEFAULT_RATIO) return null
    val shown = inUnit(unit)
    return "Ratio ${WeightMath.format(ratio, 3)} → ${WeightMath.formatWeight(shown.calculatedWeight, unit)}"
}
