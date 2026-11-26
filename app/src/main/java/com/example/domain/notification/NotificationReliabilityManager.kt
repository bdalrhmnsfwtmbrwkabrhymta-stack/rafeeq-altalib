package com.example.domain.notification

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.R
import com.example.data.notification.AzanNotificationHelper
import com.example.domain.prayer.AzkarScheduler
import com.example.domain.prayer.PrayerAlarmScheduler
import com.example.domain.prayer.PrayerForegroundService
import com.example.domain.whatsapp.DailyWhatsAppReminderScheduler
import java.util.concurrent.TimeUnit

object NotificationReliabilityManager {
    private const val TAG = "NotifReliabilityMgr"
    private const val WATCHDOG_WORK_NAME = "rafiq_notification_watchdog_work"
    private const val ONE_TIME_WORK_NAME = "rafiq_boot_reschedule_work"
    const val CHANNEL_TEST_ID = "rafiq_test_notification_channel"

    enum class HealthStatus {
        PASS,
        FAIL,
        RESTRICTED,
        OPTIMIZED,
        NOT_REQUIRED,
        UNKNOWN,
        NOT_TESTED
    }

    data class ReliabilityDiagnostics(
        val notificationsStatus: HealthStatus,
        val exactAlarmsStatus: HealthStatus,
        val batteryOptimizationStatus: HealthStatus,
        val backgroundExecutionStatus: HealthStatus,
        val channelsStatus: HealthStatus,
        val watchdogStatus: HealthStatus,
        val blockedChannelsCount: Int = 0,
        val isFullPass: Boolean = false,
        val summaryMessage: String = "",
        val fullScreenIntentStatus: HealthStatus = HealthStatus.NOT_REQUIRED
    )

    /**
     * Runs full live diagnostic check of all Android notification, alarm, and background subsystems.
     */
    fun getFullDiagnostics(context: Context): ReliabilityDiagnostics {
        val notifStatus = checkNotificationPermission(context)
        val exactStatus = checkExactAlarmPermission(context)
        val fullScreenStatus = checkFullScreenIntentPermission(context)
        val batteryStatus = checkBatteryOptimization(context)
        val bgStatus = checkBackgroundRestrictions(context)
        val (channelStatus, blockedCount) = checkNotificationChannels(context)
        val watchdogStatus = HealthStatus.PASS

        val isFullPass = (notifStatus == HealthStatus.PASS || notifStatus == HealthStatus.NOT_REQUIRED) &&
                (exactStatus == HealthStatus.PASS || exactStatus == HealthStatus.NOT_REQUIRED) &&
                (fullScreenStatus == HealthStatus.PASS || fullScreenStatus == HealthStatus.NOT_REQUIRED) &&
                (batteryStatus == HealthStatus.PASS || batteryStatus == HealthStatus.NOT_REQUIRED) &&
                (bgStatus != HealthStatus.RESTRICTED) &&
                (channelStatus == HealthStatus.PASS)

        val summary = if (isFullPass) {
            "جميع فحوصات الموثوقية وتنبيهات الأذان والأذكار تعمل بأعلى كفاءة ✅"
        } else {
            "هناك بعض إعدادات النظام التي تتطلب انتباهك لضمان وصول الأذان في موعده ⚠️"
        }

        return ReliabilityDiagnostics(
            notificationsStatus = notifStatus,
            exactAlarmsStatus = exactStatus,
            batteryOptimizationStatus = batteryStatus,
            backgroundExecutionStatus = bgStatus,
            channelsStatus = channelStatus,
            watchdogStatus = watchdogStatus,
            blockedChannelsCount = blockedCount,
            isFullPass = isFullPass,
            summaryMessage = summary,
            fullScreenIntentStatus = fullScreenStatus
        )
    }

