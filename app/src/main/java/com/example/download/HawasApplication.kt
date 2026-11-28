package com.example.download

import android.app.Application
import android.os.Looper
import android.util.Log
import com.example.data.notification.AzanNotificationHelper
import com.example.domain.prayer.PrayerAlarmScheduler
import com.example.domain.prayer.PrayerDirectBootStore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HawasApplication : Application() {

    private val appExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.w("HawasApplication", "Handled background coroutine exception: ${throwable.message}")
    }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + appExceptionHandler)

    override fun onCreate() {
        super.onCreate()
        installBackgroundCrashShield()
        val appCtx = this@HawasApplication
        appScope.launch {
            // 1. Always ensure notification channels exist (Direct Boot safe)
            runCatching {
                AzanNotificationHelper(appCtx).createAllNotificationChannels()
            }.onFailure {
                Log.w("HawasApplication", "Notification channels setup skipped: ${it.message}")
            }

            val unlocked = runCatching { PrayerDirectBootStore.isUserUnlocked(appCtx) }.getOrDefault(true)

            // 2. Fast check: only reschedule from Application.onCreate if alarms haven't been armed recently
            runCatching {
                val lastRescheduled = PrayerDirectBootStore.getLastRescheduleMs(appCtx)
                val now = System.currentTimeMillis()
                if (now - lastRescheduled > 6 * 60 * 60 * 1000L) {
                    PrayerAlarmScheduler.rescheduleAllSuspending(appCtx)
                }
            }.onFailure {
                Log.w("HawasApplication", "Startup alarm reschedule skipped: ${it.message}")
            }

            if (unlocked) {
                runCatching {
                    SmartDownloadManager.getInstance(appCtx).cleanOrphanTempFiles()
                }.onFailure {
                    Log.w("HawasApplication", "Download temp cleanup skipped: ${it.message}")
                }
            }
        }
    }

    private fun installBackgroundCrashShield() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val isMainThread = runCatching { thread == Looper.getMainLooper().thread }.getOrDefault(true)
            if (!isMainThread) {
                Log.e(
                    "HawasApplication",
                    "Intercepted background thread exception on [${thread.name}]: ${throwable.message}",
                    throwable
                )
                return@setDefaultUncaughtExceptionHandler
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}
