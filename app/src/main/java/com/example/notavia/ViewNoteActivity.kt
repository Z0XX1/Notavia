package com.example.notavia

import android.animation.LayoutTransition
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.notavia.data.NotePriority
import com.example.notavia.data.NoteRepository
import com.example.notavia.data.NotaviaDatabase
import com.example.notavia.databinding.ActivityViewNoteBinding
import com.example.notavia.navigation.NoteNavigationContract.EXTRA_NOTE_ID
import com.example.notavia.navigation.NoteNavigationContract.NO_NOTE_ID
import com.example.notavia.ui.NoteCategoryUi
import com.example.notavia.ui.NotePriorityUi
import com.example.notavia.ui.currentLocale
import com.example.notavia.ui.installAlphaPressFeedback
import com.example.notavia.ui.resolveThemeColor
import com.example.notavia.viewer.ChecklistViewRenderer
import com.example.notavia.viewer.ViewNoteEffect
import com.example.notavia.viewer.ViewNoteUiState
import com.example.notavia.viewer.ViewNoteViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToInt


class ViewNoteActivity : NotaviaActivity() {

    private lateinit var binding: ActivityViewNoteBinding
    private lateinit var viewModel: ViewNoteViewModel
    private lateinit var checklistRenderer: ChecklistViewRenderer

    private var noteId: Long = NO_NOTE_ID
    private var latestUiState: ViewNoteUiState = ViewNoteUiState()
    private var draggedChecklistItemKey: Long? = null
    private var draggedChecklistItemIndex: Int? = null
    private var draggedChecklistItemView: View? = null
    private var checklistDragTouchX: Int = 0
    private var checklistDragTouchY: Int = 0
    private var didMoveDraggedChecklistItem: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityViewNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        checklistRenderer = ChecklistViewRenderer(
            activity = this,
            container = binding.checklistItemsContainer,
            onTouch = ::recordChecklistDragTouch,
            onClick = ::handleChecklistRowClick,
            onLongClick = ::handleChecklistRowLongClick,
            onDrag = ::handleChecklistRowDragEvent,
            completedItemAlpha = COMPLETED_ITEM_ALPHA,
            draggedItemAlpha = DRAGGED_ITEM_ALPHA,
            draggedItemScale = DRAGGED_ITEM_SCALE,
        )
        setupChecklistContainerAnimation()

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        viewModel = ViewModelProvider(
            this,
            ViewNoteViewModel.Factory(
                NoteRepository(NotaviaDatabase.getDatabase(this).noteDao()),
            ),
        )[ViewNoteViewModel::class.java]
        noteId = intent.getLongExtra(EXTRA_NOTE_ID, NO_NOTE_ID)

