package com.example.notavia

import com.example.notavia.data.Note
import com.example.notavia.data.NotesRepository
import com.example.notavia.settings.AppFontSize
import com.example.notavia.settings.AppLanguage
import com.example.notavia.settings.AppTheme
import com.example.notavia.settings.AppearanceSettings
import com.example.notavia.settings.CategorySettings
import com.example.notavia.settings.LanguageSettings
import com.example.notavia.settings.ThemeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeNotesRepository(
    initialNotes: List<Note> = emptyList(),
) : NotesRepository {
    private val notes = MutableStateFlow(initialNotes)
    private var nextId = (initialNotes.maxOfOrNull { it.id } ?: 0L) + 1L

    override val notesFlow: Flow<List<Note>> = notes

    override suspend fun getAllNotesSnapshot(): List<Note> = notes.value

    override suspend fun getNoteById(id: Long): Note? {
        return notes.value.firstOrNull { it.id == id }
    }

    override suspend fun saveNote(note: Note): Long {
        val savedNote = if (note.id == 0L) {
            note.copy(id = nextId++)
        } else {
            note
        }
        notes.value = notes.value
            .filterNot { it.id == savedNote.id } + savedNote
        return savedNote.id
    }

    override suspend fun deleteNotes(ids: List<Long>) {
        notes.value = notes.value.filterNot { it.id in ids }
    }

    override suspend fun updatePinnedState(ids: List<Long>, isPinned: Boolean) {
        notes.value = notes.value.map { note ->
            if (note.id in ids) {
                note.copy(isPinned = isPinned)
            } else {
                note
            }
        }
    }

    fun emit(notes: List<Note>) {
        this.notes.value = notes
    }
}

class FakeCategorySettings(
    pinned: Set<String> = emptySet(),
    hidden: Set<String> = emptySet(),
    custom: Set<String> = emptySet(),
) : CategorySettings {
    private val pinnedCategories = MutableStateFlow(pinned)
    private val hiddenCategories = MutableStateFlow(hidden)
    private val customCategories = MutableStateFlow(custom)

    override val pinnedCategoriesFlow: Flow<Set<String>> = pinnedCategories
    override val hiddenCategoriesFlow: Flow<Set<String>> = hiddenCategories
    override val customCategoriesFlow: Flow<Set<String>> = customCategories

    override suspend fun setPinnedCategories(categories: Set<String>) {
        pinnedCategories.value = categories
    }

    override suspend fun setHiddenCategories(categories: Set<String>) {
        hiddenCategories.value = categories
    }

    override suspend fun setCustomCategories(categories: Set<String>) {
        customCategories.value = categories
    }
}

class FakeThemeSettings(initialTheme: AppTheme = AppTheme.DARK) : ThemeSettings {
    private val theme = MutableStateFlow(initialTheme)
    override val themeFlow: Flow<AppTheme> = theme

    override suspend fun setTheme(theme: AppTheme) {
        this.theme.value = theme
    }
}

class FakeAppearanceSettings(initialFontSize: AppFontSize = AppFontSize.MEDIUM) : AppearanceSettings {
    private val fontSize = MutableStateFlow(initialFontSize)
    override val fontSizeFlow: Flow<AppFontSize> = fontSize

    override suspend fun setFontSize(fontSize: AppFontSize) {
        this.fontSize.value = fontSize
    }
}

class FakeLanguageSettings(initialLanguage: AppLanguage = AppLanguage.RUSSIAN) : LanguageSettings {
    private val language = MutableStateFlow(initialLanguage)
    override val languageFlow: Flow<AppLanguage> = language

    override suspend fun setLanguage(language: AppLanguage) {
        this.language.value = language
    }
}
