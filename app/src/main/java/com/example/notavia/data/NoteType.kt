package com.example.notavia.data

// Enum разделяет обычные заметки и чек-листы в общей таблице notes.
enum class NoteType(val storageValue: String) {
    NOTE("note"),
    CHECKLIST("checklist");

    companion object {
        fun fromStorage(value: String?): NoteType {
            return values().firstOrNull { it.storageValue == value } ?: NOTE
        }
    }
}
