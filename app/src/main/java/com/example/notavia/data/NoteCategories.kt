package com.example.notavia.data

import java.util.Locale


object NoteCategories {
    const val DEFAULT = "default"
    const val ALL = "all"
    const val PERSONAL = "personal"
    const val STUDY = "study"
    const val WORK = "work"
    const val IDEAS = "ideas"
    private const val SEPARATOR = "||"

    val STANDARD = listOf(
        DEFAULT,
        PERSONAL,
        STUDY,
        WORK,
        IDEAS,
    )


    fun normalize(category: String): String {
        val trimmedCategory = category.trim()
        if (trimmedCategory.isBlank()) return DEFAULT

        return storageValueFor(trimmedCategory) ?: formatCustomCategory(trimmedCategory)
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


    fun availableFrom(notes: List<Note>): List<String> {
        return (STANDARD + notes.flatMap { parse(it.category) })
            .filterNot { it == ALL }
            .distinct()
    }


    fun availableFrom(notes: List<Note>, pinnedCategories: Set<String>): List<String> {
        return availableFrom(notes, pinnedCategories, emptySet())
    }


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

    private fun storageValueFor(category: String): String? {
        val normalizedInput = category.trim().lowercase(Locale.ROOT)
        return when (normalizedInput) {
            DEFAULT,
            "\u0431\u0435\u0437 \u043a\u0430\u0442\u0435\u0433\u043e\u0440\u0438\u0438",
            "no category",
            -> DEFAULT
            ALL,
            "\u0432\u0441\u0435",
            "all",
            -> ALL
            PERSONAL,
            "\u043b\u0438\u0447\u043d\u043e\u0435",
            "personal",
            -> PERSONAL
            STUDY,
            "\u0443\u0447\u0435\u0431\u0430",
            "\u0443\u0447\u0451\u0431\u0430",
            "study",
            -> STUDY
            WORK,
            "\u0440\u0430\u0431\u043e\u0442\u0430",
            "work",
            -> WORK
            IDEAS,
            "\u0438\u0434\u0435\u0438",
            "ideas",
            -> IDEAS
            else -> null
        }
    }
}
