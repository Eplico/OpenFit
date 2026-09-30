package com.eplico.openfit.core

enum class WeightUnit(val label: String, val defaultIncrement: Double) {
    KG("kg", 2.5),
    LB("lb", 5.0);

    val other: WeightUnit
        get() = if (this == KG) LB else KG

    /** Converts [value] expressed in this unit into [target]. */
    fun convert(value: Double, target: WeightUnit): Double = when {
        this == target -> value
        this == KG -> value * LB_PER_KG
        else -> value / LB_PER_KG
    }

    companion object {
        const val LB_PER_KG = 2.20462262185

        fun fromLabel(label: String?): WeightUnit? = entries.firstOrNull { it.label == label || it.name == label }
    }
}
