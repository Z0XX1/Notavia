package com.example.notavia.ui

import android.content.res.ColorStateList
import android.widget.ImageView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.example.notavia.R
import com.example.notavia.data.NotePriority

// Единое место для цветов, подписей и описаний приоритетов.
object NotePriorityUi {
    @ColorRes
    fun colorRes(priority: NotePriority): Int {
        return when (priority) {
            NotePriority.NONE -> R.color.priority_none
            NotePriority.HIGH -> R.color.priority_high
            NotePriority.MEDIUM -> R.color.priority_medium
            NotePriority.LOW -> R.color.priority_low
        }
    }

    @StringRes
    fun labelRes(priority: NotePriority): Int {
        return when (priority) {
            NotePriority.NONE -> R.string.priority_none
            NotePriority.HIGH -> R.string.priority_high
            NotePriority.MEDIUM -> R.string.priority_medium
            NotePriority.LOW -> R.string.priority_low
        }
    }

    fun applyTo(view: ImageView, priority: NotePriority) {
        val context = view.context
        view.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(context, colorRes(priority)),
        )
        view.contentDescription = context.getString(
            R.string.priority_content_description,
            context.getString(labelRes(priority)),
        )
    }
}
