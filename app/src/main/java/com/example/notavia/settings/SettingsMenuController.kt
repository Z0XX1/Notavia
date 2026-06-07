package com.example.notavia.settings

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.notavia.NotaviaActivity
import com.example.notavia.R
import com.example.notavia.ui.dp
import com.example.notavia.ui.installAlphaPressFeedback
import com.example.notavia.ui.resolveThemeColor

class SettingsMenuController(
    private val activity: NotaviaActivity,
) {
    fun <T> show(
        anchor: View,
        options: List<SettingsOption<T>>,
        selectedValue: T,
        onSelected: (T) -> Unit,
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
        options.forEach { option ->
            val isSelected = option.value == selectedValue
            container.addView(
                createPopupRow(
                    title = option.title,
                    isSelected = isSelected,
                ) {
                    popupWindow?.dismiss()
                    onSelected(option.value)
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
        popupWindow.showAsDropDown(anchor, xOffset, -activity.dp(4))
    }

    private fun createPopupRow(
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
}

data class SettingsOption<T>(
    val value: T,
    val title: String,
)
