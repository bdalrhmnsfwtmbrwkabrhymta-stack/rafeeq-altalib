package com.example.domain.prayer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.preferences.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object PrayerAlarmScheduler {

    private const val REQ_FAJR = 101
    private const val REQ_SUNRISE = 107
    private const val REQ_DHUHR = 102
    private const val REQ_ASR = 103
    private const val REQ_MAGHRIB = 104
    private const val REQ_ISHA = 105
    private const val REQ_SALAWAT = 106
    // Note: Azkar alarm request codes (108 & 109) are managed separately and encapsulated in AzkarScheduler

    private val scheduleLock = Any()

    fun rescheduleAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            rescheduleAllSuspending(context)
        }
    }

    suspend fun rescheduleAllSuspending(context: Context) {
        try {
            val prefsRepo = UserPreferencesRepository(context)
            val settings = prefsRepo.userSettingsFlow.first()

            val calcMethod = try {
                CalculationMethod.valueOf(settings.calculationMethod.uppercase())
            } catch (_: Exception) {
                CalculationMethod.EGYPTIAN
            }

            val todayDate = java.util.Date()
            val targetTz = com.example.data.prayer.PrayerTimeCalculator.getTargetTimeZone(
                latitude = settings.latitude,
                longitude = settings.longitude,
                dstMode = settings.dstMode,
                date = todayDate
            )
            val tomorrowCal = java.util.Calendar.getInstance(targetTz).apply {
                time = todayDate
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }

            // 1. Arm exact alarms IMMEDIATELY (0ms network block) from cached official timings or Adhan calculation
            val cachedToday = AlAdhanRepository.getCachedDailyTimings(
                context = context,
                date = todayDate,
                latitude = settings.latitude,
                longitude = settings.longitude,
                methodName = settings.calculationMethod,
                madhabName = settings.madhab
            )
            val cachedTomorrow = AlAdhanRepository.getCachedDailyTimings(
                context = context,
                date = tomorrowCal.time,
                latitude = settings.latitude,
                longitude = settings.longitude,
                methodName = settings.calculationMethod,
                madhabName = settings.madhab
            )

            applySchedule(context, settings, calcMethod, todayDate, tomorrowCal.time, cachedToday, cachedTomorrow)

            // 2. If online official timings aren't cached yet for today or tomorrow, fetch and re-arm if updated
            if (cachedToday == null || cachedTomorrow == null) {
                val fetchedToday = cachedToday ?: AlAdhanRepository.getOrFetchDailyTimings(
                    context = context,
                    city = settings.cityLocation,
                    country = settings.countryLocation,
                    latitude = settings.latitude,
                    longitude = settings.longitude,
                    methodName = settings.calculationMethod,
                    madhabName = settings.madhab,
                    date = todayDate
                )
                val fetchedTomorrow = cachedTomorrow ?: AlAdhanRepository.getOrFetchDailyTimings(
                    context = context,
                    city = settings.cityLocation,
                    country = settings.countryLocation,
                    latitude = settings.latitude,
                    longitude = settings.longitude,
                    methodName = settings.calculationMethod,
                    madhabName = settings.madhab,
                    date = tomorrowCal.time
                )
                if ((cachedToday == null && fetchedToday != null) || (cachedTomorrow == null && fetchedTomorrow != null)) {
                    applySchedule(context, settings, calcMethod, todayDate, tomorrowCal.time, fetchedToday, fetchedTomorrow)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applySchedule(
        context: Context,
        settings: UserSettings,
        calcMethod: CalculationMethod,
        todayDate: java.util.Date,
        tomorrowDate: java.util.Date,
        todayOnline: AlAdhanTimingResult?,
        tomorrowOnline: AlAdhanTimingResult?
    ) {
        val prayerResult = PrayerTimesCalculator.calculatePrayerTimes(
            latitude = settings.latitude,
            longitude = settings.longitude,
            date = todayDate,
            method = calcMethod,
            onlineResult = todayOnline,
            isEnglish = settings.appLanguage != "ar",
            dstMode = settings.dstMode,
            madhabName = settings.madhab
        )

        val tomorrowPrayerResult = PrayerTimesCalculator.calculatePrayerTimes(
            latitude = settings.latitude,
            longitude = settings.longitude,
            date = tomorrowDate,
            method = calcMethod,
            onlineResult = tomorrowOnline,
            isEnglish = settings.appLanguage != "ar",
            dstMode = settings.dstMode,
            madhabName = settings.madhab
        )

        schedulePrayerAlarms(
            context = context,
            prayerTimesResult = prayerResult,
            tomorrowPrayerTimesResult = tomorrowPrayerResult,
            cityName = settings.cityLocation,
            muezzinVoice = settings.muezzinVoice,
            isFullAudio = settings.isAdhanFullAudio,
            isEnabled = settings.isPrayerNotificationEnabled,
            mutedPrayers = settings.mutedPrayers,
            isSunriseAlertEnabled = settings.isSunriseAlertEnabled
        )

        AdhkarAutoTriggerManager.scheduleDynamicWindowAlarms(
            context = context,
            morningEnabled = settings.isMorningAzkarEnabled,
            eveningEnabled = settings.isEveningAzkarEnabled,
            todayResult = prayerResult,
            tomorrowResult = tomorrowPrayerResult
        )

        scheduleSalawatReminder(
            context = context,
            isSalawatEnabled = settings.isSalawatEnabled,
            intervalMinutes = settings.salawatIntervalMinutes
        )
    }

    fun scheduleAzkarAlarms(
        context: Context,
        morningEnabled: Boolean,
        eveningEnabled: Boolean,
        morningHour: Int = 9,
        morningMinute: Int = 0,
        eveningHour: Int = 17,
        eveningMinute: Int = 0
    ) {
        AzkarScheduler.scheduleAzkar(
            context = context,
            morningEnabled = morningEnabled,
            eveningEnabled = eveningEnabled,
            morningHour = morningHour,
            morningMinute = morningMinute,
            eveningHour = eveningHour,
            eveningMinute = eveningMinute
        )
    }

    fun shouldSchedulePrayer(
        prayerId: String,
        mutedPrayers: Set<String>,
        isSunriseAlertEnabled: Boolean
    ): Boolean {
        if (prayerId == "sunrise" && !isSunriseAlertEnabled) return false
        return !mutedPrayers.contains(prayerId)
    }

    fun schedulePrayerAlarms(
        context: Context,
        prayerTimesResult: PrayerTimesResult,
        tomorrowPrayerTimesResult: PrayerTimesResult? = null,
        cityName: String,
        muezzinVoice: String,
        isFullAudio: Boolean,
        isEnabled: Boolean,
        mutedPrayers: Set<String>,
        isSunriseAlertEnabled: Boolean = false
    ) {
        synchronized(scheduleLock) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // Cancel previous pending alarms to avoid duplicates
            cancelAllAlarms(context, alarmManager)

            if (!isEnabled) return

            val now = System.currentTimeMillis()

            val prayers = listOf(
                Triple("fajr", REQ_FAJR, prayerTimesResult.fajr),
                Triple("sunrise", REQ_SUNRISE, prayerTimesResult.sunrise),
                Triple("dhuhr", REQ_DHUHR, prayerTimesResult.dhuhr),
                Triple("asr", REQ_ASR, prayerTimesResult.asr),
                Triple("maghrib", REQ_MAGHRIB, prayerTimesResult.maghrib),
                Triple("isha", REQ_ISHA, prayerTimesResult.isha)
            )

            val tomorrowPrayersMap = mapOf(
                "fajr" to (tomorrowPrayerTimesResult?.fajr?.timestampMillis ?: (prayerTimesResult.fajr.timestampMillis + 86400000L)),
                "sunrise" to (tomorrowPrayerTimesResult?.sunrise?.timestampMillis ?: (prayerTimesResult.sunrise.timestampMillis + 86400000L)),
                "dhuhr" to (tomorrowPrayerTimesResult?.dhuhr?.timestampMillis ?: (prayerTimesResult.dhuhr.timestampMillis + 86400000L)),
                "asr" to (tomorrowPrayerTimesResult?.asr?.timestampMillis ?: (prayerTimesResult.asr.timestampMillis + 86400000L)),
                "maghrib" to (tomorrowPrayerTimesResult?.maghrib?.timestampMillis ?: (prayerTimesResult.maghrib.timestampMillis + 86400000L)),
                "isha" to (tomorrowPrayerTimesResult?.isha?.timestampMillis ?: (prayerTimesResult.isha.timestampMillis + 86400000L))
            )

            for ((prayerId, reqCode, prayerTimeData) in prayers) {
                if (!shouldSchedulePrayer(prayerId, mutedPrayers, isSunriseAlertEnabled)) continue

                val todayMs = prayerTimeData.timestampMillis
                val tomorrowMs = tomorrowPrayersMap[prayerId] ?: (todayMs + 86400000L)

                // Fire at the exact millisecond of today's calculated prayer if strictly in the future; otherwise tomorrow's exact prayer time.
                var targetMs = if (todayMs > now) todayMs else tomorrowMs

                // Safety invariant: targetMs MUST always be strictly in the future.
                while (targetMs <= now) {
                    targetMs += 86400000L
                }

                val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                    action = PrayerAlarmReceiver.ACTION_PRAYER_ALARM
                    putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_NAME, prayerTimeData.nameArabic)
                    putExtra(PrayerAlarmReceiver.EXTRA_CITY_NAME, cityName)
                    putExtra(PrayerAlarmReceiver.EXTRA_MUEZZIN, muezzinVoice)
                    putExtra(PrayerAlarmReceiver.EXTRA_IS_FULL_AUDIO, isFullAudio)
                    putExtra(PrayerAlarmReceiver.EXTRA_SCHEDULED_TIME_MS, targetMs)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    reqCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val showIntent = Intent(context, com.example.ui.prayer.AdhanFullScreenActivity::class.java).apply {
                    putExtra(com.example.ui.prayer.AdhanFullScreenActivity.EXTRA_PRAYER_NAME, prayerTimeData.nameArabic)
                    putExtra(com.example.ui.prayer.AdhanFullScreenActivity.EXTRA_CITY_NAME, cityName)
                    putExtra(com.example.ui.prayer.AdhanFullScreenActivity.EXTRA_MUEZZIN, muezzinVoice)
                    putExtra(com.example.ui.prayer.AdhanFullScreenActivity.EXTRA_AUTO_PLAY, isFullAudio)
                    putExtra(com.example.ui.prayer.AdhanFullScreenActivity.EXTRA_SCHEDULED_TIME_MS, targetMs)
                }
                val showPendingIntent = PendingIntent.getActivity(
                    context,
                    reqCode + 500,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                scheduleExactAlarmSafely(alarmManager, targetMs, pendingIntent, showPendingIntent)
            }
        }
    }

    private fun scheduleExactAlarmSafely(
        alarmManager: AlarmManager,
        triggerMs: Long,
        operation: PendingIntent,
        showIntent: PendingIntent? = null
    ) {
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        try {
            if (canExact) {
                if (showIntent != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerMs, showIntent)
                    alarmManager.setAlarmClock(alarmClockInfo, operation)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, operation)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMs, operation)
                }
            } else {
                // Fallback when exact alarms are restricted by system or user
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, operation)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMs, operation)
                }
            }
        } catch (e: SecurityException) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, operation)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMs, operation)
                }
            } catch (_: Exception) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMs, operation)
            }
        } catch (e: Exception) {
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMs, operation)
            } catch (_: Exception) {}
        }
    }

    private const val PREFS_SALAWAT = "salawat_alarm_prefs"
    private const val KEY_SALAWAT_ENABLED = "key_salawat_enabled"
    private const val KEY_SALAWAT_INTERVAL = "key_salawat_interval"

    fun scheduleSalawatReminder(
        context: Context,
        isSalawatEnabled: Boolean,
        intervalMinutes: Int
    ) {
        val prefs = context.getSharedPreferences(PREFS_SALAWAT, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_SALAWAT_ENABLED, isSalawatEnabled)
            .putInt(KEY_SALAWAT_INTERVAL, intervalMinutes.coerceAtLeast(1))
            .apply()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = PrayerAlarmReceiver.ACTION_SALAWAT_ALARM
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQ_SALAWAT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (!isSalawatEnabled) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val safeIntervalMinutes = intervalMinutes.coerceAtLeast(1)
        val intervalMs = safeIntervalMinutes * 60 * 1000L
        val nextTriggerMs = System.currentTimeMillis() + intervalMs

        val showIntent = Intent(context, com.example.ui.prayer.AthkarPopupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(com.example.ui.prayer.AthkarPopupActivity.EXTRA_ATHKAR_TYPE, com.example.ui.prayer.AthkarPopupActivity.TYPE_SALAWAT)
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            REQ_SALAWAT + 500,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleExactAlarmSafely(alarmManager, nextTriggerMs, pendingIntent, showPendingIntent)
    }

    fun scheduleNextSalawatExactAlarm(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_SALAWAT, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(KEY_SALAWAT_ENABLED, true)
        val intervalMinutes = prefs.getInt(KEY_SALAWAT_INTERVAL, 10).coerceAtLeast(1)
        if (isEnabled) {
            scheduleSalawatReminder(context, true, intervalMinutes)
        }
    }

    private fun cancelAllAlarms(context: Context, alarmManager: AlarmManager) {
        val reqCodes = listOf(REQ_FAJR, REQ_SUNRISE, REQ_DHUHR, REQ_ASR, REQ_MAGHRIB, REQ_ISHA)

        for (reqCode in reqCodes) {
            val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                action = PrayerAlarmReceiver.ACTION_PRAYER_ALARM
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }
}
