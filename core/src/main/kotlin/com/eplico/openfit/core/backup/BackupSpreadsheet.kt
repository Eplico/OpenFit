package com.eplico.openfit.core.backup

import com.eplico.openfit.core.DistanceUnit
import com.eplico.openfit.core.Durations
import com.eplico.openfit.core.Measure
import com.eplico.openfit.core.SetValues
import com.eplico.openfit.core.WeightMath
import com.eplico.openfit.core.WeightMode
import com.eplico.openfit.core.WeightUnit
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate

/** Result of reading a spreadsheet: the data found, plus a note for every row that was skipped. */
data class ParsedBackup(
    val backup: Backup,
    val warnings: List<String>,
)

/**
 * Converts a [Backup] to and from an .xlsx workbook with four sheets:
 *
 * - **Sets**: one row per set (Date, Exercise, Category, Set, Weight, Unit, Ratio,
 *   Calculated Weight, Reps, Time, Distance, Distance Unit). A row with no weight, reps, time or
 *   distance is an exercise that was planned for that day but not logged.
 * - **Presets**: one row per exercise in a preset (Preset, Order, Exercise, Category).
 * - **Exercises**: the exercise library (Exercise, Category, Measure, Weight).
 * - **Categories**: category names in display order.
 *
 * Reading is lenient so a sheet edited by hand (or written by an older OpenFit) still imports:
 * columns are found by header name in any order, only Date, Exercise and one of Reps/Time/Distance
 * are required, missing exercise types are inferred from the sets, a set without a Unit uses the
 * default unit, and bad rows are skipped with a warning.
 */
object BackupSpreadsheet {
    const val SETS = "Sets"
    const val PRESETS = "Presets"
    const val EXERCISES = "Exercises"
    const val CATEGORIES = "Categories"

    private const val DATE = "Date"
    private const val EXERCISE = "Exercise"
    private const val CATEGORY = "Category"
    private const val SET = "Set"
    private const val WEIGHT = "Weight"
    private const val UNIT = "Unit"
    private const val RATIO = "Ratio"
    private const val CALCULATED = "Calculated Weight"
    private const val REPS = "Reps"
    private const val TIME = "Time"
    private const val DISTANCE = "Distance"
    private const val DISTANCE_UNIT = "Distance Unit"
    /** Written by OpenFit 0.3 and earlier; still read as the unit for sets that don't name one. */
    private const val WORKOUT_UNIT = "Workout Unit"
    private const val PRESET = "Preset"
    private const val ORDER = "Order"
    private const val MEASURE = "Measure"

    fun write(backup: Backup, out: OutputStream) = Xlsx.write(toSheets(backup), out)

    /** [defaultUnit] is used for weights whose row doesn't say kg or lb. */
    fun read(input: InputStream, defaultUnit: WeightUnit = WeightUnit.KG): ParsedBackup =
        fromWorkbook(Xlsx.read(input), defaultUnit)

    // ---------------------------------------------------------------- export

    fun toSheets(backup: Backup): List<Sheet> =
        listOf(setsSheet(backup), presetsSheet(backup), exercisesSheet(backup), categoriesSheet(backup))

