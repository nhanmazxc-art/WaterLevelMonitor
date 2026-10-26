package com.nhom6.waterlevelmonitor.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.android.gms.tasks.Task

/**
 * Nơi duy nhất giữ các đường dẫn Firebase
 * để Android và ESP32 không bị lệch tên.
 */
object FirebaseManager {

    // =========================
    // Firebase Auth
    // =========================

    val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()


    // =========================
    // Firebase Database
    // =========================

    private val database: FirebaseDatabase
        get() = FirebaseDatabase.getInstance()


    // =========================
    // Device
    // =========================

    val deviceRef: DatabaseReference
        get() = database.getReference("mucnuoc")


    // =========================
    // Firebase connection
    // =========================

    /**
     * true khi app đang nối được tới máy chủ Firebase.
     */
    val connectedRef: DatabaseReference
        get() = database.getReference(".info/connected")


    /**
     * Độ lệch giữa đồng hồ điện thoại
     * và giờ máy chủ Firebase (mili giây).
     */
    val serverOffsetRef: DatabaseReference
        get() = database.getReference(".info/serverTimeOffset")


    // =========================
    // Alert
    // =========================

    val alertsRef: DatabaseReference
        get() = database.getReference("alerts")


    // =========================
    // Notification settings
    // =========================

    val notificationSettingsRef: DatabaseReference
        get() = database.getReference("notificationSettings")


    // =========================
    // Alert rules
    // =========================

    val alertRulesRef: DatabaseReference
        get() = database.getReference("alertRules")


    // =========================
    // Device configuration
    // =========================

    /**
     * Đồng bộ cấu hình mà firmware ESP32 đọc tại /mucnuoc.
     *
     * Các giá trị tính theo cm.
     */
    fun saveDeviceConfig(
        sensorHeightCm: Double,
        warningThresholdCm: Double,
        dangerThresholdCm: Double
    ): Task<Void> {

        return deviceRef.updateChildren(
            mapOf(
                "chieuCaoCamBien" to sensorHeightCm,
                "nguongCanhBao" to warningThresholdCm,
                "nguongNguyHiem" to dangerThresholdCm,
                "cauHinhCapNhatLuc" to ServerValue.TIMESTAMP
            )
        )
    }
}