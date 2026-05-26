package com.example.notavia

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.notavia.data.Note
import com.example.notavia.data.NoteCategories
import com.example.notavia.data.NotePriority
import com.example.notavia.data.NoteRepository
import com.example.notavia.data.NotaviaDatabase
import com.example.notavia.databinding.ActivityViewNoteBinding
import com.example.notavia.ui.NotePriorityUi
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ViewNoteActivity : NotaviaActivity() {
    private lateinit var binding: ActivityViewNoteBinding
    private lateinit var repository: NoteRepository

    private var noteId: Long = NO_NOTE_ID
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
    }

    override fun onResume() {
        super.onResume()
        loadNote()
    }

    private fun setupActions() {
        installAlphaPressFeedback(binding.backButton)
        installAlphaPressFeedback(binding.editButton)

        binding.backButton.setOnClickListener {
            finish()
        }

        binding.editButton.setOnClickListener {
            if (noteId == NO_NOTE_ID) return@setOnClickListener
            val intent = Intent(this, EditNoteActivity::class.java).apply {
                putExtra(EditNoteActivity.EXTRA_NOTE_ID, noteId)
            }
            startActivity(intent)
        }
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

    private fun bindNote(note: Note) {
        binding.titleTextView.text = note.title.ifBlank {
            getString(R.string.untitled_note)
        }
        binding.contentTextView.text = note.content.ifBlank {
            getString(R.string.empty_note_preview)
        }
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

    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
        private const val NO_NOTE_ID = -1L
        private const val BUTTON_PRESSED_ALPHA = 0.68f
        private const val DEADLINE_DATE_PATTERN = "d MMM yyyy"
        private val RUSSIAN_LOCALE: Locale = Locale.forLanguageTag("ru")
    }
}
