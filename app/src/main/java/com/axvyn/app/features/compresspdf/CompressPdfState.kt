package com.axvyn.app.features.compresspdf

import android.net.Uri
import com.axvyn.app.core.processing.ProcessingState

enum class PdfCompressionLevel(val label: String, val dpi: Int, val jpegQuality: Int) {
    SMALLER("Smaller file", 96, 60),
    BALANCED("Balanced", 120, 75),
    BETTER("Better quality", 150, 85),
}

data class CompressPdfSourceInfo(
    val displayName: String,
    val sizeBytes: Long,
    val pageCount: Int,
)

data class CompressPdfResult(
    val fileName: String,
    val originalBytes: Long,
    val outputBytes: Long,
    val pageCount: Int,
    val shareUri: Uri?,
)

data class CompressPdfUiState(
    val source: CompressPdfSourceInfo? = null,
    val level: PdfCompressionLevel = PdfCompressionLevel.BALANCED,
    val processing: ProcessingState = ProcessingState.Idle,
    val result: CompressPdfResult? = null,
    val message: String? = null,
)

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
    else -> "$bytes B"
}
