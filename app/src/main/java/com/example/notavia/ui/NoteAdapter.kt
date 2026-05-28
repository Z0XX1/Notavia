package com.example.notavia.ui

import android.content.res.ColorStateList
import android.graphics.Paint
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.example.notavia.R
import com.example.notavia.data.ChecklistContent
import com.example.notavia.data.Note
import com.example.notavia.data.NoteCategories
import com.example.notavia.data.NotePriority
import com.example.notavia.data.NoteType
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
            val noteType = NoteType.fromStorage(note.type)
            if (noteType == NoteType.CHECKLIST) {
                binding.contentTextView.visibility = View.GONE
                binding.checklistPreviewContainer.visibility = View.VISIBLE
                renderChecklistPreview(note.content)
            } else {
                binding.contentTextView.visibility = View.VISIBLE
                binding.checklistPreviewContainer.visibility = View.GONE
                binding.contentTextView.text = note.content.ifBlank {
                    binding.root.context.getString(R.string.empty_note_preview)
                }
            }
            binding.categoryTextView.text = NoteCategories.display(note.category)
            binding.categoryTextView.visibility = if (noteType == NoteType.CHECKLIST) {
                View.GONE
            } else {
                View.VISIBLE
            }
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

        private fun renderChecklistPreview(content: String) {
            binding.checklistPreviewContainer.removeAllViews()
            val context = binding.root.context
            val items = ChecklistContent.parse(content).take(MAX_CHECKLIST_PREVIEW_ITEMS)

            if (items.isEmpty()) {
                binding.contentTextView.visibility = View.VISIBLE
                binding.checklistPreviewContainer.visibility = View.GONE
                binding.contentTextView.text = context.getString(R.string.empty_checklist_preview)
                return
            }

            items.forEach { item ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 1.dp, 0, 1.dp)
                }

                row.addView(
                    ImageView(context).apply {
                        setImageResource(if (item.isDone) R.drawable.checkbox else R.drawable.emptybox)
                        setColorFilter(
                            ContextCompat.getColor(context, R.color.note_stroke_color),
                        )
                        contentDescription = null
                    },
                    LinearLayout.LayoutParams(16.dp, 16.dp).apply {
                        marginEnd = 6.dp
                    },
                )

                row.addView(
                    TextView(context).apply {
                        text = item.text
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        textSize = 13f
                        setTextColor(
                            ContextCompat.getColor(context, R.color.note_stroke_color),
                        )
                        paintFlags = if (item.isDone) {
                            paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                        } else {
                            paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                        }
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )

                binding.checklistPreviewContainer.addView(
                    row,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        }

        private val Int.dp: Int
            get() = (this * binding.root.resources.displayMetrics.density).toInt()
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
        private const val MAX_CHECKLIST_PREVIEW_ITEMS = 3
    }
}
