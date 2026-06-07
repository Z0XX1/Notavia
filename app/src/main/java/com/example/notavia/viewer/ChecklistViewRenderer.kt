package com.example.notavia.viewer

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import com.example.notavia.NotaviaActivity
import com.example.notavia.R
import com.example.notavia.checklist.ChecklistItemUiState
import com.example.notavia.ui.dp
import com.example.notavia.ui.installAlphaPressFeedback
import com.example.notavia.ui.resolveThemeColor

class ChecklistViewRenderer(
    private val activity: NotaviaActivity,
    private val container: LinearLayout,
    private val onTouch: (View, MotionEvent) -> Unit,
    private val onClick: (View) -> Unit,
    private val onLongClick: (View) -> Boolean,
    private val onDrag: (View, DragEvent) -> Boolean,
    private val completedItemAlpha: Float,
    private val draggedItemAlpha: Float,
    private val draggedItemScale: Float,
) {
    fun render(state: ViewNoteUiState, draggedItemKey: Long?) {
        container.removeAllViews()
        container.visibility = View.VISIBLE

        renderSection(
            title = activity.getString(R.string.checklist_incomplete_title),
            items = state.checklist.incompleteItems,
            isFirstSection = true,
            isSelectionMode = state.checklist.hasSelection,
            draggedItemKey = draggedItemKey,
        )
        renderSection(
            title = activity.getString(R.string.checklist_completed_title),
            items = state.checklist.completedItems,
            isFirstSection = state.checklist.incompleteItems.isEmpty(),
            isSelectionMode = state.checklist.hasSelection,
            draggedItemKey = draggedItemKey,
        )
    }

    private fun renderSection(
        title: String,
        items: List<ChecklistItemUiState>,
        isFirstSection: Boolean,
        isSelectionMode: Boolean,
        draggedItemKey: Long?,
    ) {
        if (items.isEmpty()) return

        container.addView(
            createSectionHeader(title, isFirstSection),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        items.forEach { itemState ->
            container.addView(
                createRow(itemState, isSelectionMode, draggedItemKey),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun createRow(
        itemState: ChecklistItemUiState,
        isSelectionMode: Boolean,
        draggedItemKey: Long?,
    ): View {
        val item = itemState.item
        val itemKey = itemState.key
        val isDraggedItem = draggedItemKey == itemKey
        val row = LinearLayout(activity).apply {
            tag = itemKey
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, activity.dp(4), 0, activity.dp(4))
            alpha = when {
                isDraggedItem -> draggedItemAlpha
                item.isDone -> completedItemAlpha
                else -> 1f
            }
            scaleX = if (isDraggedItem) draggedItemScale else 1f
            scaleY = if (isDraggedItem) draggedItemScale else 1f
            setOnTouchListener { touchedView, event ->
                onTouch(touchedView, event)
                false
            }
            setOnClickListener {
                onClick(this)
            }
            setOnLongClickListener {
                onLongClick(this)
            }
            setOnDragListener { _, event ->
                onDrag(this, event)
            }
        }

        row.addView(
            AppCompatImageButton(activity).apply {
                setImageResource(
                    when {
                        itemState.isSelected -> R.drawable.checkcircle
                        isSelectionMode -> R.drawable.emptycircle
                        item.isDone -> R.drawable.checkbox
                        else -> R.drawable.emptybox
                    },
                )
                background = ColorDrawable(Color.TRANSPARENT)
                imageTintList = ColorStateList.valueOf(
                    activity.resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant),
                )
                contentDescription = item.text
                setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10))
                installAlphaPressFeedback(this)
                setOnClickListener {
                    onClick(row)
                }
            },
            LinearLayout.LayoutParams(activity.dp(44), activity.dp(44)),
        )

        row.addView(
            TextView(activity).apply {
                text = item.text
                textSize = 18f
                minLines = 1
                maxLines = Int.MAX_VALUE
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(activity.resolveThemeColor(com.google.android.material.R.attr.colorOnBackground))
                setHintTextColor(activity.resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                paintFlags = if (item.isDone) {
                    paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }
                setOnTouchListener { _, event ->
                    onTouch(row, event)
                    false
                }
                setOnClickListener {
                    onClick(row)
                }
                setOnLongClickListener {
                    onLongClick(row)
                }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )

        return row
    }

    private fun createSectionHeader(title: String, isFirstSection: Boolean): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, if (isFirstSection) activity.dp(2) else activity.dp(14), 0, activity.dp(4))

            addView(
                TextView(activity).apply {
                    text = title
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(activity.resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                View(activity).apply {
                    setBackgroundColor(activity.resolveThemeColor(com.google.android.material.R.attr.colorOutline))
                },
                LinearLayout.LayoutParams(0, activity.dp(1), 1f).apply {
                    marginStart = activity.dp(12)
                },
            )
        }
    }
}
