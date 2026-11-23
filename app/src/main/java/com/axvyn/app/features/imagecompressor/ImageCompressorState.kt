package com.axvyn.app.features.imagecompressor

import android.net.Uri
import com.axvyn.app.core.processing.ProcessingState

enum class ImageFormat(val label: String, val mimeType: String, val extension: String) {
    JPEG("JPEG", "image/jpeg", "jpg"),
    PNG("PNG", "image/png", "png"),
    WEBP("WebP", "image/webp", "webp"),
}

data class SourceImageInfo(
    val displayName: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val format: ImageFormat,
)

data class CompressedImage(
    val fileName: String,
    val originalBytes: Long,
    val compressedBytes: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val shareUri: Uri?,
    val note: String?,
)

data class ImageCompressorUiState(
    val source: SourceImageInfo? = null,
    val quality: Int = 80,
    val outputFormat: ImageFormat = ImageFormat.JPEG,
    val resizeEnabled: Boolean = false,
    val widthInput: String = "",
    val heightInput: String = "",
    val targetSizeKbInput: String = "",
    val processing: ProcessingState = ProcessingState.Idle,
    val result: CompressedImage? = null,
    val message: String? = null,
)

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
    else -> "$bytes B"
}
