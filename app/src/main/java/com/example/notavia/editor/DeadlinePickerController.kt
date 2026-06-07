package com.example.notavia.editor

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.NumberPicker
import com.example.notavia.NotaviaActivity
import com.example.notavia.R
import com.example.notavia.databinding.ActivityEditNoteBinding
import com.example.notavia.ui.currentLocale
import com.example.notavia.ui.dp
import com.example.notavia.ui.installAlphaPressFeedback
import com.example.notavia.ui.resolveThemeColor
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

class DeadlinePickerController(
    private val activity: NotaviaActivity,
    private val binding: ActivityEditNoteBinding,
) {
    var selectedDeadlineAt: Long? = null
        private set

    private var isExpanded: Boolean = false
    private var isUpdatingPickers: Boolean = false
    private val deadlineDateFormatter: SimpleDateFormat by lazy {
        SimpleDateFormat(DEADLINE_DATE_PATTERN, activity.currentLocale())
    }
    private val monthLabels: Array<String> by lazy {
        val locale = activity.currentLocale()
        DateFormatSymbols.getInstance(locale).shortMonths
            .take(12)
            .map { month ->
                month.trim()
                    .removeSuffix(".")
                    .lowercase(locale)
            }
            .toTypedArray()
    }

    fun setup(onChanged: () -> Unit) {
        listOf(
            binding.deadlineDayPicker,
            binding.deadlineMonthPicker,
            binding.deadlineYearPicker,
        ).forEach(::styleNumberPicker)

        configurePickers(selectedDeadlineAt ?: todayStartMillis())
        updateUi()

        val deadlineValueChangeListener = NumberPicker.OnValueChangeListener { _, _, _ ->
            updateDeadlineFromPickers(onChanged)
        }
        binding.deadlineDayPicker.setOnValueChangedListener(deadlineValueChangeListener)
        binding.deadlineMonthPicker.setOnValueChangedListener(deadlineValueChangeListener)
        binding.deadlineYearPicker.setOnValueChangedListener(deadlineValueChangeListener)

        installAlphaPressFeedback(binding.deadlineHeader)
        binding.deadlineHeader.setOnClickListener {
            setExpanded(!isExpanded)
        }

        installAlphaPressFeedback(binding.clearDeadlineTextView)
        binding.clearDeadlineTextView.setOnClickListener {
            clear()
            onChanged()
        }
    }

    fun setDeadline(deadlineAt: Long?) {
        selectedDeadlineAt = deadlineAt
        configurePickers(selectedDeadlineAt ?: todayStartMillis())
        updateUi()
    }

    fun clear() {
        selectedDeadlineAt = null
        setExpanded(false)
        updateUi()
    }

    fun hidePicker() {
        isExpanded = false
        binding.deadlinePickerCardView.visibility = View.GONE
        binding.deadlinePickerCardView.alpha = 1f
        binding.deadlinePickerCardView.translationY = 0f
        updateUi()
    }

    private fun setExpanded(expanded: Boolean) {
        if (isExpanded == expanded) return

        isExpanded = expanded
        binding.deadlineArrowImageView.animate()
            .rotation(if (expanded) 90f else 0f)
            .setDuration(DEADLINE_ARROW_ANIMATION_MS)
            .start()

        if (expanded) {
            configurePickers(selectedDeadlineAt ?: todayStartMillis())
            binding.deadlinePickerCardView.apply {
                visibility = View.VISIBLE
                alpha = 0f
                translationY = -activity.dp(6).toFloat()
                animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(DEADLINE_PICKER_ANIMATION_MS)
                    .start()
            }
        } else {
            binding.deadlinePickerCardView.animate()
                .alpha(0f)
                .translationY(-activity.dp(6).toFloat())
                .setDuration(DEADLINE_PICKER_ANIMATION_MS)
                .withEndAction {
                    binding.deadlinePickerCardView.visibility = View.GONE
                    binding.deadlinePickerCardView.alpha = 1f
                    binding.deadlinePickerCardView.translationY = 0f
                }
                .start()
        }
    }

    private fun updateDeadlineFromPickers(onChanged: () -> Unit) {
        if (isUpdatingPickers) return

        val deadline = coerceDeadlineMillis(
            startOfDayMillis(
                year = binding.deadlineYearPicker.value,
                month = binding.deadlineMonthPicker.value,
                day = binding.deadlineDayPicker.value,
            ),
        )
        selectedDeadlineAt = deadline
        configurePickers(deadline)
        updateUi()
        onChanged()
    }

    private fun updateUi() {
        binding.deadlineValueTextView.text = selectedDeadlineAt?.let { deadline ->
            deadlineDateFormatter.format(Date(deadline))
        } ?: activity.getString(R.string.deadline_not_selected)
        binding.deadlineArrowImageView.rotation = if (isExpanded) 90f else 0f
    }

    private fun configurePickers(deadlineMillis: Long) {
        val coercedDeadline = coerceDeadlineMillis(deadlineMillis)
        val (year, month, day) = dateParts(coercedDeadline)
        val (currentYear, currentMonth, currentDay) = dateParts(todayStartMillis())

        isUpdatingPickers = true
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
            isUpdatingPickers = false
        }
    }

    private fun styleNumberPicker(picker: NumberPicker) {
        picker.wrapSelectorWheel = false
        picker.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        picker.setOnLongPressUpdateInterval(120L)
        tintNumberPickerText(picker)
    }

    private fun tintNumberPickerText(view: View) {
        if (view is EditText) {
            view.setTextColor(activity.resolveThemeColor(com.google.android.material.R.attr.colorOnSurface))
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

    private companion object {
        const val DEADLINE_ARROW_ANIMATION_MS = 180L
        const val DEADLINE_PICKER_ANIMATION_MS = 160L
        const val MAX_DEADLINE_YEAR = 2067
        const val DEADLINE_DATE_PATTERN = "d MMM yyyy"
    }
}
