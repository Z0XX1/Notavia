package com.example.notavia

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.notavia.data.NotePriority
import com.example.notavia.databinding.ActivityViewNoteBinding
import com.example.notavia.di.notaviaContainer
import com.example.notavia.navigation.NoteNavigationContract.EXTRA_NOTE_ID
import com.example.notavia.navigation.NoteNavigationContract.NO_NOTE_ID
import com.example.notavia.ui.NoteCategoryUi
import com.example.notavia.ui.NotePriorityUi
import com.example.notavia.ui.currentLocale
import com.example.notavia.ui.installAlphaPressFeedback
import com.example.notavia.ui.resolveThemeColor
import com.example.notavia.viewer.ChecklistInteractionController
import com.example.notavia.viewer.ViewNoteEffect
import com.example.notavia.viewer.ViewNoteUiState
import com.example.notavia.viewer.ViewNoteViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date


class ViewNoteActivity : NotaviaActivity() {

    private lateinit var binding: ActivityViewNoteBinding
    private lateinit var viewModel: ViewNoteViewModel
    private lateinit var checklistController: ChecklistInteractionController

    private var noteId: Long = NO_NOTE_ID
    private var latestUiState: ViewNoteUiState = ViewNoteUiState()

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

        val appContainer = notaviaContainer()
        viewModel = ViewModelProvider(
            this,
            ViewNoteViewModel.Factory(appContainer.notesRepository),
        )[ViewNoteViewModel::class.java]
        checklistController = ChecklistInteractionController(
            activity = this,
            container = binding.checklistItemsContainer,
            viewModel = viewModel,
        )
        checklistController.setup()
        noteId = intent.getLongExtra(EXTRA_NOTE_ID, NO_NOTE_ID)

        observeViewState()
        setupActions()
        setupBackHandling()
    }

    override fun onResume() {
        super.onResume()
        viewModel.load(noteId)
    }

    override fun onPause() {
        viewModel.flushChecklistAutoSave()
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
            if (checklistController.isSelectionMode()) {
                checklistController.exitSelectionMode()
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
                putExtra(EXTRA_NOTE_ID, noteId)
            }
            startActivity(intent)
        }

        binding.selectAllChecklistItemsButton.setOnClickListener {
            checklistController.selectAllOrClear()
        }

        binding.deleteChecklistItemsButton.setOnClickListener {
            checklistController.showDeleteSelectedConfirmation()
        }

        binding.deleteChecklistSelectionButton.setOnClickListener {
            checklistController.showDeleteSelectedConfirmation()
        }

    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (checklistController.isSelectionMode()) {
                    checklistController.exitSelectionMode()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }


    private fun observeViewState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderViewState(state)
                    }
                }
                launch {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            ViewNoteEffect.Finish -> finish()
                        }
                    }
                }
            }
        }
    }

    private fun saveCurrentNote() {
        currentFocus?.clearFocus()
        viewModel.saveCurrentNote(
            title = binding.titleTextView.text?.toString()?.trim().orEmpty(),
            content = binding.contentTextView.text?.toString().orEmpty(),
        )
    }


    private fun renderViewState(state: ViewNoteUiState) {
        val note = state.note ?: return
        latestUiState = state
        val isChecklist = state.isChecklist
        binding.screenTitleTextView.setText(R.string.view_note_title)
        binding.saveButton.visibility = View.GONE
        binding.editButton.visibility = View.VISIBLE
        binding.selectAllChecklistItemsButton.visibility = View.GONE
        binding.deleteChecklistItemsButton.visibility = View.GONE
        binding.checklistSelectionActionBar.visibility = View.GONE
        binding.titleTextView.hint = getString(R.string.untitled_note)
        binding.titleTextView.setText(note.title)
        if (isChecklist) {
            binding.contentTextView.visibility = View.GONE
            binding.checklistItemsContainer.visibility = View.VISIBLE
            renderChecklistItems(state)
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
            updateChecklistTopBar(state)
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


    private fun renderChecklistItems(state: ViewNoteUiState) {
        checklistController.render(state)
        binding.contentTextView.visibility = View.GONE
    }

    private fun updateChecklistTopBar(state: ViewNoteUiState = latestUiState) {
        val isSelectionMode = state.checklist.hasSelection
        binding.screenTitleTextView.text = if (isSelectionMode) {
            getString(R.string.selected_count_format, state.checklist.selectedCount)
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

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()


    private fun countWords(text: String): Int {
        return Regex("\\S+").findAll(text).count()
    }

    private fun countLines(text: String): Int {
        if (text.isBlank()) return 0
        return text.split('\n').size
    }

    private fun deadlineFormatter(): SimpleDateFormat {
        return SimpleDateFormat(DEADLINE_DATE_PATTERN, currentLocale())
    }

    private fun isDeadlineOverdue(deadlineAt: Long): Boolean {
        return deadlineAt < System.currentTimeMillis()
    }

    companion object {
        private const val CHECKLIST_TOP_MARGIN_DP = 10
        private const val CHECKLIST_PINNED_TOP_MARGIN_DP = 18
        private const val DEADLINE_DATE_PATTERN = "d MMM yyyy"
    }
}
