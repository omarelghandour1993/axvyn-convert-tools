package com.axvyn.app.features.exceltopdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min

class ExcelToPdfEngine {

    data class InspectResult(val name: String, val sizeBytes: Long, val extension: String)
    data class ConvertResult(val pageCount: Int, val outputBytes: Long, val sheetCount: Int)

    fun inspect(context: Context, uri: Uri): InspectResult {
        val name = queryName(context, uri) ?: "spreadsheet.xlsx"
        val size = querySize(context, uri)
        val ext = name.substringAfterLast('.', "").lowercase()
        return InspectResult(name = name, sizeBytes = size, extension = ext)
    }

    fun convert(
        context: Context,
        uri: Uri,
        outputFile: File,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): ConvertResult {
        onProgress(0.02f)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Cannot open the selected file")
        if (isCancelled()) throw Cancellation()

        val ext = (queryName(context, uri) ?: "").substringAfterLast('.', "").lowercase()
        if (ext == "xls") error("Legacy .xls files are not supported yet. Please save the spreadsheet as .xlsx and try again.")
        onProgress(0.05f)

        val workbook = parseWorkbook(bytes, isCancelled)
        if (workbook.sheets.isEmpty()) error("No sheets were found in this spreadsheet")
        onProgress(0.25f)

        val pageCount = renderPdf(workbook, outputFile, onProgress, isCancelled)
        return ConvertResult(pageCount = pageCount, outputBytes = outputFile.length(), sheetCount = workbook.sheets.size)
    }

    private fun parseWorkbook(bytes: ByteArray, isCancelled: () -> Boolean): Workbook {
        var workbookXml: ByteArray? = null
        var workbookRelsXml: ByteArray? = null
        var sharedStringsXml: ByteArray? = null
        val sheetXmls = mutableMapOf<String, ByteArray>()

        ZipInputStream(BufferedInputStream(bytes.inputStream())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (isCancelled()) throw Cancellation()
                if (!entry.isDirectory) {
                    val name = entry.name.trimStart('/')
                    when {
                        name == "xl/workbook.xml" -> workbookXml = zip.readBytes()
                        name == "xl/_rels/workbook.xml.rels" -> workbookRelsXml = zip.readBytes()
                        name == "xl/sharedStrings.xml" -> sharedStringsXml = zip.readBytes()
                        name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") -> sheetXmls[name] = zip.readBytes()
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val wb = workbookXml ?: error("This file is not a valid .xlsx spreadsheet")
        val rels = workbookRelsXml?.let { parseRelationships(it) } ?: emptyMap()
        val sharedStrings = sharedStringsXml?.let { parseSharedStrings(it) } ?: emptyList()
        val sheetRefs = parseWorkbookSheets(wb)

        val sheets = sheetRefs.mapNotNull { ref ->
            if (isCancelled()) throw Cancellation()
            val target = rels[ref.relId] ?: return@mapNotNull null
            val path = normalizeXlPath(target)
            val xml = sheetXmls[path] ?: sheetXmls.entries.firstOrNull { it.key.endsWith(path.substringAfterLast('/')) }?.value
            xml?.let { parseSheet(ref.name, it, sharedStrings) }
        }
        return Workbook(sheets)
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        val parser = newParser(bytes)
        var inSi = false
        var current = StringBuilder()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> { inSi = true; current = StringBuilder() }
                    "t" -> if (inSi) current.append(parser.nextTextSafe())
                }
                XmlPullParser.END_TAG -> if (parser.name == "si") { strings += current.toString(); inSi = false }
            }
            parser.next()
        }
        return strings
    }

