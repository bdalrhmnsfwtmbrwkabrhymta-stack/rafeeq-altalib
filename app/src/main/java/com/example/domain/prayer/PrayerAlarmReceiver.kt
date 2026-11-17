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
import kotlinx.coroutines.flow.first

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
                        PrayerAlarmScheduler.rescheduleAll(context)
                        return
                    }

                    // 1. STOP any other audio playing (Athkar, Quran, Salawat) before Adhan
                    AppAudioManager.stopAllNonAdhanAudio(context)
                    AppAudioManager.requestAudioFocus(context, isAdhan = true)

                    val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: "الصلاة"
                    val cityName = intent.getStringExtra(EXTRA_CITY_NAME) ?: "القاهرة"
                    val muezzinVoice = intent.getStringExtra(EXTRA_MUEZZIN) ?: "الأذان المصري الأصيل (MBC وإذاعة القرآن)"
                    val isFullAudio = intent.getBooleanExtra(EXTRA_IS_FULL_AUDIO, true)

                    val player = AzanAudioPlayer(context)
                    val soundResId = player.getRawResForMuezzin(muezzinVoice, muezzinVoice)

                    // Start Foreground Service that holds WakeLock, plays audio via STREAM_ALARM, and triggers FullScreenIntent on lockscreen
                    AdhanPlaybackService.start(
                        context = context,
                        prayerName = prayerName,
                        cityName = cityName,
                        muezzinVoice = muezzinVoice,
                        rawResId = soundResId,
                        playAudio = isFullAudio
                    )

                    // Auto-reschedule next prayer alarms and start foreground service
                    PrayerAlarmScheduler.rescheduleAll(context)
                    PrayerForegroundService.start(context)
                }

                ACTION_SALAWAT_ALARM -> {
                    val appContext = context.applicationContext
                    var voiceId = 1
                    var audioMode = "VOICE_FORMULA"
                    var customUri = ""

                    try {
                        kotlinx.coroutines.runBlocking {
                            val repo = com.example.data.preferences.UserPreferencesRepository(appContext)
                            val settings = repo.userSettingsFlow.first()
                            voiceId = settings.salawatVoiceId
                            audioMode = settings.salawatAudioMode
                            customUri = settings.customSalawatAudioUri
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading Salawat preferences", e)
                    }

                    // Start foreground service that handles wake lock, notification and pure audio playback in background
                    SalawatPlaybackService.start(context, voiceId, audioMode, customUri)

                    try {
                        PrayerAlarmScheduler.scheduleNextSalawatExactAlarm(context)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error scheduling next Salawat alarm", e)
                    }
                }

                ACTION_MORNING_AZKAR, AzkarScheduler.ACTION_MORNING_AZKAR -> {
                    AzkarScheduler.rescheduleAzkar(context)
                    val activeWindow = AdhkarAutoTriggerManager.detectCurrentWindowForContext(context)
                    if (activeWindow != AdhkarWindow.MORNING) {
                        Log.i(TAG, "Suppressing out-of-window Morning Adhkar trigger (active=$activeWindow)")
                        return
                    }

                    // 1. Immediately launch audio playback in foreground service so sound plays right away
                    AzkarAudioService.startMorning(context)

                    // 2. Prepare and launch full-screen athkar popup
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

                    val notifHelper = AzanNotificationHelper(context)
                    notifHelper.showAzkarNotification(
                        title = "أذكار الصباح المباركة 🌅",
                        message = "حان وقت أذكار الصباح لحفظك وتحصينك اليوم بذكر الله.",
                        contentIntent = popupPendingIntent
                    )

                    try {
                        context.startActivity(popupIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting AthkarPopupActivity for morning", e)
                    }
                }

                ACTION_EVENING_AZKAR, AzkarScheduler.ACTION_EVENING_AZKAR -> {
                    AzkarScheduler.rescheduleAzkar(context)
                    val activeWindow = AdhkarAutoTriggerManager.detectCurrentWindowForContext(context)
                    if (activeWindow != AdhkarWindow.EVENING) {
                        Log.i(TAG, "Suppressing out-of-window Evening Adhkar trigger (active=$activeWindow)")
                        return
                    }

                    // 1. Immediately launch audio playback in foreground service so sound plays right away
                    AzkarAudioService.startEvening(context)

                    // 2. Prepare and launch full-screen athkar popup
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

                    val notifHelper = AzanNotificationHelper(context)
                    notifHelper.showAzkarNotification(
                        title = "أذكار المساء المباركة 🌇",
                        message = "حان وقت أذكار المساء لختام يومك بحفظ الله وذكره.",
                        contentIntent = popupPendingIntent
                    )

                    try {
                        context.startActivity(popupIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting AthkarPopupActivity for evening", e)
                    }
                }

                ACTION_RESTART_SERVICE -> {
                    Log.i(TAG, "Watchdog restart triggered: Ensuring ForegroundService & alarms are active")
                    try {
                        PrayerForegroundService.start(context)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not start foreground service from watchdog", e)
                    }
                    PrayerAlarmScheduler.rescheduleAll(context)
                    AzkarScheduler.rescheduleAzkar(context)
                }

                com.example.domain.notification.NotificationReliabilityManager.ACTION_TEST_ALARM -> {
                    val isEnglish = intent.getBooleanExtra("is_english", false)
                    Log.i(TAG, "Handling scheduled test alarm trigger")
                    com.example.domain.notification.NotificationReliabilityManager.sendTestNotification(context, isEnglish)
                }

                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                "android.intent.action.QUICKBOOT_POWERON",
                "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                    // Reschedule all alarms and start persistent notification service
                    PrayerAlarmScheduler.rescheduleAll(context)
                    AzkarScheduler.rescheduleAzkar(context)
                    PrayerForegroundService.start(context)
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
