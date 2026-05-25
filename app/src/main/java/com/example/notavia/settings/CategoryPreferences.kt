package com.example.notavia.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.notavia.data.NoteCategories
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.categoryDataStore by preferencesDataStore(name = "notavia_categories")

class CategoryPreferences(private val context: Context) {
    val pinnedCategoriesFlow: Flow<Set<String>> = context.categoryDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[Keys.PINNED_CATEGORIES]
                ?.split(SEPARATOR)
                ?.filter { it.isNotBlank() }
                ?.map { NoteCategories.normalize(it) }
                ?.toSet()
                .orEmpty()
        }

    val hiddenCategoriesFlow: Flow<Set<String>> = context.categoryDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[Keys.HIDDEN_CATEGORIES]
                ?.split(SEPARATOR)
                ?.filter { it.isNotBlank() }
                ?.map { NoteCategories.normalize(it) }
                ?.toSet()
                .orEmpty()
        }

    suspend fun setPinnedCategories(categories: Set<String>) {
        context.categoryDataStore.edit { preferences ->
            val pinnedCategories = categories
                .map { NoteCategories.normalize(it) }
                .distinct()
            if (pinnedCategories.isEmpty()) {
                preferences.remove(Keys.PINNED_CATEGORIES)
            } else {
                preferences[Keys.PINNED_CATEGORIES] = pinnedCategories.joinToString(SEPARATOR)
            }
        }
    }

    suspend fun setHiddenCategories(categories: Set<String>) {
        context.categoryDataStore.edit { preferences ->
            val hiddenCategories = categories
                .map { NoteCategories.normalize(it) }
                .filterNot { it == NoteCategories.DEFAULT }
                .distinct()
            if (hiddenCategories.isEmpty()) {
                preferences.remove(Keys.HIDDEN_CATEGORIES)
            } else {
                preferences[Keys.HIDDEN_CATEGORIES] = hiddenCategories.joinToString(SEPARATOR)
            }
        }
    }

    private object Keys {
        val PINNED_CATEGORIES = stringPreferencesKey("pinned_categories")
        val HIDDEN_CATEGORIES = stringPreferencesKey("hidden_categories")
    }

    private companion object {
        const val SEPARATOR = "||"
    }
}
