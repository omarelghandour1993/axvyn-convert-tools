package com.axvyn.app.features.texttovoice

import android.net.Uri
import com.axvyn.app.core.processing.ProcessingState

data class TtsVoiceOption(
    val id: String,
    val label: String,
    val localeTag: String,
    val languageDisplay: String,
    val enginePackage: String?,
    val isOffline: Boolean,
    val quality: Int = 0,
)

data class TextToVoiceResult(
    val fileName: String,
    val charCount: Int,
    val mp3Bytes: Long,
    val durationMs: Long?,
    val voiceLabel: String,
    val shareUri: Uri?,
)

data class TextToVoiceUiState(
    val text: String = "",
    val maxCharacters: Int = 3_000,
    val selectedLanguage: String = "English",
    val selectedVoiceId: String? = null,
    val availableVoices: List<TtsVoiceOption> = emptyList(),
    val languages: List<String> = emptyList(),
    val processing: ProcessingState = ProcessingState.Idle,
    val result: TextToVoiceResult? = null,
    val message: String? = null,
    val isTtsReady: Boolean = false,
    val isLoadingVoices: Boolean = true,
)

fun formatTtsBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
    else -> "$bytes B"
}

fun formatTtsDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
