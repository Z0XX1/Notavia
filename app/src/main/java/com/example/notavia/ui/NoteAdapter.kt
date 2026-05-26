package com.example.notavia.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.example.notavia.R
import com.example.notavia.data.Note
import com.example.notavia.data.NoteCategories
import com.example.notavia.data.NotePriority
import com.example.notavia.databinding.ItemNoteBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteAdapter(
    private val onNoteClicked: (Note) -> Unit,
    private val onNoteLongClicked: (Note) -> Unit,
) : ListAdapter<Note, NoteAdapter.NoteViewHolder>(NoteDiffCallback()) {

    private var isSelectionMode: Boolean = false
    private var selectedIds: Set<Long> = emptySet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemNoteBinding.inflate(inflater, parent, false)
        return NoteViewHolder(binding, onNoteClicked, onNoteLongClicked)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(
            note = getItem(position),
            isSelectionMode = isSelectionMode,
            isSelected = selectedIds.contains(getItem(position).id),
        )
    }

    fun updateSelectionState(isSelectionMode: Boolean, selectedIds: Set<Long>) {
        this.isSelectionMode = isSelectionMode
        this.selectedIds = selectedIds
        notifyDataSetChanged()
    }

    class NoteViewHolder(
        private val binding: ItemNoteBinding,
        private val onNoteClicked: (Note) -> Unit,
        private val onNoteLongClicked: (Note) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormatter = SimpleDateFormat(UPDATED_AT_PATTERN, Locale.ENGLISH)
        private val deadlineFormatter = SimpleDateFormat(DEADLINE_DATE_PATTERN, Locale.ENGLISH)

        fun bind(note: Note, isSelectionMode: Boolean, isSelected: Boolean) {
            binding.titleTextView.text = note.title.ifBlank {
                binding.root.context.getString(R.string.untitled_note)
            }
            binding.contentTextView.text = note.content.ifBlank {
                binding.root.context.getString(R.string.empty_note_preview)
            }
            binding.categoryTextView.text = NoteCategories.display(note.category)
            val priority = NotePriority.fromStorage(note.priority)
            binding.priorityIndicatorImageView.visibility = if (priority == NotePriority.NONE) {
                View.GONE
            } else {
                View.VISIBLE
            }
            NotePriorityUi.applyTo(binding.priorityIndicatorImageView, priority)
            binding.updatedAtTextView.text = note.deadlineAt?.let { deadline ->
                binding.root.context.getString(
                    R.string.updated_at_with_deadline_format,
                    dateFormatter.format(Date(note.updatedAt)),
                    deadlineFormatter.format(Date(deadline)),
                )
            } ?: binding.root.context.getString(
                R.string.updated_at_format,
                dateFormatter.format(Date(note.updatedAt)),
            )

            binding.pinnedImageView.visibility = if (note.isPinned) {
                View.VISIBLE
            } else {
                View.GONE
            }

            binding.selectionImageView.visibility = if (isSelectionMode) {
                View.VISIBLE
            } else {
                View.GONE
            }
            if (isSelectionMode) {
                binding.selectionImageView.setImageResource(
                    if (isSelected) R.drawable.checkcircle else R.drawable.emptycircle,
                )
                binding.selectionImageView.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        binding.root.context,
                        if (isSelected) R.color.selection_stroke_color
                        else R.color.note_stroke_color,
                    ),
                )
            } else {
                binding.selectionImageView.imageTintList = null
            }

            binding.root.strokeWidth = if (isSelected) 3 else 1
            binding.root.strokeColor = ContextCompat.getColor(
                binding.root.context,
                if (isSelected) R.color.selection_stroke_color else R.color.note_stroke_color,
            )

            binding.root.setOnClickListener {
                onNoteClicked(note)
            }

            binding.root.setOnLongClickListener {
                onNoteLongClicked(note)
                true
            }
        }
    }

    class NoteDiffCallback : DiffUtil.ItemCallback<Note>() {
        override fun areItemsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private const val UPDATED_AT_PATTERN = "MMM d, yyyy h:mma"
        private const val DEADLINE_DATE_PATTERN = "MMM d, yyyy"
    }
}
