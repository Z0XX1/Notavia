package com.example.notavia.editor

import com.example.notavia.data.NoteCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorCategoryStateTest {
    @Test
    fun hiddenCustomCategoryIsVisibleWhenSelected() {
        val state = EditorCategoryState()
        val travelCategory = NoteCategories.normalize("travel")

        state.mergeCustomCategories(listOf(travelCategory))
        state.setHiddenCategories(listOf(travelCategory))

        assertFalse(state.visibleCategories().contains(travelCategory))

        state.select(travelCategory)

        assertTrue(state.visibleCategories().contains(travelCategory))
        assertEquals(travelCategory, state.serializeSelected())
    }

    @Test
    fun removingLastSelectedCustomCategoryFallsBackToDefault() {
        val state = EditorCategoryState()
        val travelCategory = NoteCategories.normalize("travel")

        state.select(travelCategory)
        state.toggle(travelCategory)

        assertEquals(NoteCategories.DEFAULT, state.serializeSelected())
    }

    @Test
    fun storedCustomCategoryCanBeRestoredIntoVisibleOptions() {
        val state = EditorCategoryState()
        val travelCategory = NoteCategories.normalize("travel")

        state.selectStoredCategories(travelCategory)

        assertTrue(state.includeSelectedCustomCategories())
        assertTrue(state.visibleCategories().contains(travelCategory))
    }
}
