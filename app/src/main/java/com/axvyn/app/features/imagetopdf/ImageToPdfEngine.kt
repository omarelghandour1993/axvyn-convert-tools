package com.axvyn.app.features.imagetopdf

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ImageToPdfEngine {

    data class InspectResult(
        val name: String,
        val sizeBytes: Long,
        val mimeType: String?,
        val width: Int,
        val height: Int,
    )

    data class ConvertResult(val pageCount: Int, val outputBytes: Long)

    fun inspect(context: Context, uri: Uri): InspectResult {
        val name = queryName(context, uri) ?: "image"
        val size = querySize(context, uri)
        val mime = context.contentResolver.getType(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = context.contentResolver.openInputStream(uri) ?: error("Cannot open the selected file")
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) error("The selected file is not a supported image")
        return InspectResult(name = name, sizeBytes = size, mimeType = mime, width = w, height = h)
    }

    fun convert(
        context: Context,
        uris: List<Uri>,
        outputFile: File,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): ConvertResult {
        if (uris.isEmpty()) error("Choose at least one image")
        onProgress(0.01f)
        val doc = PdfDocument()
        try {
            val total = uris.size
            uris.forEachIndexed { index, uri ->
                if (isCancelled()) throw Cancellation()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open the selected image")
                input.use { BitmapFactory.decodeStream(it, null, bounds) }
                val srcW = bounds.outWidth
                val srcH = bounds.outHeight
                if (srcW <= 0 || srcH <= 0) error("One of the selected files is not a supported image")

                val (pageW, pageH) = pageSize(srcW, srcH)
                val margin = 24f
                val maxW = pageW - margin * 2
                val maxH = pageH - margin * 2
                val scale = min(maxW / srcW.toFloat(), maxH / srcH.toFloat()).coerceAtMost(1f)
                val drawW = srcW * scale
                val drawH = srcH * scale
                val left = (pageW - drawW) / 2f
                val top = (pageH - drawH) / 2f

                val sample = computeInSampleSize(srcW, srcH, drawW.roundToInt().coerceAtLeast(1), drawH.roundToInt().coerceAtLeast(1))
                val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }
                val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    ?: error("Could not decode an image")

                val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create())
                val canvas: Canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(bitmap, null, RectF(left, top, left + drawW, top + drawH), Paint(Paint.FILTER_BITMAP_FLAG))
                doc.finishPage(page)
                bitmap.recycle()
                onProgress((index + 1).toFloat() / total.toFloat() * 0.95f)
            }

            if (isCancelled()) throw Cancellation()
            FileOutputStream(outputFile).use { doc.writeTo(it) }
            onProgress(1f)
        } catch (e: Exception) {
            doc.close()
            outputFile.delete()
            throw e
        }
        doc.close()
        return ConvertResult(pageCount = uris.size, outputBytes = outputFile.length())
    }

    private fun pageSize(srcW: Int, srcH: Int): Pair<Int, Int> {
        // A4 in points (72dpi): 595 x 842
        return if (srcW > srcH) 842 to 595 else 595 to 842
    }

    private fun computeInSampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
        var sample = 1
        var w = srcW
        var h = srcH
        while (w / 2 >= reqW && h / 2 >= reqH) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return max(1, sample)
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