    private fun parseWorkbookSheets(bytes: ByteArray): List<SheetRef> {
        val refs = mutableListOf<SheetRef>()
        val parser = newParser(bytes)
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.attr("name") ?: "Sheet${refs.size + 1}"
                val relId = parser.attrNs(R_NS, "id") ?: parser.attr("id")
                if (!relId.isNullOrBlank()) refs += SheetRef(name, relId)
            }
            parser.next()
        }
        return refs
    }

    private fun parseSheet(name: String, bytes: ByteArray, sharedStrings: List<String>): Sheet {
        val rows = mutableListOf<List<String>>()
        var maxCol = 0
        val parser = newParser(bytes)
        var inRow = false
        var rowIndex = 0
        var currentRow = mutableMapOf<Int, String>()
        var cellRef = ""
        var cellType = ""
        var cellValue = StringBuilder()
        var inInline = false
        var inlineText = StringBuilder()

        fun flushCell() {
            val col = colIndex(cellRef)
            val value = when (cellType) {
                "s" -> sharedStrings.getOrNull(cellValue.toString().trim().toIntOrNull() ?: -1) ?: ""
                "inlineStr" -> inlineText.toString()
                "b" -> if (cellValue.toString().trim() == "1") "TRUE" else "FALSE"
                else -> cellValue.toString()
            }
            if (col >= 0) {
                currentRow[col] = value
                if (col + 1 > maxCol) maxCol = col + 1
            }
            cellValue = StringBuilder(); inlineText = StringBuilder(); cellType = ""; cellRef = ""; inInline = false
        }

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> { inRow = true; currentRow = mutableMapOf(); rowIndex = parser.attr("r")?.toIntOrNull() ?: (rowIndex + 1) }
                    "c" -> if (inRow) { cellRef = parser.attr("r") ?: ""; cellType = parser.attr("t") ?: ""; cellValue = StringBuilder(); inlineText = StringBuilder(); inInline = false }
                    "v" -> if (inRow) cellValue.append(parser.nextTextSafe())
                    "is" -> if (inRow) inInline = true
                    "t" -> if (inRow && inInline) inlineText.append(parser.nextTextSafe())
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "c" -> if (inRow) flushCell()
                    "row" -> if (inRow) {
                        val list = MutableList(max(maxCol, (currentRow.keys.maxOrNull() ?: -1) + 1)) { "" }
                        currentRow.forEach { (k, v) -> if (k in list.indices) list[k] = v }
                        if (list.any { it.isNotBlank() }) rows += list else if (rows.isNotEmpty()) rows += list
                        inRow = false
                    }
                }
            }
            parser.next()
        }

        val normalizedMax = rows.maxOfOrNull { it.size } ?: 0
        val normalized = rows.map { row -> if (row.size < normalizedMax) row + List(normalizedMax - row.size) { "" } else row }
        return Sheet(name, normalized, normalizedMax)
    }

    private fun renderPdf(
        workbook: Workbook,
        outputFile: File,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): Int {
        val doc = PdfDocument()
        val pageWidth = 842 // A4 landscape
        val pageHeight = 595
        val margin = 28f
        val contentWidth = pageWidth - margin * 2
        val contentHeight = pageHeight - margin * 2
        var pageNumber = 0

        val totalSheets = workbook.sheets.size.coerceAtLeast(1)
        workbook.sheets.forEachIndexed { sheetIndex, sheet ->
            if (isCancelled()) { doc.close(); outputFile.delete(); throw Cancellation() }
            pageNumber++
            var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var canvas = page.canvas
            var y = margin

            fun newPage() {
                doc.finishPage(page)
                pageNumber++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = margin
            }

            // Sheet title
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 14f; isFakeBoldText = true }
            canvas.drawText(sheet.name.take(80), margin, y + 14f, titlePaint)
            y += 24f

            val rows = sheet.rows.take(MAX_ROWS)
            val cols = min(sheet.columnCount, MAX_COLS).coerceAtLeast(1)
            val cellPadding = 3f
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 8f }
            val borderPaint = Paint().apply { style = Paint.Style.STROKE; color = Color.LTGRAY; strokeWidth = 0.8f }
            val headerBg = Paint().apply { style = Paint.Style.FILL; color = Color.rgb(242, 242, 242) }

            val colWidths = computeColWidths(rows, cols, textPaint, contentWidth, cellPadding)
            val tableWidth = colWidths.sum()

            rows.forEachIndexed { rowIdx, row ->
                val rowHeight = measureRowHeight(row, cols, colWidths, textPaint, cellPadding)
                if (y + rowHeight > pageHeight - margin) newPage()
                var x = margin
                for (c in 0 until cols) {
                    val cw = colWidths[c]
                    val rect = android.graphics.RectF(x, y, x + cw, y + rowHeight)
                    if (rowIdx == 0) canvas.drawRect(rect, headerBg)
                    canvas.drawRect(rect, borderPaint)
                    val text = row.getOrNull(c).orEmpty().replace('\n', ' ').take(200)
                    if (text.isNotBlank()) {
                        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, (cw - cellPadding * 2).toInt().coerceAtLeast(1))
                            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                            .setMaxLines(2)
                            .setEllipsize(android.text.TextUtils.TruncateAt.END)
                            .setIncludePad(false)
                            .build()
                        canvas.save()
                        canvas.clipRect(rect)
                        canvas.translate(x + cellPadding, y + cellPadding)
                        layout.draw(canvas)
                        canvas.restore()
                    }
                    x += cw
                }
                y += rowHeight
            }

            if (sheet.rows.size > MAX_ROWS) {
                if (y + 16f > pageHeight - margin) newPage()
                canvas.drawText("Rows truncated for display (max $MAX_ROWS rows per sheet).", margin, y + 12f, textPaint)
                y += 16f
            }

            doc.finishPage(page)
            onProgress(0.25f + 0.7f * (sheetIndex + 1) / totalSheets)
        }

        FileOutputStream(outputFile).use { doc.writeTo(it) }
        doc.close()
        onProgress(1f)
        return pageNumber
    }

    private fun computeColWidths(rows: List<List<String>>, cols: Int, paint: TextPaint, contentWidth: Float, padding: Float): List<Float> {
        val maxWidth = 140f
        val minWidth = 36f
        val widths = MutableList(cols) { minWidth }
        val sample = rows.take(30)
        for (c in 0 until cols) {
            var w = minWidth
            sample.forEach { row ->
                val text = row.getOrNull(c).orEmpty().take(40)
                if (text.isNotEmpty()) w = max(w, paint.measureText(text) + padding * 2 + 4f)
            }
            widths[c] = w.coerceIn(minWidth, maxWidth)
        }
        val total = widths.sum()
        if (total > contentWidth) {
            val scale = contentWidth / total
            for (i in widths.indices) widths[i] = (widths[i] * scale).coerceAtLeast(24f)
        }
        return widths
    }

    private fun measureRowHeight(row: List<String>, cols: Int, colWidths: List<Float>, paint: TextPaint, padding: Float): Float {
        var h = 16f
        for (c in 0 until cols) {
            val text = row.getOrNull(c).orEmpty().replace('\n', ' ').take(200)
            if (text.isBlank()) continue
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, (colWidths[c] - padding * 2).toInt().coerceAtLeast(1))
                .setMaxLines(2)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .build()
            h = max(h, layout.height + padding * 2)
        }
        return h.coerceAtLeast(16f)
    }

    private fun colIndex(cellRef: String): Int {
        val letters = cellRef.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return -1
        var col = 0
        for (ch in letters) col = col * 26 + (ch - 'A' + 1)
        return col - 1
    }

    private fun parseRelationships(bytes: ByteArray): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val parser = newParser(bytes)
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = parser.attr("Id")
                val target = parser.attr("Target")
                if (id != null && target != null) map[id] = target
            }
            parser.next()
        }
        return map
    }

    private fun normalizeXlPath(target: String): String {
        val clean = target.trimStart('/')
        return when {
            clean.startsWith("xl/") -> clean
            clean.startsWith("worksheets/") -> "xl/$clean"
            clean.startsWith("../") -> "xl/${clean.removePrefix("../")}"
            else -> "xl/$clean"
        }
    }

    private fun newParser(bytes: ByteArray): XmlPullParser {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT) event = parser.next()
        return parser
    }

    private fun XmlPullParser.attr(name: String): String? = getAttributeValue(null, name)
        ?: (0 until attributeCount).firstOrNull { getAttributeName(it) == name }?.let { getAttributeValue(it) }

    private fun XmlPullParser.attrNs(ns: String, name: String): String? = getAttributeValue(ns, name)
        ?: (0 until attributeCount).firstOrNull { getAttributeNamespace(it) == ns && getAttributeName(it) == name }?.let { getAttributeValue(it) }

    private fun XmlPullParser.nextTextSafe(): String = if (eventType == XmlPullParser.START_TAG) nextText() else ""

    private fun queryName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null) ?: return null
        return cursor.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    private fun querySize(context: Context, uri: Uri): Long {
        val cursor = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null) ?: return 0L
        return cursor.use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else 0L }
    }

    private data class Workbook(val sheets: List<Sheet>)
    private data class SheetRef(val name: String, val relId: String)
    private data class Sheet(val name: String, val rows: List<List<String>>, val columnCount: Int)

    class Cancellation : RuntimeException("Conversion cancelled")

    companion object {
        private const val R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        private const val MAX_ROWS = 500
        private const val MAX_COLS = 30
    }
}