package com.example.notavia

import android.content.res.ColorStateList
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
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
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.notavia.data.Note
import com.example.notavia.data.NoteCategories
import com.example.notavia.data.NoteRepository
import com.example.notavia.data.NotaviaDatabase
import com.example.notavia.databinding.ActivityMainBinding
import com.example.notavia.settings.CategoryPreferences
import com.example.notavia.ui.NoteAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationBarView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var noteAdapter: NoteAdapter
    private lateinit var repository: NoteRepository
    private lateinit var categoryPreferences: CategoryPreferences

    private var currentSection: MainSection = MainSection.NOTES
    private var allNotes: List<Note> = emptyList()
    private var visibleNotes: List<Note> = emptyList()
    private val selectedNoteIds = linkedSetOf<Long>()
    private val selectedCategoryNames = linkedSetOf<String>()
    private val pinnedCategoryNames = linkedSetOf<String>()
    private val hiddenCategoryNames = linkedSetOf<String>()
    private val customCategoryNames = linkedSetOf<String>()
    private var selectionMode: SelectionMode = SelectionMode.NONE
    private var searchQuery: String = ""
    private var selectedCategoryFilter: String? = null
    private val categoryFilterButtons = linkedMapOf<String?, MaterialButton>()
    private val isSelectionMode: Boolean
        get() = selectionMode != SelectionMode.NONE
    private val isNoteSelectionMode: Boolean
        get() = selectionMode == SelectionMode.NOTES
    private val isCategorySelectionMode: Boolean
        get() = selectionMode == SelectionMode.CATEGORIES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val defaultTopBarPadding = binding.defaultTopBar.capturePadding()
        val selectionTopBarPadding = binding.selectionTopBar.capturePadding()
        val bottomNavigationPadding = binding.bottomNavigationView.capturePadding()
        val selectionActionBarPadding = binding.selectionActionBar.capturePadding()
        val notesRecyclerViewPadding = binding.notesRecyclerView.capturePadding()

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val splitBottomInsetTop = systemBars.bottom / 2
            val splitBottomInsetBottom = systemBars.bottom - splitBottomInsetTop
            binding.defaultTopBar.updatePadding(
                left = defaultTopBarPadding.left + systemBars.left,
                top = defaultTopBarPadding.top + systemBars.top,
                right = defaultTopBarPadding.right + systemBars.right,
            )
            binding.selectionTopBar.updatePadding(
                left = selectionTopBarPadding.left + systemBars.left,
                top = selectionTopBarPadding.top + systemBars.top,
                right = selectionTopBarPadding.right + systemBars.right,
            )
            binding.bottomNavigationView.updatePadding(
                left = bottomNavigationPadding.left + systemBars.left,
                top = bottomNavigationPadding.top + splitBottomInsetTop,
                right = bottomNavigationPadding.right + systemBars.right,
                bottom = bottomNavigationPadding.bottom + splitBottomInsetBottom,
            )
            binding.selectionActionBar.updatePadding(
                left = selectionActionBarPadding.left + systemBars.left,
                top = selectionActionBarPadding.top + splitBottomInsetTop,
                right = selectionActionBarPadding.right + systemBars.right,
                bottom = selectionActionBarPadding.bottom + splitBottomInsetBottom,
            )
            binding.notesRecyclerView.updatePadding(
                left = notesRecyclerViewPadding.left,
                top = notesRecyclerViewPadding.top,
                right = notesRecyclerViewPadding.right,
                bottom = notesRecyclerViewPadding.bottom + systemBars.bottom,
            )
            if (!isImeVisible && binding.searchEditText.hasFocus()) {
                clearSearchFocus()
            }
            insets
        }

        repository = NoteRepository(NotaviaDatabase.getDatabase(this).noteDao())
        categoryPreferences = CategoryPreferences(this)

        setupRecyclerView()
        setupActions()
        setupBackHandling()
        observePinnedCategories()
        observeHiddenCategories()
        observeCustomCategories()
        renderUi()
    }

    override fun onResume() {
        super.onResume()
        loadNotes()
    }

    override fun onPause() {
        clearSearchFocus()
        super.onPause()
    }

    private fun setupRecyclerView() {
        noteAdapter = NoteAdapter(
            onNoteClicked = { note ->
                if (isNoteSelectionMode) {
                    toggleSelection(note.id)
                } else if (!isCategorySelectionMode) {
                    openViewer(note.id)
                }
            },
            onNoteLongClicked = { note ->
                if (!isSelectionMode) {
                    enterNoteSelectionMode(note.id)
                }
            },
        )

        binding.notesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = noteAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupActions() {
        binding.addNoteFab.setOnClickListener {
            clearSearchFocus()
            openEditor()
        }

        binding.settingsButton.setOnClickListener {
            clearSearchFocus()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.searchEditText.doAfterTextChanged { editable ->
            searchQuery = editable?.toString().orEmpty()
            applySearchFilter()
        }

        binding.bottomNavigationView.selectedItemId = R.id.navigation_notes
        binding.bottomNavigationView.setOnItemSelectedListener(
            NavigationBarView.OnItemSelectedListener { item ->
                currentSection = when (item.itemId) {
                    R.id.navigation_notes -> MainSection.NOTES
                    R.id.navigation_checklists -> MainSection.CHECKLISTS
                    else -> return@OnItemSelectedListener false
                }
                if (currentSection != MainSection.NOTES) {
                    exitSelectionMode()
                }
                renderUi()
                true
            },
        )

        binding.closeSelectionButton.setOnClickListener {
            exitSelectionMode()
        }

        binding.selectAllButton.setOnClickListener {
            when (selectionMode) {
                SelectionMode.NOTES -> selectAllVisibleNotes()
                SelectionMode.CATEGORIES -> selectAllCategories()
                SelectionMode.NONE -> Unit
            }
        }

        binding.pinSelectedButton.setOnClickListener {
            when (selectionMode) {
                SelectionMode.NOTES -> pinOrUnpinSelectedNotes()
                SelectionMode.CATEGORIES -> pinOrUnpinSelectedCategories()
                SelectionMode.NONE -> Unit
            }
        }

        binding.deleteSelectedButton.setOnClickListener {
            when (selectionMode) {
                SelectionMode.NOTES -> deleteSelectedNotes()
                SelectionMode.CATEGORIES -> deleteSelectedCategories()
                SelectionMode.NONE -> Unit
            }
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

    private fun addCustomCategory(rawCategory: String) {
        val category = NoteCategories.normalize(rawCategory)
        if (NoteCategories.isReserved(category)) return

        if (!NoteCategories.isStandard(category)) {
            customCategoryNames.add(category)
        }
        hiddenCategoryNames.remove(category)
        renderUi()

        lifecycleScope.launch {
            categoryPreferences.setCustomCategories(customCategoryNames)
            categoryPreferences.setHiddenCategories(hiddenCategoryNames)
        }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSelectionMode) {
                    exitSelectionMode()
                } else if (binding.searchEditText.hasFocus()) {
                    clearSearchFocus()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun loadNotes() {
        lifecycleScope.launch {
            allNotes = repository.getAllNotes()
            applySearchFilter()
        }
    }

    private fun applySearchFilter() {
        resetMissingCategoryFilter()

        visibleNotes = allNotes.filter { note ->
            val matchesSearch = searchQuery.isBlank() ||
                note.title.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategoryFilter == null ||
                NoteCategories.contains(note.category, selectedCategoryFilter.orEmpty())

            matchesSearch && matchesCategory
        }

        if (selectedNoteIds.isNotEmpty()) {
            val existingIds = allNotes.map { it.id }.toSet()
            selectedNoteIds.retainAll(existingIds)
        }

        noteAdapter.submitList(visibleNotes)
        renderUi()
    }

    private fun renderUi() {
        val isNotesSection = currentSection == MainSection.NOTES
        val hasVisibleNotes = visibleNotes.isNotEmpty()
        val showSearch = isNotesSection && !isSelectionMode
        val showCategoryFilter = isNotesSection && !isNoteSelectionMode

        binding.screenTitleTextView.setText(
            if (isNotesSection) R.string.notes_title else R.string.checklists_title,
        )
        binding.defaultTopBar.isVisible = !isSelectionMode
        binding.selectionTopBar.isVisible = isSelectionMode
        binding.searchCardView.isVisible = showSearch
        binding.categoryFilterScrollView.isVisible = showCategoryFilter
        binding.checklistsPlaceholderGroup.isVisible = !isNotesSection
        binding.bottomNavigationView.isVisible = !isSelectionMode
        binding.selectionActionBar.isVisible = isSelectionMode

        binding.notesRecyclerView.isVisible = isNotesSection && hasVisibleNotes
        binding.emptyStateGroup.isVisible = isNotesSection && !hasVisibleNotes
        binding.addNoteFab.isVisible = isNotesSection && !isSelectionMode

        if (isSelectionMode) {
            updateSelectionTitle()
        }

        updateEmptyState()
        updateSelectionControls()
        renderCategoryFilters()
        noteAdapter.updateSelectionState(isNoteSelectionMode, selectedNoteIds)
    }

    private fun updateEmptyState() {
        if (currentSection != MainSection.NOTES) return

        val hasSearch = searchQuery.isNotBlank() || selectedCategoryFilter != null
        binding.emptyTitleTextView.text = getString(
            if (hasSearch) R.string.empty_search_title else R.string.empty_state_title,
        )
        binding.emptyMessageTextView.text = getString(
            if (hasSearch) R.string.empty_search_message else R.string.empty_state_message,
        )
    }

    private fun renderCategoryFilters() {
        categoryFilterButtons.clear()
        binding.categoryFilterContainer.removeAllViews()

        if (
            currentSection != MainSection.NOTES ||
            isNoteSelectionMode
        ) {
            return
        }

        addCategoryAddButton()
        addCategoryFilterButton(null, getString(R.string.all_categories))
        availableCategoryFilters().forEach { category ->
            addCategoryFilterButton(category, category)
        }
        updateCategoryFilterButtons()
    }

    private fun addCategoryAddButton() {
        val selectableBackground = TypedValue()
        theme.resolveAttribute(
            android.R.attr.selectableItemBackgroundBorderless,
            selectableBackground,
            true,
        )
        val button = AppCompatImageButton(this).apply {
            setImageResource(R.drawable.plus)
            background = ContextCompat.getDrawable(this@MainActivity, selectableBackground.resourceId)
            contentDescription = getString(R.string.custom_category_hint)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.note_stroke_color))
            setOnClickListener {
                clearSearchFocus()
                showAddCategoryDialog()
            }
        }
        val params = LinearLayout.LayoutParams(
            dp(32),
            dp(32),
        ).apply {
            marginEnd = dp(8)
        }
        binding.categoryFilterContainer.addView(button, params)
    }

    private fun addCategoryFilterButton(category: String?, title: String) {
        val button = MaterialButton(this).apply {
            text = title
            setAllCaps(false)
            minWidth = 0
            minHeight = 0
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(13)
            textSize = 12f
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener {
                if (isCategorySelectionMode) {
                    if (!isProtectedCategory(category)) {
                        category?.let { toggleCategorySelection(it) }
                    }
                } else {
                    selectedCategoryFilter = category
                    clearSearchFocus()
                    applySearchFilter()
                }
            }
            setOnLongClickListener {
                if (!isProtectedCategory(category)) {
                    category?.let {
                        enterCategorySelectionMode(it)
                    }
                    true
                } else {
                    false
                }
            }
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            dp(32),
        ).apply {
            marginEnd = dp(8)
        }
        binding.categoryFilterContainer.addView(button, params)
        categoryFilterButtons[category] = button
    }

    private fun updateCategoryFilterButtons() {
        categoryFilterButtons.forEach { (category, button) ->
            val isActive = if (isCategorySelectionMode) {
                category != null &&
                    !isProtectedCategory(category) &&
                    selectedCategoryNames.contains(category)
            } else {
                category == selectedCategoryFilter
            }
            styleCategoryFilterButton(
                button,
                isActive = isActive,
                isPinned = category != null &&
                    !isProtectedCategory(category) &&
                    pinnedCategoryNames.contains(category),
            )
        }
    }

    private fun styleCategoryFilterButton(
        button: MaterialButton,
        isActive: Boolean,
        isPinned: Boolean,
    ) {
        button.icon = if (isPinned) {
            ContextCompat.getDrawable(this, R.drawable.pushpin)
        } else {
            null
        }
        button.iconSize = dp(12)
        button.iconPadding = dp(4)
        button.iconTint = ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                if (isActive) R.color.light_on_primary else R.color.note_stroke_color,
            ),
        )
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

    private fun resetMissingCategoryFilter() {
        val selectedCategory = selectedCategoryFilter ?: return
        val normalizedCategory = NoteCategories.normalize(selectedCategory)
        val isStandardCategory = NoteCategories.isStandard(normalizedCategory)
        val isCustomCategory = customCategoryNames.contains(normalizedCategory)
        val hasNotesInCategory = allNotes.any { note ->
            NoteCategories.contains(note.category, normalizedCategory)
        }
        if (!isStandardCategory && !isCustomCategory && !hasNotesInCategory) {
            selectedCategoryFilter = null
        }
    }

    private fun availableCategoryFilters(): List<String> {
        return NoteCategories.availableFrom(allNotes, pinnedCategoryNames, customCategoryNames)
            .filterNot { it != NoteCategories.DEFAULT && hiddenCategoryNames.contains(it) }
    }

    private fun isProtectedCategory(category: String?): Boolean {
        return category == null || NoteCategories.isReserved(category)
    }

    private fun observePinnedCategories() {
        lifecycleScope.launch {
            categoryPreferences.pinnedCategoriesFlow.collect { categories ->
                pinnedCategoryNames.clear()
                pinnedCategoryNames.addAll(categories)
                renderUi()
            }
        }
    }

    private fun observeHiddenCategories() {
        lifecycleScope.launch {
            categoryPreferences.hiddenCategoriesFlow.collect { categories ->
                hiddenCategoryNames.clear()
                hiddenCategoryNames.addAll(categories)
                if (selectedCategoryFilter != null && hiddenCategoryNames.contains(selectedCategoryFilter)) {
                    selectedCategoryFilter = null
                    applySearchFilter()
                } else {
                    renderUi()
                }
            }
        }
    }

    private fun observeCustomCategories() {
        lifecycleScope.launch {
            categoryPreferences.customCategoriesFlow.collect { categories ->
                customCategoryNames.clear()
                customCategoryNames.addAll(categories)
                renderUi()
            }
        }
    }

    private fun updateSelectionTitle() {
        binding.selectionCountTextView.text = when (selectionMode) {
            SelectionMode.NOTES -> getString(
                R.string.selected_count_format,
                selectedNoteIds.size,
            )
            SelectionMode.CATEGORIES -> getString(
                R.string.selected_categories_count_format,
                selectedCategoryNames.size,
            )
            SelectionMode.NONE -> getString(R.string.selected_count)
        }
    }

    private fun updateSelectionControls() {
        val shouldUnpin = when (selectionMode) {
            SelectionMode.NOTES -> {
                val selectedNotes = allNotes.filter { selectedNoteIds.contains(it.id) }
                selectedNotes.isNotEmpty() && selectedNotes.all { it.isPinned }
            }
            SelectionMode.CATEGORIES -> {
                selectedCategoryNames.isNotEmpty() &&
                    selectedCategoryNames.all { pinnedCategoryNames.contains(it) }
            }
            SelectionMode.NONE -> false
        }
        binding.pinSelectedButton.text = getString(
            if (shouldUnpin) R.string.unpin_action else R.string.pin_action,
        )
    }

    private fun selectAllVisibleNotes() {
        if (visibleNotes.isEmpty()) return
        val visibleIds = visibleNotes.map { it.id }.toSet()
        val allVisibleSelected = visibleIds.isNotEmpty() && selectedNoteIds.containsAll(visibleIds)
        selectedNoteIds.clear()
        if (!allVisibleSelected) {
            selectedNoteIds.addAll(visibleIds)
        }
        renderUi()
    }

    private fun selectAllCategories() {
        val categories = availableCategoryFilters()
            .filterNot { isProtectedCategory(it) }
        if (categories.isEmpty()) return
        val allCategoriesSelected = selectedCategoryNames.containsAll(categories)
        selectedCategoryNames.clear()
        if (!allCategoriesSelected) {
            selectedCategoryNames.addAll(categories)
        }
        renderUi()
    }

    private fun enterNoteSelectionMode(initialNoteId: Long) {
        selectionMode = SelectionMode.NOTES
        selectedNoteIds.clear()
        selectedCategoryNames.clear()
        selectedNoteIds.add(initialNoteId)
        renderUi()
    }

    private fun enterCategorySelectionMode(initialCategory: String) {
        if (isProtectedCategory(initialCategory)) return

        selectionMode = SelectionMode.CATEGORIES
        selectedNoteIds.clear()
        selectedCategoryNames.clear()
        selectedCategoryNames.add(NoteCategories.normalize(initialCategory))
        clearSearchFocus()
        renderUi()
    }

    private fun exitSelectionMode() {
        selectionMode = SelectionMode.NONE
        selectedNoteIds.clear()
        selectedCategoryNames.clear()
        renderUi()
    }

    private fun toggleSelection(noteId: Long) {
        if (selectedNoteIds.contains(noteId)) {
            selectedNoteIds.remove(noteId)
        } else {
            selectedNoteIds.add(noteId)
        }
        renderUi()
    }

    private fun toggleCategorySelection(category: String) {
        val normalizedCategory = NoteCategories.normalize(category)
        if (isProtectedCategory(normalizedCategory)) return

        if (selectedCategoryNames.contains(normalizedCategory)) {
            selectedCategoryNames.remove(normalizedCategory)
        } else {
            selectedCategoryNames.add(normalizedCategory)
        }
        if (selectedCategoryNames.isEmpty()) {
            exitSelectionMode()
        } else {
            renderUi()
        }
    }

    private fun pinOrUnpinSelectedNotes() {
        if (selectedNoteIds.isEmpty()) return

        val selectedNotes = allNotes.filter { selectedNoteIds.contains(it.id) }
        val shouldPin = selectedNotes.any { !it.isPinned }

        lifecycleScope.launch {
            repository.updatePinnedState(selectedNoteIds.toList(), shouldPin)
            exitSelectionMode()
            loadNotes()
        }
    }

    private fun pinOrUnpinSelectedCategories() {
        val categoriesToPin = selectedCategoryNames
            .filterNot { isProtectedCategory(it) }
            .toSet()
        if (categoriesToPin.isEmpty()) return

        val shouldPin = categoriesToPin.any { !pinnedCategoryNames.contains(it) }
        if (shouldPin) {
            pinnedCategoryNames.addAll(categoriesToPin)
        } else {
            pinnedCategoryNames.removeAll(categoriesToPin)
        }

        lifecycleScope.launch {
            categoryPreferences.setPinnedCategories(pinnedCategoryNames)
            exitSelectionMode()
        }
    }

    private fun deleteSelectedNotes() {
        if (selectedNoteIds.isEmpty()) return

        lifecycleScope.launch {
            repository.deleteNotes(selectedNoteIds.toList())
            exitSelectionMode()
            loadNotes()
        }
    }

    private fun deleteSelectedCategories() {
        val categoriesToDelete = selectedCategoryNames
            .filterNot { isProtectedCategory(it) }
            .toSet()
        if (categoriesToDelete.isEmpty()) return

        lifecycleScope.launch {
            allNotes.forEach { note ->
                val updatedCategories = NoteCategories.parse(note.category)
                    .filterNot { categoriesToDelete.contains(it) }
                repository.saveNote(
                    note.copy(
                        category = NoteCategories.serialize(updatedCategories),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }

            pinnedCategoryNames.removeAll(categoriesToDelete)
            customCategoryNames.removeAll(categoriesToDelete)
            hiddenCategoryNames.addAll(categoriesToDelete)
            categoryPreferences.setPinnedCategories(pinnedCategoryNames)
            categoryPreferences.setCustomCategories(customCategoryNames)
            categoryPreferences.setHiddenCategories(hiddenCategoryNames)
            if (selectedCategoryFilter != null && categoriesToDelete.contains(selectedCategoryFilter)) {
                selectedCategoryFilter = null
            }
            exitSelectionMode()
            loadNotes()
        }
    }

    private fun openViewer(noteId: Long) {
        val intent = Intent(this, ViewNoteActivity::class.java).apply {
            putExtra(ViewNoteActivity.EXTRA_NOTE_ID, noteId)
        }
        startActivity(intent)
    }

    private fun openEditor(noteId: Long? = null) {
        val intent = Intent(this, EditNoteActivity::class.java).apply {
            noteId?.let { putExtra(EditNoteActivity.EXTRA_NOTE_ID, it) }
        }
        startActivity(intent)
    }

    private fun clearSearchFocus() {
        binding.searchEditText.clearFocus()
        binding.main.requestFocus()
        val inputMethodManager = getSystemService<InputMethodManager>()
        inputMethodManager?.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private enum class MainSection {
        NOTES,
        CHECKLISTS,
    }

    private enum class SelectionMode {
        NONE,
        NOTES,
        CATEGORIES,
    }
}

private data class ViewPadding(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

private fun View.capturePadding(): ViewPadding {
    return ViewPadding(
        left = paddingLeft,
        top = paddingTop,
        right = paddingRight,
        bottom = paddingBottom,
    )
}
