package com.eplico.openfit.core.backup

import com.eplico.openfit.core.SetValues
import com.eplico.openfit.core.WeightMath
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
 * Converts a [Backup] to and from an .xlsx workbook with three sheets:
 *
 * - **Sets**: one row per set (Date, Exercise, Category, Set, Weight, Unit, Ratio,
 *   Calculated Weight, Reps, Workout Unit). A row with no weight and no reps is an exercise that
 *   was planned for that day but not logged.
 * - **Presets**: one row per exercise in a preset (Preset, Order, Exercise, Category).
 * - **Exercises**: the exercise library (Exercise, Category).
 *
 * Reading is lenient so a sheet edited by hand still imports: columns are found by header name in
 * any order, only Date/Exercise/Weight/Reps are required, and bad rows are skipped with a warning.
 */
object BackupSpreadsheet {
    const val SETS = "Sets"
    const val PRESETS = "Presets"
    const val EXERCISES = "Exercises"

    private const val DATE = "Date"
    private const val EXERCISE = "Exercise"
    private const val CATEGORY = "Category"
    private const val SET = "Set"
    private const val WEIGHT = "Weight"
    private const val UNIT = "Unit"
    private const val RATIO = "Ratio"
    private const val CALCULATED = "Calculated Weight"
    private const val REPS = "Reps"
    private const val WORKOUT_UNIT = "Workout Unit"
    private const val PRESET = "Preset"
    private const val ORDER = "Order"

    fun write(backup: Backup, out: OutputStream) = Xlsx.write(toSheets(backup), out)

    fun read(input: InputStream): ParsedBackup = fromWorkbook(Xlsx.read(input))

    // ---------------------------------------------------------------- export

    fun toSheets(backup: Backup): List<Sheet> = listOf(setsSheet(backup), presetsSheet(backup), exercisesSheet(backup))

