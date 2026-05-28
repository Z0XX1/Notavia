package com.example.notavia

import android.content.res.ColorStateList
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
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
import com.example.notavia.data.NotePriority
import com.example.notavia.data.NoteRepository
import com.example.notavia.data.NoteType
import com.example.notavia.data.NotaviaDatabase
import com.example.notavia.databinding.ActivityMainBinding
import com.example.notavia.settings.CategoryPreferences
import com.example.notavia.ui.NoteAdapter
import com.example.notavia.ui.NotePriorityUi
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationBarView
import kotlinx.coroutines.launch

class MainActivity : NotaviaActivity() {
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
    private val selectedCategoryFilters = linkedSetOf<String>()
    private val selectedPriorityFilters = linkedSetOf<NotePriority>()
    private val selectedDeadlineFilters = linkedSetOf<DeadlineFilter>()
    private val selectedSortOptions = linkedMapOf<SortGroup, NoteSortOption>()
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
        setupBottomBarLayers()

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

    private fun setupBottomBarLayers() {
        binding.bottomNavigationBackgroundView.translationZ = 0f
        binding.bottomNavigationView.translationZ = dp(1).toFloat()
        binding.addNoteFab.stateListAnimator = null
        binding.addNoteFab.elevation = dp(8).toFloat()
        binding.addNoteFab.translationZ = dp(8).toFloat()
        binding.addNoteFab.bringToFront()
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
        installAlphaPressFeedback(binding.addNoteFab)
        installAlphaPressFeedback(binding.settingsButton)
        installAlphaPressFeedback(binding.closeSelectionButton)
        installAlphaPressFeedback(binding.selectAllButton)
        installAlphaPressFeedback(binding.pinSelectedButton)
        installAlphaPressFeedback(binding.deleteSelectedButton)
        installAlphaPressFeedback(binding.filterButton)
        installAlphaPressFeedback(binding.sortButton)

        binding.addNoteFab.setOnClickListener {
            clearSearchFocus()
            openEditor()
        }

        binding.settingsButton.setOnClickListener {
            clearSearchFocus()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.filterButton.setOnClickListener {
            clearSearchFocus()
            showFilterSheet()
        }

        binding.sortButton.setOnClickListener {
            clearSearchFocus()
            showSortSheet()
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
                    clearChecklistOnlyControls()
                }
                applySearchFilter()
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
                SelectionMode.NOTES -> showDeleteNotesConfirmation()
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
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).apply {
                setBackgroundColor(Color.TRANSPARENT)
                installAlphaPressFeedback(this)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                setBackgroundColor(Color.TRANSPARENT)
                installAlphaPressFeedback(this)
                setOnClickListener {
                    submitCategory()
                }
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
            val matchesSection = NoteType.fromStorage(note.type) == currentSection.noteType
            val matchesSearch = searchQuery.isBlank() ||
                note.title.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategoryFilters.isEmpty() ||
                selectedCategoryFilters.any { category ->
                    NoteCategories.contains(note.category, category)
                }
            val matchesPriority = selectedPriorityFilters.isEmpty() ||
                NotePriority.fromStorage(note.priority) in selectedPriorityFilters
            val matchesDeadline = selectedDeadlineFilters.isEmpty() ||
                selectedDeadlineFilters.any { deadlineFilter ->
                    when (deadlineFilter) {
                        DeadlineFilter.WITH_DEADLINE -> note.deadlineAt != null
                        DeadlineFilter.WITHOUT_DEADLINE -> note.deadlineAt == null
                    }
                }

            matchesSection && matchesSearch && matchesCategory && matchesPriority && matchesDeadline
        }.sortForCurrentMode()

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
        binding.searchActionsRow.isVisible = showSearch
        binding.searchCardView.isVisible = showSearch
        binding.categoryFilterScrollView.isVisible = showCategoryFilter
        binding.checklistsPlaceholderGroup.isVisible = false
        binding.bottomNavigationView.isVisible = !isSelectionMode
        binding.bottomNavigationBackgroundView.isVisible = !isSelectionMode
        binding.selectionActionBar.isVisible = isSelectionMode

        binding.notesRecyclerView.isVisible = hasVisibleNotes
        binding.emptyStateGroup.isVisible = !hasVisibleNotes
        binding.addNoteFab.isVisible = !isSelectionMode
        binding.addNoteFab.bringToFront()

        if (isSelectionMode) {
            updateSelectionTitle()
        }

        updateEmptyState()
        updateFilterSortButtons()
        updateSelectionControls()
        renderCategoryFilters()
        noteAdapter.updateSelectionState(isNoteSelectionMode, selectedNoteIds)
    }

