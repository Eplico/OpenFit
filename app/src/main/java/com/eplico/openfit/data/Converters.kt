package com.eplico.openfit.data

import androidx.room.TypeConverter
import com.eplico.openfit.core.DistanceUnit
import com.eplico.openfit.core.Measure
import com.eplico.openfit.core.WeightMode
import com.eplico.openfit.core.WeightUnit
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun dateToEpochDay(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun epochDayToDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    @TypeConverter
    fun unitToName(unit: WeightUnit): String = unit.name

    @TypeConverter
    fun nameToUnit(name: String): WeightUnit = WeightUnit.valueOf(name)

    @TypeConverter
    fun measureToName(measure: Measure): String = measure.name

    @TypeConverter
    fun nameToMeasure(name: String): Measure = Measure.entries.firstOrNull { it.name == name } ?: Measure.REPS

    @TypeConverter
    fun weightModeToName(mode: WeightMode): String = mode.name

    @TypeConverter
    fun nameToWeightMode(name: String): WeightMode = WeightMode.entries.firstOrNull { it.name == name } ?: WeightMode.DEFAULT

    @TypeConverter
    fun distanceUnitToName(unit: DistanceUnit): String = unit.name

    @TypeConverter
    fun nameToDistanceUnit(name: String): DistanceUnit = DistanceUnit.entries.firstOrNull { it.name == name } ?: DistanceUnit.KM
}
