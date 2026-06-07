package com.example.notavia

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.notavia.checklist.ChecklistState
import com.example.notavia.data.NoteCategories
import com.example.notavia.data.NotePriority
import com.example.notavia.data.NoteRepository
import com.example.notavia.data.NoteType
import com.example.notavia.data.NotaviaDatabase
import com.example.notavia.databinding.ActivityEditNoteBinding
import com.example.notavia.editor.ChecklistEditorRenderer
import com.example.notavia.editor.DeadlinePickerController
import com.example.notavia.editor.EditNoteEffect
import com.example.notavia.editor.EditNoteUiState
import com.example.notavia.editor.EditNoteViewModel
import com.example.notavia.editor.EditorCategoryState
import com.example.notavia.editor.NoteDraft
import com.example.notavia.navigation.NoteNavigationContract.EXTRA_NOTE_ID
import com.example.notavia.navigation.NoteNavigationContract.EXTRA_NOTE_TYPE
import com.example.notavia.navigation.NoteNavigationContract.NO_NOTE_ID
import com.example.notavia.settings.CategoryPreferences
import com.example.notavia.ui.CategoryInputDialog
import com.example.notavia.ui.NoteCategoryUi
import com.example.notavia.ui.NotePriorityUi
import com.example.notavia.ui.dp
import com.example.notavia.ui.installAlphaPressFeedback
import com.example.notavia.ui.resolveThemeColor
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch


class EditNoteActivity : NotaviaActivity() {

    private lateinit var binding: ActivityEditNoteBinding
    private lateinit var viewModel: EditNoteViewModel
    private lateinit var repository: NoteRepository
    private lateinit var categoryPreferences: CategoryPreferences
    private lateinit var checklistRenderer: ChecklistEditorRenderer
    private lateinit var deadlineController: DeadlinePickerController


    private var noteId: Long = NO_NOTE_ID
    private var selectedNoteType: NoteType = NoteType.NOTE
    private val checklistItems = ChecklistState()
    private val categoryState = EditorCategoryState()
    private val categoryButtons = mutableMapOf<String, MaterialButton>()
    private var selectedPriority: NotePriority = NotePriority.NONE
    private var isApplyingLoadedNote: Boolean = false
    private var renderedLoadedNoteId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEditNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        checklistRenderer = ChecklistEditorRenderer(
            activity = this,
            container = binding.checklistItemsContainer,
            onToggleDone = { index ->
                checklistItems.toggleDone(index)
                renderChecklistItems()
                scheduleAutoSave()
            },
            onDelete = { index ->
                checklistItems.removeAt(index)
                updateChecklistInputHint()
                renderChecklistItems()
                scheduleAutoSave()
            },
        )
        deadlineController = DeadlinePickerController(this, binding)

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
        viewModel = ViewModelProvider(
            this,
            EditNoteViewModel.Factory(repository),
        )[EditNoteViewModel::class.java]
        categoryPreferences = CategoryPreferences(this)
        noteId = intent.getLongExtra(EXTRA_NOTE_ID, NO_NOTE_ID)
        selectedNoteType = NoteType.fromStorage(intent.getStringExtra(EXTRA_NOTE_TYPE))

        observeEditorState()
        setupActions()
        setupCategoryPicker()
        setupDeadlinePicker()
        updatePriorityUi()
        updateEditorMode()

        if (noteId != NO_NOTE_ID) {
            viewModel.start(noteId, selectedNoteType)
        } else {
            binding.screenTitleTextView.text = getString(
                if (selectedNoteType == NoteType.CHECKLIST) {
                    R.string.new_checklist_title
                } else {
                    R.string.new_note_title
                },
            )
            focusTitleField()
            viewModel.start(null, selectedNoteType)
        }

