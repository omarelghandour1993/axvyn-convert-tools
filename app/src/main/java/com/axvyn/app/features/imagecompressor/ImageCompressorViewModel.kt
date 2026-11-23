package com.axvyn.app.features.imagecompressor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ImageCompressorViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ImageCompressorUiState())
    val uiState: StateFlow<ImageCompressorUiState> = _uiState.asStateFlow()

    fun onQualityChange(value: Float) {
        viewModelScope.launch { _uiState.emit(_uiState.value.copy(quality = value)) }
    }

    fun onResizeChange(enabled: Boolean) {
        viewModelScope.launch { _uiState.emit(_uiState.value.copy(resizeEnabled = enabled)) }
    }
}
