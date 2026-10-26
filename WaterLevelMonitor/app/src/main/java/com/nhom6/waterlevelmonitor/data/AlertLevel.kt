package com.nhom6.waterlevelmonitor.data

import com.nhom6.waterlevelmonitor.R
import java.text.Normalizer
import java.util.Locale

enum class AlertLevel(
    val label: String,
    val hint: String,
    val textColorRes: Int,
    val bgColorRes: Int
) {
    NORMAL("An toàn", "Khoảng cách đến mặt nước đang an toàn", R.color.green, R.color.green_bg),
    MID("Cảnh báo", "Nước đang dâng, cần theo dõi", R.color.yellow, R.color.yellow_bg),
    RECOVERING("An toàn", "Mực nước đang hạ sau cảnh báo lũ", R.color.green, R.color.green_bg),
    HIGH("Cảnh báo", "Khoảng cách đến mặt nước đã chạm ngưỡng cảnh báo", R.color.yellow, R.color.yellow_bg),
    LOW("An toàn", "Nước đang xuống thấp", R.color.green, R.color.green_bg),
    DANGER("Nguy hiểm", "Cần xử lý ngay", R.color.red, R.color.red_bg),
    SENSOR_ERROR("Lỗi cảm biến", "Kiểm tra dây HC-SR04", R.color.orange, R.color.orange_bg),
    UNKNOWN("Chưa xác định", "Chưa có dữ liệu", R.color.text_secondary, R.color.gray_bg);

    companion object {

        /**
         * Hai ngưỡng được so sánh trực tiếp với khoảng cách d HC-SR04 đo được.
         */
        fun resolve(device: DeviceData?, distanceCm: Double?, warnCm: Double, dangerCm: Double): AlertLevel {
            if (device == null) return UNKNOWN
            if (device.sensorOk == false) return SENSOR_ERROR
            if (distanceCm != null) {
                return when {
                    distanceCm <= dangerCm -> DANGER
                    distanceCm <= warnCm -> HIGH
                    else -> NORMAL
                }
            }
            return fromText(device.levelText)
        }

        /** Suy ra mức từ chữ ESP32 gửi: CAO, AN TOAN, TRUNG GIAN, LOI CAM BIEN. */
        fun fromText(levelText: String?): AlertLevel {
            val t = normalize(levelText) ?: return UNKNOWN
            return when {
                "LOI" in t -> SENSOR_ERROR
                "NGUY" in t -> DANGER
                "TRUNG GIAN" in t -> MID
                "CAO" in t -> HIGH
                "THAP" in t -> LOW
                "AN TOAN" in t || "BINH THUONG" in t -> NORMAL
                else -> UNKNOWN
            }
        }

        private fun normalize(text: String?): String? {
            if (text.isNullOrBlank()) return null
            return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .uppercase(Locale.ROOT)
                .trim()
        }
    }
}
