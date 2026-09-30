package com.eplico.openfit.core.backup

import com.eplico.openfit.core.SetValues
import com.eplico.openfit.core.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate

class BackupSpreadsheetTest {
    private val squat = BackupExercise("Barbell Squat", "Legs")
    private val bench = BackupExercise("Flat Barbell Bench Press", "Chest")
    private val cable = BackupExercise("Cable Row (single arm)", "Back")

    private val sample = Backup(
        exercises = listOf(squat, bench, cable, BackupExercise("Zercher Squat", "Legs")),
        workouts = listOf(
            BackupWorkout(
                date = LocalDate.of(2026, 9, 30),
                unit = WeightUnit.LB,
                exercises = listOf(
                    BackupWorkoutExercise(squat, listOf(SetValues(225.0, WeightUnit.LB, 1.0, 5), SetValues(235.0, WeightUnit.LB, 1.0, 3))),
                    BackupWorkoutExercise(cable, listOf(SetValues(40.0, WeightUnit.KG, 0.5, 12))),
                    BackupWorkoutExercise(bench, emptyList()),
                ),
            ),
            BackupWorkout(
                date = LocalDate.of(2026, 9, 28),
                unit = WeightUnit.KG,
                exercises = listOf(BackupWorkoutExercise(bench, listOf(SetValues(62.5, WeightUnit.KG, 1.0, 8)))),
            ),
        ),
        presets = listOf(
            BackupPreset("Legs", listOf(squat, BackupExercise("Zercher Squat", "Legs"))),
            BackupPreset("Empty day", emptyList()),
        ),
    )

    private fun roundTrip(backup: Backup): ParsedBackup {
        val out = ByteArrayOutputStream()
        BackupSpreadsheet.write(backup, out)
        return BackupSpreadsheet.read(ByteArrayInputStream(out.toByteArray()))
    }

    @Test
    fun roundTripKeepsEverything() {
        val parsed = roundTrip(sample)
        assertEquals(emptyList<String>(), parsed.warnings)
        // Workouts come back oldest first and the library sorted by category; nothing else changes.
        val expected = sample.copy(
            exercises = listOf(cable, bench, squat, BackupExercise("Zercher Squat", "Legs")),
            workouts = sample.workouts.sortedBy { it.date },
        )
        assertEquals(expected, parsed.backup)
        assertEquals(4, parsed.backup.setCount)
    }

    @Test
    fun setsSheetIsReadableAsAPlainTable() {
        val sheets = BackupSpreadsheet.toSheets(sample)
        assertEquals(listOf("Sets", "Presets", "Exercises"), sheets.map { it.name })
        val sets = sheets.first()
        assertEquals(
            listOf("Date", "Exercise", "Category", "Set", "Weight", "Unit", "Ratio", "Calculated Weight", "Reps", "Workout Unit"),
            sets.header,
        )
        val cableRow = sets.rows.first { (it[1] as Cell.Text).value == cable.name }
        assertEquals(Cell.Number(40.0), cableRow[4])
        assertEquals(Cell.Text("kg"), cableRow[5])
        assertEquals(Cell.Number(0.5), cableRow[6])
        assertEquals(Cell.Number(20.0), cableRow[7]) // calculated weight = 40 × 0.5
        assertEquals(Cell.Text("lb"), cableRow[9])
    }

    private fun workbook(vararg sheets: Pair<String, List<List<String>>>) = linkedMapOf(*sheets)

    @Test
    fun handEditedSheetWithReorderedColumnsAndDefaults() {
        val parsed = BackupSpreadsheet.fromWorkbook(
            workbook(
                "sets" to listOf(
                    listOf(),
                    listOf("reps", " EXERCISE ", "weight", "date", "unit"),
                    listOf("5", "Deadlift", "140", "2026-09-01", ""),
                    listOf("3", "deadlift", "150,5", "2026-09-01", "kgs"),
                    listOf("8", "Pull Up", "", "2026-09-01", ""),
                    listOf("", "", "", "", ""),
                    listOf("10", "Curl", "30", "45901", "lbs"),
                ),
            ),
        )
        assertEquals(emptyList<String>(), parsed.warnings)
        val (sep1, sep2) = parsed.backup.workouts
        assertEquals(LocalDate.of(2025, 9, 1), sep1.date) // serial 45901
        assertEquals(WeightUnit.LB, sep1.unit)
        assertEquals(LocalDate.of(2026, 9, 1), sep2.date)
        assertEquals(WeightUnit.KG, sep2.unit) // from the first explicit set unit
        assertEquals(listOf("Deadlift", "Pull Up"), sep2.exercises.map { it.exercise.name })
        assertEquals(
            listOf(SetValues(140.0, WeightUnit.KG, 1.0, 5), SetValues(150.5, WeightUnit.KG, 1.0, 3)),
            sep2.exercises[0].sets,
        )
        assertEquals(listOf(SetValues(0.0, WeightUnit.KG, 1.0, 8)), sep2.exercises[1].sets) // bodyweight
        assertEquals("", sep2.exercises[0].exercise.category)
    }

