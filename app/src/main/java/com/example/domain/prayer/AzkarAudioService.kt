package com.example.domain.prayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.audio.AppAudioManager
import com.example.data.audio.AzanAudioPlayer

/**
 * Foreground Service for playing Morning/Evening Azkar audio.
 * Returns START_STICKY to guarantee continuous playback in the background.
 * Manages partial WakeLock and Transient Audio Focus to prevent overlap with other audio.
 */
class AzkarAudioService : Service() {

    private var azkarPlayer: AzanAudioPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AzkarAudioService onCreate")

        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "RafiqAlTalib:AzkarAudioWakeLock"
            ).apply {
                setReferenceCounted(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create WakeLock", e)
        }

        azkarPlayer = AzanAudioPlayer(this).apply {
            onCompletionCallback = {
                Log.d(TAG, "Azkar audio playback completed.")
                AppAudioManager.abandonAudioFocus()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_PLAY_MORNING
        Log.i(TAG, "AzkarAudioService onStartCommand with action: $action")

        acquireWakeLock()

        // Start as foreground service to prevent OS termination
        try {
            val notification = createNotification(action)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
        }

        when (action) {
            ACTION_PLAY_MORNING -> {
                // Stop other media, but DO NOT stop Azkar service itself
                AppAudioManager.stopQuranAndSalawatOnly(this)
                AppAudioManager.requestAudioFocus(this, isAdhan = false)
                isPlayingAzkar = true
                currentAzkarType = ACTION_PLAY_MORNING
                azkarPlayer?.playMorningAzkarSequence {
                    Log.i(TAG, "Morning Azkar sequence fully finished.")
                    isPlayingAzkar = false
                    AppAudioManager.abandonAudioFocus()
                    releaseWakeLock()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            ACTION_PLAY_EVENING -> {
                AppAudioManager.stopQuranAndSalawatOnly(this)
                AppAudioManager.requestAudioFocus(this, isAdhan = false)
                isPlayingAzkar = true
                currentAzkarType = ACTION_PLAY_EVENING
                azkarPlayer?.playEveningAzkar {
                    Log.i(TAG, "Evening Azkar finished.")
                    isPlayingAzkar = false
                    AppAudioManager.abandonAudioFocus()
                    releaseWakeLock()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            ACTION_PAUSE -> {
                azkarPlayer?.pauseAudio()
            }
            ACTION_RESUME -> {
                azkarPlayer?.resumeAudio()
            }
            ACTION_STOP -> {
                isPlayingAzkar = false
                azkarPlayer?.stopAudio()
                AppAudioManager.abandonAudioFocus()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        // Return START_STICKY to guarantee recovery and persistence
        return START_STICKY
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(20 * 60 * 1000L) // 20 minutes timeout
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WakeLock", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "أذكار الصباح والمساء والذكر 📿",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تشغيل وتلاوة أذكار الصباح والمساء في الخلفية وإظهار التنبيهات بملء الشاشة"
                setSound(null, null)
                setShowBadge(true)
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(action: String?): Notification {
        val isMorning = (action == ACTION_PLAY_MORNING)
        val title = if (isMorning) "أذكار الصباح المباركة 🌅" else "أذكار المساء المباركة 🌇"
        val message = if (isMorning) {
            "جاري تلاوة أذكار الصباح لحفظك وتحصينك بذكر الله..."
        } else {
            "جاري تلاوة أذكار المساء لختام يومك بحفظ الله وذكره..."
        }

        val fullScreenPopupIntent = Intent(this, com.example.ui.prayer.AthkarPopupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(
                com.example.ui.prayer.AthkarPopupActivity.EXTRA_ATHKAR_TYPE,
                if (isMorning) com.example.ui.prayer.AthkarPopupActivity.TYPE_MORNING else com.example.ui.prayer.AthkarPopupActivity.TYPE_EVENING
            )
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            201,
            fullScreenPopupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AzkarAudioService::class.java).apply {
            this.action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(contentIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إيقاف 🛑", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        Log.d(TAG, "AzkarAudioService onDestroy")
        isPlayingAzkar = false
        try {
            azkarPlayer?.stopAudio()
            AppAudioManager.abandonAudioFocus()
            releaseWakeLock()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AzkarAudioService"

        const val ACTION_PLAY_MORNING = "com.example.ACTION_PLAY_MORNING"
        const val ACTION_PLAY_EVENING = "com.example.ACTION_PLAY_EVENING"
        const val ACTION_PAUSE = "com.example.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.ACTION_RESUME"
        const val ACTION_STOP = "com.example.ACTION_STOP"

        const val CHANNEL_ID = "azkar_channel"
        private const val NOTIFICATION_ID = 2002

        var isPlayingAzkar: Boolean = false
            internal set
        var currentAzkarType: String = ACTION_PLAY_MORNING
            internal set

        fun startMorning(context: Context) {
            try {
                val serviceIntent = Intent(context, AzkarAudioService::class.java).apply {
                    action = ACTION_PLAY_MORNING
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Morning Azkar service", e)
            }
        }

        fun startEvening(context: Context) {
            try {
                val serviceIntent = Intent(context, AzkarAudioService::class.java).apply {
                    action = ACTION_PLAY_EVENING
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Evening Azkar service", e)
            }
        }

        fun stop(context: Context) {
            try {
                isPlayingAzkar = false
                val serviceIntent = Intent(context, AzkarAudioService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(serviceIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop Azkar service", e)
            }
        }
    }
}
