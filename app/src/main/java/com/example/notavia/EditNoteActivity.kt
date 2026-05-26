package com.example.notavia

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.notavia.data.Note
import com.example.notavia.data.NoteCategories
import com.example.notavia.data.NotePriority
import com.example.notavia.data.NoteRepository
import com.example.notavia.data.NotaviaDatabase
import com.example.notavia.databinding.ActivityEditNoteBinding
import com.example.notavia.settings.CategoryPreferences
import com.example.notavia.ui.NotePriorityUi
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EditNoteActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditNoteBinding
    private lateinit var repository: NoteRepository
    private lateinit var categoryPreferences: CategoryPreferences

    private var noteId: Long = NO_NOTE_ID
    private var existingNote: Note? = null
    private val selectedCategories = linkedSetOf(NoteCategories.DEFAULT)
    private val categoryButtons = mutableMapOf<String, MaterialButton>()
    private val customCategories = linkedSetOf<String>()
    private val hiddenCategories = linkedSetOf<String>()
    private var selectedPriority: NotePriority = NotePriority.NONE
    private var isApplyingLoadedNote: Boolean = false
    private var autoSaveDelayJob: Job? = null
    private var autoSaveJob: Job? = null
    private var pendingSaveAfterCurrent: Boolean = false
    private var lastSavedDraft: NoteDraft? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEditNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val bottomInset = if (isImeVisible) {
                maxOf(systemBars.bottom, ime.bottom)
            } else {
                systemBars.bottom
            }
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomInset)
            if (isImeVisible && binding.contentEditText.hasFocus()) {
                scrollToContentCursor()
            }
            if (!isImeVisible && hasEditorFocus()) {
                clearEditorFocus()
            }
            insets
        }

        repository = NoteRepository(NotaviaDatabase.getDatabase(this).noteDao())
        categoryPreferences = CategoryPreferences(this)
        noteId = intent.getLongExtra(EXTRA_NOTE_ID, NO_NOTE_ID)

        setupActions()
        setupCategoryPicker()
        updatePriorityUi()

        if (noteId != NO_NOTE_ID) {
            loadNote()
        } else {
            binding.screenTitleTextView.text = getString(R.string.new_note_title)
            focusTitleField()
        }

        setupBackHandling()
    }

    private fun setupActions() {
        binding.backButton.setOnClickListener {
            finishAfterAutoSave()
        }

        binding.priorityButton.setOnClickListener {
            showPriorityMenu()
        }
        binding.priorityButton.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.alpha = PRIORITY_BUTTON_PRESSED_ALPHA
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> view.alpha = 1f
            }
            false
        }

        binding.titleEditText.doAfterTextChanged {
            scheduleAutoSave()
        }
        binding.contentEditText.doAfterTextChanged {
            scrollToContentCursor()
            scheduleAutoSave()
        }
        binding.contentEditText.setOnClickListener {
            scrollToContentCursor()
        }
        binding.contentEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                scrollToContentCursor()
            }
        }
    }

    private fun setupCategoryPicker() {
        renderCategoryButtons()

        observeHiddenCategories()
        observeCustomCategories()
        loadCustomCategoryOptions()
        updateCategoryUi()
    }

    private fun showPriorityMenu() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(resolveThemeColor(com.google.android.material.R.attr.colorSurface))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), ContextCompat.getColor(this@EditNoteActivity, R.color.note_stroke_color))
            }
            clipToOutline = true
        }

        var popupWindow: PopupWindow? = null
        listOf(
            NotePriority.NONE,
            NotePriority.HIGH,
            NotePriority.MEDIUM,
            NotePriority.LOW,
        ).forEach { priority ->
            container.addView(
                createPriorityRow(priority) {
                    selectedPriority = priority
                    updatePriorityUi()
                    scheduleAutoSave()
                    popupWindow?.dismiss()
                },
            )
        }

        popupWindow = PopupWindow(
            container,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
        }
        popupWindow.showAsDropDown(binding.priorityButton, 0, dp(2))
    }

    private fun createPriorityRow(
        priority: NotePriority,
        onClick: () -> Unit,
    ): View {
        val selectableBackground = TypedValue()
        theme.resolveAttribute(
            android.R.attr.selectableItemBackground,
            selectableBackground,
            true,
        )

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            minimumWidth = dp(166)
            background = ContextCompat.getDrawable(this@EditNoteActivity, selectableBackground.resourceId)
            setPadding(dp(14), dp(10), dp(16), dp(10))
            setOnClickListener { onClick() }

            addView(
                ImageView(this@EditNoteActivity).apply {
                    setImageResource(R.drawable.circle)
                    NotePriorityUi.applyTo(this, priority)
                },
                LinearLayout.LayoutParams(dp(12), dp(12)),
            )
            addView(
                TextView(this@EditNoteActivity).apply {
                    text = getString(NotePriorityUi.labelRes(priority))
                    textSize = 14f
                    setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurface))
                    typeface = if (priority == selectedPriority) {
                        Typeface.DEFAULT_BOLD
                    } else {
                        Typeface.DEFAULT
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginStart = dp(10)
                },
            )
        }
    }

    private fun updatePriorityUi() {
        if (selectedPriority == NotePriority.NONE) {
            binding.priorityButton.setImageResource(R.drawable.addpriority)
            binding.priorityButton.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.selection_stroke_color),
            )
            binding.priorityButton.contentDescription = getString(
                R.string.priority_content_description,
                getString(NotePriorityUi.labelRes(NotePriority.NONE)),
            )
        } else {
            binding.priorityButton.setImageResource(R.drawable.circle)
            NotePriorityUi.applyTo(binding.priorityButton, selectedPriority)
        }
    }

    private fun showAddCategoryDialog() {
        val input = AppCompatEditText(this).apply {
            hint = getString(R.string.category_name_hint)
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 1
            setSingleLine(true)
            textSize = 16f
        }
        val inputContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(6), dp(24), 0)
            addView(
                input,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(48),
                ),
            )
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.custom_category_hint)
            .setView(inputContainer)
            .setPositiveButton(R.string.add_category_action, null)
            .setNegativeButton(R.string.cancel_action, null)
            .create()

        fun submitCategory(): Boolean {
            val rawCategory = input.text?.toString()?.trim().orEmpty()
            if (rawCategory.isBlank()) return false

            addCustomCategory(rawCategory)
            dialog.dismiss()
            return true
        }

        input.setOnEditorActionListener { _, actionId, event ->
            val isKeyboardDone = actionId == EditorInfo.IME_ACTION_DONE
            val isEnterUp = event?.let {
                it.keyCode == KeyEvent.KEYCODE_ENTER && it.action == KeyEvent.ACTION_UP
            } == true
            if (!isKeyboardDone && !isEnterUp) {
                return@setOnEditorActionListener false
            }

            submitCategory()
        }
        input.setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_ENTER || event.action != KeyEvent.ACTION_UP) {
                return@setOnKeyListener false
            }

            submitCategory()
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                submitCategory()
            }
            input.requestFocus()
            input.post {
                val inputMethodManager = getSystemService<InputMethodManager>()
                inputMethodManager?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        dialog.show()
    }

    private fun observeHiddenCategories() {
        lifecycleScope.launch {
            categoryPreferences.hiddenCategoriesFlow.collect { categories ->
                hiddenCategories.clear()
                hiddenCategories.addAll(categories)
                renderCategoryButtons()
            }
        }
    }

    private fun observeCustomCategories() {
        lifecycleScope.launch {
            categoryPreferences.customCategoriesFlow.collect { categories ->
                customCategories.addAll(categories)
                renderCategoryButtons()
            }
        }
    }

    private fun loadCustomCategoryOptions() {
        lifecycleScope.launch {
            val enteredCategories = customCategories.toList()
            customCategories.clear()
            customCategories.addAll(enteredCategories)
            customCategories.addAll(
                repository.getAllNotes()
                    .flatMap { NoteCategories.parse(it.category) }
                    .filterNot { NoteCategories.isStandard(it) || it == NoteCategories.ALL },
            )
            renderCategoryButtons()
        }
    }

    private fun renderCategoryButtons() {
        categoryButtons.clear()
        binding.categoryButtonsContainer.removeAllViews()
        addCategoryAddButton()

        val categories = (NoteCategories.STANDARD + customCategories + selectedCategories)
            .map { NoteCategories.normalize(it) }
            .filterNot { hiddenCategories.contains(it) && !selectedCategories.contains(it) }
            .distinct()

        categories.forEach { category ->
            addCategoryButton(category)
        }
        updateCategoryButtons()
    }

    private fun addCategoryAddButton() {
        val selectableBackground = TypedValue()
        theme.resolveAttribute(
            android.R.attr.selectableItemBackgroundBorderless,
            selectableBackground,
            true,
        )
        val button = AppCompatImageButton(this).apply {
            setImageResource(R.drawable.addplusfilter)
            background = ContextCompat.getDrawable(this@EditNoteActivity, selectableBackground.resourceId)
            contentDescription = getString(R.string.custom_category_hint)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(7), dp(7), dp(7), dp(7))
            setColorFilter(ContextCompat.getColor(this@EditNoteActivity, R.color.note_stroke_color))
            setOnClickListener {
                showAddCategoryDialog()
            }
        }
        val params = LinearLayout.LayoutParams(
            dp(32),
            dp(32),
        ).apply {
            marginEnd = dp(8)
        }
        binding.categoryButtonsContainer.addView(button, params)
    }

    private fun addCategoryButton(category: String) {
        val button = MaterialButton(this).apply {
            text = category
            setAllCaps(false)
            minWidth = 0
            minHeight = 0
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(15)
            textSize = 13f
            setPadding(dp(14), 0, dp(14), 0)
            setOnClickListener {
                toggleCategory(category)
                updateCategoryUi()
                scheduleAutoSave()
            }
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            dp(36),
        ).apply {
            marginEnd = dp(8)
        }
        binding.categoryButtonsContainer.addView(button, params)
        categoryButtons[category] = button
    }

    private fun addCustomCategory(rawCategory: String) {
        if (rawCategory.isBlank()) return

        val category = NoteCategories.normalize(rawCategory)
        if (category == NoteCategories.ALL) return

        if (!NoteCategories.isStandard(category)) {
            customCategories.add(category)
        }
        restoreHiddenCategory(category)
        selectCategory(category)
        lifecycleScope.launch {
            categoryPreferences.setCustomCategories(customCategories)
        }
        renderCategoryButtons()
        updateCategoryUi()
        scheduleAutoSave()
    }

    private fun restoreHiddenCategory(category: String) {
        val normalizedCategory = NoteCategories.normalize(category)
        if (!hiddenCategories.remove(normalizedCategory)) return

        lifecycleScope.launch {
            categoryPreferences.setHiddenCategories(hiddenCategories)
        }
    }

    private fun toggleCategory(category: String) {
        val normalizedCategory = NoteCategories.normalize(category)
        if (normalizedCategory == NoteCategories.DEFAULT) {
            selectedCategories.clear()
            selectedCategories.add(NoteCategories.DEFAULT)
            return
        }
        if (normalizedCategory == NoteCategories.ALL) return

        selectedCategories.remove(NoteCategories.DEFAULT)
        if (selectedCategories.contains(normalizedCategory)) {
            selectedCategories.remove(normalizedCategory)
        } else {
            selectedCategories.add(normalizedCategory)
        }
        if (selectedCategories.isEmpty()) {
            selectedCategories.add(NoteCategories.DEFAULT)
        }
    }

    private fun selectCategory(category: String) {
        val normalizedCategory = NoteCategories.normalize(category)
        if (normalizedCategory == NoteCategories.DEFAULT) {
            selectedCategories.clear()
            selectedCategories.add(NoteCategories.DEFAULT)
            return
        }
        if (normalizedCategory == NoteCategories.ALL) return

        selectedCategories.remove(NoteCategories.DEFAULT)
        selectedCategories.add(normalizedCategory)
    }

    private fun loadNote() {
        lifecycleScope.launch {
            val note = repository.getNoteById(noteId) ?: run {
                finish()
                return@launch
            }

            existingNote = note
            isApplyingLoadedNote = true
            try {
                binding.screenTitleTextView.text = getString(R.string.edit_note_title)
                binding.titleEditText.setText(note.title)
                binding.contentEditText.setText(note.content)
                selectedPriority = NotePriority.fromStorage(note.priority)
                updatePriorityUi()
                selectedCategories.clear()
                selectedCategories.addAll(NoteCategories.parse(note.category))
                updateCategoryUi()
                lastSavedDraft = currentDraft()
            } finally {
                isApplyingLoadedNote = false
            }
        }
    }

    private fun scheduleAutoSave() {
        if (isApplyingLoadedNote) return

        autoSaveDelayJob?.cancel()
        autoSaveDelayJob = lifecycleScope.launch {
            delay(AUTO_SAVE_DELAY_MS)
            requestAutoSaveNow()
        }
    }

    private fun requestAutoSaveNow() {
        if (isApplyingLoadedNote) return

        if (autoSaveJob?.isActive == true) {
            pendingSaveAfterCurrent = true
            return
        }

        autoSaveJob = lifecycleScope.launch {
            do {
                pendingSaveAfterCurrent = false
                persistCurrentNote()
            } while (pendingSaveAfterCurrent)
        }
    }

    private fun flushAutoSaveThen(onComplete: () -> Unit = {}) {
        autoSaveDelayJob?.cancel()
        if (autoSaveJob?.isActive == true) {
            pendingSaveAfterCurrent = true
            lifecycleScope.launch {
                autoSaveJob?.join()
                onComplete()
            }
            return
        }

        autoSaveJob = lifecycleScope.launch {
            persistCurrentNote()
            onComplete()
        }
    }

    private suspend fun persistCurrentNote() {
        val draft = currentDraft()
        if (draft == lastSavedDraft) return
        if (existingNote == null && draft.title.isBlank() && draft.content.isBlank()) return

        val now = System.currentTimeMillis()
        val noteToSave = existingNote?.copy(
            title = draft.title,
            content = draft.content,
            category = draft.category,
            priority = draft.priority,
            updatedAt = now,
        ) ?: Note(
            title = draft.title,
            content = draft.content,
            category = draft.category,
            priority = draft.priority,
            createdAt = now,
            updatedAt = now,
        )

        val savedId = repository.saveNote(noteToSave)
        existingNote = noteToSave.copy(id = savedId)
        noteId = savedId
        lastSavedDraft = draft
    }

    private fun currentDraft(): NoteDraft {
        return NoteDraft(
            title = binding.titleEditText.text?.toString()?.trim().orEmpty(),
            content = binding.contentEditText.text?.toString().orEmpty(),
            category = NoteCategories.serialize(selectedCategories),
            priority = selectedPriority.storageValue,
        )
    }

    private fun focusTitleField() {
        binding.titleEditText.requestFocus()
        binding.titleEditText.post {
            val inputMethodManager = getSystemService<InputMethodManager>()
            inputMethodManager?.showSoftInput(
                binding.titleEditText,
                InputMethodManager.SHOW_IMPLICIT,
            )
        }
    }

    override fun onPause() {
        flushAutoSaveThen()
        clearEditorFocus()
        super.onPause()
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasEditorFocus()) {
                    clearEditorFocus()
                } else {
                    finishAfterAutoSave()
                }
            }
        })
    }

    private fun finishAfterAutoSave() {
        flushAutoSaveThen {
            finish()
        }
    }

    private fun hasEditorFocus(): Boolean {
        return binding.titleEditText.hasFocus() ||
            binding.contentEditText.hasFocus()
    }

    private fun clearEditorFocus() {
        val focusedView = currentFocus ?: binding.contentEditText
        binding.titleEditText.clearFocus()
        binding.contentEditText.clearFocus()
        binding.main.requestFocus()
        val inputMethodManager = getSystemService<InputMethodManager>()
        inputMethodManager?.hideSoftInputFromWindow(focusedView.windowToken, 0)
    }

    private fun scrollToContentCursor() {
        binding.contentEditText.post {
            val layout = binding.contentEditText.layout ?: return@post
            val cursorPosition = binding.contentEditText.selectionStart.coerceAtLeast(0)
            val cursorLine = layout.getLineForOffset(cursorPosition)
            val cursorTop = binding.contentEditText.top + layout.getLineTop(cursorLine)
            val cursorBottom = binding.contentEditText.top + layout.getLineBottom(cursorLine) + dp(72)
            val visibleTop = binding.contentScrollView.scrollY
            val visibleBottom = visibleTop + binding.contentScrollView.height - binding.contentScrollView.paddingBottom

            when {
                cursorBottom > visibleBottom -> {
                    binding.contentScrollView.smoothScrollTo(
                        0,
                        cursorBottom - binding.contentScrollView.height + binding.contentScrollView.paddingBottom,
                    )
                }
                cursorTop < visibleTop -> {
                    binding.contentScrollView.smoothScrollTo(0, cursorTop)
                }
            }
        }
    }

    private fun updateCategoryUi() {
        val customSelectedCategories = selectedCategories
            .map { NoteCategories.normalize(it) }
            .filterNot { NoteCategories.isStandard(it) }
        customSelectedCategories.forEach { category ->
            customCategories.add(category)
            if (!categoryButtons.containsKey(category)) {
                renderCategoryButtons()
            }
        }

        updateCategoryButtons()
    }

    private fun updateCategoryButtons() {
        categoryButtons.forEach { (category, button) ->
            styleCategoryButton(
                button,
                isActive = selectedCategories.contains(NoteCategories.normalize(category)),
            )
        }
    }

    private fun styleCategoryButton(button: MaterialButton, isActive: Boolean) {
        button.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                if (isActive) R.color.selection_stroke_color else android.R.color.transparent,
            ),
        )
        button.setTextColor(
            ContextCompat.getColor(
                this,
                if (isActive) R.color.light_on_primary else R.color.note_stroke_color,
            ),
        )
        button.strokeWidth = if (isActive) 0 else dp(1)
        button.strokeColor = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.note_stroke_color),
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun resolveThemeColor(attr: Int): Int {
        val typedValue = TypedValue()
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
        private const val PRIORITY_BUTTON_PRESSED_ALPHA = 0.68f
    }

    private data class NoteDraft(
        val title: String,
        val content: String,
        val category: String,
        val priority: String,
    )
}
