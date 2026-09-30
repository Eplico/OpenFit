package com.eplico.openfit.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eplico.openfit.core.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.util.Locale

data class UserSettings(
    /** Unit a brand-new workout starts in. Each workout can still be switched individually. */
    val defaultUnit: WeightUnit = localeDefaultUnit(),
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val kgIncrement: Double = WeightUnit.KG.defaultIncrement,
    val lbIncrement: Double = WeightUnit.LB.defaultIncrement,
) {
    fun increment(unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> kgIncrement
        WeightUnit.LB -> lbIncrement
    }
}

private fun localeDefaultUnit(): WeightUnit =
    if (Locale.getDefault().country in setOf("US", "LR", "MM")) WeightUnit.LB else WeightUnit.KG

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val defaultUnit = stringPreferencesKey("default_unit")
        val weekStart = stringPreferencesKey("week_start")
        val kgIncrement = doublePreferencesKey("kg_increment")
        val lbIncrement = doublePreferencesKey("lb_increment")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): UserSettings = settings.first()

    suspend fun setDefaultUnit(unit: WeightUnit) {
        context.dataStore.edit { it[Keys.defaultUnit] = unit.name }
    }

    suspend fun setWeekStart(day: DayOfWeek) {
        context.dataStore.edit { it[Keys.weekStart] = day.name }
    }

    suspend fun setIncrement(unit: WeightUnit, value: Double) {
        val key = if (unit == WeightUnit.KG) Keys.kgIncrement else Keys.lbIncrement
        context.dataStore.edit { it[key] = value }
    }

    private fun Preferences.toSettings(): UserSettings {
        val defaults = UserSettings()
        return UserSettings(
            defaultUnit = this[Keys.defaultUnit]?.let { name -> WeightUnit.entries.firstOrNull { it.name == name } }
                ?: defaults.defaultUnit,
            weekStart = this[Keys.weekStart]?.let { name -> DayOfWeek.values().firstOrNull { it.name == name } }
                ?: defaults.weekStart,
            kgIncrement = this[Keys.kgIncrement] ?: defaults.kgIncrement,
            lbIncrement = this[Keys.lbIncrement] ?: defaults.lbIncrement,
        )
    }
}
