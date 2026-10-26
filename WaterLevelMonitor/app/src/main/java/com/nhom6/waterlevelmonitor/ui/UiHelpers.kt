package com.nhom6.waterlevelmonitor.ui

import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nhom6.waterlevelmonitor.R
import com.nhom6.waterlevelmonitor.data.AlertLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val VI: Locale = Locale.forLanguageTag("vi-VN")

fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm:ss, dd/MM/yyyy", VI).format(Date(millis))

/** Cập nhật nhãn Online/Offline (cần có các id chipOnline, viewOnlineDot, tvOnline). */
fun bindOnlineChip(root: View, online: Boolean) {
    val context = root.context
    val textColor = ContextCompat.getColor(context, if (online) R.color.green else R.color.text_secondary)
    val bgColor = ContextCompat.getColor(context, if (online) R.color.green_bg else R.color.gray_bg)
    root.findViewById<TextView>(R.id.tvOnline).apply {
        text = if (online) "Online" else "Offline"
        setTextColor(textColor)
    }
    root.findViewById<View>(R.id.chipOnline).backgroundTintList = ColorStateList.valueOf(bgColor)
    root.findViewById<View>(R.id.viewOnlineDot).backgroundTintList = ColorStateList.valueOf(textColor)
}

fun bindBanner(view: TextView, message: String?) {
    view.visibility = if (message == null) View.GONE else View.VISIBLE
    view.text = message.orEmpty()
}

/** Nếu mức chưa nhận ra nhưng ESP32 có gửi chữ thì hiện nguyên chữ đó. */
fun bindLevelChip(view: TextView, level: AlertLevel, rawText: String?) {
    val context = view.context
    val showRaw = level == AlertLevel.UNKNOWN && !rawText.isNullOrBlank()
    view.text = if (showRaw) rawText else level.label
    view.setTextColor(ContextCompat.getColor(context, level.textColorRes))
    view.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, level.bgColorRes))
}