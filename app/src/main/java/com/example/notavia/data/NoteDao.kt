package com.example.notavia.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

// DAO содержит SQL-операции для таблицы заметок.
@Dao
interface NoteDao {
    // Получение всех записей: закрепленные выше, затем новые изменения выше старых.
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getAllNotes(): List<Note>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): Note?

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE notes SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun updatePinnedState(ids: List<Long>, isPinned: Boolean, updatedAt: Long)
}
