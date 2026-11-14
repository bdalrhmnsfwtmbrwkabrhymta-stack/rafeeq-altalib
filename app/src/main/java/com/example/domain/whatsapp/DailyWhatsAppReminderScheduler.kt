package com.example.domain.whatsapp

import android.content.Context
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

object DailyWhatsAppReminderScheduler {

    private const val UNIQUE_WORK_NAME = "daily_whatsapp_report_worker"

    private fun getWorkManagerSafely(context: Context): WorkManager? {
        val appContext = context.applicationContext ?: context
        return try {
            if (!WorkManager.isInitialized()) {
                try {
                    WorkManager.initialize(appContext, Configuration.Builder().build())
                } catch (_: Throwable) {
                    // Already initialized concurrently or cannot auto-init
                }
            }
            WorkManager.getInstance(appContext)
        } catch (t: Throwable) {
            android.util.Log.w("DailyWhatsAppScheduler", "WorkManager unavailable: ${t.message}")
            null
        }
    }

    fun scheduleDailyReminder(context: Context, hour: Int = 20, minute: Int = 0) {
        try {
            val workManager = getWorkManagerSafely(context) ?: return
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val initialDelay = (target.timeInMillis - now.timeInMillis).coerceAtLeast(1000L)

            val workRequest = OneTimeWorkRequestBuilder<DailyWhatsAppReminderWorker>()
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(false)
                        .build()
                )
                .addTag("whatsapp_daily_report")
                .build()

            workManager.enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        } catch (t: Throwable) {
            android.util.Log.w("DailyWhatsAppScheduler", "Failed scheduling daily reminder: ${t.message}")
        }
    }

    fun cancelReminder(context: Context) {
        try {
            getWorkManagerSafely(context)?.cancelUniqueWork(UNIQUE_WORK_NAME)
        } catch (t: Throwable) {
            android.util.Log.w("DailyWhatsAppScheduler", "Failed cancelling daily reminder: ${t.message}")
        }
    }
}
