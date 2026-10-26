package com.nhom6.waterlevelmonitor.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

/** Nơi duy nhất giữ các đường dẫn Firebase để Android và ESP32 không bị lệch tên. */
object FirebaseManager {

    val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val database: FirebaseDatabase
        get() = FirebaseDatabase.getInstance()

    val deviceRef: DatabaseReference
        get() = database.getReference("mucnuoc")

    /** true khi app đang nối được tới máy chủ Firebase. */
    val connectedRef: DatabaseReference
        get() = database.getReference(".info/connected")

    /** Độ lệch giữa đồng hồ điện thoại và giờ máy chủ Firebase (mili giây). */
    val serverOffsetRef: DatabaseReference
        get() = database.getReference(".info/serverTimeOffset")
}