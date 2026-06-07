package com.example.notavia.editor

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import com.example.notavia.NotaviaActivity
import com.example.notavia.R
import com.example.notavia.data.ChecklistItem
import com.example.notavia.ui.dp
import com.example.notavia.ui.installAlphaPressFeedback
import com.example.notavia.ui.resolveThemeColor

class ChecklistEditorRenderer(
    private val activity: NotaviaActivity,
    private val container: LinearLayout,
    private val onToggleDone: (Int) -> Unit,
    private val onDelete: (Int) -> Unit,
) {
    fun render(
        incompleteItems: List<IndexedValue<ChecklistItem>>,
        completedItems: List<IndexedValue<ChecklistItem>>,
    ) {
        container.removeAllViews()
        renderSection(
            title = activity.getString(R.string.checklist_incomplete_title),
            indexedItems = incompleteItems,
        )
        renderSection(
            title = activity.getString(R.string.checklist_completed_title),
            indexedItems = completedItems,
        )
    }

    private fun renderSection(
        title: String,
        indexedItems: List<IndexedValue<ChecklistItem>>,
    ) {
        if (indexedItems.isEmpty()) return

        container.addView(
            TextView(activity).apply {
                text = title
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(activity.resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(0, activity.dp(14), 0, activity.dp(4))
            },
        )

        indexedItems.forEach { (index, item) ->
            container.addView(createChecklistRow(index, item))
        }
    }

    private fun createChecklistRow(index: Int, item: ChecklistItem): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, activity.dp(4), 0, activity.dp(4))
        }

        val toggleButton = AppCompatImageButton(activity).apply {
            setImageResource(if (item.isDone) R.drawable.checkbox else R.drawable.emptybox)
            background = ColorDrawable(Color.TRANSPARENT)
            contentDescription = item.text
            setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10))
            imageTintList = ColorStateList.valueOf(
                activity.resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant),
            )
            installAlphaPressFeedback(this)
            setOnClickListener {
                onToggleDone(index)
            }
        }
        row.addView(toggleButton, LinearLayout.LayoutParams(activity.dp(44), activity.dp(44)))

        row.addView(
            TextView(activity).apply {
                text = item.text
                textSize = 17f
                setTextColor(activity.resolveThemeColor(com.google.android.material.R.attr.colorOnBackground))
                paintFlags = if (item.isDone) {
                    paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )

        val deleteButton = AppCompatImageButton(activity).apply {
            setImageResource(R.drawable.close)
            background = ColorDrawable(Color.TRANSPARENT)
            contentDescription = activity.getString(R.string.delete_action)
            setPadding(activity.dp(12), activity.dp(12), activity.dp(12), activity.dp(12))
            imageTintList = ColorStateList.valueOf(
                activity.resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant),
            )
            installAlphaPressFeedback(this)
            setOnClickListener {
                onDelete(index)
            }
        }
        row.addView(deleteButton, LinearLayout.LayoutParams(activity.dp(44), activity.dp(44)))

        return row
    }
}
