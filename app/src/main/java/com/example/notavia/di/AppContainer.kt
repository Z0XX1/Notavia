package com.example.notavia.di

import android.content.Context
import com.example.notavia.data.NoteRepository
import com.example.notavia.data.NotesRepository
import com.example.notavia.data.NotaviaDatabase
import com.example.notavia.settings.AppearancePreferences
import com.example.notavia.settings.AppearanceSettings
import com.example.notavia.settings.CategoryPreferences
import com.example.notavia.settings.CategorySettings
import com.example.notavia.settings.LanguagePreferences
import com.example.notavia.settings.LanguageSettings
import com.example.notavia.settings.ThemePreferences
import com.example.notavia.settings.ThemeSettings

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database: NotaviaDatabase by lazy {
        NotaviaDatabase.getDatabase(appContext)
    }

    val notesRepository: NotesRepository by lazy {
        NoteRepository(database.noteDao())
    }

    val themeSettings: ThemeSettings by lazy {
        ThemePreferences(appContext)
    }

    val appearanceSettings: AppearanceSettings by lazy {
        AppearancePreferences(appContext)
    }

    val languageSettings: LanguageSettings by lazy {
        LanguagePreferences(appContext)
    }

    val categorySettings: CategorySettings by lazy {
        CategoryPreferences(appContext)
    }
}

object NotaviaDependencies {
    @Volatile
    private var container: AppContainer? = null

    fun initialize(context: Context): AppContainer {
        return container ?: synchronized(this) {
            container ?: AppContainer(context.applicationContext).also { container = it }
        }
    }

    fun from(context: Context): AppContainer {
        return initialize(context.applicationContext)
    }
}

fun Context.notaviaContainer(): AppContainer {
    return NotaviaDependencies.from(this)
}
