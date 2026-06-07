package com.example.notavia.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val theme: AppTheme = AppTheme.DARK,
    val fontSize: AppFontSize = AppFontSize.MEDIUM,
    val language: AppLanguage = AppLanguage.RUSSIAN,
)

sealed interface SettingsEffect {
    data class ApplyTheme(val theme: AppTheme) : SettingsEffect
    data class ApplyLanguage(val language: AppLanguage) : SettingsEffect
    data object Recreate : SettingsEffect
}

class SettingsViewModel(
    private val themePreferences: ThemeSettings,
    private val appearancePreferences: AppearanceSettings,
    private val languagePreferences: LanguageSettings,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<SettingsEffect>()
    val effects: SharedFlow<SettingsEffect> = _effects.asSharedFlow()

    init {
        observeSettings()
    }

    fun changeTheme(theme: AppTheme) {
        if (theme == _uiState.value.theme) return

        viewModelScope.launch {
            themePreferences.setTheme(theme)
            _effects.emit(SettingsEffect.ApplyTheme(theme))
        }
    }

    fun changeFontSize(fontSize: AppFontSize) {
        if (fontSize == _uiState.value.fontSize) return

        viewModelScope.launch {
            appearancePreferences.setFontSize(fontSize)
            _effects.emit(SettingsEffect.Recreate)
        }
    }

    fun changeLanguage(language: AppLanguage) {
        if (language == _uiState.value.language) return

        viewModelScope.launch {
            languagePreferences.setLanguage(language)
            _effects.emit(SettingsEffect.ApplyLanguage(language))
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            themePreferences.themeFlow.collect { theme ->
                _uiState.update { state -> state.copy(theme = theme) }
            }
        }
        viewModelScope.launch {
            appearancePreferences.fontSizeFlow.collect { fontSize ->
                _uiState.update { state -> state.copy(fontSize = fontSize) }
            }
        }
        viewModelScope.launch {
            languagePreferences.languageFlow.collect { language ->
                _uiState.update { state -> state.copy(language = language) }
            }
        }
    }

    class Factory(
        private val themePreferences: ThemeSettings,
        private val appearancePreferences: AppearanceSettings,
        private val languagePreferences: LanguageSettings,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(
                    themePreferences,
                    appearancePreferences,
                    languagePreferences,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
