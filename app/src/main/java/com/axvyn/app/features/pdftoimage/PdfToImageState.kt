package com.axvyn.app.features.pdftoimage

import android.net.Uri
import com.axvyn.app.core.processing.ProcessingState

enum class RasterFormat(val label: String, val mimeType: String, val extension: String) {
    JPG("JPG", "image/jpeg", "jpg"),
    PNG("PNG", "image/png", "png"),
}

data class SourcePdfInfo(
    val displayName: String,
    val sizeBytes: Long,
    val pageCount: Int,
)

data class RenderedPage(
    val fileName: String,
    val pageNumber: Int,
    val pageCount: Int,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val mimeType: String,
    val shareUri: Uri?,
)

data class PdfToImageUiState(
    val source: SourcePdfInfo? = null,
    val format: RasterFormat = RasterFormat.JPG,
    val resolutionDpi: Int = 150,
    val pageInput: String = "1",
    val processing: ProcessingState = ProcessingState.Idle,
    val result: RenderedPage? = null,
    val message: String? = null,
)

fun formatPdfBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
    else -> "$bytes B"
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
    else -> "$bytes B"
}
