package com.example.notavia.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.notavia.data.ChecklistContent
import com.example.notavia.data.Note
import com.example.notavia.data.NoteRepository
import com.example.notavia.data.NoteType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

data class NoteDraft(
    val title: String,
    val content: String,
    val category: String,
    val priority: String,
    val deadlineAt: Long?,
    val type: String,
) {
    fun isBlank(): Boolean {
        return title.isBlank() &&
            if (NoteType.fromStorage(type) == NoteType.CHECKLIST) {
                ChecklistContent.parse(content).isEmpty()
            } else {
                content.isBlank()
            }
    }
}

data class EditNoteUiState(
    val noteId: Long? = null,
    val noteType: NoteType = NoteType.NOTE,
    val loadedNote: Note? = null,
    val isLoading: Boolean = false,
)

sealed interface EditNoteEffect {
    data object Finish : EditNoteEffect
}

class EditNoteViewModel(
    private val repository: NoteRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditNoteUiState())
    val uiState: StateFlow<EditNoteUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<EditNoteEffect>()
    val effects: SharedFlow<EditNoteEffect> = _effects.asSharedFlow()

    private var started = false
    private var existingNote: Note? = null
    private var lastSavedDraft: NoteDraft? = null
    private var autoSaveDelayJob: Job? = null
    private var autoSaveJob: Job? = null
    private var pendingDraftAfterCurrent: NoteDraft? = null

    fun start(noteId: Long?, initialNoteType: NoteType) {
        if (started) return
        started = true

        if (noteId == null) {
            _uiState.update { state ->
                state.copy(noteId = null, noteType = initialNoteType, isLoading = false)
            }
            return
        }

        _uiState.update { state ->
            state.copy(noteId = noteId, noteType = initialNoteType, isLoading = true)
        }
        viewModelScope.launch {
            val note = repository.getNoteById(noteId)
            if (note == null) {
                _effects.emit(EditNoteEffect.Finish)
                return@launch
            }

            existingNote = note
            lastSavedDraft = note.toDraft()
            _uiState.update { state ->
                state.copy(
                    noteId = note.id,
                    noteType = NoteType.fromStorage(note.type),
                    loadedNote = note,
                    isLoading = false,
                )
            }
        }
    }

    fun scheduleAutoSave(draft: NoteDraft) {
        autoSaveDelayJob?.cancel()
        autoSaveDelayJob = viewModelScope.launch {
            delay(AUTO_SAVE_DELAY_MS)
            requestAutoSaveNow(draft)
        }
    }

    fun requestAutoSaveNow(draft: NoteDraft) {
        if (autoSaveJob?.isActive == true) {
            pendingDraftAfterCurrent = draft
            return
        }

        autoSaveJob = viewModelScope.launch {
            var draftToSave: NoteDraft? = draft
            while (draftToSave != null) {
                val currentDraft = draftToSave
                pendingDraftAfterCurrent = null
                persistDraft(currentDraft)
                draftToSave = pendingDraftAfterCurrent
            }
        }
    }

    suspend fun flushAutoSave(draft: NoteDraft) {
        autoSaveDelayJob?.cancel()
        val runningSave = autoSaveJob
        if (runningSave?.isActive == true) {
            pendingDraftAfterCurrent = draft
            runningSave.join()
        } else {
            persistDraft(draft)
        }
    }

    private suspend fun persistDraft(draft: NoteDraft) {
        if (draft == lastSavedDraft) return
        if (existingNote == null && draft.isBlank()) return

        val now = System.currentTimeMillis()
        val noteToSave = existingNote?.copy(
            title = draft.title,
            content = draft.content,
            category = draft.category,
            priority = draft.priority,
            deadlineAt = draft.deadlineAt,
            type = draft.type,
            updatedAt = now,
        ) ?: Note(
            title = draft.title,
            content = draft.content,
            category = draft.category,
            priority = draft.priority,
            deadlineAt = draft.deadlineAt,
            type = draft.type,
            createdAt = now,
            updatedAt = now,
        )

        val savedId = repository.saveNote(noteToSave)
        existingNote = noteToSave.copy(id = savedId)
        lastSavedDraft = draft
        _uiState.update { state ->
            state.copy(noteId = savedId, noteType = NoteType.fromStorage(draft.type))
        }
    }

    private fun Note.toDraft(): NoteDraft {
        return NoteDraft(
            title = title,
            content = content,
            category = category,
            priority = priority,
            deadlineAt = deadlineAt,
            type = type,
        )
    }

    class Factory(
        private val repository: NoteRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(EditNoteViewModel::class.java)) {
                return EditNoteViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    private companion object {
        const val AUTO_SAVE_DELAY_MS = 450L
    }
}
