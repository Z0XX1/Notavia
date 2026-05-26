package com.example.notavia.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.example.notavia.R
import com.google.android.material.R as MaterialR

class BottomBarCradleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val barPath = Path()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        barPaint.color = resolveThemeColor(MaterialR.attr.colorSurfaceContainerLow)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f) return

        val centerX = viewWidth / 2f
        val sideTop = dp(12f)
        val cradleTop = 0f
        val shoulderHalfWidth = dp(88f)
        val controlNear = dp(56f)
        val controlFar = dp(72f)

        barPath.reset()
        barPath.moveTo(0f, sideTop)
        barPath.lineTo(centerX - shoulderHalfWidth, sideTop)
        barPath.cubicTo(
            centerX - controlFar,
            sideTop,
            centerX - controlNear,
            cradleTop,
            centerX,
            cradleTop,
        )
        barPath.cubicTo(
            centerX + controlNear,
            cradleTop,
            centerX + controlFar,
            sideTop,
            centerX + shoulderHalfWidth,
            sideTop,
        )
        barPath.lineTo(viewWidth, sideTop)
        barPath.lineTo(viewWidth, viewHeight)
        barPath.lineTo(0f, viewHeight)
        barPath.close()

        canvas.drawPath(barPath, barPaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun resolveThemeColor(attr: Int): Int {
        val typedValue = TypedValue()
        val resolved = context.theme.resolveAttribute(attr, typedValue, true)
        if (!resolved) return 0
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(context, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }
}
