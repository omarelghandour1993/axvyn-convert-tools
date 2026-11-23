package com.axvyn.app.features.texttovoice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axvyn.app.data.voicepacks.VoicePackCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TextToVoiceViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(
        TextToVoiceUiState(
            maxCharacters = VoicePackCatalog.MAX_CHARACTERS,
            installedVoicePacks = VoicePackCatalog.installedPacks.size,
        ),
    )
    val uiState: StateFlow<TextToVoiceUiState> = _uiState.asStateFlow()

    fun onTextChange(value: String) {
        val limited = value.take(VoicePackCatalog.MAX_CHARACTERS)
        viewModelScope.launch { _uiState.emit(_uiState.value.copy(text = limited)) }
    }

    fun onLanguageChange(language: String) {
        viewModelScope.launch { _uiState.emit(_uiState.value.copy(language = language)) }
    }
}
