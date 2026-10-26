package com.nhom6.waterlevelmonitor.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.nhom6.waterlevelmonitor.R
import kotlin.math.max

/** Biểu đồ cột tĩnh để số liệu thống kê không nhảy theo mỗi lần cập nhật UI. */
class BarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    /** value = null khi khoảng đó chưa có số đo, khác với mực nước bằng 0. */
    data class Bar(val label: String, val value: Double?)
    private var bars: List<Bar> = emptyList()
    private var warn = 0.0
    private var progress = 1f
    private val blue = ContextCompat.getColor(context, R.color.primary)
    private val cyan = ContextCompat.getColor(context, R.color.water_normal)
    private val orange = ContextCompat.getColor(context, R.color.orange)
    private val red = ContextCompat.getColor(context, R.color.red)
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.card_border); strokeWidth = dp(1f) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.text_secondary); textSize = sp(10f); textAlign = Paint.Align.CENTER }
    private val valueText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.text_primary); textSize = sp(10f); textAlign = Paint.Align.CENTER }
    private val warnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = red; strokeWidth = dp(1.4f) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val emptyFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.card_border) }

    fun setData(data: List<Bar>, warning: Double) {
        bars = data; warn = warning; progress = 1f
        invalidate()
    }
    private fun animateBars() {
        val start = System.currentTimeMillis()
        fun frame() {
            progress = ((System.currentTimeMillis() - start) / 360f).coerceIn(0f, 1f)
            invalidate()
            if (progress < 1f) postOnAnimation { frame() }
        }; frame()
    }
    override fun onDraw(c: Canvas) {
        val l=dp(32f); val r=width-dp(8f); val t=dp(22f); val b=height-dp(30f); val h=b-t
        val measured = bars.mapNotNull { it.value }
        val rawMin = minOf(measured.minOrNull() ?: 0.0, if (warn > 0) warn else Double.MAX_VALUE)
        val rawMax = max(measured.maxOrNull() ?: 1.0, warn)
        val spread = (rawMax - rawMin).coerceAtLeast(2.0)
        val chartMin = (rawMin - spread * 0.18).coerceAtLeast(0.0)
        val chartMax = rawMax + spread * 0.18
        fun yOf(value: Double) = b - ((value - chartMin) / (chartMax - chartMin)).toFloat() * h
        c.drawLine(l, t, l, b, grid)
        for (i in 0..3) {
            val value = chartMin + (chartMax - chartMin) * i / 3
            val y = yOf(value)
            c.drawLine(l, y, r, y, grid)
            text.textAlign = Paint.Align.RIGHT
            c.drawText(fmt(value), l - dp(5f), y + dp(3f), text)
        }
        if (warn > 0) {
            val y=yOf(warn); c.drawLine(l,y,r,y,warnPaint)
            text.textAlign=Paint.Align.LEFT; text.color=red; c.drawText("Ngưỡng ${fmt(warn)} cm",l+dp(4f),y-dp(4f),text)
            text.color=ContextCompat.getColor(context,R.color.text_secondary)
        }
        if (bars.isEmpty()) { text.textAlign=Paint.Align.CENTER; c.drawText("Chưa có dữ liệu trong khoảng này", width/2f, t+h/2, text); return }
        val slot=(r-l)/bars.size; val bw=(slot*0.82f).coerceAtMost(dp(46f))
        bars.forEachIndexed { i, bar ->
            val x=l+slot*i+(slot-bw)/2
            val value = bar.value
            if (value == null) {
                // Một vạch mờ cho biết đây là vùng chưa được đo, không phải giá trị 0 cm.
                c.drawRoundRect(RectF(x, b-dp(4f), x+bw, b), dp(3f), dp(3f), emptyFill)
                text.textAlign=Paint.Align.CENTER; c.drawText("–", x+bw/2, b-dp(9f), text)
            } else {
                val y = b - (b-yOf(value))*progress
                fill.color=when { value>=warn -> red; value>=warn*0.72 -> orange; i==bars.lastIndex -> blue; else -> cyan }
                c.drawRoundRect(RectF(x,y,x+bw,b),dp(7f),dp(7f),fill)
                valueText.textAlign = Paint.Align.CENTER
                c.drawText(fmt(value), x+bw/2, y-dp(5f), valueText)
            }
            text.textAlign=Paint.Align.CENTER; c.drawText(bar.label,x+bw/2,height-dp(8f),text)
        }
    }
    private fun fmt(v: Double)=if(v % 1.0 == 0.0) v.toInt().toString() else String.format(VI,"%.1f",v)
    private fun dp(v:Float)=TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,v,resources.displayMetrics)
    private fun sp(v:Float)=TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,v,resources.displayMetrics)
}
