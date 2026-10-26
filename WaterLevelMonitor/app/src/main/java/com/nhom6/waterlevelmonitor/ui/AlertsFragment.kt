package com.nhom6.waterlevelmonitor.ui

import android.os.Bundle
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.nhom6.waterlevelmonitor.R
import com.nhom6.waterlevelmonitor.data.AlertLevel
import com.nhom6.waterlevelmonitor.data.DeviceRepository
import com.nhom6.waterlevelmonitor.data.UiState

class AlertsFragment : Fragment(R.layout.fragment_alerts) {
    private lateinit var levels: LinearLayout
    private lateinit var history: LinearLayout
    private val observer: (UiState) -> Unit = { render(it) }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) { levels = view.findViewById(R.id.alertLevels); history = view.findViewById(R.id.alertHistory) }
    override fun onStart() { super.onStart(); DeviceRepository.addObserver(observer) }
    override fun onStop() { DeviceRepository.removeObserver(observer); super.onStop() }
    private fun render(state: UiState) {
        levels.removeAllViews()
        addLevel(AlertLevel.NORMAL, "d > ${number(state.warnLevelCm)} cm · Khoảng cách đến mặt nước bình thường", state.level)
        addLevel(AlertLevel.HIGH, "${number(state.safeLevelCm)} cm < d ≤ ${number(state.warnLevelCm)} cm · Cần theo dõi", state.level)
        addLevel(AlertLevel.DANGER, "d ≤ ${number(state.safeLevelCm)} cm · Cần xử lý ngay", state.level)
        addLevel(AlertLevel.SENSOR_ERROR, "Không nhận được tín hiệu HC-SR04", state.level)
        history.removeAllViews()
        val events = DeviceRepository.eventsSnapshot().take(12)
        if (events.isEmpty()) add(history, "Chưa có thay đổi mức cảnh báo", "Dữ liệu sẽ xuất hiện khi trạng thái thay đổi.")
        else events.forEach { event -> add(history, "${event.from.label} → ${event.to.label}", formatTime(event.timeMs), event.to) }
    }
    private fun addLevel(level: AlertLevel, description: String, current: AlertLevel) {
        val active = current == level || (current == AlertLevel.RECOVERING && level == AlertLevel.NORMAL)
        val color = ContextCompat.getColor(requireContext(), level.textColorRes)
        val bg = ContextCompat.getColor(requireContext(), level.bgColorRes)
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
            val shape = GradientDrawable().apply { cornerRadius = dp(26).toFloat(); setColor(bg); if (active) setStroke(dp(3), color) }
            background = shape; setPadding(dp(20), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) }
        }
        row.addView(TextView(requireContext()).apply { text = "●"; textSize = 28f; setTextColor(color) })
        row.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply { text = level.label; textSize = 23f; setTextColor(color); setTypeface(typeface, android.graphics.Typeface.BOLD) })
            addView(TextView(context).apply { text = description; textSize = 16f; setTextColor(ContextCompat.getColor(context, R.color.text_secondary)) })
        })
        if (active) row.addView(TextView(requireContext()).apply { text = "HIỆN TẠI"; textSize = 11f; setTextColor(color); setTypeface(typeface, android.graphics.Typeface.BOLD); background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(Color.WHITE) }; setPadding(dp(12), dp(7), dp(12), dp(7)) })
        levels.addView(row)
    }
    private fun add(parent: LinearLayout, title: String, sub: String, level: AlertLevel? = null) { parent.addView(LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, dp(11), 0, dp(11)); addView(TextView(context).apply { text = title; textSize = 16f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(ContextCompat.getColor(context, level?.textColorRes ?: R.color.text_primary)); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }); addView(TextView(context).apply { text = sub.substringBefore(','); textSize = 15f; setTextColor(ContextCompat.getColor(context, R.color.text_secondary)) }) }) }
    private fun number(value: Double) = if (value == value.toInt().toDouble()) value.toInt().toString() else String.format(VI, "%.1f", value)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
