package com.axvyn.app.features.powerpointtopdf

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min

class PowerPointToPdfEngine {

    data class InspectResult(val name: String, val sizeBytes: Long, val extension: String)

    data class ConvertResult(val pageCount: Int, val outputBytes: Long)

    fun inspect(context: Context, uri: Uri): InspectResult {
        val name = queryName(context, uri) ?: "presentation.pptx"
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
        if (ext == "ppt") {
            error("Legacy .ppt files are not supported yet. Please save the presentation as .pptx and try again.")
        }
        onProgress(0.06f)

        val pptx = parsePptx(bytes, isCancelled)
        if (pptx.slides.isEmpty()) error("No slides were found in this presentation")
        onProgress(0.25f)

        val pageCount = renderPdf(pptx, outputFile, onProgress, isCancelled)
        return ConvertResult(pageCount = pageCount, outputBytes = outputFile.length())
    }

    private fun parsePptx(bytes: ByteArray, isCancelled: () -> Boolean): PptxContent {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(BufferedInputStream(bytes.inputStream())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (isCancelled()) throw Cancellation()
                if (!entry.isDirectory) {
                    val name = entry.name.trimStart('/')
                    if (name == "ppt/presentation.xml" ||
                        name == "ppt/_rels/presentation.xml.rels" ||
                        name.startsWith("ppt/slides/slide") ||
                        name.startsWith("ppt/slides/_rels/slide") ||
                        name.startsWith("ppt/media/")
                    ) {
                        entries[name] = zip.readBytes()
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val presentation = entries["ppt/presentation.xml"] ?: error("This file is not a valid .pptx presentation")
        val presRels = entries["ppt/_rels/presentation.xml.rels"]?.let { parseRelationships(it) } ?: emptyMap()
        val (slideWidthEmu, slideHeightEmu, slideRelIds) = parsePresentation(presentation)
        val slidePaths = slideRelIds.mapNotNull { relId ->
            val target = presRels[relId] ?: return@mapNotNull null
            normalizePptPath(target)
        }

        val slides = slidePaths.mapNotNull { slidePath ->
            if (isCancelled()) throw Cancellation()
            val slideBytes = entries[slidePath] ?: return@mapNotNull null
            val relPath = "ppt/slides/_rels/${slidePath.substringAfterLast('/')}.rels"
            val slideRels = entries[relPath]?.let { parseRelationships(it) } ?: emptyMap()
            parseSlide(slideBytes, slideRels)
        }

        val media = entries.filterKeys { it.startsWith("ppt/media/") }
            .mapKeys { it.key.removePrefix("ppt/") }
        return PptxContent(
            slideWidthEmu = slideWidthEmu,
            slideHeightEmu = slideHeightEmu,
            slides = slides,
            media = media,
        )
    }

    private fun parsePresentation(bytes: ByteArray): Triple<Long, Long, List<String>> {
        val parser = newParser(bytes)
        var width = 9144000L // 10 inches
        var height = 6858000L // 7.5 inches
        val relIds = mutableListOf<String>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "sldSz" -> {
                        width = parser.attr("cx")?.toLongOrNull() ?: width
                        height = parser.attr("cy")?.toLongOrNull() ?: height
                    }
                    "sldId" -> {
                        val relId = parser.attrNs(R_NS, "id") ?: parser.attr("id")
                        if (!relId.isNullOrBlank()) relIds += relId
                    }
                }
            }
            parser.next()
        }
        return Triple(width, height, relIds)
    }

