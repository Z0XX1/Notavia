package com.example.notavia.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.notavia.data.Note
import com.example.notavia.data.NoteCategories
import com.example.notavia.data.NotePriority
import com.example.notavia.data.NoteRepository
import com.example.notavia.data.NoteType
import com.example.notavia.settings.CategoryPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MainSection(val noteType: NoteType) {
    NOTES(NoteType.NOTE),
    CHECKLISTS(NoteType.CHECKLIST),
}

enum class SelectionMode {
    NONE,
    NOTES,
    CATEGORIES,
}

enum class DeadlineFilter {
    WITH_DEADLINE,
    WITHOUT_DEADLINE,
}

enum class SortGroup {
    CREATED,
    PRIORITY,
    DEADLINE,
}

enum class NoteSortOption(val group: SortGroup) {
    CREATED_NEWEST(SortGroup.CREATED),
    CREATED_OLDEST(SortGroup.CREATED),
    PRIORITY_HIGH_FIRST(SortGroup.PRIORITY),
    PRIORITY_LOW_FIRST(SortGroup.PRIORITY),
    DEADLINE_NEAREST(SortGroup.DEADLINE),
    DEADLINE_FARTHEST(SortGroup.DEADLINE),
}

data class MainUiState(
    val currentSection: MainSection = MainSection.NOTES,
    val allNotes: List<Note> = emptyList(),
    val visibleNotes: List<Note> = emptyList(),
    val selectedNoteIds: Set<Long> = emptySet(),
    val selectedCategoryNames: Set<String> = emptySet(),
    val pinnedCategoryNames: Set<String> = emptySet(),
    val hiddenCategoryNames: Set<String> = emptySet(),
    val customCategoryNames: Set<String> = emptySet(),
    val selectionMode: SelectionMode = SelectionMode.NONE,
    val searchQuery: String = "",
    val selectedCategoryFilters: Set<String> = emptySet(),
    val selectedPriorityFilters: Set<NotePriority> = emptySet(),
    val selectedDeadlineFilters: Set<DeadlineFilter> = emptySet(),
    val selectedSortOptions: Map<SortGroup, NoteSortOption> = emptyMap(),
) {
    val isSelectionMode: Boolean
        get() = selectionMode != SelectionMode.NONE

    val isNoteSelectionMode: Boolean
        get() = selectionMode == SelectionMode.NOTES

    val isCategorySelectionMode: Boolean
        get() = selectionMode == SelectionMode.CATEGORIES

    val hasActiveFilters: Boolean
        get() = selectedCategoryFilters.isNotEmpty() ||
            selectedPriorityFilters.isNotEmpty() ||
            selectedDeadlineFilters.isNotEmpty()
}

