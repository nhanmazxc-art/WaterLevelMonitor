package com.nhom6.waterlevelmonitor.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.nhom6.waterlevelmonitor.R
import com.nhom6.waterlevelmonitor.data.AlertLevel
import com.nhom6.waterlevelmonitor.data.AppSettings
import com.nhom6.waterlevelmonitor.data.DeviceRepository
import com.nhom6.waterlevelmonitor.data.UiState
import com.nhom6.waterlevelmonitor.data.banner
import java.util.Locale

class OverviewFragment : Fragment(R.layout.fragment_overview) {
    private lateinit var banner: TextView
    private lateinit var chip: TextView
    private lateinit var chart: ChartView
    private lateinit var motor: MaterialSwitch
    private lateinit var buzzer: MaterialSwitch
    private lateinit var pump: MaterialSwitch
    private lateinit var motorState: TextView
    private lateinit var buzzerState: TextView
    private lateinit var pumpState: TextView
    private lateinit var lastUpdate: TextView
    private lateinit var modeHint: TextView
    private lateinit var modeGroup: MaterialButtonToggleGroup

    private var updating = false
    private var state: UiState? = null
    private val observer: (UiState) -> Unit = { render(it) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        banner = view.findViewById(R.id.tvBanner)
        chip = view.findViewById(R.id.tvLevelChip)
        chart = view.findViewById(R.id.chartView)
        motor = view.findViewById(R.id.switchMotor)
        buzzer = view.findViewById(R.id.switchBuzzer)
        pump = view.findViewById(R.id.switchPump)
        motorState = view.findViewById(R.id.tvMotorState)
        buzzerState = view.findViewById(R.id.tvBuzzerState)
        pumpState = view.findViewById(R.id.tvPumpState)
        lastUpdate = view.findViewById(R.id.tvLastUpdate)
        modeHint = view.findViewById(R.id.tvModeHint)
        modeGroup = view.findViewById(R.id.toggleMode)

        // Xử lý CHUYỂN CHẾ ĐỘ
        modeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked && !updating) {
                val mode = if (checkedId == R.id.btnModeManual) "MANUAL" else "AUTO"
                Log.d("OverviewFragment", "Người dùng chọn chế độ: $mode")
                DeviceRepository.sendMode(mode)
            }
        }

        // Xử lý điều khiển thiết bị khi ở chế độ Thủ công
        motor.setOnCheckedChangeListener { _, on ->
            if (!updating) DeviceRepository.sendMotorCommand(on)
        }

        buzzer.setOnCheckedChangeListener { _, on ->
            if (!updating) DeviceRepository.sendAlertCommand(on)
        }

        pump.setOnCheckedChangeListener { _, on ->
            if (!updating) DeviceRepository.sendPumpCommand(on)
        }
    }

    override fun onStart() {
        super.onStart()
        DeviceRepository.addObserver(observer)
    }

    override fun onStop() {
        DeviceRepository.removeObserver(observer)
        super.onStop()
    }

    private fun render(s: UiState) {
        state = s
        val d = s.device
        bindOnlineChip(requireView(), s.online)
        bindBanner(banner, s.banner())
        bindLevelChip(chip, s.level, d?.levelText)

        val warning = s.warnLevelCm.takeIf { s.sensorHeightCm > 0 }
        val now = System.currentTimeMillis()
        chart.setData(
            DeviceRepository.samplesSnapshot().mapNotNull { it.levelCm?.let { level -> ChartView.Point(it.timeMs, level) } },
            LIVE, now, warning, "Mực nước", false, AppSettings.distanceUnit
        )

        updating = true // KHÓA để tránh tạo vòng lặp gửi lệnh khi set UI

        try {
            val mode = d?.mode?.uppercase(Locale.ROOT) ?: "AUTO"
            val isManual = mode == "MANUAL"

            // 1. Cửa ngăn lũ (Servo) - Khởi động mặc định mở (motor = true)
            val isMotorOn = d?.motorOn ?: true
            motor.isChecked = isMotorOn
            if (isMotorOn) {
                motorState.text = "Đang mở"
                motorState.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
            } else {
                motorState.text = "Đang đóng"
                motorState.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
            }

            // 2. Còi báo
            val isBuzzerOn = d?.alertOn == true
            buzzer.isChecked = isBuzzerOn
            if (isBuzzerOn) {
                buzzerState.text = "Đang kêu"
                buzzerState.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
            } else {
                buzzerState.text = "Đang tắt"
                buzzerState.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            }

            // 3. Máy bơm - Chạy ở nguy hiểm và giữ chạy hạ mức đến khi về an toàn
            val isPumpOn = d?.pumpOn == true
            pump.isChecked = isPumpOn
            if (isPumpOn) {
                val isHaMuc = !isManual && s.level != AlertLevel.DANGER
                pumpState.text = if (isHaMuc) "Đang hạ mức" else "Đang bơm"
                pumpState.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
            } else {
                pumpState.text = "Đang tắt"
                pumpState.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            }

            lastUpdate.text = s.lastUpdateMs?.let { "●  Cập nhật: ${formatTime(it)}" } ?: "●  Đang chờ dữ liệu..."

            // Cập nhật nút chọn chế độ
            val targetId = if (isManual) R.id.btnModeManual else R.id.btnModeAuto
            if (modeGroup.checkedButtonId != targetId) {
                modeGroup.check(targetId)
            }

            // Chỉ cho phép gạt Switch khi ở chế độ THỦ CÔNG và thiết bị Online
            motor.isEnabled = s.online && isManual
            buzzer.isEnabled = s.online && isManual
            pump.isEnabled = s.online && isManual

            // Dòng hướng dẫn logic trực quan
            if (isManual) {
                modeHint.text = "Đang ở chế độ THỦ CÔNG: Bạn có thể tự gạt công tắc để đóng/mở cửa, bật/tắt còi và bơm."
                modeHint.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
            } else {
                modeHint.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                val statusText = when (s.level) {
                    AlertLevel.DANGER -> "Mực NGUY HIỂM: Cửa đóng, bơm bật thoát nước."
                    AlertLevel.HIGH, AlertLevel.MID -> {
                        if (isPumpOn) "Mực CẢNH BÁO: Bơm tiếp tục chạy hạ mức về An toàn."
                        else "Mực CẢNH BÁO: Cửa mở, còi chu kỳ 3s, bơm tắt."
                    }
                    AlertLevel.RECOVERING -> "Mực HỒI PHỤC: Đang bơm hạ mức nước về An toàn."
                    AlertLevel.SENSOR_ERROR -> "Cảm biến lỗi: Tự động ngắt còi và bơm để an toàn."
                    else -> "Mực AN TOÀN: Cửa mở, bơm tắt, còi tắt."
                }
                modeHint.text = "TỰ ĐỘNG · $statusText\n(Nhấn 'THỦ CÔNG' ở trên để mở khóa công tắc tự điều khiển)"
            }

        } catch (e: Exception) {
            Log.e("OverviewFragment", "Lỗi render: ${e.message}")
        } finally {
            updating = false // MỞ KHÓA sau khi vẽ xong
        }
    }

    companion object {
        const val LIVE = 120_000L
    }
}