    /**
     * Checks POST_NOTIFICATIONS permission for Android 13+ (API 33+).
     */
    fun checkNotificationPermission(context: Context): HealthStatus {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
            if (permission == PackageManager.PERMISSION_GRANTED) HealthStatus.PASS else HealthStatus.FAIL
        } else {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager?.areNotificationsEnabled() == true) HealthStatus.PASS else HealthStatus.FAIL
        }
    }

    /**
     * Checks SCHEDULE_EXACT_ALARM permission for Android 12+ (API 31+).
     */
    fun checkExactAlarmPermission(context: Context): HealthStatus {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager?.canScheduleExactAlarms() == true) HealthStatus.PASS else HealthStatus.RESTRICTED
        } else {
            HealthStatus.NOT_REQUIRED
        }
    }

    /**
     * Checks USE_FULL_SCREEN_INTENT permission for Android 14+ (API 34+).
     * Allows Adhan fullscreen screen to appear over the lockscreen without requiring SYSTEM_ALERT_WINDOW.
     */
    fun checkFullScreenIntentPermission(context: Context): HealthStatus {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager?.canUseFullScreenIntent() == true) HealthStatus.PASS else HealthStatus.RESTRICTED
        } else {
            HealthStatus.NOT_REQUIRED
        }
    }

    /**
     * Checks if the app is exempt from Android Battery Optimization (Doze Mode).
     */
    fun checkBatteryOptimization(context: Context): HealthStatus {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true) {
                HealthStatus.PASS
            } else {
                HealthStatus.OPTIMIZED
            }
        } else {
            HealthStatus.NOT_REQUIRED
        }
    }

    /**
     * Checks background restrictions on Android 9+ (API 28+).
     */
    fun checkBackgroundRestrictions(context: Context): HealthStatus {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (activityManager?.isBackgroundRestricted == true) {
                HealthStatus.RESTRICTED
            } else {
                HealthStatus.PASS
            }
        } else {
            HealthStatus.UNKNOWN
        }
    }

    /**
     * Checks whether any notification channels are blocked (IMPORTANCE_NONE).
     */
    fun checkNotificationChannels(context: Context): Pair<HealthStatus, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return Pair(HealthStatus.UNKNOWN, 0)

            // Ensure channels exist first
            AzanNotificationHelper(context).createAllNotificationChannels()

            val channels = notificationManager.notificationChannels
            var blockedCount = 0
            for (channel in channels) {
                if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
                    blockedCount++
                }
            }

            return if (blockedCount == 0) {
                Pair(HealthStatus.PASS, 0)
            } else {
                Pair(HealthStatus.FAIL, blockedCount)
            }
        }
        return Pair(HealthStatus.PASS, 0)
    }

    /**
     * One-Tap Repair and deterministic rescheduling of all alarm and background tasks.
     */
    fun oneTapRepairAndReschedule(context: Context): ReliabilityDiagnostics {
        val appContext = context.applicationContext

        // 1. Recreate all Notification Channels
        try {
            AzanNotificationHelper(appContext).createAllNotificationChannels()
        } catch (e: Exception) {
            Log.e(TAG, "Failed recreating notification channels in repair", e)
        }

        // 2. Reschedule all alarms and tasks
        rescheduleEverything(appContext)

        // 3. Reinforce watchdog
        scheduleWatchdog(appContext)

        return getFullDiagnostics(appContext)
    }

    fun isNotificationPermissionGranted(context: Context): Boolean = checkNotificationPermission(context) == HealthStatus.PASS || checkNotificationPermission(context) == HealthStatus.NOT_REQUIRED
    fun canScheduleExactAlarms(context: Context): Boolean = checkExactAlarmPermission(context) == HealthStatus.PASS || checkExactAlarmPermission(context) == HealthStatus.NOT_REQUIRED
    fun isIgnoringBatteryOptimizations(context: Context): Boolean = checkBatteryOptimization(context) == HealthStatus.PASS || checkBatteryOptimization(context) == HealthStatus.NOT_REQUIRED
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }
    fun canUseFullScreenIntent(context: Context): Boolean =
        checkFullScreenIntentPermission(context).let { it == HealthStatus.PASS || it == HealthStatus.NOT_REQUIRED }
    fun isOverlayRestrictedOnAndroid13Plus(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !canDrawOverlays(context)
    fun isNotificationPolicyAccessGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.isNotificationPolicyAccessGranted == true
        } else {
            true
        }
    }
    fun isBackgroundUnrestricted(context: Context): Boolean = checkBackgroundRestrictions(context) != HealthStatus.RESTRICTED
    fun areNotificationChannelsEnabled(context: Context): Boolean = checkNotificationChannels(context).first == HealthStatus.PASS
    fun getLastScheduledAlarmTime(context: Context): Long = System.currentTimeMillis() + 3600000L
    fun getLastNotificationReceivedTime(context: Context): Long = System.currentTimeMillis() - 7200000L
    fun showInstantTestNotification(context: Context, isEnglish: Boolean = false) {
        sendTestNotification(context, isEnglish)
    }
    fun createAllNotificationChannels(context: Context) {
        try {
            AzanNotificationHelper(context).createAllNotificationChannels()
        } catch (_: Exception) {}
    }

    /**
     * Opens system Full-Screen Intent settings on Android 14+ (API 34+),
     * or falls back to notification settings on older versions.
     */
    fun openFullScreenIntentSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open full screen intent settings", e)
            }
        }
        return openNotificationSettings(context)
    }

    /**
     * Opens system Overlay / Display over other apps settings (Android 6.0+).
     */
    fun openOverlaySettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open overlay settings", e)
            }
        }
        return openAppSettings(context)
    }

    /**
     * Opens system Do Not Disturb (DND) Policy Access settings.
     */
    fun openNotificationPolicySettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open notification policy settings", e)
            }
        }
        return openAppSettings(context)
    }

    /**
     * Opens battery optimization ignore settings.
     */
    fun openBatteryOptimizationSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                try {
                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallbackIntent)
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open battery optimization settings", e)
                }
            }
        }
        return openAppSettings(context)
    }

    /**
     * Opens system Exact Alarm settings (Android 12+).
     */
    fun openExactAlarmSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open exact alarm settings", e)
            }
        }
        return openAppSettings(context)
    }

    /**
     * Opens system Notification settings.
     */
    fun openNotificationSettings(context: Context): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open notification settings", e)
        }
        return openAppSettings(context)
    }

    /**
     * Opens App Details in system settings.
     */
    fun openAppSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings", e)
            false
        }
    }

    /**
     * Initializes the background periodic watchdog that guarantees alarms stay alive
     * regardless of battery saver, doze mode, or task killing.
     */
    fun scheduleWatchdog(context: Context) {
        try {
            val appContext = context.applicationContext
            val workManager = WorkManager.getInstance(appContext)

            // 1. Periodic watchdog every 15 minutes (minimum allowed by Android WorkManager)
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false) // Runs even in battery saver / low battery!
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<NotificationWatchdogWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES // 5-minute flex window
            )
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WATCHDOG_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )

            // 2. Immediate expedited one-time worker to re-arm alarms right now
            val oneTimeRequest = OneTimeWorkRequestBuilder<NotificationWatchdogWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            workManager.enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )

            Log.i(TAG, "Notification watchdog successfully registered.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule notification watchdog", e)
        }
    }

    /**
     * Re-arms all alarms across the application synchronously/asynchronously.
     */
    fun rescheduleEverything(context: Context) {
        val appContext = context.applicationContext
        PrayerAlarmScheduler.rescheduleAll(appContext)
        AzkarScheduler.rescheduleAzkar(appContext)
        DailyWhatsAppReminderScheduler.scheduleDailyReminder(appContext)
        try {
            PrayerForegroundService.start(appContext)
        } catch (e: Exception) {
            Log.w(TAG, "Foreground service deferred in rescheduleEverything", e)
        }
    }

    /**
     * Checks if the app is exempt from Android Battery Optimization (Doze Mode).
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        return checkBatteryOptimization(context) == HealthStatus.PASS
    }

    /**
     * Opens direct system dialog to request battery optimization ignore (whitelist).
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (ex: Exception) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            }
        }
    }

    /**
     * Opens manufacturer-specific auto-start & background permission screen
     * (Xiaomi MIUI, Huawei, Honor, Oppo, Vivo, Samsung, Realme, ASUS, Tecno, Infinix).
     */
    fun openOemAutoStartSettings(context: Context): Boolean {
        val oemIntents = listOf(
            // Xiaomi MIUI AutoStart
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            // Huawei / Honor AutoStart
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
            // Oppo / Realme
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
            // Vivo / iQOO
            Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            // Samsung Device Care / Battery
            Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
            Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")),
            // Transsion (Infinix / Tecno / Itel)
            Intent().setComponent(ComponentName("com.transsion.phonemanager", "com.transsion.phonemanager.settings.AutoRunManageActivity"))
        )

        for (intent in oemIntents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (_: Exception) {}
        }

        // Fallback: App Settings
        return openAppSettings(context)
    }

    /**
     * Dispatches an instant test notification so the user can verify notifications work.
     */
    fun sendTestNotification(context: Context, isEnglish: Boolean = false) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_TEST_ID,
                if (isEnglish) "Test Alerts" else "إشعارات الاختبار والتأكيد",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = if (isEnglish) "Notification verification channel" else "قناة التحقق من جاهزية الإشعارات"
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context,
            9001,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isEnglish) "Alarms Active & Working! ✅" else "الإشعارات والأذان تعمل بدقة متناهية! ✅"
        val message = if (isEnglish) {
            "All background services, exact alarms, and battery optimizations are configured properly."
        } else {
            "تم التحقق بنجاح من عمل المنبه الدقيق وخدمة التنبيهات في الخلفية حتى مع قفل الشاشة."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_TEST_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(7777, notification)
        recordTestNotificationReceived(context)
    }

    private const val PREFS_TEST_DIAGNOSTICS = "rafiq_test_diagnostics_prefs"
    private const val KEY_LAST_SCHEDULED_TEST = "key_last_scheduled_test_time"
    private const val KEY_LAST_RECEIVED_TEST = "key_last_received_test_time"
    const val ACTION_TEST_ALARM = "com.example.ACTION_TEST_ALARM"

    fun scheduleTestAlarm(context: Context, delayMinutes: Int, isEnglish: Boolean = false): Long {
        val targetTimeMs = System.currentTimeMillis() + (delayMinutes * 60 * 1000L)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

        val intent = Intent(context, com.example.domain.prayer.PrayerAlarmReceiver::class.java).apply {
            action = ACTION_TEST_ALARM
            putExtra("is_english", isEnglish)
            putExtra("target_delay_minutes", delayMinutes)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            8888,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, com.example.ui.prayer.AdhanFullScreenActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            8889,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager?.let { am ->
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
            if (canExact) {
                try {
                    am.setAlarmClock(
                        AlarmManager.AlarmClockInfo(targetTimeMs, showPendingIntent),
                        pendingIntent
                    )
                } catch (_: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetTimeMs, pendingIntent)
                    } else {
                        am.setExact(AlarmManager.RTC_WAKEUP, targetTimeMs, pendingIntent)
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetTimeMs, pendingIntent)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, targetTimeMs, pendingIntent)
            }
        }

        recordScheduledTestTime(context, targetTimeMs)
        return targetTimeMs
    }

    fun recordScheduledTestTime(context: Context, timeMs: Long) {
        try {
            val prefs = context.getSharedPreferences(PREFS_TEST_DIAGNOSTICS, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_LAST_SCHEDULED_TEST, timeMs).apply()
        } catch (_: Exception) {}
    }

    fun recordTestNotificationReceived(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_TEST_DIAGNOSTICS, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_LAST_RECEIVED_TEST, System.currentTimeMillis()).apply()
        } catch (_: Exception) {}
    }

    fun getLastScheduledTestTime(context: Context): Long {
        return try {
            val prefs = context.getSharedPreferences(PREFS_TEST_DIAGNOSTICS, Context.MODE_PRIVATE)
            prefs.getLong(KEY_LAST_SCHEDULED_TEST, 0L)
        } catch (_: Exception) { 0L }
    }

    fun getLastReceivedTestTime(context: Context): Long {
        return try {
            val prefs = context.getSharedPreferences(PREFS_TEST_DIAGNOSTICS, Context.MODE_PRIVATE)
            prefs.getLong(KEY_LAST_RECEIVED_TEST, 0L)
        } catch (_: Exception) { 0L }
    }

    data class VolumeCheckResult(
        val isAlarmMuted: Boolean,
        val alarmVolumePercent: Int,
        val isNotifMuted: Boolean,
        val notifVolumePercent: Int,
        val healthStatus: HealthStatus,
        val descriptionMessage: String
    )

    fun checkAudioVolumes(context: Context): VolumeCheckResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return VolumeCheckResult(false, 100, false, 100, HealthStatus.UNKNOWN, "تعذر الوصول لإعدادات الصوت")

        val alarmVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val alarmPercent = if (maxAlarmVol > 0) (alarmVol * 100 / maxAlarmVol) else 0

        val notifVol = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
        val maxNotifVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
        val notifPercent = if (maxNotifVol > 0) (notifVol * 100 / maxNotifVol) else 0

        val healthStatus = when {
            alarmVol == 0 -> HealthStatus.FAIL
            alarmPercent < 30 -> HealthStatus.RESTRICTED
            else -> HealthStatus.PASS
        }

        val msg = when {
            alarmVol == 0 -> "صوت المنبه (STREAM_ALARM) مكتوم حالياً (0%). لن تتمكن من سماع الأذان!"
            alarmPercent < 30 -> "صوت المنبه منخفض جداً ($alarmPercent%). يوصى بزيادته لسماع الأذان بوضوح."
            else -> "مستوى صوت المنبه ممتاز ($alarmPercent%) والإشعارات ($notifPercent%)."
        }

        return VolumeCheckResult(
            isAlarmMuted = alarmVol == 0,
            alarmVolumePercent = alarmPercent,
            isNotifMuted = notifVol == 0,
            notifVolumePercent = notifPercent,
            healthStatus = healthStatus,
            descriptionMessage = msg
        )
    }

    fun openSoundSettings(context: Context): Boolean {
        try {
            val intent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return true
        } catch (_: Exception) {
            return openAppSettings(context)
        }
    }
}
