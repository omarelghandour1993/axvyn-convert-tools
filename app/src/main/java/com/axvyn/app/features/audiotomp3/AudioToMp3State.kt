package com.axvyn.app.features.audiotomp3

data class AudioToMp3UiState(
    val outputBitrateKbps: Int = 320,
    val acceptedFormats: String = "M4A, WAV, FLAC",
)
