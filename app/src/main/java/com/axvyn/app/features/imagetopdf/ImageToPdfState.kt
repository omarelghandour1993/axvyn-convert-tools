package com.axvyn.app.features.imagetopdf

import android.net.Uri
import com.axvyn.app.core.processing.ProcessingState

data class ImageSourceInfo(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val width: Int,
    val height: Int,
)

data class ImageToPdfResult(
    val fileName: String,
    val originalBytes: Long,
    val outputBytes: Long,
    val pageCount: Int,
    val shareUri: Uri?,
)

data class ImageToPdfUiState(
    val sources: List<ImageSourceInfo> = emptyList(),
    val processing: ProcessingState = ProcessingState.Idle,
    val result: ImageToPdfResult? = null,
    val message: String? = null,
)

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
    else -> "$bytes B"
}
