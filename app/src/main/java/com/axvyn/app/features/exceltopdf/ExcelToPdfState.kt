package com.axvyn.app.features.exceltopdf

import android.net.Uri
import com.axvyn.app.core.processing.ProcessingState

data class ExcelSourceInfo(
    val displayName: String,
    val sizeBytes: Long,
    val extension: String,
)

data class ExcelToPdfResult(
    val fileName: String,
    val originalBytes: Long,
    val outputBytes: Long,
    val pageCount: Int,
    val sheetCount: Int,
    val shareUri: Uri?,
)

data class ExcelToPdfUiState(
    val source: ExcelSourceInfo? = null,
    val processing: ProcessingState = ProcessingState.Idle,
    val result: ExcelToPdfResult? = null,
    val message: String? = null,
)

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
    else -> "$bytes B"
}