package com.example.notavia.viewer

import android.animation.LayoutTransition
import android.content.ClipData
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.notavia.NotaviaActivity
import com.example.notavia.R
import com.example.notavia.ui.dp
import com.example.notavia.ui.installAlphaPressFeedback
import com.example.notavia.ui.resolveThemeColor
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

class ChecklistInteractionController(
    private val activity: NotaviaActivity,
    private val container: LinearLayout,
    private val viewModel: ViewNoteViewModel,
) {
    private val checklistRenderer = ChecklistViewRenderer(
        activity = activity,
        container = container,
        onTouch = ::recordChecklistDragTouch,
        onClick = ::handleChecklistRowClick,
        onLongClick = ::handleChecklistRowLongClick,
        onDrag = ::handleChecklistRowDragEvent,
        completedItemAlpha = COMPLETED_ITEM_ALPHA,
        draggedItemAlpha = DRAGGED_ITEM_ALPHA,
        draggedItemScale = DRAGGED_ITEM_SCALE,
    )

    private var latestUiState: ViewNoteUiState = ViewNoteUiState()
    private var draggedChecklistItemKey: Long? = null
    private var draggedChecklistItemIndex: Int? = null
    private var draggedChecklistItemView: View? = null
    private var checklistDragTouchX: Int = 0
    private var checklistDragTouchY: Int = 0
    private var didMoveDraggedChecklistItem: Boolean = false

    fun setup() {
        container.layoutTransition = LayoutTransition().apply {
            setDuration(CHECKLIST_REORDER_ANIMATION_MS)
            setAnimator(LayoutTransition.APPEARING, null)
            setAnimator(LayoutTransition.DISAPPEARING, null)
            setAnimator(LayoutTransition.CHANGE_APPEARING, null)
            setAnimator(LayoutTransition.CHANGE_DISAPPEARING, null)
            enableTransitionType(LayoutTransition.CHANGING)
        }
        container.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_ENDED -> {
                    finishChecklistItemDrag()
                    true
                }
                else -> true
            }
        }
    }

    fun render(state: ViewNoteUiState) {
        latestUiState = state
        checklistRenderer.render(state, draggedChecklistItemKey)
    }

    fun isSelectionMode(): Boolean {
        return latestUiState.checklist.hasSelection
    }

    fun exitSelectionMode() {
        viewModel.exitChecklistSelection()
    }

    fun selectAllOrClear() {
        viewModel.selectAllOrClearChecklistItems()
    }

    fun showDeleteSelectedConfirmation() {
        val count = latestUiState.checklist.selectedCount
        if (count == 0) return

        val dialog = BottomSheetDialog(activity)
        val dialogContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(24), activity.dp(20), activity.dp(24), activity.dp(16))
            background = GradientDrawable().apply {
                setColor(activity.resolveThemeColor(com.google.android.material.R.attr.colorSurface))
                cornerRadii = floatArrayOf(
                    activity.dp(22).toFloat(),
                    activity.dp(22).toFloat(),
                    activity.dp(22).toFloat(),
                    activity.dp(22).toFloat(),
                    0f,
                    0f,
                    0f,
                    0f,
                )
            }
        }

        dialogContainer.addView(
            TextView(activity).apply {
                text = activity.getString(R.string.delete_notes_title)
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(activity.resolveThemeColor(com.google.android.material.R.attr.colorOnSurface))
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        dialogContainer.addView(
            TextView(activity).apply {
                text = activity.resources.getQuantityString(
                    R.plurals.delete_notes_confirmation_message,
                    count,
                    count,
                )
                textSize = 15f
                setTextColor(activity.resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = activity.dp(8)
            },
        )

        val actionsRow = LinearLayout(activity).apply {
            gravity = android.view.Gravity.END
            orientation = LinearLayout.HORIZONTAL
        }
        actionsRow.addView(
            createDialogActionButton(
                text = activity.getString(R.string.cancel_action),
                textColor = activity.resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant),
            ) {
                dialog.dismiss()
            },
        )
        actionsRow.addView(
            createDialogActionButton(
                text = activity.getString(R.string.delete_confirm_action),
                textColor = ContextCompat.getColor(activity, R.color.priority_high),
            ) {
                dialog.dismiss()
                viewModel.deleteSelectedChecklistItems()
            },
        )
        dialogContainer.addView(
            actionsRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = activity.dp(20)
            },
        )

        dialog.setContentView(dialogContainer)
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet,
            )
            bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
        }
        dialog.show()
    }

    private fun createDialogActionButton(
        text: String,
        textColor: Int,
        onClick: () -> Unit,
    ): MaterialButton {
        return MaterialButton(activity).apply {
            this.text = text
            setAllCaps(false)
            setTextColor(textColor)
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            rippleColor = ColorStateList.valueOf(Color.TRANSPARENT)
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minHeight = 0
            setPadding(activity.dp(14), 0, activity.dp(14), 0)
            installAlphaPressFeedback(this)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                activity.dp(44),
            )
        }
    }

    private fun currentChecklistIndexForRow(row: View): Int? {
        val key = row.tag as? Long ?: return null
        return viewModel.checklistIndexForKey(key)
    }

    private fun findChecklistRowByKey(key: Long): View? {
        for (childIndex in 0 until container.childCount) {
            val child = container.getChildAt(childIndex)
            if (child.tag == key) return child
        }
        return null
    }

    private fun recordChecklistDragTouch(row: View, event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_DOWN &&
            event.actionMasked != MotionEvent.ACTION_MOVE
        ) {
            return
        }

        val rowLocation = IntArray(2)
        row.getLocationOnScreen(rowLocation)
        checklistDragTouchX = (event.rawX - rowLocation[0])
            .roundToInt()
            .coerceIn(0, row.width.coerceAtLeast(1))
        checklistDragTouchY = (event.rawY - rowLocation[1])
            .roundToInt()
            .coerceIn(0, row.height.coerceAtLeast(1))
    }

    private fun handleChecklistRowClick(row: View) {
        currentChecklistIndexForRow(row)?.let { currentIndex ->
            if (isSelectionMode()) {
                viewModel.toggleChecklistSelection(currentIndex)
            } else {
                viewModel.toggleChecklistItemDone(currentIndex)
            }
        }
    }

    private fun handleChecklistRowLongClick(row: View): Boolean {
        return currentChecklistIndexForRow(row)?.let { currentIndex ->
            if (isSelectionMode()) {
                startChecklistItemDrag(currentIndex, row)
                true
            } else {
                viewModel.enterChecklistSelection(currentIndex)
                true
            }
        } ?: false
    }

    private fun handleChecklistRowDragEvent(row: View, event: DragEvent): Boolean {
        return handleChecklistItemDragEvent(event, currentChecklistIndexForRow(row))
    }

    private fun startChecklistItemDrag(index: Int, row: View) {
        val itemKey = viewModel.checklistItemKeyAt(index) ?: return
        if (!viewModel.isChecklistItemSelected(index)) {
            viewModel.enterChecklistSelection(index)
        }
        draggedChecklistItemKey = itemKey
        draggedChecklistItemIndex = index
        draggedChecklistItemView = row
        didMoveDraggedChecklistItem = false

        val dragStarted = row.startDragAndDrop(
            ClipData.newPlainText(CHECKLIST_DRAG_LABEL, itemKey.toString()),
            TouchPointDragShadowBuilder(row, checklistDragTouchX, checklistDragTouchY),
            itemKey,
            0,
        )
        if (dragStarted) {
            row.animate()
                .scaleX(DRAGGED_ITEM_SCALE)
                .scaleY(DRAGGED_ITEM_SCALE)
                .alpha(DRAGGED_ITEM_ALPHA)
                .setDuration(CHECKLIST_DRAG_ANIMATION_MS)
                .start()
        } else {
            finishChecklistItemDrag()
        }
    }

    private fun handleChecklistItemDragEvent(event: DragEvent, targetIndex: Int?): Boolean {
        if (!isSelectionMode()) return false

        return when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> event.localState is Long
            DragEvent.ACTION_DRAG_ENTERED -> {
                val itemKey = draggedChecklistItemKey ?: event.localState as? Long
                if (itemKey != null && targetIndex != null) {
                    moveChecklistItem(itemKey, targetIndex)
                }
                true
            }
            DragEvent.ACTION_DROP,
            DragEvent.ACTION_DRAG_ENDED,
            -> {
                finishChecklistItemDrag()
                true
            }
            else -> true
        }
    }

    private fun moveChecklistItem(itemKey: Long, toIndex: Int) {
        val targetKey = viewModel.checklistItemKeyAt(toIndex) ?: return
        val draggedRow = draggedChecklistItemView ?: findChecklistRowByKey(itemKey)
        val targetRow = findChecklistRowByKey(targetKey)
        val targetChildIndex = targetRow?.let { container.indexOfChild(it) } ?: -1

        if (!viewModel.moveChecklistItemByKey(itemKey, toIndex)) return
        draggedChecklistItemIndex = toIndex
        didMoveDraggedChecklistItem = true
        if (draggedRow != null && targetChildIndex >= 0) {
            moveChecklistRowView(draggedRow, targetChildIndex)
        }
    }

    private fun moveChecklistRowView(row: View, targetChildIndex: Int) {
        if (container.indexOfChild(row) == -1) return

        container.removeView(row)
        container.addView(
            row,
            targetChildIndex.coerceIn(0, container.childCount),
        )
    }

    private fun finishChecklistItemDrag() {
        val shouldSave = didMoveDraggedChecklistItem
        val row = draggedChecklistItemView
        val finalIndex = draggedChecklistItemIndex
        if (draggedChecklistItemKey == null && !shouldSave) return

        val targetAlpha = if (finalIndex != null &&
            viewModel.checklistItemAt(finalIndex)?.isDone == true
        ) {
            COMPLETED_ITEM_ALPHA
        } else {
            1f
        }

        draggedChecklistItemKey = null
        draggedChecklistItemIndex = null
        draggedChecklistItemView = null
        didMoveDraggedChecklistItem = false
        row?.animate()
            ?.scaleX(1f)
            ?.scaleY(1f)
            ?.alpha(targetAlpha)
            ?.setDuration(CHECKLIST_DRAG_ANIMATION_MS)
            ?.start()
        if (shouldSave) {
            viewModel.finishChecklistReorder()
        }
    }

    private class TouchPointDragShadowBuilder(
        view: View,
        private val touchX: Int,
        private val touchY: Int,
    ) : View.DragShadowBuilder(view) {
        private val shadowView = view

        override fun onProvideShadowMetrics(
            outShadowSize: Point,
            outShadowTouchPoint: Point,
        ) {
            outShadowSize.set(shadowView.width, shadowView.height)
            outShadowTouchPoint.set(
                touchX.coerceIn(0, shadowView.width.coerceAtLeast(1)),
                touchY.coerceIn(0, shadowView.height.coerceAtLeast(1)),
            )
        }
    }

    private companion object {
        const val COMPLETED_ITEM_ALPHA = 0.45f
        const val DRAGGED_ITEM_ALPHA = 0f
        const val DRAGGED_ITEM_SCALE = 1.03f
        const val CHECKLIST_DRAG_ANIMATION_MS = 120L
        const val CHECKLIST_REORDER_ANIMATION_MS = 160L
        const val CHECKLIST_DRAG_LABEL = "notavia_checklist_item"
    }
}
