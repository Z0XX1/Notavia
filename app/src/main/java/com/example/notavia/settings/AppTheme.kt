package com.example.notavia.settings

import androidx.appcompat.app.AppCompatDelegate

// Тема хранит значение для DataStore и режим AppCompatDelegate.
enum class AppTheme(val storageValue: String, val nightMode: Int) {
    LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO),
    DARK("dark", AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        fun fromStorage(value: String?): AppTheme {
            return entries.firstOrNull { it.storageValue == value } ?: DARK
        }
    }
}
