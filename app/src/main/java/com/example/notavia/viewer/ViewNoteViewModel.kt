package com.example.notavia.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.notavia.checklist.ChecklistState
import com.example.notavia.checklist.ChecklistUiState
import com.example.notavia.data.Note
import com.example.notavia.data.NoteCategories
import com.example.notavia.data.NotePriority
import com.example.notavia.data.NoteRepository
import com.example.notavia.data.NoteType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ViewNoteUiState(
    val noteId: Long? = null,
    val note: Note? = null,
    val noteType: NoteType = NoteType.NOTE,
    val checklist: ChecklistUiState = ChecklistUiState(),
    val isLoading: Boolean = false,
) {
    val isChecklist: Boolean
        get() = noteType == NoteType.CHECKLIST
}

sealed interface ViewNoteEffect {
    data object Finish : ViewNoteEffect
}

class ViewNoteViewModel(
    private val repository: NoteRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ViewNoteUiState())
    val uiState: StateFlow<ViewNoteUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ViewNoteEffect>()
    val effects: SharedFlow<ViewNoteEffect> = _effects.asSharedFlow()

    private val checklistItems = ChecklistState()
    private var currentNote: Note? = null
    private var currentNoteType: NoteType = NoteType.NOTE
    private var currentTitle: String = ""
    private var autoSaveDelayJob: Job? = null
    private var autoSaveJob: Job? = null
    private var pendingChecklistSaveAfterCurrent: Boolean = false
    private var hasUnpublishedChecklistReorder: Boolean = false

    fun load(noteId: Long) {
        if (noteId == NO_NOTE_ID) {
            viewModelScope.launch {
                _effects.emit(ViewNoteEffect.Finish)
            }
            return
        }

        _uiState.update { state ->
            state.copy(noteId = noteId, isLoading = true)
        }
        viewModelScope.launch {
            val note = repository.getNoteById(noteId)
            if (note == null) {
                _effects.emit(ViewNoteEffect.Finish)
                return@launch
            }
            applyLoadedNote(note)
        }
    }

    fun updateTitle(title: String) {
        val normalizedTitle = title.trim()
        if (normalizedTitle == currentTitle) return

        currentTitle = normalizedTitle
        if (currentNoteType == NoteType.CHECKLIST) {
            scheduleChecklistAutoSave()
        }
    }

    fun saveCurrentNote(title: String, content: String) {
        val note = currentNote ?: return
        val updatedNote = if (currentNoteType == NoteType.CHECKLIST) {
            checklistNoteCopy(note, title.trim())
        } else {
            note.copy(
                title = title.trim(),
                content = content,
                updatedAt = System.currentTimeMillis(),
            )
        }

        viewModelScope.launch {
            repository.saveNote(updatedNote)
            currentNote = updatedNote
            publishState(isLoading = false)
            _effects.emit(ViewNoteEffect.Finish)
        }
    }

    fun flushChecklistAutoSave() {
        autoSaveDelayJob?.cancel()
        if (autoSaveJob?.isActive == true) {
            pendingChecklistSaveAfterCurrent = true
            return
        }
        if (currentNoteType == NoteType.CHECKLIST) {
            autoSaveJob = viewModelScope.launch {
                persistChecklist()
            }
        }
    }

    fun toggleChecklistItemDone(index: Int): Boolean {
        if (!checklistItems.toggleDone(index)) return false

        publishState(isLoading = false)
        scheduleChecklistAutoSave()
        return true
    }

    fun enterChecklistSelection(index: Int): Boolean {
        if (!checklistItems.enterSelection(index)) return false

        publishState(isLoading = false)
        return true
    }

    fun toggleChecklistSelection(index: Int): Boolean {
        if (!checklistItems.containsIndex(index)) return false

        checklistItems.toggleSelection(index)
        publishState(isLoading = false)
        return true
    }

    fun exitChecklistSelection() {
        checklistItems.exitSelection()
        publishState(isLoading = false)
    }

    fun selectAllOrClearChecklistItems() {
        checklistItems.selectAllOrClear()
        publishState(isLoading = false)
    }

    fun deleteSelectedChecklistItems(): Boolean {
        if (!checklistItems.removeSelected()) return false

        publishState(isLoading = false)
        scheduleChecklistAutoSave()
        return true
    }

    fun moveChecklistItemByKey(itemKey: Long, toIndex: Int): Boolean {
        val moved = checklistItems.moveByKey(itemKey, toIndex)
        if (moved) {
            hasUnpublishedChecklistReorder = true
        }
        return moved
    }

    fun finishChecklistReorder() {
        if (!hasUnpublishedChecklistReorder) return

        hasUnpublishedChecklistReorder = false
        publishState(isLoading = false)
        scheduleChecklistAutoSave()
    }

    fun checklistIndexForKey(key: Long): Int? {
        return checklistItems.indexForKey(key)
    }

    fun checklistItemKeyAt(index: Int): Long? {
        return checklistItems.keyAt(index)
    }

    fun checklistItemAt(index: Int) = checklistItems.itemAt(index)

    fun isChecklistItemSelected(index: Int): Boolean {
        return checklistItems.isSelected(index)
    }

    private fun applyLoadedNote(note: Note) {
        currentNote = note
        currentNoteType = NoteType.fromStorage(note.type)
        currentTitle = note.title
        hasUnpublishedChecklistReorder = false
        if (currentNoteType == NoteType.CHECKLIST) {
            checklistItems.replaceWithContent(note.content)
        } else {
            checklistItems.exitSelection()
        }
        publishState(isLoading = false)
    }

    private fun scheduleChecklistAutoSave() {
        if (currentNoteType != NoteType.CHECKLIST) return

        autoSaveDelayJob?.cancel()
        autoSaveDelayJob = viewModelScope.launch {
            delay(AUTO_SAVE_DELAY_MS)
            requestChecklistAutoSaveNow()
        }
    }

    private fun requestChecklistAutoSaveNow() {
        if (currentNoteType != NoteType.CHECKLIST) return

        if (autoSaveJob?.isActive == true) {
            pendingChecklistSaveAfterCurrent = true
            return
        }

        autoSaveJob = viewModelScope.launch {
            do {
                pendingChecklistSaveAfterCurrent = false
                persistChecklist()
            } while (pendingChecklistSaveAfterCurrent)
        }
    }

    private suspend fun persistChecklist() {
        val note = currentNote ?: return
        if (currentNoteType != NoteType.CHECKLIST) return

        val updatedNote = checklistNoteCopy(note, currentTitle)
        if (
            updatedNote.title == note.title &&
            updatedNote.content == note.content &&
            updatedNote.category == note.category &&
            updatedNote.priority == note.priority &&
            updatedNote.deadlineAt == note.deadlineAt
        ) {
            return
        }

        repository.saveNote(updatedNote)
        currentNote = updatedNote
        publishState(isLoading = false)
    }

    private fun checklistNoteCopy(note: Note, title: String): Note {
        return note.copy(
            title = title,
            content = checklistItems.serialize(),
            category = NoteCategories.DEFAULT,
            priority = NotePriority.NONE.storageValue,
            deadlineAt = null,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun publishState(isLoading: Boolean) {
        _uiState.update { state ->
            state.copy(
                noteId = currentNote?.id ?: state.noteId,
                note = currentNote,
                noteType = currentNoteType,
                checklist = checklistItems.snapshot(),
                isLoading = isLoading,
            )
        }
    }

    class Factory(
        private val repository: NoteRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ViewNoteViewModel::class.java)) {
                return ViewNoteViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    private companion object {
        const val NO_NOTE_ID = -1L
        const val AUTO_SAVE_DELAY_MS = 450L
    }
}
