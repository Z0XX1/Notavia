package com.example.notavia

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.notavia.settings.AppearancePreferences
import com.example.notavia.settings.AppFontSize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

abstract class NotaviaActivity : AppCompatActivity() {
    private var appliedFontSize: AppFontSize = AppFontSize.MEDIUM

    override fun attachBaseContext(newBase: Context) {
        val fontSize = runBlocking {
            AppearancePreferences(newBase).fontSizeFlow.first()
        }
        appliedFontSize = fontSize
        super.attachBaseContext(newBase.withFontScale(fontSize.fontScale))
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val latestFontSize = AppearancePreferences(this@NotaviaActivity).fontSizeFlow.first()
            if (latestFontSize != appliedFontSize) {
                recreate()
            }
        }
    }

    private fun Context.withFontScale(fontScale: Float): Context {
        val configuration = Configuration(resources.configuration).apply {
            this.fontScale = fontScale
        }
        return createConfigurationContext(configuration)
    }
}
