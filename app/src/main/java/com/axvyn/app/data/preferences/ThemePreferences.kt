package com.axvyn.app.data.preferences

import android.content.Context

/** Small device-local preference store; no cloud sync and no account required. */
class ThemePreferences(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = runCatching {
            ThemeMode.valueOf(preferences.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)
        set(value) {
            preferences.edit().putString(KEY_THEME_MODE, value.name).apply()
        }

    private companion object {
        const val PREFERENCES_NAME = "axvyn.preferences"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
