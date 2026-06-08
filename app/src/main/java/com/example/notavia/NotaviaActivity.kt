package com.example.notavia

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.notavia.di.NotaviaDependencies
import com.example.notavia.settings.AppFontSize
import com.example.notavia.settings.AppLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale


abstract class NotaviaActivity : AppCompatActivity() {
    private var appliedFontSize: AppFontSize = AppFontSize.MEDIUM
    private var appliedLanguage: AppLanguage = AppLanguage.RUSSIAN


    override fun attachBaseContext(newBase: Context) {
        val appContainer = NotaviaDependencies.from(newBase)
        val (fontSize, language) = runBlocking {
            appContainer.appearanceSettings.fontSizeFlow.first() to
                appContainer.languageSettings.languageFlow.first()
        }
        appliedFontSize = fontSize
        appliedLanguage = language
        super.attachBaseContext(newBase.withAppearance(fontSize.fontScale, language.localeTag))
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val appContainer = NotaviaDependencies.from(this@NotaviaActivity)
            val latestFontSize = appContainer.appearanceSettings.fontSizeFlow.first()
            val latestLanguage = appContainer.languageSettings.languageFlow.first()
            if (latestFontSize != appliedFontSize || latestLanguage != appliedLanguage) {
                recreate()
            }
        }
    }

    private fun Context.withAppearance(fontScale: Float, localeTag: String): Context {
        val locale = Locale.forLanguageTag(localeTag)
        Locale.setDefault(locale)
        val configuration = Configuration(resources.configuration).apply {
            this.fontScale = fontScale
            setLocales(LocaleList(locale))
        }
        return createConfigurationContext(configuration)
    }
}
