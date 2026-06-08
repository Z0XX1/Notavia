package com.example.notavia.main

import com.example.notavia.FakeCategorySettings
import com.example.notavia.FakeNotesRepository
import com.example.notavia.MainDispatcherRule
import com.example.notavia.data.Note
import com.example.notavia.data.NoteCategories
import com.example.notavia.data.NotePriority
import com.example.notavia.data.NoteType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun observesNotesFlowAndSwitchesSections() = runTest {
        val repository = FakeNotesRepository()
        val viewModel = MainViewModel(repository, FakeCategorySettings())
        val note = testNote(id = 1, title = "Note", type = NoteType.NOTE)
        val checklist = testNote(id = 2, title = "Checklist", type = NoteType.CHECKLIST)

        repository.emit(listOf(note, checklist))

        assertEquals(listOf(note), viewModel.uiState.value.visibleNotes)

        viewModel.setSection(MainSection.CHECKLISTS)

        assertEquals(listOf(checklist), viewModel.uiState.value.visibleNotes)
    }

    @Test
    fun categoryFilterSupportsStandardIdsAndEnglishCustomCategories() = runTest {
        val repository = FakeNotesRepository()
        val viewModel = MainViewModel(repository, FakeCategorySettings())
        val personalNote = testNote(
            id = 1,
            title = "Personal",
            category = NoteCategories.PERSONAL,
        )
        val healthCategory = NoteCategories.normalize("health")
        val healthNote = testNote(
            id = 2,
            title = "Health",
            category = healthCategory,
        )
        repository.emit(listOf(personalNote, healthNote))

        viewModel.toggleCategoryFilter(healthCategory)

        assertEquals(listOf(healthNote), viewModel.uiState.value.visibleNotes)
        assertTrue(viewModel.availableCategoryFilters().contains(healthCategory))
        assertTrue(viewModel.availableCategoryFilters().contains(NoteCategories.PERSONAL))
    }

    @Test
    fun deletingSelectedNotesReliesOnReactiveFlowUpdate() = runTest {
        val repository = FakeNotesRepository(
            listOf(testNote(id = 1, title = "Alpha")),
        )
        val viewModel = MainViewModel(repository, FakeCategorySettings())

        viewModel.enterNoteSelectionMode(1)
        viewModel.deleteSelectedNotes()

        assertTrue(viewModel.uiState.value.visibleNotes.isEmpty())
        assertFalse(viewModel.uiState.value.isSelectionMode)
    }

    @Test
    fun sortingKeepsPinnedNotesBeforeCurrentSortOrder() = runTest {
        val repository = FakeNotesRepository()
        val viewModel = MainViewModel(repository, FakeCategorySettings())
        val oldest = testNote(id = 1, title = "Oldest", createdAt = 10, updatedAt = 10)
        val middle = testNote(id = 2, title = "Middle", createdAt = 20, updatedAt = 20)
        val pinnedNewest = testNote(
            id = 3,
            title = "Pinned newest",
            createdAt = 30,
            updatedAt = 30,
            isPinned = true,
        )
        repository.emit(listOf(middle, pinnedNewest, oldest))

        viewModel.setSortOption(SortGroup.CREATED, NoteSortOption.CREATED_OLDEST)

        assertEquals(
            listOf(pinnedNewest, oldest, middle),
            viewModel.uiState.value.visibleNotes,
        )
    }

    @Test
    fun priorityAndDeadlineFiltersCanBeCombinedAndCleared() = runTest {
        val repository = FakeNotesRepository()
        val viewModel = MainViewModel(repository, FakeCategorySettings())
        val highWithDeadline = testNote(
            id = 1,
            title = "High with deadline",
            priority = NotePriority.HIGH,
            deadlineAt = 100,
        )
        val highWithoutDeadline = testNote(
            id = 2,
            title = "High without deadline",
            priority = NotePriority.HIGH,
        )
        val lowWithDeadline = testNote(
            id = 3,
            title = "Low with deadline",
            priority = NotePriority.LOW,
            deadlineAt = 200,
        )
        repository.emit(listOf(highWithDeadline, highWithoutDeadline, lowWithDeadline))

        viewModel.togglePriorityFilter(NotePriority.HIGH)
        viewModel.toggleDeadlineFilter(DeadlineFilter.WITH_DEADLINE)

        assertEquals(listOf(highWithDeadline), viewModel.uiState.value.visibleNotes)

        viewModel.clearDeadlineFilters()

        assertEquals(
            listOf(highWithoutDeadline, highWithDeadline),
            viewModel.uiState.value.visibleNotes,
        )
    }

    @Test
    fun deadlineFiltersSeparateOverdueAndActiveNotes() = runTest {
        val repository = FakeNotesRepository()
        val viewModel = MainViewModel(repository, FakeCategorySettings())
        val now = System.currentTimeMillis()
        val overdue = testNote(
            id = 1,
            title = "Overdue",
            deadlineAt = now - 60_000,
        )
        val active = testNote(
            id = 2,
            title = "Active",
            deadlineAt = now + 86_400_000,
        )
        val withoutDeadline = testNote(
            id = 3,
            title = "Without deadline",
        )
        repository.emit(listOf(overdue, active, withoutDeadline))

        viewModel.toggleDeadlineFilter(DeadlineFilter.OVERDUE)

        assertEquals(listOf(overdue), viewModel.uiState.value.visibleNotes)

        viewModel.clearDeadlineFilters()
        viewModel.toggleDeadlineFilter(DeadlineFilter.ACTIVE)

        assertEquals(listOf(active), viewModel.uiState.value.visibleNotes)
    }

    @Test
    fun pinningSelectedNotesUpdatesRepositoryAndLeavesSelectionMode() = runTest {
        val first = testNote(id = 1, title = "First")
        val second = testNote(id = 2, title = "Second")
        val repository = FakeNotesRepository(listOf(first, second))
        val viewModel = MainViewModel(repository, FakeCategorySettings())

        viewModel.enterNoteSelectionMode(first.id)
        viewModel.toggleNoteSelection(second.id)
        viewModel.pinOrUnpinSelectedNotes()

        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.allNotes.all { it.isPinned })

        viewModel.enterNoteSelectionMode(first.id)
        viewModel.toggleNoteSelection(second.id)
        viewModel.pinOrUnpinSelectedNotes()

        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.allNotes.none { it.isPinned })
    }

    @Test
    fun deletingSelectedCategoryRemovesItFromNotesAndCategorySettings() = runTest {
        val healthCategory = NoteCategories.normalize("health")
        val personalHealthNote = testNote(
            id = 1,
            title = "Personal health",
            category = NoteCategories.serialize(listOf(NoteCategories.PERSONAL, healthCategory)),
        )
        val onlyHealthNote = testNote(
            id = 2,
            title = "Only health",
            category = healthCategory,
        )
        val repository = FakeNotesRepository(listOf(personalHealthNote, onlyHealthNote))
        val viewModel = MainViewModel(
            repository,
            FakeCategorySettings(
                pinned = setOf(healthCategory),
                custom = setOf(healthCategory),
            ),
        )
        viewModel.toggleCategoryFilter(healthCategory)

        viewModel.enterCategorySelectionMode(healthCategory)
        viewModel.deleteSelectedCategories()

        val updatedPersonalNote = viewModel.uiState.value.allNotes.first { it.id == personalHealthNote.id }
        val updatedDefaultNote = viewModel.uiState.value.allNotes.first { it.id == onlyHealthNote.id }
        assertEquals(NoteCategories.PERSONAL, updatedPersonalNote.category)
        assertEquals(NoteCategories.DEFAULT, updatedDefaultNote.category)
        assertFalse(viewModel.uiState.value.pinnedCategoryNames.contains(healthCategory))
        assertFalse(viewModel.uiState.value.customCategoryNames.contains(healthCategory))
        assertTrue(viewModel.uiState.value.hiddenCategoryNames.contains(healthCategory))
        assertFalse(viewModel.uiState.value.selectedCategoryFilters.contains(healthCategory))
        assertFalse(viewModel.uiState.value.isSelectionMode)
    }

    private fun testNote(
        id: Long,
        title: String,
        category: String = NoteCategories.DEFAULT,
        priority: NotePriority = NotePriority.NONE,
        type: NoteType = NoteType.NOTE,
        deadlineAt: Long? = null,
        createdAt: Long = id,
        updatedAt: Long = id,
        isPinned: Boolean = false,
    ): Note {
        return Note(
            id = id,
            title = title,
            content = title,
            category = category,
            priority = priority.storageValue,
            type = type.storageValue,
            deadlineAt = deadlineAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isPinned = isPinned,
        )
    }
}
