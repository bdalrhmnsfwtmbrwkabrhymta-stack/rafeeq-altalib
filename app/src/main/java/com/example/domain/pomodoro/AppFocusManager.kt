package com.example.domain.pomodoro

import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.example.MainActivity

data class BlockableApp(
    val id: String,
    val nameArabic: String,
    val packageName: String,
    val defaultBlocked: Boolean = true
)

object AppFocusManager {

    val DEFAULT_SOCIAL_APPS = listOf(
        BlockableApp("whatsapp", "واتساب", "com.whatsapp"),
        BlockableApp("instagram", "إنستغرام", "com.instagram.android"),
        BlockableApp("facebook", "فيسبوك", "com.facebook.katana"),
        BlockableApp("messenger", "ماسنجر", "com.facebook.orca"),
        BlockableApp("tiktok", "تيك توك", "com.zhiliaoapp.musically"),
        BlockableApp("youtube", "يوتيوب", "com.google.android.youtube"),
        BlockableApp("snapchat", "سناب شات", "com.snapchat.android"),
        BlockableApp("telegram", "تيليجرام", "org.telegram.messenger"),
        BlockableApp("twitter", "منصة X (تويتر)", "com.twitter.android"),
        BlockableApp("netflix", "نتفليكس", "com.netflix.mediaclient")
    )

    private var previousRingerMode: Int? = null
    private var previousInterruptionFilter: Int? = null

    /**
     * Check if the app has permission to access Usage Stats
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Open Usage Access Settings for user to enable permission
     */
    fun openUsageStatsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Check if Do Not Disturb (Notification Policy) access is granted
     */
    fun hasDndPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            return notificationManager?.isNotificationPolicyAccessGranted == true
        }
        return true
    }

    /**
     * Open DND Settings
     */
    fun openDndSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Enable Do Not Disturb & Mute Ringtone/Calls 100%
     */
    fun enableDoNotDisturb(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                if (previousRingerMode == null) {
                    previousRingerMode = audioManager.ringerMode
                }
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (notificationManager != null && notificationManager.isNotificationPolicyAccessGranted) {
                    if (previousInterruptionFilter == null) {
                        previousInterruptionFilter = notificationManager.currentInterruptionFilter
                    }
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Disable Do Not Disturb & Restore Ringer
     */
    fun disableDoNotDisturb(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null && previousRingerMode != null) {
                audioManager.ringerMode = previousRingerMode ?: AudioManager.RINGER_MODE_NORMAL
                previousRingerMode = null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (notificationManager != null && notificationManager.isNotificationPolicyAccessGranted) {
                    notificationManager.setInterruptionFilter(
                        previousInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
                    )
                    previousInterruptionFilter = null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Detect current foreground package using UsageStats
     */
    fun getForegroundPackageName(context: Context): String? {
        if (!hasUsageStatsPermission(context)) return null
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 10000 // Last 10 seconds

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var lastForegroundPackage: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED || event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastForegroundPackage = event.packageName
            }
        }
        return lastForegroundPackage
    }

    /**
     * Bring MainActivity to front when user opens a blocked app
     */
    fun bringAppToFront(context: Context, blockedAppName: String) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra("BLOCKED_APP_NAME", blockedAppName)
                putExtra("NAVIGATE_TO", "pomodoro")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
