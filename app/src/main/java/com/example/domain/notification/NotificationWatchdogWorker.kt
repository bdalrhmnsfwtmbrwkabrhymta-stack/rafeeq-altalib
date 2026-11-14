package com.example.domain.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.database.AppDatabase
import com.example.domain.prayer.AzkarScheduler
import com.example.domain.prayer.PrayerAlarmScheduler
import com.example.domain.prayer.PrayerForegroundService
import com.example.domain.schedule.StudyReminderScheduler
import com.example.domain.whatsapp.DailyWhatsAppReminderScheduler

/**
 * High-reliability watchdog worker that runs periodically (every 15 minutes)
 * to verify and reinforce all alarm schedules across the app.
 *
 * This guards against aggressive OEM background task killers (Xiaomi MIUI,
 * Samsung OneUI, Huawei EMUI, Oppo ColorOS) and deep battery saver modes.
 */
class NotificationWatchdogWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "NotificationWatchdogWorker executing alarm health check...")
        val context = applicationContext

        try {
            // 1. Reschedule Prayer Times & Salawat exact alarms
            PrayerAlarmScheduler.rescheduleAllSuspending(context)

            // 2. Reschedule Morning & Evening Azkar alarms
            AzkarScheduler.rescheduleAzkar(context)

            // 3. Reschedule Daily WhatsApp reports
            DailyWhatsAppReminderScheduler.scheduleDailyReminder(context)

            // 4. Reschedule Study Sessions / Lectures from Room DB
            try {
                val db = AppDatabase.getInstance(context)
                val sessions = db.scheduleDao().getAllSessionsList()
                val subjects = db.subjectDao().getAllSubjectsList().associateBy { it.id }
                StudyReminderScheduler.rescheduleAll(
                    context = context,
                    sessions = sessions,
                    subjects = subjects,
                    isEnabled = true
                )
            } catch (e: Exception) {
                Log.w(TAG, "Watchdog: error rescheduling study sessions", e)
            }

            // 5. Ensure Prayer Foreground Service is alive if active
            try {
                PrayerForegroundService.start(context)
            } catch (e: Exception) {
                Log.w(TAG, "Watchdog: foreground service start deferred", e)
            }

            Log.i(TAG, "NotificationWatchdogWorker alarm health check completed successfully.")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "NotificationWatchdogWorker encountered error during check", e)
            return Result.retry()
        }
    }

    companion object {
        private const val TAG = "WatchdogWorker"
    }
}
