package com.example.notavia.settings

import com.example.notavia.FakeAppearanceSettings
import com.example.notavia.FakeLanguageSettings
import com.example.notavia.FakeThemeSettings
import com.example.notavia.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun changingThemeUpdatesStateAndEmitsThemeEffect() = runBlocking {
        val viewModel = SettingsViewModel(
            FakeThemeSettings(AppTheme.DARK),
            FakeAppearanceSettings(),
            FakeLanguageSettings(),
        )
        val effect = async { viewModel.effects.first() }
        yield()

        viewModel.changeTheme(AppTheme.LIGHT)

        assertEquals(AppTheme.LIGHT, viewModel.uiState.value.theme)
        assertEquals(SettingsEffect.ApplyTheme(AppTheme.LIGHT), effect.await())
    }

    @Test
    fun changingFontSizeUpdatesStateAndEmitsRecreateEffect() = runBlocking {
        val viewModel = SettingsViewModel(
            FakeThemeSettings(),
            FakeAppearanceSettings(AppFontSize.MEDIUM),
            FakeLanguageSettings(),
        )
        val effect = async { viewModel.effects.first() }
        yield()

        viewModel.changeFontSize(AppFontSize.LARGE)

        assertEquals(AppFontSize.LARGE, viewModel.uiState.value.fontSize)
        assertEquals(SettingsEffect.Recreate, effect.await())
    }

    @Test
    fun changingLanguageUpdatesStateAndEmitsLanguageEffect() = runBlocking {
        val viewModel = SettingsViewModel(
            FakeThemeSettings(),
            FakeAppearanceSettings(),
            FakeLanguageSettings(AppLanguage.RUSSIAN),
        )
        val effect = async { viewModel.effects.first() }
        yield()

        viewModel.changeLanguage(AppLanguage.ENGLISH)

        assertEquals(AppLanguage.ENGLISH, viewModel.uiState.value.language)
        assertEquals(SettingsEffect.ApplyLanguage(AppLanguage.ENGLISH), effect.await())
    }
}
