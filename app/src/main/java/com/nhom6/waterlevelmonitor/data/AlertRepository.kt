package com.nhom6.waterlevelmonitor.data

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener

object AlertRepository {

    private val alertsRef: DatabaseReference =
        FirebaseManager.alertsRef

    private val notificationSettingsRef: DatabaseReference =
        FirebaseManager.notificationSettingsRef

    private val alertRulesRef: DatabaseReference =
        FirebaseManager.alertRulesRef

    private val alertCache = mutableListOf<AlertLogEntry>()

    init {
        alertsRef.addValueEventListener(object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<AlertLogEntry>()

                snapshot.children.forEach { child ->
                    val entry = child.getValue(AlertLogEntry::class.java)

                    if (entry != null) {
                        list.add(entry)
                    }
                }

                alertCache.clear()
                alertCache.addAll(
                    list.sortedByDescending { it.timestamp }
                )
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(
                    "AlertRepository",
                    "Failed to listen alerts: ${error.message}"
                )
            }
        })
    }

    // =========================================================
    // THÊM LOG CẢNH BÁO
    // =========================================================

    fun addAlert(entry: AlertLogEntry) {

        val key = if (entry.id.isNotBlank()) {
            entry.id
        } else {
            alertsRef.push().key ?: return
        }

        alertsRef
            .child(key)
            .setValue(entry)
    }

    // =========================================================
    // LẤY DANH SÁCH LOG
    // =========================================================

    fun getAlertLog(): List<AlertLogEntry> {
        return alertCache.toList()
    }

    // =========================================================
    // LẮNG NGHE LOG CẢNH BÁO
    // =========================================================

    fun listenAlertLog(
        onDataChanged: (List<AlertLogEntry>) -> Unit
    ) {

        alertsRef.addValueEventListener(
            object : ValueEventListener {

                override fun onDataChange(
                    snapshot: DataSnapshot
                ) {

                    val list = mutableListOf<AlertLogEntry>()

                    snapshot.children.forEach { child ->

                        val entry =
                            child.getValue(AlertLogEntry::class.java)

                        if (entry != null) {
                            list.add(entry)
                        }
                    }

                    val sortedList =
                        list.sortedByDescending { it.timestamp }

                    alertCache.clear()
                    alertCache.addAll(sortedList)

                    onDataChanged(sortedList)
                }

                override fun onCancelled(
                    error: DatabaseError
                ) {

                    Log.w(
                        "AlertRepository",
                        "listenAlertLog error: ${error.message}"
                    )
                }
            }
        )
    }

    // =========================================================
    // XÓA TẤT CẢ LOG CẢNH BÁO
    // =========================================================

    fun clearAllAlerts() {
        alertsRef.removeValue()
        alertCache.clear()
    }

    // =========================================================
    // CÀI ĐẶT THÔNG BÁO
    // =========================================================

    fun setPushEnabled(enabled: Boolean) {

        notificationSettingsRef
            .child("pushEnabled")
            .setValue(enabled)
    }

    fun setSoundEnabled(enabled: Boolean) {

        notificationSettingsRef
            .child("soundEnabled")
            .setValue(enabled)
    }

    fun setHardwareErrorNotify(enabled: Boolean) {

        notificationSettingsRef
            .child("hardwareErrorEnabled")
            .setValue(enabled)
    }

    // =========================================================
    // THỜI GIAN LẶP CẢNH BÁO
    // =========================================================

    fun setRepeatInterval(minutes: Int) {

        alertRulesRef
            .child("repeatIntervalMinutes")
            .setValue(minutes)
    }

    // =========================================================
    // KHÔNG LÀM PHIỀN - BẮT ĐẦU
    // =========================================================

    fun setDndStart(
        hour: Int,
        minute: Int
    ) {

        alertRulesRef
            .child("dndStartHour")
            .setValue(hour)

        alertRulesRef
            .child("dndStartMinute")
            .setValue(minute)
    }

    // =========================================================
    // KHÔNG LÀM PHIỀN - KẾT THÚC
    // =========================================================

    fun setDndEnd(
        hour: Int,
        minute: Int
    ) {

        alertRulesRef
            .child("dndEndHour")
            .setValue(hour)

        alertRulesRef
            .child("dndEndMinute")
            .setValue(minute)
    }
}