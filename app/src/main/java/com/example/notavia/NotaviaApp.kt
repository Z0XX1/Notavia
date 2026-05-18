package com.example.notavia

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.example.notavia.settings.ThemePreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class NotaviaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val theme = runBlocking {
            ThemePreferences(this@NotaviaApp).themeFlow.first()
        }
        AppCompatDelegate.setDefaultNightMode(theme.nightMode)
    }
}
