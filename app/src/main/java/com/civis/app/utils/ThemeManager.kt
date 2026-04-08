package com.civis.app.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Gestiona el tema de la aplicacion (claro/oscuro).
 * Se persiste en SharedPreferences independiente de TokenManager.
 */
object ThemeManager {

    private const val PREFS_NAME = "civis_theme"
    private const val KEY_THEME = "theme_mode"

    // Modos: "light", "dark", "system"
    fun getThemeMode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME, "light") ?: "light"
    }

    fun setThemeMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, mode)
            .apply()
        applyTheme(mode)
    }

    fun applyTheme(mode: String) {
        when (mode) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    /**
     * Aplica el tema guardado. Llamar en onCreate de CivisApp o antes de super.onCreate().
     */
    fun applySavedTheme(context: Context) {
        val mode = getThemeMode(context)
        applyTheme(mode)
    }

    /**
     * Alterna entre claro y oscuro.
     */
    fun toggleTheme(context: Context): String {
        val current = getThemeMode(context)
        val newMode = if (current == "dark") "light" else "dark"
        setThemeMode(context, newMode)
        return newMode
    }

    fun isDarkMode(context: Context): Boolean {
        return getThemeMode(context) == "dark"
    }
}
