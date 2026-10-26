package com.nhom6.waterlevelmonitor.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object DailyCsvExportScheduler {

    private const val UNIQUE_WORK_NAME =
        "daily_csv_export"

    private const val TEST_WORK_NAME =
        "daily_csv_export_test"

    /**
     * TEST:
     * Chạy xuất CSV sau 10 giây.
     */
    fun scheduleTest(context: Context) {

        createChannel(context)

        val request =
            OneTimeWorkRequestBuilder<DailyCsvExportWorker>()
                .setInitialDelay(
                    10,
                    TimeUnit.SECONDS
                )
                .build()

        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(
                TEST_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
    }

    /**
     * CHÍNH THỨC:
     * Chạy khoảng 00:05 mỗi ngày.
     */
    fun schedule(context: Context) {

        createChannel(context)

        val now = Calendar.getInstance()

        val firstRun =
            (now.clone() as Calendar).apply {

                set(
                    Calendar.HOUR_OF_DAY,
                    0
                )

                set(
                    Calendar.MINUTE,
                    5
                )

                set(
                    Calendar.SECOND,
                    0
                )

                set(
                    Calendar.MILLISECOND,
                    0
                )

                // Nếu 00:05 hôm nay đã qua
                // thì chạy vào ngày mai
                if (!after(now)) {
                    add(
                        Calendar.DATE,
                        1
                    )
                }
            }

        val delayMs =
            firstRun.timeInMillis -
                    now.timeInMillis

        val request =
            PeriodicWorkRequestBuilder<DailyCsvExportWorker>(
                24,
                TimeUnit.HOURS
            )
                .setInitialDelay(
                    delayMs,
                    TimeUnit.MILLISECONDS
                )
                .build()

        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
    }

    private fun createChannel(context: Context) {

        if (Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val channel = NotificationChannel(
            DailyCsvExportWorker.EXPORT_CHANNEL_ID,
            "Xuất dữ liệu tự động",
            NotificationManager.IMPORTANCE_LOW
        ).apply {

            description =
                "Thông báo khi app xuất CSV mực nước hằng ngày"
        }

        val manager =
            context.getSystemService(
                NotificationManager::class.java
            )

        manager.createNotificationChannel(channel)
    }
}