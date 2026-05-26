package com.example.notavia

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.notavia.databinding.ActivitySettingsBinding
import com.example.notavia.settings.AppFontSize
import com.example.notavia.settings.AppTheme
import com.example.notavia.settings.AppearancePreferences
import com.example.notavia.settings.ThemePreferences
import kotlinx.coroutines.launch

class SettingsActivity : NotaviaActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var themePreferences: ThemePreferences
    private lateinit var appearancePreferences: AppearancePreferences

    private var currentTheme: AppTheme = AppTheme.DARK
    private var currentFontSize: AppFontSize = AppFontSize.MEDIUM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        themePreferences = ThemePreferences(this)
        appearancePreferences = AppearancePreferences(this)
        setupActions()
        observeSettings()
    }

    private fun setupActions() {
        installAlphaPressFeedback(binding.backButton)
        installAlphaPressFeedback(binding.fontSizeRow)
        installAlphaPressFeedback(binding.themeRow)

        binding.backButton.setOnClickListener {
            finish()
        }

        binding.fontSizeRow.setOnClickListener {
            showFontSizeMenu()
        }

        binding.themeRow.setOnClickListener {
            showThemeMenu()
        }
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            themePreferences.themeFlow.collect { theme ->
                currentTheme = theme
                updateThemeRow()
            }
        }
        lifecycleScope.launch {
            appearancePreferences.fontSizeFlow.collect { fontSize ->
                currentFontSize = fontSize
                updateFontSizeRow()
            }
        }
    }

    private fun showFontSizeMenu() {
        showSettingsMenu(
            anchor = binding.fontSizeRow,
            options = AppFontSize.entries.map { fontSize ->
                SettingsOption(fontSize, getString(fontSize.labelRes))
            },
            selectedValue = currentFontSize,
        ) { fontSize ->
            changeFontSize(fontSize)
        }
    }

    private fun showThemeMenu() {
        showSettingsMenu(
            anchor = binding.themeRow,
            options = listOf(
                SettingsOption(AppTheme.DARK, getString(R.string.dark_theme_short)),
                SettingsOption(AppTheme.LIGHT, getString(R.string.light_theme_short)),
            ),
            selectedValue = currentTheme,
        ) { theme ->
            changeTheme(theme)
        }
    }

    private fun <T> showSettingsMenu(
        anchor: View,
        options: List<SettingsOption<T>>,
        selectedValue: T,
        onSelected: (T) -> Unit,
    ) {
        val popupWidth = dp(252)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(resolveThemeColor(com.google.android.material.R.attr.colorSurfaceContainerLow))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), resolveThemeColor(com.google.android.material.R.attr.colorOutline))
            }
            clipToOutline = true
            setPadding(dp(8), dp(8), dp(8), dp(8))
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
            elevation = dp(8).toFloat()
        }

        val xOffset = (anchor.width - popupWidth).coerceAtLeast(0)
        popupWindow.showAsDropDown(anchor, xOffset, -dp(4))
    }

    private fun createPopupRow(
        title: String,
        isSelected: Boolean,
        onClick: () -> Unit,
    ): View {
        val selectedBackgroundColor = resolveThemeColor(
            com.google.android.material.R.attr.colorSurfaceVariant,
        )
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = if (isSelected) {
                GradientDrawable().apply {
                    setColor(selectedBackgroundColor)
                    cornerRadius = dp(12).toFloat()
                }
            } else {
                ColorDrawable(Color.TRANSPARENT)
            }
            setPadding(dp(22), 0, dp(20), 0)
            installAlphaPressFeedback(this)
            setOnClickListener {
                onClick()
            }

            addView(
                TextView(this@SettingsActivity).apply {
                    text = title
                    textSize = 16f
                    setTextColor(
                        if (isSelected) {
                            ContextCompat.getColor(this@SettingsActivity, R.color.selection_stroke_color)
                        } else {
                            resolveThemeColor(com.google.android.material.R.attr.colorOnSurface)
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
                ImageView(this@SettingsActivity).apply {
                    setImageResource(R.drawable.check)
                    imageTintList = android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this@SettingsActivity, R.color.selection_stroke_color),
                    )
                    visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
                    contentDescription = null
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.MATCH_PARENT),
            )
        }.also { row ->
            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56),
            )
        }
    }

    private fun changeTheme(theme: AppTheme) {
        if (theme == currentTheme) return

        lifecycleScope.launch {
            themePreferences.setTheme(theme)
            AppCompatDelegate.setDefaultNightMode(theme.nightMode)
        }
    }

    private fun changeFontSize(fontSize: AppFontSize) {
        if (fontSize == currentFontSize) return

        lifecycleScope.launch {
            appearancePreferences.setFontSize(fontSize)
            recreate()
        }
    }

    private fun updateThemeRow() {
        binding.themeValueTextView.text = getString(
            if (currentTheme == AppTheme.DARK) {
                R.string.dark_theme_short
            } else {
                R.string.light_theme_short
            },
        )
    }

    private fun updateFontSizeRow() {
        binding.fontSizeValueTextView.text = getString(currentFontSize.labelRes)
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
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(this, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class SettingsOption<T>(
        val value: T,
        val title: String,
    )

    companion object {
        private const val BUTTON_PRESSED_ALPHA = 0.68f
    }
}
