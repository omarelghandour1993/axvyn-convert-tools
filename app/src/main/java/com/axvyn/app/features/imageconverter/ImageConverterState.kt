package com.axvyn.app.features.imageconverter

import android.net.Uri
import com.axvyn.app.core.processing.ProcessingState

enum class SourceImageFormat(val label: String) {
    WEBP("WebP"),
    AVIF("AVIF"),
    UNKNOWN("Unknown"),
}

data class SourceImageInfo(
    val displayName: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val format: SourceImageFormat,
)

data class ConvertedImage(
    val fileName: String,
    val originalBytes: Long,
    val convertedBytes: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val shareUri: Uri?,
)

data class ImageConverterUiState(
    val source: SourceImageInfo? = null,
    val processing: ProcessingState = ProcessingState.Idle,
    val result: ConvertedImage? = null,
    val message: String? = null,
)

fun formatImageBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
    else -> "$bytes B"
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
    else -> "$bytes B"
}