    @Test
    fun badRowsAreSkippedWithAReason() {
        val parsed = BackupSpreadsheet.fromWorkbook(
            workbook(
                "Sets" to listOf(
                    listOf("Date", "Exercise", "Weight", "Reps", "Ratio", "Unit"),
                    listOf("yesterday", "Squat", "100", "5", "", ""),
                    listOf("2026-09-30", "", "100", "5", "", ""),
                    listOf("2026-09-30", "Squat", "heavy", "5", "", ""),
                    listOf("2026-09-30", "Squat", "100", "2.5", "", ""),
                    listOf("2026-09-30", "Squat", "100", "5", "0", ""),
                    listOf("2026-09-30", "Squat", "100", "5", "", "stone"),
                    listOf("2026-09-30", "Squat", "100", "5", "2", "lb"),
                ),
            ),
        )
        assertEquals(6, parsed.warnings.size)
        assertTrue(parsed.warnings[0], parsed.warnings[0].startsWith("Sets row 2: couldn't read the date"))
        assertTrue(parsed.warnings[5], parsed.warnings[5].startsWith("Sets row 7: unit must be kg or lb"))
        val squatSets = parsed.backup.workouts.single().exercises.single().sets
        assertEquals(listOf(SetValues(100.0, WeightUnit.LB, 2.0, 5)), squatSets)
    }

    @Test
    fun missingRequiredColumnsIsAnError() {
        val error = assertThrows(SpreadsheetFormatException::class.java) {
            BackupSpreadsheet.fromWorkbook(workbook("Sets" to listOf(listOf("Date", "Exercise", "Weight"))))
        }
        assertTrue(error.message!!, error.message!!.contains("Reps"))

        assertThrows(SpreadsheetFormatException::class.java) {
            BackupSpreadsheet.fromWorkbook(workbook("Notes" to listOf(listOf("Hello"))))
        }
    }

    @Test
    fun presetsAreOrderedByTheOrderColumn() {
        val parsed = BackupSpreadsheet.fromWorkbook(
            workbook(
                "Sets" to listOf(listOf("Date", "Exercise", "Weight", "Reps")),
                "Presets" to listOf(
                    listOf("Preset", "Order", "Exercise", "Category"),
                    listOf("Push", "2", "Dips", "Chest"),
                    listOf("Push", "1", "Bench", "Chest"),
                    listOf("push", "", "Pushdown", "Triceps"),
                    listOf("", "1", "Orphan", ""),
                ),
            ),
        )
        val push = parsed.backup.presets.single()
        assertEquals("Push", push.name)
        assertEquals(listOf("Bench", "Dips", "Pushdown"), push.exercises.map { it.name })
        assertEquals(listOf("Presets row 5: no preset name"), parsed.warnings)
    }

    @Test
    fun parsesUnitsAndDatesLeniently() {
        assertEquals(WeightUnit.LB, BackupSpreadsheet.parseUnit(" Pounds "))
        assertEquals(WeightUnit.KG, BackupSpreadsheet.parseUnit("KG"))
        assertEquals(null, BackupSpreadsheet.parseUnit("st"))
        assertEquals(LocalDate.of(2026, 9, 30), BackupSpreadsheet.parseDate("2026-09-30T08:00:00"))
        assertEquals(LocalDate.of(2026, 9, 30), BackupSpreadsheet.parseDate("46295"))
        assertEquals(null, BackupSpreadsheet.parseDate("-3"))
        assertEquals(null, BackupSpreadsheet.parseDate("30/09/2026"))
    }
}
