package com.example.domain.whatsapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.data.database.AppDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.domain.telegram.DailyTelegramReportHelper
import kotlinx.coroutines.flow.first

class DailyWhatsAppReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val CHANNEL_ID = "daily_telegram_reminders_channel"
        const val NOTIFICATION_ID = 8820
    }

    override suspend fun doWork(): Result {
        val prefsRepo = UserPreferencesRepository(applicationContext)
        val userSettings = prefsRepo.userSettingsFlow.first()

        if (!userSettings.isDailyWhatsAppReminderEnabled) {
            return Result.success()
        }

        val db = AppDatabase.getInstance(applicationContext)
        val tasks = try {
            db.taskDao().getAllActiveTasksList()
        } catch (e: Exception) {
            emptyList()
        }

        val isFriday = DailyTelegramReportHelper.isTodayFriday()
        val completedCount = tasks.count { it.isCompleted }
        val waterCups = userSettings.dailyWaterCups
        val reportMessage = DailyTelegramReportHelper.generateDailyReportMessage(userSettings, tasks)

        showDailyReminderNotification(
            userName = userSettings.userName,
            telegramTarget = userSettings.telegramNumber.ifBlank { userSettings.whatsappNumber },
            completedCount = completedCount,
            waterCups = waterCups,
            waterGoal = userSettings.dailyWaterGoalCups,
            isFriday = isFriday,
            reportMessage = reportMessage
        )

        // Reschedule for tomorrow
        DailyWhatsAppReminderScheduler.scheduleDailyReminder(
            context = applicationContext,
            hour = userSettings.whatsappDailyReminderHour,
            minute = userSettings.whatsappDailyReminderMinute
        )

        return Result.success()
    }

    private fun showDailyReminderNotification(
        userName: String,
        telegramTarget: String,
        completedCount: Int,
        waterCups: Int,
        waterGoal: Int,
        isFriday: Boolean,
        reportMessage: String
    ) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "تذكيرات وتقارير التليجرام اليومية 📲",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات يومية بملخص المهام المنجزة، تذكيرات شرب الماء، وسُنن يوم الجمعة (سورة الكهف والجمعة)"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 1. Open App PendingIntent
        val appIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "home")
        }
        val appPendingIntent = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Direct Telegram Send Intent
        val cleanUser = telegramTarget.replace("@", "").trim()
        val encodedMsg = Uri.encode(reportMessage)
        val telegramUrl = if (cleanUser.isNotBlank() && !cleanUser.all { it.isDigit() }) {
            "https://t.me/$cleanUser?text=$encodedMsg"
        } else {
            "https://t.me/share/url?url=${Uri.encode("https://t.me/RafeeqAlTalebBot")}&text=$encodedMsg"
        }
        val tgIntent = Intent(Intent.ACTION_VIEW, Uri.parse(telegramUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val tgPendingIntent = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID + 1,
            tgIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "تقريرك وتذكيرك اليومي جاهز يا ${userName.ifBlank { "طالبنا العزيز" }} 📲"
        val snippetText = if (isFriday) {
            "🕌 يوم الجمعة: اقرأ سورة الكهف وسورة الجمعة | أنجزت $completedCount مهام | ماء: $waterCups/$waterGoal أكواب 💧"
        } else {
            "أنجزت $completedCount مهام اليوم | شربت $waterCups من $waterGoal أكواب ماء 💧. انقر للمراجعة والإرسال"
        }

        val bigText = buildString {
            append("🎓 ملخص إنجازك اليومي:\n")
            append("• المهام المنجزة: $completedCount مهام مكتملة بنجاح ✅\n")
            append("• شرب الماء: $waterCups من أصل $waterGoal أكواب ($waterCups/8) 💧\n")
            if (isFriday) {
                append("• 🕌 سُنن الجمعة المباركة: قراءة سورة الكهف وسورة الجمعة/السجدة والصلاة على النبي ﷺ ✨\n")
            }
            append("📲 انقر على زر إرسال للتليجرام للمشاركة أو حفظ التقرير!")
        }

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(snippetText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setContentIntent(appPendingIntent)
            .addAction(android.R.drawable.ic_menu_send, "إرسال إلى Telegram 💬", tgPendingIntent)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
