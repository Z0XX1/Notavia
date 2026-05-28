package com.example.notavia.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    @ColumnInfo(defaultValue = "'Без категории'")
    val category: String = NoteCategories.DEFAULT,
    @ColumnInfo(defaultValue = "'none'")
    val priority: String = NotePriority.NONE.storageValue,
    @ColumnInfo(defaultValue = "NULL")
    val deadlineAt: Long? = null,
    @ColumnInfo(defaultValue = "'note'")
    val type: String = NoteType.NOTE.storageValue,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
)
