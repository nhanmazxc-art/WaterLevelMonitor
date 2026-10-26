package com.nhom6.waterlevelmonitor.data

import android.os.SystemClock

/**
 * Firmware hiện chưa gửi mốc thời gian, nên ta theo dõi thoiGianChayMs:
 * còn thay đổi thì ESP32 đang hoạt động, đứng yên quá lâu thì coi là Offline.
 */
object OnlineTracker {

    enum class Status { CHECKING, ONLINE, OFFLINE }

    /** Quá bấy nhiêu mili giây không thấy dữ liệu đổi thì coi là Offline. */
    const val STALE_AFTER_MS = 20_000L

    private var lastUptime: Long? = null
    private var firstSeenAt: Long? = null
    private var lastChangeAt: Long? = null

    /** Giờ điện thoại lúc thấy dữ liệu đổi lần cuối. */
    var lastChangeWallClock: Long? = null
        private set

    fun reset() {
        lastUptime = null
        firstSeenAt = null
        lastChangeAt = null
        lastChangeWallClock = null
    }

    fun onUptime(uptimeMs: Long?) {
        if (uptimeMs == null) return
        val now = SystemClock.elapsedRealtime()
        if (firstSeenAt == null) firstSeenAt = now
        val previous = lastUptime
        if (previous != null && previous != uptimeMs) {
            lastChangeAt = now
            lastChangeWallClock = System.currentTimeMillis()
        }
        lastUptime = uptimeMs
    }

    fun status(): Status {
        val now = SystemClock.elapsedRealtime()
        val changed = lastChangeAt
        if (changed != null && now - changed <= STALE_AFTER_MS) return Status.ONLINE
        val first = firstSeenAt ?: return Status.CHECKING
        val reference = changed ?: first
        return if (now - reference > STALE_AFTER_MS) Status.OFFLINE else Status.CHECKING
    }
}