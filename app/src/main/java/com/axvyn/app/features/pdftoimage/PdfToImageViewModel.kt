package com.axvyn.app.features.pdftoimage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PdfToImageViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PdfToImageUiState())
    val uiState: StateFlow<PdfToImageUiState> = _uiState.asStateFlow()

    fun onFormatChange(format: RasterFormat) {
        viewModelScope.launch { _uiState.emit(_uiState.value.copy(format = format)) }
    }

    fun onResolutionChange(dpi: Int) {
        viewModelScope.launch { _uiState.emit(_uiState.value.copy(resolutionDpi = dpi)) }
    }
}