    private fun updateEmptyState() {
        val hasSearch = searchQuery.isNotBlank() || hasActiveFilters()
        binding.emptyTitleTextView.text = getString(
            when {
                hasSearch -> R.string.empty_search_title
                currentSection == MainSection.CHECKLISTS -> R.string.empty_checklists_title
                else -> R.string.empty_state_title
            },
        )
        binding.emptyMessageTextView.text = getString(
            when {
                hasSearch -> R.string.empty_search_message
                currentSection == MainSection.CHECKLISTS -> R.string.empty_checklists_message
                else -> R.string.empty_state_message
            },
        )
    }

    private fun updateFilterSortButtons() {
        if (currentSection != MainSection.NOTES) return

        val filtersActive = hasActiveFilters()
        binding.filterButton.setImageResource(
            if (filtersActive) R.drawable.filter else R.drawable.filteroff,
        )
        binding.filterButton.imageTintList = ColorStateList.valueOf(
            if (filtersActive) {
                ContextCompat.getColor(this, R.color.selection_stroke_color)
            } else {
                resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
            },
        )
        binding.sortButton.imageTintList = ColorStateList.valueOf(
            if (selectedSortOptions.isNotEmpty()) {
                ContextCompat.getColor(this, R.color.selection_stroke_color)
            } else {
                resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
            },
        )
    }

    private fun hasActiveFilters(): Boolean {
        return selectedCategoryFilters.isNotEmpty() ||
            selectedPriorityFilters.isNotEmpty() ||
            selectedDeadlineFilters.isNotEmpty()
    }

    private fun List<Note>.sortForCurrentMode(): List<Note> {
        return sortedWith { first, second ->
            comparePinned(first, second)
                .takeIf { it != 0 }
                ?: compareByCurrentSort(first, second)
        }
    }

    private fun comparePinned(first: Note, second: Note): Int {
        return when {
            first.isPinned == second.isPinned -> 0
            first.isPinned -> -1
            else -> 1
        }
    }

    private fun compareByCurrentSort(first: Note, second: Note): Int {
        val sortOptions = selectedSortOptions.values.toList()
            .ifEmpty { listOf(NoteSortOption.CREATED_NEWEST) }

        sortOptions.forEach { sortOption ->
            val optionCompare = compareBySortOption(first, second, sortOption)
            if (optionCompare != 0) {
                return optionCompare
            }
        }
        return second.updatedAt.compareTo(first.updatedAt)
    }

    private fun compareBySortOption(first: Note, second: Note, sortOption: NoteSortOption): Int {
        return when (sortOption) {
            NoteSortOption.CREATED_NEWEST -> second.createdAt.compareTo(first.createdAt)
            NoteSortOption.CREATED_OLDEST -> first.createdAt.compareTo(second.createdAt)
            NoteSortOption.PRIORITY_HIGH_FIRST -> compareValues(
                prioritySortRank(first, lowPriorityFirst = false),
                prioritySortRank(second, lowPriorityFirst = false),
            )
            NoteSortOption.PRIORITY_LOW_FIRST -> compareValues(
                prioritySortRank(first, lowPriorityFirst = true),
                prioritySortRank(second, lowPriorityFirst = true),
            )
            NoteSortOption.DEADLINE_NEAREST -> compareDeadlines(first, second, nearestFirst = true)
            NoteSortOption.DEADLINE_FARTHEST -> compareDeadlines(first, second, nearestFirst = false)
        }
    }

    private fun prioritySortRank(note: Note, lowPriorityFirst: Boolean): Int {
        return when (NotePriority.fromStorage(note.priority)) {
            NotePriority.HIGH -> if (lowPriorityFirst) 2 else 0
            NotePriority.MEDIUM -> 1
            NotePriority.LOW -> if (lowPriorityFirst) 0 else 2
            NotePriority.NONE -> 3
        }
    }

