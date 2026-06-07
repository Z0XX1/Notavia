package com.example.notavia.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface NotesRepository {
    val notesFlow: Flow<List<Note>>

    suspend fun getAllNotesSnapshot(): List<Note>

    suspend fun getNoteById(id: Long): Note?

    suspend fun saveNote(note: Note): Long

    suspend fun deleteNotes(ids: List<Long>)

    suspend fun updatePinnedState(ids: List<Long>, isPinned: Boolean)
}

class NoteRepository(private val noteDao: NoteDao) : NotesRepository {
    override val notesFlow: Flow<List<Note>> = noteDao.observeAllNotes()

    override suspend fun getAllNotesSnapshot(): List<Note> = notesFlow.first()

    override suspend fun getNoteById(id: Long): Note? = noteDao.getNoteById(id)


    override suspend fun saveNote(note: Note): Long {
        return if (note.id == 0L) {
            noteDao.insert(note)
        } else {
            noteDao.update(note)
            note.id
        }
    }

    override suspend fun deleteNotes(ids: List<Long>) {
        if (ids.isEmpty()) return
        noteDao.deleteByIds(ids)
    }

    override suspend fun updatePinnedState(ids: List<Long>, isPinned: Boolean) {
        if (ids.isEmpty()) return
        noteDao.updatePinnedState(ids, isPinned, System.currentTimeMillis())
    }
}