class MainViewModel(
    private val repository: NoteRepository,
    private val categoryPreferences: CategoryPreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        observeCategoryPreferences()
    }

    fun loadNotes() {
        viewModelScope.launch {
            val notes = repository.getAllNotes()
            updateAndFilter { state ->
                state.copy(allNotes = notes)
            }
        }
    }

    fun setSection(section: MainSection) {
        updateAndFilter { state ->
            if (state.currentSection == section) {
                state
            } else {
                state.copy(
                    currentSection = section,
                    selectionMode = SelectionMode.NONE,
                    selectedNoteIds = emptySet(),
                    selectedCategoryNames = emptySet(),
                    searchQuery = "",
                    selectedCategoryFilters = emptySet(),
                    selectedPriorityFilters = emptySet(),
                    selectedDeadlineFilters = emptySet(),
                    selectedSortOptions = emptyMap(),
                )
            }
        }
    }

    fun setSearchQuery(query: String) {
        updateAndFilter { state ->
            state.copy(searchQuery = query)
        }
    }

    fun addCustomCategory(rawCategory: String) {
        val category = NoteCategories.normalize(rawCategory)
        if (category.isBlank() || NoteCategories.isReserved(category)) return

        val updatedState = _uiState.value.copy(
            customCategoryNames = if (NoteCategories.isStandard(category)) {
                _uiState.value.customCategoryNames
            } else {
                _uiState.value.customCategoryNames + category
            },
            hiddenCategoryNames = _uiState.value.hiddenCategoryNames - category,
        )
        updateAndFilter { updatedState }

        viewModelScope.launch {
            categoryPreferences.setCustomCategories(updatedState.customCategoryNames)
            categoryPreferences.setHiddenCategories(updatedState.hiddenCategoryNames)
        }
    }

    fun togglePriorityFilter(priority: NotePriority) {
        updateAndFilter { state ->
            state.copy(
                selectedPriorityFilters = state.selectedPriorityFilters.toggle(priority),
            )
        }
    }

    fun clearPriorityFilters() {
        updateAndFilter { state ->
            state.copy(selectedPriorityFilters = emptySet())
        }
    }

    fun toggleDeadlineFilter(deadlineFilter: DeadlineFilter) {
        updateAndFilter { state ->
            state.copy(
                selectedDeadlineFilters = state.selectedDeadlineFilters.toggle(deadlineFilter),
            )
        }
    }

    fun clearDeadlineFilters() {
        updateAndFilter { state ->
            state.copy(selectedDeadlineFilters = emptySet())
        }
    }

    fun toggleCategoryFilter(category: String) {
        val normalizedCategory = NoteCategories.normalize(category)
        updateAndFilter { state ->
            state.copy(
                selectedCategoryFilters = state.selectedCategoryFilters.toggle(normalizedCategory),
            )
        }
    }

    fun selectSingleCategoryFilter(category: String?) {
        updateAndFilter { state ->
            state.copy(
                selectedCategoryFilters = category
                    ?.let { setOf(NoteCategories.normalize(it)) }
                    .orEmpty(),
            )
        }
    }

    fun clearCategoryFilters() {
        updateAndFilter { state ->
            state.copy(selectedCategoryFilters = emptySet())
        }
    }

    fun clearAllFilters() {
        updateAndFilter { state ->
            state.copy(
                selectedPriorityFilters = emptySet(),
                selectedDeadlineFilters = emptySet(),
                selectedCategoryFilters = emptySet(),
            )
        }
    }

    fun setSortOption(group: SortGroup, sortOption: NoteSortOption?) {
        updateAndFilter { state ->
            val sortOptions = state.selectedSortOptions.toMutableMap()
            if (sortOption == null) {
                sortOptions.remove(group)
            } else {
                sortOptions[group] = sortOption
            }
            state.copy(selectedSortOptions = sortOptions)
        }
    }

    fun clearSortOptions() {
        updateAndFilter { state ->
            state.copy(selectedSortOptions = emptyMap())
        }
    }

    fun enterNoteSelectionMode(noteId: Long) {
        _uiState.update { state ->
            state.copy(
                selectionMode = SelectionMode.NOTES,
                selectedNoteIds = setOf(noteId),
                selectedCategoryNames = emptySet(),
            )
        }
    }

    fun enterCategorySelectionMode(category: String) {
        val normalizedCategory = NoteCategories.normalize(category)
        if (isProtectedCategory(normalizedCategory)) return

        _uiState.update { state ->
            state.copy(
                selectionMode = SelectionMode.CATEGORIES,
                selectedNoteIds = emptySet(),
                selectedCategoryNames = setOf(normalizedCategory),
            )
        }
    }

    fun exitSelectionMode() {
        _uiState.update { state ->
            state.copy(
                selectionMode = SelectionMode.NONE,
                selectedNoteIds = emptySet(),
                selectedCategoryNames = emptySet(),
            )
        }
    }

    fun toggleNoteSelection(noteId: Long) {
        _uiState.update { state ->
            state.copy(selectedNoteIds = state.selectedNoteIds.toggle(noteId))
        }
    }

    fun toggleCategorySelection(category: String) {
        val normalizedCategory = NoteCategories.normalize(category)
        if (isProtectedCategory(normalizedCategory)) return

        _uiState.update { state ->
            val selectedCategories = state.selectedCategoryNames.toggle(normalizedCategory)
            if (selectedCategories.isEmpty()) {
                state.copy(
                    selectionMode = SelectionMode.NONE,
                    selectedCategoryNames = emptySet(),
                )
            } else {
                state.copy(selectedCategoryNames = selectedCategories)
            }
        }
    }

    fun selectAllVisibleNotes() {
        val state = _uiState.value
        if (state.visibleNotes.isEmpty()) return

        val visibleIds = state.visibleNotes.map { it.id }.toSet()
        val allVisibleSelected = visibleIds.isNotEmpty() && state.selectedNoteIds.containsAll(visibleIds)
        _uiState.update { currentState ->
            currentState.copy(
                selectedNoteIds = if (allVisibleSelected) emptySet() else visibleIds,
            )
        }
    }

    fun selectAllCategories() {
        val categories = availableCategoryFilters()
            .filterNot(::isProtectedCategory)
        if (categories.isEmpty()) return

        val allCategoriesSelected = _uiState.value.selectedCategoryNames.containsAll(categories)
        _uiState.update { state ->
            state.copy(
                selectedCategoryNames = if (allCategoriesSelected) emptySet() else categories.toSet(),
            )
        }
    }

    fun pinOrUnpinSelectedNotes() {
        val state = _uiState.value
        if (state.selectedNoteIds.isEmpty()) return

        val selectedNotes = state.allNotes.filter { state.selectedNoteIds.contains(it.id) }
        val shouldPin = selectedNotes.any { !it.isPinned }
        val selectedIds = state.selectedNoteIds.toList()

        viewModelScope.launch {
            repository.updatePinnedState(selectedIds, shouldPin)
            exitSelectionMode()
            loadNotes()
        }
    }

    fun pinOrUnpinSelectedCategories() {
        val state = _uiState.value
        val categoriesToPin = state.selectedCategoryNames
            .filterNot(::isProtectedCategory)
            .toSet()
        if (categoriesToPin.isEmpty()) return

        val shouldPin = categoriesToPin.any { !state.pinnedCategoryNames.contains(it) }
        val pinnedCategories = if (shouldPin) {
            state.pinnedCategoryNames + categoriesToPin
        } else {
            state.pinnedCategoryNames - categoriesToPin
        }

        _uiState.update { currentState ->
            currentState.copy(pinnedCategoryNames = pinnedCategories)
        }
        viewModelScope.launch {
            categoryPreferences.setPinnedCategories(pinnedCategories)
            exitSelectionMode()
        }
    }

    fun deleteSelectedNotes() {
        val selectedIds = _uiState.value.selectedNoteIds.toList()
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            repository.deleteNotes(selectedIds)
            exitSelectionMode()
            loadNotes()
        }
    }

    fun deleteSelectedCategories() {
        val state = _uiState.value
        val categoriesToDelete = state.selectedCategoryNames
            .filterNot(::isProtectedCategory)
            .toSet()
        if (categoriesToDelete.isEmpty()) return

        viewModelScope.launch {
            state.allNotes.forEach { note ->
                val updatedCategories = NoteCategories.parse(note.category)
                    .filterNot { categoriesToDelete.contains(it) }
                repository.saveNote(
                    note.copy(
                        category = NoteCategories.serialize(updatedCategories),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }

            val pinnedCategories = state.pinnedCategoryNames - categoriesToDelete
            val customCategories = state.customCategoryNames - categoriesToDelete
            val hiddenCategories = state.hiddenCategoryNames + categoriesToDelete
            val categoryFilters = state.selectedCategoryFilters - categoriesToDelete

            categoryPreferences.setPinnedCategories(pinnedCategories)
            categoryPreferences.setCustomCategories(customCategories)
            categoryPreferences.setHiddenCategories(hiddenCategories)
            updateAndFilter {
                it.copy(
                    pinnedCategoryNames = pinnedCategories,
                    customCategoryNames = customCategories,
                    hiddenCategoryNames = hiddenCategories,
                    selectedCategoryFilters = categoryFilters,
                    selectionMode = SelectionMode.NONE,
                    selectedCategoryNames = emptySet(),
                )
            }
            loadNotes()
        }
    }

    fun shouldUnpinSelection(): Boolean {
        val state = _uiState.value
        return when (state.selectionMode) {
            SelectionMode.NOTES -> {
                val selectedNotes = state.allNotes.filter { state.selectedNoteIds.contains(it.id) }
                selectedNotes.isNotEmpty() && selectedNotes.all { it.isPinned }
            }
            SelectionMode.CATEGORIES -> {
                state.selectedCategoryNames.isNotEmpty() &&
                    state.selectedCategoryNames.all { state.pinnedCategoryNames.contains(it) }
            }
            SelectionMode.NONE -> false
        }
    }

    fun availableCategoryFilters(): List<String> {
        val state = _uiState.value
        val sectionNotes = state.allNotes.filter { note ->
            NoteType.fromStorage(note.type) == state.currentSection.noteType
        }
        return NoteCategories.availableFrom(
            sectionNotes,
            state.pinnedCategoryNames,
            state.customCategoryNames,
        ).filterNot { category ->
            category != NoteCategories.DEFAULT && state.hiddenCategoryNames.contains(category)
        }
    }

    fun isProtectedCategory(category: String?): Boolean {
        return category == null || NoteCategories.isReserved(category)
    }

    private fun observeCategoryPreferences() {
        viewModelScope.launch {
            categoryPreferences.pinnedCategoriesFlow.collect { categories ->
                _uiState.update { state ->
                    state.copy(pinnedCategoryNames = categories)
                }
            }
        }
        viewModelScope.launch {
            categoryPreferences.hiddenCategoriesFlow.collect { categories ->
                updateAndFilter { state ->
                    state.copy(
                        hiddenCategoryNames = categories,
                        selectedCategoryFilters = state.selectedCategoryFilters - categories,
                    )
                }
            }
        }
        viewModelScope.launch {
            categoryPreferences.customCategoriesFlow.collect { categories ->
                updateAndFilter { state ->
                    state.copy(customCategoryNames = categories)
                }
            }
        }
    }

    private fun updateAndFilter(updateState: (MainUiState) -> MainUiState) {
        _uiState.update { state ->
            updateState(state).withVisibleNotes()
        }
    }

    private fun MainUiState.withVisibleNotes(): MainUiState {
        val existingFilters = existingCategoryFilters()
        val stateWithFilters = copy(selectedCategoryFilters = existingFilters)
        val visibleNotes = stateWithFilters.allNotes.filter { note ->
            val matchesSection = NoteType.fromStorage(note.type) == stateWithFilters.currentSection.noteType
            val matchesSearch = stateWithFilters.searchQuery.isBlank() ||
                note.title.contains(stateWithFilters.searchQuery, ignoreCase = true)
            val matchesCategory = stateWithFilters.selectedCategoryFilters.isEmpty() ||
                stateWithFilters.selectedCategoryFilters.any { category ->
                    NoteCategories.contains(note.category, category)
                }
            val matchesPriority = stateWithFilters.selectedPriorityFilters.isEmpty() ||
                NotePriority.fromStorage(note.priority) in stateWithFilters.selectedPriorityFilters
            val matchesDeadline = stateWithFilters.selectedDeadlineFilters.isEmpty() ||
                stateWithFilters.selectedDeadlineFilters.any { deadlineFilter ->
                    when (deadlineFilter) {
                        DeadlineFilter.WITH_DEADLINE -> note.deadlineAt != null
                        DeadlineFilter.WITHOUT_DEADLINE -> note.deadlineAt == null
                    }
                }

            matchesSection && matchesSearch && matchesCategory && matchesPriority && matchesDeadline
        }.sortForState(stateWithFilters)
        val existingNoteIds = stateWithFilters.allNotes.map { it.id }.toSet()

        return stateWithFilters.copy(
            visibleNotes = visibleNotes,
            selectedNoteIds = stateWithFilters.selectedNoteIds.intersect(existingNoteIds),
        )
    }

    private fun MainUiState.existingCategoryFilters(): Set<String> {
        if (selectedCategoryFilters.isEmpty()) return emptySet()

        return selectedCategoryFilters.filterTo(linkedSetOf()) { category ->
            val normalizedCategory = NoteCategories.normalize(category)
            val isStandardCategory = NoteCategories.isStandard(normalizedCategory)
            val isCustomCategory = customCategoryNames.contains(normalizedCategory)
            val hasNotesInCategory = allNotes.any { note ->
                NoteCategories.contains(note.category, normalizedCategory)
            }

            isStandardCategory || isCustomCategory || hasNotesInCategory
        }
    }

    private fun List<Note>.sortForState(state: MainUiState): List<Note> {
        return sortedWith { first, second ->
            comparePinned(first, second)
                .takeIf { it != 0 }
                ?: compareByCurrentSort(first, second, state.selectedSortOptions)
        }
    }

    private fun comparePinned(first: Note, second: Note): Int {
        return when {
            first.isPinned == second.isPinned -> 0
            first.isPinned -> -1
            else -> 1
        }
    }

    private fun compareByCurrentSort(
        first: Note,
        second: Note,
        selectedSortOptions: Map<SortGroup, NoteSortOption>,
    ): Int {
        val sortOptions = selectedSortOptions.values.toList()
            .ifEmpty { listOf(NoteSortOption.CREATED_NEWEST) }

        sortOptions.forEach { sortOption ->
            val optionCompare = compareBySortOption(first, second, sortOption)
            if (optionCompare != 0) {
                return optionCompare
            }
        }
        return second.updatedAt.compareTo(first.updatedAt)
    }

    private fun compareBySortOption(first: Note, second: Note, sortOption: NoteSortOption): Int {
        return when (sortOption) {
            NoteSortOption.CREATED_NEWEST -> second.createdAt.compareTo(first.createdAt)
            NoteSortOption.CREATED_OLDEST -> first.createdAt.compareTo(second.createdAt)
            NoteSortOption.PRIORITY_HIGH_FIRST -> compareValues(
                prioritySortRank(first, lowPriorityFirst = false),
                prioritySortRank(second, lowPriorityFirst = false),
            )
            NoteSortOption.PRIORITY_LOW_FIRST -> compareValues(
                prioritySortRank(first, lowPriorityFirst = true),
                prioritySortRank(second, lowPriorityFirst = true),
            )
            NoteSortOption.DEADLINE_NEAREST -> compareDeadlines(first, second, nearestFirst = true)
            NoteSortOption.DEADLINE_FARTHEST -> compareDeadlines(first, second, nearestFirst = false)
        }
    }

    private fun prioritySortRank(note: Note, lowPriorityFirst: Boolean): Int {
        return when (NotePriority.fromStorage(note.priority)) {
            NotePriority.HIGH -> if (lowPriorityFirst) 2 else 0
            NotePriority.MEDIUM -> 1
            NotePriority.LOW -> if (lowPriorityFirst) 0 else 2
            NotePriority.NONE -> 3
        }
    }

    private fun compareDeadlines(first: Note, second: Note, nearestFirst: Boolean): Int {
        val firstDeadline = first.deadlineAt
        val secondDeadline = second.deadlineAt
        return when {
            firstDeadline == null && secondDeadline == null -> 0
            firstDeadline == null -> 1
            secondDeadline == null -> -1
            nearestFirst -> firstDeadline.compareTo(secondDeadline)
            else -> secondDeadline.compareTo(firstDeadline)
        }
    }

    private fun <T> Set<T>.toggle(value: T): Set<T> {
        return if (contains(value)) this - value else this + value
    }

    class Factory(
        private val repository: NoteRepository,
        private val categoryPreferences: CategoryPreferences,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(repository, categoryPreferences) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
