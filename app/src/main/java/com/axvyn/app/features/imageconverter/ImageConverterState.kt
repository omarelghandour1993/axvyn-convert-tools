package com.axvyn.app.features.imageconverter

data class ImageConverterUiState(
    val sourceFormats: String = "WEBP, AVIF",
    val outputFormat: String = "PNG",
)
