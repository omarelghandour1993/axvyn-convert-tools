package com.axvyn.app.features.wordtopdf

data class WordToPdfUiState(
    val acceptedFormat: String = ".docx",
    val maxSizeMb: Int = 10,
)
