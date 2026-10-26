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
    NORMAL("An toàn", "Mực nước an toàn: Cửa mở, bơm tắt, còi tắt", R.color.green, R.color.green_bg),
    MID("Cảnh báo", "Nước dâng: Cửa mở, bơm tắt, còi kêu 3s/nghỉ 3s", R.color.yellow, R.color.yellow_bg),
    RECOVERING("Bơm hạ mức", "Bơm tiếp tục chạy cho đến khi về mức an toàn", R.color.green, R.color.green_bg),
    HIGH("Cảnh báo", "Nước dâng: Cửa mở, bơm tắt, còi kêu 3s/nghỉ 3s", R.color.yellow, R.color.yellow_bg),
    LOW("An toàn", "Mực nước an toàn: Cửa mở, bơm tắt, còi tắt", R.color.green, R.color.green_bg),
    DANGER("Nguy hiểm", "Nguy hiểm: Cửa đóng, còi 3s, sau đó bơm bật", R.color.red, R.color.red_bg),
    SENSOR_ERROR("Lỗi cảm biến", "Kiểm tra dây kết nối HC-SR04", R.color.orange, R.color.orange_bg),
    UNKNOWN("Chưa xác định", "Đang chờ dữ liệu từ thiết bị", R.color.text_secondary, R.color.gray_bg);

    companion object {

        /** Mực nước càng cao thì mức cảnh báo càng nghiêm trọng. */
        fun resolve(device: DeviceData?, levelCm: Double?, warnCm: Double, dangerCm: Double): AlertLevel {
            if (device == null) return UNKNOWN
            if (device.sensorOk == false) return SENSOR_ERROR
            if (levelCm != null) {
                return when {
                    levelCm >= dangerCm -> DANGER
                    levelCm >= warnCm -> HIGH
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
