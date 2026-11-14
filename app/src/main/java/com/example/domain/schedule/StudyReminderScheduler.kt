package com.example.domain.schedule

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.MainActivity
import com.example.data.database.ScheduleSessionEntity
import com.example.data.database.SubjectEntity
import java.util.Calendar
import java.util.concurrent.TimeUnit

object StudyReminderScheduler {

    const val TAG_STUDY_REMINDER = "study_lecture_reminder"

    /**
     * Map app dayOfWeek (1=Sat, 2=Sun, 3=Mon, 4=Tue, 5=Wed, 6=Thu, 7=Fri)
     * to Java Calendar DAY_OF_WEEK constant.
     */
    fun mapAppDayToCalendarDay(appDay: Int): Int {
        return when (appDay) {
            1 -> Calendar.SATURDAY
            2 -> Calendar.SUNDAY
            3 -> Calendar.MONDAY
            4 -> Calendar.TUESDAY
            5 -> Calendar.WEDNESDAY
            6 -> Calendar.THURSDAY
            7 -> Calendar.FRIDAY
            else -> Calendar.SATURDAY
        }
    }

    /**
     * Calculate milliseconds delay from now until next occurrence of the specified day and time,
     * offset by [minutesBefore].
     */
    fun calculateInitialDelayMillis(dayOfWeek: Int, startTime: String, minutesBefore: Int): Long {
        val parts = startTime.split(":").mapNotNull { it.trim().toIntOrNull() }
        val hour = if (parts.isNotEmpty()) parts[0] else 8
        val minute = if (parts.size > 1) parts[1] else 0

        val now = Calendar.getInstance()
        val targetCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, mapAppDayToCalendarDay(dayOfWeek))
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, -minutesBefore)
        }

        // If time has already passed this week, schedule for next week
        if (targetCal.timeInMillis <= now.timeInMillis) {
            targetCal.add(Calendar.DAY_OF_YEAR, 7)
        }

        val delay = targetCal.timeInMillis - now.timeInMillis
        return delay.coerceAtLeast(1000L)
    }

    /**
     * Schedule a lecture reminder using WorkManager
     */
    fun scheduleSessionReminder(
        context: Context,
        sessionId: Long,
        subjectName: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        roomOrLink: String = "",
        note: String = "",
        minutesBefore: Int = 10
    ) {
        val delayMillis = calculateInitialDelayMillis(dayOfWeek, startTime, minutesBefore)

        val inputData = workDataOf(
            LectureNotificationWorker.KEY_SESSION_ID to sessionId,
            LectureNotificationWorker.KEY_SUBJECT_NAME to subjectName,
            LectureNotificationWorker.KEY_START_TIME to startTime,
            LectureNotificationWorker.KEY_END_TIME to endTime,
            LectureNotificationWorker.KEY_ROOM_OR_LINK to roomOrLink,
            LectureNotificationWorker.KEY_NOTE to note,
            LectureNotificationWorker.KEY_DAY_OF_WEEK to dayOfWeek,
            LectureNotificationWorker.KEY_MINUTES_BEFORE to minutesBefore
        )

        val workRequest = OneTimeWorkRequestBuilder<LectureNotificationWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .addTag(TAG_STUDY_REMINDER)
            .addTag("session_$sessionId")
            .build()

        try {
            WorkManager.getInstance(context.applicationContext ?: context).enqueueUniqueWork(
                "lecture_reminder_$sessionId",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        } catch (t: Throwable) {
            android.util.Log.w("StudyReminderScheduler", "WorkManager unavailable: ${t.message}")
        }
    }

    /**
     * Reschedule for the next week's occurrence
     */
    fun scheduleNextWeeklyOccurrence(
        context: Context,
        sessionId: Long,
        subjectName: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        roomOrLink: String,
        note: String,
        minutesBefore: Int
    ) {
        val delayMillis = calculateInitialDelayMillis(dayOfWeek, startTime, minutesBefore)

        val inputData = workDataOf(
            LectureNotificationWorker.KEY_SESSION_ID to sessionId,
            LectureNotificationWorker.KEY_SUBJECT_NAME to subjectName,
            LectureNotificationWorker.KEY_START_TIME to startTime,
            LectureNotificationWorker.KEY_END_TIME to endTime,
            LectureNotificationWorker.KEY_ROOM_OR_LINK to roomOrLink,
            LectureNotificationWorker.KEY_NOTE to note,
            LectureNotificationWorker.KEY_DAY_OF_WEEK to dayOfWeek,
            LectureNotificationWorker.KEY_MINUTES_BEFORE to minutesBefore
        )

        val workRequest = OneTimeWorkRequestBuilder<LectureNotificationWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .addTag(TAG_STUDY_REMINDER)
            .addTag("session_$sessionId")
            .build()

        try {
            WorkManager.getInstance(context.applicationContext ?: context).enqueueUniqueWork(
                "lecture_reminder_$sessionId",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        } catch (t: Throwable) {
            android.util.Log.w("StudyReminderScheduler", "WorkManager unavailable: ${t.message}")
        }
    }

    /**
     * Cancel a specific session reminder
     */
    fun cancelSessionReminder(context: Context, sessionId: Long) {
        try {
            WorkManager.getInstance(context.applicationContext ?: context).cancelUniqueWork("lecture_reminder_$sessionId")
        } catch (t: Throwable) {
            android.util.Log.w("StudyReminderScheduler", "WorkManager unavailable: ${t.message}")
        }
    }

    /**
     * Cancel all study lecture reminders
     */
    fun cancelAllReminders(context: Context) {
        try {
            WorkManager.getInstance(context.applicationContext ?: context).cancelAllWorkByTag(TAG_STUDY_REMINDER)
        } catch (t: Throwable) {
            android.util.Log.w("StudyReminderScheduler", "WorkManager unavailable: ${t.message}")
        }
    }

    /**
     * Reschedule all active study sessions
     */
    fun rescheduleAll(
        context: Context,
        sessions: List<ScheduleSessionEntity>,
        subjects: Map<Long, SubjectEntity>,
        isEnabled: Boolean,
        minutesBefore: Int = 10
    ) {
        // First cancel all previous works
        cancelAllReminders(context)

        if (!isEnabled || sessions.isEmpty()) return

        // Reschedule each session
        for (session in sessions) {
            val subject = subjects[session.subjectId]
            val subjectName = subject?.name ?: "مادة دراسية"
            scheduleSessionReminder(
                context = context,
                sessionId = session.id,
                subjectName = subjectName,
                dayOfWeek = session.dayOfWeek,
                startTime = session.startTimeString,
                endTime = session.endTimeString,
                roomOrLink = session.roomOrLink,
                note = session.note,
                minutesBefore = minutesBefore
            )
        }
    }

    /**
     * Trigger an immediate test notification so the user can verify sound, vibration, and display
     */
    fun triggerTestNotification(
        context: Context,
        subjectName: String = "الفيزياء ⚡",
        startTime: String = "10:00",
        endTime: String = "11:30",
        room: String = "المدرج الرئيسي / قاعة 3",
        minutesBefore: Int = 10
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                LectureNotificationWorker.CHANNEL_ID,
                "تنبيهات المحاضرات والدروس 📚",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات تذكيرية ذكية بمواعيد الحصص والمحاضرات الدراسية"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "schedule")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            99999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, LectureNotificationWorker.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⏰ اختبار تنبيه المحاضرة: $subjectName")
            .setContentText("تبدأ خلال $minutesBefore دقيقة (الساعة $startTime) | $room")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "🔔 تجربة تنبيه المحاضرات بنجاح عبر WorkManager!\n" +
                            "⏰ موعد المحاضرة: $startTime - $endTime\n" +
                            "📍 المكان: $room\n" +
                            "✨ تم تفعيل نظام التنبيهات الذكي لضمان عدم تفويت أي درس!"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setContentIntent(pendingIntent)
            .build()

        try {
            notificationManager.notify(88888, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
