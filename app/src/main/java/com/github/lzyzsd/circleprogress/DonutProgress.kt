package com.github.lzyzsd.circleprogress

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.github.lzyzsd.circleprogress.DonutProgress
import android.view.View.MeasureSpec

/**
 * Created by bruce on 14-10-30. Edited by QuantumBadger and Cguy7777.
 */
class DonutProgress(context: Context?) : View(context) {
    private var finishedPaint: Paint? = null
    private var unfinishedPaint: Paint? = null
    private var aspectIndicatorPaint: Paint? = null
    private val finishedOuterRect = RectF()
    private val unfinishedOuterRect = RectF()
    private val aspectIndicatorRect = RectF()
    private var indeterminate = false
    private var aspectIndicatorDisplay = false
    var progress = 0f
        set(progress) {
            if (Math.abs(progress - this.progress) > 0.0001) {
                field = progress
                invalidate()
            }
        }
    private var finishedStrokeColor = 0
    private var unfinishedStrokeColor = 0
    private var aspectIndicatorStrokeColor = 0
    var startingDegree = 0
    private var finishedStrokeWidth = 0f
    private var unfinishedStrokeWidth = 0f
    private var aspectIndicatorStrokeWidth = 0f
    private var imageAspectRatio = 0f
    private val min_size: Int

    init {
        min_size = dp2px(resources, 100f).toInt()
        initPainters()
    }

    fun initPainters() {
        finishedPaint = Paint()
        finishedPaint!!.color = finishedStrokeColor
        finishedPaint!!.style = Paint.Style.STROKE
        finishedPaint!!.isAntiAlias = true
        finishedPaint!!.strokeWidth = finishedStrokeWidth
        unfinishedPaint = Paint()
        unfinishedPaint!!.color = unfinishedStrokeColor
        unfinishedPaint!!.style = Paint.Style.STROKE
        unfinishedPaint!!.isAntiAlias = true
        unfinishedPaint!!.strokeWidth = unfinishedStrokeWidth
        aspectIndicatorPaint = Paint()
        aspectIndicatorPaint!!.color = aspectIndicatorStrokeColor
        aspectIndicatorPaint!!.style = Paint.Style.STROKE
        aspectIndicatorPaint!!.isAntiAlias = true
        aspectIndicatorPaint!!.strokeWidth = aspectIndicatorStrokeWidth
    }

    fun setFinishedStrokeWidth(finishedStrokeWidth: Float) {
        this.finishedStrokeWidth = finishedStrokeWidth
    }

    fun setUnfinishedStrokeWidth(unfinishedStrokeWidth: Float) {
        this.unfinishedStrokeWidth = unfinishedStrokeWidth
    }

    fun setAspectIndicatorStrokeWidth(aspectIndicatorStrokeWidth: Float) {
        this.aspectIndicatorStrokeWidth = aspectIndicatorStrokeWidth
    }

    fun setIndeterminate(value: Boolean) {
        indeterminate = value
        invalidate()
    }

    fun setAspectIndicatorDisplay(value: Boolean) {
        aspectIndicatorDisplay = value
    }

    private val progressAngle: Float
        private get() = progress * 360f

    fun setFinishedStrokeColor(finishedStrokeColor: Int) {
        this.finishedStrokeColor = finishedStrokeColor
    }

    fun setUnfinishedStrokeColor(unfinishedStrokeColor: Int) {
        this.unfinishedStrokeColor = unfinishedStrokeColor
    }

    fun setAspectIndicatorStrokeColor(aspectIndicatorStrokeColor: Int) {
        this.aspectIndicatorStrokeColor = aspectIndicatorStrokeColor
    }

    fun setLoadingImageAspectRatio(imageAspectRatio: Float) {
        this.imageAspectRatio = imageAspectRatio
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(measure(widthMeasureSpec), measure(heightMeasureSpec))
    }

    private fun measure(measureSpec: Int): Int {
        var result: Int
        val mode = MeasureSpec.getMode(measureSpec)
        val size = MeasureSpec.getSize(measureSpec)
        if (mode == MeasureSpec.EXACTLY) {
            result = size
        } else {
            result = min_size
            if (mode == MeasureSpec.AT_MOST) {
                result = Math.min(result, size)
            }
        }
        return result
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val delta = Math.max(finishedStrokeWidth, unfinishedStrokeWidth)
        finishedOuterRect[delta, delta, width - delta] = height - delta
        unfinishedOuterRect[delta, delta, width - delta] = height - delta
        canvas.drawArc(unfinishedOuterRect, 0f, 360f, false, unfinishedPaint!!)
        if (indeterminate) {
            val startAngle = (System.currentTimeMillis() % 1000).toFloat() * 360 / 1000
            canvas.drawArc(finishedOuterRect, startAngle, 50f, false, finishedPaint!!)
            invalidate()
        } else {
            canvas.drawArc(
                finishedOuterRect,
                startingDegree.toFloat(),
                progressAngle,
                false,
                finishedPaint!!
            )
        }
        if (aspectIndicatorDisplay) {
            val maxRatio = 2.75f
            if (imageAspectRatio > maxRatio) {
                imageAspectRatio = maxRatio
            } else if (imageAspectRatio < 1 / maxRatio) {
                imageAspectRatio = 1 / maxRatio
            }
            val arDeltaMultiplier = 3.5f
            val indicatorLeft =
                delta * arDeltaMultiplier + delta * (arDeltaMultiplier / 4) * (1 - imageAspectRatio)
            val indicatorTop =
                delta * arDeltaMultiplier + delta * (arDeltaMultiplier / 4) * (1 - 1 / imageAspectRatio)
            aspectIndicatorRect[indicatorLeft, indicatorTop, width - indicatorLeft] =
                height - indicatorTop
            canvas.drawRect(aspectIndicatorRect, aspectIndicatorPaint!!)
        }
    }

    companion object {
        fun dp2px(resources: Resources, dp: Float): Float {
            val scale = resources.displayMetrics.density
            return dp * scale + 0.5f
        }
    }
}
