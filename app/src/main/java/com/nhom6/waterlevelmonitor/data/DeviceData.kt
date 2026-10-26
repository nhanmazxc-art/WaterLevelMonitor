package com.nhom6.waterlevelmonitor.data

import com.google.firebase.database.DataSnapshot

/** Dữ liệu ESP32 ghi ở nhánh /mucnuoc (tên trường theo firmware hiện tại của nhóm). */
data class DeviceData(
    val sensorOk: Boolean?,      // camBienOK
    /** d = t × 0.0343 / 2, do ESP32 tính từ thời gian xung echo của HC-SR04. */
    val distanceCm: Double?,     // khoangCachCm (không có khi cảm biến lỗi)
    val mode: String?,           // cheDo
    val motorCommand: Boolean?,  // lenhMotor (firmware cũ có thể dùng lenhServo)
    val motorOn: Boolean?,       // servoState (firmware cũ có thể dùng motor)
    val lightOn: Boolean?,
    val alertOn: Boolean?,
    val pumpOn: Boolean?,
    val levelText: String?,      // mucNuoc
    val uptimeMs: Long?,         // thoiGianChayMs
    val wifiRssi: Int?           // wifiRSSI
) {
    companion object {
        fun fromSnapshot(snapshot: DataSnapshot): DeviceData? {
            if (!snapshot.exists()) return null
            return DeviceData(
                sensorOk = snapshot.child("camBienOK").bool(),
                distanceCm = snapshot.child("khoangCachCm").number()?.takeIf { it >= 0.0 },
                mode = snapshot.child("cheDo").text(),
                motorCommand = snapshot.firstBoolean("lenhMotor", "lenhServo"),
                motorOn = snapshot.firstBoolean("motor", "servoState"),
                lightOn = snapshot.firstBoolean("denCanhBao", "den", "light", "led"),
                // canhBao là mức cảnh báo tính toán, không phải trạng thái còi.
                alertOn = snapshot.firstBoolean("coi", "buzzer", "alert"),
                pumpOn = snapshot.firstBoolean("mayBom", "bom", "pump"),
                levelText = snapshot.child("mucNuoc").text(),
                uptimeMs = snapshot.child("thoiGianChayMs").number()?.toLong(),
                wifiRssi = snapshot.child("wifiRSSI").number()?.toInt()
            )
        }

        private fun DataSnapshot.bool(): Boolean? = value as? Boolean

        private fun DataSnapshot.firstBoolean(vararg names: String): Boolean? =
            names.asSequence().mapNotNull { child(it).bool() }.firstOrNull()

        private fun DataSnapshot.number(): Double? = (value as? Number)?.toDouble()

        private fun DataSnapshot.text(): String? = value as? String
    }
}
