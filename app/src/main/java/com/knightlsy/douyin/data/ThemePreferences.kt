package com.knightlsy.douyin.data

import android.content.Context
import android.content.SharedPreferences

class ThemePreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    private val key = "dark_mode"

    fun isDarkMode(): Boolean = prefs.getBoolean(key, false)

    fun setDarkMode(dark: Boolean) {
        prefs.edit().putBoolean(key, dark).apply()
    }
}
