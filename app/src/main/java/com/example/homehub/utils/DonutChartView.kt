package com.example.homehub.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private var data = listOf<Segment>()
    private val rectF = RectF()

    data class Segment(
        val value: Float,
        val color: Int,
        val label: String
    )

    fun setData(newData: List<Segment>) {
        data = newData
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val size = Math.min(width, height)
        val strokeWidth = size * 0.15f
        paint.strokeWidth = strokeWidth

        val margin = strokeWidth / 2f
        rectF.set(margin, margin, size - margin, size - margin)
        
        // Center the rect
        rectF.offset((width - size) / 2f, (height - size) / 2f)

        val total = data.sumOf { it.value.toDouble() }.toFloat()
        if (total == 0f) return

        var startAngle = -90f
        for (segment in data) {
            val sweepAngle = (segment.value / total) * 360f
            paint.color = segment.color
            
            // Draw a subtle background for the segment
            val bgPaint = Paint(paint).apply {
                alpha = 40
            }
            canvas.drawArc(rectF, startAngle, sweepAngle, false, bgPaint)
            
            // Draw the actual segment (slightly shorter to create gaps)
            canvas.drawArc(rectF, startAngle + 2f, sweepAngle - 4f, false, paint)
            
            startAngle += sweepAngle
        }
    }
}
