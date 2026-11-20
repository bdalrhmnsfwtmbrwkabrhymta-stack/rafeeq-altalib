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
 * Reschedules exact Prayer & Adhan alarms automatically upon device reboot
 * or app update using the saved persistent geolocation coordinates.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
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
                val appContext = context.applicationContext
                LocationPrayerManager.syncSavedLocationSilently(
                    appContext,
                    UserPreferencesRepository(appContext)
                )
                PrayerAlarmScheduler.rescheduleAllSuspending(appContext)
                Log.i(TAG, "Rescheduled prayer alarms after $action using saved location.")
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
