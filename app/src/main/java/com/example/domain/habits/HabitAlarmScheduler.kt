package com.example.domain.habits

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.example.MainActivity
import com.example.data.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * HabitAlarmScheduler:
 * Schedules and persists exact alarms for habits across device reboots and app restarts.
 */
object HabitAlarmScheduler {

    private const val TAG = "HabitAlarmScheduler"
    private const val PREFS_NAME = "habit_alarm_schedules"
    private const val KEY_CONFIGS_JSON = "configs_json"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Schedule exact reminders for a habit across selected days and times.
     */
    fun scheduleHabitReminders(
        context: Context,
        habitId: Long,
        title: String,
        description: String,
        selectedDays: List<Int>, // 1..7
        reminders: List<String>, // e.g. ["05:00 م", "09:30 ص"]
        soundType: String = "DEFAULT"
    ) {
        if (reminders.isEmpty()) {
            cancelHabitReminders(context, habitId)
            return
        }

        // Cancel previous alarms for this habit first to avoid ghost triggers
        cancelHabitReminders(context, habitId)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val daysJson = JSONArray(selectedDays).toString()

        reminders.forEachIndexed { timeIndex, timeStr ->
            val parsedTime = parseTimeArabic(timeStr)
            val hour = parsedTime.first
            val minute = parsedTime.second

            val nextTriggerMs = calculateNextTriggerMillis(hour, minute, selectedDays)
            val requestCode = generateRequestCode(habitId, timeIndex)

            val intent = Intent(context, HabitReminderReceiver::class.java).apply {
                action = HabitReminderReceiver.ACTION_HABIT_REMINDER
                putExtra(HabitReminderReceiver.EXTRA_HABIT_ID, habitId)
                putExtra(HabitReminderReceiver.EXTRA_HABIT_TITLE, title)
                putExtra(HabitReminderReceiver.EXTRA_HABIT_DESC, description)
                putExtra(HabitReminderReceiver.EXTRA_REMINDER_TIME, timeStr)
                putExtra(HabitReminderReceiver.EXTRA_SOUND_TYPE, soundType)
                putExtra(HabitReminderReceiver.EXTRA_DAYS_JSON, daysJson)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            setExactAlarm(alarmManager, nextTriggerMs, pendingIntent, context)
            Log.i(TAG, "Scheduled Habit '$title' (#$habitId) at $timeStr ($nextTriggerMs ms), reqCode=$requestCode")
        }

        // Save config in preferences
        saveHabitConfig(
            context = context,
            habitId = habitId,
            title = title,
            desc = description,
            days = selectedDays,
            reminders = reminders,
            soundType = soundType
        )
    }

    /**
     * Schedule next occurrence after a reminder fires.
     */
    fun scheduleNextOccurrence(
        context: Context,
        habitId: Long,
        title: String,
        desc: String,
        reminderTime: String,
        soundType: String,
        daysJson: String
    ) {
        val days = try {
            val jsonArr = JSONArray(daysJson)
            val list = mutableListOf<Int>()
            for (i in 0 until jsonArr.length()) {
                list.add(jsonArr.getInt(i))
            }
            list
        } catch (_: Exception) {
            listOf(1, 2, 3, 4, 5, 6, 7)
        }

        val parsedTime = parseTimeArabic(reminderTime)
        val hour = parsedTime.first
        val minute = parsedTime.second

        // Force strictly future occurrence (> 1 minute from now)
        val nextTriggerMs = calculateNextTriggerMillis(hour, minute, days, skipToday = true)
        val requestCode = generateRequestCode(habitId, 0)

        val intent = Intent(context, HabitReminderReceiver::class.java).apply {
            action = HabitReminderReceiver.ACTION_HABIT_REMINDER
            putExtra(HabitReminderReceiver.EXTRA_HABIT_ID, habitId)
            putExtra(HabitReminderReceiver.EXTRA_HABIT_TITLE, title)
            putExtra(HabitReminderReceiver.EXTRA_HABIT_DESC, desc)
            putExtra(HabitReminderReceiver.EXTRA_REMINDER_TIME, reminderTime)
            putExtra(HabitReminderReceiver.EXTRA_SOUND_TYPE, soundType)
            putExtra(HabitReminderReceiver.EXTRA_DAYS_JSON, daysJson)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        setExactAlarm(alarmManager, nextTriggerMs, pendingIntent, context)
        Log.i(TAG, "Scheduled next occurrence for Habit #$habitId at $nextTriggerMs")
    }

    /**
     * Cancel all alarms for a habit.
     */
    fun cancelHabitReminders(context: Context, habitId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        for (timeIndex in 0..10) {
            val requestCode = generateRequestCode(habitId, timeIndex)
            val intent = Intent(context, HabitReminderReceiver::class.java).apply {
                action = HabitReminderReceiver.ACTION_HABIT_REMINDER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }

        removeHabitConfig(context, habitId)
        Log.i(TAG, "Cancelled all alarms for Habit #$habitId")
    }

    /**
     * Reschedule all active habits from preferences.
     */
    fun rescheduleAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = getPrefs(context)
                val rawJson = prefs.getString(KEY_CONFIGS_JSON, "{}") ?: "{}"
                val jsonObject = JSONObject(rawJson)

                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val item = jsonObject.optJSONObject(key) ?: continue

                    val habitId = item.optLong("habitId")
                    val title = item.optString("title")
                    val desc = item.optString("desc")
                    val soundType = item.optString("soundType", "DEFAULT")

                    val daysArray = item.optJSONArray("days")
                    val daysList = mutableListOf<Int>()
                    if (daysArray != null) {
                        for (i in 0 until daysArray.length()) {
                            daysList.add(daysArray.getInt(i))
                        }
                    }

                    val remindersArray = item.optJSONArray("reminders")
                    val remindersList = mutableListOf<String>()
                    if (remindersArray != null) {
                        for (i in 0 until remindersArray.length()) {
                            remindersList.add(remindersArray.getString(i))
                        }
                    }

                    if (habitId > 0 && remindersList.isNotEmpty()) {
                        scheduleHabitReminders(
                            context = context,
                            habitId = habitId,
                            title = title,
                            description = desc,
                            selectedDays = if (daysList.isNotEmpty()) daysList else listOf(1, 2, 3, 4, 5, 6, 7),
                            reminders = remindersList,
                            soundType = soundType
                        )
                    }
                }
                Log.i(TAG, "Rescheduled all active habit alarms from preferences")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reschedule all habit alarms", e)
            }
        }
    }

    private fun setExactAlarm(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        operation: PendingIntent,
        context: Context
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        // Use AlarmClockInfo on modern devices to ensure absolute priority
                        val showIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("route", "habits")
                        }
                        val showPendingIntent = PendingIntent.getActivity(
                            context,
                            0,
                            showIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
                        alarmManager.setAlarmClock(alarmClockInfo, operation)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SCHEDULE_EXACT_ALARM not allowed, falling back to inexact idle alarm", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting alarm", e)
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }

    private fun generateRequestCode(habitId: Long, timeIndex: Int): Int {
        return ((habitId % 100000) * 10 + (timeIndex % 10)).toInt() + 50000
    }

    /**
     * Parse Arabic time format like "05:00 م" or "09:30 ص" into 24-hr (hour, minute).
     */
    fun parseTimeArabic(timeStr: String): Pair<Int, Int> {
        val trimmed = timeStr.trim()
        val isPm = trimmed.contains("م") || trimmed.contains("PM", ignoreCase = true)
        val clean = trimmed
            .replace("م", "")
            .replace("ص", "")
            .replace("PM", "", ignoreCase = true)
            .replace("AM", "", ignoreCase = true)
            .trim()

        val parts = clean.split(":")
        var hour = parts.firstOrNull()?.toIntOrNull() ?: 12
        val minute = if (parts.size > 1) parts[1].trim().toIntOrNull() ?: 0 else 0

        if (isPm) {
            if (hour < 12) hour += 12
        } else {
            if (hour == 12) hour = 0
        }

        return Pair(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }

    /**
     * Calculates the nearest upcoming epoch millisecond for given hour, minute and selected days.
     * selectedDays map:
     * 1: Mon, 2: Tue, 3: Wed, 4: Thu, 5: Fri, 6: Sat, 7: Sun
     */
    private fun calculateNextTriggerMillis(
        hour: Int,
        minute: Int,
        selectedDays: List<Int>,
        skipToday: Boolean = false
    ): Long {
        val now = Calendar.getInstance()

        // Check the next 14 days
        for (dayOffset in (if (skipToday) 1 else 0)..14) {
            val target = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (target.timeInMillis > now.timeInMillis + 5000L) {
                val calDayOfWeek = target.get(Calendar.DAY_OF_WEEK)
                // Convert Calendar.DAY_OF_WEEK (Sun=1, Mon=2, ... Sat=7)
                // to our 1..7 standard (Mon=1, Tue=2, Wed=3, Thu=4, Fri=5, Sat=6, Sun=7)
                val standardDay = when (calDayOfWeek) {
                    Calendar.MONDAY -> 1
                    Calendar.TUESDAY -> 2
                    Calendar.WEDNESDAY -> 3
                    Calendar.THURSDAY -> 4
                    Calendar.FRIDAY -> 5
                    Calendar.SATURDAY -> 6
                    Calendar.SUNDAY -> 7
                    else -> 1
                }

                if (selectedDays.contains(standardDay)) {
                    return target.timeInMillis
                }
            }
        }

        // Fallback: 24 hours from now
        return now.timeInMillis + 24 * 60 * 60 * 1000L
    }

    private fun saveHabitConfig(
        context: Context,
        habitId: Long,
        title: String,
        desc: String,
        days: List<Int>,
        reminders: List<String>,
        soundType: String
    ) {
        try {
            val prefs = getPrefs(context)
            val raw = prefs.getString(KEY_CONFIGS_JSON, "{}") ?: "{}"
            val root = JSONObject(raw)

            val item = JSONObject().apply {
                put("habitId", habitId)
                put("title", title)
                put("desc", desc)
                put("soundType", soundType)
                put("days", JSONArray(days))
                put("reminders", JSONArray(reminders))
            }

            root.put(habitId.toString(), item)
            prefs.edit().putString(KEY_CONFIGS_JSON, root.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving habit config", e)
        }
    }

    private fun removeHabitConfig(context: Context, habitId: Long) {
        try {
            val prefs = getPrefs(context)
            val raw = prefs.getString(KEY_CONFIGS_JSON, "{}") ?: "{}"
            val root = JSONObject(raw)
            root.remove(habitId.toString())
            prefs.edit().putString(KEY_CONFIGS_JSON, root.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error removing habit config", e)
        }
    }
}
