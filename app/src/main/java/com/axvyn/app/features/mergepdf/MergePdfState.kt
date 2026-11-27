package com.axvyn.app.features.mergepdf

import android.net.Uri
import com.axvyn.app.core.processing.ProcessingState

data class MergePdfSourceInfo(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val pageCount: Int,
)

data class MergePdfResult(
    val fileName: String,
    val originalBytes: Long,
    val outputBytes: Long,
    val pageCount: Int,
    val sourceCount: Int,
    val shareUri: Uri?,
)

data class MergePdfUiState(
    val sources: List<MergePdfSourceInfo> = emptyList(),
    val processing: ProcessingState = ProcessingState.Idle,
    val result: MergePdfResult? = null,
    val message: String? = null,
)

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
    else -> "$bytes B"
}
