package com.example.notavia.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "notavia_settings")

interface ThemeSettings {
    val themeFlow: Flow<AppTheme>

    suspend fun setTheme(theme: AppTheme)
}

interface AppearanceSettings {
    val fontSizeFlow: Flow<AppFontSize>

    suspend fun setFontSize(fontSize: AppFontSize)
}

interface LanguageSettings {
    val languageFlow: Flow<AppLanguage>

    suspend fun setLanguage(language: AppLanguage)
}

class ThemePreferences(private val context: Context) : ThemeSettings {
    override val themeFlow: Flow<AppTheme> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AppTheme.fromStorage(preferences[Keys.THEME_MODE])
        }

    override suspend fun setTheme(theme: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[Keys.THEME_MODE] = theme.storageValue
        }
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}


class AppearancePreferences(private val context: Context) : AppearanceSettings {
    override val fontSizeFlow: Flow<AppFontSize> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AppFontSize.fromStorage(preferences[Keys.FONT_SIZE])
        }

    override suspend fun setFontSize(fontSize: AppFontSize) {
        context.dataStore.edit { preferences ->
            preferences[Keys.FONT_SIZE] = fontSize.storageValue
        }
    }

    private object Keys {
        val FONT_SIZE = stringPreferencesKey("font_size")
    }
}

class LanguagePreferences(private val context: Context) : LanguageSettings {
    override val languageFlow: Flow<AppLanguage> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AppLanguage.fromStorage(preferences[Keys.LANGUAGE])
        }

    override suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { preferences ->
            preferences[Keys.LANGUAGE] = language.storageValue
        }
    }

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
    }
}
