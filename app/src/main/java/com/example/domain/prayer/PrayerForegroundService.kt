package com.example.domain.prayer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Permanent Foreground Service providing a non-dismissible status bar notification
 * showing the next prayer time and keeping background prayer services 100% reliable.
 */
class PrayerForegroundService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    buildInitialNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    startForeground(
                        NOTIFICATION_ID,
                        buildInitialNotification(),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } catch (e: Exception) {
                    startForeground(NOTIFICATION_ID, buildInitialNotification())
                }
            } else {
                startForeground(NOTIFICATION_ID, buildInitialNotification())
            }
            startPeriodicUpdates()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updatePrayerNotification()
        return START_STICKY
    }

    private fun startPeriodicUpdates() {
        updateRunnable = object : Runnable {
            override fun run() {
                updatePrayerNotification()
                handler.postDelayed(this, 60 * 1000L) // Update every 1 minute
            }
        }
        updateRunnable?.let { handler.post(it) }
    }

    private fun updatePrayerNotification() {
        serviceScope.launch {
            try {
                val prefsRepo = UserPreferencesRepository(applicationContext)
                val settings = prefsRepo.userSettingsFlow.first()
                val isEnglish = settings.appLanguage == "en"

                val schedule = AdhanPrayerService.calculateDailySchedule(
                    latitude = settings.latitude,
                    longitude = settings.longitude,
                    cityName = settings.cityLocation.ifEmpty { "القاهرة" },
                    countryName = settings.countryLocation.ifEmpty { "مصر" },
                    methodName = settings.calculationMethod,
                    madhabName = settings.madhab,
                    dstMode = settings.dstMode,
                    isEnglish = isEnglish
                )

                val nextPrayerName = if (isEnglish) schedule.nextPrayer.nameEnglish else schedule.nextPrayer.nameArabic
                val remaining = schedule.timeRemainingFormatted

                val title = if (isEnglish) {
                    "Next Prayer: $nextPrayerName (in $remaining)"
                } else {
                    "الصلاة القادمة: $nextPrayerName (خلال $remaining) 🕌"
                }

                val summaryContent = if (isEnglish) {
                    "${schedule.cityName}: Fajr ${schedule.fajr.timeFormatted} • Dhuhr ${schedule.dhuhr.timeFormatted} • Asr ${schedule.asr.timeFormatted} • Maghrib ${schedule.maghrib.timeFormatted} • Isha ${schedule.isha.timeFormatted}"
                } else {
                    "${schedule.cityName}: الفجر ${schedule.fajr.timeFormatted} • الظهر ${schedule.dhuhr.timeFormatted} • العصر ${schedule.asr.timeFormatted} • المغرب ${schedule.maghrib.timeFormatted} • العشاء ${schedule.isha.timeFormatted}"
                }

                val expandedText = if (isEnglish) {
                    """
                    📍 Location: ${schedule.cityName}, ${schedule.countryName} (${String.format(java.util.Locale.US, "%.2f", schedule.latitude)}°, ${String.format(java.util.Locale.US, "%.2f", schedule.longitude)}°)
                    🗓️ ${schedule.dateFormatted} | ${schedule.hijriDateFormatted}
                    
                    🌅 Fajr: ${schedule.fajr.timeFormatted}
                    ☀️ Sunrise: ${schedule.sunrise.timeFormatted}
                    🌤️ Dhuhr: ${schedule.dhuhr.timeFormatted}
                    ⛅ Asr: ${schedule.asr.timeFormatted}
                    🌇 Maghrib: ${schedule.maghrib.timeFormatted}
                    🌌 Isha: ${schedule.isha.timeFormatted}
                    ✨ Tahajjud (Last Third): ${schedule.tahajjud.timeFormatted}
                    🧭 Qibla Direction: ${schedule.qiblaDirectionAngle.toInt()}°
                    """.trimIndent()
                } else {
                    """
                    📍 الموقع: ${schedule.cityName}، ${schedule.countryName} (${String.format(java.util.Locale.US, "%.2f", schedule.latitude)}°، ${String.format(java.util.Locale.US, "%.2f", schedule.longitude)}°)
                    🗓️ ${schedule.dateFormatted} | ${schedule.hijriDateFormatted}
                    
                    🌅 الفجر: ${schedule.fajr.timeFormatted}
                    ☀️ الشروق: ${schedule.sunrise.timeFormatted}
                    🌤️ الظهر: ${schedule.dhuhr.timeFormatted}
                    ⛅ العصر: ${schedule.asr.timeFormatted}
                    🌇 المغرب: ${schedule.maghrib.timeFormatted}
                    🌌 العشاء: ${schedule.isha.timeFormatted}
                    ✨ قيام الليل: ${schedule.tahajjud.timeFormatted}
                    🧭 اتجاه القبلة: ${schedule.qiblaDirectionAngle.toInt()}°
                    """.trimIndent()
                }

                val notification = buildPrayerNotification(title, summaryContent, expandedText)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun buildInitialNotification(): Notification {
        return buildPrayerNotification("مواقيت الصلاة • رفيق الطالب 🕌", "جاري حساب مواقيت الصلاة عبر مكتبة Adhan بدقة...", null)
    }

    private fun buildPrayerNotification(title: String, message: String, expandedText: String? = null): Notification {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            this, 0, appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(appPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (!expandedText.isNullOrBlank()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "مواقيت الصلاة الدائمة",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعار دائم لعرض موعد الصلاة القادمة وضمان دقة الأذان"
                setShowBadge(false)
                setSound(null, null)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Safety net for Android 15 (API 35+) foreground service timeout regulation.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w("PrayerForegroundService", "onTimeout called for fgsType=$fgsType")
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf()
    }

    override fun onTimeout(startId: Int) {
        Log.w("PrayerForegroundService", "Service onTimeout triggered for startId: $startId. Stopping safely to avoid system crash.")
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        updateRunnable?.let { handler.removeCallbacks(it) }
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i("PrayerForegroundService", "onTaskRemoved triggered: app swiped from recents. Reinforcing alarms & watchdog...")
        try {
            PrayerAlarmScheduler.rescheduleAll(applicationContext)
            AzkarScheduler.rescheduleAzkar(applicationContext)

            val restartIntent = Intent(applicationContext, PrayerAlarmReceiver::class.java).apply {
                action = PrayerAlarmReceiver.ACTION_RESTART_SERVICE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                8899,
                restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager?.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 2000L,
                    pendingIntent
                )
            } else {
                alarmManager?.set(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 2000L,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "PrayerForegroundServiceChannel"
        const val NOTIFICATION_ID = 2001

        fun start(context: Context) {
            try {
                val intent = Intent(context, PrayerForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
