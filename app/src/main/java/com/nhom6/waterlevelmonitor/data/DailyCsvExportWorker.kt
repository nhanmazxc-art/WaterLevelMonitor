package com.nhom6.waterlevelmonitor.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nhom6.waterlevelmonitor.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Xuất bản sao lịch sử mực nước ra:
 * Downloads/MucNuoc/muc-nuoc-YYYY-MM-DD.csv
 */
class DailyCsvExportWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {

            // Lấy dữ liệu CSV từ lịch sử
            val csv = HistoryStore.dailyCsv(applicationContext)
                ?: return Result.success()

            // Tạo tên file theo ngày Việt Nam
            val formatter = SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
            ).apply {
                timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
            }

            val fileName = "muc-nuoc-${formatter.format(Date())}.csv"

            // Tạo thông tin file
            val values = ContentValues().apply {

                put(
                    MediaStore.Downloads.DISPLAY_NAME,
                    fileName
                )

                put(
                    MediaStore.Downloads.MIME_TYPE,
                    "text/csv"
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        "Download/MucNuoc"
                    )

                    put(
                        MediaStore.Downloads.IS_PENDING,
                        1
                    )
                }
            }

            val resolver = applicationContext.contentResolver

            // Tạo file
            val uri = resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            ) ?: return Result.retry()

            // Ghi dữ liệu CSV
            resolver.openOutputStream(uri)?.bufferedWriter().use { writer ->
                if (writer == null) {
                    return Result.retry()
                }

                writer.write(csv)
            }

            // Cho file hiển thị bình thường
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                val updateValues = ContentValues().apply {
                    put(
                        MediaStore.Downloads.IS_PENDING,
                        0
                    )
                }

                resolver.update(
                    uri,
                    updateValues,
                    null,
                    null
                )
            }

            // Thông báo không được làm hỏng quá trình xuất CSV
            try {
                showNotification()
            } catch (_: Exception) {
                // Bỏ qua lỗi thông báo
            }

            Result.success()

        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun showNotification() {

        createNotificationChannel()

        val notification = NotificationCompat.Builder(
            applicationContext,
            EXPORT_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle("Đã xuất dữ liệu mực nước")
            .setContentText(
                "Tệp CSV đã được lưu trong Downloads/MucNuoc"
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat
            .from(applicationContext)
            .notify(
                EXPORT_NOTIFICATION_ID,
                notification
            )
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            EXPORT_CHANNEL_ID,
            "Xuất dữ liệu tự động",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description =
                "Thông báo khi app xuất CSV mực nước hằng ngày"
        }

        val manager =
            applicationContext.getSystemService(
                NotificationManager::class.java
            )

        manager.createNotificationChannel(channel)
    }

    companion object {

        const val EXPORT_CHANNEL_ID =
            "daily_csv_export"

        private const val EXPORT_NOTIFICATION_ID =
            1001
    }
}