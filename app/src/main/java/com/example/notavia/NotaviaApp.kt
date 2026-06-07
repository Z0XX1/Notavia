package com.example.notavia

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.notavia.settings.LanguagePreferences
import com.example.notavia.settings.ThemePreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking


class NotaviaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val (theme, language) = runBlocking {
            ThemePreferences(this@NotaviaApp).themeFlow.first() to
                LanguagePreferences(this@NotaviaApp).languageFlow.first()
        }
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(language.localeTag),
        )
        AppCompatDelegate.setDefaultNightMode(theme.nightMode)
    }
}