    private fun parseSlide(bytes: ByteArray, rels: Map<String, String>): Slide {
        val parser = newParser(bytes)
        val texts = mutableListOf<TextBlock>()
        val images = mutableListOf<ImageBlock>()
        var inSp = false
        var inPic = false
        var currentText = StringBuilder()
        var currentFontSizeHundredPt: Int? = null
        var currentBold = false
        var currentX: Long? = null
        var currentY: Long? = null
        var currentCx: Long? = null
        var currentCy: Long? = null
        var currentRelId: String? = null

        fun flushText() {
            val t = currentText.toString().trim()
            if (t.isNotEmpty()) {
                texts += TextBlock(
                    text = t,
                    fontSizePt = ((currentFontSizeHundredPt ?: 1800) / 100f),
                    bold = currentBold,
                    xEmu = currentX, yEmu = currentY, cxEmu = currentCx, cyEmu = currentCy,
                )
            }
            currentText = StringBuilder()
            currentFontSizeHundredPt = null
            currentBold = false
        }

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "sp" -> { inSp = true; currentX = null; currentY = null; currentCx = null; currentCy = null }
                    "pic" -> { inPic = true; currentRelId = null; currentX = null; currentY = null; currentCx = null; currentCy = null }
                    "off" -> {
                        val x = parser.attr("x")?.toLongOrNull()
                        val y = parser.attr("y")?.toLongOrNull()
                        if (x != null) { if (inPic) currentX = x else if (inSp && currentX == null) currentX = x }
                        if (y != null) { if (inPic) currentY = y else if (inSp && currentY == null) currentY = y }
                    }
                    "ext" -> {
                        val cx = parser.attr("cx")?.toLongOrNull()
                        val cy = parser.attr("cy")?.toLongOrNull()
                        if (cx != null) { if (inPic) currentCx = cx else if (inSp && currentCx == null) currentCx = cx }
                        if (cy != null) { if (inPic) currentCy = cy else if (inSp && currentCy == null) currentCy = cy }
                    }
                    "blip" -> if (inPic) currentRelId = parser.attrNs(R_NS, "embed") ?: currentRelId
                    "p" -> if (inSp) { if (currentText.isNotEmpty()) currentText.append('\n') }
                    "br" -> if (inSp) currentText.append('\n')
                    "rPr" -> if (inSp) {
                        currentFontSizeHundredPt = parser.attr("sz")?.toIntOrNull() ?: currentFontSizeHundredPt
                        currentBold = parser.attr("b") == "1"
                    }
                    "t" -> if (inSp) currentText.append(parser.nextTextSafe())
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "sp" -> { flushText(); inSp = false }
                    "pic" -> {
                        val rel = currentRelId
                        val target = rel?.let { rels[it] }
                        if (target != null) {
                            images += ImageBlock(
                                target = normalizePptPath(target).removePrefix("ppt/"),
                                xEmu = currentX, yEmu = currentY, cxEmu = currentCx, cyEmu = currentCy,
                            )
                        }
                        inPic = false
                    }
                }
            }
            parser.next()
        }
        return Slide(texts = texts, images = images)
    }

    private fun renderPdf(
        content: PptxContent,
        outputFile: File,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): Int {
        val doc = PdfDocument()
        val pageWidthPt = 842 // landscape-ish default
        val pageHeightPt = 595
        val slideW = content.slideWidthEmu.coerceAtLeast(1L)
        val slideH = content.slideHeightEmu.coerceAtLeast(1L)
        val scale = min(pageWidthPt.toFloat() / emuToPt(slideW), pageHeightPt.toFloat() / emuToPt(slideH))
        val drawW = emuToPt(slideW) * scale
        val drawH = emuToPt(slideH) * scale
        val offsetX = (pageWidthPt - drawW) / 2f
        val offsetY = (pageHeightPt - drawH) / 2f

        content.slides.forEachIndexed { index, slide ->
            if (isCancelled()) { doc.close(); outputFile.delete(); throw Cancellation() }
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, index + 1).create())
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            val bgPaint = Paint().apply { color = Color.WHITE }
            canvas.drawRect(offsetX, offsetY, offsetX + drawW, offsetY + drawH, bgPaint)

            slide.images.forEach { img ->
                val bytes = content.media[img.target] ?: content.media["media/${img.target.removePrefix("media/")}"]
                val bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                if (bmp != null) {
                    val rect = slideRect(img.xEmu, img.yEmu, img.cxEmu, img.cyEmu, slideW, slideH, offsetX, offsetY, drawW, drawH)
                    canvas.drawBitmap(bmp, null, rect, null)
                    bmp.recycle()
                }
            }

            slide.texts.forEach { block ->
                val rect = slideRect(block.xEmu, block.yEmu, block.cxEmu, block.cyEmu, slideW, slideH, offsetX, offsetY, drawW, drawH)
                drawTextBlock(canvas, block, rect)
            }

            doc.finishPage(page)
            onProgress(0.25f + 0.7f * (index + 1) / content.slides.size.coerceAtLeast(1))
        }

        FileOutputStream(outputFile).use { doc.writeTo(it) }
        doc.close()
        onProgress(1f)
        return content.slides.size
    }

    private fun drawTextBlock(canvas: Canvas, block: TextBlock, rect: RectF) {
        val padding = 4f
        val width = (rect.width() - padding * 2).toInt().coerceAtLeast(1)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = max(10f, block.fontSizePt * 0.9f)
            isFakeBoldText = block.bold
        }
        val layout = StaticLayout.Builder.obtain(block.text, 0, block.text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.1f)
            .build()
        canvas.save()
        canvas.clipRect(rect)
        canvas.translate(rect.left + padding, rect.top + padding)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun slideRect(
        xEmu: Long?, yEmu: Long?, cxEmu: Long?, cyEmu: Long?,
        slideW: Long, slideH: Long,
        offsetX: Float, offsetY: Float, drawW: Float, drawH: Float,
    ): RectF {
        val x = if (xEmu != null) offsetX + emuToPt(xEmu) / emuToPt(slideW) * drawW else offsetX + 24f
        val y = if (yEmu != null) offsetY + emuToPt(yEmu) / emuToPt(slideH) * drawH else offsetY + 24f
        val w = if (cxEmu != null) emuToPt(cxEmu) / emuToPt(slideW) * drawW else drawW - 48f
        val h = if (cyEmu != null) emuToPt(cyEmu) / emuToPt(slideH) * drawH else 60f
        return RectF(x, y, x + w.coerceAtLeast(20f), y + h.coerceAtLeast(20f))
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

    private fun normalizePptPath(target: String): String {
        val clean = target.trimStart('/')
        return when {
            clean.startsWith("ppt/") -> clean
            clean.startsWith("slides/") -> "ppt/$clean"
            clean.startsWith("media/") -> "ppt/$clean"
            clean.startsWith("../") -> "ppt/${clean.removePrefix("../")}"
            else -> "ppt/$clean"
        }
    }

    private fun emuToPt(emu: Long): Float = emu / 12700f

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

    private data class PptxContent(
        val slideWidthEmu: Long,
        val slideHeightEmu: Long,
        val slides: List<Slide>,
        val media: Map<String, ByteArray>,
    )

    private data class Slide(val texts: List<TextBlock>, val images: List<ImageBlock>)
    private data class TextBlock(
        val text: String, val fontSizePt: Float, val bold: Boolean,
        val xEmu: Long?, val yEmu: Long?, val cxEmu: Long?, val cyEmu: Long?,
    )
    private data class ImageBlock(
        val target: String,
        val xEmu: Long?, val yEmu: Long?, val cxEmu: Long?, val cyEmu: Long?,
    )

    class Cancellation : RuntimeException("Conversion cancelled")

    companion object {
        private const val R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    }
}