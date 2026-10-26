package com.nhom6.waterlevelmonitor.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.nhom6.waterlevelmonitor.LoginActivity
import com.nhom6.waterlevelmonitor.R
import com.nhom6.waterlevelmonitor.data.AppSettings
import com.nhom6.waterlevelmonitor.data.DeviceRepository
import com.nhom6.waterlevelmonitor.data.FirebaseManager
import com.nhom6.waterlevelmonitor.data.HistoryStore
import com.nhom6.waterlevelmonitor.data.OnlineTracker
import com.nhom6.waterlevelmonitor.data.UiState
import java.util.Locale

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var tvConnFirebase: TextView
    private lateinit var tvConnDevice: TextView
    private lateinit var tvConnWifi: TextView
    private lateinit var tvConnAccount: TextView
    private lateinit var btnQuickCheck: MaterialButton
    private lateinit var tilHeight: TextInputLayout
    private lateinit var tilWarn: TextInputLayout
    private lateinit var tilSafe: TextInputLayout
    private lateinit var etHeight: TextInputEditText
    private lateinit var etWarn: TextInputEditText
    private lateinit var etSafe: TextInputEditText
    private lateinit var btnSave: MaterialButton
    private lateinit var btnLogout: MaterialButton

    private var currentState: UiState? = null
    private var quickStatusDialog: AlertDialog? = null
    private var dialogView: View? = null

    private var updatingSwitch = false
    private lateinit var intervalGroup: MaterialButtonToggleGroup
    private lateinit var unitGroup: MaterialButtonToggleGroup
    private lateinit var switchDark: MaterialSwitch
    private val exportCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@registerForActivityResult
        try {
            requireContext().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { out ->
                val saved = HistoryStore.csv()
                if (saved != null) out.write(saved) else {
                    out.appendLine("Thời gian,Khoảng cách (cm),Mực nước (cm)")
                    DeviceRepository.samplesSnapshot().forEach { sample -> out.appendLine("${formatTime(sample.timeMs)},${sample.distanceCm},${sample.levelCm ?: ""}") }
                }
            }
            Toast.makeText(requireContext(), "Đã xuất dữ liệu CSV", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) { Toast.makeText(requireContext(), "Không thể xuất tệp CSV", Toast.LENGTH_SHORT).show() }
    }

    private val observer: (UiState) -> Unit = { 
        currentState = it
        render(it)
        if (quickStatusDialog?.isShowing == true && dialogView != null) {
            renderQuickStatusDialog(it, dialogView!!)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvConnFirebase = view.findViewById(R.id.tvConnFirebase)
        tvConnDevice = view.findViewById(R.id.tvConnDevice)
        tvConnWifi = view.findViewById(R.id.tvConnWifi)
        tvConnAccount = view.findViewById(R.id.tvConnAccount)
        btnQuickCheck = view.findViewById(R.id.btnQuickCheck)
        tilHeight = view.findViewById(R.id.tilHeight)
        tilWarn = view.findViewById(R.id.tilOn)
        tilSafe = view.findViewById(R.id.tilOff)
        etHeight = view.findViewById(R.id.etSensorHeight)
        etWarn = view.findViewById(R.id.etThresholdOn)
        etSafe = view.findViewById(R.id.etThresholdOff)
        btnSave = view.findViewById(R.id.btnSaveConfig)
        btnLogout = view.findViewById(R.id.btnLogout)
        intervalGroup = view.findViewById(R.id.toggleChartInterval)
        unitGroup = view.findViewById(R.id.toggleDistanceUnit)
        switchDark = view.findViewById(R.id.switchDarkMode)

        etHeight.setText(numberText(AppSettings.sensorHeightCm))
        etWarn.setText(numberText(AppSettings.warnLevelCm))
        etSafe.setText(numberText(AppSettings.dangerLevelCm))

        btnSave.setOnClickListener { saveConfig() }
        btnLogout.setOnClickListener { logout() }
        btnQuickCheck.setOnClickListener { showQuickStatusDialog() }

        intervalGroup.check(when (AppSettings.chartIntervalSeconds) { 5 -> R.id.btnInterval5; 10 -> R.id.btnInterval10; else -> R.id.btnInterval3 })
        intervalGroup.addOnButtonCheckedListener { _, id, checked -> if (checked) AppSettings.chartIntervalSeconds = when (id) { R.id.btnInterval5 -> 5; R.id.btnInterval10 -> 10; else -> 3 } }
        unitGroup.check(if (AppSettings.distanceUnit == "m") R.id.btnUnitM else R.id.btnUnitCm)
        unitGroup.addOnButtonCheckedListener { _, id, checked -> if (checked) { AppSettings.distanceUnit = if (id == R.id.btnUnitM) "m" else "cm"; DeviceRepository.refresh() } }
        switchDark.isChecked = AppSettings.darkMode
        switchDark.setOnCheckedChangeListener { _, checked -> AppSettings.darkMode = checked; AppCompatDelegate.setDefaultNightMode(if (checked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO) }
        view.findViewById<MaterialButton>(R.id.btnExportCsv).setOnClickListener { exportCsv.launch("muc-nuoc-${System.currentTimeMillis()}.csv") }
        view.findViewById<MaterialButton>(R.id.btnClearHistory).setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle("Xóa lịch sử thống kê?").setMessage("Dữ liệu đã ghi trong phiên hiện tại sẽ bị xóa.").setNegativeButton("Hủy", null).setPositiveButton("Xóa") { _, _ -> DeviceRepository.clearHistory(); Toast.makeText(requireContext(), "Đã xóa lịch sử", Toast.LENGTH_SHORT).show() }.show()
        }
    }

    override fun onStart() {
        super.onStart()
        DeviceRepository.addObserver(observer)
    }

    override fun onStop() {
        super.onStop()
        DeviceRepository.removeObserver(observer)
    }

    override fun onDestroyView() {
        quickStatusDialog?.dismiss()
        quickStatusDialog = null
        dialogView = null
        super.onDestroyView()
    }

    private fun showQuickStatusDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_quick_status, null)
        dialogView = view

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        currentState?.let { renderQuickStatusDialog(it, view) } ?: run {
            view.findViewById<TextView>(R.id.tvDialogWaterLevel).text = "--"
            view.findViewById<TextView>(R.id.tvDialogStatusHint).text = "Đang chờ dữ liệu từ thiết bị…"
        }

        view.findViewById<MaterialButton>(R.id.btnDialogRefresh).setOnClickListener {
            DeviceRepository.refresh()
            Toast.makeText(requireContext(), "Đang làm mới dữ liệu...", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<MaterialButton>(R.id.btnDialogClose).setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            if (quickStatusDialog == dialog) {
                quickStatusDialog = null
                dialogView = null
            }
        }

        quickStatusDialog = dialog
        dialog.show()
    }

    private fun renderQuickStatusDialog(s: UiState, view: View) {
        val tvLevel = view.findViewById<TextView>(R.id.tvDialogWaterLevel)
        val tvChip = view.findViewById<TextView>(R.id.tvDialogLevelChip)
        val tvHint = view.findViewById<TextView>(R.id.tvDialogStatusHint)
        val tvDist = view.findViewById<TextView>(R.id.tvDialogDistance)
        val tvHeight = view.findViewById<TextView>(R.id.tvDialogSensorHeight)
        val tvSensorOk = view.findViewById<TextView>(R.id.tvDialogSensorOk)
        val tvDeviceStatus = view.findViewById<TextView>(R.id.tvDialogDeviceStatus)
        val tvLastUpdate = view.findViewById<TextView>(R.id.tvDialogLastUpdate)

        val isMeter = AppSettings.distanceUnit == "m"

        // 1. Mực nước
        if (s.levelCm != null) {
            tvLevel.text = if (isMeter) {
                String.format(Locale.getDefault(), "%.2f m", s.levelCm / 100.0)
            } else {
                String.format(Locale.getDefault(), "%.1f cm", s.levelCm)
            }
        } else {
            tvLevel.text = "--"
        }

        // 2. Chip cảnh báo & gợi ý
        bindLevelChip(tvChip, s.level, s.device?.levelText)
        tvHint.text = when {
            s.device?.sensorOk == false -> "Cảm biến siêu âm báo lỗi, hãy kiểm tra dây HC-SR04."
            !s.online -> "Thiết bị đang Offline hoặc mất kết nối mạng."
            s.levelCm == null && s.sensorHeightCm <= 0 -> "Chưa cài đặt chiều cao cảm biến (H) ở mục bên dưới."
            else -> s.level.hint
        }

        // 3. Khoảng cách đo được d
        val dist = s.device?.distanceCm
        tvDist.text = if (dist != null) {
            if (isMeter) String.format(Locale.getDefault(), "%.2f m", dist / 100.0)
            else String.format(Locale.getDefault(), "%.1f cm", dist)
        } else "--"

        // 4. Chiều cao cảm biến H
        tvHeight.text = if (s.sensorHeightCm > 0) {
            if (isMeter) String.format(Locale.getDefault(), "%.2f m", s.sensorHeightCm / 100.0)
            else String.format(Locale.getDefault(), "%.1f cm", s.sensorHeightCm)
        } else "Chưa cài đặt"

        // 5. Cảm biến siêu âm
        when (s.device?.sensorOk) {
            true -> setValue(tvSensorOk, "Hoạt động tốt", R.color.green)
            false -> setValue(tvSensorOk, "Lỗi cảm biến", R.color.red)
            null -> setValue(tvSensorOk, "--", R.color.text_secondary)
        }

        // 6. ESP32 & Wi-Fi
        val wifiText = s.device?.wifiRssi?.let { " ($it dBm)" } ?: ""
        when {
            s.demo -> setValue(tvDeviceStatus, "Giả lập demo", R.color.text_secondary)
            s.status == OnlineTracker.Status.ONLINE -> setValue(tvDeviceStatus, "Online$wifiText", R.color.green)
            s.status == OnlineTracker.Status.OFFLINE -> setValue(tvDeviceStatus, "Offline", R.color.red)
            else -> setValue(tvDeviceStatus, "Đang kiểm tra…", R.color.text_secondary)
        }

        // 7. Cập nhật gần nhất
        tvLastUpdate.text = s.lastUpdateMs?.let { formatTime(it) } ?: "--"
    }

    private fun render(s: UiState) {
        bindOnlineChip(requireView(), s.online)

        when {
            s.demo -> setValue(tvConnFirebase, "Trình diễn (không dùng Firebase)", R.color.text_secondary)
            s.firebaseConnected == true -> setValue(tvConnFirebase, "Đã kết nối", R.color.green)
            s.firebaseConnected == false -> setValue(tvConnFirebase, "Mất kết nối", R.color.red)
            else -> setValue(tvConnFirebase, "Đang kết nối…", R.color.text_secondary)
        }

        when {
            s.demo -> setValue(tvConnDevice, "Dữ liệu giả lập", R.color.text_secondary)
            s.status == OnlineTracker.Status.ONLINE -> setValue(tvConnDevice, "Online", R.color.green)
            s.status == OnlineTracker.Status.OFFLINE -> setValue(tvConnDevice, "Offline", R.color.red)
            else -> setValue(tvConnDevice, "Đang kiểm tra…", R.color.text_secondary)
        }

        val rssi = s.device?.wifiRssi
        tvConnWifi.text = if (rssi == null) {
            "--"
        } else {
            val quality = when {
                rssi >= -60 -> "tốt"
                rssi >= -75 -> "trung bình"
                else -> "yếu"
            }
            "$rssi dBm ($quality)"
        }

        tvConnAccount.text = FirebaseManager.auth.currentUser?.email ?: "--"
    }

    private fun saveConfig() {
        val height = parse(etHeight)
        val warn = parse(etWarn)
        val safe = parse(etSafe)
        tilHeight.error = null
        tilWarn.error = null
        tilSafe.error = null

        var valid = true
        if (height == null || height <= 0.0) { tilHeight.error = "Nhập chiều cao lớn hơn 0"; valid = false }
        if (warn == null || warn <= 0.0) {
            tilWarn.error = "Nhập số lớn hơn 0"
            valid = false
        }
        if (safe == null || safe <= 0.0) {
            tilSafe.error = "Nhập số lớn hơn 0"
            valid = false
        }
        if (!valid || height == null || warn == null || safe == null) return

        if (safe <= warn) {
            tilSafe.error = "Phải cao hơn ngưỡng cảnh báo"
            return
        }
        if (safe > height) {
            tilSafe.error = "Không được cao hơn chiều cao cảm biến"
            return
        }
        AppSettings.save(height, warn, safe)
        DeviceRepository.refresh()
        btnSave.isEnabled = false
        FirebaseManager.saveDeviceConfig(height, warn, safe)
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                btnSave.isEnabled = true
                Toast.makeText(
                    requireContext(),
                    "Đã lưu và gửi cấu hình cho ESP32",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                btnSave.isEnabled = true
                Toast.makeText(
                    requireContext(),
                    "Đã lưu trên điện thoại, chưa gửi được lên Firebase",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun logout() {
        FirebaseManager.auth.signOut()
        DeviceRepository.reset()
        startActivity(
            Intent(requireContext(), LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        requireActivity().finish()
    }

    private fun setValue(view: TextView, text: String, colorRes: Int) {
        view.text = text
        view.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
    }

    private fun parse(edit: TextInputEditText): Double? =
        edit.text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull()

    private fun numberText(value: Double, blankIfZero: Boolean = false): String = when {
        blankIfZero && value == 0.0 -> ""
        value == value.toInt().toDouble() -> value.toInt().toString()
        else -> value.toString()
    }
}
