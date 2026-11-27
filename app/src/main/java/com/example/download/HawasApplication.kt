package com.example.download

import android.app.Application
import android.util.Log
import com.example.data.audio.AzkarRecitationEngine
import com.example.data.notification.AzanNotificationHelper
import com.example.data.preferences.UserPreferencesRepository
import com.example.domain.notification.NotificationReliabilityManager
import com.example.domain.prayer.PrayerAlarmScheduler
import com.example.domain.prayer.PrayerDirectBootStore
import com.example.download.data.DownloadSettingsRepo
import com.example.download.extract.LinkExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HawasApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val appCtx = this@HawasApplication
        appScope.launch {
            // 1. Always ensure notification channels exist (Direct Boot safe)
            runCatching {
                AzanNotificationHelper(appCtx).createAllNotificationChannels()
            }.onFailure {
                Log.w("HawasApplication", "Notification channels setup skipped: ${it.message}")
            }

            val unlocked = PrayerDirectBootStore.isUserUnlocked(appCtx)

            // 2. Migrate legacy defaults & ensure prayer/azkar/salawat alarms are always armed on cold process start
            if (unlocked) {
                runCatching {
                    UserPreferencesRepository(appCtx).migrateLegacyPrayerDefaultsIfNeeded()
                }.onFailure {
                    Log.w("HawasApplication", "Prayer defaults migration skipped: ${it.message}")
                }
            }

            runCatching {
                PrayerAlarmScheduler.rescheduleAllSuspending(appCtx)
            }.onFailure {
                Log.w("HawasApplication", "Startup alarm reschedule skipped: ${it.message}")
            }

            if (unlocked) {
                runCatching {
                    NotificationReliabilityManager.scheduleWatchdog(appCtx)
                }
                runCatching {
                    AzkarRecitationEngine.warmUpAssetsAsync(appCtx)
                }
                runCatching {
                    val settingsRepo = DownloadSettingsRepo(appCtx)
                    LinkExtractor.ensureInitialized(appCtx)
                    LinkExtractor.updateYtDlpIfStale(appCtx, settingsRepo)
                    SmartDownloadManager.getInstance(appCtx).cleanOrphanTempFiles()
                }.onFailure {
                    Log.w("HawasApplication", "Download subsystem init skipped: ${it.message}")
                }
            }
        }
    }
}
