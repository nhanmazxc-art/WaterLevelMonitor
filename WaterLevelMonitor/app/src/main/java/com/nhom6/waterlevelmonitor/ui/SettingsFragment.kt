package com.nhom6.waterlevelmonitor.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.nhom6.waterlevelmonitor.LoginActivity
import com.nhom6.waterlevelmonitor.R
import com.nhom6.waterlevelmonitor.data.AppSettings
import com.nhom6.waterlevelmonitor.data.DeviceRepository
import com.nhom6.waterlevelmonitor.data.FirebaseManager
import com.nhom6.waterlevelmonitor.data.OnlineTracker
import com.nhom6.waterlevelmonitor.data.UiState

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var tvConnFirebase: TextView
    private lateinit var tvConnDevice: TextView
    private lateinit var tvConnWifi: TextView
    private lateinit var tvConnAccount: TextView
    private lateinit var tilWarn: TextInputLayout
    private lateinit var tilSafe: TextInputLayout
    private lateinit var etWarn: TextInputEditText
    private lateinit var etSafe: TextInputEditText
    private lateinit var switchDemo: MaterialSwitch
    private lateinit var btnSave: MaterialButton
    private lateinit var btnLogout: MaterialButton

    private var updatingSwitch = false
    private lateinit var intervalGroup: MaterialButtonToggleGroup
    private lateinit var unitGroup: MaterialButtonToggleGroup
    private lateinit var languageGroup: MaterialButtonToggleGroup
    private lateinit var switchDark: MaterialSwitch
    private val exportCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@registerForActivityResult
        try {
            requireContext().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { out ->
                out.appendLine("Thời gian,Khoảng cách (cm),Mực nước (cm)")
                DeviceRepository.samplesSnapshot().forEach { sample ->
                    out.appendLine("${formatTime(sample.timeMs)},${sample.distanceCm},${sample.levelCm ?: ""}")
                }
            }
            Toast.makeText(requireContext(), "Đã xuất dữ liệu CSV", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) { Toast.makeText(requireContext(), "Không thể xuất tệp CSV", Toast.LENGTH_SHORT).show() }
    }

    private val observer: (UiState) -> Unit = { render(it) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvConnFirebase = view.findViewById(R.id.tvConnFirebase)
        tvConnDevice = view.findViewById(R.id.tvConnDevice)
        tvConnWifi = view.findViewById(R.id.tvConnWifi)
        tvConnAccount = view.findViewById(R.id.tvConnAccount)
        tilWarn = view.findViewById(R.id.tilOn)
        tilSafe = view.findViewById(R.id.tilOff)
        etWarn = view.findViewById(R.id.etThresholdOn)
        etSafe = view.findViewById(R.id.etThresholdOff)
        switchDemo = view.findViewById(R.id.switchDemo)
        btnSave = view.findViewById(R.id.btnSaveConfig)
        btnLogout = view.findViewById(R.id.btnLogout)
        intervalGroup = view.findViewById(R.id.toggleChartInterval)
        unitGroup = view.findViewById(R.id.toggleDistanceUnit)
        languageGroup = view.findViewById(R.id.toggleLanguage)
        switchDark = view.findViewById(R.id.switchDarkMode)

        etWarn.setText(numberText(AppSettings.warnLevelCm))
        etSafe.setText(numberText(AppSettings.safeLevelCm))

        updatingSwitch = true
        switchDemo.isChecked = AppSettings.demoMode
        updatingSwitch = false
        switchDemo.setOnCheckedChangeListener { _, checked ->
            if (updatingSwitch) return@setOnCheckedChangeListener
            AppSettings.demoMode = checked
            DeviceRepository.restart()
            Toast.makeText(
                requireContext(),
                if (checked) "Đã bật chế độ trình diễn" else "Đã tắt chế độ trình diễn",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnSave.setOnClickListener { saveConfig() }
        btnLogout.setOnClickListener { logout() }

        intervalGroup.check(when (AppSettings.chartIntervalSeconds) { 5 -> R.id.btnInterval5; 10 -> R.id.btnInterval10; else -> R.id.btnInterval3 })
        intervalGroup.addOnButtonCheckedListener { _, id, checked -> if (checked) AppSettings.chartIntervalSeconds = when (id) { R.id.btnInterval5 -> 5; R.id.btnInterval10 -> 10; else -> 3 } }
        unitGroup.check(if (AppSettings.distanceUnit == "m") R.id.btnUnitM else R.id.btnUnitCm)
        unitGroup.addOnButtonCheckedListener { _, id, checked -> if (checked) { AppSettings.distanceUnit = if (id == R.id.btnUnitM) "m" else "cm"; DeviceRepository.refresh() } }
        switchDark.isChecked = AppSettings.darkMode
        switchDark.setOnCheckedChangeListener { _, checked -> AppSettings.darkMode = checked; AppCompatDelegate.setDefaultNightMode(if (checked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO) }
        languageGroup.check(if (AppSettings.language == "en") R.id.btnLanguageEn else R.id.btnLanguageVi)
        languageGroup.addOnButtonCheckedListener { _, id, checked -> if (checked) { val language = if (id == R.id.btnLanguageEn) "en" else "vi"; AppSettings.language = language; AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language)) } }
        view.findViewById<MaterialButton>(R.id.btnExportCsv).setOnClickListener { exportCsv.launch("water-level-${System.currentTimeMillis()}.csv") }
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
        val warn = parse(etWarn)
        val safe = parse(etSafe)
        tilWarn.error = null
        tilSafe.error = null

        var valid = true
        if (warn == null || warn <= 0.0) {
            tilWarn.error = "Nhập số lớn hơn 0"
            valid = false
        }
        if (safe == null || safe <= 0.0) {
            tilSafe.error = "Nhập số lớn hơn 0"
            valid = false
        }
        if (!valid || warn == null || safe == null) return

        if (safe >= warn) {
            tilSafe.error = "Phải nhỏ hơn ngưỡng cảnh báo"
            return
        }

        AppSettings.saveDistanceThresholds(warn, safe)
        DeviceRepository.refresh()
        Toast.makeText(requireContext(), "Đã lưu cấu hình", Toast.LENGTH_SHORT).show()
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
