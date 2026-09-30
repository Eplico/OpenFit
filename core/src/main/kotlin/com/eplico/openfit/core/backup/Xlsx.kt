package com.eplico.openfit.core.backup

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

/** A single spreadsheet cell to write. */
sealed interface Cell {
    data class Text(val value: String) : Cell
    data class Number(val value: Double) : Cell
    data class Date(val value: LocalDate) : Cell
    data object Empty : Cell
}

/** One worksheet: a bold, frozen header row followed by data rows. */
data class Sheet(
    val name: String,
    val header: List<String>,
    val rows: List<List<Cell>>,
    /** Column widths in characters; columns without a width use the spreadsheet default. */
    val columnWidths: List<Double> = emptyList(),
)

class SpreadsheetFormatException(message: String) : Exception(message)

/**
 * Just enough of the Office Open XML (.xlsx) format to write a small workbook and read one back,
 * including files that were opened and re-saved by Excel or Google Sheets.
 */
object Xlsx {
    const val MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    private const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PKG_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"

    /** Excel counts days from 1899-12-30 (a historical quirk that keeps 1900 leap-year compatibility). */
    private val EXCEL_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)

    private const val STYLE_HEADER = 1
    private const val STYLE_DATE = 2

    private const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 128L * 1024 * 1024

    // ---------------------------------------------------------------- writing

    fun write(sheets: List<Sheet>, out: OutputStream) {
        require(sheets.isNotEmpty()) { "A workbook needs at least one sheet" }
        val zip = ZipOutputStream(out)
        fun entry(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        entry("[Content_Types].xml", contentTypes(sheets.size))
        entry("_rels/.rels", rootRels())
        entry("xl/workbook.xml", workbook(sheets))
        entry("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
        entry("xl/styles.xml", styles())
        sheets.forEachIndexed { index, sheet -> entry("xl/worksheets/sheet${index + 1}.xml", worksheet(sheet)) }
        zip.finish()
        zip.flush()
    }

    fun toBytes(sheets: List<Sheet>): ByteArray = ByteArrayOutputStream().also { write(sheets, it) }.toByteArray()

    fun excelSerial(date: LocalDate): Long = ChronoUnit.DAYS.between(EXCEL_EPOCH, date)

    fun fromExcelSerial(serial: Double): LocalDate = EXCEL_EPOCH.plusDays(kotlin.math.floor(serial).toLong())

    private fun contentTypes(sheetCount: Int) = buildString {
        append(XML_HEADER)
        append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        for (i in 1..sheetCount) {
            append("<Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        append("</Types>")
    }

    private fun rootRels() = buildString {
        append(XML_HEADER)
        append("<Relationships xmlns=\"$PKG_REL_NS\">")
        append("<Relationship Id=\"rId1\" Type=\"$REL_NS/officeDocument\" Target=\"xl/workbook.xml\"/>")
        append("</Relationships>")
    }

    private fun workbook(sheets: List<Sheet>) = buildString {
        append(XML_HEADER)
        append("<workbook xmlns=\"$MAIN_NS\" xmlns:r=\"$REL_NS\"><sheets>")
        sheets.forEachIndexed { index, sheet ->
            append("<sheet name=\"${escape(sheet.name)}\" sheetId=\"${index + 1}\" r:id=\"rId${index + 1}\"/>")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(sheetCount: Int) = buildString {
        append(XML_HEADER)
        append("<Relationships xmlns=\"$PKG_REL_NS\">")
        for (i in 1..sheetCount) {
            append("<Relationship Id=\"rId$i\" Type=\"$REL_NS/worksheet\" Target=\"worksheets/sheet$i.xml\"/>")
        }
        append("<Relationship Id=\"rId${sheetCount + 1}\" Type=\"$REL_NS/styles\" Target=\"styles.xml\"/>")
        append("</Relationships>")
    }

    private fun styles() = buildString {
        append(XML_HEADER)
        append("<styleSheet xmlns=\"$MAIN_NS\">")
        append("<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"yyyy-mm-dd\"/></numFmts>")
        append("<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>")
        append("<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>")
        append("<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>")
        append("<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>")
        append("<cellXfs count=\"3\">")
        append("<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>")
        append("<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>")
        append("<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>")
        append("</cellXfs>")
        append("<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>")
        append("</styleSheet>")
    }

    private fun worksheet(sheet: Sheet) = buildString {
        append(XML_HEADER)
        append("<worksheet xmlns=\"$MAIN_NS\">")
        append("<sheetViews><sheetView workbookViewId=\"0\">")
        append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
        append("</sheetView></sheetViews>")
        if (sheet.columnWidths.isNotEmpty()) {
            append("<cols>")
            sheet.columnWidths.forEachIndexed { index, width ->
                append("<col min=\"${index + 1}\" max=\"${index + 1}\" width=\"$width\" customWidth=\"1\"/>")
            }
            append("</cols>")
        }
        append("<sheetData>")
        appendRow(1, sheet.header.map { Cell.Text(it) }, STYLE_HEADER)
        sheet.rows.forEachIndexed { index, row -> appendRow(index + 2, row, null) }
        append("</sheetData></worksheet>")
    }

    private fun StringBuilder.appendRow(rowNumber: Int, cells: List<Cell>, style: Int?) {
        append("<row r=\"$rowNumber\">")
        cells.forEachIndexed { column, cell ->
            val ref = columnName(column) + rowNumber
            val styleAttr = style?.let { " s=\"$it\"" } ?: ""
            when (cell) {
                is Cell.Text -> append("<c r=\"$ref\" t=\"inlineStr\"$styleAttr><is><t xml:space=\"preserve\">${escape(cell.value)}</t></is></c>")
                is Cell.Number -> if (cell.value.isFinite()) {
                    append("<c r=\"$ref\"$styleAttr><v>${plainNumber(cell.value)}</v></c>")
                }
                is Cell.Date -> append("<c r=\"$ref\" s=\"$STYLE_DATE\"><v>${excelSerial(cell.value)}</v></c>")
                Cell.Empty -> Unit
            }
        }
        append("</row>")
    }

    private fun plainNumber(value: Double): String =
        BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().let { if (it == "-0") "0" else it }

    /** 0 -> "A", 25 -> "Z", 26 -> "AA". */
    fun columnName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append('A' + rem)
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    /** "B12" -> 1. */
    fun columnIndex(cellRef: String): Int {
        var result = 0
        for (ch in cellRef) {
            if (!ch.isLetter()) break
            result = result * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return result - 1
    }

    private fun escape(text: String): String = buildString(text.length) {
        for (ch in text) {
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                ch == '\t' || ch == '\n' || ch == '\r' -> append(ch)
                ch < ' ' || ch == '￾' || ch == '￿' -> Unit // not allowed in XML 1.0
                else -> append(ch)
            }
        }
    }

    // ---------------------------------------------------------------- reading

    /**
     * Reads every sheet as rows of cell text, keyed by sheet name in workbook order.
     * Blank rows are kept (as empty lists) so row numbers match what the user sees.
     * Numbers come back as their stored text (e.g. "62.5"; dates as Excel serials like "46295").
     */
    fun read(input: InputStream): Map<String, List<List<String>>> {
        val files = unzip(input)
        val workbook = files["xl/workbook.xml"]
            ?: throw SpreadsheetFormatException("This doesn't look like an .xlsx spreadsheet")
        val relationships = files["xl/_rels/workbook.xml.rels"]?.let { parseRelationships(it) }.orEmpty()
        val sharedStrings = files["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) }.orEmpty()

        val result = LinkedHashMap<String, List<List<String>>>()
        parse(workbook).documentElement.descendants("sheet").forEachIndexed { index, sheet ->
            val name = sheet.getAttribute("name")
            val relId = sheet.attributeByLocalName("id")
            val target = relationships[relId] ?: "worksheets/sheet${index + 1}.xml"
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
            val bytes = files[path] ?: return@forEachIndexed
            result[name] = parseWorksheet(bytes, sharedStrings)
        }
        return result
    }

    private fun unzip(input: InputStream): Map<String, ByteArray> {
        val files = HashMap<String, ByteArray>()
        var total = 0L
        val zip = ZipInputStream(input)
        try {
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.removePrefix("/")
                if (entry.isDirectory || !(name.endsWith(".xml") || name.endsWith(".rels"))) continue
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                var entryBytes = 0L
                while (true) {
                    val read = zip.read(chunk)
                    if (read < 0) break
                    entryBytes += read
                    total += read
                    if (entryBytes > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) {
                        throw SpreadsheetFormatException("This spreadsheet is too large to import")
                    }
                    buffer.write(chunk, 0, read)
                }
                files[name] = buffer.toByteArray()
            }
        } catch (e: java.util.zip.ZipException) {
            throw SpreadsheetFormatException("This doesn't look like an .xlsx spreadsheet")
        }
        if (files.isEmpty()) throw SpreadsheetFormatException("This doesn't look like an .xlsx spreadsheet")
        return files
    }

    private fun parse(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.isExpandEntityReferences = false
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        return try {
            factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            throw SpreadsheetFormatException("The spreadsheet is damaged or in an unsupported format")
        }
    }

    private fun parseRelationships(bytes: ByteArray): Map<String, String> =
        parse(bytes).documentElement.descendants("Relationship")
            .associate { it.getAttribute("Id") to it.getAttribute("Target") }

    private fun parseSharedStrings(bytes: ByteArray): List<String> =
        parse(bytes).documentElement.children("si").map { richText(it) }

    private fun parseWorksheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val sheetData = parse(bytes).documentElement.descendants("sheetData").firstOrNull() ?: return rows
        for (row in sheetData.children("row")) {
            val rowIndex = row.getAttribute("r").toIntOrNull()?.minus(1) ?: rows.size
            while (rows.size < rowIndex) rows.add(emptyList())
            val cells = ArrayList<String>()
            for (cell in row.children("c")) {
                val ref = cell.getAttribute("r")
                val column = if (ref.isNotEmpty()) columnIndex(ref) else cells.size
                val value = when (cell.getAttribute("t")) {
                    "s" -> cell.child("v")?.textContent?.trim()?.toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty()
                    "inlineStr" -> cell.child("is")?.let { richText(it) }.orEmpty()
                    else -> cell.child("v")?.textContent.orEmpty()
                }
                while (cells.size < column) cells.add("")
                if (column < cells.size) cells[column] = value else cells.add(value)
            }
            if (rowIndex < rows.size) rows[rowIndex] = cells else rows.add(cells)
        }
        return rows
    }

    /** Text of an `<si>` or `<is>` element: either a plain `<t>` or a run of `<r><t>` pieces. */
    private fun richText(element: Element): String = buildString {
        for (child in element.children()) {
            when (child.local()) {
                "t" -> append(child.textContent)
                "r" -> child.children("t").forEach { append(it.textContent) }
            }
        }
    }

    private fun Node.local(): String = localName ?: nodeName.substringAfter(':')

    private fun Element.children(): List<Element> {
        val list = ArrayList<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element) list.add(node)
        }
        return list
    }

    private fun Element.children(localName: String): List<Element> = children().filter { it.local() == localName }

    private fun Element.child(localName: String): Element? = children().firstOrNull { it.local() == localName }

    private fun Element.descendants(localName: String): List<Element> {
        val list = ArrayList<Element>()
        fun visit(element: Element) {
            for (child in element.children()) {
                if (child.local() == localName) list.add(child)
                visit(child)
            }
        }
        visit(this)
        return list
    }

    private fun Element.attributeByLocalName(localName: String): String {
        val attributes = attributes
        for (i in 0 until attributes.length) {
            val attribute = attributes.item(i)
            if (attribute.local() == localName) return attribute.nodeValue
        }
        return ""
    }
}
