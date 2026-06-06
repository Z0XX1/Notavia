package com.example.notavia.data

// Enum ограничивает допустимые значения приоритета заметки.
enum class NotePriority(val storageValue: String) {
    NONE("none"),
    HIGH("high"),
    MEDIUM("medium"),
    LOW("low");

    companion object {
        fun fromStorage(value: String?): NotePriority {
            return entries.firstOrNull { it.storageValue == value } ?: NONE
        }
    }
}