    private fun setsSheet(backup: Backup): Sheet {
        val rows = ArrayList<List<Cell>>()
        for (workout in backup.workouts.sortedBy { it.date }) {
            for (entry in workout.exercises) {
                val name = Cell.Text(entry.exercise.name)
                val category = Cell.Text(entry.exercise.category)
                val workoutUnit = Cell.Text(workout.unit.label)
                if (entry.sets.isEmpty()) {
                    rows += listOf(
                        Cell.Date(workout.date), name, category,
                        Cell.Empty, Cell.Empty, Cell.Empty, Cell.Empty, Cell.Empty, Cell.Empty,
                        workoutUnit,
                    )
                }
                entry.sets.forEachIndexed { index, set ->
                    rows += listOf(
                        Cell.Date(workout.date),
                        name,
                        category,
                        Cell.Number((index + 1).toDouble()),
                        Cell.Number(set.weight),
                        Cell.Text(set.unit.label),
                        Cell.Number(set.ratio),
                        Cell.Number(WeightMath.round(set.calculatedWeight, 3)),
                        Cell.Number(set.reps.toDouble()),
                        workoutUnit,
                    )
                }
            }
        }
        return Sheet(
            name = SETS,
            header = listOf(DATE, EXERCISE, CATEGORY, SET, WEIGHT, UNIT, RATIO, CALCULATED, REPS, WORKOUT_UNIT),
            rows = rows,
            columnWidths = listOf(12.0, 30.0, 12.0, 6.0, 9.0, 6.0, 7.0, 18.0, 6.0, 14.0),
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
        header = listOf(EXERCISE, CATEGORY),
        rows = backup.exercises
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, BackupExercise::category).thenBy(String.CASE_INSENSITIVE_ORDER, BackupExercise::name))
            .map { listOf(Cell.Text(it.name), Cell.Text(it.category)) },
        columnWidths = listOf(30.0, 12.0),
    )

    // ---------------------------------------------------------------- import

    fun fromWorkbook(workbook: Map<String, List<List<String>>>): ParsedBackup {
        val warnings = ArrayList<String>()
        val setsRows = workbook.sheetNamed(SETS)
            ?: workbook.values.firstOrNull()?.takeIf { Table(it).has(DATE) && Table(it).has(EXERCISE) }
            ?: throw SpreadsheetFormatException("Couldn't find a \"$SETS\" sheet with $DATE, $EXERCISE, $WEIGHT and $REPS columns")

        val workouts = parseSets(Table(setsRows), warnings)
        val exercises = workbook.sheetNamed(EXERCISES)?.let { parseExercises(Table(it), warnings) }.orEmpty()
        val presets = workbook.sheetNamed(PRESETS)?.let { parsePresets(Table(it), warnings) }.orEmpty()
        return ParsedBackup(Backup(exercises = exercises, workouts = workouts, presets = presets), warnings)
    }

    private class PendingSet(val weight: Double, val unit: WeightUnit?, val ratio: Double, val reps: Int)

    private class PendingDay(val date: LocalDate) {
        var unit: WeightUnit? = null
        val exercises = LinkedHashMap<String, Pair<String, MutableList<PendingSet>>>()
    }

    private fun parseSets(table: Table, warnings: MutableList<String>): List<BackupWorkout> {
        val missing = listOf(DATE, EXERCISE, WEIGHT, REPS).filterNot { table.has(it) }
        if (missing.isNotEmpty()) {
            throw SpreadsheetFormatException("The $SETS sheet is missing these columns: ${missing.joinToString()}")
        }
        val days = LinkedHashMap<LocalDate, PendingDay>()
        val categories = HashMap<String, String>()

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
            val key = name.lowercase()
            table.value(row, CATEGORY).takeIf { it.isNotEmpty() }?.let { categories.putIfAbsent(key, it) }

            val weightText = table.value(row, WEIGHT)
            val repsText = table.value(row, REPS)
            val set = if (weightText.isEmpty() && repsText.isEmpty()) {
                null // planned but not logged
            } else {
                val weight = if (weightText.isEmpty()) 0.0 else WeightMath.parse(weightText)?.takeIf { it >= 0.0 }
                val reps = parseReps(repsText)
                val ratioText = table.value(row, RATIO)
                val ratio = if (ratioText.isEmpty()) WeightMath.DEFAULT_RATIO else WeightMath.parse(ratioText)?.takeIf { it > 0.0 }
                val unitText = table.value(row, UNIT)
                val unit = if (unitText.isEmpty()) null else parseUnit(unitText)
                val problem = when {
                    weight == null -> "couldn't read the weight \"$weightText\""
                    reps == null -> "reps must be a whole number above 0 (got \"$repsText\")"
                    ratio == null -> "ratio must be a number above 0 (got \"$ratioText\")"
                    unitText.isNotEmpty() && unit == null -> "unit must be kg or lb (got \"$unitText\")"
                    else -> null
                }
                if (problem != null || weight == null || reps == null || ratio == null) {
                    warnings += "$SETS row $rowNumber: $problem"
                    continue
                }
                PendingSet(weight, unit, ratio, reps)
            }

            val day = days.getOrPut(date) { PendingDay(date) }
            if (day.unit == null) day.unit = parseUnit(table.value(row, WORKOUT_UNIT))
            val slot = day.exercises.getOrPut(key) { name to ArrayList() }
            if (set != null) slot.second += set
        }

        return days.values.sortedBy { it.date }.map { day ->
            val dayUnit = day.unit
                ?: day.exercises.values.flatMap { it.second }.firstNotNullOfOrNull { it.unit }
                ?: WeightUnit.KG
            BackupWorkout(
                date = day.date,
                unit = dayUnit,
                exercises = day.exercises.map { (key, slot) ->
                    BackupWorkoutExercise(
                        exercise = BackupExercise(slot.first, categories[key].orEmpty()),
                        sets = slot.second.map { SetValues(it.weight, it.unit ?: dayUnit, it.ratio, it.reps) },
                    )
                },
            )
        }
    }

    private fun parseExercises(table: Table, warnings: MutableList<String>): List<BackupExercise> {
        if (!table.has(EXERCISE)) {
            if (table.dataRows.isNotEmpty()) warnings += "$EXERCISES sheet skipped: it has no \"$EXERCISE\" column"
            return emptyList()
        }
        return table.dataRows.mapNotNull { (_, row) ->
            table.value(row, EXERCISE).takeIf { it.isNotEmpty() }?.let { BackupExercise(it, table.value(row, CATEGORY)) }
        }.distinctBy { it.name.lowercase() }
    }

    private fun parsePresets(table: Table, warnings: MutableList<String>): List<BackupPreset> {
        if (!table.has(PRESET) || !table.has(EXERCISE)) {
            if (table.dataRows.isNotEmpty()) warnings += "$PRESETS sheet skipped: it needs \"$PRESET\" and \"$EXERCISE\" columns"
            return emptyList()
        }
        class Item(val order: Double?, val row: Int, val exercise: BackupExercise)
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
            slot.second += Item(WeightMath.parse(table.value(row, ORDER)), rowNumber, BackupExercise(exercise, table.value(row, CATEGORY)))
        }
        return presets.values.map { (name, items) ->
            BackupPreset(
                name = name,
                exercises = items
                    .sortedWith(compareBy<Item, Double?>(nullsLast()) { it.order }.thenBy { it.row })
                    .map { it.exercise },
            )
        }
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
        if (value <= 0.0 || value % 1.0 != 0.0 || value > 100_000) return null
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
