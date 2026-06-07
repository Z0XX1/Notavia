package com.example.notavia

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.notavia.databinding.ActivitySettingsBinding
import com.example.notavia.settings.AppFontSize
import com.example.notavia.settings.AppLanguage
import com.example.notavia.settings.AppTheme
import com.example.notavia.settings.AppearancePreferences
import com.example.notavia.settings.LanguagePreferences
import com.example.notavia.settings.SettingsEffect
import com.example.notavia.settings.SettingsMenuController
import com.example.notavia.settings.SettingsOption
import com.example.notavia.settings.SettingsUiState
import com.example.notavia.settings.SettingsViewModel
import com.example.notavia.settings.ThemePreferences
import com.example.notavia.ui.installAlphaPressFeedback
import kotlinx.coroutines.launch


class SettingsActivity : NotaviaActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var viewModel: SettingsViewModel
    private lateinit var menuController: SettingsMenuController

    private var currentSettings: SettingsUiState = SettingsUiState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        menuController = SettingsMenuController(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        viewModel = ViewModelProvider(
            this,
            SettingsViewModel.Factory(
                ThemePreferences(this),
                AppearancePreferences(this),
                LanguagePreferences(this),
            ),
        )[SettingsViewModel::class.java]
        setupActions()
        observeSettings()
    }


    private fun setupActions() {
        installAlphaPressFeedback(binding.backButton)
        installAlphaPressFeedback(binding.fontSizeRow)
        installAlphaPressFeedback(binding.themeRow)
        installAlphaPressFeedback(binding.languageRow)

        binding.backButton.setOnClickListener {
            finish()
        }

        binding.fontSizeRow.setOnClickListener {
            showFontSizeMenu()
        }

        binding.themeRow.setOnClickListener {
            showThemeMenu()
        }

        binding.languageRow.setOnClickListener {
            showLanguageMenu()
        }
    }


    private fun observeSettings() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        currentSettings = state
                        renderSettings(state)
                    }
                }
                launch {
                    viewModel.effects.collect { effect ->
                        handleSettingsEffect(effect)
                    }
                }
            }
        }
    }

    private fun showFontSizeMenu() {
        menuController.show(
            anchor = binding.fontSizeRow,
            options = AppFontSize.entries.map { fontSize ->
                SettingsOption(fontSize, getString(fontSize.labelRes))
            },
            selectedValue = currentSettings.fontSize,
        ) { fontSize ->
            viewModel.changeFontSize(fontSize)
        }
    }

    private fun showThemeMenu() {
        menuController.show(
            anchor = binding.themeRow,
            options = listOf(
                SettingsOption(AppTheme.DARK, getString(R.string.dark_theme_short)),
                SettingsOption(AppTheme.LIGHT, getString(R.string.light_theme_short)),
            ),
            selectedValue = currentSettings.theme,
        ) { theme ->
            viewModel.changeTheme(theme)
        }
    }

    private fun showLanguageMenu() {
        menuController.show(
            anchor = binding.languageRow,
            options = AppLanguage.entries.map { language ->
                SettingsOption(language, getString(language.labelRes))
            },
            selectedValue = currentSettings.language,
        ) { language ->
            viewModel.changeLanguage(language)
        }
    }


    private fun renderSettings(state: SettingsUiState) {
        binding.themeValueTextView.text = getString(
            if (state.theme == AppTheme.DARK) {
                R.string.dark_theme_short
            } else {
                R.string.light_theme_short
            },
        )
        binding.fontSizeValueTextView.text = getString(state.fontSize.labelRes)
        binding.languageValueTextView.text = getString(state.language.labelRes)
    }

    private fun handleSettingsEffect(effect: SettingsEffect) {
        when (effect) {
            is SettingsEffect.ApplyTheme -> {
                AppCompatDelegate.setDefaultNightMode(effect.theme.nightMode)
            }
            is SettingsEffect.ApplyLanguage -> {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(effect.language.localeTag),
                )
                recreate()
            }
            SettingsEffect.Recreate -> recreate()
        }
    }

}
