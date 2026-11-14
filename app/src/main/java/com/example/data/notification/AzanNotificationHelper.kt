package com.example.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.ui.prayer.AthkarPopupActivity

class AzanNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_AZAN_ID = "azan_channel"
        const val CHANNEL_AZKAR_ID = "azkar_channel"
        const val CHANNEL_SALAWAT_MAIN_ID = "salawat_channel"

        const val CHANNEL_MAKKAH_ID = "azan_makkah_channel_v2"
        const val CHANNEL_CAIRO_ID = "azan_cairo_channel_v2"
        const val CHANNEL_MOROCCO_ID = "azan_morocco_channel_v2"
        const val CHANNEL_ALI_MULLA_ID = "azan_ali_mulla_channel_v2"
        const val CHANNEL_SALAWAT_ID = "salawat_channel_v2"

        const val CHANNEL_ID = CHANNEL_AZAN_ID
        const val SALAWAT_CHANNEL_ID = CHANNEL_SALAWAT_MAIN_ID
        const val NOTIFICATION_ID = 1001
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createAllNotificationChannels()
    }

    /**
     * إنشاء كافة القنوات المخصصة لكل صوت أذان وتذكير وأذكار
     */
    fun createAllNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            // Main Azan Channel
            val mainAzanUri = Uri.parse("android.resource://" + context.packageName + "/" + R.raw.azan_makkah_1)
            val mainAzanChannel = NotificationChannel(
                CHANNEL_AZAN_ID,
                "الأذان ومواقيت الصلاة 🕌",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات مواقيت الصلاة والأذان المباشر والشاشة الكاملة"
                setSound(mainAzanUri, audioAttributes)
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(mainAzanChannel)

            // Main Azkar Channel
            val azkarChannel = NotificationChannel(
                CHANNEL_AZKAR_ID,
                "أذكار الصباح والمساء 🌅",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات وتنبيهات أذكار الصباح والمساء والتحصين"
                setSound(null, null)
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(azkarChannel)

            // Main Salawat Channel
            val salawatMainUri = Uri.parse("android.resource://" + context.packageName + "/" + R.raw.salli_ala_muhammad)
            val salawatMainChannel = NotificationChannel(
                CHANNEL_SALAWAT_MAIN_ID,
                "الصلاة على النبي ﷺ 📿",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات الصلاة على النبي ﷺ"
                setSound(salawatMainUri, audioAttributes)
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(salawatMainChannel)

            // 1. قناة أذان مكة المكرمة
            val makkahUri = Uri.parse("android.resource://" + context.packageName + "/" + R.raw.azan_makkah_1)
            val makkahChannel = NotificationChannel(
                CHANNEL_MAKKAH_ID,
                "أذان مكة المكرمة 🕋",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات الأذان بصوت أذان مكة المكرمة"
                setSound(makkahUri, audioAttributes)
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(makkahChannel)

            // 2. قناة أذان القاهرة
            val cairoUri = Uri.parse("android.resource://" + context.packageName + "/" + R.raw.adhan_cairo)
            val cairoChannel = NotificationChannel(
                CHANNEL_CAIRO_ID,
                "أذان القاهرة 🇪🇬",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات الأذان بصوت أذان القاهرة"
                setSound(cairoUri, audioAttributes)
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(cairoChannel)

            // 3. قناة أذان المغرب
            val moroccoUri = Uri.parse("android.resource://" + context.packageName + "/" + R.raw.adhan_morocco)
            val moroccoChannel = NotificationChannel(
                CHANNEL_MOROCCO_ID,
                "أذان المغرب 🇲🇦",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات الأذان بصوت أذان المغرب"
                setSound(moroccoUri, audioAttributes)
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(moroccoChannel)

            // 4. قناة أذان مصر (عبد الباسط)
            val aliMullaUri = Uri.parse("android.resource://" + context.packageName + "/" + R.raw.azan_ali_mulla)
            val aliMullaChannel = NotificationChannel(
                CHANNEL_ALI_MULLA_ID,
                "أذان مصر - الشيخ عبد الباسط 🕌",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات الأذان بصوت الشيخ عبد الباسط عبد الصمد"
                setSound(aliMullaUri, audioAttributes)
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(aliMullaChannel)

            // 5. قناة التذكير بالصلاة على النبي ﷺ
            val salawatUri = Uri.parse("android.resource://" + context.packageName + "/" + R.raw.salli_ala_muhammad)
            val salawatChannel = NotificationChannel(
                CHANNEL_SALAWAT_ID,
                "تذكير الصلاة على النبي ﷺ 📿",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "التذكير الدوري بالصلاة على النبي ﷺ"
                setSound(salawatUri, audioAttributes)
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(salawatChannel)
        }
    }

    /**
     * إظهار إشعار الأذكار المخصص عبر قناة azkar_channel
     */
    fun showAzkarNotification(
        title: String,
        message: String,
        contentIntent: android.app.PendingIntent? = null
    ) {
        createAllNotificationChannels()
        val builder = NotificationCompat.Builder(context, CHANNEL_AZKAR_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)

        if (contentIntent != null) {
            builder.setContentIntent(contentIntent)
            builder.setFullScreenIntent(contentIntent, true)
        }

        val notifId = NOTIFICATION_ID + 200 + (System.currentTimeMillis() % 1000).toInt()
        notificationManager.notify(notifId, builder.build())
    }

    /**
     * إظهار إشعار الصلاة على النبي ﷺ عبر قناة salawat_channel مع FullScreenIntent
     */
    fun showSalawatNotification(
        title: String,
        message: String,
        rawAudioResId: Int = R.raw.salli_ala_muhammad,
        contentIntent: PendingIntent? = null
    ) {
        createAllNotificationChannels()
        val soundUri = Uri.parse("android.resource://" + context.packageName + "/" + rawAudioResId)

        val fullScreenPendingIntent = contentIntent ?: run {
            val popupIntent = Intent(context, AthkarPopupActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(AthkarPopupActivity.EXTRA_ATHKAR_TYPE, AthkarPopupActivity.TYPE_SALAWAT)
            }
            PendingIntent.getActivity(
                context,
                301,
                popupIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_SALAWAT_MAIN_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)

        val notifId = NOTIFICATION_ID + 300 + (System.currentTimeMillis() % 1000).toInt()
        notificationManager.notify(notifId, builder.build())
    }

    /**
     * إظهار إشعار الأذان مع تشغيل الملف الصوتي المخصص بالقناة المناسبة
     */
    fun showAzanNotification(
        title: String,
        message: String,
        rawAudioResId: Int = R.raw.azan_makkah_1
    ) {
        createAllNotificationChannels()

        val channelId = when (rawAudioResId) {
            R.raw.adhan_cairo -> CHANNEL_CAIRO_ID
            R.raw.adhan_morocco -> CHANNEL_MOROCCO_ID
            R.raw.azan_ali_mulla -> CHANNEL_ALI_MULLA_ID
            R.raw.salli_ala_muhammad, R.raw.salawat_reminder -> CHANNEL_SALAWAT_ID
            else -> CHANNEL_MAKKAH_ID
        }

        // بناء المسار المباشر للملف الصوتي من مجلد res/raw
        val soundUri = Uri.parse("android.resource://" + context.packageName + "/" + rawAudioResId)

        // بناء الإشعار بالقناة المحددة ديناميكياً
        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(soundUri)
            .setAutoCancel(true)

        val notifId = NOTIFICATION_ID + (System.currentTimeMillis() % 1000).toInt()
        notificationManager.notify(notifId, notificationBuilder.build())
    }
}
