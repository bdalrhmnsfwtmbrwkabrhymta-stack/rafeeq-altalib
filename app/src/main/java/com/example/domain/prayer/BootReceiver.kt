package com.example.domain.prayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.example.data.audio.AzkarRecitationEngine
import com.example.data.notification.AzanNotificationHelper
import com.example.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * BootReceiver automatically wakes up upon device startup (both Direct Boot before unlock
 * and post-unlock), user unlock, time/timezone/date changes, app update, or exact-alarm
 * permission restoration. Uses a synchronized/debounced pipeline so simultaneous system
 * broadcasts never race or cancel each other's alarms.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "BootReceiver received action: $action")

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = runCatching {
            powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "RafiqAlTalib:BootReceiverWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(90_000L)
            }
        }.getOrNull()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                runSynchronizedReschedule(appContext, action)
            } catch (e: Exception) {
                Log.e(TAG, "Error in BootReceiver rescheduling", e)
            } finally {
                runCatching {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                }
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
        private val rescheduleMutex = Mutex()

        @Volatile
        private var lastUnlockedRescheduleElapsedMs: Long = 0L

        @Volatile
        private var lastLockedRescheduleElapsedMs: Long = 0L

        private const val DEBOUNCE_WINDOW_MS = 2_500L

        /**
         * Single authoritative boot/clock/unlock recovery routine shared by BootReceiver,
         * BootCompletedReceiver, and PrayerAlarmReceiver.
         */
        suspend fun runSynchronizedReschedule(context: Context, reason: String) {
            val appCtx = context.applicationContext
            rescheduleMutex.withLock {
                val unlocked = PrayerDirectBootStore.isUserUnlocked(appCtx)
                val nowElapsed = SystemClock.elapsedRealtime()
                val isCriticalClockEvent =
                    reason == Intent.ACTION_TIME_CHANGED ||
                    reason == Intent.ACTION_TIMEZONE_CHANGED ||
                    reason == Intent.ACTION_DATE_CHANGED ||
                    reason == "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

                if (!isCriticalClockEvent) {
                    val lastMs = if (unlocked) lastUnlockedRescheduleElapsedMs else lastLockedRescheduleElapsedMs
                    if (lastMs > 0L && (nowElapsed - lastMs) < DEBOUNCE_WINDOW_MS) {
                        Log.d(TAG, "Skipping duplicate reschedule ($reason, unlocked=$unlocked) within debounce window.")
                        return
                    }
                }

                if (unlocked) {
                    lastUnlockedRescheduleElapsedMs = nowElapsed
                } else {
                    lastLockedRescheduleElapsedMs = nowElapsed
                }

                Log.i(TAG, "Running synchronized alarm recovery (reason=$reason, unlocked=$unlocked)")

                // 1. Ensure all notification channels exist immediately
                runCatching {
                    AzanNotificationHelper(appCtx).createAllNotificationChannels()
                }.onFailure {
                    Log.w(TAG, "Failed creating notification channels during recovery", it)
                }

                // 2. Apply one-time migration for 9:00 AM / 5:00 PM Azkar & 60m Salawat defaults when unlocked
                if (unlocked) {
                    runCatching {
                        UserPreferencesRepository(appCtx).migrateLegacyPrayerDefaultsIfNeeded()
                    }.onFailure {
                        Log.w(TAG, "Legacy defaults migration skipped", it)
                    }
                }

                // 3. Reschedule Prayer Times, Morning/Evening Azkar, and Hourly Salawat (Direct Boot safe)
                runCatching {
                    PrayerAlarmScheduler.rescheduleAllSuspending(appCtx)
                }.onFailure {
                    Log.e(TAG, "Failed rescheduling prayer/azkar/salawat alarms", it)
                }

                if (unlocked) {
                    // 4. Reschedule Daily WhatsApp report & health reminders
                    runCatching {
                        com.example.domain.whatsapp.DailyWhatsAppReminderScheduler.scheduleDailyReminder(appCtx)
                    }.onFailure {
                        Log.w(TAG, "Error rescheduling WhatsApp reminder on boot", it)
                    }

                    // 5. Reschedule Study Sessions / Lectures Reminders from local Room DB
                    runCatching {
                        val db = com.example.data.database.AppDatabase.getInstance(appCtx)
                        val sessions = db.scheduleDao().getAllSessionsList()
                        val subjects = db.subjectDao().getAllSubjectsList().associateBy { it.id }
                        com.example.domain.schedule.StudyReminderScheduler.rescheduleAll(
                            context = appCtx,
                            sessions = sessions,
                            subjects = subjects,
                            isEnabled = true
                        )
                        Log.i(TAG, "Rescheduled ${sessions.size} study lectures.")
                    }.onFailure {
                        Log.e(TAG, "Error rescheduling study sessions on boot", it)
                    }

                    // 6. Reschedule Habit Reminders & Alarms
                    runCatching {
                        com.example.domain.habits.HabitAlarmScheduler.rescheduleAll(appCtx)
                    }.onFailure {
                        Log.e(TAG, "Error rescheduling habit alarms on boot", it)
                    }

                    // 7. Enqueue KeepAlive Watchdog Worker (WorkManager) for continuous OEM protection
                    runCatching {
                        com.example.domain.notification.NotificationReliabilityManager.scheduleWatchdog(appCtx)
                    }.onFailure {
                        Log.w(TAG, "Error scheduling watchdog worker on boot", it)
                    }

                    // 8. Pre-cache Ayat Al-Kursi & Dawn Birdsong audio files in Device-Protected storage
                    runCatching {
                        AzkarRecitationEngine.warmUpAssetsAsync(appCtx)
                    }
                }

                // 9. Best-effort start of persistent prayer foreground service (non-fatal if OEM/Android 14+ blocks)
                runCatching {
                    PrayerForegroundService.start(appCtx)
                }.onFailure {
                    Log.w(TAG, "Foreground service start deferred on boot: ${it.message}")
                }

                Log.i(TAG, "Synchronized boot/clock recovery completed (reason=$reason, unlocked=$unlocked).")
            }
        }
    }
}
