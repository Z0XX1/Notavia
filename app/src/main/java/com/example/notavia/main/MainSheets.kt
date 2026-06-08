package com.example.notavia.main

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.notavia.NotaviaActivity
import com.example.notavia.R
import com.example.notavia.data.NotePriority
import com.example.notavia.ui.NoteCategoryUi
import com.example.notavia.ui.NotePriorityUi
import com.example.notavia.ui.dp
import com.example.notavia.ui.installAlphaPressFeedback
import com.example.notavia.ui.resolveThemeColor
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

class MainSheets(
    private val activity: NotaviaActivity,
    private val viewModel: MainViewModel,
    private val stateProvider: () -> MainUiState,
) {
    private val uiState: MainUiState
        get() = stateProvider()

    fun showFilterSheet() {
        val dialog = BottomSheetDialog(activity)
        val content = createBottomSheetContainer()
        val filterRowRefreshers = mutableListOf<() -> Unit>()
        val onFilterChanged = {
            filterRowRefreshers.forEach { refreshRow ->
                refreshRow()
            }
        }

        content.addView(createBottomSheetTitle(activity.getString(R.string.filters_title)))
        content.addView(createBottomSheetSectionTitle(activity.getString(R.string.filter_priority_title)))
        content.addView(
            createSheetOptionRow(
                title = activity.getString(R.string.filter_any),
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

        content.addView(createBottomSheetSectionTitle(activity.getString(R.string.filter_deadline_title)))
        content.addView(
            createSheetOptionRow(
                title = activity.getString(R.string.filter_any),
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

        content.addView(createBottomSheetSectionTitle(activity.getString(R.string.filter_category_title)))
        content.addView(
            createSheetOptionRow(
                title = activity.getString(R.string.filter_any),
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
                    title = NoteCategoryUi.displayName(activity, category),
                    isSelected = { uiState.selectedCategoryFilters.contains(category) },
                    registerSelectionUpdater = filterRowRefreshers::add,
                ) {
                    viewModel.toggleCategoryFilter(category)
                    onFilterChanged()
                },
            )
        }

        content.addView(
            createResetButton {
                viewModel.clearAllFilters()
                dialog.dismiss()
            },
        )

        val filterSheetHeight = activity.resources.displayMetrics.heightPixels / 2
        dialog.setContentView(wrapBottomSheetContent(content, fixedHeight = filterSheetHeight))
        styleBottomSheet(dialog, fixedHeight = filterSheetHeight)
        dialog.show()
    }

    fun showSortSheet() {
        val dialog = BottomSheetDialog(activity)
        val content = createBottomSheetContainer()
        val sortRowRefreshers = mutableListOf<() -> Unit>()
        val onSortChanged = {
            sortRowRefreshers.forEach { refreshRow ->
                refreshRow()
            }
        }

        content.addView(createBottomSheetTitle(activity.getString(R.string.sort_title)))
        content.addView(createBottomSheetSectionTitle(activity.getString(R.string.sort_by_created)))
        content.addView(createSortChoiceRow(SortGroup.CREATED, sortRowRefreshers::add, onSortChanged))

        content.addView(createBottomSheetSectionTitle(activity.getString(R.string.sort_by_priority)))
        content.addView(createSortChoiceRow(SortGroup.PRIORITY, sortRowRefreshers::add, onSortChanged))

        content.addView(createBottomSheetSectionTitle(activity.getString(R.string.sort_by_deadline)))
        content.addView(createSortChoiceRow(SortGroup.DEADLINE, sortRowRefreshers::add, onSortChanged))

        content.addView(
            createResetButton {
                viewModel.clearSortOptions()
                dialog.dismiss()
            },
        )

        val sortSheetHeight = activity.resources.displayMetrics.heightPixels / 2
        dialog.setContentView(wrapBottomSheetContent(content, fixedHeight = sortSheetHeight))
        styleBottomSheet(dialog, fixedHeight = sortSheetHeight)
        dialog.show()
    }

    private fun createSortChoiceRow(
        group: SortGroup,
        registerSelectionUpdater: ((() -> Unit) -> Unit)? = null,
        onSelectionChanged: () -> Unit,
    ): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            minimumHeight = activity.dp(58)
            setPadding(0, 0, activity.dp(8), 0)
            installAlphaPressFeedback(this)

            addView(
                TextView(activity).apply {
                    text = activity.getString(R.string.sort_first_label)
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(activity.resolveThemeColor(com.google.android.material.R.attr.colorOnSurface))
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )

            val valueTextView = TextView(activity).apply {
                text = uiState.selectedSortOptions[group]?.title() ?: activity.getString(R.string.sort_none)
                textSize = 16f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(activity.resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            }
            addView(
                valueTextView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )

            addView(
                ImageView(activity).apply {
                    setImageResource(R.drawable.unfold)
                    imageTintList = ColorStateList.valueOf(
                        activity.resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant),
                    )
                    contentDescription = null
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8))
                },
                LinearLayout.LayoutParams(activity.dp(36), activity.dp(36)),
            )

            val refreshValue = {
                valueTextView.text = uiState.selectedSortOptions[group]?.title()
                    ?: activity.getString(R.string.sort_none)
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
        val popupWidth = activity.dp(252)
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(activity.resolveThemeColor(com.google.android.material.R.attr.colorSurfaceContainerLow))
                cornerRadius = activity.dp(18).toFloat()
                setStroke(activity.dp(1), activity.resolveThemeColor(com.google.android.material.R.attr.colorOutline))
            }
            clipToOutline = true
            setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8))
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
            elevation = activity.dp(8).toFloat()
        }

        val xOffset = (anchor.width - popupWidth).coerceAtLeast(0)
        container.measure(
            View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val anchorLocation = IntArray(2)
        anchor.getLocationOnScreen(anchorLocation)
        val availableBelow = activity.resources.displayMetrics.heightPixels -
            anchorLocation[1] -
            anchor.height
        val yOffset = if (availableBelow < container.measuredHeight + activity.dp(8)) {
            -anchor.height - container.measuredHeight - activity.dp(4)
        } else {
            -activity.dp(4)
        }
        popupWindow.showAsDropDown(anchor, xOffset, yOffset)
    }

    private fun createSortPopupRow(
        title: String,
        isSelected: Boolean,
        onClick: () -> Unit,
    ): View {
        val selectedBackgroundColor = activity.resolveThemeColor(
            com.google.android.material.R.attr.colorSurfaceVariant,
        )
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = if (isSelected) {
                GradientDrawable().apply {
                    setColor(selectedBackgroundColor)
                    cornerRadius = activity.dp(12).toFloat()
                }
            } else {
                ColorDrawable(Color.TRANSPARENT)
            }
            setPadding(activity.dp(22), 0, activity.dp(20), 0)
            installAlphaPressFeedback(this)
            setOnClickListener {
                onClick()
            }

            addView(
                TextView(activity).apply {
                    text = title
                    textSize = 16f
                    setTextColor(
                        if (isSelected) {
                            ContextCompat.getColor(activity, R.color.selection_stroke_color)
                        } else {
                            activity.resolveThemeColor(com.google.android.material.R.attr.colorOnSurface)
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
                ImageView(activity).apply {
                    setImageResource(R.drawable.check)
                    imageTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(activity, R.color.selection_stroke_color),
                    )
                    visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
                    contentDescription = null
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(activity.dp(28), LinearLayout.LayoutParams.MATCH_PARENT),
            )
        }.also { row ->
            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                activity.dp(56),
            )
        }
    }

    private fun sortChoicesForGroup(group: SortGroup): List<SortChoice> {
        return when (group) {
            SortGroup.CREATED -> listOf(
                SortChoice(null, activity.getString(R.string.sort_none)),
                SortChoice(NoteSortOption.CREATED_NEWEST, activity.getString(R.string.sort_created_new)),
                SortChoice(NoteSortOption.CREATED_OLDEST, activity.getString(R.string.sort_created_old)),
            )
            SortGroup.PRIORITY -> listOf(
                SortChoice(null, activity.getString(R.string.sort_none)),
                SortChoice(NoteSortOption.PRIORITY_HIGH_FIRST, activity.getString(R.string.sort_priority_high)),
                SortChoice(NoteSortOption.PRIORITY_LOW_FIRST, activity.getString(R.string.sort_priority_low)),
            )
            SortGroup.DEADLINE -> listOf(
                SortChoice(null, activity.getString(R.string.sort_none)),
                SortChoice(NoteSortOption.DEADLINE_NEAREST, activity.getString(R.string.sort_deadline_near)),
                SortChoice(NoteSortOption.DEADLINE_FARTHEST, activity.getString(R.string.sort_deadline_far)),
            )
        }
    }

    private fun createBottomSheetContainer(): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(24), activity.dp(22), activity.dp(24), activity.dp(18))
        }
    }

    private fun wrapBottomSheetContent(content: View, fixedHeight: Int? = null): View {
        val root = FrameLayout(activity).apply {
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
        val scrollView = ScrollView(activity).apply {
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
            setColor(activity.resolveThemeColor(com.google.android.material.R.attr.colorSurfaceContainerLow))
            cornerRadii = floatArrayOf(
                activity.dp(24).toFloat(),
                activity.dp(24).toFloat(),
                activity.dp(24).toFloat(),
                activity.dp(24).toFloat(),
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
        return TextView(activity).apply {
            text = title
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(activity.resolveThemeColor(com.google.android.material.R.attr.colorOnSurface))
        }
    }

    private fun createBottomSheetSectionTitle(title: String): TextView {
        return TextView(activity).apply {
            text = title
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(activity.resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = activity.dp(18)
                bottomMargin = activity.dp(6)
            }
        }
    }

    private fun createPriorityFilterRow(
        priority: NotePriority,
        registerSelectionUpdater: ((() -> Unit) -> Unit)? = null,
        onClick: () -> Unit,
    ): View {
        val priorityDot = ImageView(activity).apply {
            setImageResource(R.drawable.circle)
            NotePriorityUi.applyTo(this, priority)
            contentDescription = null
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        return createSheetOptionRow(
            title = activity.getString(NotePriorityUi.labelRes(priority)),
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
        val selectedBackgroundColor = activity.resolveThemeColor(
            com.google.android.material.R.attr.colorSurfaceVariant,
        )
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            minimumHeight = activity.dp(48)
            setPadding(activity.dp(14), activity.dp(8), activity.dp(12), activity.dp(8))
            installAlphaPressFeedback(this)

            leadingView?.let { view ->
                addView(
                    view,
                    LinearLayout.LayoutParams(activity.dp(18), activity.dp(18)).apply {
                        marginEnd = activity.dp(12)
                    },
                )
            }
            addView(
                TextView(activity).apply {
                    text = title
                    textSize = 16f
                    setTextColor(activity.resolveThemeColor(com.google.android.material.R.attr.colorOnSurface))
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )
            addView(
                ImageView(activity).apply {
                    setImageResource(R.drawable.check)
                    imageTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(activity, R.color.selection_stroke_color),
                    )
                    visibility = View.INVISIBLE
                    contentDescription = null
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(activity.dp(28), activity.dp(28)),
            )
            fun updateSelectionStyle() {
                val selected = isSelected()
                background = if (selected) {
                    GradientDrawable().apply {
                        setColor(selectedBackgroundColor)
                        cornerRadius = activity.dp(14).toFloat()
                    }
                } else {
                    null
                }
                (getChildAt(childCount - 2) as? TextView)?.setTextColor(
                    if (selected) {
                        ContextCompat.getColor(activity, R.color.selection_stroke_color)
                    } else {
                        activity.resolveThemeColor(com.google.android.material.R.attr.colorOnSurface)
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

    private fun createResetButton(onClick: () -> Unit): MaterialButton {
        return MaterialButton(activity).apply {
            text = activity.getString(R.string.filter_reset_action)
            setAllCaps(false)
            minWidth = 0
            minHeight = 0
            insetTop = 0
            insetBottom = 0
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            rippleColor = ColorStateList.valueOf(Color.TRANSPARENT)
            setTextColor(activity.resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(8))
            installAlphaPressFeedback(this)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.END
                topMargin = activity.dp(16)
            }
        }
    }

    private fun DeadlineFilter.title(): String {
        return when (this) {
            DeadlineFilter.WITH_DEADLINE -> activity.getString(R.string.filter_deadline_with)
            DeadlineFilter.OVERDUE -> activity.getString(R.string.filter_deadline_overdue)
            DeadlineFilter.ACTIVE -> activity.getString(R.string.filter_deadline_active)
            DeadlineFilter.WITHOUT_DEADLINE -> activity.getString(R.string.filter_deadline_without)
        }
    }

    private fun NoteSortOption.title(): String {
        return when (this) {
            NoteSortOption.CREATED_NEWEST -> activity.getString(R.string.sort_created_new)
            NoteSortOption.CREATED_OLDEST -> activity.getString(R.string.sort_created_old)
            NoteSortOption.PRIORITY_HIGH_FIRST -> activity.getString(R.string.sort_priority_high)
            NoteSortOption.PRIORITY_LOW_FIRST -> activity.getString(R.string.sort_priority_low)
            NoteSortOption.DEADLINE_NEAREST -> activity.getString(R.string.sort_deadline_near)
            NoteSortOption.DEADLINE_FARTHEST -> activity.getString(R.string.sort_deadline_far)
        }
    }

    private data class SortChoice(
        val option: NoteSortOption?,
        val title: String,
    )
}
