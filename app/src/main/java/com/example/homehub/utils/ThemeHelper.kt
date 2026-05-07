package com.example.homehub.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeHelper {
    const val MODE_DARK = "dark"
    const val MODE_LIGHT = "light"
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME = "current_theme"

    fun applyTheme(theme: String) {
        when (theme) {
            MODE_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            MODE_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    fun loadTheme(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME, MODE_LIGHT) ?: MODE_LIGHT
    }

    fun saveTheme(context: Context, mode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, mode).apply()
        applyTheme(mode)
    }

    fun isDarkMode(context: Context): Boolean {
        return loadTheme(context) == MODE_DARK
    }
}
