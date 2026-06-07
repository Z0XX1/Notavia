package com.example.notavia

import android.content.res.ColorStateList
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
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
import android.widget.Toast
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.notavia.data.Note
import com.example.notavia.data.NoteCategories
import com.example.notavia.data.NotePriority
import com.example.notavia.data.NoteRepository
import com.example.notavia.data.NoteType
import com.example.notavia.data.NotaviaDatabase
import com.example.notavia.databinding.ActivityMainBinding
import com.example.notavia.settings.CategoryPreferences
import com.example.notavia.main.DeadlineFilter
import com.example.notavia.main.MainSection
import com.example.notavia.main.MainUiState
import com.example.notavia.main.MainViewModel
import com.example.notavia.main.NoteSortOption
import com.example.notavia.main.SelectionMode
import com.example.notavia.main.SortGroup
import com.example.notavia.ui.NoteAdapter
import com.example.notavia.ui.NoteCategoryUi
import com.example.notavia.ui.NotePriorityUi
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationBarView
import kotlinx.coroutines.launch

// Главный экран: список заметок и чек-листов, поиск, фильтры, сортировка и режим выбора.
class MainActivity : NotaviaActivity() {
    // ViewBinding, адаптер списка и доступ к локальному хранилищу заметок.
    private lateinit var binding: ActivityMainBinding
    private lateinit var noteAdapter: NoteAdapter
    private lateinit var viewModel: MainViewModel

    // Состояние главного экрана: текущий раздел, выбранные элементы, категории, фильтры и сортировки.
    private var latestUiState: MainUiState = MainUiState()
    private val categoryFilterButtons = linkedMapOf<String?, MaterialButton>()
    private val uiState: MainUiState
        get() = viewModel.uiState.value

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupSystemNavigationBarColor()
        setupBottomBarLayers()

