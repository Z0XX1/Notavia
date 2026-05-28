package com.example.notavia

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
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
import com.example.notavia.data.ChecklistContent
import com.example.notavia.data.ChecklistItem
import com.example.notavia.data.Note
import com.example.notavia.data.NoteCategories
import com.example.notavia.data.NotePriority
import com.example.notavia.data.NoteRepository
import com.example.notavia.data.NoteType
import com.example.notavia.data.NotaviaDatabase
import com.example.notavia.databinding.ActivityViewNoteBinding
import com.example.notavia.ui.NotePriorityUi
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ViewNoteActivity : NotaviaActivity() {
    private lateinit var binding: ActivityViewNoteBinding
    private lateinit var repository: NoteRepository

    private var noteId: Long = NO_NOTE_ID
    private var currentNote: Note? = null
    private var currentNoteType: NoteType = NoteType.NOTE
    private val checklistItems = mutableListOf<ChecklistItem>()
    private val selectedChecklistItemIndexes = linkedSetOf<Int>()
    private var autoSaveDelayJob: Job? = null
    private var autoSaveJob: Job? = null
    private var pendingSaveAfterCurrent: Boolean = false
    private var isApplyingLoadedNote: Boolean = false
    private val deadlineFormatter: SimpleDateFormat by lazy {
        SimpleDateFormat(DEADLINE_DATE_PATTERN, RUSSIAN_LOCALE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityViewNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

    override fun onResume() {
        super.onResume()
        loadNote()
    }

    override fun onPause() {
        flushChecklistAutoSave()
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
                putExtra(EditNoteActivity.EXTRA_NOTE_ID, noteId)
            }
            startActivity(intent)
        }

        binding.selectAllChecklistItemsButton.setOnClickListener {
            if (selectedChecklistItemIndexes.size == checklistItems.size) {
                selectedChecklistItemIndexes.clear()
            } else {
                selectedChecklistItemIndexes.clear()
                selectedChecklistItemIndexes.addAll(checklistItems.indices)
            }
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
                content = ChecklistContent.serialize(checklistItems),
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

    private suspend fun persistChecklist() {
        val note = currentNote ?: return
        if (currentNoteType != NoteType.CHECKLIST) return

        val updatedNote = note.copy(
            title = binding.titleTextView.text?.toString()?.trim().orEmpty(),
            content = ChecklistContent.serialize(checklistItems),
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

    private fun bindNote(note: Note) {
        isApplyingLoadedNote = true
        val noteType = NoteType.fromStorage(note.type)
        currentNote = note
        currentNoteType = noteType
        val isChecklist = noteType == NoteType.CHECKLIST
        selectedChecklistItemIndexes.clear()
        binding.screenTitleTextView.setText(R.string.view_note_title)
        binding.saveButton.visibility = View.GONE
        binding.editButton.visibility = if (isChecklist) View.GONE else View.VISIBLE
        binding.selectAllChecklistItemsButton.visibility = View.GONE
        binding.deleteChecklistItemsButton.visibility = View.GONE
        binding.checklistSelectionActionBar.visibility = View.GONE
        binding.titleTextView.isFocusable = isChecklist
        binding.titleTextView.isFocusableInTouchMode = isChecklist
        binding.titleTextView.isCursorVisible = isChecklist
        binding.contentTextView.isFocusable = isChecklist
        binding.contentTextView.isFocusableInTouchMode = isChecklist
        binding.contentTextView.isCursorVisible = isChecklist
        binding.titleTextView.hint = getString(R.string.untitled_note)
        binding.titleTextView.setText(note.title)
        if (noteType == NoteType.CHECKLIST) {
            val items = ChecklistContent.parse(note.content)
            checklistItems.clear()
            checklistItems.addAll(items)
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
                NoteCategories.display(note.category),
            )
            binding.deadlineTextView.visibility = if (note.deadlineAt == null) {
                View.GONE
            } else {
                View.VISIBLE
            }
            note.deadlineAt?.let { deadline ->
                binding.deadlineTextView.text = getString(
                    R.string.deadline_format,
                    deadlineFormatter.format(Date(deadline)),
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
        binding.pinnedBadgeTextView.visibility = if (note.isPinned) {
            View.VISIBLE
        } else {
            View.GONE
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

    private fun renderChecklistItems() {
        binding.checklistItemsContainer.removeAllViews()
        binding.checklistItemsContainer.visibility = View.VISIBLE
        binding.contentTextView.visibility = View.GONE

        renderChecklistSection(
            title = getString(R.string.checklist_incomplete_title),
            indexedItems = checklistItems.withIndex().filterNot { it.value.isDone },
        )
        renderChecklistSection(
            title = getString(R.string.checklist_completed_title),
            indexedItems = checklistItems.withIndex().filter { it.value.isDone },
        )
        addChecklistInputRow()
    }

    private fun renderChecklistSection(
        title: String,
        indexedItems: List<IndexedValue<ChecklistItem>>,
    ) {
        if (indexedItems.isEmpty()) return

        binding.checklistItemsContainer.addView(
            createSectionHeader(title),
        )

        indexedItems.forEach { (index, item) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 4.dp, 0, 4.dp)
                alpha = if (item.isDone) COMPLETED_ITEM_ALPHA else 1f
                setOnClickListener {
                    if (isChecklistSelectionMode()) {
                        toggleChecklistItemSelection(index)
                    }
                }
                setOnLongClickListener {
                    enterChecklistSelectionMode(index)
                    true
                }
            }

            row.addView(
                AppCompatImageButton(this).apply {
                    setImageResource(
                        when {
                            selectedChecklistItemIndexes.contains(index) -> R.drawable.checkcircle
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
                        if (isChecklistSelectionMode()) {
                            toggleChecklistItemSelection(index)
                        } else {
                            checklistItems[index] = item.copy(isDone = !item.isDone)
                            renderChecklistItems()
                            scheduleChecklistAutoSave()
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
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    maxLines = 2
                    isEnabled = !isChecklistSelectionMode()
                    setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnBackground))
                    setHintTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                    paintFlags = if (item.isDone) {
                        paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    } else {
                        paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    }
                    setOnFocusChangeListener { view, hasFocus ->
                        if (!hasFocus) {
                            updateChecklistItemText(index, (view as EditText).text.toString())
                        }
                    }
                    doAfterTextChanged { editable ->
                        updateChecklistItemText(index, editable?.toString().orEmpty())
                        scheduleChecklistAutoSave()
                    }
                    setOnLongClickListener {
                        enterChecklistSelectionMode(index)
                        true
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

    private fun createSectionHeader(title: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 14.dp, 0, 4.dp)

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
                    alpha = 0.5f
                    setBackgroundColor(resolveThemeColor(com.google.android.material.R.attr.colorOutline))
                },
                LinearLayout.LayoutParams(0, 1.dp, 1f).apply {
                    marginStart = 12.dp
                },
            )
        }
    }

    private fun isChecklistSelectionMode(): Boolean {
        return selectedChecklistItemIndexes.isNotEmpty()
    }

    private fun enterChecklistSelectionMode(index: Int) {
        selectedChecklistItemIndexes.add(index)
        renderChecklistItems()
        updateChecklistTopBar()
    }

    private fun toggleChecklistItemSelection(index: Int) {
        if (selectedChecklistItemIndexes.contains(index)) {
            selectedChecklistItemIndexes.remove(index)
        } else {
            selectedChecklistItemIndexes.add(index)
        }

        if (selectedChecklistItemIndexes.isEmpty()) {
            exitChecklistSelectionMode()
        } else {
            renderChecklistItems()
            updateChecklistTopBar()
        }
    }

    private fun exitChecklistSelectionMode() {
        selectedChecklistItemIndexes.clear()
        renderChecklistItems()
        updateChecklistTopBar()
    }

    private fun updateChecklistTopBar() {
        val isSelectionMode = isChecklistSelectionMode()
        binding.screenTitleTextView.text = if (isSelectionMode) {
            getString(R.string.selected_count_format, selectedChecklistItemIndexes.size)
        } else {
            getString(R.string.view_note_title)
        }
        binding.selectAllChecklistItemsButton.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        binding.deleteChecklistItemsButton.visibility = View.GONE
        binding.checklistSelectionActionBar.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
    }

    private fun showDeleteChecklistItemsConfirmation() {
        val count = selectedChecklistItemIndexes.size
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
        val indexesToDelete = selectedChecklistItemIndexes.sortedDescending()
        indexesToDelete.forEach { index ->
            if (index in checklistItems.indices) {
                checklistItems.removeAt(index)
            }
        }
        selectedChecklistItemIndexes.clear()
        renderChecklistItems()
        updateChecklistTopBar()
        scheduleChecklistAutoSave()
    }

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

        checklistItems.add(ChecklistItem(text = text))
        renderChecklistItems()
        scheduleChecklistAutoSave()
    }

    private fun updateChecklistItemText(index: Int, text: String) {
        if (index !in checklistItems.indices) return

        checklistItems[index] = checklistItems[index].copy(text = text)
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

    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
        private const val NO_NOTE_ID = -1L
        private const val AUTO_SAVE_DELAY_MS = 450L
        private const val BUTTON_PRESSED_ALPHA = 0.68f
        private const val COMPLETED_ITEM_ALPHA = 0.45f
        private const val DEADLINE_DATE_PATTERN = "d MMM yyyy"
        private val RUSSIAN_LOCALE: Locale = Locale.forLanguageTag("ru")
    }
}
