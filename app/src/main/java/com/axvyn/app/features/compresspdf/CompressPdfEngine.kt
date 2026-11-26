package com.axvyn.app.features.compresspdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

class CompressPdfEngine {

    data class InspectResult(val name: String, val sizeBytes: Long, val pageCount: Int)
    data class ConvertResult(val pageCount: Int, val outputBytes: Long)

    fun inspect(context: Context, uri: Uri): InspectResult {
        val name = queryName(context, uri) ?: "document.pdf"
        val size = querySize(context, uri)
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Cannot open the selected file")
        pfd.use { descriptor ->
            val renderer = try {
                PdfRenderer(descriptor)
            } catch (e: Exception) {
                throw IllegalArgumentException("The selected file is not a valid PDF document.", e)
            }
            try {
                return InspectResult(name = name, sizeBytes = size, pageCount = renderer.pageCount)
            } finally {
                renderer.close()
            }
        }
    }

    fun convert(
        context: Context,
        uri: Uri,
        outputFile: File,
        level: PdfCompressionLevel,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): ConvertResult {
        onProgress(0.01f)
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Cannot open the selected file")
        val outDoc = PdfDocument()
        var resultPageCount = 0
        try {
            pfd.use { descriptor ->
                val renderer = try {
                    PdfRenderer(descriptor)
                } catch (e: Exception) {
                    throw IllegalArgumentException("The selected file is not a valid PDF document.", e)
                }
                try {
                    val pageCount = renderer.pageCount
                    if (pageCount <= 0) error("This PDF has no pages.")
                    resultPageCount = pageCount
                    for (i in 0 until pageCount) {
                        if (isCancelled()) throw Cancellation()
                        val page = renderer.openPage(i)
                        try {
                            val pageWidthPt = page.width
                            val pageHeightPt = page.height
                            val scale = level.dpi / 72f
                            val bmpW = max(1, (pageWidthPt * scale).roundToInt())
                            val bmpH = max(1, (pageHeightPt * scale).roundToInt())

                            val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                val jpegBytes = ByteArrayOutputStream().use { bos ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, level.jpegQuality, bos)
                                    bos.toByteArray()
                                }
                                val compressedBitmap = android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                                    ?: bitmap

                                val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, i + 1).create()
                                val outPage = outDoc.startPage(pageInfo)
                                val canvas: Canvas = outPage.canvas
                                canvas.drawColor(Color.WHITE)
                                val dest = Rect(0, 0, pageWidthPt, pageHeightPt)
                                canvas.drawBitmap(compressedBitmap, null, dest, Paint(Paint.FILTER_BITMAP_FLAG))
                                outDoc.finishPage(outPage)

                                if (compressedBitmap !== bitmap) compressedBitmap.recycle()
                            } finally {
                                bitmap.recycle()
                            }
                        } finally {
                            page.close()
                        }
                        onProgress((i + 1).toFloat() / pageCount.toFloat() * 0.95f)
                    }
                } finally {
                    renderer.close()
                }
            }
            if (isCancelled()) throw Cancellation()
            FileOutputStream(outputFile).use { outDoc.writeTo(it) }
            onProgress(1f)
        } catch (e: Exception) {
            outDoc.close()
            outputFile.delete()
            throw e
        }
        outDoc.close()
        return ConvertResult(pageCount = resultPageCount, outputBytes = outputFile.length())
    }

    private fun queryName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null) ?: return null
        return cursor.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    private fun querySize(context: Context, uri: Uri): Long {
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null) ?: return 0L
        return cursor.use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else 0L }
    }

    class Cancellation : RuntimeException("Conversion cancelled")
}
