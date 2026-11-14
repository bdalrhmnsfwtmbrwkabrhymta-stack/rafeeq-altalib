package com.example.domain.habits

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.database.AppDatabase
import com.example.data.database.HabitEntity
import com.example.data.database.HabitLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * HabitReminderReceiver:
 * High-reliability BroadcastReceiver for Habit Timers & Reminders.
 * Fired via AlarmManager (RTC_WAKEUP, setExactAndAllowWhileIdle) even when app is closed,
 * in background, or device is locked / in Doze mode.
 */
class HabitReminderReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "HabitReminderReceiver"
        const val ACTION_HABIT_REMINDER = "com.example.ACTION_HABIT_REMINDER"
        const val ACTION_MARK_HABIT_DONE = "com.example.ACTION_MARK_HABIT_DONE"

        const val EXTRA_HABIT_ID = "extra_habit_id"
        const val EXTRA_HABIT_TITLE = "extra_habit_title"
        const val EXTRA_HABIT_DESC = "extra_habit_desc"
        const val EXTRA_REMINDER_TIME = "extra_reminder_time"
        const val EXTRA_SOUND_TYPE = "extra_sound_type"
        const val EXTRA_DAYS_JSON = "extra_days_json"

        const val CHANNEL_ID_DEFAULT = "habit_reminders_channel_default"
        const val CHANNEL_ID_GENTLE = "habit_reminders_channel_gentle"
        const val CHANNEL_ID_MOTIVATIONAL = "habit_reminders_channel_motivational"
        const val CHANNEL_ID_BELL = "habit_reminders_channel_bell"
        const val CHANNEL_ID_TAKBEER = "habit_reminders_channel_takbeer"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "onReceive action: $action")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RafiqAlTalib:HabitWakeLock"
        )?.apply {
            setReferenceCounted(false)
            acquire(60 * 1000L) // 1 minute wake lock to ensure sound & notification finish
        }

        try {
            when (action) {
                ACTION_HABIT_REMINDER -> {
                    handleHabitAlarm(context, intent)
                }
                ACTION_MARK_HABIT_DONE -> {
                    handleMarkHabitDone(context, intent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling habit action: $action", e)
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (_: Exception) {}
        }
    }

    private fun handleHabitAlarm(context: Context, intent: Intent) {
        val habitId = intent.getLongExtra(EXTRA_HABIT_ID, 0L)
        val habitTitle = intent.getStringExtra(EXTRA_HABIT_TITLE) ?: "عادتك اليومية"
        val habitDesc = intent.getStringExtra(EXTRA_HABIT_DESC) ?: "حان وقت إنجاز العادة والاستمرار على الإنجاز!"
        val reminderTime = intent.getStringExtra(EXTRA_REMINDER_TIME) ?: ""
        val soundType = intent.getStringExtra(EXTRA_SOUND_TYPE) ?: "DEFAULT"
        val daysJson = intent.getStringExtra(EXTRA_DAYS_JSON) ?: "[1,2,3,4,5,6,7]"

        Log.i(TAG, "Habit Alarm Fired: #$habitId '$habitTitle' sound=$soundType time=$reminderTime")

        // 1. Show notification with sound & vibration
        showHabitNotification(context, habitId, habitTitle, habitDesc, soundType, reminderTime)

        // 2. Play audible ringtone fallback if needed
        playHabitAlertSound(context, soundType)

        // 3. Reschedule for next week / next active day
        HabitAlarmScheduler.scheduleNextOccurrence(
            context = context,
            habitId = habitId,
            title = habitTitle,
            desc = habitDesc,
            reminderTime = reminderTime,
            soundType = soundType,
            daysJson = daysJson
        )
    }

    private fun handleMarkHabitDone(context: Context, intent: Intent) {
        val habitId = intent.getLongExtra(EXTRA_HABIT_ID, 0L)
        val habitTitle = intent.getStringExtra(EXTRA_HABIT_TITLE) ?: "العادة"

        // Dismiss notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(habitId.toInt())

        // Save log to DB
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                db.habitDao().insertHabitLog(
                    HabitLogEntity(
                        habitId = habitId,
                        dateString = today
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log habit done", e)
            }
        }

        Toast.makeText(context, "عاش يا بطل! تم تسجيل إنجاز «$habitTitle» اليوم 🎉", Toast.LENGTH_LONG).show()
    }

    private fun showHabitNotification(
        context: Context,
        habitId: Long,
        title: String,
        desc: String,
        soundType: String,
        reminderTime: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = getChannelIdForSound(soundType)

        createHabitNotificationChannel(context, channelId, soundType)

        // Intent to open Habits screen in app
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("route", "habits")
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            (habitId * 10).toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to mark habit as done directly from notification
        val doneIntent = Intent(context, HabitReminderReceiver::class.java).apply {
            action = ACTION_MARK_HABIT_DONE
            putExtra(EXTRA_HABIT_ID, habitId)
            putExtra(EXTRA_HABIT_TITLE, title)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            (habitId * 10 + 1).toInt(),
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = getSoundUri(context, soundType)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("حان موعد عادتك: $title 🎯")
            .setContentText(if (desc.isNotBlank()) desc else "حان وقت إنجاز «$title» للمحافظة على استمرارك وجمع النقاط!")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("حان موعد عادتك: $title 🎯")
                    .bigText(if (desc.isNotBlank()) "$desc\n(الموعد المحدد: $reminderTime)" else "حان وقت إنجاز عادتك للمحافظة على استمرارك وجمع النقاط ومواصلة التحدي!\n(الموعد: $reminderTime)")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 400, 250, 400, 250, 600))
            .addAction(android.R.drawable.checkbox_on_background, "تم الإنجاز بنجاح ✅", donePendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "فتح التطبيق 🚀", openPendingIntent)

        notificationManager.notify(habitId.toInt().coerceAtLeast(100), builder.build())
    }

    private fun playHabitAlertSound(context: Context, soundType: String) {
        try {
            val soundUri = getSoundUri(context, soundType)
            val ringtone = RingtoneManager.getRingtone(context, soundUri)
            ringtone?.play()
        } catch (e: Exception) {
            Log.w(TAG, "Could not play fallback ringtone", e)
        }
    }

    private fun getChannelIdForSound(soundType: String): String {
        return when (soundType.uppercase()) {
            "GENTLE" -> CHANNEL_ID_GENTLE
            "MOTIVATIONAL" -> CHANNEL_ID_MOTIVATIONAL
            "BELL" -> CHANNEL_ID_BELL
            "TAKBEER" -> CHANNEL_ID_TAKBEER
            else -> CHANNEL_ID_DEFAULT
        }
    }

    private fun getSoundUri(context: Context, soundType: String): Uri {
        return when (soundType.uppercase()) {
            "BELL" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            "MOTIVATIONAL" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        }
    }

    private fun createHabitNotificationChannel(context: Context, channelId: String, soundType: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.getNotificationChannel(channelId) != null) return

            val (name, desc) = when (soundType.uppercase()) {
                "GENTLE" -> Pair("تذكيرات العادات (رنين هادئ)", "تنبيهات صوتية هادئة لمواعيد العادات اليومية")
                "MOTIVATIONAL" -> Pair("تذكيرات العادات (نشاط وتحفيز)", "تنبيهات نشطة لتحفيز إنجاز العادات")
                "BELL" -> Pair("تذكيرات العادات (جرس ومنبه)", "رنات جرس واضحة وقوية للتذكير بالعادات")
                "TAKBEER" -> Pair("تذكيرات العادات (ذكر وتكبير)", "تنبيهات بأصوات الأذكار والتكبير")
                else -> Pair("تذكيرات العادات اليومية", "تنبيهات فورية بمواعيد العادات مع رنة إشعار")
            }

            val channel = NotificationChannel(
                channelId,
                name,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = desc
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 250, 400, 250, 600)
                val soundUri = getSoundUri(context, soundType)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .build()
                setSound(soundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
