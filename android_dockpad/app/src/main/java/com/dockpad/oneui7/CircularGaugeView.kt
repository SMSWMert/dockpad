package com.dockpad.oneui7

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class CircularGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progressPercent: Int = 0
    private var ringColor: Int = Color.parseColor("#0381FE")
    private var subLabel: String = "Kullanım"

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 18f
        color = Color.parseColor("#27272A")
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 18f
        color = ringColor
        strokeCap = Paint.Cap.ROUND
    }

    private val percentTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A1A1AA")
        textSize = 20f
        textAlign = Paint.Align.CENTER
    }

    private val arcRect = RectF()

    fun setProgress(percent: Int, colorHex: Int, label: String = "Kullanım") {
        progressPercent = percent.coerceIn(0, 100)
        ringColor = colorHex
        subLabel = label
        progressPaint.color = ringColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        val pad = 16f
        arcRect.set(pad, pad, size - pad, size - pad)

        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint)
        val sweep = (progressPercent / 100f) * 360f
        canvas.drawArc(arcRect, -90f, sweep, false, progressPaint)

        val cx = width / 2f
        val cy = height / 2f
        canvas.drawText("${progressPercent}%", cx, cy + 6f, percentTextPaint)
        canvas.drawText(subLabel, cx, cy + 32f, subTextPaint)
    }
}