        val defaultTopBarPadding = binding.defaultTopBar.capturePadding()
        val selectionTopBarPadding = binding.selectionTopBar.capturePadding()
        val bottomNavigationPadding = binding.bottomNavigationView.capturePadding()
        val selectionActionBarPadding = binding.selectionActionBar.capturePadding()
        val notesRecyclerViewPadding = binding.notesRecyclerView.capturePadding()
        val bottomNavigationBottomMargin = binding.bottomNavigationView.captureBottomMargin()
        val bottomNavigationBackgroundHeight = binding.bottomNavigationBackgroundView.captureHeight()
        val addNoteFabBottomMargin = binding.addNoteFab.captureBottomMargin()
        val selectionActionBarBottomMargin = binding.selectionActionBar.captureBottomMargin()

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigationView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = bottomNavigationPadding.left + systemBars.left,
                top = bottomNavigationPadding.top,
                right = bottomNavigationPadding.right + systemBars.right,
                bottom = bottomNavigationPadding.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }

        // Ручная обработка системных отступов для edge-to-edge режима и нижней навигации.
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val bottomBarInset = bottomBarInset(insets)
            val bottomContentLift = dp(BOTTOM_NAVIGATION_CONTENT_LIFT_DP)
            binding.bottomNavigationView.updateBottomMargin(
                bottomNavigationBottomMargin + bottomBarInset + bottomContentLift,
            )
            binding.bottomNavigationBackgroundView.updateHeight(bottomNavigationBackgroundHeight + bottomBarInset)
            binding.addNoteFab.updateBottomMargin(addNoteFabBottomMargin + bottomBarInset + bottomContentLift)
            binding.selectionActionBar.updateBottomMargin(selectionActionBarBottomMargin + bottomBarInset)
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
            binding.selectionActionBar.updatePadding(
                left = selectionActionBarPadding.left + systemBars.left,
                top = selectionActionBarPadding.top,
                right = selectionActionBarPadding.right + systemBars.right,
                bottom = selectionActionBarPadding.bottom,
            )
            binding.notesRecyclerView.updatePadding(
                left = notesRecyclerViewPadding.left,
                top = notesRecyclerViewPadding.top,
                right = notesRecyclerViewPadding.right,
                bottom = notesRecyclerViewPadding.bottom + bottomBarInset,
            )
            if (!isImeVisible && binding.searchEditText.hasFocus()) {
                clearSearchFocus()
            }
            insets
        }

        viewModel = ViewModelProvider(
            this,
            MainViewModel.Factory(
                NoteRepository(NotaviaDatabase.getDatabase(this).noteDao()),
                CategoryPreferences(this),
            ),
        )[MainViewModel::class.java]

        setupRecyclerView()
        setupActions()
        setupBackHandling()
        observeUiState()
        renderUi(latestUiState)
    }

    private fun setupBottomBarLayers() {
        binding.bottomNavigationBackgroundView.translationZ = 0f
        binding.bottomNavigationView.translationZ = dp(1).toFloat()
        binding.addNoteFab.stateListAnimator = null
        binding.addNoteFab.elevation = dp(8).toFloat()
        binding.addNoteFab.translationZ = dp(8).toFloat()
        binding.addNoteFab.bringToFront()
    }

    private fun setupSystemNavigationBarColor() {
        window.navigationBarColor = resolveThemeColor(
            com.google.android.material.R.attr.colorSurfaceContainerLow,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadNotes()
    }

    override fun onPause() {
        clearSearchFocus()
        super.onPause()
    }

    // Настройка списка: обычное нажатие открывает заметку, долгое нажатие включает выбор.
    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    latestUiState = state
                    renderUi(state)
                }
            }
        }
    }

    private fun setupRecyclerView() {
        noteAdapter = NoteAdapter(
            onNoteClicked = { note ->
                if (uiState.isNoteSelectionMode) {
                    viewModel.toggleNoteSelection(note.id)
                } else if (!uiState.isCategorySelectionMode) {
                    openViewer(note.id)
                }
            },
            onNoteLongClicked = { note ->
                if (!uiState.isSelectionMode) {
                    viewModel.enterNoteSelectionMode(note.id)
                }
            },
        )

        binding.notesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = noteAdapter
            setHasFixedSize(true)
        }
    }

    // Подключение кнопок главного экрана, поиска, нижней навигации и действий выбора.
    private fun setupActions() {
        installAlphaPressFeedback(binding.addNoteFab)
        installAlphaPressFeedback(binding.remindersButton)
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

        binding.remindersButton.setOnClickListener {
            clearSearchFocus()
            Toast.makeText(this, getString(R.string.reminders_coming_soon), Toast.LENGTH_SHORT).show()
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
            viewModel.setSearchQuery(editable?.toString().orEmpty())
        }

        binding.bottomNavigationView.selectedItemId = R.id.navigation_notes
        binding.bottomNavigationView.setOnItemSelectedListener(
            NavigationBarView.OnItemSelectedListener { item ->
                val section = when (item.itemId) {
                    R.id.navigation_notes -> MainSection.NOTES
                    R.id.navigation_checklists -> MainSection.CHECKLISTS
                    else -> return@OnItemSelectedListener false
                }
                if (section != MainSection.NOTES) {
                    binding.searchEditText.text?.clear()
                    clearSearchFocus()
                }
                viewModel.setSection(section)
                true
            },
        )

        binding.closeSelectionButton.setOnClickListener {
            viewModel.exitSelectionMode()
        }

        binding.selectAllButton.setOnClickListener {
            when (uiState.selectionMode) {
                SelectionMode.NOTES -> viewModel.selectAllVisibleNotes()
                SelectionMode.CATEGORIES -> viewModel.selectAllCategories()
                SelectionMode.NONE -> Unit
            }
        }

        binding.pinSelectedButton.setOnClickListener {
            when (uiState.selectionMode) {
                SelectionMode.NOTES -> viewModel.pinOrUnpinSelectedNotes()
                SelectionMode.CATEGORIES -> viewModel.pinOrUnpinSelectedCategories()
                SelectionMode.NONE -> Unit
            }
        }

        binding.deleteSelectedButton.setOnClickListener {
            when (uiState.selectionMode) {
                SelectionMode.NOTES -> showDeleteNotesConfirmation()
                SelectionMode.CATEGORIES -> viewModel.deleteSelectedCategories()
                SelectionMode.NONE -> Unit
            }
        }
    }

    // Диалог добавления категории без отдельного экрана.
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

    // Нормализация пользовательской категории и сохранение ее в DataStore.
    private fun addCustomCategory(rawCategory: String) {
        viewModel.addCustomCategory(rawCategory)
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (uiState.isSelectionMode) {
                    viewModel.exitSelectionMode()
                } else if (binding.searchEditText.hasFocus()) {
                    clearSearchFocus()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    // Загрузка всех заметок из Room через Repository.
    private fun loadNotes() {
        viewModel.loadNotes()
    }

    // Единая пересборка списка: раздел, поиск, фильтры, сортировка и отправка в адаптер.
    private fun applySearchFilter() {
        renderUi(uiState)
    }

    // Обновление видимости основных блоков интерфейса под текущее состояние экрана.
    private fun renderUi(state: MainUiState) {
        val isNotesSection = state.currentSection == MainSection.NOTES
        val hasVisibleNotes = state.visibleNotes.isNotEmpty()
        val showSearch = isNotesSection && !state.isSelectionMode
        val showCategoryFilter = isNotesSection && !state.isNoteSelectionMode

        binding.screenTitleTextView.setText(
            if (isNotesSection) R.string.notes_title else R.string.checklists_title,
        )
        binding.defaultTopBar.isVisible = !state.isSelectionMode
        binding.selectionTopBar.isVisible = state.isSelectionMode
        binding.searchActionsRow.isVisible = showSearch
        binding.searchCardView.isVisible = showSearch
        binding.categoryFilterScrollView.isVisible = showCategoryFilter
        binding.checklistsPlaceholderGroup.isVisible = false
        binding.bottomNavigationView.isVisible = !state.isSelectionMode
        binding.bottomNavigationBackgroundView.isVisible = !state.isSelectionMode
        binding.selectionActionBar.isVisible = state.isSelectionMode

        binding.notesRecyclerView.isVisible = hasVisibleNotes
        binding.emptyStateGroup.isVisible = !hasVisibleNotes
        binding.addNoteFab.isVisible = !state.isSelectionMode
        binding.addNoteFab.bringToFront()

        noteAdapter.submitList(state.visibleNotes)

        if (state.isSelectionMode) {
            updateSelectionTitle(state)
        }

        updateEmptyState(state)
        updateFilterSortButtons(state)
        updateSelectionControls()
        renderCategoryFilters(state)
        noteAdapter.updateSelectionState(state.isNoteSelectionMode, state.selectedNoteIds)
    }

    private fun updateEmptyState(state: MainUiState) {
        val hasSearch = state.searchQuery.isNotBlank() || state.hasActiveFilters
        binding.emptyTitleTextView.text = getString(
            when {
                hasSearch -> R.string.empty_search_title
                state.currentSection == MainSection.CHECKLISTS -> R.string.empty_checklists_title
                else -> R.string.empty_state_title
            },
        )
        binding.emptyMessageTextView.text = getString(
            when {
                hasSearch -> R.string.empty_search_message
                state.currentSection == MainSection.CHECKLISTS -> R.string.empty_checklists_message
                else -> R.string.empty_state_message
            },
        )
    }

    // Подсветка иконок фильтра и сортировки при активных параметрах.
    private fun updateFilterSortButtons(state: MainUiState) {
        if (state.currentSection != MainSection.NOTES) return

        val filtersActive = state.hasActiveFilters
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
            if (state.selectedSortOptions.isNotEmpty()) {
                ContextCompat.getColor(this, R.color.selection_stroke_color)
            } else {
                resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
            },
        )
    }

    private fun hasActiveFilters(): Boolean {
        return uiState.hasActiveFilters
    }

    // Сортировка видимого списка: закрепленные элементы выше, затем выбранные правила сортировки.
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
        val sortOptions = uiState.selectedSortOptions.values.toList()
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

    // Перерисовка горизонтальной строки категорий на главном экране.
    private fun renderCategoryFilters(state: MainUiState) {
        categoryFilterButtons.clear()
        binding.categoryFilterContainer.removeAllViews()

        if (
            state.currentSection != MainSection.NOTES ||
            state.isNoteSelectionMode
        ) {
            return
        }

        addCategoryAddButton()
        addCategoryFilterButton(null, getString(R.string.all_categories))
        viewModel.availableCategoryFilters().forEach { category ->
            addCategoryFilterButton(category, NoteCategoryUi.displayName(this, category))
        }
        updateCategoryFilterButtons(state)
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
                if (uiState.isCategorySelectionMode) {
                    if (!viewModel.isProtectedCategory(category)) {
                        category?.let { viewModel.toggleCategorySelection(it) }
                    }
                } else {
                    viewModel.selectSingleCategoryFilter(category)
                    clearSearchFocus()
                }
            }
            setOnLongClickListener {
                if (!viewModel.isProtectedCategory(category)) {
                    category?.let {
                        viewModel.enterCategorySelectionMode(it)
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

    private fun updateCategoryFilterButtons(state: MainUiState) {
        categoryFilterButtons.forEach { (category, button) ->
            val isActive = if (state.isCategorySelectionMode) {
                category != null &&
                    !viewModel.isProtectedCategory(category) &&
                    state.selectedCategoryNames.contains(category)
            } else {
                if (category == null) {
                    state.selectedCategoryFilters.isEmpty()
                } else {
                    state.selectedCategoryFilters.contains(category)
                }
            }
            styleCategoryFilterButton(
                button,
                isActive = isActive,
                isPinned = category != null &&
                    !viewModel.isProtectedCategory(category) &&
                    state.pinnedCategoryNames.contains(category),
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

    // Нижнее окно фильтров по приоритету, дедлайну и категории.
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
                isSelected = { uiState.selectedPriorityFilters.isEmpty() },
                registerSelectionUpdater = filterRowRefreshers::add,
            ) {
                viewModel.clearPriorityFilters()
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
                    viewModel.togglePriorityFilter(priority)
                    onFilterChanged()
                },
            )
        }

        content.addView(createBottomSheetSectionTitle(getString(R.string.filter_deadline_title)))
        content.addView(
            createSheetOptionRow(
                title = getString(R.string.filter_any),
                isSelected = { uiState.selectedDeadlineFilters.isEmpty() },
                registerSelectionUpdater = filterRowRefreshers::add,
            ) {
                viewModel.clearDeadlineFilters()
                onFilterChanged()
            },
        )
        DeadlineFilter.entries.forEach { deadlineFilter ->
            content.addView(
                createSheetOptionRow(
                    title = deadlineFilter.title(),
                    isSelected = { uiState.selectedDeadlineFilters.contains(deadlineFilter) },
                    registerSelectionUpdater = filterRowRefreshers::add,
                ) {
                    viewModel.toggleDeadlineFilter(deadlineFilter)
                    onFilterChanged()
                },
            )
        }

        content.addView(createBottomSheetSectionTitle(getString(R.string.filter_category_title)))
        content.addView(
            createSheetOptionRow(
                title = getString(R.string.filter_any),
                isSelected = { uiState.selectedCategoryFilters.isEmpty() },
                registerSelectionUpdater = filterRowRefreshers::add,
            ) {
                viewModel.clearCategoryFilters()
                onFilterChanged()
            },
        )
        viewModel.availableCategoryFilters().forEach { category ->
            content.addView(
                createSheetOptionRow(
                    title = NoteCategoryUi.displayName(this, category),
                    isSelected = { uiState.selectedCategoryFilters.contains(category) },
                    registerSelectionUpdater = filterRowRefreshers::add,
                ) {
                    viewModel.toggleCategoryFilter(category)
                    onFilterChanged()
                },
            )
        }

        content.addView(
            createResetFiltersButton {
                viewModel.clearAllFilters()
                dialog.dismiss()
            },
        )

        val filterSheetHeight = resources.displayMetrics.heightPixels / 2
        dialog.setContentView(wrapBottomSheetContent(content, fixedHeight = filterSheetHeight))
        styleBottomSheet(dialog, fixedHeight = filterSheetHeight)
        dialog.show()
    }

    // Нижнее окно выбора сортировки по дате создания, приоритету и дедлайну.
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
                viewModel.clearSortOptions()
                dialog.dismiss()
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
                text = uiState.selectedSortOptions[group]?.title() ?: getString(R.string.sort_none)
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
                valueTextView.text = uiState.selectedSortOptions[group]?.title() ?: getString(R.string.sort_none)
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
                    isSelected = uiState.selectedSortOptions[group] == choice.option,
                ) {
                    popupWindow?.dismiss()
                    viewModel.setSortOption(group, choice.option)
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

    // Переключатели мультивыбора для групп фильтров.
    private fun togglePriorityFilter(priority: NotePriority) {
        viewModel.togglePriorityFilter(priority)
    }

    private fun toggleDeadlineFilter(deadlineFilter: DeadlineFilter) {
        viewModel.toggleDeadlineFilter(deadlineFilter)
    }

    private fun toggleCategoryFilter(category: String) {
        viewModel.toggleCategoryFilter(category)
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
            isSelected = { uiState.selectedPriorityFilters.contains(priority) },
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
        // Category filters are now normalized inside MainViewModel.
    }

    private fun availableCategoryFilters(): List<String> {
        return viewModel.availableCategoryFilters()
    }

    private fun isProtectedCategory(category: String?): Boolean {
        return viewModel.isProtectedCategory(category)
    }

    // Подписки на DataStore с состоянием закрепленных, скрытых и пользовательских категорий.
    private fun observePinnedCategories() {
        // Category streams are observed by MainViewModel.
    }

    private fun observeHiddenCategories() {
        // Category streams are observed by MainViewModel.
    }

    private fun observeCustomCategories() {
        // Category streams are observed by MainViewModel.
    }

    // Обновление верхней панели в режиме выбора заметок или категорий.
    private fun updateSelectionTitle(state: MainUiState) {
        binding.selectionCountTextView.text = when (state.selectionMode) {
            SelectionMode.NOTES -> getString(
                R.string.selected_count_format,
                state.selectedNoteIds.size,
            )
            SelectionMode.CATEGORIES -> getString(
                R.string.selected_categories_count_format,
                state.selectedCategoryNames.size,
            )
            SelectionMode.NONE -> getString(R.string.selected_count)
        }
    }

    private fun updateSelectionControls() {
        val shouldUnpin = viewModel.shouldUnpinSelection()
        binding.pinSelectedButton.text = getString(
            if (shouldUnpin) R.string.unpin_action else R.string.pin_action,
        )
    }

    private fun selectAllVisibleNotes() {
        viewModel.selectAllVisibleNotes()
    }

    private fun selectAllCategories() {
        viewModel.selectAllCategories()
    }

    private fun enterNoteSelectionMode(initialNoteId: Long) {
        viewModel.enterNoteSelectionMode(initialNoteId)
    }

    private fun enterCategorySelectionMode(initialCategory: String) {
        viewModel.enterCategorySelectionMode(initialCategory)
        clearSearchFocus()
    }

    private fun exitSelectionMode() {
        viewModel.exitSelectionMode()
    }

    private fun toggleSelection(noteId: Long) {
        viewModel.toggleNoteSelection(noteId)
    }

    private fun toggleCategorySelection(category: String) {
        viewModel.toggleCategorySelection(category)
    }

    private fun pinOrUnpinSelectedNotes() {
        viewModel.pinOrUnpinSelectedNotes()
    }

    private fun pinOrUnpinSelectedCategories() {
        viewModel.pinOrUnpinSelectedCategories()
    }

    // Подтверждение удаления через BottomSheet перед изменением базы.
    private fun showDeleteNotesConfirmation() {
        val count = uiState.selectedNoteIds.size
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
        viewModel.deleteSelectedNotes()
    }

    // Удаление категорий из заметок и обновление сохраненного состояния категорий.
    private fun deleteSelectedCategories() {
        viewModel.deleteSelectedCategories()
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
            putExtra(EditNoteActivity.EXTRA_NOTE_TYPE, uiState.currentSection.noteType.storageValue)
        }
        startActivity(intent)
    }

    private fun clearChecklistOnlyControls() {
        binding.searchEditText.text?.clear()
        clearSearchFocus()
    }

    private fun clearSearchFocus() {
        binding.searchEditText.clearFocus()
        binding.main.requestFocus()
        val inputMethodManager = getSystemService<InputMethodManager>()
        inputMethodManager?.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
    }

    private fun bottomBarInset(insets: WindowInsetsCompat): Int {
        val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        val tappableElement = insets.getInsets(WindowInsetsCompat.Type.tappableElement())
        return if (tappableElement.bottom > 0 && tappableElement.bottom >= navigationBars.bottom) {
            navigationBars.bottom
        } else {
            0
        }
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

    // Внутренние типы состояния главного экрана.
    private data class SortChoice(
        val option: NoteSortOption?,
        val title: String,
    )

    companion object {
        private const val BUTTON_PRESSED_ALPHA = 0.68f
        private const val BOTTOM_NAVIGATION_CONTENT_LIFT_DP = 20
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

private fun View.captureBottomMargin(): Int {
    return (layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
}

private fun View.captureHeight(): Int {
    val layoutHeight = layoutParams?.height ?: 0
    return if (layoutHeight > 0) layoutHeight else height
}

private fun View.updateHeight(height: Int) {
    if (layoutParams.height == height) return

    layoutParams = layoutParams.apply {
        this.height = height
    }
}

private fun View.updateBottomMargin(bottomMargin: Int) {
    val marginLayoutParams = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    if (marginLayoutParams.bottomMargin == bottomMargin) return

    marginLayoutParams.bottomMargin = bottomMargin
    layoutParams = marginLayoutParams
}