    private fun setsSheet(backup: Backup): Sheet {
        val rows = ArrayList<List<Cell>>()
        for (workout in backup.workouts.sortedBy { it.date }) {
            for (entry in workout.exercises) {
                val exercise = entry.exercise
                val leading = listOf(Cell.Date(workout.date), Cell.Text(exercise.name), Cell.Text(exercise.category))
                if (entry.sets.isEmpty()) {
                    rows += leading + List(9) { Cell.Empty }
                }
                entry.sets.forEachIndexed { index, set ->
                    val weight = if (exercise.weightMode.tracksWeight) {
                        listOf(
                            Cell.Number(set.weight),
                            Cell.Text(set.unit.label),
                            Cell.Number(set.ratio),
                            Cell.Number(WeightMath.round(set.calculatedWeight, 3)),
                        )
                    } else {
                        List(4) { Cell.Empty }
                    }
                    val showDistanceUnit = set.distance > 0.0 || exercise.measure.usesDistance
                    rows += leading +
                        Cell.Number((index + 1).toDouble()) +
                        weight +
                        listOf(
                            if (set.reps > 0) Cell.Number(set.reps.toDouble()) else Cell.Empty,
                            if (set.durationSeconds > 0) Cell.Text(Durations.format(set.durationSeconds)) else Cell.Empty,
                            if (set.distance > 0.0) Cell.Number(set.distance) else Cell.Empty,
                            if (showDistanceUnit) Cell.Text(set.distanceUnit.label) else Cell.Empty,
                        )
                }
            }
        }
        return Sheet(
            name = SETS,
            header = listOf(DATE, EXERCISE, CATEGORY, SET, WEIGHT, UNIT, RATIO, CALCULATED, REPS, TIME, DISTANCE, DISTANCE_UNIT),
            rows = rows,
            columnWidths = listOf(12.0, 30.0, 12.0, 6.0, 9.0, 6.0, 7.0, 18.0, 6.0, 9.0, 10.0, 14.0),
        )
    }

    private fun presetsSheet(backup: Backup): Sheet {
        val rows = ArrayList<List<Cell>>()
        for (preset in backup.presets) {
            if (preset.exercises.isEmpty()) {
                rows += listOf(Cell.Text(preset.name), Cell.Empty, Cell.Empty, Cell.Empty)
            }
            preset.exercises.forEachIndexed { index, exercise ->
                rows += listOf(
                    Cell.Text(preset.name),
                    Cell.Number((index + 1).toDouble()),
                    Cell.Text(exercise.name),
                    Cell.Text(exercise.category),
                )
            }
        }
        return Sheet(
            name = PRESETS,
            header = listOf(PRESET, ORDER, EXERCISE, CATEGORY),
            rows = rows,
            columnWidths = listOf(20.0, 7.0, 30.0, 12.0),
        )
    }