        setupBackHandling()
    }


    private fun setupActions() {
        installAlphaPressFeedback(binding.backButton)

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

        installAlphaPressFeedback(binding.addChecklistItemButton)
        binding.addChecklistItemButton.setOnClickListener {
            addChecklistItemFromInput()
        }
        binding.checklistItemEditText.setOnEditorActionListener { _, actionId, event ->
            val isKeyboardDone = actionId == EditorInfo.IME_ACTION_DONE
            val isEnterUp = event?.let {
                it.keyCode == KeyEvent.KEYCODE_ENTER && it.action == KeyEvent.ACTION_UP
            } == true
            if (!isKeyboardDone && !isEnterUp) {
                return@setOnEditorActionListener false
            }

            addChecklistItemFromInput()
            true
        }
    }


    private fun updateEditorMode() {
        val isChecklist = selectedNoteType == NoteType.CHECKLIST
        binding.contentEditText.visibility = if (isChecklist) View.GONE else View.VISIBLE
        binding.checklistEditorGroup.visibility = if (isChecklist) View.VISIBLE else View.GONE
        binding.priorityButton.visibility = if (isChecklist) View.GONE else View.VISIBLE
        binding.categoryTitleTextView.visibility = if (isChecklist) View.GONE else View.VISIBLE
        binding.categoryScrollView.visibility = if (isChecklist) View.GONE else View.VISIBLE
        binding.deadlineHeader.visibility = if (isChecklist) View.GONE else View.VISIBLE
        deadlineController.hidePicker()
        if (isChecklist) {
            categoryState.resetSelectionToDefault()
            selectedPriority = NotePriority.NONE
            deadlineController.clear()
        }
        if (isChecklist) {
            updateChecklistInputHint()
            renderChecklistItems()
        }
    }

    private fun addChecklistItemFromInput() {
        val text = binding.checklistItemEditText.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) return

        checklistItems.add(text)
        binding.checklistItemEditText.text?.clear()
        updateChecklistInputHint()
        renderChecklistItems()
        scheduleAutoSave()
    }

    private fun updateChecklistInputHint() {
        binding.checklistItemEditText.hint = getString(
            R.string.checklist_item_number_hint,
            checklistItems.size + 1,
        )
    }


    private fun renderChecklistItems() {
        checklistRenderer.render(
            incompleteItems = checklistItems.incompleteItems(),
            completedItems = checklistItems.completedItems(),
        )
    }


    private fun setupCategoryPicker() {
        renderCategoryButtons()

        observeHiddenCategories()
        observeCustomCategories()
        loadCustomCategoryOptions()
        updateCategoryUi()
    }


    private fun setupDeadlinePicker() {
        deadlineController.setup {
            scheduleAutoSave()
        }
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
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            minimumWidth = dp(166)
            background = ColorDrawable(Color.TRANSPARENT)
            setPadding(dp(14), dp(10), dp(16), dp(10))
            installAlphaPressFeedback(this)
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
        CategoryInputDialog(this) { rawCategory ->
            addCustomCategory(rawCategory)
        }.show()
    }

    private fun observeHiddenCategories() {
        lifecycleScope.launch {
            categoryPreferences.hiddenCategoriesFlow.collect { categories ->
                categoryState.setHiddenCategories(categories)
                renderCategoryButtons()
            }
        }
    }

    private fun observeCustomCategories() {
        lifecycleScope.launch {
            categoryPreferences.customCategoriesFlow.collect { categories ->
                categoryState.mergeCustomCategories(categories)
                renderCategoryButtons()
            }
        }
    }

    private fun loadCustomCategoryOptions() {
        lifecycleScope.launch {
            categoryState.mergeCustomCategories(
                repository.getAllNotesSnapshot()
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

        categoryState.visibleCategories().forEach { category ->
            addCategoryButton(category)
        }
        updateCategoryButtons()
    }

    private fun addCategoryAddButton() {
        val button = AppCompatImageButton(this).apply {
            setImageResource(R.drawable.addplusfilter)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = getString(R.string.custom_category_hint)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(7), dp(7), dp(7), dp(7))
            setColorFilter(ContextCompat.getColor(this@EditNoteActivity, R.color.note_stroke_color))
            installAlphaPressFeedback(this)
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
            text = NoteCategoryUi.displayName(this@EditNoteActivity, category)
            setAllCaps(false)
            minWidth = 0
            minHeight = 0
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(15)
            textSize = 13f
            rippleColor = ColorStateList.valueOf(Color.TRANSPARENT)
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

        categoryState.addCustomCategory(category)
        restoreHiddenCategory(category)
        categoryState.select(category)
        lifecycleScope.launch {
            categoryPreferences.setCustomCategories(categoryState.custom)
        }
        renderCategoryButtons()
        updateCategoryUi()
        scheduleAutoSave()
    }

    private fun restoreHiddenCategory(category: String) {
        if (!categoryState.restoreHiddenCategory(category)) return

        lifecycleScope.launch {
            categoryPreferences.setHiddenCategories(categoryState.hidden)
        }
    }

    private fun toggleCategory(category: String) {
        categoryState.toggle(category)
    }


    private fun observeEditorState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderEditorState(state)
                    }
                }
                launch {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            EditNoteEffect.Finish -> finish()
                        }
                    }
                }
            }
        }
    }

    private fun renderEditorState(state: EditNoteUiState) {
        noteId = state.noteId ?: NO_NOTE_ID
        val note = state.loadedNote ?: return
        if (renderedLoadedNoteId == note.id) return

        renderedLoadedNoteId = note.id
        isApplyingLoadedNote = true
        try {
            binding.screenTitleTextView.text = getString(R.string.edit_note_title)
            selectedNoteType = NoteType.fromStorage(note.type)
            updateEditorMode()
            binding.titleEditText.setText(note.title)
            if (selectedNoteType == NoteType.CHECKLIST) {
                checklistItems.replaceWithContent(note.content)
                updateChecklistInputHint()
                renderChecklistItems()
            } else {
                binding.contentEditText.setText(note.content)
            }
            if (selectedNoteType == NoteType.CHECKLIST) {
                selectedPriority = NotePriority.NONE
                deadlineController.clear()
                categoryState.resetSelectionToDefault()
            } else {
                selectedPriority = NotePriority.fromStorage(note.priority)
                updatePriorityUi()
                deadlineController.setDeadline(note.deadlineAt)
                categoryState.selectStoredCategories(note.category)
                updateCategoryUi()
            }
        } finally {
            isApplyingLoadedNote = false
        }
    }


    private fun scheduleAutoSave() {
        if (isApplyingLoadedNote) return

        viewModel.scheduleAutoSave(currentDraft())
    }


    private fun requestAutoSaveNow() {
        if (isApplyingLoadedNote) return

        viewModel.requestAutoSaveNow(currentDraft())
    }

    private fun flushAutoSaveThen(onComplete: () -> Unit = {}) {
        lifecycleScope.launch {
            viewModel.flushAutoSave(currentDraft())
            onComplete()
        }
    }



    private fun currentDraft(): NoteDraft {
        return NoteDraft(
            title = binding.titleEditText.text?.toString()?.trim().orEmpty(),
            content = if (selectedNoteType == NoteType.CHECKLIST) {
                checklistItems.serialize()
            } else {
                binding.contentEditText.text?.toString().orEmpty()
            },
            category = if (selectedNoteType == NoteType.CHECKLIST) {
                NoteCategories.DEFAULT
            } else {
                categoryState.serializeSelected()
            },
            priority = if (selectedNoteType == NoteType.CHECKLIST) {
                NotePriority.NONE.storageValue
            } else {
                selectedPriority.storageValue
            },
            deadlineAt = if (selectedNoteType == NoteType.CHECKLIST) {
                null
            } else {
                deadlineController.selectedDeadlineAt
            },
            type = selectedNoteType.storageValue,
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
            binding.contentEditText.hasFocus() ||
            binding.checklistItemEditText.hasFocus()
    }

    private fun clearEditorFocus() {
        val focusedView = currentFocus ?: binding.contentEditText
        binding.titleEditText.clearFocus()
        binding.contentEditText.clearFocus()
        binding.checklistItemEditText.clearFocus()
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
        val addedCustomCategories = categoryState.includeSelectedCustomCategories()
        val missingSelectedButtons = categoryState.customSelectedCategories()
            .any { category -> !categoryButtons.containsKey(category) }
        if (addedCustomCategories || missingSelectedButtons) {
            renderCategoryButtons()
            return
        }

        updateCategoryButtons()
    }

    private fun updateCategoryButtons() {
        categoryButtons.forEach { (category, button) ->
            styleCategoryButton(
                button,
                isActive = categoryState.isSelected(category),
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

    companion object {
        private const val PRIORITY_BUTTON_PRESSED_ALPHA = 0.68f
    }


}
