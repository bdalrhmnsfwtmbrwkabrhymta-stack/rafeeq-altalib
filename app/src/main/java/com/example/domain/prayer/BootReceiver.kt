package com.example.domain.prayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BootReceiver automatically wakes up upon device startup or app update,
 * acquiring a temporary partial WakeLock and rescheduling all Prayer,
 * Morning/Evening Azkar, and Salawat alarms to ensure 100% background persistence.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "BootReceiver received action: $action")

        val pendingResult = goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RafiqAlTalib:BootReceiverWakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(3 * 60 * 1000L) // 3 minutes wake lock for safe background scheduling
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "Device booted or app updated ($action). Rescheduling all alarms...")

                // 1. Reschedule Prayer Times & Salawat Alarms synchronously in IO coroutine
                PrayerAlarmScheduler.rescheduleAllSuspending(context)

                // 2. Reschedule Morning & Evening Azkar Alarms
                AzkarScheduler.rescheduleAzkar(context)

                // 3. Reschedule Daily WhatsApp report & health reminders
                try {
                    com.example.domain.whatsapp.DailyWhatsAppReminderScheduler.scheduleDailyReminder(context)
                } catch (e: Exception) {
                    Log.w(TAG, "Error rescheduling WhatsApp reminder on boot", e)
                }

                // 4. Reschedule Study Sessions / Lectures Reminders from local Room DB
                try {
                    val db = com.example.data.database.AppDatabase.getInstance(context)
                    val sessions = db.scheduleDao().getAllSessionsList()
                    val subjects = db.subjectDao().getAllSubjectsList().associateBy { it.id }
                    com.example.domain.schedule.StudyReminderScheduler.rescheduleAll(
                        context = context,
                        sessions = sessions,
                        subjects = subjects,
                        isEnabled = true
                    )
                    Log.i(TAG, "Successfully rescheduled ${sessions.size} study lectures on boot.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error rescheduling study sessions on boot", e)
                }

                // 5. Reschedule Habit Reminders & Alarms
                try {
                    com.example.domain.habits.HabitAlarmScheduler.rescheduleAll(context)
                    Log.i(TAG, "Successfully rescheduled Habit Reminders on boot.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error rescheduling habit alarms on boot", e)
                }

                // 6. Enqueue KeepAlive Watchdog Worker (WorkManager) for continuous protection
                com.example.domain.notification.NotificationReliabilityManager.scheduleWatchdog(context)

                // 7. Ensure Prayer Foreground Service is started if allowed
                try {
                    PrayerForegroundService.start(context)
                } catch (e: Exception) {
                    Log.w(TAG, "Foreground service start deferred on boot: ${e.message}")
                }

                Log.i(TAG, "Boot alarm rescheduling completed successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Error in BootReceiver rescheduling", e)
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
