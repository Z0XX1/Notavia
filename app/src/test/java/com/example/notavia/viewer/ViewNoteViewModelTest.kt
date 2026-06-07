package com.example.notavia.viewer

import com.example.notavia.FakeNotesRepository
import com.example.notavia.MainDispatcherRule
import com.example.notavia.data.ChecklistContent
import com.example.notavia.data.ChecklistItem
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
class ViewNoteViewModelTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun loadingChecklistPublishesChecklistState() = runTest {
        val note = checklistNote()
        val viewModel = ViewNoteViewModel(FakeNotesRepository(listOf(note)))

        viewModel.load(note.id)

        val checklist = viewModel.uiState.value.checklist
        assertEquals(listOf("Alpha"), checklist.incompleteItems.map { it.item.text })
        assertEquals(listOf("Beta"), checklist.completedItems.map { it.item.text })
    }

    @Test
    fun togglingChecklistItemPersistsOnFlush() = runTest {
        val note = checklistNote()
        val repository = FakeNotesRepository(listOf(note))
        val viewModel = ViewNoteViewModel(repository)

        viewModel.load(note.id)
        assertTrue(viewModel.toggleChecklistItemDone(0))
        viewModel.flushChecklistAutoSave()

        val savedItems = ChecklistContent.parse(repository.getNoteById(note.id)!!.content)
        assertTrue(savedItems.first { it.text == "Alpha" }.isDone)
    }

    @Test
    fun deletingSelectedChecklistItemsClearsSelection() = runTest {
        val note = checklistNote()
        val viewModel = ViewNoteViewModel(FakeNotesRepository(listOf(note)))

        viewModel.load(note.id)
        viewModel.enterChecklistSelection(0)

        assertTrue(viewModel.deleteSelectedChecklistItems())

        val checklist = viewModel.uiState.value.checklist
        assertEquals(listOf("Beta"), checklist.completedItems.map { it.item.text })
        assertFalse(checklist.hasSelection)
    }

    private fun checklistNote(): Note {
        return Note(
            id = 1,
            title = "Checklist",
            content = ChecklistContent.serialize(
                listOf(
                    ChecklistItem("Alpha"),
                    ChecklistItem("Beta", isDone = true),
                ),
            ),
            category = NoteCategories.DEFAULT,
            priority = NotePriority.NONE.storageValue,
            type = NoteType.CHECKLIST.storageValue,
            createdAt = 1,
            updatedAt = 1,
        )
    }
}
