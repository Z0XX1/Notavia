package com.example.notavia.ui

import android.content.Context
import androidx.annotation.StringRes
import com.example.notavia.R
import com.example.notavia.data.NoteCategories

object NoteCategoryUi {
    fun displayName(context: Context, category: String): String {
        return labelRes(category)?.let(context::getString) ?: category
    }

    fun display(context: Context, categories: String): String {
        return NoteCategories.parse(categories).joinToString(", ") { category ->
            displayName(context, category)
        }
    }

    @StringRes
    private fun labelRes(category: String): Int? {
        return when (NoteCategories.normalize(category)) {
            NoteCategories.DEFAULT -> R.string.category_default
            NoteCategories.PERSONAL -> R.string.category_personal
            NoteCategories.STUDY -> R.string.category_study
            NoteCategories.WORK -> R.string.category_work
            NoteCategories.IDEAS -> R.string.category_ideas
            else -> null
        }
    }
}
