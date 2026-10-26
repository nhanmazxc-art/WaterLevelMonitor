package com.nhom6.waterlevelmonitor.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.nhom6.waterlevelmonitor.R
import com.nhom6.waterlevelmonitor.data.DeviceRepository
import com.nhom6.waterlevelmonitor.data.AppSettings
import com.nhom6.waterlevelmonitor.data.UiState
import com.nhom6.waterlevelmonitor.data.banner
import java.util.Locale

class OverviewFragment : Fragment(R.layout.fragment_overview) {
    private lateinit var banner: TextView
    private lateinit var chip: TextView
    private lateinit var chart: ChartView
    private lateinit var ranges: MaterialButtonToggleGroup
    private lateinit var motor: MaterialSwitch
    private lateinit var light: MaterialSwitch
    private lateinit var buzzer: MaterialSwitch
    private lateinit var motorState: TextView
    private lateinit var lightState: TextView
    private lateinit var buzzerState: TextView
    private lateinit var lastUpdate: TextView
    private lateinit var modeGroup: MaterialButtonToggleGroup

    private var rangeMs = LIVE
    private var updating = false
    private var state: UiState? = null
    private val observer: (UiState) -> Unit = { render(it) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        banner = view.findViewById(R.id.tvBanner); chip = view.findViewById(R.id.tvLevelChip)
        chart = view.findViewById(R.id.chartView); ranges = view.findViewById(R.id.toggleRange)
        motor = view.findViewById(R.id.switchMotor); light = view.findViewById(R.id.switchLight); buzzer = view.findViewById(R.id.switchBuzzer)
        motorState = view.findViewById(R.id.tvMotorState); lastUpdate = view.findViewById(R.id.tvLastUpdate)
        lightState = view.findViewById(R.id.tvLightState); buzzerState = view.findViewById(R.id.tvBuzzerState)
        modeGroup = view.findViewById(R.id.toggleMode)

        // 1. Xử lý chọn khoảng thời gian biểu đồ
        ranges.addOnButtonCheckedListener { _, id, checked -> 
            if (checked && !updating) { 
                rangeMs = when (id) { R.id.btnRange10 -> TEN; R.id.btnRange60 -> HOUR; else -> LIVE }
                state?.let(::render) 
            } 
        }
        ranges.check(R.id.btnRangeLive)

        // 2. Xử lý CHUYỂN CHẾ ĐỘ (Dùng listener của Group thay vì từng nút)
        modeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked && !updating) {
                val mode = if (checkedId == R.id.btnModeManual) "MANUAL" else "AUTO"
                Log.d("OverviewFragment", "Người dùng chọn chế độ: $mode")
                DeviceRepository.sendMode(mode)
            }
        }

        // 3. Xử lý điều khiển Motor
        motor.setOnCheckedChangeListener { _, on -> 
            if (!updating) DeviceRepository.sendMotorCommand(on)
        }
        
        light.setOnCheckedChangeListener { _, on ->
            if (!updating) DeviceRepository.sendLightCommand(on)
        }
        buzzer.setOnCheckedChangeListener { _, on ->
            if (!updating) DeviceRepository.sendAlertCommand(on)
        }
    }

    override fun onStart() { super.onStart(); DeviceRepository.addObserver(observer) }
    override fun onStop() { DeviceRepository.removeObserver(observer); super.onStop() }

    private fun render(s: UiState) {
        state = s; val d = s.device
        bindOnlineChip(requireView(), s.online)
        bindBanner(banner, s.banner())
        bindLevelChip(chip, s.level, d?.levelText)
        
        val warning = if (s.sensorHeightCm > 0) (s.sensorHeightCm - s.warnLevelCm).coerceAtLeast(0.0) else null
        val now = System.currentTimeMillis()
        chart.setData(DeviceRepository.samplesSnapshot().filter { it.timeMs >= now - rangeMs }.map { ChartView.Point(it.timeMs, it.distanceCm) }, rangeMs, now, warning, "Khoảng cách an toàn", true, AppSettings.distanceUnit)
        
        updating = true // KHÓA để tránh tạo vòng lặp gửi lệnh khi set UI
        
        try {
            // Cập nhật trạng thái motor
            motor.isChecked = d?.motorOn == true
            motorState.text = if (d?.motorOn == true) "Đang mở" else "Đang đóng"
            light.isChecked = d?.lightOn == true
            lightState.text = if (d?.lightOn == true) "Đang bật" else "Đang tắt"
            buzzer.isChecked = d?.alertOn == true
            buzzerState.text = if (d?.alertOn == true) "Đang bật" else "Đang tắt"
            lastUpdate.text = s.lastUpdateMs?.let { "●  Cập nhật: ${formatTime(it)}" } ?: "●  Đang chờ dữ liệu..."
            
            // Cập nhật chế độ điều khiển
            // Không tự chọn AUTO khi ESP32 chưa trả về trường cheDo. Trước đây
            // nhánh mặc định này khiến lựa chọn THỦ CÔNG vừa bấm đã bị ghi đè.
            val mode = d?.mode?.uppercase(Locale.ROOT)
            val targetId = when (mode) {
                "MANUAL" -> R.id.btnModeManual
                "AUTO" -> R.id.btnModeAuto
                else -> View.NO_ID
            }
            if (targetId != View.NO_ID && modeGroup.checkedButtonId != targetId) {
                modeGroup.check(targetId)
            }
            
            // Chỉ cho phép gạt Switch motor khi ở chế độ THỦ CÔNG và thiết bị Online
            motor.isEnabled = s.online && mode == "MANUAL"
            light.isEnabled = s.online && mode == "MANUAL"
            buzzer.isEnabled = s.online && mode == "MANUAL"
            
        } catch (e: Exception) {
            Log.e("OverviewFragment", "Lỗi render: ${e.message}")
        } finally {
            updating = false // MỞ KHÓA sau khi vẽ xong
        }
    }

    companion object { const val LIVE=120_000L; const val TEN=600_000L; const val HOUR=3_600_000L }
}
