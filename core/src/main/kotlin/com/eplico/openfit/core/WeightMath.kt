package com.eplico.openfit.core

import java.math.BigDecimal
import java.math.RoundingMode

object WeightMath {

    const val DEFAULT_RATIO = 1.0

    /**
     * The weight actually moved: the number entered (e.g. what the machine's stack says)
     * multiplied by the ratio (e.g. 2.0 for a 2:1 pulley, 0.5 for a single-arm handle).
     */
    fun calculatedWeight(weight: Double, ratio: Double): Double = weight * ratio

    /** Converts a weight between units and rounds it so it is pleasant to edit. */
    fun convertForEntry(weight: Double, from: WeightUnit, to: WeightUnit): Double =
        round(from.convert(weight, to))

    fun round(value: Double, decimals: Int = 2): Double {
        if (value.isNaN() || value.isInfinite()) return value
        return BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).toDouble()
    }

    /**
     * Formats a number without trailing zeros: 100.0 -> "100", 62.50 -> "62.5", 61.235 -> "61.24".
     * Always uses '.' as the decimal separator so stored and displayed values match.
     */
    fun format(value: Double, maxDecimals: Int = 2): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        val scaled = BigDecimal.valueOf(value).setScale(maxDecimals, RoundingMode.HALF_UP).stripTrailingZeros()
        val text = scaled.toPlainString()
        return if (text == "-0") "0" else text
    }

    /** Parses user input, accepting either '.' or ',' as the decimal separator. */
    fun parse(text: String): Double? {
        val cleaned = text.trim().replace(',', '.')
        if (cleaned.isEmpty()) return null
        return cleaned.toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }
    }

    /** Epley estimated one-rep max. Returns the weight itself for a single. */
    fun estimatedOneRepMax(weight: Double, reps: Int): Double = when {
        reps <= 0 -> 0.0
        reps == 1 -> weight
        else -> weight * (1.0 + reps / 30.0)
    }

    fun formatWeight(value: Double, unit: WeightUnit): String = "${format(value)} ${unit.label}"

    /** "60 kg × 2 = 120 kg" when a ratio is in play, otherwise just "60 kg". */
    fun describe(weight: Double, ratio: Double, unit: WeightUnit): String =
        if (ratio == DEFAULT_RATIO) {
            formatWeight(weight, unit)
        } else {
            "${format(weight)} × ${format(ratio, 3)} = ${formatWeight(calculatedWeight(weight, ratio), unit)}"
        }
}
