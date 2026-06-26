package com.algerianwood.app.ui.theme

import android.content.Context

enum class AppThemeMode {
    NIGHT,
    DAY;

    fun toggled(): AppThemeMode = if (this == NIGHT) DAY else NIGHT
}

class ThemePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AppThemeMode {
        val name = prefs.getString(KEY_MODE, AppThemeMode.NIGHT.name) ?: AppThemeMode.NIGHT.name
        return runCatching { AppThemeMode.valueOf(name) }.getOrDefault(AppThemeMode.NIGHT)
    }

    fun save(mode: AppThemeMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "arwood_theme_prefs"
        private const val KEY_MODE = "theme_mode"
    }
}
