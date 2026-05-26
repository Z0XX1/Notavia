package com.example.notavia

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.notavia.databinding.ActivitySettingsBinding
import com.example.notavia.settings.AppTheme
import com.example.notavia.settings.ThemePreferences
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var themePreferences: ThemePreferences

    private var currentTheme: AppTheme = AppTheme.DARK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        themePreferences = ThemePreferences(this)
        setupActions()
        observeTheme()
    }

    private fun setupActions() {
        installAlphaPressFeedback(binding.backButton)
        installAlphaPressFeedback(binding.lightThemeButton)
        installAlphaPressFeedback(binding.darkThemeButton)

        binding.backButton.setOnClickListener {
            finish()
        }

        binding.lightThemeButton.setOnClickListener {
            changeTheme(AppTheme.LIGHT)
        }

        binding.darkThemeButton.setOnClickListener {
            changeTheme(AppTheme.DARK)
        }
    }

    private fun observeTheme() {
        lifecycleScope.launch {
            themePreferences.themeFlow.collect { theme ->
                currentTheme = theme
                updateThemeButtons()
            }
        }
    }

    private fun changeTheme(theme: AppTheme) {
        if (theme == currentTheme) return

        lifecycleScope.launch {
            themePreferences.setTheme(theme)
            AppCompatDelegate.setDefaultNightMode(theme.nightMode)
        }
    }

    private fun updateThemeButtons() {
        val isLight = currentTheme == AppTheme.LIGHT
        styleThemeButton(binding.lightThemeButton, isActive = isLight)
        styleThemeButton(binding.darkThemeButton, isActive = !isLight)
    }

    private fun styleThemeButton(
        button: com.google.android.material.button.MaterialButton,
        isActive: Boolean,
    ) {
        val backgroundColor = ContextCompat.getColor(
            this,
            when {
                isActive && currentTheme == AppTheme.LIGHT -> R.color.theme_preview_light_active_bg
                isActive && currentTheme == AppTheme.DARK -> R.color.theme_preview_dark_active_bg
                !isActive && currentTheme == AppTheme.LIGHT -> R.color.theme_preview_light_inactive_bg
                else -> R.color.theme_preview_dark_inactive_bg
            },
        )
        val textColor = ContextCompat.getColor(
            this,
            when {
                isActive && currentTheme == AppTheme.LIGHT -> R.color.theme_preview_light_active_fg
                isActive && currentTheme == AppTheme.DARK -> R.color.theme_preview_dark_active_fg
                !isActive && currentTheme == AppTheme.LIGHT -> R.color.theme_preview_light_inactive_fg
                else -> R.color.theme_preview_dark_inactive_fg
            },
        )
        val strokeColor = ContextCompat.getColor(
            this,
            R.color.note_stroke_color,
        )

        button.backgroundTintList = ColorStateList.valueOf(backgroundColor)
        button.setTextColor(textColor)
        button.strokeWidth = if (isActive) 0 else 1
        button.strokeColor = ColorStateList.valueOf(strokeColor)
        button.alpha = 1f
    }

    private fun installAlphaPressFeedback(view: View) {
        view.setOnTouchListener { pressedView, event ->
            pressedView.alpha = when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> BUTTON_PRESSED_ALPHA
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> 1f
                else -> pressedView.alpha
            }
            false
        }
    }

    companion object {
        private const val BUTTON_PRESSED_ALPHA = 0.68f
    }
}
