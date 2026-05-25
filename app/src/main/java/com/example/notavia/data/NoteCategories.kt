package com.example.notavia.data

import java.util.Locale

object NoteCategories {
    const val DEFAULT = "Без категории"
    private const val SEPARATOR = "||"

    val STANDARD = listOf(
        DEFAULT,
        "Личное",
        "Учёба",
        "Работа",
        "Идеи",
    )

    fun normalize(category: String): String {
        val trimmedCategory = category.trim()
        if (trimmedCategory.isBlank()) return DEFAULT

        return STANDARD.firstOrNull { it.equals(trimmedCategory, ignoreCase = true) }
            ?: trimmedCategory.lowercase(Locale.ROOT)
    }

    fun parse(categories: String): List<String> {
        return categories
            .split(SEPARATOR)
            .map { normalize(it) }
            .distinct()
            .ifEmpty { listOf(DEFAULT) }
    }

    fun serialize(categories: Collection<String>): String {
        val normalized = categories
            .map { normalize(it) }
            .distinct()
        val categoriesToSave = normalized
            .filterNot { it == DEFAULT }
            .ifEmpty { listOf(DEFAULT) }

        return categoriesToSave.joinToString(SEPARATOR)
    }

    fun display(categories: String): String {
        return parse(categories).joinToString(", ")
    }

    fun contains(categories: String, category: String): Boolean {
        return parse(categories).contains(normalize(category))
    }

    fun isStandard(category: String): Boolean {
        return STANDARD.contains(normalize(category))
    }

    fun availableFrom(notes: List<Note>): List<String> {
        return (STANDARD + notes.flatMap { parse(it.category) })
            .distinct()
    }

    fun availableFrom(notes: List<Note>, pinnedCategories: Set<String>): List<String> {
        val categories = availableFrom(notes)
        val normalizedPinnedCategories = pinnedCategories.map { normalize(it) }.toSet()
        val pinned = categories.filter { normalizedPinnedCategories.contains(it) }
        val regular = categories.filterNot { normalizedPinnedCategories.contains(it) }

        return pinned + regular
    }
}
