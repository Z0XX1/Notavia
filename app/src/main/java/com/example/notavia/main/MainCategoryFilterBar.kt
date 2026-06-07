package com.example.notavia.main

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import com.example.notavia.NotaviaActivity
import com.example.notavia.R
import com.example.notavia.ui.NoteCategoryUi
import com.example.notavia.ui.dp
import com.example.notavia.ui.installAlphaPressFeedback
import com.google.android.material.button.MaterialButton

class MainCategoryFilterBar(
    private val activity: NotaviaActivity,
    private val container: LinearLayout,
    private val viewModel: MainViewModel,
    private val stateProvider: () -> MainUiState,
    private val onAddCategory: () -> Unit,
    private val onClearSearchFocus: () -> Unit,
) {
    private val categoryFilterButtons = linkedMapOf<String?, MaterialButton>()

    private val uiState: MainUiState
        get() = stateProvider()

    fun render(state: MainUiState) {
        categoryFilterButtons.clear()
        container.removeAllViews()

        if (
            state.currentSection != MainSection.NOTES ||
            state.isNoteSelectionMode
        ) {
            return
        }

        addCategoryAddButton()
        addCategoryFilterButton(null, activity.getString(R.string.all_categories))
        viewModel.availableCategoryFilters().forEach { category ->
            addCategoryFilterButton(category, NoteCategoryUi.displayName(activity, category))
        }
        updateButtons(state)
    }

    private fun addCategoryAddButton() {
        val button = AppCompatImageButton(activity).apply {
            setImageResource(R.drawable.addplusfilter)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = activity.getString(R.string.custom_category_hint)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8))
            setColorFilter(ContextCompat.getColor(activity, R.color.note_stroke_color))
            installAlphaPressFeedback(this)
            setOnClickListener {
                onClearSearchFocus()
                onAddCategory()
            }
        }
        val params = LinearLayout.LayoutParams(
            activity.dp(32),
            activity.dp(32),
        ).apply {
            marginEnd = activity.dp(8)
        }
        container.addView(button, params)
    }

    private fun addCategoryFilterButton(category: String?, title: String) {
        val button = MaterialButton(activity).apply {
            text = title
            setAllCaps(false)
            minWidth = 0
            minHeight = 0
            insetTop = 0
            insetBottom = 0
            cornerRadius = activity.dp(13)
            textSize = 12f
            rippleColor = ColorStateList.valueOf(Color.TRANSPARENT)
            setPadding(activity.dp(12), 0, activity.dp(12), 0)
            setOnClickListener {
                if (uiState.isCategorySelectionMode) {
                    if (!viewModel.isProtectedCategory(category)) {
                        category?.let { viewModel.toggleCategorySelection(it) }
                    }
                } else {
                    viewModel.selectSingleCategoryFilter(category)
                    onClearSearchFocus()
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
            activity.dp(32),
        ).apply {
            marginEnd = activity.dp(8)
        }
        container.addView(button, params)
        categoryFilterButtons[category] = button
    }

    private fun updateButtons(state: MainUiState) {
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
            styleButton(
                button,
                isActive = isActive,
                isPinned = category != null &&
                    !viewModel.isProtectedCategory(category) &&
                    state.pinnedCategoryNames.contains(category),
            )
        }
    }

    private fun styleButton(
        button: MaterialButton,
        isActive: Boolean,
        isPinned: Boolean,
    ) {
        button.icon = if (isPinned) {
            ContextCompat.getDrawable(activity, R.drawable.pushpin)
        } else {
            null
        }
        button.iconSize = activity.dp(12)
        button.iconPadding = activity.dp(4)
        button.iconTint = ColorStateList.valueOf(
            ContextCompat.getColor(
                activity,
                if (isActive) R.color.light_on_primary else R.color.note_stroke_color,
            ),
        )
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
