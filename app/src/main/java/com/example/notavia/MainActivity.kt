package com.example.notavia

import android.content.res.ColorStateList
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
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
import com.example.notavia.data.NoteRepository
import com.example.notavia.data.NotaviaDatabase
import com.example.notavia.databinding.ActivityMainBinding
import com.example.notavia.settings.CategoryPreferences
import com.example.notavia.main.MainCategoryFilterBar
import com.example.notavia.main.MainSection
import com.example.notavia.main.MainSheets
import com.example.notavia.main.MainUiState
import com.example.notavia.main.MainViewModel
import com.example.notavia.main.SelectionMode
import com.example.notavia.navigation.NoteNavigationContract.EXTRA_NOTE_ID
import com.example.notavia.navigation.NoteNavigationContract.EXTRA_NOTE_TYPE
import com.example.notavia.ui.NoteAdapter
import com.example.notavia.ui.CategoryInputDialog
import com.example.notavia.ui.captureBottomMargin
import com.example.notavia.ui.captureHeight
import com.example.notavia.ui.capturePadding
import com.example.notavia.ui.dp
import com.example.notavia.ui.installAlphaPressFeedback
import com.example.notavia.ui.resolveThemeColor
import com.example.notavia.ui.updateBottomMargin
import com.example.notavia.ui.updateHeight
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationBarView
import kotlinx.coroutines.launch


class MainActivity : NotaviaActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var noteAdapter: NoteAdapter
    private lateinit var viewModel: MainViewModel
    private lateinit var mainSheets: MainSheets
    private lateinit var categoryFilterBar: MainCategoryFilterBar


    private var latestUiState: MainUiState = MainUiState()
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
        mainSheets = MainSheets(this, viewModel) { uiState }
        categoryFilterBar = MainCategoryFilterBar(
            activity = this,
            container = binding.categoryFilterContainer,
            viewModel = viewModel,
            stateProvider = { uiState },
            onAddCategory = ::showAddCategoryDialog,
            onClearSearchFocus = ::clearSearchFocus,
        )

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
            mainSheets.showFilterSheet()
        }

        binding.sortButton.setOnClickListener {
            clearSearchFocus()
            mainSheets.showSortSheet()
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


    private fun showAddCategoryDialog() {
        CategoryInputDialog(this) { rawCategory ->
            addCustomCategory(rawCategory)
        }.show()
    }


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
        categoryFilterBar.render(state)
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


    private fun openViewer(noteId: Long) {
        val intent = Intent(this, ViewNoteActivity::class.java).apply {
            putExtra(EXTRA_NOTE_ID, noteId)
        }
        startActivity(intent)
    }

    private fun openEditor(noteId: Long? = null) {
        val intent = Intent(this, EditNoteActivity::class.java).apply {
            noteId?.let { putExtra(EXTRA_NOTE_ID, it) }
            putExtra(EXTRA_NOTE_TYPE, uiState.currentSection.noteType.storageValue)
        }
        startActivity(intent)
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

    companion object {
        private const val BOTTOM_NAVIGATION_CONTENT_LIFT_DP = 20
    }
}
