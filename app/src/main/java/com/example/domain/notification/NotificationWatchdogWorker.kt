package com.example.domain.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
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

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = NotificationChannel(
                PrayerForegroundService.CHANNEL_ID,
                "مواقيت الصلاة الدائمة",
                NotificationManager.IMPORTANCE_LOW
            )
            manager?.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, PrayerForegroundService.CHANNEL_ID)
            .setContentTitle("تحديث تنبيهات رفيق الطالب")
            .setContentText("جاري التحقق من مواقيت الصلاة والتنبيهات...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                WATCHDOG_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(WATCHDOG_NOTIFICATION_ID, notification)
        }
    }

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

            // Note: Do NOT call PrayerForegroundService.start(context) from a background WorkManager thread.
            // On Android 12–16 (targetSdk 36), starting a foreground service from the background triggers
            // ForegroundServiceStartNotAllowedException / RemoteServiceException and kills the app process.
            // PrayerForegroundService is safely started when MainActivity is resumed in the foreground.

            Log.i(TAG, "NotificationWatchdogWorker alarm health check completed successfully.")
            return Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "NotificationWatchdogWorker encountered error during check", t)
            return Result.retry()
        }
    }

    companion object {
        private const val TAG = "WatchdogWorker"
        private const val WATCHDOG_NOTIFICATION_ID = 2009
    }
}
