package com.example.domain.schedule

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R

class LectureNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong(KEY_SESSION_ID, 0L)
        val subjectName = inputData.getString(KEY_SUBJECT_NAME) ?: "مادة دراسية"
        val startTime = inputData.getString(KEY_START_TIME) ?: "08:00"
        val endTime = inputData.getString(KEY_END_TIME) ?: "09:30"
        val roomOrLink = inputData.getString(KEY_ROOM_OR_LINK) ?: ""
        val note = inputData.getString(KEY_NOTE) ?: ""
        val dayOfWeek = inputData.getInt(KEY_DAY_OF_WEEK, 1)
        val minutesBefore = inputData.getInt(KEY_MINUTES_BEFORE, 10)

        // Show the notification
        showLectureNotification(
            sessionId = sessionId,
            subjectName = subjectName,
            startTime = startTime,
            endTime = endTime,
            roomOrLink = roomOrLink,
            note = note,
            minutesBefore = minutesBefore
        )

        // Reschedule for next week's occurrence automatically
        if (sessionId > 0) {
            StudyReminderScheduler.scheduleNextWeeklyOccurrence(
                context = applicationContext,
                sessionId = sessionId,
                subjectName = subjectName,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                roomOrLink = roomOrLink,
                note = note,
                minutesBefore = minutesBefore
            )
        }

        return Result.success()
    }

    private fun showLectureNotification(
        sessionId: Long,
        subjectName: String,
        startTime: String,
        endTime: String,
        roomOrLink: String,
        note: String,
        minutesBefore: Int
    ) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android 8.0+ (Oreo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
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

        // PendingIntent to launch the schedule screen in MainActivity
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "schedule")
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            (sessionId xor 0x5A5A).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timingText = if (minutesBefore > 0) {
            "تبدأ خلال $minutesBefore دقيقة (الساعة $startTime)"
        } else {
            "تبدأ الآن (الساعة $startTime)"
        }

        val locationText = if (roomOrLink.isNotBlank()) " | المكان: $roomOrLink" else ""
        val fullContentText = "$timingText$locationText"

        val bigText = buildString {
            append("🔔 موعد حصة: $subjectName\n")
            append("⏰ التوقيت: $startTime إلى $endTime\n")
            if (roomOrLink.isNotBlank()) {
                append("📍 القاعة / الرابط: $roomOrLink\n")
            }
            if (note.isNotBlank()) {
                append("📝 ملاحظات: $note\n")
            }
            append("✨ جهز كشكولك وأدواتك، وبالتوفيق والنجاح!")
        }

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⏰ تذكير بمحاضرة: $subjectName")
            .setContentText(fullContentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setContentIntent(pendingIntent)
            .build()

        try {
            val notificationId = if (sessionId > 0) (10000 + sessionId).toInt() else (System.currentTimeMillis() % 10000).toInt()
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    companion object {
        const val CHANNEL_ID = "study_lecture_reminders_channel"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_SUBJECT_NAME = "subject_name"
        const val KEY_START_TIME = "start_time"
        const val KEY_END_TIME = "end_time"
        const val KEY_ROOM_OR_LINK = "room_or_link"
        const val KEY_NOTE = "note"
        const val KEY_DAY_OF_WEEK = "day_of_week"
        const val KEY_MINUTES_BEFORE = "minutes_before"
    }
}
