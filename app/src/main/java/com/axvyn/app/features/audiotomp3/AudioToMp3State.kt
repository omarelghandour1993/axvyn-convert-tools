package com.axvyn.app.features.audiotomp3

import android.net.Uri
import com.axvyn.app.core.processing.ProcessingState

enum class Mp3Bitrate(val kbps: Int, val label: String) {
    K128(128, "128 kbps"),
    K192(192, "192 kbps"),
    K256(256, "256 kbps"),
    K320(320, "320 kbps"),
}

data class SourceAudioInfo(
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val durationMs: Long?,
)

data class ConvertedAudio(
    val fileName: String,
    val originalBytes: Long,
    val convertedBytes: Long,
    val bitrateKbps: Int,
    val durationMs: Long?,
    val mimeType: String,
    val shareUri: Uri?,
)

data class AudioToMp3UiState(
    val source: SourceAudioInfo? = null,
    val bitrate: Mp3Bitrate = Mp3Bitrate.K320,
    val processing: ProcessingState = ProcessingState.Idle,
    val result: ConvertedAudio? = null,
    val message: String? = null,
)

fun formatAudioBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
    else -> "$bytes B"
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
