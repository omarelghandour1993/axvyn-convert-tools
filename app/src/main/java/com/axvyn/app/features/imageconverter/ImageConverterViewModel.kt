package com.axvyn.app.features.imageconverter

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ImageConverterViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ImageConverterUiState())
    val uiState: StateFlow<ImageConverterUiState> = _uiState.asStateFlow()
}
