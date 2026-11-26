package com.axvyn.app.features.wordtopdf

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min

class WordToPdfEngine {

    data class InspectResult(val name: String, val sizeBytes: Long)

    data class ConvertResult(val pageCount: Int, val outputBytes: Long)

    fun inspect(context: Context, uri: Uri): InspectResult {
        val name = queryName(context, uri) ?: "document.docx"
        val size = querySize(context, uri)
        return InspectResult(name = name, sizeBytes = size)
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
        onProgress(0.08f)

        val docx = parseDocx(bytes, isCancelled)
        onProgress(0.35f)

        val pageCount = renderPdf(docx, outputFile, onProgress, isCancelled)
        return ConvertResult(pageCount = pageCount, outputBytes = outputFile.length())
    }

    private fun parseDocx(bytes: ByteArray, isCancelled: () -> Boolean): DocxContent {
        var documentXml: ByteArray? = null
        var stylesXml: ByteArray? = null
        var relsXml: ByteArray? = null
        val media = mutableMapOf<String, ByteArray>()

        ZipInputStream(BufferedInputStream(bytes.inputStream())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (isCancelled()) throw Cancellation()
                if (!entry.isDirectory) {
                    val name = entry.name.trimStart('/')
                    when {
                        name == "word/document.xml" -> documentXml = zip.readBytes()
                        name == "word/styles.xml" -> stylesXml = zip.readBytes()
                        name == "word/_rels/document.xml.rels" -> relsXml = zip.readBytes()
                        name.startsWith("word/media/") -> media[name.removePrefix("word/")] = zip.readBytes()
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val docBytes = documentXml ?: error("This file is not a valid .docx document")
        val rels = relsXml?.let { parseRelationships(it) } ?: emptyMap()
        val styles = stylesXml?.let { parseStyles(it) } ?: emptyMap()
        val blocks = parseDocument(docBytes, styles)
        return DocxContent(blocks = blocks, relationships = rels, media = media)
    }

    private fun parseRelationships(bytes: ByteArray): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val parser = newParser(bytes)
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = parser.attr("Id")
                val target = parser.attr("Target")
                if (id != null && target != null) {
                    val normalized = if (target.startsWith("/")) target.trimStart('/') else "word/$target"
                    val mediaTarget = when {
                        normalized.startsWith("word/media/") -> normalized.removePrefix("word/")
                        target.startsWith("media/") -> target
                        else -> target
                    }
                    map[id] = mediaTarget
                }
            }
            parser.next()
        }
        return map
    }

