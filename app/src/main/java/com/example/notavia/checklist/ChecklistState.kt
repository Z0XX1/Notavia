package com.example.notavia.checklist

import com.example.notavia.data.ChecklistContent
import com.example.notavia.data.ChecklistItem

class ChecklistState {
    private val mutableItems = mutableListOf<ChecklistItem>()
    private val itemKeys = mutableListOf<Long>()
    private val selectedIndexes = linkedSetOf<Int>()
    private var nextItemKey = 0L

    val items: List<ChecklistItem>
        get() = mutableItems

    val size: Int
        get() = mutableItems.size

    val selectedCount: Int
        get() = selectedIndexes.size

    fun replaceWithContent(content: String) {
        mutableItems.clear()
        mutableItems.addAll(ChecklistContent.parse(content))
        selectedIndexes.clear()
        resetKeys()
    }

    fun serialize(): String {
        return ChecklistContent.serialize(mutableItems)
    }

    fun add(text: String): Boolean {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) return false

        mutableItems.add(ChecklistItem(text = normalizedText))
        itemKeys.add(nextItemKey++)
        return true
    }

    fun removeAt(index: Int): Boolean {
        if (!containsIndex(index)) return false

        mutableItems.removeAt(index)
        itemKeys.removeAt(index)
        selectedIndexes.remove(index)
        remapSelectedIndexesAfterRemove(index)
        return true
    }

    fun removeSelected(): Boolean {
        if (selectedIndexes.isEmpty()) return false

        selectedIndexes.sortedDescending().forEach { index ->
            if (containsIndex(index)) {
                mutableItems.removeAt(index)
                itemKeys.removeAt(index)
            }
        }
        selectedIndexes.clear()
        return true
    }

    fun toggleDone(index: Int): Boolean {
        if (!containsIndex(index)) return false

        val item = mutableItems[index]
        mutableItems[index] = item.copy(isDone = !item.isDone)
        selectedIndexes.clear()
        return true
    }

    fun updateText(index: Int, text: String): Boolean {
        if (!containsIndex(index)) return false

        mutableItems[index] = mutableItems[index].copy(text = text)
        return true
    }

    fun itemAt(index: Int): ChecklistItem? {
        return mutableItems.getOrNull(index)
    }

    fun containsIndex(index: Int): Boolean {
        return index in mutableItems.indices
    }

    fun incompleteItems(): List<IndexedValue<ChecklistItem>> {
        return mutableItems.withIndex().filterNot { it.value.isDone }
    }

    fun completedItems(): List<IndexedValue<ChecklistItem>> {
        return mutableItems.withIndex().filter { it.value.isDone }
    }

    fun resetKeys() {
        itemKeys.clear()
        repeat(mutableItems.size) {
            itemKeys.add(nextItemKey++)
        }
    }

    fun ensureKeys() {
        while (itemKeys.size < mutableItems.size) {
            itemKeys.add(nextItemKey++)
        }
        while (itemKeys.size > mutableItems.size) {
            itemKeys.removeAt(itemKeys.lastIndex)
        }
    }

    fun keyAt(index: Int): Long? {
        return itemKeys.getOrNull(index)
    }

    fun indexForKey(key: Long): Int? {
        val index = itemKeys.indexOf(key)
        return index.takeIf { it in mutableItems.indices }
    }

    fun hasSelection(): Boolean {
        return selectedIndexes.isNotEmpty()
    }

    fun isSelected(index: Int): Boolean {
        return selectedIndexes.contains(index)
    }

    fun enterSelection(index: Int): Boolean {
        if (!containsIndex(index)) return false

        selectedIndexes.add(index)
        return true
    }

    fun toggleSelection(index: Int): Boolean {
        if (!containsIndex(index)) return hasSelection()

        if (selectedIndexes.contains(index)) {
            selectedIndexes.remove(index)
        } else {
            selectedIndexes.add(index)
        }
        return hasSelection()
    }

    fun exitSelection() {
        selectedIndexes.clear()
    }

    fun selectAllOrClear() {
        if (selectedIndexes.size == mutableItems.size) {
            selectedIndexes.clear()
        } else {
            selectedIndexes.clear()
            selectedIndexes.addAll(mutableItems.indices)
        }
    }

    fun moveByKey(itemKey: Long, toIndex: Int): Boolean {
        val fromIndex = itemKeys.indexOf(itemKey)
        if (
            fromIndex == toIndex ||
            !containsIndex(fromIndex) ||
            !containsIndex(toIndex)
        ) {
            return false
        }

        val movedItem = mutableItems.removeAt(fromIndex)
        val movedKey = itemKeys.removeAt(fromIndex)
        mutableItems.add(toIndex, movedItem)
        itemKeys.add(toIndex, movedKey)
        remapSelectedIndexesAfterMove(fromIndex, toIndex)
        return true
    }

    private fun remapSelectedIndexesAfterMove(fromIndex: Int, toIndex: Int) {
        val updatedSelection = selectedIndexes.mapTo(linkedSetOf()) { selectedIndex ->
            when {
                selectedIndex == fromIndex -> toIndex
                fromIndex < toIndex && selectedIndex in (fromIndex + 1)..toIndex -> selectedIndex - 1
                fromIndex > toIndex && selectedIndex in toIndex until fromIndex -> selectedIndex + 1
                else -> selectedIndex
            }
        }
        selectedIndexes.clear()
        selectedIndexes.addAll(updatedSelection)
    }

    private fun remapSelectedIndexesAfterRemove(removedIndex: Int) {
        val updatedSelection = selectedIndexes.mapNotNullTo(linkedSetOf()) { selectedIndex ->
            when {
                selectedIndex == removedIndex -> null
                selectedIndex > removedIndex -> selectedIndex - 1
                else -> selectedIndex
            }
        }
        selectedIndexes.clear()
        selectedIndexes.addAll(updatedSelection)
    }
}
