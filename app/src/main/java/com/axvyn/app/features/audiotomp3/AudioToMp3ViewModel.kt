package com.axvyn.app.features.audiotomp3

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioToMp3ViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AudioToMp3UiState())
    val uiState: StateFlow<AudioToMp3UiState> = _uiState.asStateFlow()
}