        observeViewState()
        setupActions()
        setupBackHandling()
    }

    private fun setupChecklistContainerAnimation() {
        binding.checklistItemsContainer.layoutTransition = LayoutTransition().apply {
            setDuration(CHECKLIST_REORDER_ANIMATION_MS)
            setAnimator(LayoutTransition.APPEARING, null)
            setAnimator(LayoutTransition.DISAPPEARING, null)
            setAnimator(LayoutTransition.CHANGE_APPEARING, null)
            setAnimator(LayoutTransition.CHANGE_DISAPPEARING, null)
            enableTransitionType(LayoutTransition.CHANGING)
        }
        binding.checklistItemsContainer.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_ENDED -> {
                    finishChecklistItemDrag()
                    true
                }
                else -> true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.load(noteId)
    }

    override fun onPause() {
        viewModel.flushChecklistAutoSave()
        super.onPause()
    }


    private fun setupActions() {
        installAlphaPressFeedback(binding.backButton)
        installAlphaPressFeedback(binding.saveButton)
        installAlphaPressFeedback(binding.editButton)
        installAlphaPressFeedback(binding.selectAllChecklistItemsButton)
        installAlphaPressFeedback(binding.deleteChecklistItemsButton)
        installAlphaPressFeedback(binding.deleteChecklistSelectionButton)

        binding.backButton.setOnClickListener {
            if (isChecklistSelectionMode()) {
                exitChecklistSelectionMode()
            } else {
                finish()
            }
        }

        binding.saveButton.setOnClickListener {
            saveCurrentNote()
        }

        binding.editButton.setOnClickListener {
            if (noteId == NO_NOTE_ID) return@setOnClickListener
            val intent = Intent(this, EditNoteActivity::class.java).apply {
                putExtra(EXTRA_NOTE_ID, noteId)
            }
            startActivity(intent)
        }

        binding.selectAllChecklistItemsButton.setOnClickListener {
            viewModel.selectAllOrClearChecklistItems()
        }

        binding.deleteChecklistItemsButton.setOnClickListener {
            showDeleteChecklistItemsConfirmation()
        }

        binding.deleteChecklistSelectionButton.setOnClickListener {
            showDeleteChecklistItemsConfirmation()
        }

        binding.titleTextView.doAfterTextChanged {
            viewModel.updateTitle(it?.toString().orEmpty())
        }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isChecklistSelectionMode()) {
                    exitChecklistSelectionMode()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }


    private fun observeViewState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderViewState(state)
                    }
                }
                launch {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            ViewNoteEffect.Finish -> finish()
                        }
                    }
                }
            }
        }
    }

    private fun saveCurrentNote() {
        currentFocus?.clearFocus()
        viewModel.saveCurrentNote(
            title = binding.titleTextView.text?.toString()?.trim().orEmpty(),
            content = binding.contentTextView.text?.toString().orEmpty(),
        )
    }


    private fun renderViewState(state: ViewNoteUiState) {
        val note = state.note ?: return
        latestUiState = state
        val isChecklist = state.isChecklist
        binding.screenTitleTextView.setText(R.string.view_note_title)
        binding.saveButton.visibility = View.GONE
        binding.editButton.visibility = View.VISIBLE
        binding.selectAllChecklistItemsButton.visibility = View.GONE
        binding.deleteChecklistItemsButton.visibility = View.GONE
        binding.checklistSelectionActionBar.visibility = View.GONE
        binding.titleTextView.isFocusable = false
        binding.titleTextView.isFocusableInTouchMode = false
        binding.titleTextView.isCursorVisible = false
        binding.contentTextView.isFocusable = false
        binding.contentTextView.isFocusableInTouchMode = false
        binding.contentTextView.isCursorVisible = false
        binding.titleTextView.hint = getString(R.string.untitled_note)
        binding.titleTextView.setText(note.title)
        if (isChecklist) {
            binding.contentTextView.visibility = View.GONE
            binding.checklistItemsContainer.visibility = View.VISIBLE
            renderChecklistItems(state)
            binding.statisticsCardView.visibility = View.GONE
            binding.categoryTextView.visibility = View.GONE
            binding.deadlineTextView.visibility = View.GONE
            binding.priorityIndicatorImageView.visibility = View.GONE
        } else {
            binding.contentTextView.visibility = View.VISIBLE
            binding.checklistItemsContainer.visibility = View.GONE
            binding.statisticsCardView.visibility = View.VISIBLE
            binding.categoryTextView.visibility = View.VISIBLE
            binding.contentTextView.hint = getString(R.string.empty_note_preview)
            binding.contentTextView.setText(note.content)
            binding.categoryTextView.text = getString(
                R.string.category_format,
                NoteCategoryUi.display(this, note.category),
            )
            binding.deadlineTextView.visibility = if (note.deadlineAt == null) {
                View.GONE
            } else {
                View.VISIBLE
            }
            note.deadlineAt?.let { deadline ->
                binding.deadlineTextView.text = getString(
                    R.string.deadline_format,
                    deadlineFormatter().format(Date(deadline)),
                )
                binding.deadlineTextView.setTextColor(
                    if (isDeadlineOverdue(deadline)) {
                        ContextCompat.getColor(this, R.color.priority_high)
                    } else {
                        resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
                    },
                )
            }
            val priority = NotePriority.fromStorage(note.priority)
            binding.priorityIndicatorImageView.visibility = if (priority == NotePriority.NONE) {
                View.GONE
            } else {
                View.VISIBLE
            }
            NotePriorityUi.applyTo(binding.priorityIndicatorImageView, priority)
        }
        binding.pinnedBadgeTextView.text = getString(
            if (isChecklist) R.string.pinned_checklist else R.string.pinned_note,
        )
        binding.pinnedBadgeTextView.visibility = if (note.isPinned) {
            View.VISIBLE
        } else {
            View.GONE
        }
        if (isChecklist) {
            updateChecklistTopSpacing(note.isPinned)
            updateChecklistTopBar(state)
        }

        val textForStats = note.content.trim()

        binding.charactersValueTextView.text = getString(
            R.string.statistics_value,
            textForStats.length,
        )
        binding.wordsValueTextView.text = getString(
            R.string.statistics_value,
            countWords(textForStats),
        )
        binding.linesValueTextView.text = getString(
            R.string.statistics_value,
            countLines(textForStats),
        )
    }


    private fun renderChecklistItems(state: ViewNoteUiState) {
        checklistRenderer.render(state, draggedChecklistItemKey)
        binding.contentTextView.visibility = View.GONE
    }

    private fun isChecklistSelectionMode(): Boolean {
        return latestUiState.checklist.hasSelection
    }

    private fun currentChecklistIndexForRow(row: View): Int? {
        val key = row.tag as? Long ?: return null
        return viewModel.checklistIndexForKey(key)
    }

    private fun findChecklistRowByKey(key: Long): View? {
        for (childIndex in 0 until binding.checklistItemsContainer.childCount) {
            val child = binding.checklistItemsContainer.getChildAt(childIndex)
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
            if (isChecklistSelectionMode()) {
                toggleChecklistItemSelection(currentIndex)
            } else {
                toggleChecklistItemDone(currentIndex)
            }
        }
    }

    private fun handleChecklistRowLongClick(row: View): Boolean {
        return currentChecklistIndexForRow(row)?.let { currentIndex ->
            handleChecklistItemLongClick(currentIndex, row)
        } ?: false
    }

    private fun handleChecklistRowDragEvent(row: View, event: DragEvent): Boolean {
        return handleChecklistItemDragEvent(event, currentChecklistIndexForRow(row))
    }

    private fun toggleChecklistItemDone(index: Int) {
        viewModel.toggleChecklistItemDone(index)
    }

    private fun handleChecklistItemLongClick(index: Int, row: View): Boolean {
        return if (isChecklistSelectionMode()) {
            startChecklistItemDrag(index, row)
            true
        } else {
            enterChecklistSelectionMode(index)
            true
        }
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
        if (!isChecklistSelectionMode()) return false

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
        val targetChildIndex = targetRow?.let { binding.checklistItemsContainer.indexOfChild(it) } ?: -1

        if (!viewModel.moveChecklistItemByKey(itemKey, toIndex)) return
        draggedChecklistItemIndex = toIndex
        didMoveDraggedChecklistItem = true
        if (draggedRow != null && targetChildIndex >= 0) {
            moveChecklistRowView(draggedRow, targetChildIndex)
        }
    }

    private fun moveChecklistRowView(row: View, targetChildIndex: Int) {
        val container = binding.checklistItemsContainer
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


    private fun enterChecklistSelectionMode(index: Int) {
        viewModel.enterChecklistSelection(index)
    }

    private fun toggleChecklistItemSelection(index: Int) {
        viewModel.toggleChecklistSelection(index)
    }

    private fun exitChecklistSelectionMode() {
        viewModel.exitChecklistSelection()
    }

    private fun updateChecklistTopBar(state: ViewNoteUiState = latestUiState) {
        val isSelectionMode = state.checklist.hasSelection
        binding.screenTitleTextView.text = if (isSelectionMode) {
            getString(R.string.selected_count_format, state.checklist.selectedCount)
        } else {
            getString(R.string.view_note_title)
        }
        binding.selectAllChecklistItemsButton.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        binding.deleteChecklistItemsButton.visibility = View.GONE
        binding.editButton.visibility = if (isSelectionMode) View.GONE else View.VISIBLE
        binding.checklistSelectionActionBar.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
    }

    private fun updateChecklistTopSpacing(isPinned: Boolean) {
        val params = binding.checklistItemsContainer.layoutParams as? ViewGroup.MarginLayoutParams
            ?: return
        val topMargin = if (isPinned) CHECKLIST_PINNED_TOP_MARGIN_DP.dp else CHECKLIST_TOP_MARGIN_DP.dp
        if (params.topMargin != topMargin) {
            params.topMargin = topMargin
            binding.checklistItemsContainer.layoutParams = params
        }
    }


    private fun showDeleteChecklistItemsConfirmation() {
        val count = latestUiState.checklist.selectedCount
        if (count == 0) return

        val dialog = BottomSheetDialog(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 20.dp, 24.dp, 16.dp)
            background = GradientDrawable().apply {
                setColor(resolveThemeColor(com.google.android.material.R.attr.colorSurface))
                cornerRadii = floatArrayOf(
                    22.dp.toFloat(), 22.dp.toFloat(),
                    22.dp.toFloat(), 22.dp.toFloat(),
                    0f, 0f,
                    0f, 0f,
                )
            }
        }

        container.addView(
            TextView(this).apply {
                text = getString(R.string.delete_notes_title)
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurface))
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        container.addView(
            TextView(this).apply {
                text = resources.getQuantityString(
                    R.plurals.delete_notes_confirmation_message,
                    count,
                    count,
                )
                textSize = 15f
                setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = 8.dp
            },
        )

        val actionsRow = LinearLayout(this).apply {
            gravity = android.view.Gravity.END
            orientation = LinearLayout.HORIZONTAL
        }
        actionsRow.addView(
            createDialogActionButton(
                text = getString(R.string.cancel_action),
                textColor = resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant),
            ) {
                dialog.dismiss()
            },
        )
        actionsRow.addView(
            createDialogActionButton(
                text = getString(R.string.delete_confirm_action),
                textColor = ContextCompat.getColor(this, R.color.priority_high),
            ) {
                dialog.dismiss()
                deleteSelectedChecklistItems()
            },
        )
        container.addView(
            actionsRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = 20.dp
            },
        )

        dialog.setContentView(container)
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
        return MaterialButton(this).apply {
            this.text = text
            setAllCaps(false)
            setTextColor(textColor)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            rippleColor = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minHeight = 0
            setPadding(14.dp, 0, 14.dp, 0)
            installAlphaPressFeedback(this)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                44.dp,
            )
        }
    }

    private fun deleteSelectedChecklistItems() {
        viewModel.deleteSelectedChecklistItems()
    }


    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()


    private fun countWords(text: String): Int {
        return Regex("\\S+").findAll(text).count()
    }

    private fun countLines(text: String): Int {
        if (text.isBlank()) return 0
        return text.split('\n').size
    }

    private fun deadlineFormatter(): SimpleDateFormat {
        return SimpleDateFormat(DEADLINE_DATE_PATTERN, currentLocale())
    }

    private fun isDeadlineOverdue(deadlineAt: Long): Boolean {
        return deadlineAt < System.currentTimeMillis()
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

    companion object {
        private const val COMPLETED_ITEM_ALPHA = 0.45f
        private const val DRAGGED_ITEM_ALPHA = 0f
        private const val DRAGGED_ITEM_SCALE = 1.03f
        private const val CHECKLIST_DRAG_ANIMATION_MS = 120L
        private const val CHECKLIST_REORDER_ANIMATION_MS = 160L
        private const val CHECKLIST_TOP_MARGIN_DP = 10
        private const val CHECKLIST_PINNED_TOP_MARGIN_DP = 18
        private const val CHECKLIST_DRAG_LABEL = "notavia_checklist_item"
        private const val DEADLINE_DATE_PATTERN = "d MMM yyyy"
    }
}
