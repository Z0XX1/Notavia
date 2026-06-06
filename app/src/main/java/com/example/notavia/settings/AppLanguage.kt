package com.example.notavia.settings

import androidx.annotation.StringRes
import com.example.notavia.R

enum class AppLanguage(
    val storageValue: String,
    val localeTag: String,
    @StringRes val labelRes: Int,
) {
    RUSSIAN("ru", "ru", R.string.language_russian),
    ENGLISH("en", "en", R.string.language_english);

    companion object {
        fun fromStorage(value: String?): AppLanguage {
            return entries.firstOrNull { it.storageValue == value } ?: RUSSIAN
        }
    }
}
