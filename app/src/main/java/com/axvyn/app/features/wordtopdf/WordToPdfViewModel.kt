package com.axvyn.app.features.wordtopdf

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WordToPdfViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(WordToPdfUiState())
    val uiState: StateFlow<WordToPdfUiState> = _uiState.asStateFlow()
}
