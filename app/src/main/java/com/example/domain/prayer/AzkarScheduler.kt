package com.example.domain.prayer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Dedicated scheduler for Morning & Evening Azkar.
 * Guarantees execution even when app is killed or device is in Doze mode
 * via AlarmManager.setExactAndAllowWhileIdle() and RTC_WAKEUP.
 */
object AzkarScheduler {

    private const val TAG = "AzkarScheduler"

    const val REQ_MORNING_AZKAR = 108
    const val REQ_EVENING_AZKAR = 109

    const val ACTION_MORNING_AZKAR = "com.example.ACTION_MORNING_AZKAR"
    const val ACTION_EVENING_AZKAR = "com.example.ACTION_EVENING_AZKAR"

    /**
     * Reschedules both Morning (default 09:00 AM) and Evening (default 05:00 PM) Azkar
     * from user preferences (or Device-Protected Storage when device is still locked after reboot).
     */
    fun rescheduleAzkar(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            rescheduleAzkarSuspending(context)
        }
    }

    suspend fun rescheduleAzkarSuspending(context: Context) {
        try {
            val appContext = context.applicationContext
            val snapshot = if (PrayerDirectBootStore.isUserUnlocked(appContext)) {
                runCatching {
                    val prefsRepo = UserPreferencesRepository(appContext)
                    val settings = prefsRepo.userSettingsFlow.first()
                    PrayerDirectBootStore.saveFromSettings(appContext, settings)
                    PrayerDirectBootStore.loadSnapshot(appContext)
                }.getOrElse { PrayerDirectBootStore.loadSnapshot(appContext) }
            } else {
                PrayerDirectBootStore.loadSnapshot(appContext)
            }

            scheduleAzkar(
                context = appContext,
                morningEnabled = snapshot.isMorningAzkarEnabled,
                eveningEnabled = snapshot.isEveningAzkarEnabled,
                morningHour = snapshot.morningAzkarHour,
                morningMinute = snapshot.morningAzkarMinute,
                eveningHour = snapshot.eveningAzkarHour,
                eveningMinute = snapshot.eveningAzkarMinute
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reschedule Azkar", e)
        }
    }

    /**
     * Schedules or cancels Morning and Evening Azkar alarms.
     */
    fun scheduleAzkar(
        context: Context,
        morningEnabled: Boolean,
        eveningEnabled: Boolean,
        morningHour: Int = 9,
        morningMinute: Int = 0,
        eveningHour: Int = 17,
        eveningMinute: Int = 0
    ) {
        if (morningEnabled) {
            scheduleMorningAzkar(context, morningHour, morningMinute)
        } else {
            cancelMorningAzkar(context)
        }

        if (eveningEnabled) {
            scheduleEveningAzkar(context, eveningHour, eveningMinute)
        } else {
            cancelEveningAzkar(context)
        }
    }

    /**
     * Schedules the next Morning Azkar alarm (default 09:00 AM).
     * If the time today has already passed, schedules for tomorrow (+1 day).
     */
    fun scheduleMorningAzkar(context: Context, hour: Int = 9, minute: Int = 0) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_MORNING_AZKAR
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQ_MORNING_AZKAR,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val targetCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If time has already passed today, advance to next day
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val triggerTimeMs = targetCalendar.timeInMillis
        Log.i(TAG, "Scheduling Morning Azkar for timestamp: $triggerTimeMs (${targetCalendar.time})")

        scheduleMorningAzkarAtTimestamp(context, triggerTimeMs)
    }

    fun scheduleMorningAzkarAtTimestamp(context: Context, triggerTimeMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_MORNING_AZKAR
            putExtra(PrayerAlarmReceiver.EXTRA_SCHEDULED_TIME_MS, triggerTimeMs)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQ_MORNING_AZKAR,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleExactAlarm(context, alarmManager, triggerTimeMs, pendingIntent, isMorning = true)
    }

    /**
     * Schedules the next Evening Azkar alarm (default 05:00 PM / 17:00).
     * If the time today has already passed, schedules for tomorrow (+1 day).
     */
    fun scheduleEveningAzkar(context: Context, hour: Int = 17, minute: Int = 0) {
        val targetCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If time has already passed today (or is within 5s of now), advance to next day
            if (timeInMillis <= System.currentTimeMillis() + 5_000L) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val triggerTimeMs = targetCalendar.timeInMillis
        Log.i(TAG, "Scheduling Evening Azkar for timestamp: $triggerTimeMs (${targetCalendar.time})")

        scheduleEveningAzkarAtTimestamp(context, triggerTimeMs)
    }

    fun scheduleEveningAzkarAtTimestamp(context: Context, triggerTimeMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_EVENING_AZKAR
            putExtra(PrayerAlarmReceiver.EXTRA_SCHEDULED_TIME_MS, triggerTimeMs)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQ_EVENING_AZKAR,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleExactAlarm(context, alarmManager, triggerTimeMs, pendingIntent, isMorning = false)
    }

    /**
     * Cancels scheduled Morning Azkar alarm.
     */
    fun cancelMorningAzkar(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_MORNING_AZKAR
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQ_MORNING_AZKAR,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Morning Azkar alarm cancelled.")
        }
    }

    /**
     * Cancels scheduled Evening Azkar alarm.
     */
    fun cancelEveningAzkar(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_EVENING_AZKAR
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQ_EVENING_AZKAR,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Evening Azkar alarm cancelled.")
        }
    }

    private fun scheduleExactAlarm(
        context: Context,
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        operation: PendingIntent,
        isMorning: Boolean
    ) {
        val showIntent = Intent(context, com.example.ui.prayer.AthkarPopupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(
                com.example.ui.prayer.AthkarPopupActivity.EXTRA_ATHKAR_TYPE,
                if (isMorning) com.example.ui.prayer.AthkarPopupActivity.TYPE_MORNING else com.example.ui.prayer.AthkarPopupActivity.TYPE_EVENING
            )
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            if (isMorning) 401 else 402,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        try {
            if (canExact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
                    alarmManager.setAlarmClock(alarmClockInfo, operation)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
                }
            }
        } catch (e: SecurityException) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
                }
            } catch (_: Exception) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule exact alarm, falling back", e)
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }
}
