package com.example.notavia

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.notavia.data.Note
import com.example.notavia.data.NoteRepository
import com.example.notavia.data.NotaviaDatabase
import com.example.notavia.databinding.ActivityMainBinding
import com.example.notavia.ui.NoteAdapter
import com.google.android.material.navigation.NavigationBarView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var noteAdapter: NoteAdapter
    private lateinit var repository: NoteRepository

    private var currentSection: MainSection = MainSection.NOTES
    private var allNotes: List<Note> = emptyList()
    private var visibleNotes: List<Note> = emptyList()
    private val selectedNoteIds = linkedSetOf<Long>()
    private var isSelectionMode: Boolean = false
    private var searchQuery: String = ""

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

        setupRecyclerView()
        setupActions()
        setupBackHandling()
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
                if (isSelectionMode) {
                    toggleSelection(note.id)
                } else {
                    openViewer(note.id)
                }
            },
            onNoteLongClicked = { note ->
                if (!isSelectionMode) {
                    enterSelectionMode(note.id)
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
            if (visibleNotes.isEmpty()) return@setOnClickListener
            val visibleIds = visibleNotes.map { it.id }.toSet()
            val allVisibleSelected = visibleIds.isNotEmpty() && selectedNoteIds.containsAll(visibleIds)
            selectedNoteIds.clear()
            if (!allVisibleSelected) {
                selectedNoteIds.addAll(visibleIds)
            }
            renderUi()
        }

        binding.pinSelectedButton.setOnClickListener {
            pinOrUnpinSelectedNotes()
        }

        binding.deleteSelectedButton.setOnClickListener {
            deleteSelectedNotes()
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
        visibleNotes = if (searchQuery.isBlank()) {
            allNotes
        } else {
            allNotes.filter { note ->
                note.title.contains(searchQuery, ignoreCase = true)
            }
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

        binding.screenTitleTextView.setText(
            if (isNotesSection) R.string.notes_title else R.string.checklists_title,
        )
        binding.defaultTopBar.isVisible = !isSelectionMode
        binding.selectionTopBar.isVisible = isSelectionMode
        binding.searchCardView.isVisible = showSearch
        binding.checklistsPlaceholderGroup.isVisible = !isNotesSection
        binding.bottomNavigationView.isVisible = !isSelectionMode
        binding.selectionActionBar.isVisible = isSelectionMode

        binding.notesRecyclerView.isVisible = isNotesSection && hasVisibleNotes
        binding.emptyStateGroup.isVisible = isNotesSection && !hasVisibleNotes
        binding.addNoteFab.isVisible = isNotesSection && !isSelectionMode

        if (isSelectionMode) {
            binding.selectionCountTextView.text = getString(
                R.string.selected_count_format,
                selectedNoteIds.size,
            )
        }

        updateEmptyState()
        updateSelectionControls()
        noteAdapter.updateSelectionState(isSelectionMode, selectedNoteIds)
    }

    private fun updateEmptyState() {
        if (currentSection != MainSection.NOTES) return

        val hasSearch = searchQuery.isNotBlank()
        binding.emptyTitleTextView.text = getString(
            if (hasSearch) R.string.empty_search_title else R.string.empty_state_title,
        )
        binding.emptyMessageTextView.text = getString(
            if (hasSearch) R.string.empty_search_message else R.string.empty_state_message,
        )
    }

    private fun updateSelectionControls() {
        val selectedNotes = allNotes.filter { selectedNoteIds.contains(it.id) }
        val shouldUnpin = selectedNotes.isNotEmpty() && selectedNotes.all { it.isPinned }
        binding.pinSelectedButton.text = getString(
            if (shouldUnpin) R.string.unpin_action else R.string.pin_action,
        )
    }

    private fun enterSelectionMode(initialNoteId: Long) {
        isSelectionMode = true
        selectedNoteIds.clear()
        selectedNoteIds.add(initialNoteId)
        renderUi()
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedNoteIds.clear()
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

    private fun deleteSelectedNotes() {
        if (selectedNoteIds.isEmpty()) return

        lifecycleScope.launch {
            repository.deleteNotes(selectedNoteIds.toList())
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

    private enum class MainSection {
        NOTES,
        CHECKLISTS,
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
