package com.example.notavia.editor

import com.example.notavia.FakeNotesRepository
import com.example.notavia.MainDispatcherRule
import com.example.notavia.data.NoteCategories
import com.example.notavia.data.NotePriority
import com.example.notavia.data.NoteType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditNoteViewModelTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun blankNewDraftIsNotSaved() = runTest {
        val repository = FakeNotesRepository()
        val viewModel = EditNoteViewModel(repository)

        viewModel.start(null, NoteType.NOTE)
        viewModel.flushAutoSave(
            NoteDraft(
                title = "",
                content = "",
                category = NoteCategories.DEFAULT,
                priority = NotePriority.NONE.storageValue,
                deadlineAt = null,
                type = NoteType.NOTE.storageValue,
            ),
        )

        assertTrue(repository.getAllNotesSnapshot().isEmpty())
    }

    @Test
    fun flushAutoSaveCreatesNewNoteAndPublishesId() = runTest {
        val repository = FakeNotesRepository()
        val viewModel = EditNoteViewModel(repository)
        val draft = NoteDraft(
            title = "Alpha",
            content = "Body",
            category = NoteCategories.PERSONAL,
            priority = NotePriority.HIGH.storageValue,
            deadlineAt = 1000L,
            type = NoteType.NOTE.storageValue,
        )

        viewModel.start(null, NoteType.NOTE)
        viewModel.flushAutoSave(draft)

        val savedNote = repository.getAllNotesSnapshot().single()
        assertEquals("Alpha", savedNote.title)
        assertEquals(NoteCategories.PERSONAL, savedNote.category)
        assertEquals(NotePriority.HIGH.storageValue, savedNote.priority)
        assertEquals(savedNote.id, viewModel.uiState.value.noteId)
    }
}
