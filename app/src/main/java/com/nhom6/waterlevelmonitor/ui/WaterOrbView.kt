package com.nhom6.waterlevelmonitor.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.nhom6.waterlevelmonitor.R
import java.util.Locale

/** Ô tròn biểu diễn mực nước h = H - d. d là khoảng cách đo từ HC-SR04. */
class WaterOrbView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var level: Double? = null
    private var sensorHeight = 25.0
    private var waterColor = ContextCompat.getColor(context, R.color.water_normal)
    private val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 38 }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(4f); color = Color.WHITE }
    private val number = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = sp(42f); isFakeBoldText = true }
    private val unit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = sp(14f); isFakeBoldText = true }

    fun setData(levelCm: Double?, colorRes: Int, sensorHeightCm: Double) {
        level = levelCm
        sensorHeight = sensorHeightCm.takeIf { it > 0.0 } ?: 25.0
        waterColor = ContextCompat.getColor(context, colorRes)
        contentDescription = levelCm?.let { "Mực nước ${String.format(Locale("vi", "VN"), "%.1f", it)} cm" } ?: "Chưa có mực nước"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat(); val r = size / 2f - dp(3f); val cx = width / 2f; val cy = height / 2f
        canvas.drawCircle(cx, cy, r, base)
        val ratio = ((level ?: 0.0) / sensorHeight).coerceIn(0.05, 1.0).toFloat()
        val top = cy + r - 2f * r * ratio
        fill.color = waterColor; fill.alpha = 220
        canvas.save(); canvas.clipPath(android.graphics.Path().apply { addCircle(cx, cy, r, android.graphics.Path.Direction.CW) })
        canvas.drawRoundRect(RectF(cx-r, top, cx+r, cy+r), r, r, fill)
        canvas.restore(); canvas.drawCircle(cx, cy, r, rim)
        canvas.drawText(level?.let { String.format(Locale("vi", "VN"), "%.1f", it) } ?: "--", cx, cy + dp(4f), number)
        canvas.drawText("cm", cx, cy + dp(28f), unit)
    }
    private fun dp(v:Float)=TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,v,resources.displayMetrics)
    private fun sp(v:Float)=TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,v,resources.displayMetrics)
}
