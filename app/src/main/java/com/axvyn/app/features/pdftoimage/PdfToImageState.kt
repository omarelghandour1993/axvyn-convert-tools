package com.axvyn.app.features.pdftoimage

enum class RasterFormat(val label: String) {
    JPG("JPG"),
    PNG("PNG"),
}

data class PdfToImageUiState(
    val format: RasterFormat = RasterFormat.JPG,
    val resolutionDpi: Int = 150,
)
