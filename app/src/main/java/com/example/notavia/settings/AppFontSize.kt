package com.example.notavia.settings

import androidx.annotation.StringRes
import com.example.notavia.R

// Размер шрифта хранит значение DataStore, масштаб и строковый ресурс подписи.
enum class AppFontSize(
    val storageValue: String,
    val fontScale: Float,
    @StringRes val labelRes: Int,
) {
    SMALL("small", 0.81f, R.string.font_size_small),
    MEDIUM("medium", 1f, R.string.font_size_medium),
    LARGE("large", 1.23f, R.string.font_size_large);

    companion object {
        fun fromStorage(value: String?): AppFontSize {
            return entries.firstOrNull { it.storageValue == value } ?: MEDIUM
        }
    }
}
