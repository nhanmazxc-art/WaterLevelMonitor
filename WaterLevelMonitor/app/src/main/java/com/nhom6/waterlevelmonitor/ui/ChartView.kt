package com.nhom6.waterlevelmonitor.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.nhom6.waterlevelmonitor.R
import java.text.SimpleDateFormat
import java.util.Date

/** Biểu đồ đường theo thời gian, có đường ngưỡng cảnh báo nét đứt. */
class ChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Point(val timeMs: Long, val value: Double)

    private val primary = ContextCompat.getColor(context, R.color.primary)
    private val red = ContextCompat.getColor(context, R.color.red)
    private val secondary = ContextCompat.getColor(context, R.color.text_secondary)

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.card_border)
        strokeWidth = dp(1f)
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secondary
        textSize = sp(10f)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = primary
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = (primary and 0x00FFFFFF) or (0x26 shl 24)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = primary
    }
    private val warnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = red
        pathEffect = DashPathEffect(floatArrayOf(dp(6f), dp(4f)), 0f)
    }
    private val warnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = red
        textSize = sp(10f)
        isFakeBoldText = true
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secondary
        textSize = sp(13f)
        textAlign = Paint.Align.CENTER
    }

    private val linePath = Path()
    private val fillPath = Path()

    private var points: List<Point> = emptyList()
    private var rangeMs = 120_000L
    private var endMs = 0L
    private var warnValue: Double? = null
    private var metricName = "Mực nước"
    private var alertWhenLower = false
    private var unit = "cm"

    fun setData(
        points: List<Point>,
        rangeMs: Long,
        endMs: Long,
        warnValue: Double?,
        metricName: String = "Mực nước",
        alertWhenLower: Boolean = false,
        unit: String = "cm"
    ) {
        this.points = points
        this.rangeMs = rangeMs
        this.endMs = endMs
        this.warnValue = warnValue
        this.metricName = metricName
        this.alertWhenLower = alertWhenLower
        this.unit = unit
        contentDescription = if (points.isEmpty()) {
            "Biểu đồ $metricName, chưa có dữ liệu"
        } else {
            "Biểu đồ $metricName, giá trị mới nhất ${String.format(VI, "%.1f", points.last().value)} xăng-ti-mét"
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = dp(40f)
        val right = width - dp(12f)
        val top = dp(12f)
        val bottom = height - dp(26f)
        val w = right - left
        val h = bottom - top
        if (w <= 0f || h <= 0f) return

        // Thang đo trục tung
        val values = points.map { it.value } + listOfNotNull(warnValue)
        var lo = 0.0
        var hi = 10.0
        if (values.isNotEmpty()) {
            lo = minOf(0.0, values.minOrNull() ?: 0.0)
            hi = maxOf((values.maxOrNull() ?: 10.0) * 1.15, lo + 10.0)
        }
        fun yOf(value: Double): Float = (bottom - ((value - lo) / (hi - lo)).toFloat() * h)

        // Lưới ngang và nhãn trục tung
        axisPaint.textAlign = Paint.Align.RIGHT
        for (i in 0..4) {
            val y = bottom - h * i / 4f
            canvas.drawLine(left, y, right, y, gridPaint)
            val value = lo + (hi - lo) * i / 4.0
            val shown = if (unit == "m") value / 100.0 else value
            canvas.drawText(String.format(VI, if (unit == "m") "%.2f" else "%.0f", shown), left - dp(6f), y + sp(3.5f), axisPaint)
        }

        // Nhãn thời gian hai đầu
        val pattern = if (rangeMs >= 600_000L) "HH:mm" else "HH:mm:ss"
        val format = SimpleDateFormat(pattern, VI)
        axisPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(format.format(Date(endMs - rangeMs)), left, height - dp(8f), axisPaint)
        axisPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(format.format(Date(endMs)), right, height - dp(8f), axisPaint)

        // Đường ngưỡng cảnh báo
        warnValue?.let { warn ->
            val y = yOf(warn)
            canvas.drawLine(left, y, right, y, warnPaint)
            canvas.drawText(
                "${if (alertWhenLower) "Cảnh báo ≤" else "Cảnh báo"} ${String.format(VI, if (unit == "m") "%.2f" else "%.0f", if (unit == "m") warn / 100.0 else warn)} $unit",
                left + dp(4f), y - dp(4f), warnTextPaint
            )
        }

        if (points.isEmpty()) {
            canvas.drawText("Chưa có dữ liệu, đang chờ thiết bị…", (left + right) / 2f, (top + bottom) / 2f, emptyPaint)
            return
        }

        fun xOf(timeMs: Long): Float {
            val fraction = (timeMs - (endMs - rangeMs)).toFloat() / rangeMs.toFloat()
            return left + fraction.coerceIn(0f, 1f) * w
        }

        // Đường và vùng tô dưới đường
        linePath.reset()
        fillPath.reset()
        points.forEachIndexed { index, p ->
            val x = xOf(p.timeMs)
            val y = yOf(p.value)
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, bottom)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        val last = points.last()
        fillPath.lineTo(xOf(last.timeMs), bottom)
        fillPath.close()
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)
        canvas.drawCircle(xOf(last.timeMs), yOf(last.value), dp(4f), dotPaint)
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
