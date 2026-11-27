package com.example.domain.prayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.example.R
import com.example.data.audio.AppAudioManager
import com.example.data.audio.AzanAudioPlayer
import com.example.data.notification.AzanNotificationHelper
import com.example.ui.prayer.AthkarPopupActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * High-reliability BroadcastReceiver for exact alarms:
 * - Prayer times (Adhan)
 * - Salawat reminders
 * - Morning Azkar (Ayat Al-Kursi -> Morning Azkar)
 * - Evening Azkar
 * - Boot & App replacement persistence
 */
class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "PrayerAlarmReceiver onReceive action: $action")

        // Acquire temporary WakeLock to guarantee CPU stays active during alarm handling
        var wakeLock: PowerManager.WakeLock? = null
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "RafiqAlTalib:PrayerAlarmWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(3 * 60 * 1000L) // 3 minutes timeout
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }

        try {
            when (action) {
                ACTION_PRAYER_ALARM -> {
                    val scheduledTimeMs = intent.getLongExtra(EXTRA_SCHEDULED_TIME_MS, 0L)
                    val now = System.currentTimeMillis()
                    // Reject stale or misfired alarms (more than 15 minutes drift)
                    if (scheduledTimeMs > 0L && Math.abs(now - scheduledTimeMs) > 15 * 60 * 1000L) {
                        Log.w(TAG, "Ignoring stale prayer alarm: scheduled at $scheduledTimeMs, current time is $now")
                        val pendingResult = goAsync()
                        val appCtx = context.applicationContext
                        val heldWakeLock = wakeLock
                        wakeLock = null
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                PrayerAlarmScheduler.rescheduleAllSuspending(appCtx)
                            } finally {
                                runCatching { if (heldWakeLock?.isHeld == true) heldWakeLock.release() }
                                pendingResult.finish()
                            }
                        }
                        return
                    }

                    // 1. STOP any other audio playing (Athkar, Quran, Salawat) safely before Adhan
                    AppAudioManager.stopAllNonAdhanAudio(context)
                    AppAudioManager.requestAudioFocus(context, isAdhan = true)

                    val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: "الصلاة"
                    val cityName = intent.getStringExtra(EXTRA_CITY_NAME) ?: "القاهرة"
                    val muezzinVoice = intent.getStringExtra(EXTRA_MUEZZIN) ?: "الأذان المصري الأصيل (MBC وإذاعة القرآن)"
                    val isFullAudio = intent.getBooleanExtra(EXTRA_IS_FULL_AUDIO, true)

                    val player = AzanAudioPlayer(context)
                    val soundResId = player.getRawResForMuezzin(muezzinVoice, muezzinVoice)

                    // 2. Start Foreground Service (with automatic fallback notification if OEM restricts FGS)
                    AdhanPlaybackService.start(
                        context = context,
                        prayerName = prayerName,
                        cityName = cityName,
                        muezzinVoice = muezzinVoice,
                        rawResId = soundResId,
                        playAudio = isFullAudio,
                        scheduledTimeMs = scheduledTimeMs
                    )

                    // 3. Direct Activity launch paired with setFullScreenIntent guarantees instant popup across OEMs
                    try {
                        val directAdhanIntent = Intent(context, com.example.ui.prayer.AdhanFullScreenActivity::class.java).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
                            putExtra(com.example.ui.prayer.AdhanFullScreenActivity.EXTRA_PRAYER_NAME, prayerName)
                            putExtra(com.example.ui.prayer.AdhanFullScreenActivity.EXTRA_CITY_NAME, cityName)
                            putExtra(com.example.ui.prayer.AdhanFullScreenActivity.EXTRA_MUEZZIN, muezzinVoice)
                            putExtra(com.example.ui.prayer.AdhanFullScreenActivity.EXTRA_AUTO_PLAY, false)
                            if (scheduledTimeMs > 0L) {
                                putExtra(com.example.ui.prayer.AdhanFullScreenActivity.EXTRA_SCHEDULED_TIME_MS, scheduledTimeMs)
                            }
                        }
                        context.startActivity(directAdhanIntent)
                    } catch (e: Exception) {
                        Log.w(TAG, "Direct startActivity blocked by background launch policy; relying on FullScreenIntent: ${e.message}")
                    }

                    // 4. Reliably reschedule next prayer alarms via goAsync() so OS never kills process before re-arming
                    val pendingResult = goAsync()
                    val appCtx = context.applicationContext
                    val heldWakeLock = wakeLock
                    wakeLock = null
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            PrayerAlarmScheduler.rescheduleAllSuspending(appCtx)
                            PrayerForegroundService.start(appCtx)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error rescheduling after prayer alarm: ${e.message}")
                        } finally {
                            runCatching { if (heldWakeLock?.isHeld == true) heldWakeLock.release() }
                            pendingResult.finish()
                        }
                    }
                }

                ACTION_SALAWAT_ALARM -> {
                    // Zero-lag synchronous read from Device-Protected Storage (never blocks main thread with DataStore!)
                    val snapshot = PrayerDirectBootStore.loadSnapshot(context)

                    // Immediately arm the next hourly exact alarm first so timing stays rock-solid
                    try {
                        PrayerAlarmScheduler.scheduleNextSalawatExactAlarm(context)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error scheduling next Salawat alarm", e)
                    }

                    if (!snapshot.isSalawatEnabled) {
                        return
                    }

                    // Do not interrupt an active Adhan or Azkar recitation
                    if (AdhanPlaybackService.isPlayingAdhan || AzkarAudioService.isPlayingAzkar) {
                        Log.i(TAG, "Skipping Salawat audio because Adhan or Azkar is currently playing")
                        return
                    }

                    SalawatPlaybackService.start(
                        context = context,
                        voiceId = snapshot.salawatVoiceId,
                        audioMode = snapshot.salawatAudioMode,
                        customUri = snapshot.customSalawatAudioUri
                    )
                }

                ACTION_MORNING_AZKAR, AzkarScheduler.ACTION_MORNING_AZKAR -> {
                    val scheduledTimeMs = intent.getLongExtra(EXTRA_SCHEDULED_TIME_MS, 0L)
                    val now = System.currentTimeMillis()
                    val snapshot = PrayerDirectBootStore.loadSnapshot(context)

                    // Re-arm tomorrow's Morning Azkar reliably via goAsync
                    val pendingResult = goAsync()
                    val appCtx = context.applicationContext
                    val heldWakeLock = wakeLock
                    wakeLock = null

                    val isStale = scheduledTimeMs > 0L && Math.abs(now - scheduledTimeMs) > 30 * 60 * 1000L
                    if (snapshot.isMorningAzkarEnabled && !isStale && !AdhanPlaybackService.isPlayingAdhan) {
                        // 1. Launch Morning Azkar sequence (Dawn birdsong + Ayat Al-Kursi -> Morning Azkar)
                        AzkarAudioService.startMorning(context)

                        // 2. Notification + Full-Screen popup
                        val popupIntent = Intent(context, AthkarPopupActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra(AthkarPopupActivity.EXTRA_ATHKAR_TYPE, AthkarPopupActivity.TYPE_MORNING)
                        }
                        val popupPendingIntent = android.app.PendingIntent.getActivity(
                            context,
                            401,
                            popupIntent,
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                        )
                        AzanNotificationHelper(context).showAzkarNotification(
                            title = "أذكار الصباح المباركة 🌅",
                            message = "بدأت الآن تلاوة آية الكرسي وأذكار الصباح لحفظك وتحصينك بذكر الله.",
                            contentIntent = popupPendingIntent
                        )
                        try {
                            context.startActivity(popupIntent)
                        } catch (e: Exception) {
                            Log.w(TAG, "Morning Athkar popup deferred to FullScreenIntent: ${e.message}")
                        }
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            AzkarScheduler.rescheduleAzkarSuspending(appCtx)
                        } finally {
                            runCatching { if (heldWakeLock?.isHeld == true) heldWakeLock.release() }
                            pendingResult.finish()
                        }
                    }
                }

                ACTION_EVENING_AZKAR, AzkarScheduler.ACTION_EVENING_AZKAR -> {
                    val scheduledTimeMs = intent.getLongExtra(EXTRA_SCHEDULED_TIME_MS, 0L)
                    val now = System.currentTimeMillis()
                    val snapshot = PrayerDirectBootStore.loadSnapshot(context)

                    val pendingResult = goAsync()
                    val appCtx = context.applicationContext
                    val heldWakeLock = wakeLock
                    wakeLock = null

                    val isStale = scheduledTimeMs > 0L && Math.abs(now - scheduledTimeMs) > 30 * 60 * 1000L
                    if (snapshot.isEveningAzkarEnabled && !isStale && !AdhanPlaybackService.isPlayingAdhan) {
                        // 1. Launch Evening Azkar sequence (Ayat Al-Kursi without birdsong -> Evening Azkar)
                        AzkarAudioService.startEvening(context)

                        // 2. Notification + Full-Screen popup
                        val popupIntent = Intent(context, AthkarPopupActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra(AthkarPopupActivity.EXTRA_ATHKAR_TYPE, AthkarPopupActivity.TYPE_EVENING)
                        }
                        val popupPendingIntent = android.app.PendingIntent.getActivity(
                            context,
                            402,
                            popupIntent,
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                        )
                        AzanNotificationHelper(context).showAzkarNotification(
                            title = "أذكار المساء المباركة 🌇",
                            message = "بدأت الآن تلاوة آية الكرسي وأذكار المساء لختام يومك بحفظ الله وذكره.",
                            contentIntent = popupPendingIntent
                        )
                        try {
                            context.startActivity(popupIntent)
                        } catch (e: Exception) {
                            Log.w(TAG, "Evening Athkar popup deferred to FullScreenIntent: ${e.message}")
                        }
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            AzkarScheduler.rescheduleAzkarSuspending(appCtx)
                        } finally {
                            runCatching { if (heldWakeLock?.isHeld == true) heldWakeLock.release() }
                            pendingResult.finish()
                        }
                    }
                }

                ACTION_RESTART_SERVICE -> {
                    Log.i(TAG, "Watchdog restart triggered: Ensuring exact alarms & watchdog are active")
                    val pendingResult = goAsync()
                    val appCtx = context.applicationContext
                    val heldWakeLock = wakeLock
                    wakeLock = null
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            PrayerAlarmScheduler.rescheduleAllSuspending(appCtx)
                            com.example.domain.notification.NotificationReliabilityManager.scheduleWatchdog(appCtx)
                            runCatching { PrayerForegroundService.start(appCtx) }
                        } finally {
                            runCatching { if (heldWakeLock?.isHeld == true) heldWakeLock.release() }
                            pendingResult.finish()
                        }
                    }
                }

                com.example.domain.notification.NotificationReliabilityManager.ACTION_TEST_ALARM -> {
                    val isEnglish = intent.getBooleanExtra("is_english", false)
                    Log.i(TAG, "Handling scheduled test alarm trigger")
                    com.example.domain.notification.NotificationReliabilityManager.sendTestNotification(context, isEnglish)
                }

                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_LOCKED_BOOT_COMPLETED,
                Intent.ACTION_USER_UNLOCKED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_DATE_CHANGED,
                "android.intent.action.TIME_SET",
                "android.intent.action.QUICKBOOT_POWERON",
                "com.htc.intent.action.QUICKBOOT_POWERON",
                "com.huawei.intent.action.QUICKBOOT_POWERON" -> {
                    val pendingResult = goAsync()
                    val appCtx = context.applicationContext
                    val heldWakeLock = wakeLock
                    wakeLock = null
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            BootReceiver.runSynchronizedReschedule(appCtx, action)
                        } finally {
                            runCatching { if (heldWakeLock?.isHeld == true) heldWakeLock.release() }
                            pendingResult.finish()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during alarm handling in PrayerAlarmReceiver", e)
        } finally {
            try {
                if (wakeLock != null && wakeLock.isHeld) {
                    wakeLock.release()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        private const val TAG = "PrayerAlarmReceiver"

        const val CHANNEL_ID = AzanNotificationHelper.CHANNEL_ID
        const val SALAWAT_CHANNEL_ID = AzanNotificationHelper.SALAWAT_CHANNEL_ID

        const val ACTION_PRAYER_ALARM = "com.example.ACTION_PRAYER_ALARM"
        const val ACTION_SALAWAT_ALARM = "com.example.ACTION_SALAWAT_ALARM"
        const val ACTION_MORNING_AZKAR = "com.example.ACTION_MORNING_AZKAR"
        const val ACTION_EVENING_AZKAR = "com.example.ACTION_EVENING_AZKAR"
        const val ACTION_RESTART_SERVICE = "com.example.ACTION_RESTART_SERVICE"

        const val EXTRA_PRAYER_NAME = "extra_prayer_name"
        const val EXTRA_CITY_NAME = "extra_city_name"
        const val EXTRA_MUEZZIN = "extra_muezzin"
        const val EXTRA_IS_FULL_AUDIO = "extra_is_full_audio"
        const val EXTRA_SCHEDULED_TIME_MS = "extra_scheduled_time_ms"
    }
}
