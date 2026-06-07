package com.example.notavia

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.notavia.settings.AppearancePreferences
import com.example.notavia.settings.AppFontSize
import com.example.notavia.settings.AppLanguage
import com.example.notavia.settings.LanguagePreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale


abstract class NotaviaActivity : AppCompatActivity() {
    private var appliedFontSize: AppFontSize = AppFontSize.MEDIUM
    private var appliedLanguage: AppLanguage = AppLanguage.RUSSIAN


    override fun attachBaseContext(newBase: Context) {
        val (fontSize, language) = runBlocking {
            AppearancePreferences(newBase).fontSizeFlow.first() to
                LanguagePreferences(newBase).languageFlow.first()
        }
        appliedFontSize = fontSize
        appliedLanguage = language
        super.attachBaseContext(newBase.withAppearance(fontSize.fontScale, language.localeTag))
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val latestFontSize = AppearancePreferences(this@NotaviaActivity).fontSizeFlow.first()
            val latestLanguage = LanguagePreferences(this@NotaviaActivity).languageFlow.first()
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
