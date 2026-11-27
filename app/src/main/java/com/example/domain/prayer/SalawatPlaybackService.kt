package com.example.domain.prayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.audio.AppAudioManager
import com.example.data.notification.AzanNotificationHelper

/**
 * Lightweight Foreground Service for smooth, zero-lag hourly Salawat playback
 * even when the device is in deep sleep, Direct Boot, or Doze mode.
 */
class SalawatPlaybackService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "RafiqAlTalib:SalawatPlaybackServiceWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(45_000L) // Lightweight 45s max wake lock
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wakeLock", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP_SALAWAT) {
            stopPlayback()
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
            return START_NOT_STICKY
        }

        val voiceId = intent?.getIntExtra(EXTRA_VOICE_ID, 1) ?: 1
        val audioMode = intent?.getStringExtra(EXTRA_AUDIO_MODE) ?: "VOICE_FORMULA"
        val customUri = intent?.getStringExtra(EXTRA_CUSTOM_URI) ?: ""
        val voiceItem = SalawatAudioData.getVoiceById(voiceId)

        createChannel()

        val notification = buildForegroundNotification(voiceItem)
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
            Log.e(TAG, "startForeground failed in SalawatPlaybackService; using notification fallback", e)
            runCatching {
                AzanNotificationHelper(applicationContext).showSalawatNotification(
                    title = "صلِّ على النبي ﷺ 📿",
                    message = "${voiceItem.fullText} ✨",
                    rawAudioResId = if (voiceItem.rawResId != 0) voiceItem.rawResId else R.raw.salli_ala_muhammad
                )
            }
            false
        }

        if (!startedForeground) {
            stopPlayback()
            stopSelf()
            return START_NOT_STICKY
        }

        // Never interrupt an active Adhan or Azkar recitation
        if (AdhanPlaybackService.isPlayingAdhan || AzkarAudioService.isPlayingAzkar) {
            Log.i(TAG, "Skipping Salawat audio because Adhan or Azkar is actively playing.")
            stopPlayback()
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
            return START_NOT_STICKY
        }

        startAudioPlayback(voiceItem, audioMode, customUri)

        return START_NOT_STICKY
    }

    private fun startAudioPlayback(voiceItem: SalawatVoiceItem, audioMode: String, customUri: String) {
        try {
            AppAudioManager.requestAudioFocus(this, isAdhan = false)

            val speechAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            if (audioMode == "CUSTOM_FILE" && customUri.isNotBlank()) {
                SalawatAudioData.stopPlayer(mediaPlayer)
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(speechAttributes)
                    setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                    setDataSource(applicationContext, Uri.parse(customUri))
                    setOnPreparedListener { player ->
                        runCatching { player.start() }.onFailure { finishService() }
                    }
                    setOnCompletionListener { finishService() }
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "Custom Salawat MediaPlayer error: $what, $extra")
                        finishService()
                        true
                    }
                    prepareAsync()
                }
                return
            }

            // Check if user selected a downloaded online voice
            if (!voiceItem.isBuiltIn) {
                val downloadedFile = runCatching {
                    SalawatAudioData.getDownloadedVoiceFile(this, voiceItem.id)
                }.getOrNull()
                if (downloadedFile != null && downloadedFile.exists() && downloadedFile.length() > 1024) {
                    SalawatAudioData.stopPlayer(mediaPlayer)
                    mediaPlayer = MediaPlayer().apply {
                        setAudioAttributes(speechAttributes)
                        setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                        setDataSource(downloadedFile.absolutePath)
                        setOnPreparedListener { player ->
                            runCatching { player.start() }.onFailure { finishService() }
                        }
                        setOnCompletionListener { finishService() }
                        setOnErrorListener { _, what, extra ->
                            Log.e(TAG, "Downloaded Salawat MediaPlayer error: $what, $extra")
                            finishService()
                            true
                        }
                        prepareAsync()
                    }
                    return
                }
            }

            // Built-in raw voice prepared asynchronously so the main thread never lags
            val rawResId = if (voiceItem.rawResId != 0) voiceItem.rawResId else R.raw.salli_ala_muhammad
            SalawatAudioData.stopPlayer(mediaPlayer)
            val afd = resources.openRawResourceFd(rawResId)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(speechAttributes)
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                isLooping = false
                setOnPreparedListener { player ->
                    runCatching { player.start() }.onFailure { finishService() }
                }
                setOnCompletionListener { finishService() }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "Salawat MediaPlayer error: $what, $extra")
                    finishService()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Salawat audio playback", e)
            finishService()
        }
    }

    private fun finishService() {
        stopPlayback()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun buildForegroundNotification(voiceItem: SalawatVoiceItem): Notification {
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val openPendingIntent = if (openIntent != null) {
            PendingIntent.getActivity(
                this,
                301,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val stopIntent = Intent(this, SalawatPlaybackService::class.java).apply {
            action = ACTION_STOP_SALAWAT
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            302,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "صلِّ على النبي ﷺ 📿"
        val message = "${voiceItem.fullText} ✨"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(false)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "إغلاق 🛑",
                stopPendingIntent
            )

        if (openPendingIntent != null) {
            builder.setContentIntent(openPendingIntent)
        }

        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "الصلاة على النبي ﷺ 📿",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تشغيل صوت الصلاة على النبي تلقائياً كل ساعة بدون لاج أو إزعاج"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopPlayback() {
        try {
            SalawatAudioData.stopTts()
            SalawatAudioData.stopPlayer(mediaPlayer)
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping media player", e)
        }
        runCatching { AppAudioManager.abandonAudioFocus() }
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
        private const val TAG = "SalawatPlaybackService"
        const val CHANNEL_ID = "salawat_playback_foreground_channel"
        const val NOTIFICATION_ID = 3006

        const val ACTION_PLAY_SALAWAT = "com.example.ACTION_PLAY_SALAWAT"
        const val ACTION_STOP_SALAWAT = "com.example.ACTION_STOP_SALAWAT"

        const val EXTRA_VOICE_ID = "extra_voice_id"
        const val EXTRA_AUDIO_MODE = "extra_audio_mode"
        const val EXTRA_CUSTOM_URI = "extra_custom_uri"

        fun start(
            context: Context,
            voiceId: Int,
            audioMode: String = "VOICE_FORMULA",
            customUri: String = ""
        ) {
            try {
                val intent = Intent(context, SalawatPlaybackService::class.java).apply {
                    action = ACTION_PLAY_SALAWAT
                    putExtra(EXTRA_VOICE_ID, voiceId)
                    putExtra(EXTRA_AUDIO_MODE, audioMode)
                    putExtra(EXTRA_CUSTOM_URI, customUri)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SalawatPlaybackService; using notification fallback", e)
                runCatching {
                    val voiceItem = SalawatAudioData.getVoiceById(voiceId)
                    AzanNotificationHelper(context.applicationContext).showSalawatNotification(
                        title = "صلِّ على النبي ﷺ 📿",
                        message = "${voiceItem.fullText} ✨",
                        rawAudioResId = if (voiceItem.rawResId != 0) voiceItem.rawResId else R.raw.salli_ala_muhammad
                    )
                }
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, SalawatPlaybackService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop SalawatPlaybackService", e)
            }
        }
    }
}
