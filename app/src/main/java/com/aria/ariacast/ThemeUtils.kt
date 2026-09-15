package com.aria.ariacast

import androidx.appcompat.app.AppCompatDelegate

object ThemeUtils {
    const val MODE_NIGHT_NO = AppCompatDelegate.MODE_NIGHT_NO
    const val MODE_NIGHT_YES = AppCompatDelegate.MODE_NIGHT_YES
    const val MODE_NIGHT_FOLLOW_SYSTEM = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

    fun applyTheme(themeMode: Int) {
        AppCompatDelegate.setDefaultNightMode(themeMode)
    }
}
