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
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.audio.AppAudioManager
import com.example.ui.prayer.AthkarPopupActivity

/**
 * Foreground Service for guaranteed Salawat playback even when the device
 * is in deep sleep, locked with a password/PIN, or in Doze mode.
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
                acquire(2 * 60 * 1000L) // 2 minutes max
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wakeLock", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP_SALAWAT) {
            stopPlayback()
            stopSelf()
            return START_NOT_STICKY
        }

        val voiceId = intent?.getIntExtra(EXTRA_VOICE_ID, 1) ?: 1
        val audioMode = intent?.getStringExtra(EXTRA_AUDIO_MODE) ?: "VOICE_FORMULA"
        val customUri = intent?.getStringExtra(EXTRA_CUSTOM_URI) ?: ""
        val voiceItem = SalawatAudioData.getVoiceById(voiceId)

        createChannel()

        val notification = buildForegroundNotification(voiceItem)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startAudioPlayback(voiceItem, audioMode, customUri)

        return START_NOT_STICKY
    }

    private fun startAudioPlayback(voiceItem: SalawatVoiceItem, audioMode: String, customUri: String) {
        try {
            AppAudioManager.stopAllNonAdhanAudio(this)
            AppAudioManager.requestAudioFocus(this, isAdhan = false)

            if (audioMode == "CUSTOM_FILE" && customUri.isNotBlank()) {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                    setDataSource(applicationContext, Uri.parse(customUri))
                    setOnPreparedListener { player ->
                        try {
                            player.start()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error starting custom audio", e)
                            stopPlayback()
                            stopSelf()
                        }
                    }
                    setOnCompletionListener {
                        stopPlayback()
                        stopSelf()
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "Custom Salawat MediaPlayer error: $what, $extra")
                        stopPlayback()
                        stopSelf()
                        true
                    }
                    prepareAsync()
                }
                return
            }

            // Check if user selected a downloaded online voice
            if (!voiceItem.isBuiltIn) {
                val downloadedFile = SalawatAudioData.getDownloadedVoiceFile(this, voiceItem.id)
                if (downloadedFile.exists() && downloadedFile.length() > 1024) {
                    SalawatAudioData.stopPlayer(mediaPlayer)
                    mediaPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                        setDataSource(downloadedFile.absolutePath)
                        setOnPreparedListener { player ->
                            try {
                                player.start()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error starting downloaded audio", e)
                                stopPlayback()
                                stopSelf()
                            }
                        }
                        setOnCompletionListener {
                            stopPlayback()
                            stopSelf()
                        }
                        setOnErrorListener { _, what, extra ->
                            Log.e(TAG, "Downloaded Salawat MediaPlayer error: $what, $extra")
                            stopPlayback()
                            stopSelf()
                            true
                        }
                        prepareAsync()
                    }
                    return
                }
            }

            // Fixed built-in voices (or fallback)
            val rawResId = if (voiceItem.rawResId != 0) voiceItem.rawResId else R.raw.salli_ala_muhammad
            SalawatAudioData.stopPlayer(mediaPlayer)
            val mp = MediaPlayer.create(applicationContext, rawResId)
            if (mp != null) {
                mediaPlayer = mp
                mp.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                mp.isLooping = false
                mp.setOnCompletionListener {
                    stopPlayback()
                    stopSelf()
                }
                mp.setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "Salawat MediaPlayer error: $what, $extra")
                    stopPlayback()
                    stopSelf()
                    true
                }
                mp.start()
            } else {
                stopPlayback()
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Salawat audio playback", e)
            stopPlayback()
            stopSelf()
        }
    }

    private fun buildForegroundNotification(voiceItem: SalawatVoiceItem): Notification {
        // Standard App Open Intent (Optional click to open main app without interrupting user)
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val openPendingIntent = if (openIntent != null) {
            PendingIntent.getActivity(
                this,
                301,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        // Stop Action Intent
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
                description = "تشغيل صوت الصلاة على النبي في الخلفية بصوت نقي بدون إزعاج الشاشة"
                setSound(null, null) // Handled by MediaPlayer directly for pure volume control
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
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wakeLock", e)
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
                Log.e(TAG, "Failed to start SalawatPlaybackService", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, SalawatPlaybackService::class.java).apply {
                    action = ACTION_STOP_SALAWAT
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop SalawatPlaybackService", e)
            }
        }
    }
}
