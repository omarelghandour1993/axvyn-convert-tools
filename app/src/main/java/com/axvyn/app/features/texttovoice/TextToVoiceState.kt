package com.axvyn.app.features.texttovoice

data class TextToVoiceUiState(
    val text: String = "",
    val language: String = "English",
    val maxCharacters: Int = 3_000,
    val installedVoicePacks: Int = 0,
)
