package com.example.notavia.data

class NoteRepository(private val noteDao: NoteDao) {
    suspend fun getAllNotes(): List<Note> = noteDao.getAllNotes()

    suspend fun getNoteById(id: Long): Note? = noteDao.getNoteById(id)

    suspend fun saveNote(note: Note): Long {
        return if (note.id == 0L) {
            noteDao.insert(note)
        } else {
            noteDao.update(note)
            note.id
        }
    }

    suspend fun deleteNotes(ids: List<Long>) {
        if (ids.isEmpty()) return
        noteDao.deleteByIds(ids)
    }

    suspend fun updatePinnedState(ids: List<Long>, isPinned: Boolean) {
        if (ids.isEmpty()) return
        noteDao.updatePinnedState(ids, isPinned, System.currentTimeMillis())
    }
}
