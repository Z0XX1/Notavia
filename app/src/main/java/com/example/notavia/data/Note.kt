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
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
)
