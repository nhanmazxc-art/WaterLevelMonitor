package com.nhom6.waterlevelmonitor.data

/** Toàn bộ trạng thái mà các màn hình cần để vẽ giao diện. */
data class UiState(
    val loading: Boolean,
    val errorMessage: String?,
    val device: DeviceData?,
    val firebaseConnected: Boolean?,
    val status: OnlineTracker.Status,
    val online: Boolean,
    val lastUpdateMs: Long?,
    val demo: Boolean,
    val level: AlertLevel,
    /** Mực nước h = H - d (cm). null nếu chưa có khoảng cách hoặc chưa cài H. */
    val levelCm: Double?,
    val sensorHeightCm: Double,
    val warnLevelCm: Double,
    val safeLevelCm: Double,
    /** Lệnh motor (true = BẬT) đã gửi nhưng ESP32 chưa xác nhận. */
    val pendingCommand: Boolean?
)

/** Dòng thông báo màu cam ở đầu màn hình. Trả về null khi mọi thứ bình thường. */
fun UiState.banner(): String? {
    val d = device
    return when {
        demo -> "Chế độ trình diễn: đây là dữ liệu giả lập, không phải từ thiết bị thật."
        firebaseConnected == false ->
            "Chưa kết nối được Firebase. Hãy kiểm tra Internet của điện thoại."
        errorMessage != null -> errorMessage
        loading -> "Đang tải dữ liệu…"
        d == null ->
            "Chưa có dữ liệu ở nhánh \"mucnuoc\". Hãy kiểm tra ESP32 đã gửi dữ liệu lên Firebase chưa."
        d.uptimeMs == null ->
            "Thiết bị chưa gửi thoiGianChayMs nên chưa xác định được Online hay Offline."
        status == OnlineTracker.Status.CHECKING -> "Đang kiểm tra thiết bị…"
        status == OnlineTracker.Status.OFFLINE ->
            "Thiết bị Offline: dữ liệu không đổi trong ${OnlineTracker.STALE_AFTER_MS / 1000} giây. " +
                    "ESP32 có thể đang tắt hoặc mất Wi-Fi."
        d.sensorOk == false ->
            "Thiết bị Online nhưng cảm biến siêu âm báo lỗi. Hãy kiểm tra dây HC-SR04."
        else -> null
    }
}