package com.axvyn.app.features.pdftoimage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.axvyn.app.core.files.FilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class RenderSettings(
    val format: RasterFormat,
    val dpi: Int,
    val pageNumber: Int,
)

data class RenderOutcome(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val pageNumber: Int,
    val pageCount: Int,
)

/** Renders one PDF page at a time using the platform PdfRenderer. No network, no extra page bitmaps. */
class PdfRenderingEngine {

    suspend fun inspect(context: Context, uri: Uri): SourcePdfInfo = withContext(Dispatchers.IO) {
        withRenderer(context, uri) { renderer ->
            val metadata = FilePicker.metadata(context, uri)
            SourcePdfInfo(
                displayName = metadata?.displayName ?: "document.pdf",
                sizeBytes = metadata?.sizeBytes ?: 0L,
                pageCount = renderer.pageCount,
            )
        }
    }

    suspend fun render(
        context: Context,
        uri: Uri,
        settings: RenderSettings,
        onProgress: (Float) -> Unit,
    ): RenderOutcome = withContext(Dispatchers.IO) {
        withRenderer(context, uri) { renderer ->
            currentCoroutineContext().ensureActive()
            val pageCount = renderer.pageCount
            val pageNumber = settings.pageNumber
            require(pageNumber in 1..pageCount) { "Choose a page between 1 and $pageCount." }
            onProgress(0.05f)

            renderer.openPage(pageNumber - 1).usePage { page ->
                val scale = settings.dpi.coerceIn(72, 300) / 72f
                val width = (page.width * scale).toInt().coerceAtLeast(1)
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    onProgress(0.6f)
                    currentCoroutineContext().ensureActive()

                    val bytes = encode(bitmap, settings.format)
                    onProgress(1f)
                    RenderOutcome(
                        bytes = bytes,
                        width = width,
                        height = height,
                        pageNumber = pageNumber,
                        pageCount = pageCount,
                    )
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    private suspend fun <T> withRenderer(context: Context, uri: Uri, block: suspend (PdfRenderer) -> T): T {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Could not open the selected PDF")
        val renderer = try {
            PdfRenderer(descriptor)
        } catch (error: Throwable) {
            descriptor.close()
            throw error
        }
        return try {
            block(renderer)
        } finally {
            renderer.close()
        }
    }

    private suspend fun <T> PdfRenderer.Page.usePage(block: suspend (PdfRenderer.Page) -> T): T = try {
        block(this)
    } finally {
        close()
    }

    private fun encode(bitmap: Bitmap, format: RasterFormat): ByteArray {
        val compressFormat = when (format) {
            RasterFormat.JPG -> Bitmap.CompressFormat.JPEG
            RasterFormat.PNG -> Bitmap.CompressFormat.PNG
        }
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(compressFormat, 92, output)
            output.toByteArray()
        }
    }
}
