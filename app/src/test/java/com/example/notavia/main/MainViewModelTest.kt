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

    private fun testNote(
        id: Long,
        title: String,
        category: String = NoteCategories.DEFAULT,
        priority: NotePriority = NotePriority.NONE,
        type: NoteType = NoteType.NOTE,
    ): Note {
        return Note(
            id = id,
            title = title,
            content = title,
            category = category,
            priority = priority.storageValue,
            type = type.storageValue,
            createdAt = id,
            updatedAt = id,
        )
    }
}
