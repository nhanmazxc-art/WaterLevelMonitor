package com.nhom6.waterlevelmonitor.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Nhật ký CSV bền vững, có thể mở trực tiếp bằng Excel. */
object HistoryStore {
    private var file: File? = null
    private var lastSavedAtMs = 0L
    private const val AUTO_SAVE_INTERVAL_MS = 60_000L
    fun init(context: Context) { file = File(context.filesDir, "muc-nuoc-tu-dong.csv") }
    fun append(timeMs: Long, distanceCm: Double, levelCm: Double?) {
        val target = file ?: return
        if (timeMs - lastSavedAtMs < AUTO_SAVE_INTERVAL_MS) return
        lastSavedAtMs = timeMs
        if (!target.exists()) target.writeText("Thời gian,Khoảng cách (cm),Mực nước (cm)\n", Charsets.UTF_8)
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timeMs))
        target.appendText("$time,$distanceCm,${levelCm ?: ""}\n", Charsets.UTF_8)
    }
    fun csv(): String? = file?.takeIf { it.exists() }?.readText(Charsets.UTF_8)

    /** Dữ liệu nguồn cho tác vụ xuất CSV tự động hằng ngày. */
    fun dailyCsv(context: Context): String? {
        init(context)
        return csv()
    }
}
