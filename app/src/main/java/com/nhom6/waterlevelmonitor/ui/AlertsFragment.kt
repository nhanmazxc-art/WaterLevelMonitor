package com.nhom6.waterlevelmonitor.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.nhom6.waterlevelmonitor.R
import com.nhom6.waterlevelmonitor.data.AlertLevel
import com.nhom6.waterlevelmonitor.data.AlertLogEntry
import com.nhom6.waterlevelmonitor.data.AlertRepository
import com.nhom6.waterlevelmonitor.data.DeviceRepository
import com.nhom6.waterlevelmonitor.data.UiState
import java.util.Locale

class AlertsFragment : Fragment(R.layout.fragment_alerts) {
    private lateinit var levels: LinearLayout
    private lateinit var recyclerAlertHistory: RecyclerView
    private lateinit var btnClearAlertHistory: MaterialButton
    private lateinit var tvEmptyAlerts: TextView
    private lateinit var adapter: AlertHistoryAdapter

    private val observer: (UiState) -> Unit = { render(it) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        levels = view.findViewById(R.id.alertLevels)
        recyclerAlertHistory = view.findViewById(R.id.recyclerAlertHistory)
        btnClearAlertHistory = view.findViewById(R.id.btnClearAlertHistory)
        tvEmptyAlerts = view.findViewById(R.id.tvEmptyAlerts)

        // Setup RecyclerView & Adapter
        adapter = AlertHistoryAdapter()
        recyclerAlertHistory.layoutManager = LinearLayoutManager(requireContext())
        recyclerAlertHistory.adapter = adapter

        // Setup Clear Log button
        btnClearAlertHistory.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Xóa nhật ký sự cố")
                .setMessage("Bạn có chắc chắn muốn xóa tất cả lịch sử sự cố và cảnh báo không?")
                .setPositiveButton("Xóa") { _, _ ->
                    AlertRepository.clearAllAlerts()
                    Toast.makeText(requireContext(), "Đã xóa lịch sử cảnh báo", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Hủy", null)
                .show()
        }

        // Lắng nghe realtime từ Firebase /alerts thông qua AlertRepository
        AlertRepository.listenAlertLog { list ->
            activity?.runOnUiThread {
                updateAlertList(list)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        DeviceRepository.addObserver(observer)
        updateAlertList(AlertRepository.getAlertLog())
    }

    override fun onStop() {
        DeviceRepository.removeObserver(observer)
        super.onStop()
    }

    private fun updateAlertList(list: List<AlertLogEntry>) {
        if (!isAdded) return
        if (list.isEmpty()) {
            tvEmptyAlerts.visibility = View.VISIBLE
            recyclerAlertHistory.visibility = View.GONE
        } else {
            tvEmptyAlerts.visibility = View.GONE
            recyclerAlertHistory.visibility = View.VISIBLE
            adapter.submitList(list)
        }
    }

    private fun render(state: UiState) {
        levels.removeAllViews()
        addLevel(AlertLevel.NORMAL, "Mực nước < ${number(state.warnLevelCm)} cm", state.level)
        addLevel(
            AlertLevel.HIGH,
            "${number(state.warnLevelCm)} – < ${number(state.safeLevelCm)} cm · Cần theo dõi",
            state.level
        )
        addLevel(
            AlertLevel.DANGER,
            "Mực nước ≥ ${number(state.safeLevelCm)} cm · Cần xử lý ngay",
            state.level
        )
        addLevel(AlertLevel.SENSOR_ERROR, "Không nhận được tín hiệu HC-SR04", state.level)
    }

    private fun addLevel(level: AlertLevel, description: String, current: AlertLevel) {
        val active = current == level || (current == AlertLevel.RECOVERING && level == AlertLevel.NORMAL)
        val color = ContextCompat.getColor(requireContext(), level.textColorRes)
        val bg = ContextCompat.getColor(requireContext(), level.bgColorRes)
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            val shape = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(bg)
                if (active) setStroke(dp(2), color)
            }
            background = shape
            setPadding(dp(12), dp(9), dp(10), dp(9))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        row.addView(TextView(requireContext()).apply {
            text = "●"
            textSize = 18f
            setTextColor(color)
        })
        row.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = level.label
                textSize = 16f
                setTextColor(color)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = description
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            })
        })
        if (active) {
            row.addView(TextView(requireContext()).apply {
                text = "HIỆN TẠI"
                textSize = 11f
                setTextColor(color)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setColor(Color.WHITE)
                }
                setPadding(dp(12), dp(7), dp(12), dp(7))
            })
        }
        levels.addView(row)
    }

    private fun number(value: Double) =
        if (value == value.toInt().toDouble()) value.toInt().toString() else String.format(Locale.getDefault(), "%.1f", value)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
