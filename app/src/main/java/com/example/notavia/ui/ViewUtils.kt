package com.example.notavia.ui

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import java.util.Locale

data class ViewPadding(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

fun Context.dp(value: Int): Int {
    return (value * resources.displayMetrics.density).toInt()
}

fun Context.resolveThemeColor(attr: Int): Int {
    val typedValue = android.util.TypedValue()
    theme.resolveAttribute(attr, typedValue, true)
    return if (typedValue.resourceId != 0) {
        ContextCompat.getColor(this, typedValue.resourceId)
    } else {
        typedValue.data
    }
}

fun Context.currentLocale(): Locale {
    val locales = resources.configuration.locales
    return if (locales.size() > 0) locales[0] else Locale.getDefault()
}

fun installAlphaPressFeedback(view: View, pressedAlpha: Float = 0.68f) {
    view.setOnTouchListener { pressedView, event ->
        pressedView.alpha = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> pressedAlpha
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> 1f
            else -> pressedView.alpha
        }
        false
    }
}

fun View.capturePadding(): ViewPadding {
    return ViewPadding(
        left = paddingLeft,
        top = paddingTop,
        right = paddingRight,
        bottom = paddingBottom,
    )
}

fun View.captureBottomMargin(): Int {
    return (layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
}

fun View.captureHeight(): Int {
    val layoutHeight = layoutParams?.height ?: 0
    return if (layoutHeight > 0) layoutHeight else height
}

fun View.updateHeight(height: Int) {
    if (layoutParams.height == height) return

    layoutParams = layoutParams.apply {
        this.height = height
    }
}

fun View.updateBottomMargin(bottomMargin: Int) {
    val marginLayoutParams = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    if (marginLayoutParams.bottomMargin == bottomMargin) return

    marginLayoutParams.bottomMargin = bottomMargin
    layoutParams = marginLayoutParams
}