    private fun exercisesSheet(backup: Backup): Sheet = Sheet(
        name = EXERCISES,
        header = listOf(EXERCISE, CATEGORY, MEASURE, WEIGHT),
        rows = backup.exercises
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, BackupExercise::category).thenBy(String.CASE_INSENSITIVE_ORDER, BackupExercise::name))
            .map { listOf(Cell.Text(it.name), Cell.Text(it.category), Cell.Text(it.measure.label), Cell.Text(it.weightMode.label)) },
        columnWidths = listOf(30.0, 12.0, 16.0, 14.0),
    )

    private fun categoriesSheet(backup: Backup): Sheet = Sheet(
        name = CATEGORIES,
        header = listOf(CATEGORY),
        rows = backup.categories.map { listOf(Cell.Text(it)) },
        columnWidths = listOf(20.0),
    )

    // ---------------------------------------------------------------- import

    fun fromWorkbook(workbook: Map<String, List<List<String>>>, defaultUnit: WeightUnit = WeightUnit.KG): ParsedBackup {
        val warnings = ArrayList<String>()
        val setsRows = workbook.sheetNamed(SETS)
            ?: workbook.values.firstOrNull()?.takeIf { Table(it).has(DATE) && Table(it).has(EXERCISE) }
            ?: throw SpreadsheetFormatException("Couldn't find a \"$SETS\" sheet with $DATE and $EXERCISE columns")

        val days = parseSets(Table(setsRows), warnings)
        val sheetExercises = workbook.sheetNamed(EXERCISES)?.let { parseExercises(Table(it), warnings) }.orEmpty()
        val presets = workbook.sheetNamed(PRESETS)?.let { parsePresets(Table(it), warnings) }.orEmpty()
        val categories = workbook.sheetNamed(CATEGORIES)?.let { parseCategories(Table(it)) }.orEmpty()

        // One definition per exercise name, from the Exercises sheet first, then the other sheets.
        val definitions = LinkedHashMap<String, Definition>()
        sheetExercises.forEach { definitions.putIfAbsent(it.name.lowercase(), it) }
        for (day in days) {
            for ((key, slot) in day.exercises) {
                val definition = definitions.getOrPut(key) { Definition(slot.name, slot.category) }
                if (definition.category.isEmpty()) definition.category = slot.category
                definition.sets += slot.sets
            }
        }
        for (preset in presets) {
            for (item in preset.items) {
                val definition = definitions.getOrPut(item.name.lowercase()) { Definition(item.name, item.category) }
                if (definition.category.isEmpty()) definition.category = item.category
            }
        }
        val resolved = definitions.mapValues { (_, definition) -> definition.resolve() }

        val workouts = days.sortedBy { it.date }.map { day ->
            val fallbackUnit = day.legacyUnit ?: defaultUnit
            BackupWorkout(
                date = day.date,
                exercises = day.exercises.map { (key, slot) ->
                    BackupWorkoutExercise(resolved.getValue(key), slot.sets.map { it.toSetValues(fallbackUnit) })
                },
            )
        }
        return ParsedBackup(
            backup = Backup(
                exercises = resolved.values.toList(),
                workouts = workouts,
                presets = presets.map { preset ->
                    BackupPreset(preset.name, preset.items.map { resolved.getValue(it.name.lowercase()) })
                },
                categories = categories,
            ),
            warnings = warnings,
        )
    }

    private class PendingSet(
        val weight: Double,
        val unit: WeightUnit?,
        val ratio: Double,
        val reps: Int,
        val durationSeconds: Int,
        val distance: Double,
        val distanceUnit: DistanceUnit,
    ) {
        fun toSetValues(fallbackUnit: WeightUnit) =
            SetValues(weight, unit ?: fallbackUnit, ratio, reps, durationSeconds, distance, distanceUnit)
    }

    private class PendingExercise(val name: String, var category: String) {
        val sets = ArrayList<PendingSet>()
    }

    private class PendingDay(val date: LocalDate) {
        /** The day's "Workout Unit" from an older spreadsheet, if it has one. */
        var legacyUnit: WeightUnit? = null
        val exercises = LinkedHashMap<String, PendingExercise>()
    }

    private class Definition(
        val name: String,
        var category: String,
        val measure: Measure? = null,
        val weightMode: WeightMode? = null,
    ) {
        val sets = ArrayList<PendingSet>()

        /** Fills in a missing measure/weight setting from what the sets actually recorded. */
        fun resolve(): BackupExercise {
            val measure = measure ?: when {
                sets.any { it.reps > 0 } -> Measure.REPS
                sets.any { it.distance > 0.0 } && sets.any { it.durationSeconds > 0 } -> Measure.DISTANCE_TIME
                sets.any { it.distance > 0.0 } -> Measure.DISTANCE
                sets.any { it.durationSeconds > 0 } -> Measure.TIME
                else -> Measure.REPS
            }
            val weightMode = weightMode ?: if (measure != Measure.REPS && sets.none { it.weight > 0.0 }) {
                WeightMode.NONE
            } else {
                WeightMode.DEFAULT
            }
            return BackupExercise(name, category, measure, weightMode)
        }
    }

    private fun parseSets(table: Table, warnings: MutableList<String>): List<PendingDay> {
        val missing = listOf(DATE, EXERCISE).filterNot { table.has(it) }
        if (missing.isNotEmpty() || listOf(REPS, TIME, DISTANCE).none { table.has(it) }) {
            throw SpreadsheetFormatException(
                "The $SETS sheet needs $DATE and $EXERCISE columns, plus at least one of $REPS, $TIME or $DISTANCE",
            )
        }
        val days = LinkedHashMap<LocalDate, PendingDay>()

        for ((rowNumber, row) in table.dataRows) {
            val dateText = table.value(row, DATE)
            val date = parseDate(dateText)
            if (date == null) {
                warnings += "$SETS row $rowNumber: couldn't read the date \"$dateText\""
                continue
            }
            val name = table.value(row, EXERCISE)
            if (name.isEmpty()) {
                warnings += "$SETS row $rowNumber: no exercise name"
                continue
            }

            val weightText = table.value(row, WEIGHT)
            val repsText = table.value(row, REPS)
            val timeText = table.value(row, TIME)
            val distanceText = table.value(row, DISTANCE)
            val set = if (listOf(weightText, repsText, timeText, distanceText).all { it.isEmpty() }) {
                null // planned but not logged
            } else {
                val weight = if (weightText.isEmpty()) 0.0 else WeightMath.parse(weightText)?.takeIf { it >= 0.0 }
                val reps = if (repsText.isEmpty()) 0 else parseReps(repsText)
                val duration = if (timeText.isEmpty()) 0 else Durations.parse(timeText)
                val distance = if (distanceText.isEmpty()) 0.0 else WeightMath.parse(distanceText)?.takeIf { it >= 0.0 }
                val distanceUnitText = table.value(row, DISTANCE_UNIT)
                val distanceUnit = if (distanceUnitText.isEmpty()) DistanceUnit.KM else DistanceUnit.parse(distanceUnitText)
                val ratioText = table.value(row, RATIO)
                val ratio = if (ratioText.isEmpty()) WeightMath.DEFAULT_RATIO else WeightMath.parse(ratioText)?.takeIf { it > 0.0 }
                val unitText = table.value(row, UNIT)
                val unit = if (unitText.isEmpty()) null else parseUnit(unitText)
                val problem = when {
                    weight == null -> "couldn't read the weight \"$weightText\""
                    reps == null -> "reps must be a whole number (got \"$repsText\")"
                    duration == null -> "time must look like 25:30 or 1:02:03 (got \"$timeText\")"
                    distance == null -> "couldn't read the distance \"$distanceText\""
                    distanceUnit == null -> "distance unit must be m, km or mi (got \"$distanceUnitText\")"
                    ratio == null -> "ratio must be a number above 0 (got \"$ratioText\")"
                    unitText.isNotEmpty() && unit == null -> "unit must be kg or lb (got \"$unitText\")"
                    reps == 0 && duration == 0 && distance == 0.0 -> "needs reps, a time or a distance"
                    else -> null
                }
                if (problem != null || weight == null || reps == null || duration == null ||
                    distance == null || distanceUnit == null || ratio == null
                ) {
                    warnings += "$SETS row $rowNumber: $problem"
                    continue
                }
                PendingSet(weight, unit, ratio, reps, duration, distance, distanceUnit)
            }

            val day = days.getOrPut(date) { PendingDay(date) }
            if (day.legacyUnit == null) day.legacyUnit = parseUnit(table.value(row, WORKOUT_UNIT))
            val slot = day.exercises.getOrPut(name.lowercase()) { PendingExercise(name, "") }
            if (slot.category.isEmpty()) slot.category = table.value(row, CATEGORY)
            if (set != null) slot.sets += set
        }
        return days.values.toList()
    }

    private fun parseExercises(table: Table, warnings: MutableList<String>): List<Definition> {
        if (!table.has(EXERCISE)) {
            if (table.dataRows.isNotEmpty()) warnings += "$EXERCISES sheet skipped: it has no \"$EXERCISE\" column"
            return emptyList()
        }
        return table.dataRows.mapNotNull { (rowNumber, row) ->
            val name = table.value(row, EXERCISE).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val measureText = table.value(row, MEASURE)
            val weightText = table.value(row, WEIGHT)
            val measure = Measure.parse(measureText)
            val weightMode = WeightMode.parse(weightText)
            if (measureText.isNotEmpty() && measure == null) {
                warnings += "$EXERCISES row $rowNumber: unknown measure \"$measureText\" (use Reps, Time, Distance or Distance + time)"
            }
            if (weightText.isNotEmpty() && weightMode == null) {
                warnings += "$EXERCISES row $rowNumber: unknown weight setting \"$weightText\" (use Default unit, kg, lb or No weight)"
            }
            Definition(name, table.value(row, CATEGORY), measure, weightMode)
        }.distinctBy { it.name.lowercase() }
    }

    private class PresetRow(val name: String, val items: List<PendingExercise>)

    private fun parsePresets(table: Table, warnings: MutableList<String>): List<PresetRow> {
        if (!table.has(PRESET) || !table.has(EXERCISE)) {
            if (table.dataRows.isNotEmpty()) warnings += "$PRESETS sheet skipped: it needs \"$PRESET\" and \"$EXERCISE\" columns"
            return emptyList()
        }
        class Item(val order: Double?, val row: Int, val exercise: PendingExercise)
        val presets = LinkedHashMap<String, Pair<String, MutableList<Item>>>()
        for ((rowNumber, row) in table.dataRows) {
            val presetName = table.value(row, PRESET)
            if (presetName.isEmpty()) {
                warnings += "$PRESETS row $rowNumber: no preset name"
                continue
            }
            val slot = presets.getOrPut(presetName.lowercase()) { presetName to ArrayList() }
            val exercise = table.value(row, EXERCISE)
            if (exercise.isEmpty()) continue // a preset with no exercises yet
            slot.second += Item(WeightMath.parse(table.value(row, ORDER)), rowNumber, PendingExercise(exercise, table.value(row, CATEGORY)))
        }
        return presets.values.map { (name, items) ->
            PresetRow(
                name = name,
                items = items
                    .sortedWith(compareBy<Item, Double?>(nullsLast()) { it.order }.thenBy { it.row })
                    .map { it.exercise },
            )
        }
    }

    private fun parseCategories(table: Table): List<String> {
        if (!table.has(CATEGORY)) return emptyList()
        return table.dataRows
            .mapNotNull { (_, row) -> table.value(row, CATEGORY).takeIf { it.isNotEmpty() } }
            .distinctBy { it.lowercase() }
    }

    // ---------------------------------------------------------------- cell parsing

    /** Accepts Excel date serials (what we write) and ISO dates like 2026-09-30 (what people type). */
    fun parseDate(text: String): LocalDate? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        trimmed.toDoubleOrNull()?.let { serial ->
            return if (serial >= 1.0 && serial < 2_958_466.0) Xlsx.fromExcelSerial(serial) else null
        }
        return runCatching { LocalDate.parse(trimmed.take(10)) }.getOrNull()
    }

    fun parseUnit(text: String): WeightUnit? = when (text.trim().lowercase()) {
        "kg", "kgs", "kilo", "kilos", "kilogram", "kilograms" -> WeightUnit.KG
        "lb", "lbs", "pound", "pounds" -> WeightUnit.LB
        else -> null
    }

    private fun parseReps(text: String): Int? {
        val value = WeightMath.parse(text) ?: return null
        if (value < 0.0 || value % 1.0 != 0.0 || value > 100_000) return null
        return value.toInt()
    }

    private fun Map<String, List<List<String>>>.sheetNamed(name: String): List<List<String>>? =
        entries.firstOrNull { it.key.trim().equals(name, ignoreCase = true) }?.value

    /** A sheet with a header row; columns are looked up by (case-insensitive) header name. */
    private class Table(rows: List<List<String>>) {
        private val headerIndex = rows.indexOfFirst { row -> row.any { it.isNotBlank() } }
        private val columns: Map<String, Int> = if (headerIndex < 0) {
            emptyMap()
        } else {
            val map = HashMap<String, Int>()
            rows[headerIndex].forEachIndexed { index, title -> map.putIfAbsent(normalize(title), index) }
            map
        }

        /** Non-blank rows below the header, paired with their 1-based row number in the sheet. */
        val dataRows: List<Pair<Int, List<String>>> = if (headerIndex < 0) {
            emptyList()
        } else {
            rows.withIndex()
                .drop(headerIndex + 1)
                .filter { (_, row) -> row.any { it.isNotBlank() } }
                .map { (index, row) -> (index + 1) to row }
        }

        fun has(column: String): Boolean = normalize(column) in columns

        fun value(row: List<String>, column: String): String =
            columns[normalize(column)]?.let { row.getOrNull(it) }?.trim().orEmpty()

        private fun normalize(title: String) = title.trim().lowercase().replace(Regex("\\s+"), " ")
    }
}
