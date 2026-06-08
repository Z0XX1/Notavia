package com.example.notavia

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.notavia.di.NotaviaDependencies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking


class NotaviaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val appContainer = NotaviaDependencies.initialize(this)
        val (theme, language) = runBlocking {
            appContainer.themeSettings.themeFlow.first() to
                appContainer.languageSettings.languageFlow.first()
        }
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(language.localeTag),
        )
        AppCompatDelegate.setDefaultNightMode(theme.nightMode)
    }
}