    private fun compareDeadlines(first: Note, second: Note, nearestFirst: Boolean): Int {
        val firstDeadline = first.deadlineAt
        val secondDeadline = second.deadlineAt
        return when {
            firstDeadline == null && secondDeadline == null -> 0
            firstDeadline == null -> 1
            secondDeadline == null -> -1
            nearestFirst -> firstDeadline.compareTo(secondDeadline)
            else -> secondDeadline.compareTo(firstDeadline)
        }
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
        val button = AppCompatImageButton(this).apply {
            setImageResource(R.drawable.addplusfilter)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = getString(R.string.custom_category_hint)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.note_stroke_color))
            installAlphaPressFeedback(this)
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
            rippleColor = ColorStateList.valueOf(Color.TRANSPARENT)
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener {
                if (isCategorySelectionMode) {
                    if (!isProtectedCategory(category)) {
                        category?.let { toggleCategorySelection(it) }
                    }
                } else {
                    selectedCategoryFilters.clear()
                    category?.let { selectedCategoryFilters.add(NoteCategories.normalize(it)) }
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
                if (category == null) {
                    selectedCategoryFilters.isEmpty()
                } else {
                    selectedCategoryFilters.contains(category)
                }
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

    private fun showFilterSheet() {
        val dialog = BottomSheetDialog(this)
        val content = createBottomSheetContainer()
        val filterRowRefreshers = mutableListOf<() -> Unit>()
        val onFilterChanged = {
            applySearchFilter()
            filterRowRefreshers.forEach { refreshRow ->
                refreshRow()
            }
        }

        content.addView(createBottomSheetTitle(getString(R.string.filters_title)))
        content.addView(createBottomSheetSectionTitle(getString(R.string.filter_priority_title)))
        content.addView(
            createSheetOptionRow(
                title = getString(R.string.filter_any),
                isSelected = { selectedPriorityFilters.isEmpty() },
                registerSelectionUpdater = filterRowRefreshers::add,
            ) {
                selectedPriorityFilters.clear()
                onFilterChanged()
            },
        )
        listOf(
            NotePriority.NONE,
            NotePriority.HIGH,
            NotePriority.MEDIUM,
            NotePriority.LOW,
        ).forEach { priority ->
            content.addView(
                createPriorityFilterRow(priority, filterRowRefreshers::add) {
                    togglePriorityFilter(priority)
                    onFilterChanged()
                },
            )
        }

        content.addView(createBottomSheetSectionTitle(getString(R.string.filter_deadline_title)))
        content.addView(
            createSheetOptionRow(
                title = getString(R.string.filter_any),
                isSelected = { selectedDeadlineFilters.isEmpty() },
                registerSelectionUpdater = filterRowRefreshers::add,
            ) {
                selectedDeadlineFilters.clear()
                onFilterChanged()
            },
        )
        DeadlineFilter.entries.forEach { deadlineFilter ->
            content.addView(
                createSheetOptionRow(
                    title = deadlineFilter.title(),
                    isSelected = { selectedDeadlineFilters.contains(deadlineFilter) },
                    registerSelectionUpdater = filterRowRefreshers::add,
                ) {
                    toggleDeadlineFilter(deadlineFilter)
                    onFilterChanged()
                },
            )
        }

        content.addView(createBottomSheetSectionTitle(getString(R.string.filter_category_title)))
        content.addView(
            createSheetOptionRow(
                title = getString(R.string.filter_any),
                isSelected = { selectedCategoryFilters.isEmpty() },
                registerSelectionUpdater = filterRowRefreshers::add,
            ) {
                selectedCategoryFilters.clear()
                onFilterChanged()
            },
        )
        availableCategoryFilters().forEach { category ->
            content.addView(
                createSheetOptionRow(
                    title = category,
                    isSelected = { selectedCategoryFilters.contains(category) },
                    registerSelectionUpdater = filterRowRefreshers::add,
                ) {
                    toggleCategoryFilter(category)
                    onFilterChanged()
                },
            )
        }

        content.addView(
            createResetFiltersButton {
                selectedPriorityFilters.clear()
                selectedDeadlineFilters.clear()
                selectedCategoryFilters.clear()
                dialog.dismiss()
                applySearchFilter()
            },
        )

        val filterSheetHeight = resources.displayMetrics.heightPixels / 2
        dialog.setContentView(wrapBottomSheetContent(content, fixedHeight = filterSheetHeight))
        styleBottomSheet(dialog, fixedHeight = filterSheetHeight)
        dialog.show()
    }

    private fun showSortSheet() {
        val dialog = BottomSheetDialog(this)
        val content = createBottomSheetContainer()
        val sortRowRefreshers = mutableListOf<() -> Unit>()
        val onSortChanged = {
            applySearchFilter()
            sortRowRefreshers.forEach { refreshRow ->
                refreshRow()
            }
        }

        content.addView(createBottomSheetTitle(getString(R.string.sort_title)))
        content.addView(createBottomSheetSectionTitle(getString(R.string.sort_by_created)))
        content.addView(createSortChoiceRow(SortGroup.CREATED, sortRowRefreshers::add, onSortChanged))

        content.addView(createBottomSheetSectionTitle(getString(R.string.sort_by_priority)))
        content.addView(createSortChoiceRow(SortGroup.PRIORITY, sortRowRefreshers::add, onSortChanged))

        content.addView(createBottomSheetSectionTitle(getString(R.string.sort_by_deadline)))
        content.addView(createSortChoiceRow(SortGroup.DEADLINE, sortRowRefreshers::add, onSortChanged))

        content.addView(
            createResetFiltersButton {
                selectedSortOptions.clear()
                dialog.dismiss()
                applySearchFilter()
            },
        )

        val sortSheetHeight = resources.displayMetrics.heightPixels / 2
        dialog.setContentView(wrapBottomSheetContent(content, fixedHeight = sortSheetHeight))
        styleBottomSheet(dialog, fixedHeight = sortSheetHeight)
        dialog.show()
    }

    private fun createSortChoiceRow(
        group: SortGroup,
        registerSelectionUpdater: ((() -> Unit) -> Unit)? = null,
        onSelectionChanged: () -> Unit,
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            minimumHeight = dp(58)
            setPadding(0, 0, dp(8), 0)
            installAlphaPressFeedback(this)

            addView(
                TextView(this@MainActivity).apply {
                    text = getString(R.string.sort_first_label)
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurface))
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )

            val valueTextView = TextView(this@MainActivity).apply {
                text = selectedSortOptions[group]?.title() ?: getString(R.string.sort_none)
                textSize = 16f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            }
            addView(
                valueTextView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )

            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.unfold)
                    imageTintList = ColorStateList.valueOf(
                        resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant),
                    )
                    contentDescription = null
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                },
                LinearLayout.LayoutParams(dp(36), dp(36)),
            )

            val refreshValue = {
                valueTextView.text = selectedSortOptions[group]?.title() ?: getString(R.string.sort_none)
            }
            registerSelectionUpdater?.invoke(refreshValue)

            setOnClickListener {
                showSortChoiceMenu(this, group) {
                    onSelectionChanged()
                }
            }
        }
    }

    private fun showSortChoiceMenu(
        anchor: View,
        group: SortGroup,
        onSelected: () -> Unit,
    ) {
        val popupWidth = dp(252)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(resolveThemeColor(com.google.android.material.R.attr.colorSurfaceContainerLow))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), resolveThemeColor(com.google.android.material.R.attr.colorOutline))
            }
            clipToOutline = true
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        var popupWindow: PopupWindow? = null
        sortChoicesForGroup(group).forEach { choice ->
            container.addView(
                createSortPopupRow(
                    title = choice.title,
                    isSelected = selectedSortOptions[group] == choice.option,
                ) {
                    popupWindow?.dismiss()
                    if (choice.option == null) {
                        selectedSortOptions.remove(group)
                    } else {
                        selectedSortOptions[group] = choice.option
                    }
                    onSelected()
                },
            )
        }

        popupWindow = PopupWindow(
            container,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
        }

        val xOffset = (anchor.width - popupWidth).coerceAtLeast(0)
        container.measure(
            View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val anchorLocation = IntArray(2)
        anchor.getLocationOnScreen(anchorLocation)
        val availableBelow = resources.displayMetrics.heightPixels -
            anchorLocation[1] -
            anchor.height
        val yOffset = if (availableBelow < container.measuredHeight + dp(8)) {
            -anchor.height - container.measuredHeight - dp(4)
        } else {
            -dp(4)
        }
        popupWindow.showAsDropDown(anchor, xOffset, yOffset)
    }

    private fun createSortPopupRow(
        title: String,
        isSelected: Boolean,
        onClick: () -> Unit,
    ): View {
        val selectedBackgroundColor = resolveThemeColor(
            com.google.android.material.R.attr.colorSurfaceVariant,
        )
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = if (isSelected) {
                GradientDrawable().apply {
                    setColor(selectedBackgroundColor)
                    cornerRadius = dp(12).toFloat()
                }
            } else {
                ColorDrawable(Color.TRANSPARENT)
            }
            setPadding(dp(22), 0, dp(20), 0)
            installAlphaPressFeedback(this)
            setOnClickListener {
                onClick()
            }

            addView(
                TextView(this@MainActivity).apply {
                    text = title
                    textSize = 16f
                    setTextColor(
                        if (isSelected) {
                            ContextCompat.getColor(this@MainActivity, R.color.selection_stroke_color)
                        } else {
                            resolveThemeColor(com.google.android.material.R.attr.colorOnSurface)
                        },
                    )
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.check)
                    imageTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(this@MainActivity, R.color.selection_stroke_color),
                    )
                    visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
                    contentDescription = null
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.MATCH_PARENT),
            )
        }.also { row ->
            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56),
            )
        }
    }

    private fun sortChoicesForGroup(group: SortGroup): List<SortChoice> {
        return when (group) {
            SortGroup.CREATED -> listOf(
                SortChoice(null, getString(R.string.sort_none)),
                SortChoice(NoteSortOption.CREATED_NEWEST, getString(R.string.sort_created_new)),
                SortChoice(NoteSortOption.CREATED_OLDEST, getString(R.string.sort_created_old)),
            )
            SortGroup.PRIORITY -> listOf(
                SortChoice(null, getString(R.string.sort_none)),
                SortChoice(NoteSortOption.PRIORITY_HIGH_FIRST, getString(R.string.sort_priority_high)),
                SortChoice(NoteSortOption.PRIORITY_LOW_FIRST, getString(R.string.sort_priority_low)),
            )
            SortGroup.DEADLINE -> listOf(
                SortChoice(null, getString(R.string.sort_none)),
                SortChoice(NoteSortOption.DEADLINE_NEAREST, getString(R.string.sort_deadline_near)),
                SortChoice(NoteSortOption.DEADLINE_FARTHEST, getString(R.string.sort_deadline_far)),
            )
        }
    }

    private fun togglePriorityFilter(priority: NotePriority) {
        if (!selectedPriorityFilters.add(priority)) {
            selectedPriorityFilters.remove(priority)
        }
    }

    private fun toggleDeadlineFilter(deadlineFilter: DeadlineFilter) {
        if (!selectedDeadlineFilters.add(deadlineFilter)) {
            selectedDeadlineFilters.remove(deadlineFilter)
        }
    }

    private fun toggleCategoryFilter(category: String) {
        val normalizedCategory = NoteCategories.normalize(category)
        if (!selectedCategoryFilters.add(normalizedCategory)) {
            selectedCategoryFilters.remove(normalizedCategory)
        }
    }

    private fun createBottomSheetContainer(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(22), dp(24), dp(18))
        }
    }

    private fun wrapBottomSheetContent(content: View, fixedHeight: Int? = null): View {
        val root = FrameLayout(this).apply {
            background = bottomSheetBackground()
            clipChildren = false
            clipToPadding = false
            fixedHeight?.let { height ->
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    height,
                )
            }
        }
        val scrollView = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        root.addView(
            scrollView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                fixedHeight ?: FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        return root
    }

    private fun bottomSheetBackground(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(resolveThemeColor(com.google.android.material.R.attr.colorSurfaceContainerLow))
            cornerRadii = floatArrayOf(
                dp(24).toFloat(),
                dp(24).toFloat(),
                dp(24).toFloat(),
                dp(24).toFloat(),
                0f,
                0f,
                0f,
                0f,
            )
        }
    }

    private fun styleBottomSheet(dialog: BottomSheetDialog, fixedHeight: Int? = null) {
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet,
            )
            bottomSheet?.apply {
                setBackgroundColor(Color.TRANSPARENT)
                clipChildren = false
                clipToPadding = false
                fixedHeight?.let { height ->
                    layoutParams = layoutParams.apply {
                        this.height = height
                    }
                    val behavior = BottomSheetBehavior.from(this)
                    behavior.peekHeight = height
                    behavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    behavior.isDraggable = false
                }
            }
        }
    }

    private fun createBottomSheetTitle(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurface))
        }
    }

    private fun createBottomSheetSectionTitle(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(18)
                bottomMargin = dp(6)
            }
        }
    }

    private fun createPriorityFilterRow(
        priority: NotePriority,
        registerSelectionUpdater: ((() -> Unit) -> Unit)? = null,
        onClick: () -> Unit,
    ): View {
        val priorityDot = ImageView(this).apply {
            setImageResource(R.drawable.circle)
            NotePriorityUi.applyTo(this, priority)
            contentDescription = null
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        return createSheetOptionRow(
            title = getString(NotePriorityUi.labelRes(priority)),
            isSelected = { selectedPriorityFilters.contains(priority) },
            leadingView = priorityDot,
            registerSelectionUpdater = registerSelectionUpdater,
            onClick = onClick,
        )
    }

    private fun createSheetOptionRow(
        title: String,
        isSelected: () -> Boolean,
        leadingView: View? = null,
        registerSelectionUpdater: ((() -> Unit) -> Unit)? = null,
        onClick: () -> Unit,
    ): View {
        val selectedBackgroundColor = resolveThemeColor(
            com.google.android.material.R.attr.colorSurfaceVariant,
        )
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            minimumHeight = dp(48)
            setPadding(dp(14), dp(8), dp(12), dp(8))
            installAlphaPressFeedback(this)

            leadingView?.let { view ->
                addView(
                    view,
                    LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                        marginEnd = dp(12)
                    },
                )
            }
            addView(
                TextView(this@MainActivity).apply {
                    text = title
                    textSize = 16f
                    setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurface))
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.check)
                    imageTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(this@MainActivity, R.color.selection_stroke_color),
                    )
                    visibility = View.INVISIBLE
                    contentDescription = null
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(dp(28), dp(28)),
            )
            fun updateSelectionStyle() {
                val selected = isSelected()
                background = if (selected) {
                    GradientDrawable().apply {
                        setColor(selectedBackgroundColor)
                        cornerRadius = dp(14).toFloat()
                    }
                } else {
                    null
                }
                (getChildAt(childCount - 2) as? TextView)?.setTextColor(
                    if (selected) {
                        ContextCompat.getColor(this@MainActivity, R.color.selection_stroke_color)
                    } else {
                        resolveThemeColor(com.google.android.material.R.attr.colorOnSurface)
                    },
                )
                getChildAt(childCount - 1).visibility = if (selected) View.VISIBLE else View.INVISIBLE
            }
            updateSelectionStyle()
            registerSelectionUpdater?.invoke {
                updateSelectionStyle()
            }
            setOnClickListener {
                onClick()
                updateSelectionStyle()
            }
        }
    }

    private fun createResetFiltersButton(onClick: () -> Unit): MaterialButton {
        return MaterialButton(this).apply {
            text = getString(R.string.filter_reset_action)
            setAllCaps(false)
            minWidth = 0
            minHeight = 0
            insetTop = 0
            insetBottom = 0
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            rippleColor = ColorStateList.valueOf(Color.TRANSPARENT)
            setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            installAlphaPressFeedback(this)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.END
                topMargin = dp(16)
            }
        }
    }

    private fun resetMissingCategoryFilter() {
        if (selectedCategoryFilters.isEmpty()) return
        val existingFilters = selectedCategoryFilters.filterTo(linkedSetOf()) { category ->
            val normalizedCategory = NoteCategories.normalize(category)
            val isStandardCategory = NoteCategories.isStandard(normalizedCategory)
            val isCustomCategory = customCategoryNames.contains(normalizedCategory)
            val hasNotesInCategory = allNotes.any { note ->
                NoteCategories.contains(note.category, normalizedCategory)
            }

            isStandardCategory || isCustomCategory || hasNotesInCategory
        }
        if (existingFilters.size != selectedCategoryFilters.size) {
            selectedCategoryFilters.clear()
            selectedCategoryFilters.addAll(existingFilters)
        }
    }

    private fun availableCategoryFilters(): List<String> {
        val sectionNotes = allNotes.filter { note ->
            NoteType.fromStorage(note.type) == currentSection.noteType
        }
        return NoteCategories.availableFrom(sectionNotes, pinnedCategoryNames, customCategoryNames)
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
                val changedFilters = selectedCategoryFilters.removeAll(hiddenCategoryNames)
                if (changedFilters) {
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

    private fun showDeleteNotesConfirmation() {
        val count = selectedNoteIds.size
        if (count == 0) return

        val dialog = BottomSheetDialog(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(16))
            background = GradientDrawable().apply {
                setColor(resolveThemeColor(com.google.android.material.R.attr.colorSurfaceContainerLow))
                cornerRadii = floatArrayOf(
                    dp(24).toFloat(),
                    dp(24).toFloat(),
                    dp(24).toFloat(),
                    dp(24).toFloat(),
                    0f,
                    0f,
                    0f,
                    0f,
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
                topMargin = dp(8)
            },
        )

        val actionsRow = LinearLayout(this).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        actionsRow.addView(
            createDeleteDialogButton(
                text = getString(R.string.cancel_action),
                textColor = resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant),
            ) {
                dialog.dismiss()
            },
        )
        actionsRow.addView(
            createDeleteDialogButton(
                text = getString(R.string.delete_confirm_action),
                textColor = ContextCompat.getColor(this, R.color.priority_high),
            ) {
                dialog.dismiss()
                deleteSelectedNotes()
            },
        )
        container.addView(
            actionsRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(18)
            },
        )

        dialog.setContentView(container)
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)
        }
        dialog.show()
    }

    private fun createDeleteDialogButton(
        text: String,
        textColor: Int,
        onClick: () -> Unit,
    ): MaterialButton {
        return MaterialButton(this).apply {
            this.text = text
            setAllCaps(false)
            minWidth = 0
            minHeight = 0
            insetTop = 0
            insetBottom = 0
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            rippleColor = ColorStateList.valueOf(Color.TRANSPARENT)
            setTextColor(textColor)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            installAlphaPressFeedback(this)
            setOnClickListener { onClick() }
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
            selectedCategoryFilters.removeAll(categoriesToDelete)
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
            putExtra(EditNoteActivity.EXTRA_NOTE_TYPE, currentSection.noteType.storageValue)
        }
        startActivity(intent)
    }

    private fun clearChecklistOnlyControls() {
        searchQuery = ""
        binding.searchEditText.text?.clear()
        selectedCategoryFilters.clear()
        selectedPriorityFilters.clear()
        selectedDeadlineFilters.clear()
        selectedSortOptions.clear()
        clearSearchFocus()
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

    private fun resolveThemeColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(this, typedValue.resourceId)
        } else {
            typedValue.data
        }
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

    private fun DeadlineFilter.title(): String {
        return when (this) {
            DeadlineFilter.WITH_DEADLINE -> getString(R.string.filter_deadline_with)
            DeadlineFilter.WITHOUT_DEADLINE -> getString(R.string.filter_deadline_without)
        }
    }

    private fun NoteSortOption.title(): String {
        return when (this) {
            NoteSortOption.CREATED_NEWEST -> getString(R.string.sort_created_new)
            NoteSortOption.CREATED_OLDEST -> getString(R.string.sort_created_old)
            NoteSortOption.PRIORITY_HIGH_FIRST -> getString(R.string.sort_priority_high)
            NoteSortOption.PRIORITY_LOW_FIRST -> getString(R.string.sort_priority_low)
            NoteSortOption.DEADLINE_NEAREST -> getString(R.string.sort_deadline_near)
            NoteSortOption.DEADLINE_FARTHEST -> getString(R.string.sort_deadline_far)
        }
    }

    private enum class MainSection(val noteType: NoteType) {
        NOTES(NoteType.NOTE),
        CHECKLISTS(NoteType.CHECKLIST),
    }

    private enum class SelectionMode {
        NONE,
        NOTES,
        CATEGORIES,
    }

    private enum class DeadlineFilter {
        WITH_DEADLINE,
        WITHOUT_DEADLINE,
    }

    private enum class SortGroup {
        CREATED,
        PRIORITY,
        DEADLINE,
    }

    private enum class NoteSortOption(val group: SortGroup) {
        CREATED_NEWEST(SortGroup.CREATED),
        CREATED_OLDEST(SortGroup.CREATED),
        PRIORITY_HIGH_FIRST(SortGroup.PRIORITY),
        PRIORITY_LOW_FIRST(SortGroup.PRIORITY),
        DEADLINE_NEAREST(SortGroup.DEADLINE),
        DEADLINE_FARTHEST(SortGroup.DEADLINE),
    }

    private data class SortChoice(
        val option: NoteSortOption?,
        val title: String,
    )

    companion object {
        private const val BUTTON_PRESSED_ALPHA = 0.68f
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
