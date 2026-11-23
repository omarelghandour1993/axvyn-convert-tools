package com.axvyn.app.ui.settings

import com.axvyn.app.data.preferences.ThemeMode

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appVersion: String = "1.0.0",
)
