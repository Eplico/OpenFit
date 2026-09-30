package com.eplico.openfit.data

import androidx.room.TypeConverter
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
}
