package com.example.notavia.checklist

import com.example.notavia.data.ChecklistItem

data class ChecklistItemUiState(
    val index: Int,
    val key: Long,
    val item: ChecklistItem,
    val isSelected: Boolean,
)

data class ChecklistUiState(
    val incompleteItems: List<ChecklistItemUiState> = emptyList(),
    val completedItems: List<ChecklistItemUiState> = emptyList(),
    val size: Int = 0,
    val selectedCount: Int = 0,
) {
    val hasSelection: Boolean
        get() = selectedCount > 0
}
