package com.example.notavia.editor

import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.notavia.NotaviaActivity
import com.example.notavia.R
import com.example.notavia.data.NoteCategories
import com.example.notavia.data.NotesRepository
import com.example.notavia.databinding.ActivityEditNoteBinding
import com.example.notavia.settings.CategorySettings
import com.example.notavia.ui.CategoryInputDialog
import com.example.notavia.ui.NoteCategoryUi
import com.example.notavia.ui.dp
import com.example.notavia.ui.installAlphaPressFeedback
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class EditorCategoryController(
    private val activity: NotaviaActivity,
    private val binding: ActivityEditNoteBinding,
    private val categorySettings: CategorySettings,
    private val notesRepository: NotesRepository,
    private val onSelectionChanged: () -> Unit,
) {
    private val categoryState = EditorCategoryState()
    private val categoryButtons = mutableMapOf<String, MaterialButton>()

    fun setup(scope: CoroutineScope) {
        renderCategoryButtons()
        observeHiddenCategories(scope)
        observeCustomCategories(scope)
        loadCustomCategoryOptions(scope)
        updateCategoryUi()
    }

    fun resetSelectionToDefault() {
        categoryState.resetSelectionToDefault()
        updateCategoryUi()
    }

    fun selectStoredCategories(storedCategory: String) {
        categoryState.selectStoredCategories(storedCategory)
        updateCategoryUi()
    }

    fun serializeSelected(): String {
        return categoryState.serializeSelected()
    }

    private fun observeHiddenCategories(scope: CoroutineScope) {
        scope.launch {
            categorySettings.hiddenCategoriesFlow.collect { categories ->
                categoryState.setHiddenCategories(categories)
                renderCategoryButtons()
            }
        }
    }

    private fun observeCustomCategories(scope: CoroutineScope) {
        scope.launch {
            categorySettings.customCategoriesFlow.collect { categories ->
                categoryState.mergeCustomCategories(categories)
                renderCategoryButtons()
            }
        }
    }

    private fun loadCustomCategoryOptions(scope: CoroutineScope) {
        scope.launch {
            categoryState.mergeCustomCategories(
                notesRepository.getAllNotesSnapshot()
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

        categoryState.visibleCategories().forEach { category ->
            addCategoryButton(category)
        }
        updateCategoryButtons()
    }

    private fun addCategoryAddButton() {
        val button = AppCompatImageButton(activity).apply {
            setImageResource(R.drawable.addplusfilter)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = activity.getString(R.string.custom_category_hint)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(activity.dp(7), activity.dp(7), activity.dp(7), activity.dp(7))
            setColorFilter(ContextCompat.getColor(activity, R.color.note_stroke_color))
            installAlphaPressFeedback(this)
            setOnClickListener {
                showAddCategoryDialog()
            }
        }
        val params = LinearLayout.LayoutParams(
            activity.dp(32),
            activity.dp(32),
        ).apply {
            marginEnd = activity.dp(8)
        }
        binding.categoryButtonsContainer.addView(button, params)
    }

    private fun addCategoryButton(category: String) {
        val button = MaterialButton(activity).apply {
            text = NoteCategoryUi.displayName(activity, category)
            setAllCaps(false)
            minWidth = 0
            minHeight = 0
            insetTop = 0
            insetBottom = 0
            cornerRadius = activity.dp(15)
            textSize = 13f
            rippleColor = ColorStateList.valueOf(Color.TRANSPARENT)
            setPadding(activity.dp(14), 0, activity.dp(14), 0)
            setOnClickListener {
                categoryState.toggle(category)
                updateCategoryUi()
                onSelectionChanged()
            }
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            activity.dp(36),
        ).apply {
            marginEnd = activity.dp(8)
        }
        binding.categoryButtonsContainer.addView(button, params)
        categoryButtons[category] = button
    }

    private fun showAddCategoryDialog() {
        CategoryInputDialog(activity) { rawCategory ->
            addCustomCategory(rawCategory)
        }.show()
    }

    private fun addCustomCategory(rawCategory: String) {
        if (rawCategory.isBlank()) return

        val category = NoteCategories.normalize(rawCategory)
        if (category == NoteCategories.ALL) return

        categoryState.addCustomCategory(category)
        restoreHiddenCategory(category)
        categoryState.select(category)
        activity.lifecycleScope.launch {
            categorySettings.setCustomCategories(categoryState.custom)
        }
        renderCategoryButtons()
        updateCategoryUi()
        onSelectionChanged()
    }

    private fun restoreHiddenCategory(category: String) {
        if (!categoryState.restoreHiddenCategory(category)) return

        activity.lifecycleScope.launch {
            categorySettings.setHiddenCategories(categoryState.hidden)
        }
    }

    private fun updateCategoryUi() {
        val addedCustomCategories = categoryState.includeSelectedCustomCategories()
        val missingSelectedButtons = categoryState.customSelectedCategories()
            .any { category -> !categoryButtons.containsKey(category) }
        if (addedCustomCategories || missingSelectedButtons) {
            renderCategoryButtons()
            return
        }

        updateCategoryButtons()
    }

    private fun updateCategoryButtons() {
        categoryButtons.forEach { (category, button) ->
            styleCategoryButton(
                button,
                isActive = categoryState.isSelected(category),
            )
        }
    }

    private fun styleCategoryButton(button: MaterialButton, isActive: Boolean) {
        button.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                activity,
                if (isActive) R.color.selection_stroke_color else android.R.color.transparent,
            ),
        )
        button.setTextColor(
            ContextCompat.getColor(
                activity,
                if (isActive) R.color.light_on_primary else R.color.note_stroke_color,
            ),
        )
        button.strokeWidth = if (isActive) 0 else activity.dp(1)
        button.strokeColor = ColorStateList.valueOf(
            ContextCompat.getColor(activity, R.color.note_stroke_color),
        )
    }
}
