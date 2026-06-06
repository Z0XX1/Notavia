package com.example.notavia.data

import java.util.Locale

// Логика стандартных, пользовательских и служебных категорий заметок.
object NoteCategories {
    const val DEFAULT = "Без категории"
    const val ALL = "Все"
    private const val SEPARATOR = "||"

    val STANDARD = listOf(
        DEFAULT,
        "Личное",
        "Учёба",
        "Работа",
        "Идеи",
    )

    // Нормализация убирает пробелы, приводит регистр и распознает стандартные категории.
    fun normalize(category: String): String {
        val trimmedCategory = category.trim()
        if (trimmedCategory.isBlank()) return DEFAULT

        return STANDARD.firstOrNull { it.equals(trimmedCategory, ignoreCase = true) }
            ?: formatCustomCategory(trimmedCategory)
    }

    // Разбор строки из базы в список категорий.
    fun parse(categories: String): List<String> {
        return categories
            .split(SEPARATOR)
            .map { normalize(it) }
            .distinct()
            .ifEmpty { listOf(DEFAULT) }
    }

    // Подготовка выбранных категорий к сохранению в одну строку базы.
    fun serialize(categories: Collection<String>): String {
        val normalized = categories
            .map { normalize(it) }
            .distinct()
        val categoriesToSave = normalized
            .filterNot { isReserved(it) }
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

    fun isReserved(category: String): Boolean {
        val normalizedCategory = normalize(category)
        return normalizedCategory == DEFAULT || normalizedCategory == ALL
    }

    // Сбор доступных категорий с учетом заметок, закрепления и пользовательских значений.
    fun availableFrom(notes: List<Note>): List<String> {
        return (STANDARD + notes.flatMap { parse(it.category) })
            .filterNot { it == ALL }
            .distinct()
    }

    // Сбор доступных категорий с учетом заметок, закрепления и пользовательских значений.
    fun availableFrom(notes: List<Note>, pinnedCategories: Set<String>): List<String> {
        return availableFrom(notes, pinnedCategories, emptySet())
    }

    // Сбор доступных категорий с учетом заметок, закрепления и пользовательских значений.
    fun availableFrom(
        notes: List<Note>,
        pinnedCategories: Set<String>,
        customCategories: Set<String>,
    ): List<String> {
        val categories = (availableFrom(notes) + customCategories.map { normalize(it) })
            .filterNot { it == ALL }
            .distinct()
        val normalizedPinnedCategories = pinnedCategories.map { normalize(it) }.toSet()
        val pinned = categories.filter {
            it != DEFAULT && normalizedPinnedCategories.contains(it)
        }
        val regular = categories.filter {
            it != DEFAULT && !normalizedPinnedCategories.contains(it)
        }

        return listOf(DEFAULT) + pinned + regular
    }

    private fun formatCustomCategory(category: String): String {
        val lowercasedCategory = category.lowercase(Locale.ROOT)
        return lowercasedCategory.replaceFirstChar { char ->
            char.titlecase(Locale.ROOT)
        }
    }
}
