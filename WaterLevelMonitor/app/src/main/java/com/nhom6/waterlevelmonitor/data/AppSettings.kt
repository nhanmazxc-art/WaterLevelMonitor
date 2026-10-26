package com.nhom6.waterlevelmonitor.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Cấu hình lưu trên điện thoại. Mực nước h = H - d, với:
 *  H = độ cao của cảm biến so với đáy (mốc 0), d = khoảng cách cảm biến đo được.
 * Giai đoạn sau sẽ đồng bộ ngưỡng lên Firebase cho ESP32.
 */
object AppSettings {

    private const val PREFS = "waterlevel_settings"
    private const val KEY_HEIGHT = "sensor_height_cm"
    private const val KEY_WARN = "warn_level_cm"
    private const val KEY_SAFE = "safe_level_cm"
    private const val KEY_DEMO = "demo_mode"
    private const val KEY_CHART_INTERVAL = "chart_interval_seconds"
    private const val KEY_DISTANCE_UNIT = "distance_unit"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_LANGUAGE = "language"

    const val DEFAULT_WARN = 13.0
    const val DEFAULT_SAFE = 10.0

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    /** Độ cao cảm biến so với đáy. 0 nghĩa là chưa cài đặt. */
    val sensorHeightCm: Double
        get() = read(KEY_HEIGHT, 0.0)

    /** Mực nước >= giá trị này: CẢNH BÁO LŨ. */
    val warnLevelCm: Double
        get() = read(KEY_WARN, DEFAULT_WARN)

    /** Mực nước < giá trị này: AN TOÀN. Nằm giữa hai ngưỡng: nước dâng. */
    val safeLevelCm: Double
        get() = read(KEY_SAFE, DEFAULT_SAFE)

    var demoMode: Boolean
        get() = prefs?.getBoolean(KEY_DEMO, false) ?: false
        set(value) {
            prefs?.edit()?.putBoolean(KEY_DEMO, value)?.apply()
        }

    var chartIntervalSeconds: Int
        get() = prefs?.getInt(KEY_CHART_INTERVAL, 3) ?: 3
        set(value) { prefs?.edit()?.putInt(KEY_CHART_INTERVAL, value.coerceIn(1, 60))?.apply() }

    /** "cm" hoặc "m". Dữ liệu gốc luôn được lưu bằng cm. */
    var distanceUnit: String
        get() = prefs?.getString(KEY_DISTANCE_UNIT, "cm") ?: "cm"
        set(value) { prefs?.edit()?.putString(KEY_DISTANCE_UNIT, if (value == "m") "m" else "cm")?.apply() }

    var darkMode: Boolean
        get() = prefs?.getBoolean(KEY_DARK_MODE, false) ?: false
        set(value) { prefs?.edit()?.putBoolean(KEY_DARK_MODE, value)?.apply() }

    var language: String
        get() = prefs?.getString(KEY_LANGUAGE, "vi") ?: "vi"
        set(value) { prefs?.edit()?.putString(KEY_LANGUAGE, if (value == "en") "en" else "vi")?.apply() }

    fun save(heightCm: Double, warnCm: Double, safeCm: Double) {
        prefs?.edit()
            ?.putString(KEY_HEIGHT, heightCm.toString())
            ?.putString(KEY_WARN, warnCm.toString())
            ?.putString(KEY_SAFE, safeCm.toString())
            ?.apply()
    }

    /** Lưu hai ngưỡng khoảng cách d của HC-SR04; không phụ thuộc vào H. */
    fun saveDistanceThresholds(warnDistanceCm: Double, dangerDistanceCm: Double) {
        prefs?.edit()
            ?.putString(KEY_WARN, warnDistanceCm.toString())
            ?.putString(KEY_SAFE, dangerDistanceCm.toString())
            ?.apply()
    }

    private fun read(key: String, default: Double): Double =
        prefs?.getString(key, null)?.toDoubleOrNull() ?: default
}
