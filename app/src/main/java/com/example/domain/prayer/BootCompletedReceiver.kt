package com.example.domain.prayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.example.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reschedules exact Prayer, Azkar & Salawat alarms automatically upon device reboot
 * (including Direct Boot & OEM quickboot) or app update, delegating to BootReceiver's
 * synchronized pipeline so simultaneous boot broadcasts never conflict.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_USER_UNLOCKED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON" &&
            action != "com.huawei.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = runCatching {
            powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "HawasElDirasa:BootCompletedPrayerLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(60_000L)
            }
        }.getOrNull()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (PrayerDirectBootStore.isUserUnlocked(appContext)) {
                    runCatching {
                        LocationPrayerManager.syncSavedLocationSilently(
                            appContext,
                            UserPreferencesRepository(appContext)
                        )
                    }.onFailure {
                        Log.w(TAG, "Silent location sync skipped on $action: ${it.message}")
                    }
                }
                BootReceiver.runSynchronizedReschedule(appContext, action)
                Log.i(TAG, "Delegated prayer alarm recovery after $action.")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed rescheduling prayer alarms in BootCompletedReceiver", t)
            } finally {
                runCatching {
                    if (wakeLock?.isHeld == true) wakeLock.release()
                }
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
