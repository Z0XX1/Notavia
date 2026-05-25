package com.example.notavia

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.notavia.data.Note
import com.example.notavia.data.NoteCategories
import com.example.notavia.data.NoteRepository
import com.example.notavia.data.NotaviaDatabase
import com.example.notavia.databinding.ActivityEditNoteBinding
import com.example.notavia.settings.CategoryPreferences
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class EditNoteActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditNoteBinding
    private lateinit var repository: NoteRepository
    private lateinit var categoryPreferences: CategoryPreferences

    private var noteId: Long = NO_NOTE_ID
    private var existingNote: Note? = null
    private val selectedCategories = linkedSetOf(NoteCategories.DEFAULT)
    private var isUpdatingCategoryUi: Boolean = false
    private val categoryButtons = mutableMapOf<String, MaterialButton>()
    private val customCategories = linkedSetOf<String>()
    private val hiddenCategories = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEditNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
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
            finish()
        }

        binding.saveButton.setOnClickListener {
            saveNote()
        }
    }

    private fun setupCategoryPicker() {
        renderCategoryButtons()

        binding.customCategoryEditText.setOnEditorActionListener { _, actionId, event ->
            val isKeyboardDone = actionId == EditorInfo.IME_ACTION_DONE
            val isEnterUp = event?.let {
                it.keyCode == KeyEvent.KEYCODE_ENTER && it.action == KeyEvent.ACTION_UP
            } == true
            if (!isKeyboardDone && !isEnterUp) {
                return@setOnEditorActionListener false
            }

            addCustomCategoryFromInput()
            true
        }
        binding.customCategoryEditText.setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_ENTER || event.action != KeyEvent.ACTION_UP) {
                return@setOnKeyListener false
            }

            addCustomCategoryFromInput()
            true
        }

        loadCustomCategoryOptions()
        observeHiddenCategories()
        updateCategoryUi()
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

    private fun loadCustomCategoryOptions() {
        lifecycleScope.launch {
            val enteredCategories = customCategories.toList()
            customCategories.clear()
            customCategories.addAll(enteredCategories)
            customCategories.addAll(
                repository.getAllNotes()
                    .flatMap { NoteCategories.parse(it.category) }
                    .filterNot { NoteCategories.isStandard(it) },
            )
            renderCategoryButtons()
        }
    }

    private fun renderCategoryButtons() {
        categoryButtons.clear()
        binding.categoryButtonsContainer.removeAllViews()

        val categories = (NoteCategories.STANDARD + customCategories + selectedCategories)
            .map { NoteCategories.normalize(it) }
            .filterNot { hiddenCategories.contains(it) && !selectedCategories.contains(it) }
            .distinct()

        categories.forEach { category ->
            addCategoryButton(category)
        }
        updateCategoryButtons()
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

    private fun addCustomCategoryFromInput() {
        val rawCategory = binding.customCategoryEditText.text?.toString()?.trim().orEmpty()
        if (rawCategory.isBlank()) return

        val category = NoteCategories.normalize(rawCategory)
        if (!NoteCategories.isStandard(category)) {
            customCategories.add(category)
        }
        restoreHiddenCategory(category)
        selectCategory(category)
        renderCategoryButtons()
        updateCategoryUi()
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
            binding.screenTitleTextView.text = getString(R.string.edit_note_title)
            binding.titleEditText.setText(note.title)
            binding.contentEditText.setText(note.content)
            selectedCategories.clear()
            selectedCategories.addAll(NoteCategories.parse(note.category))
            updateCategoryUi()
        }
    }

    private fun saveNote() {
        val title = binding.titleEditText.text?.toString()?.trim().orEmpty()
        val content = binding.contentEditText.text?.toString()?.trim().orEmpty()
        val categoryInput = binding.customCategoryEditText.text?.toString()?.trim().orEmpty()
        val categoriesToSave = selectedCategories.toMutableList()
        if (categoryInput.isNotBlank()) {
            restoreHiddenCategory(categoryInput)
            categoriesToSave.add(categoryInput)
        }
        val category = NoteCategories.serialize(categoriesToSave)

        if (title.isBlank() && content.isBlank()) {
            return
        }

        val now = System.currentTimeMillis()
        val noteToSave = existingNote?.copy(
            title = title,
            content = content,
            category = category,
            updatedAt = now,
        ) ?: Note(
            title = title,
            content = content,
            category = category,
            createdAt = now,
            updatedAt = now,
        )

        lifecycleScope.launch {
            repository.saveNote(noteToSave)
            finish()
        }
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
        clearEditorFocus()
        super.onPause()
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasEditorFocus()) {
                    clearEditorFocus()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun hasEditorFocus(): Boolean {
        return binding.titleEditText.hasFocus() ||
            binding.customCategoryEditText.hasFocus() ||
            binding.contentEditText.hasFocus()
    }

    private fun clearEditorFocus() {
        val focusedView = currentFocus ?: binding.contentEditText
        binding.titleEditText.clearFocus()
        binding.customCategoryEditText.clearFocus()
        binding.contentEditText.clearFocus()
        binding.main.requestFocus()
        val inputMethodManager = getSystemService<InputMethodManager>()
        inputMethodManager?.hideSoftInputFromWindow(focusedView.windowToken, 0)
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

        isUpdatingCategoryUi = true
        if (binding.customCategoryEditText.text?.isNotEmpty() == true) {
            binding.customCategoryEditText.text?.clear()
        }
        isUpdatingCategoryUi = false
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

    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
        private const val NO_NOTE_ID = -1L
    }
}
