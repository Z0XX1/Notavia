package com.example.notavia

import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.notavia.checklist.ChecklistState
import com.example.notavia.data.ChecklistItem
import com.example.notavia.data.NoteCategories
import com.example.notavia.data.NotePriority
import com.example.notavia.data.NoteRepository
import com.example.notavia.data.NoteType
import com.example.notavia.data.NotaviaDatabase
import com.example.notavia.databinding.ActivityEditNoteBinding
import com.example.notavia.editor.EditNoteEffect
import com.example.notavia.editor.EditNoteUiState
import com.example.notavia.editor.EditNoteViewModel
import com.example.notavia.editor.NoteDraft
import com.example.notavia.settings.CategoryPreferences
import com.example.notavia.ui.NoteCategoryUi
import com.example.notavia.ui.NotePriorityUi
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Экран создания и редактирования заметок и чек-листов.
class EditNoteActivity : NotaviaActivity() {
    // Основные зависимости редактора: ViewBinding, Repository и настройки категорий.
    private lateinit var binding: ActivityEditNoteBinding
    private lateinit var viewModel: EditNoteViewModel
    private lateinit var repository: NoteRepository
    private lateinit var categoryPreferences: CategoryPreferences

    // Состояние редактируемой записи, выбранных категорий, приоритета, дедлайна и автосохранения.
    private var noteId: Long = NO_NOTE_ID
    private var selectedNoteType: NoteType = NoteType.NOTE
    private val checklistItems = ChecklistState()
    private val selectedCategories = linkedSetOf(NoteCategories.DEFAULT)
    private val categoryButtons = mutableMapOf<String, MaterialButton>()
    private val customCategories = linkedSetOf<String>()
    private val hiddenCategories = linkedSetOf<String>()
    private var selectedPriority: NotePriority = NotePriority.NONE
    private var selectedDeadlineAt: Long? = null
    private var isDeadlinePickerExpanded: Boolean = false
    private var isUpdatingDeadlinePickers: Boolean = false
    private var isApplyingLoadedNote: Boolean = false
    private var renderedLoadedNoteId: Long? = null
    private val deadlineDateFormatter: SimpleDateFormat by lazy {
        SimpleDateFormat(DEADLINE_DATE_PATTERN, currentLocale())
    }
    private val monthLabels: Array<String> by lazy {
        val locale = currentLocale()
        DateFormatSymbols.getInstance(locale).shortMonths
            .take(12)
            .map { month ->
                month.trim()
                    .removeSuffix(".")
                    .lowercase(locale)
            }
            .toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEditNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val bottomInset = if (isImeVisible) {
                maxOf(systemBars.bottom, ime.bottom)
            } else {
                systemBars.bottom
            }
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomInset)
            if (isImeVisible && binding.contentEditText.hasFocus()) {
                scrollToContentCursor()
            }
            if (!isImeVisible && hasEditorFocus()) {
                clearEditorFocus()
            }
            insets
        }

        repository = NoteRepository(NotaviaDatabase.getDatabase(this).noteDao())
        viewModel = ViewModelProvider(
            this,
            EditNoteViewModel.Factory(repository),
        )[EditNoteViewModel::class.java]
        categoryPreferences = CategoryPreferences(this)
        noteId = intent.getLongExtra(EXTRA_NOTE_ID, NO_NOTE_ID)
        selectedNoteType = NoteType.fromStorage(intent.getStringExtra(EXTRA_NOTE_TYPE))

        observeEditorState()
        setupActions()
        setupCategoryPicker()
        setupDeadlinePicker()
        updatePriorityUi()
        updateEditorMode()

        if (noteId != NO_NOTE_ID) {
            viewModel.start(noteId, selectedNoteType)
        } else {
            binding.screenTitleTextView.text = getString(
                if (selectedNoteType == NoteType.CHECKLIST) {
                    R.string.new_checklist_title
                } else {
                    R.string.new_note_title
                },
            )
            focusTitleField()
            viewModel.start(null, selectedNoteType)
        }

        setupBackHandling()
    }

    // Подключение кнопок, текстовых полей и обработчиков изменения содержимого.
    private fun setupActions() {
        installAlphaPressFeedback(binding.backButton)

        binding.backButton.setOnClickListener {
            finishAfterAutoSave()
        }

        binding.priorityButton.setOnClickListener {
            showPriorityMenu()
        }
        binding.priorityButton.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.alpha = PRIORITY_BUTTON_PRESSED_ALPHA
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> view.alpha = 1f
            }
            false
        }

        binding.titleEditText.doAfterTextChanged {
            scheduleAutoSave()
        }
        binding.contentEditText.doAfterTextChanged {
            scrollToContentCursor()
            scheduleAutoSave()
        }
        binding.contentEditText.setOnClickListener {
            scrollToContentCursor()
        }
        binding.contentEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                scrollToContentCursor()
            }
        }

        installAlphaPressFeedback(binding.addChecklistItemButton)
        binding.addChecklistItemButton.setOnClickListener {
            addChecklistItemFromInput()
        }
        binding.checklistItemEditText.setOnEditorActionListener { _, actionId, event ->
            val isKeyboardDone = actionId == EditorInfo.IME_ACTION_DONE
            val isEnterUp = event?.let {
                it.keyCode == KeyEvent.KEYCODE_ENTER && it.action == KeyEvent.ACTION_UP
            } == true
            if (!isKeyboardDone && !isEnterUp) {
                return@setOnEditorActionListener false
            }

            addChecklistItemFromInput()
            true
        }
    }

    // Переключение интерфейса между обычной заметкой и чек-листом.
    private fun updateEditorMode() {
        val isChecklist = selectedNoteType == NoteType.CHECKLIST
        binding.contentEditText.visibility = if (isChecklist) View.GONE else View.VISIBLE
        binding.checklistEditorGroup.visibility = if (isChecklist) View.VISIBLE else View.GONE
        binding.priorityButton.visibility = if (isChecklist) View.GONE else View.VISIBLE
        binding.categoryTitleTextView.visibility = if (isChecklist) View.GONE else View.VISIBLE
        binding.categoryScrollView.visibility = if (isChecklist) View.GONE else View.VISIBLE
        binding.deadlineHeader.visibility = if (isChecklist) View.GONE else View.VISIBLE
        binding.deadlinePickerCardView.visibility = View.GONE
        if (isChecklist) {
            selectedCategories.clear()
            selectedCategories.add(NoteCategories.DEFAULT)
            selectedPriority = NotePriority.NONE
            selectedDeadlineAt = null
            isDeadlinePickerExpanded = false
        }
        if (isChecklist) {
            updateChecklistInputHint()
            renderChecklistItems()
        }
    }

    private fun addChecklistItemFromInput() {
        val text = binding.checklistItemEditText.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) return

        checklistItems.add(text)
        binding.checklistItemEditText.text?.clear()
        updateChecklistInputHint()
        renderChecklistItems()
        scheduleAutoSave()
    }

    private fun updateChecklistInputHint() {
        binding.checklistItemEditText.hint = getString(
            R.string.checklist_item_number_hint,
            checklistItems.size + 1,
        )
    }

    // Перерисовка пунктов чек-листа с разделением на выполненные и невыполненные.
    private fun renderChecklistItems() {
        binding.checklistItemsContainer.removeAllViews()

        renderChecklistSection(
            title = getString(R.string.checklist_incomplete_title),
            indexedItems = checklistItems.incompleteItems(),
        )
        renderChecklistSection(
            title = getString(R.string.checklist_completed_title),
            indexedItems = checklistItems.completedItems(),
        )
    }

    private fun renderChecklistSection(
        title: String,
        indexedItems: List<IndexedValue<ChecklistItem>>,
    ) {
        if (indexedItems.isEmpty()) return

        binding.checklistItemsContainer.addView(
            TextView(this).apply {
                text = title
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(0, dp(14), 0, dp(4))
            },
        )

        indexedItems.forEach { (index, item) ->
            binding.checklistItemsContainer.addView(createChecklistRow(index, item))
        }
    }

    private fun createChecklistRow(index: Int, item: ChecklistItem): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }

        val toggleButton = AppCompatImageButton(this).apply {
            setImageResource(if (item.isDone) R.drawable.checkbox else R.drawable.emptybox)
            background = ColorDrawable(Color.TRANSPARENT)
            contentDescription = item.text
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setColorFilter(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            installAlphaPressFeedback(this)
            setOnClickListener {
                checklistItems.toggleDone(index)
                renderChecklistItems()
                scheduleAutoSave()
            }
        }
        row.addView(toggleButton, LinearLayout.LayoutParams(dp(44), dp(44)))

        val titleTextView = TextView(this).apply {
            text = item.text
            textSize = 17f
            setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnBackground))
            paintFlags = if (item.isDone) {
                paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
        }
        row.addView(
            titleTextView,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )

        val deleteButton = AppCompatImageButton(this).apply {
            setImageResource(R.drawable.close)
            background = ColorDrawable(Color.TRANSPARENT)
            contentDescription = getString(R.string.delete_action)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setColorFilter(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            installAlphaPressFeedback(this)
            setOnClickListener {
                checklistItems.removeAt(index)
                updateChecklistInputHint()
                renderChecklistItems()
                scheduleAutoSave()
            }
        }
        row.addView(deleteButton, LinearLayout.LayoutParams(dp(44), dp(44)))

        return row
    }

    // Подготовка выбора нескольких категорий для обычной заметки.
    private fun setupCategoryPicker() {
        renderCategoryButtons()

        observeHiddenCategories()
        observeCustomCategories()
        loadCustomCategoryOptions()
        updateCategoryUi()
    }

    // Настройка раскрывающегося выбора дедлайна через три NumberPicker.
    private fun setupDeadlinePicker() {
        listOf(
            binding.deadlineDayPicker,
            binding.deadlineMonthPicker,
            binding.deadlineYearPicker,
        ).forEach(::styleDeadlineNumberPicker)

        configureDeadlinePickers(selectedDeadlineAt ?: todayStartMillis())
        updateDeadlineUi()

        val deadlineValueChangeListener = NumberPicker.OnValueChangeListener { _, _, _ ->
            updateDeadlineFromPickers()
        }
        binding.deadlineDayPicker.setOnValueChangedListener(deadlineValueChangeListener)
        binding.deadlineMonthPicker.setOnValueChangedListener(deadlineValueChangeListener)
        binding.deadlineYearPicker.setOnValueChangedListener(deadlineValueChangeListener)

        installAlphaPressFeedback(binding.deadlineHeader)
        binding.deadlineHeader.setOnClickListener {
            setDeadlinePickerExpanded(!isDeadlinePickerExpanded)
        }

        installAlphaPressFeedback(binding.clearDeadlineTextView)
        binding.clearDeadlineTextView.setOnClickListener {
            selectedDeadlineAt = null
            updateDeadlineUi()
            scheduleAutoSave()
        }
    }

    // Анимация открытия блока дедлайна и поворота стрелки.
    private fun setDeadlinePickerExpanded(expanded: Boolean) {
        if (isDeadlinePickerExpanded == expanded) return

        isDeadlinePickerExpanded = expanded
        binding.deadlineArrowImageView.animate()
            .rotation(if (expanded) 90f else 0f)
            .setDuration(DEADLINE_ARROW_ANIMATION_MS)
            .start()

        if (expanded) {
            configureDeadlinePickers(selectedDeadlineAt ?: todayStartMillis())
            binding.deadlinePickerCardView.apply {
                visibility = View.VISIBLE
                alpha = 0f
                translationY = -dp(6).toFloat()
                animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(DEADLINE_PICKER_ANIMATION_MS)
                    .start()
            }
        } else {
            binding.deadlinePickerCardView.animate()
                .alpha(0f)
                .translationY(-dp(6).toFloat())
                .setDuration(DEADLINE_PICKER_ANIMATION_MS)
                .withEndAction {
                    binding.deadlinePickerCardView.visibility = View.GONE
                    binding.deadlinePickerCardView.alpha = 1f
                    binding.deadlinePickerCardView.translationY = 0f
                }
                .start()
        }
    }

    // Преобразование выбранного дня, месяца и года в timestamp дедлайна.
    private fun updateDeadlineFromPickers() {
        if (isUpdatingDeadlinePickers) return

        val deadline = coerceDeadlineMillis(
            startOfDayMillis(
                year = binding.deadlineYearPicker.value,
                month = binding.deadlineMonthPicker.value,
                day = binding.deadlineDayPicker.value,
            ),
        )
        selectedDeadlineAt = deadline
        configureDeadlinePickers(deadline)
        updateDeadlineUi()
        scheduleAutoSave()
    }

    private fun updateDeadlineUi() {
        binding.deadlineValueTextView.text = selectedDeadlineAt?.let { deadline ->
            deadlineDateFormatter.format(Date(deadline))
        } ?: getString(R.string.deadline_not_selected)
        binding.deadlineArrowImageView.rotation = if (isDeadlinePickerExpanded) 90f else 0f
    }

    private fun configureDeadlinePickers(deadlineMillis: Long) {
        val coercedDeadline = coerceDeadlineMillis(deadlineMillis)
        val (year, month, day) = dateParts(coercedDeadline)
        val (currentYear, currentMonth, currentDay) = dateParts(todayStartMillis())

        isUpdatingDeadlinePickers = true
        try {
            binding.deadlineYearPicker.minValue = currentYear
            binding.deadlineYearPicker.maxValue = MAX_DEADLINE_YEAR
            binding.deadlineYearPicker.value = year.coerceIn(currentYear, MAX_DEADLINE_YEAR)

            val monthMin = if (year == currentYear) currentMonth else 1
            val monthMax = 12
            val safeMonth = month.coerceIn(monthMin, monthMax)
            binding.deadlineMonthPicker.displayedValues = null
            binding.deadlineMonthPicker.minValue = monthMin
            binding.deadlineMonthPicker.maxValue = monthMax
            binding.deadlineMonthPicker.displayedValues = (monthMin..monthMax)
                .map { monthLabels[it - 1] }
                .toTypedArray()
            binding.deadlineMonthPicker.value = safeMonth

            val dayMin = if (year == currentYear && safeMonth == currentMonth) currentDay else 1
            val dayMax = daysInMonth(year, safeMonth)
            val safeDay = day.coerceIn(dayMin, dayMax)
            binding.deadlineDayPicker.displayedValues = null
            binding.deadlineDayPicker.minValue = dayMin
            binding.deadlineDayPicker.maxValue = dayMax
            binding.deadlineDayPicker.displayedValues = (dayMin..dayMax)
                .map { it.toString().padStart(2, '0') }
                .toTypedArray()
            binding.deadlineDayPicker.value = safeDay
        } finally {
            isUpdatingDeadlinePickers = false
        }
    }

    private fun styleDeadlineNumberPicker(picker: NumberPicker) {
        picker.wrapSelectorWheel = false
        picker.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        picker.setOnLongPressUpdateInterval(120L)
        tintNumberPickerText(picker)
    }

    private fun tintNumberPickerText(view: View) {
        if (view is EditText) {
            view.setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurface))
            view.textSize = 18f
            view.typeface = Typeface.DEFAULT_BOLD
            view.gravity = Gravity.CENTER
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                tintNumberPickerText(view.getChildAt(index))
            }
        }
    }

    private fun todayStartMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun startOfDayMillis(year: Int, month: Int, day: Int): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun maxDeadlineMillis(): Long {
        return startOfDayMillis(MAX_DEADLINE_YEAR, 12, 31)
    }

    private fun coerceDeadlineMillis(deadlineMillis: Long): Long {
        return deadlineMillis.coerceIn(todayStartMillis(), maxDeadlineMillis())
    }

    private fun dateParts(deadlineMillis: Long): Triple<Int, Int, Int> {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = deadlineMillis
        }
        return Triple(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
        )
    }

    private fun daysInMonth(year: Int, month: Int): Int {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    // Всплывающее меню выбора приоритета заметки.
    private fun showPriorityMenu() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(resolveThemeColor(com.google.android.material.R.attr.colorSurface))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), ContextCompat.getColor(this@EditNoteActivity, R.color.note_stroke_color))
            }
            clipToOutline = true
        }

        var popupWindow: PopupWindow? = null
        listOf(
            NotePriority.NONE,
            NotePriority.HIGH,
            NotePriority.MEDIUM,
            NotePriority.LOW,
        ).forEach { priority ->
            container.addView(
                createPriorityRow(priority) {
                    selectedPriority = priority
                    updatePriorityUi()
                    scheduleAutoSave()
                    popupWindow?.dismiss()
                },
            )
        }

        popupWindow = PopupWindow(
            container,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
        }
        popupWindow.showAsDropDown(binding.priorityButton, 0, dp(2))
    }

    private fun createPriorityRow(
        priority: NotePriority,
        onClick: () -> Unit,
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            minimumWidth = dp(166)
            background = ColorDrawable(Color.TRANSPARENT)
            setPadding(dp(14), dp(10), dp(16), dp(10))
            installAlphaPressFeedback(this)
            setOnClickListener { onClick() }

            addView(
                ImageView(this@EditNoteActivity).apply {
                    setImageResource(R.drawable.circle)
                    NotePriorityUi.applyTo(this, priority)
                },
                LinearLayout.LayoutParams(dp(12), dp(12)),
            )
            addView(
                TextView(this@EditNoteActivity).apply {
                    text = getString(NotePriorityUi.labelRes(priority))
                    textSize = 14f
                    setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurface))
                    typeface = if (priority == selectedPriority) {
                        Typeface.DEFAULT_BOLD
                    } else {
                        Typeface.DEFAULT
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginStart = dp(10)
                },
            )
        }
    }

    private fun updatePriorityUi() {
        if (selectedPriority == NotePriority.NONE) {
            binding.priorityButton.setImageResource(R.drawable.addpriority)
            binding.priorityButton.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.selection_stroke_color),
            )
            binding.priorityButton.contentDescription = getString(
                R.string.priority_content_description,
                getString(NotePriorityUi.labelRes(NotePriority.NONE)),
            )
        } else {
            binding.priorityButton.setImageResource(R.drawable.circle)
            NotePriorityUi.applyTo(binding.priorityButton, selectedPriority)
        }
    }

    // Диалог добавления новой категории из редактора.
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

    private fun observeHiddenCategories() {
        lifecycleScope.launch {
            categoryPreferences.hiddenCategoriesFlow.collect { categories ->
                hiddenCategories.clear()
                hiddenCategories.addAll(categories)
                renderCategoryButtons()
            }
        }
    }

    private fun observeCustomCategories() {
        lifecycleScope.launch {
            categoryPreferences.customCategoriesFlow.collect { categories ->
                customCategories.addAll(categories)
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
                    .filterNot { NoteCategories.isStandard(it) || it == NoteCategories.ALL },
            )
            renderCategoryButtons()
        }
    }

    private fun renderCategoryButtons() {
        categoryButtons.clear()
        binding.categoryButtonsContainer.removeAllViews()
        addCategoryAddButton()

        val categories = (NoteCategories.STANDARD + customCategories + selectedCategories)
            .map { NoteCategories.normalize(it) }
            .filterNot { hiddenCategories.contains(it) && !selectedCategories.contains(it) }
            .distinct()

        categories.forEach { category ->
            addCategoryButton(category)
        }
        updateCategoryButtons()
    }

    private fun addCategoryAddButton() {
        val button = AppCompatImageButton(this).apply {
            setImageResource(R.drawable.addplusfilter)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = getString(R.string.custom_category_hint)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(7), dp(7), dp(7), dp(7))
            setColorFilter(ContextCompat.getColor(this@EditNoteActivity, R.color.note_stroke_color))
            installAlphaPressFeedback(this)
            setOnClickListener {
                showAddCategoryDialog()
            }
        }
        val params = LinearLayout.LayoutParams(
            dp(32),
            dp(32),
        ).apply {
            marginEnd = dp(8)
        }
        binding.categoryButtonsContainer.addView(button, params)
    }

    private fun addCategoryButton(category: String) {
        val button = MaterialButton(this).apply {
            text = NoteCategoryUi.displayName(this@EditNoteActivity, category)
            setAllCaps(false)
            minWidth = 0
            minHeight = 0
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(15)
            textSize = 13f
            rippleColor = ColorStateList.valueOf(Color.TRANSPARENT)
            setPadding(dp(14), 0, dp(14), 0)
            setOnClickListener {
                toggleCategory(category)
                updateCategoryUi()
                scheduleAutoSave()
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

    private fun addCustomCategory(rawCategory: String) {
        if (rawCategory.isBlank()) return

        val category = NoteCategories.normalize(rawCategory)
        if (category == NoteCategories.ALL) return

        if (!NoteCategories.isStandard(category)) {
            customCategories.add(category)
        }
        restoreHiddenCategory(category)
        selectCategory(category)
        lifecycleScope.launch {
            categoryPreferences.setCustomCategories(customCategories)
        }
        renderCategoryButtons()
        updateCategoryUi()
        scheduleAutoSave()
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
        if (normalizedCategory == NoteCategories.ALL) return

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
        if (normalizedCategory == NoteCategories.ALL) return

        selectedCategories.remove(NoteCategories.DEFAULT)
        selectedCategories.add(normalizedCategory)
    }

    // Загрузка существующей заметки по ID и заполнение полей редактора.
    private fun observeEditorState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderEditorState(state)
                    }
                }
                launch {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            EditNoteEffect.Finish -> finish()
                        }
                    }
                }
            }
        }
    }

    private fun renderEditorState(state: EditNoteUiState) {
        noteId = state.noteId ?: NO_NOTE_ID
        val note = state.loadedNote ?: return
        if (renderedLoadedNoteId == note.id) return

        renderedLoadedNoteId = note.id
        isApplyingLoadedNote = true
        try {
            binding.screenTitleTextView.text = getString(R.string.edit_note_title)
            selectedNoteType = NoteType.fromStorage(note.type)
            updateEditorMode()
            binding.titleEditText.setText(note.title)
            if (selectedNoteType == NoteType.CHECKLIST) {
                checklistItems.replaceWithContent(note.content)
                updateChecklistInputHint()
                renderChecklistItems()
            } else {
                binding.contentEditText.setText(note.content)
            }
            if (selectedNoteType == NoteType.CHECKLIST) {
                selectedPriority = NotePriority.NONE
                selectedDeadlineAt = null
                selectedCategories.clear()
                selectedCategories.add(NoteCategories.DEFAULT)
            } else {
                selectedPriority = NotePriority.fromStorage(note.priority)
                updatePriorityUi()
                selectedDeadlineAt = note.deadlineAt
                configureDeadlinePickers(selectedDeadlineAt ?: todayStartMillis())
                updateDeadlineUi()
                selectedCategories.clear()
                selectedCategories.addAll(NoteCategories.parse(note.category))
                updateCategoryUi()
            }
        } finally {
            isApplyingLoadedNote = false
        }
    }

    // Отложенный запуск автосохранения после изменения текста или параметров.
    private fun scheduleAutoSave() {
        if (isApplyingLoadedNote) return

        viewModel.scheduleAutoSave(currentDraft())
    }

    // Защита от параллельных сохранений и повторная запись при новых изменениях.
    private fun requestAutoSaveNow() {
        if (isApplyingLoadedNote) return

        viewModel.requestAutoSaveNow(currentDraft())
    }

    private fun flushAutoSaveThen(onComplete: () -> Unit = {}) {
        lifecycleScope.launch {
            viewModel.flushAutoSave(currentDraft())
            onComplete()
        }
    }

    // Запись текущего черновика в Room через Repository.
    // Сбор текущих значений экрана в объект для сравнения и сохранения.
    private fun currentDraft(): NoteDraft {
        return NoteDraft(
            title = binding.titleEditText.text?.toString()?.trim().orEmpty(),
            content = if (selectedNoteType == NoteType.CHECKLIST) {
                checklistItems.serialize()
            } else {
                binding.contentEditText.text?.toString().orEmpty()
            },
            category = if (selectedNoteType == NoteType.CHECKLIST) {
                NoteCategories.DEFAULT
            } else {
                NoteCategories.serialize(selectedCategories)
            },
            priority = if (selectedNoteType == NoteType.CHECKLIST) {
                NotePriority.NONE.storageValue
            } else {
                selectedPriority.storageValue
            },
            deadlineAt = if (selectedNoteType == NoteType.CHECKLIST) null else selectedDeadlineAt,
            type = selectedNoteType.storageValue,
        )
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
        flushAutoSaveThen()
        clearEditorFocus()
        super.onPause()
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasEditorFocus()) {
                    clearEditorFocus()
                } else {
                    finishAfterAutoSave()
                }
            }
        })
    }

    private fun finishAfterAutoSave() {
        flushAutoSaveThen {
            finish()
        }
    }

    private fun hasEditorFocus(): Boolean {
        return binding.titleEditText.hasFocus() ||
            binding.contentEditText.hasFocus() ||
            binding.checklistItemEditText.hasFocus()
    }

    private fun clearEditorFocus() {
        val focusedView = currentFocus ?: binding.contentEditText
        binding.titleEditText.clearFocus()
        binding.contentEditText.clearFocus()
        binding.checklistItemEditText.clearFocus()
        binding.main.requestFocus()
        val inputMethodManager = getSystemService<InputMethodManager>()
        inputMethodManager?.hideSoftInputFromWindow(focusedView.windowToken, 0)
    }

    // Прокрутка редактора к курсору при открытой клавиатуре.
    private fun scrollToContentCursor() {
        binding.contentEditText.post {
            val layout = binding.contentEditText.layout ?: return@post
            val cursorPosition = binding.contentEditText.selectionStart.coerceAtLeast(0)
            val cursorLine = layout.getLineForOffset(cursorPosition)
            val cursorTop = binding.contentEditText.top + layout.getLineTop(cursorLine)
            val cursorBottom = binding.contentEditText.top + layout.getLineBottom(cursorLine) + dp(72)
            val visibleTop = binding.contentScrollView.scrollY
            val visibleBottom = visibleTop + binding.contentScrollView.height - binding.contentScrollView.paddingBottom

            when {
                cursorBottom > visibleBottom -> {
                    binding.contentScrollView.smoothScrollTo(
                        0,
                        cursorBottom - binding.contentScrollView.height + binding.contentScrollView.paddingBottom,
                    )
                }
                cursorTop < visibleTop -> {
                    binding.contentScrollView.smoothScrollTo(0, cursorTop)
                }
            }
        }
    }

    // Синхронизация выбранных категорий с кнопками в интерфейсе.
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

    private fun resolveThemeColor(attr: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(this, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }

    private fun currentLocale(): Locale {
        val locales = resources.configuration.locales
        return if (locales.size() > 0) locales[0] else Locale.getDefault()
    }

    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
        const val EXTRA_NOTE_TYPE = "extra_note_type"
        private const val NO_NOTE_ID = -1L
        private const val PRIORITY_BUTTON_PRESSED_ALPHA = 0.68f
        private const val BUTTON_PRESSED_ALPHA = 0.68f
        private const val DEADLINE_ARROW_ANIMATION_MS = 180L
        private const val DEADLINE_PICKER_ANIMATION_MS = 160L
        private const val MAX_DEADLINE_YEAR = 2067
        private const val DEADLINE_DATE_PATTERN = "d MMM yyyy"
    }

    // Черновик используется для сравнения текущего состояния с последним сохранением.
}
