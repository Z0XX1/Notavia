package com.example.notavia.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteCategoriesTest {
    @Test
    fun normalizesLegacyRussianAndEnglishStandardNamesToStorageIds() {
        assertEquals(
            NoteCategories.DEFAULT,
            NoteCategories.normalize("\u0411\u0435\u0437 \u043a\u0430\u0442\u0435\u0433\u043e\u0440\u0438\u0438"),
        )
        assertEquals(NoteCategories.PERSONAL, NoteCategories.normalize("Personal"))
        assertEquals(NoteCategories.STUDY, NoteCategories.normalize("\u0423\u0447\u0451\u0431\u0430"))
        assertEquals(NoteCategories.WORK, NoteCategories.normalize("Work"))
    }

    @Test
    fun keepsEnglishCustomCategoryAsCustomDisplayValue() {
        val category = NoteCategories.normalize("health")

        assertEquals("Health", category)
        assertFalse(NoteCategories.isStandard(category))
    }

    @Test
    fun serializesStandardIdsAndCustomCategoriesTogether() {
        val serialized = NoteCategories.serialize(
            listOf(NoteCategories.PERSONAL, "health"),
        )

        assertTrue(NoteCategories.contains(serialized, NoteCategories.PERSONAL))
        assertTrue(NoteCategories.contains(serialized, "Health"))
        assertEquals("personal||Health", serialized)
    }
}
