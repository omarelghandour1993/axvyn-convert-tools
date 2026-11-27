package com.axvyn.app.features.mergepdf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.FileOutputStream

class MergePdfEngine {

    data class InspectResult(val name: String, val sizeBytes: Long, val pageCount: Int)
    data class ConvertResult(val pageCount: Int, val outputBytes: Long)

    fun inspect(context: Context, uri: Uri): InspectResult {
        val name = queryName(context, uri) ?: "document.pdf"
        val size = querySize(context, uri)
        context.contentResolver.openInputStream(uri)?.use { input ->
            try {
                PDDocument.load(input).use { doc ->
                    return InspectResult(name = name, sizeBytes = size, pageCount = doc.numberOfPages)
                }
            } catch (e: Exception) {
                throw IllegalArgumentException("The selected file is not a valid PDF document.", e)
            }
        } ?: error("Cannot open the selected file")
    }

    fun convert(
        context: Context,
        uris: List<Uri>,
        outputFile: File,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): ConvertResult {
        if (uris.size < 2) error("Choose at least two PDF files to merge.")
        onProgress(0.02f)

        val merger = PDFMergerUtility()
        val tempFiles = mutableListOf<File>()
        try {
            uris.forEachIndexed { index, uri ->
                if (isCancelled()) throw Cancellation()
                val tmp = File(outputFile.parentFile, "merge-src-${System.currentTimeMillis()}-$index.pdf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tmp).use { out -> input.copyTo(out) }
                } ?: error("Cannot open one of the selected files")
                // Validate it's a PDF
                try {
                    PDDocument.load(tmp).use { /* ok */ }
                } catch (e: Exception) {
                    throw IllegalArgumentException("One of the selected files is not a valid PDF document.", e)
                }
                tempFiles += tmp
                merger.addSource(tmp)
                onProgress(0.05f + 0.55f * (index + 1) / uris.size.toFloat())
            }

            if (isCancelled()) throw Cancellation()
            merger.destinationFileName = outputFile.absolutePath
            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
            onProgress(0.95f)

            val pageCount = PDDocument.load(outputFile).use { it.numberOfPages }
            onProgress(1f)
            return ConvertResult(pageCount = pageCount, outputBytes = outputFile.length())
        } catch (e: Exception) {
            outputFile.delete()
            throw e
        } finally {
            tempFiles.forEach { it.delete() }
        }
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
