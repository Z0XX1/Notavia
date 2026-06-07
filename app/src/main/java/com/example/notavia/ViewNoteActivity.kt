package com.example.notavia

import android.animation.LayoutTransition
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.notavia.checklist.ChecklistState
import com.example.notavia.data.ChecklistItem
import com.example.notavia.data.Note
import com.example.notavia.data.NoteCategories
import com.example.notavia.data.NotePriority
import com.example.notavia.data.NoteRepository
import com.example.notavia.data.NoteType
import com.example.notavia.data.NotaviaDatabase
import com.example.notavia.databinding.ActivityViewNoteBinding
import com.example.notavia.ui.NoteCategoryUi
import com.example.notavia.ui.NotePriorityUi
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// Экран просмотра обычной заметки и интерактивного чек-листа.
class ViewNoteActivity : NotaviaActivity() {
    // Состояние открытой записи, пунктов чек-листа и автосохранения чек-листа.
    private lateinit var binding: ActivityViewNoteBinding
    private lateinit var repository: NoteRepository

    private var noteId: Long = NO_NOTE_ID
    private var currentNote: Note? = null
    private var currentNoteType: NoteType = NoteType.NOTE
    private val checklistItems = ChecklistState()
    private var autoSaveDelayJob: Job? = null
    private var autoSaveJob: Job? = null
    private var pendingSaveAfterCurrent: Boolean = false
    private var isApplyingLoadedNote: Boolean = false
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
        setupChecklistContainerAnimation()

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        repository = NoteRepository(NotaviaDatabase.getDatabase(this).noteDao())
        noteId = intent.getLongExtra(EXTRA_NOTE_ID, NO_NOTE_ID)

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
        loadNote()
    }

    override fun onPause() {
        flushChecklistAutoSave()
        super.onPause()
    }

    // Подключение действий просмотра, редактирования, добавления и выбора пунктов чек-листа.
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
                putExtra(EditNoteActivity.EXTRA_NOTE_ID, noteId)
            }
            startActivity(intent)
        }

        binding.selectAllChecklistItemsButton.setOnClickListener {
            checklistItems.selectAllOrClear()
            renderChecklistItems()
            updateChecklistTopBar()
        }

        binding.deleteChecklistItemsButton.setOnClickListener {
            showDeleteChecklistItemsConfirmation()
        }

        binding.deleteChecklistSelectionButton.setOnClickListener {
            showDeleteChecklistItemsConfirmation()
        }

        binding.titleTextView.doAfterTextChanged {
            if (currentNoteType == NoteType.CHECKLIST && !isApplyingLoadedNote) {
                scheduleChecklistAutoSave()
            }
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

    // Загрузка заметки по ID перед отображением на экране просмотра.
    private fun loadNote() {
        lifecycleScope.launch {
            val note = repository.getNoteById(noteId) ?: run {
                finish()
                return@launch
            }
            bindNote(note)
        }
    }

    private fun saveCurrentNote() {
        val note = currentNote ?: return
        currentFocus?.clearFocus()

        val updatedNote = if (currentNoteType == NoteType.CHECKLIST) {
            note.copy(
                title = binding.titleTextView.text?.toString()?.trim().orEmpty(),
                content = checklistItems.serialize(),
                category = NoteCategories.DEFAULT,
                priority = NotePriority.NONE.storageValue,
                deadlineAt = null,
                updatedAt = System.currentTimeMillis(),
            )
        } else {
            note.copy(
                title = binding.titleTextView.text?.toString()?.trim().orEmpty(),
                content = binding.contentTextView.text?.toString().orEmpty(),
                updatedAt = System.currentTimeMillis(),
            )
        }

        lifecycleScope.launch {
            repository.saveNote(updatedNote)
            currentNote = updatedNote
            finish()
        }
    }

    // Отложенное автосохранение изменений чек-листа.
    private fun scheduleChecklistAutoSave() {
        if (isApplyingLoadedNote || currentNoteType != NoteType.CHECKLIST) return

        autoSaveDelayJob?.cancel()
        autoSaveDelayJob = lifecycleScope.launch {
            delay(AUTO_SAVE_DELAY_MS)
            requestChecklistAutoSaveNow()
        }
    }

    private fun requestChecklistAutoSaveNow() {
        if (currentNoteType != NoteType.CHECKLIST) return

        if (autoSaveJob?.isActive == true) {
            pendingSaveAfterCurrent = true
            return
        }

        autoSaveJob = lifecycleScope.launch {
            do {
                pendingSaveAfterCurrent = false
                persistChecklist()
            } while (pendingSaveAfterCurrent)
        }
    }

    // Сохранение измененного чек-листа в поле content текущей записи.
    private suspend fun persistChecklist() {
        val note = currentNote ?: return
        if (currentNoteType != NoteType.CHECKLIST) return

        val updatedNote = note.copy(
            title = binding.titleTextView.text?.toString()?.trim().orEmpty(),
            content = checklistItems.serialize(),
            category = NoteCategories.DEFAULT,
            priority = NotePriority.NONE.storageValue,
            deadlineAt = null,
            updatedAt = System.currentTimeMillis(),
        )
        repository.saveNote(updatedNote)
        currentNote = updatedNote
    }

    private fun flushChecklistAutoSave() {
        autoSaveDelayJob?.cancel()
        if (autoSaveJob?.isActive == true) {
            pendingSaveAfterCurrent = true
            return
        }
        if (currentNoteType == NoteType.CHECKLIST) {
            autoSaveJob = lifecycleScope.launch {
                persistChecklist()
            }
        }
    }

    // Заполнение экрана данными заметки или чек-листа.
    private fun bindNote(note: Note) {
        isApplyingLoadedNote = true
        val noteType = NoteType.fromStorage(note.type)
        currentNote = note
        currentNoteType = noteType
        val isChecklist = noteType == NoteType.CHECKLIST
        checklistItems.exitSelection()
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
        if (noteType == NoteType.CHECKLIST) {
            checklistItems.replaceWithContent(note.content)
            resetChecklistItemKeys()
            binding.contentTextView.visibility = View.GONE
            binding.checklistItemsContainer.visibility = View.VISIBLE
            renderChecklistItems()
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
        isApplyingLoadedNote = false
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

    // Отрисовка пунктов чек-листа и разделов выполнения.
    private fun renderChecklistItems() {
        ensureChecklistItemKeys()
        binding.checklistItemsContainer.removeAllViews()
        binding.checklistItemsContainer.visibility = View.VISIBLE
        binding.contentTextView.visibility = View.GONE

        val incompleteItems = checklistItems.incompleteItems()
        val completedItems = checklistItems.completedItems()

        renderChecklistSection(
            title = getString(R.string.checklist_incomplete_title),
            indexedItems = incompleteItems,
            isFirstSection = true,
        )
        renderChecklistSection(
            title = getString(R.string.checklist_completed_title),
            indexedItems = completedItems,
            isFirstSection = incompleteItems.isEmpty(),
        )
    }

    private fun renderChecklistSection(
        title: String,
        indexedItems: List<IndexedValue<ChecklistItem>>,
        isFirstSection: Boolean,
    ) {
        if (indexedItems.isEmpty()) return

        binding.checklistItemsContainer.addView(
            createSectionHeader(title, isFirstSection),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        indexedItems.forEach { (index, item) ->
            val itemKey = checklistItems.keyAt(index) ?: return@forEach
            val isDraggedItem = draggedChecklistItemKey == itemKey
            val row = LinearLayout(this).apply {
                tag = itemKey
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 4.dp, 0, 4.dp)
                alpha = when {
                    isDraggedItem -> DRAGGED_ITEM_ALPHA
                    item.isDone -> COMPLETED_ITEM_ALPHA
                    else -> 1f
                }
                scaleX = if (isDraggedItem) DRAGGED_ITEM_SCALE else 1f
                scaleY = if (isDraggedItem) DRAGGED_ITEM_SCALE else 1f
                setOnTouchListener { touchedView, event ->
                    recordChecklistDragTouch(touchedView, event)
                    false
                }
                setOnClickListener {
                    currentChecklistIndexForRow(this)?.let { currentIndex ->
                        if (isChecklistSelectionMode()) {
                            toggleChecklistItemSelection(currentIndex)
                        } else {
                            toggleChecklistItemDone(currentIndex)
                        }
                    }
                }
                setOnLongClickListener {
                    currentChecklistIndexForRow(this)?.let { currentIndex ->
                        handleChecklistItemLongClick(currentIndex, this)
                    } ?: false
                }
                setOnDragListener { _, event ->
                    handleChecklistItemDragEvent(event, currentChecklistIndexForRow(this))
                }
            }

            row.addView(
                AppCompatImageButton(this).apply {
                    setImageResource(
                        when {
                            checklistItems.isSelected(index) -> R.drawable.checkcircle
                            isChecklistSelectionMode() -> R.drawable.emptycircle
                            item.isDone -> R.drawable.checkbox
                            else -> R.drawable.emptybox
                        },
                    )
                    background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
                    setColorFilter(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                    contentDescription = item.text
                    setPadding(10.dp, 10.dp, 10.dp, 10.dp)
                    installAlphaPressFeedback(this)
                    setOnClickListener {
                        val currentIndex = currentChecklistIndexForRow(row) ?: return@setOnClickListener
                        if (isChecklistSelectionMode()) {
                            toggleChecklistItemSelection(currentIndex)
                        } else {
                            toggleChecklistItemDone(currentIndex)
                        }
                    }
                },
                LinearLayout.LayoutParams(44.dp, 44.dp),
            )

            row.addView(
                AppCompatEditText(this).apply {
                    setText(item.text)
                    textSize = 18f
                    background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
                    inputType = InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                        InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    setSingleLine(false)
                    setHorizontallyScrolling(false)
                    minLines = 1
                    maxLines = Int.MAX_VALUE
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    isEnabled = true
                    isFocusable = false
                    isFocusableInTouchMode = false
                    isCursorVisible = false
                    setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnBackground))
                    setHintTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                    paintFlags = if (item.isDone) {
                        paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    } else {
                        paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    }
                    setOnFocusChangeListener { view, hasFocus ->
                        if (!hasFocus && view.hasFocus()) {
                            currentChecklistIndexForRow(row)?.let { currentIndex ->
                                updateChecklistItemText(currentIndex, (view as EditText).text.toString())
                            }
                        }
                    }
                    setOnTouchListener { _, event ->
                        recordChecklistDragTouch(row, event)
                        false
                    }
                    setOnClickListener {
                        currentChecklistIndexForRow(row)?.let { currentIndex ->
                            if (isChecklistSelectionMode()) {
                                toggleChecklistItemSelection(currentIndex)
                            } else {
                                toggleChecklistItemDone(currentIndex)
                            }
                        }
                    }
                    doAfterTextChanged { editable ->
                        if (hasFocus() && !isChecklistSelectionMode()) {
                            currentChecklistIndexForRow(row)?.let { currentIndex ->
                                updateChecklistItemText(currentIndex, editable?.toString().orEmpty())
                                scheduleChecklistAutoSave()
                            }
                        }
                    }
                    setOnLongClickListener {
                        currentChecklistIndexForRow(row)?.let { currentIndex ->
                            handleChecklistItemLongClick(currentIndex, row)
                        } ?: false
                    }
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )

            binding.checklistItemsContainer.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun createSectionHeader(title: String, isFirstSection: Boolean): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, if (isFirstSection) 2.dp else 14.dp, 0, 4.dp)

            addView(
                TextView(this@ViewNoteActivity).apply {
                    text = title
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                View(this@ViewNoteActivity).apply {
                    setBackgroundColor(resolveThemeColor(com.google.android.material.R.attr.colorOutline))
                },
                LinearLayout.LayoutParams(0, 1.dp, 1f).apply {
                    marginStart = 12.dp
                },
            )
        }
    }

    private fun isChecklistSelectionMode(): Boolean {
        return checklistItems.hasSelection()
    }

    private fun resetChecklistItemKeys() {
        checklistItems.resetKeys()
    }

    private fun ensureChecklistItemKeys() {
        checklistItems.ensureKeys()
    }

    private fun currentChecklistIndexForRow(row: View): Int? {
        val key = row.tag as? Long ?: return null
        return checklistItems.indexForKey(key)
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

    private fun toggleChecklistItemDone(index: Int) {
        if (!checklistItems.toggleDone(index)) return

        renderChecklistItems()
        updateChecklistTopBar()
        scheduleChecklistAutoSave()
    }

    private fun handleChecklistItemLongClick(index: Int, row: View): Boolean {
        if (!checklistItems.containsIndex(index)) return false

        return if (isChecklistSelectionMode()) {
            startChecklistItemDrag(index, row)
            true
        } else {
            enterChecklistSelectionMode(index)
            true
        }
    }

    private fun startChecklistItemDrag(index: Int, row: View) {
        if (!checklistItems.containsIndex(index)) return

        val itemKey = checklistItems.keyAt(index) ?: return
        if (!checklistItems.isSelected(index)) {
            checklistItems.enterSelection(index)
            updateChecklistTopBar()
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
        val targetKey = checklistItems.keyAt(toIndex) ?: return
        val draggedRow = draggedChecklistItemView ?: findChecklistRowByKey(itemKey)
        val targetRow = findChecklistRowByKey(targetKey)
        val targetChildIndex = targetRow?.let { binding.checklistItemsContainer.indexOfChild(it) } ?: -1

        if (!checklistItems.moveByKey(itemKey, toIndex)) return
        draggedChecklistItemIndex = toIndex
        didMoveDraggedChecklistItem = true
        if (draggedRow != null && targetChildIndex >= 0) {
            moveChecklistRowView(draggedRow, targetChildIndex)
        }
        updateChecklistTopBar()
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
            checklistItems.itemAt(finalIndex)?.isDone == true
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
        updateChecklistTopBar()
        if (shouldSave) {
            scheduleChecklistAutoSave()
        }
    }

    // Режим выбора пунктов чек-листа для массового удаления.
    private fun enterChecklistSelectionMode(index: Int) {
        checklistItems.enterSelection(index)
        renderChecklistItems()
        updateChecklistTopBar()
    }

    private fun toggleChecklistItemSelection(index: Int) {
        val hasSelection = checklistItems.toggleSelection(index)
        if (!hasSelection) {
            exitChecklistSelectionMode()
        } else {
            renderChecklistItems()
            updateChecklistTopBar()
        }
    }

    private fun exitChecklistSelectionMode() {
        checklistItems.exitSelection()
        renderChecklistItems()
        updateChecklistTopBar()
    }

    private fun updateChecklistTopBar() {
        val isSelectionMode = isChecklistSelectionMode()
        binding.screenTitleTextView.text = if (isSelectionMode) {
            getString(R.string.selected_count_format, checklistItems.selectedCount)
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

    // Подтверждение удаления выбранных пунктов чек-листа.
    private fun showDeleteChecklistItemsConfirmation() {
        val count = checklistItems.selectedCount
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
        if (!checklistItems.removeSelected()) return

        renderChecklistItems()
        updateChecklistTopBar()
        scheduleChecklistAutoSave()
    }

    // Нижняя строка ввода нового пункта чек-листа.
    private fun addChecklistInputRow() {
        if (isChecklistSelectionMode()) return

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 10.dp, 0, 4.dp)
        }

        val input = AppCompatEditText(this).apply {
            hint = getString(R.string.checklist_item_number_hint, checklistItems.size + 1)
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 1
            background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnBackground))
            setHintTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        }
        row.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(
            AppCompatImageButton(this).apply {
                setImageResource(R.drawable.addplusbutton)
                background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
                contentDescription = getString(R.string.add_checklist_item)
                setColorFilter(resolveThemeColor(androidx.appcompat.R.attr.colorPrimary))
                setPadding(10.dp, 10.dp, 10.dp, 10.dp)
                installAlphaPressFeedback(this)
                setOnClickListener {
                    addChecklistItem(input)
                }
            },
            LinearLayout.LayoutParams(44.dp, 44.dp),
        )

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addChecklistItem(input)
                true
            } else {
                false
            }
        }

        binding.checklistItemsContainer.addView(row)
    }

    private fun addChecklistItem(input: EditText) {
        val text = input.text.toString().trim()
        if (text.isBlank()) return

        if (!checklistItems.add(text)) return

        renderChecklistItems()
        scheduleChecklistAutoSave()
    }

    private fun updateChecklistItemText(index: Int, text: String) {
        checklistItems.updateText(index, text)
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    // Статистика обычной заметки: слова считаются как непробельные последовательности.
    private fun countWords(text: String): Int {
        return Regex("\\S+").findAll(text).count()
    }

    private fun countLines(text: String): Int {
        if (text.isBlank()) return 0
        return text.split('\n').size
    }

    private fun installAlphaPressFeedback(view: View) {
        view.setOnTouchListener { pressedView, event ->
            pressedView.alpha = when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> BUTTON_PRESSED_ALPHA
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> 1f
                else -> pressedView.alpha
            }
            false
        }
    }

    private fun resolveThemeColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(this, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }

    private fun deadlineFormatter(): SimpleDateFormat {
        return SimpleDateFormat(DEADLINE_DATE_PATTERN, currentLocale())
    }

    private fun currentLocale(): Locale {
        val locales = resources.configuration.locales
        return if (locales.size() > 0) locales[0] else Locale.getDefault()
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
        const val EXTRA_NOTE_ID = "extra_note_id"
        private const val NO_NOTE_ID = -1L
        private const val AUTO_SAVE_DELAY_MS = 450L
        private const val BUTTON_PRESSED_ALPHA = 0.68f
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
