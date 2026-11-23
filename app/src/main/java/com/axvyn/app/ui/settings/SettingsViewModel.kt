package com.axvyn.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.axvyn.app.data.preferences.ThemeMode
import com.axvyn.app.data.preferences.ThemePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = ThemePreferences(application)
    private val _uiState = MutableStateFlow(SettingsUiState(themeMode = preferences.themeMode))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch {
            _uiState.emit(_uiState.value.copy(themeMode = mode))
        }
    }
}
