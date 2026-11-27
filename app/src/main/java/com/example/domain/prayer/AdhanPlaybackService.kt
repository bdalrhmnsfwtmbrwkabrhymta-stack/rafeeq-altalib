package com.example.domain.prayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.audio.AppAudioManager
import com.example.data.notification.AzanNotificationHelper
import com.example.ui.prayer.AdhanFullScreenActivity

/**
 * Foreground Service for robust, guaranteed Adhan playback even when the device
 * is in deep sleep, doze mode, Direct Boot, or has the screen completely locked.
 */
class AdhanPlaybackService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenWakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "RafiqAlTalib:AdhanPlaybackServiceWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(5 * 60 * 1000L) // 5 minutes max
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wakeLock", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP_ADHAN) {
            stopPlayback()
            stopSelf()
            return START_NOT_STICKY
        }

        val prayerName = intent?.getStringExtra(EXTRA_PRAYER_NAME) ?: "الصلاة"
        val cityName = intent?.getStringExtra(EXTRA_CITY_NAME) ?: "القاهرة"
        val muezzinVoice = intent?.getStringExtra(EXTRA_MUEZZIN) ?: "الأذان المصري الأصيل (MBC وإذاعة القرآن)"
        val rawResId = intent?.getIntExtra(EXTRA_RAW_RES_ID, R.raw.adhan_egypt_mbc_hq) ?: R.raw.adhan_egypt_mbc_hq
        val shouldPlayAudio = intent?.getBooleanExtra(EXTRA_SHOULD_PLAY_AUDIO, true) ?: true
        val scheduledTimeMs = intent?.getLongExtra(EXTRA_SCHEDULED_TIME_MS, 0L) ?: 0L

        acquireBriefScreenWakeLock()
        createChannel()

        val notification = buildForegroundNotification(prayerName, cityName, muezzinVoice, scheduledTimeMs)
        val startedForeground = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed in AdhanPlaybackService, using full-screen fallback", e)
            runCatching {
                AzanNotificationHelper(applicationContext).showFullScreenAdhanFallbackNotification(
                    prayerName = prayerName,
                    cityName = cityName,
                    muezzinVoice = muezzinVoice,
                    rawAudioResId = rawResId,
                    scheduledTimeMs = scheduledTimeMs
                )
            }
            false
        }

        if (!startedForeground) {
            stopPlayback()
            stopSelf()
            return START_NOT_STICKY
        }

        // Reinforce lockscreen FullScreenIntent delivery on Honor/Huawei/MIUI OEMs
        runCatching {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        }

        if (shouldPlayAudio) {
            startAudioPlayback(muezzinVoice, rawResId)
        }

        // Launch full-screen Adhan screen directly alongside Notification FullScreenIntent
        try {
            val directIntent = Intent(this, AdhanFullScreenActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra(AdhanFullScreenActivity.EXTRA_PRAYER_NAME, prayerName)
                putExtra(AdhanFullScreenActivity.EXTRA_CITY_NAME, cityName)
                putExtra(AdhanFullScreenActivity.EXTRA_MUEZZIN, muezzinVoice)
                putExtra(AdhanFullScreenActivity.EXTRA_AUTO_PLAY, false)
                if (scheduledTimeMs > 0L) {
                    putExtra(AdhanFullScreenActivity.EXTRA_SCHEDULED_TIME_MS, scheduledTimeMs)
                }
            }
            startActivity(directIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Service startActivity fallback handled by FullScreenIntent: ${e.message}")
        }

        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun acquireBriefScreenWakeLock() {
        runCatching {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            if (screenWakeLock?.isHeld == true) {
                screenWakeLock?.release()
            }
            screenWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "RafiqAlTalib:AdhanScreenWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(12_000L)
            }
        }
    }

    private fun startAudioPlayback(muezzinVoice: String, rawResId: Int) {
        try {
            AppAudioManager.stopAllNonAdhanAudio(this)
            AppAudioManager.requestAudioFocus(this, isAdhan = true)

            val downloadedFile1 = runCatching {
                AdhanAudioDownloaderManager.getDownloadedVoiceFile(this, muezzinVoice)
            }.getOrNull()
            val fileToPlay = if (downloadedFile1 != null && downloadedFile1.exists() && downloadedFile1.length() > 1024) {
                downloadedFile1
            } else {
                null
            }

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                @Suppress("DEPRECATION")
                setAudioStreamType(AudioManager.STREAM_ALARM)
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)

                if (fileToPlay != null) {
                    setDataSource(fileToPlay.absolutePath)
                } else {
                    val afd = resources.openRawResourceFd(rawResId)
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                }

                prepare()
                setVolume(1.0f, 1.0f)
                setOnCompletionListener {
                    stopPlayback()
                    stopSelf()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: $what, $extra")
                    stopPlayback()
                    stopSelf()
                    true
                }
                start()
            }
            isPlayingAdhan = true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Adhan audio playback", e)
            stopPlayback()
            stopSelf()
        }
    }

    private fun buildForegroundNotification(
        prayerName: String,
        cityName: String,
        muezzinVoice: String,
        scheduledTimeMs: Long = 0L
    ): Notification {
        val fullScreenIntent = Intent(this, AdhanFullScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AdhanFullScreenActivity.EXTRA_PRAYER_NAME, prayerName)
            putExtra(AdhanFullScreenActivity.EXTRA_CITY_NAME, cityName)
            putExtra(AdhanFullScreenActivity.EXTRA_MUEZZIN, muezzinVoice)
            putExtra(AdhanFullScreenActivity.EXTRA_AUTO_PLAY, false)
            if (scheduledTimeMs > 0L) {
                putExtra(AdhanFullScreenActivity.EXTRA_SCHEDULED_TIME_MS, scheduledTimeMs)
            }
        }
        val requestCode = 101 + (System.currentTimeMillis() % 500).toInt()
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AdhanPlaybackService::class.java).apply {
            action = ACTION_STOP_ADHAN
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "حان الآن موعد صلاة $prayerName 🕌"
        val message = "الله أكبر.. حان وقت الصلاة حسب توقيت $cityName ($muezzinVoice)"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                "إيقاف الأذان",
                stopPendingIntent
            )
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "صوت الأذان والتنبيه المباشر 🕌",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تشغيل الأذان الصوتي وإظهار شاشة الأذان على شاشة القفل"
                setSound(null, null) // Sound is handled by our MediaPlayer directly for perfect reliability
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopPlayback() {
        isPlayingAdhan = false
        try {
            mediaPlayer?.let { player ->
                try {
                    player.setOnPreparedListener(null)
                    player.setOnCompletionListener(null)
                    player.setOnErrorListener(null)
                    if (player.isPlaying) {
                        player.stop()
                    }
                    player.reset()
                    player.release()
                } catch (_: Exception) {}
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping media player", e)
        }
        runCatching { AppAudioManager.abandonAudioFocus() }
        runCatching {
            if (screenWakeLock?.isHeld == true) {
                screenWakeLock?.release()
            }
        }
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AdhanPlaybackService"
        const val CHANNEL_ID = "adhan_playback_foreground_channel"
        const val NOTIFICATION_ID = 3005

        const val ACTION_PLAY_ADHAN = "com.example.ACTION_PLAY_ADHAN"
        const val ACTION_STOP_ADHAN = "com.example.ACTION_STOP_ADHAN"

        const val EXTRA_PRAYER_NAME = "extra_prayer_name"
        const val EXTRA_CITY_NAME = "extra_city_name"
        const val EXTRA_MUEZZIN = "extra_muezzin"
        const val EXTRA_RAW_RES_ID = "extra_raw_res_id"
        const val EXTRA_SHOULD_PLAY_AUDIO = "extra_should_play_audio"
        const val EXTRA_SCHEDULED_TIME_MS = "extra_scheduled_time_ms"

        @Volatile
        var isPlayingAdhan: Boolean = false

        fun start(
            context: Context,
            prayerName: String,
            cityName: String,
            muezzinVoice: String,
            rawResId: Int,
            playAudio: Boolean = true,
            scheduledTimeMs: Long = 0L
        ) {
            try {
                val intent = Intent(context, AdhanPlaybackService::class.java).apply {
                    action = ACTION_PLAY_ADHAN
                    putExtra(EXTRA_PRAYER_NAME, prayerName)
                    putExtra(EXTRA_CITY_NAME, cityName)
                    putExtra(EXTRA_MUEZZIN, muezzinVoice)
                    putExtra(EXTRA_RAW_RES_ID, rawResId)
                    putExtra(EXTRA_SHOULD_PLAY_AUDIO, playAudio)
                    if (scheduledTimeMs > 0L) {
                        putExtra(EXTRA_SCHEDULED_TIME_MS, scheduledTimeMs)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start AdhanPlaybackService; posting full-screen fallback notification", e)
                runCatching {
                    AzanNotificationHelper(context.applicationContext).showFullScreenAdhanFallbackNotification(
                        prayerName = prayerName,
                        cityName = cityName,
                        muezzinVoice = muezzinVoice,
                        rawAudioResId = rawResId,
                        scheduledTimeMs = scheduledTimeMs
                    )
                }
            }
        }

        fun stop(context: Context) {
            try {
                isPlayingAdhan = false
                context.stopService(Intent(context, AdhanPlaybackService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop AdhanPlaybackService", e)
            }
        }
    }
}
