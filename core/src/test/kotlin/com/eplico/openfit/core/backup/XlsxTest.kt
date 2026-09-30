package com.eplico.openfit.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class XlsxTest {

    @Test
    fun columnNamesAndIndexes() {
        assertEquals("A", Xlsx.columnName(0))
        assertEquals("Z", Xlsx.columnName(25))
        assertEquals("AA", Xlsx.columnName(26))
        assertEquals("AZ", Xlsx.columnName(51))
        assertEquals(0, Xlsx.columnIndex("A1"))
        assertEquals(26, Xlsx.columnIndex("AA10"))
        assertEquals(51, Xlsx.columnIndex("AZ3"))
    }

    @Test
    fun excelSerialDates() {
        assertEquals(45658L, Xlsx.excelSerial(LocalDate.of(2025, 1, 1)))
        assertEquals(LocalDate.of(2025, 1, 1), Xlsx.fromExcelSerial(45658.75))
    }

    @Test
    fun writesAValidPackage() {
        val bytes = Xlsx.toBytes(listOf(Sheet("One", listOf("A"), emptyList()), Sheet("Two", listOf("B"), emptyList())))
        val names = ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name }.toList()
        }
        assertEquals(
            listOf(
                "[Content_Types].xml",
                "_rels/.rels",
                "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels",
                "xl/styles.xml",
                "xl/worksheets/sheet1.xml",
                "xl/worksheets/sheet2.xml",
            ),
            names,
        )
    }

    @Test
    fun roundTripsTextNumbersDatesAndGaps() {
        val sheets = listOf(
            Sheet(
                name = "Data",
                header = listOf("Name", "Value", "When"),
                rows = listOf(
                    listOf(Cell.Text("Tom & \"Jerry\" <3>"), Cell.Number(62.5), Cell.Date(LocalDate.of(2026, 9, 30))),
                    listOf(Cell.Text("  spaced  "), Cell.Empty, Cell.Number(100.0)),
                    listOf(Cell.Text("control\u0001chars"), Cell.Number(-0.0)),
                ),
            ),
            Sheet("Empty", listOf("Only header"), emptyList()),
        )
        val read = Xlsx.read(ByteArrayInputStream(Xlsx.toBytes(sheets)))

        assertEquals(listOf("Data", "Empty"), read.keys.toList())
        val data = read.getValue("Data")
        assertEquals(listOf("Name", "Value", "When"), data[0])
        assertEquals(listOf("Tom & \"Jerry\" <3>", "62.5", Xlsx.excelSerial(LocalDate.of(2026, 9, 30)).toString()), data[1])
        assertEquals(listOf("  spaced  ", "", "100"), data[2])
        assertEquals(listOf("controlchars", "0"), data[3])
        assertEquals(listOf(listOf("Only header")), read.getValue("Empty"))
    }

    /** The shape Google Sheets / Excel produce when re-saving: shared strings, rich text, absolute targets. */
    @Test
    fun readsSharedStringsAndRichText() {
        val bytes = zipOf(
            "xl/workbook.xml" to """
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets><sheet name="Sets" sheetId="7" r:id="rIdA"/></sheets>
                </workbook>
            """.trimIndent(),
            "xl/_rels/workbook.xml.rels" to """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rIdA" Type="worksheet" Target="/xl/worksheets/data.xml"/>
                </Relationships>
            """.trimIndent(),
            "xl/sharedStrings.xml" to """
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <si><t>Date</t></si>
                  <si><r><t>Bench </t></r><r><rPr><b/></rPr><t>Press</t></r></si>
                </sst>
            """.trimIndent(),
            "xl/worksheets/data.xml" to """
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1"><c r="A1" t="s"><v>0</v></c></row>
                    <row r="3"><c r="C3" t="s"><v>1</v></c><c r="A3"><v>46295</v></c><c r="B3" t="str"><v>x</v></c></row>
                  </sheetData>
                </worksheet>
            """.trimIndent(),
        )
        val sets = Xlsx.read(ByteArrayInputStream(bytes)).getValue("Sets")
        assertEquals(listOf("Date"), sets[0])
        assertEquals(emptyList<String>(), sets[1])
        assertEquals(listOf("46295", "x", "Bench Press"), sets[2])
    }

    @Test
    fun rejectsFilesThatAreNotSpreadsheets() {
        assertThrows(SpreadsheetFormatException::class.java) {
            Xlsx.read(ByteArrayInputStream("Date,Exercise\n2026-09-30,Squat".toByteArray()))
        }
        assertThrows(SpreadsheetFormatException::class.java) {
            Xlsx.read(ByteArrayInputStream(zipOf("hello.xml" to "<a/>")))
        }
        assertThrows(SpreadsheetFormatException::class.java) {
            Xlsx.read(ByteArrayInputStream(zipOf("xl/workbook.xml" to "<not closed")))
        }
    }

    private fun zipOf(vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
