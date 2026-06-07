package com.example.notavia.editor

import com.example.notavia.data.NoteCategories

class EditorCategoryState {
    private val selectedCategories = linkedSetOf(NoteCategories.DEFAULT)
    private val customCategories = linkedSetOf<String>()
    private val hiddenCategories = linkedSetOf<String>()

    val selected: Set<String>
        get() = selectedCategories

    val custom: Set<String>
        get() = customCategories

    val hidden: Set<String>
        get() = hiddenCategories

    fun resetSelectionToDefault() {
        selectedCategories.clear()
        selectedCategories.add(NoteCategories.DEFAULT)
    }

    fun selectStoredCategories(storedCategory: String) {
        selectedCategories.clear()
        selectedCategories.addAll(
            NoteCategories.parse(storedCategory)
                .map { NoteCategories.normalize(it) }
                .filterNot { it == NoteCategories.ALL },
        )
        if (selectedCategories.isEmpty()) {
            selectedCategories.add(NoteCategories.DEFAULT)
        }
    }

    fun setHiddenCategories(categories: Collection<String>) {
        hiddenCategories.clear()
        hiddenCategories.addAll(categories.map { NoteCategories.normalize(it) })
    }

    fun mergeCustomCategories(categories: Collection<String>): Boolean {
        var changed = false
        categories.forEach { category ->
            val normalizedCategory = NoteCategories.normalize(category)
            if (
                normalizedCategory != NoteCategories.ALL &&
                !NoteCategories.isStandard(normalizedCategory)
            ) {
                changed = customCategories.add(normalizedCategory) || changed
            }
        }
        return changed
    }

    fun addCustomCategory(category: String): Boolean {
        val normalizedCategory = NoteCategories.normalize(category)
        if (
            normalizedCategory == NoteCategories.ALL ||
            NoteCategories.isStandard(normalizedCategory)
        ) {
            return false
        }
        return customCategories.add(normalizedCategory)
    }

    fun restoreHiddenCategory(category: String): Boolean {
        return hiddenCategories.remove(NoteCategories.normalize(category))
    }

    fun toggle(category: String) {
        val normalizedCategory = NoteCategories.normalize(category)
        if (normalizedCategory == NoteCategories.DEFAULT) {
            resetSelectionToDefault()
            return
        }
        if (normalizedCategory == NoteCategories.ALL) return

        selectedCategories.remove(NoteCategories.DEFAULT)
        if (selectedCategories.contains(normalizedCategory)) {
            selectedCategories.remove(normalizedCategory)
        } else {
            selectedCategories.add(normalizedCategory)
        }
        if (selectedCategories.isEmpty()) {
            selectedCategories.add(NoteCategories.DEFAULT)
        }
    }

    fun select(category: String) {
        val normalizedCategory = NoteCategories.normalize(category)
        if (normalizedCategory == NoteCategories.DEFAULT) {
            resetSelectionToDefault()
            return
        }
        if (normalizedCategory == NoteCategories.ALL) return

        selectedCategories.remove(NoteCategories.DEFAULT)
        selectedCategories.add(normalizedCategory)
    }

    fun visibleCategories(): List<String> {
        return (NoteCategories.STANDARD + customCategories + selectedCategories)
            .map { NoteCategories.normalize(it) }
            .filterNot { hiddenCategories.contains(it) && !selectedCategories.contains(it) }
            .distinct()
    }

    fun includeSelectedCustomCategories(): Boolean {
        return mergeCustomCategories(customSelectedCategories())
    }

    fun customSelectedCategories(): List<String> {
        return selectedCategories
            .map { NoteCategories.normalize(it) }
            .filterNot { NoteCategories.isStandard(it) }
            .filterNot { it == NoteCategories.ALL }
    }

    fun isSelected(category: String): Boolean {
        return selectedCategories.contains(NoteCategories.normalize(category))
    }

    fun serializeSelected(): String {
        return NoteCategories.serialize(selectedCategories)
    }
}
