package com.axvyn.app.features.powerpointtopdf

import android.net.Uri
import com.axvyn.app.core.processing.ProcessingState

data class PowerPointSourceInfo(
    val displayName: String,
    val sizeBytes: Long,
    val extension: String,
)

data class PowerPointToPdfResult(
    val fileName: String,
    val originalBytes: Long,
    val outputBytes: Long,
    val pageCount: Int,
    val shareUri: Uri?,
)

data class PowerPointToPdfUiState(
    val source: PowerPointSourceInfo? = null,
    val processing: ProcessingState = ProcessingState.Idle,
    val result: PowerPointToPdfResult? = null,
    val message: String? = null,
)

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
    else -> "$bytes B"
}