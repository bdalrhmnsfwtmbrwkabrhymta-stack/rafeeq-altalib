package com.example.domain.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.audio.AzkarRecitationEngine
import com.example.data.database.AppDatabase
import com.example.domain.prayer.PrayerAlarmScheduler
import com.example.domain.prayer.PrayerDirectBootStore
import com.example.domain.prayer.PrayerForegroundService
import com.example.domain.schedule.StudyReminderScheduler
import com.example.domain.whatsapp.DailyWhatsAppReminderScheduler

/**
 * High-reliability watchdog worker that runs periodically (every 15 minutes)
 * to verify and reinforce all alarm schedules across the app without resetting
 * active hourly Salawat schedules.
 */
class NotificationWatchdogWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "NotificationWatchdogWorker executing alarm health check...")
        val context = applicationContext

        try {
            // 1. Reinforce Prayer Times, Morning/Evening Azkar, and Hourly Salawat (preserves future Salawat trigger!)
            PrayerAlarmScheduler.rescheduleAllSuspending(context)

            if (PrayerDirectBootStore.isUserUnlocked(context)) {
                // 2. Ensure Ayat Al-Kursi & morning birdsong assets stay cached locally
                runCatching {
                    AzkarRecitationEngine.warmUpAssetsAsync(context)
                }

                // 3. Reschedule Daily WhatsApp reports
                runCatching {
                    DailyWhatsAppReminderScheduler.scheduleDailyReminder(context)
                }.onFailure {
                    Log.w(TAG, "Watchdog: error rescheduling WhatsApp reminder", it)
                }

                // 4. Reschedule Study Sessions / Lectures from Room DB
                runCatching {
                    val db = AppDatabase.getInstance(context)
                    val sessions = db.scheduleDao().getAllSessionsList()
                    val subjects = db.subjectDao().getAllSubjectsList().associateBy { it.id }
                    StudyReminderScheduler.rescheduleAll(
                        context = context,
                        sessions = sessions,
                        subjects = subjects,
                        isEnabled = true
                    )
                }.onFailure {
                    Log.w(TAG, "Watchdog: error rescheduling study sessions", it)
                }
            }

            // 5. Best-effort start of Prayer Foreground Service if OS permits
            runCatching {
                PrayerForegroundService.start(context)
            }.onFailure {
                Log.w(TAG, "Watchdog: foreground service start deferred: ${it.message}")
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