    private fun parseStyles(bytes: ByteArray): Map<String, StyleDef> {
        val styles = mutableMapOf<String, StyleDef>()
        val parser = newParser(bytes)
        var currentId: String? = null
        var currentType: String? = null
        var isHeading = false
        var headingLevel = 0
        var bold = false
        var italic = false
        var fontSizeHalfPoints: Int? = null
        var basedOn: String? = null

        fun flush() {
            val id = currentId ?: return
            if (currentType == "paragraph") {
                styles[id] = StyleDef(
                    isHeading = isHeading,
                    headingLevel = headingLevel,
                    bold = bold,
                    italic = italic,
                    fontSizeHalfPoints = fontSizeHalfPoints,
                    basedOn = basedOn,
                )
            }
        }

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "style" -> {
                        currentId = parser.attr("styleId")
                        currentType = parser.attr("type")
                        isHeading = false; headingLevel = 0; bold = false; italic = false
                        fontSizeHalfPoints = null; basedOn = null
                    }
                    "basedOn" -> if (currentId != null) basedOn = parser.attr("val")
                    "pPr" -> Unit
                    "rPr" -> Unit
                    "b" -> if (currentId != null) bold = parser.attr("val") != "0" && parser.attr("val") != "false"
                    "i" -> if (currentId != null) italic = parser.attr("val") != "0" && parser.attr("val") != "false"
                    "sz" -> if (currentId != null) fontSizeHalfPoints = parser.attr("val")?.toIntOrNull()
                    "name" -> if (currentId != null) {
                        val name = (parser.attr("val") ?: "").lowercase()
                        if (name.startsWith("heading")) {
                            isHeading = true
                            headingLevel = name.filter { it.isDigit() }.toIntOrNull() ?: 1
                        }
                    }
                    "outlineLvl" -> if (currentId != null) {
                        val lvl = parser.attr("val")?.toIntOrNull()
                        if (lvl != null) { isHeading = true; headingLevel = lvl + 1 }
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "style") { flush(); currentId = null }
            }
            parser.next()
        }
        return styles
    }

    private fun parseDocument(bytes: ByteArray, styles: Map<String, StyleDef>): List<Block> {
        val blocks = mutableListOf<Block>()
        val parser = newParser(bytes)
        var inBody = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "body" -> inBody = true
                    "p" -> if (inBody) blocks += parseParagraph(parser, styles)
                    "tbl" -> if (inBody) blocks += parseTable(parser, styles)
                }
            } else if (parser.eventType == XmlPullParser.END_TAG && parser.name == "body") {
                inBody = false
            }
            parser.next()
        }
        return blocks
    }

    private fun parseParagraph(parser: XmlPullParser, styles: Map<String, StyleDef>): ParagraphBlock {
        val runs = mutableListOf<Run>()
        var align: Layout.Alignment? = null
        var rtl = false
        var styleId: String? = null
        var numPr = false

        loop@ while (true) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "pStyle" -> styleId = parser.attr("val")
                    "jc" -> align = when (parser.attr("val")) {
                        "center" -> Layout.Alignment.ALIGN_CENTER
                        "right", "end" -> Layout.Alignment.ALIGN_OPPOSITE
                        "both" -> Layout.Alignment.ALIGN_NORMAL
                        else -> Layout.Alignment.ALIGN_NORMAL
                    }
                    "bidi" -> rtl = parser.attr("val") != "0" && parser.attr("val") != "false"
                    "numPr" -> numPr = true
                    "r" -> runs += parseRun(parser)
                    "hyperlink" -> runs += parseHyperlinkRuns(parser)
                }
                XmlPullParser.END_TAG -> if (parser.name == "p") break@loop
            }
            parser.next()
        }

        val resolvedStyle = resolveStyle(styleId, styles)
        val isHeading = resolvedStyle?.isHeading == true
        val headingLevel = resolvedStyle?.headingLevel ?: 0
        return ParagraphBlock(runs = runs, align = align, rtl = rtl, isHeading = isHeading, headingLevel = headingLevel, isListItem = numPr)
    }

    private fun parseHyperlinkRuns(parser: XmlPullParser): List<Run> {
        val runs = mutableListOf<Run>()
        loop@ while (true) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> if (parser.name == "r") runs += parseRun(parser)
                XmlPullParser.END_TAG -> if (parser.name == "hyperlink") break@loop
            }
            parser.next()
        }
        return runs
    }

    private fun parseRun(parser: XmlPullParser): Run {
        var bold = false
        var italic = false
        var fontSizeHalfPoints: Int? = null
        val text = StringBuilder()
        var imageRelId: String? = null

        loop@ while (true) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "b" -> bold = parser.attr("val") != "0" && parser.attr("val") != "false"
                    "i" -> italic = parser.attr("val") != "0" && parser.attr("val") != "false"
                    "sz" -> fontSizeHalfPoints = parser.attr("val")?.toIntOrNull()
                    "t" -> text.append(parser.nextTextSafe())
                    "tab" -> text.append('\t')
                    "br", "cr" -> text.append('\n')
                    "blip" -> imageRelId = parser.attrNs("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed") ?: imageRelId
                }
                XmlPullParser.END_TAG -> if (parser.name == "r") break@loop
            }
            parser.next()
        }
        return Run(text = text.toString(), bold = bold, italic = italic, fontSizeHalfPoints = fontSizeHalfPoints, imageRelId = imageRelId)
    }

    private fun parseTable(parser: XmlPullParser, styles: Map<String, StyleDef>): TableBlock {
        val rows = mutableListOf<List<List<ParagraphBlock>>>()
        var currentRow = mutableListOf<List<ParagraphBlock>>()
        var currentCell = mutableListOf<ParagraphBlock>()

        loop@ while (true) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "tr" -> currentRow = mutableListOf()
                    "tc" -> currentCell = mutableListOf()
                    "p" -> currentCell += parseParagraph(parser, styles)
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "tc" -> currentRow += currentCell.toList()
                    "tr" -> if (currentRow.isNotEmpty()) rows += currentRow.toList()
                    "tbl" -> break@loop
                }
            }
            parser.next()
        }
        return TableBlock(rows = rows)
    }

    private fun renderPdf(
        content: DocxContent,
        outputFile: File,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): Int {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val contentWidth = pageWidth - margin * 2
        val doc = PdfDocument()
        val ctx = PdfCtx(
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()),
            pageNumber = 1,
            y = margin,
        )

        fun newPage() {
            doc.finishPage(ctx.page)
            ctx.pageNumber += 1
            ctx.page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, ctx.pageNumber).create())
            ctx.y = margin
        }

        fun ensureSpace(heightNeeded: Float) {
            if (ctx.y + heightNeeded > pageHeight - margin) newPage()
        }

        val total = content.blocks.size.coerceAtLeast(1)
        content.blocks.forEachIndexed { index, block ->
            if (isCancelled()) {
                doc.close()
                outputFile.delete()
                throw Cancellation()
            }
            when (block) {
                is ParagraphBlock -> drawParagraph(block, content, ctx, margin, contentWidth, pageHeight.toFloat(), ::newPage, ::ensureSpace)
                is TableBlock -> drawTable(block, content, ctx, margin, contentWidth, pageHeight.toFloat(), ::newPage, ::ensureSpace)
            }
            onProgress(0.35f + 0.6f * (index + 1) / total)
        }

        doc.finishPage(ctx.page)
        FileOutputStream(outputFile).use { doc.writeTo(it) }
        doc.close()
        onProgress(1f)
        return ctx.pageNumber
    }

    private fun drawParagraph(
        block: ParagraphBlock,
        content: DocxContent,
        ctx: PdfCtx,
        margin: Float,
        contentWidth: Float,
        pageHeight: Float,
        newPage: () -> Unit,
        ensureSpace: (Float) -> Unit,
    ) {
        val imageRuns = block.runs.filter { it.imageRelId != null }
        for (run in imageRuns) {
            val target = content.relationships[run.imageRelId] ?: continue
            val imgBytes = content.media[target] ?: content.media[target.removePrefix("word/")] ?: continue
            val bmp = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size) ?: continue
            val scale = min(contentWidth / bmp.width.toFloat(), 1f)
            val w = bmp.width * scale
            val h = bmp.height * scale
            val needed = h + 8f
            if (ctx.y + needed > pageHeight - margin) newPage()
            val left = margin + (contentWidth - w) / 2f
            ctx.canvas.drawBitmap(bmp, null, android.graphics.RectF(left, ctx.y, left + w, ctx.y + h), null)
            ctx.y += needed
            bmp.recycle()
        }

        val text = buildSpannable(block)
        if (text.isBlank() && imageRuns.isEmpty()) {
            ensureSpace(14f)
            ctx.y += 14f
            return
        }
        if (text.isBlank()) return

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = if (block.isHeading) headingTextSize(block.headingLevel) else 12f
        }
        val align = block.align ?: if (block.rtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
        val direction = if (block.rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth.toInt().coerceAtLeast(1))
            .setAlignment(align)
            .setTextDirection(direction)
            .setIncludePad(false)
            .setLineSpacing(2f, 1f)
            .build()

        var line = 0
        while (line < layout.lineCount) {
            val lineTop = layout.getLineTop(line).toFloat()
            val lineBottom = layout.getLineBottom(line).toFloat()
            val lineHeight = lineBottom - lineTop
            if (ctx.y + lineHeight > pageHeight - margin && ctx.y > margin) {
                newPage()
            }
            ctx.canvas.save()
            ctx.canvas.clipRect(margin, ctx.y, margin + contentWidth, ctx.y + lineHeight)
            ctx.canvas.translate(margin, ctx.y - lineTop)
            layout.draw(ctx.canvas)
            ctx.canvas.restore()
            ctx.y += lineHeight
            line++
        }
        val spacing = if (block.isHeading) 10f else 6f
        ctx.y += spacing
    }

    private fun drawTable(
        block: TableBlock,
        content: DocxContent,
        ctx: PdfCtx,
        margin: Float,
        contentWidth: Float,
        pageHeight: Float,
        newPage: () -> Unit,
        ensureSpace: (Float) -> Unit,
    ) {
        if (block.rows.isEmpty()) return
        val cols = block.rows.maxOf { it.size }.coerceAtLeast(1)
        val colWidth = contentWidth / cols
        val cellPadding = 4f
        val borderPaint = Paint().apply { style = Paint.Style.STROKE; color = Color.LTGRAY; strokeWidth = 1f }
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 11f }

        for (row in block.rows) {
            val cellHeights = row.map { cell ->
                val text = cell.joinToString("\n") { p -> buildSpannable(p).toString() }.trim()
                val layout = StaticLayout.Builder.obtain(text.ifEmpty { " " }, 0, text.ifEmpty { " " }.length, textPaint, (colWidth - cellPadding * 2).toInt().coerceAtLeast(1))
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false)
                    .setLineSpacing(1f, 1f)
                    .build()
                layout.height + cellPadding * 2
            }
            val rowHeight = max(cellHeights.maxOrNull() ?: 20f, 20f)
            if (ctx.y + rowHeight > pageHeight - margin && ctx.y > margin) newPage()
            for (c in 0 until cols) {
                val left = margin + c * colWidth
                val rect = android.graphics.RectF(left, ctx.y, left + colWidth, ctx.y + rowHeight)
                ctx.canvas.drawRect(rect, borderPaint)
                val cell = row.getOrNull(c) ?: emptyList()
                val text = cell.joinToString("\n") { p -> buildSpannable(p).toString() }.trim()
                if (text.isNotEmpty()) {
                    val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, (colWidth - cellPadding * 2).toInt().coerceAtLeast(1))
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setIncludePad(false)
                        .setLineSpacing(1f, 1f)
                        .build()
                    ctx.canvas.save()
                    ctx.canvas.translate(left + cellPadding, ctx.y + cellPadding)
                    layout.draw(ctx.canvas)
                    ctx.canvas.restore()
                }
            }
            ctx.y += rowHeight
        }
        ctx.y += 8f
    }

    private fun buildSpannable(block: ParagraphBlock): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        val prefix = if (block.isListItem) "• " else ""
        if (prefix.isNotEmpty()) sb.append(prefix)
        block.runs.forEach { run ->
            if (run.text.isEmpty()) return@forEach
            val start = sb.length
            sb.append(run.text)
            val end = sb.length
            if (start == end) return@forEach
            val style = when {
                run.bold && run.italic -> Typeface.BOLD_ITALIC
                run.bold -> Typeface.BOLD
                run.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            if (style != Typeface.NORMAL) {
                sb.setSpan(StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val sizePt = run.fontSizeHalfPoints?.let { it / 2f }
            val effectivePt = when {
                sizePt != null -> sizePt
                block.isHeading -> headingTextSize(block.headingLevel) * 0.75f
                else -> null
            }
            if (effectivePt != null) {
                sb.setSpan(AbsoluteSizeSpan(effectivePt.toInt().coerceAtLeast(8), true), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        if (block.isHeading && sb.isNotEmpty()) {
            // Ensure headings are bold
            sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }

    private fun headingTextSize(level: Int): Float = when (level) {
        1 -> 24f
        2 -> 20f
        3 -> 17f
        4 -> 15f
        else -> 14f
    }

    private fun resolveStyle(styleId: String?, styles: Map<String, StyleDef>): StyleDef? {
        var current = styleId ?: return null
        val seen = mutableSetOf<String>()
        while (current !in seen) {
            seen += current
            val def = styles[current] ?: return null
            if (def.isHeading || def.bold || def.italic || def.fontSizeHalfPoints != null) return def
            current = def.basedOn ?: return def
        }
        return styles[styleId]
    }

    private fun newParser(bytes: ByteArray): XmlPullParser {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT) {
            event = parser.next()
        }
        return parser
    }

    private fun XmlPullParser.attr(name: String): String? = getAttributeValue(null, name)
        ?: (0 until attributeCount).firstOrNull { getAttributeName(it) == name }?.let { getAttributeValue(it) }

    private fun XmlPullParser.attrNs(ns: String, name: String): String? = getAttributeValue(ns, name)
        ?: (0 until attributeCount).firstOrNull { getAttributeNamespace(it) == ns && getAttributeName(it) == name }?.let { getAttributeValue(it) }

    private fun XmlPullParser.nextTextSafe(): String {
        return if (eventType == XmlPullParser.START_TAG) nextText() else ""
    }

    private fun queryName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null) ?: return null
        return cursor.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    private fun querySize(context: Context, uri: Uri): Long {
        val cursor = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null) ?: return 0L
        return cursor.use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else 0L }
    }

    private class PdfCtx(
        var page: PdfDocument.Page,
        var pageNumber: Int,
        var y: Float,
    ) {
        val canvas: Canvas get() = page.canvas
    }

    private data class DocxContent(
        val blocks: List<Block>,
        val relationships: Map<String, String>,
        val media: Map<String, ByteArray>,
    )

    private sealed interface Block
    private data class ParagraphBlock(
        val runs: List<Run>,
        val align: Layout.Alignment?,
        val rtl: Boolean,
        val isHeading: Boolean,
        val headingLevel: Int,
        val isListItem: Boolean,
    ) : Block

    private data class TableBlock(val rows: List<List<List<ParagraphBlock>>>) : Block

    private data class Run(
        val text: String,
        val bold: Boolean,
        val italic: Boolean,
        val fontSizeHalfPoints: Int?,
        val imageRelId: String?,
    )

    private data class StyleDef(
        val isHeading: Boolean,
        val headingLevel: Int,
        val bold: Boolean,
        val italic: Boolean,
        val fontSizeHalfPoints: Int?,
        val basedOn: String?,
    )

    class Cancellation : RuntimeException("Conversion cancelled")
}