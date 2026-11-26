package com.example.domain.prayer

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.preferences.UserSettings
import com.example.ui.prayer.AthkarPopupActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Calendar

enum class AdhkarWindow {
    MORNING,
    EVENING,
    NONE
}

/**
 * Coordinates automatic Morning & Evening Adhkar windows anchored strictly to
 * Fajr -> Sunrise (Morning) and Asr -> Maghrib (Evening), blocking any random
 * out-of-window triggers and scheduling dynamic AlarmManager wakeups.
 */
object AdhkarAutoTriggerManager {

    private const val TAG = "AdhkarAutoTrigger"
    private const val FIVE_MINUTES_MS = 5 * 60 * 1000L

    /**
     * Strictly locks automatic Adhkar windows to astronomical prayer spans:
     * - Morning Adhkar: [Fajr .. Sunrise)
     * - Evening Adhkar: [Asr .. Maghrib)
     */
    fun detectActiveAdhkarWindow(
        fajrMs: Long,
        sunriseMs: Long,
        dhuhrMs: Long,
        asrMs: Long,
        maghribMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): AdhkarWindow {
        val morningStart = fajrMs.coerceAtMost(sunriseMs)
        val morningEnd = sunriseMs
        if (nowMs in morningStart until morningEnd) {
            return AdhkarWindow.MORNING
        }
        val eveningStart = asrMs
        val eveningEnd = maghribMs
        if (nowMs in eveningStart until eveningEnd) {
            return AdhkarWindow.EVENING
        }
        return AdhkarWindow.NONE
    }

    fun detectActiveAdhkarWindow(
        result: PrayerTimesResult,
        nowMs: Long = System.currentTimeMillis()
    ): AdhkarWindow {
        return detectActiveAdhkarWindow(
            fajrMs = result.fajr.timestampMillis,
            sunriseMs = result.sunrise.timestampMillis,
            dhuhrMs = result.dhuhr.timestampMillis,
            asrMs = result.asr.timestampMillis,
            maghribMs = result.maghrib.timestampMillis,
            nowMs = nowMs
        )
    }

    fun detectActiveWindowForSettings(
        settings: UserSettings,
        nowMs: Long = System.currentTimeMillis()
    ): AdhkarWindow {
        val calcMethod = try {
            CalculationMethod.valueOf(settings.calculationMethod.uppercase())
        } catch (_: Exception) {
            CalculationMethod.EGYPTIAN
        }
        val todayResult = PrayerTimesCalculator.calculatePrayerTimes(
            latitude = settings.latitude,
            longitude = settings.longitude,
            method = calcMethod,
            isEnglish = settings.appLanguage != "ar",
            dstMode = settings.dstMode,
            madhabName = settings.madhab
        )
        return detectActiveAdhkarWindow(todayResult, nowMs = nowMs)
    }

    fun detectCurrentWindowForContext(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): AdhkarWindow {
        return try {
            val settings = runBlocking {
                UserPreferencesRepository(context.applicationContext).userSettingsFlow.first()
            }
            detectActiveWindowForSettings(settings, nowMs = nowMs)
        } catch (e: Exception) {
            Log.e(TAG, "Failed resolving current Adhkar window from prefs", e)
            AdhkarWindow.NONE
        }
    }

    fun recommendedCategoryForClock(nowMs: Long = System.currentTimeMillis()): AdhkarWindow {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return if (hour < 14) AdhkarWindow.MORNING else AdhkarWindow.EVENING
    }

    fun computeNextMorningTriggerMs(
        todayResult: PrayerTimesResult,
        tomorrowResult: PrayerTimesResult,
        nowMs: Long = System.currentTimeMillis()
    ): Long {
        val todayMorningMs = todayResult.fajr.timestampMillis + FIVE_MINUTES_MS
        if (todayMorningMs > nowMs) return todayMorningMs
        var nextMs = tomorrowResult.fajr.timestampMillis + FIVE_MINUTES_MS
        while (nextMs <= nowMs) {
            nextMs += 86_400_000L
        }
        return nextMs
    }

    fun computeNextEveningTriggerMs(
        todayResult: PrayerTimesResult,
        tomorrowResult: PrayerTimesResult,
        nowMs: Long = System.currentTimeMillis()
    ): Long {
        val todayEveningMs = todayResult.asr.timestampMillis + FIVE_MINUTES_MS
        if (todayEveningMs > nowMs) return todayEveningMs
        var nextMs = tomorrowResult.asr.timestampMillis + FIVE_MINUTES_MS
        while (nextMs <= nowMs) {
            nextMs += 86_400_000L
        }
        return nextMs
    }

    fun scheduleDynamicWindowAlarms(
        context: Context,
        morningEnabled: Boolean,
        eveningEnabled: Boolean,
        todayResult: PrayerTimesResult,
        tomorrowResult: PrayerTimesResult
    ) {
        val nowMs = System.currentTimeMillis()
        if (morningEnabled) {
            val triggerMs = computeNextMorningTriggerMs(todayResult, tomorrowResult, nowMs)
            Log.i(TAG, "Scheduling dynamic Morning Adhkar after Fajr at $triggerMs")
            AzkarScheduler.scheduleMorningAzkarAtTimestamp(context, triggerMs)
        } else {
            AzkarScheduler.cancelMorningAzkar(context)
        }

        if (eveningEnabled) {
            val triggerMs = computeNextEveningTriggerMs(todayResult, tomorrowResult, nowMs)
            Log.i(TAG, "Scheduling dynamic Evening Adhkar after Asr at $triggerMs")
            AzkarScheduler.scheduleEveningAzkarAtTimestamp(context, triggerMs)
        } else {
            AzkarScheduler.cancelEveningAzkar(context)
        }
    }

    fun launchAutoAdhkarPopup(context: Context, window: AdhkarWindow) {
        if (window == AdhkarWindow.NONE) return
        val typeExtra = if (window == AdhkarWindow.MORNING) {
            AthkarPopupActivity.TYPE_MORNING
        } else {
            AthkarPopupActivity.TYPE_EVENING
        }
        val intent = Intent(context, AthkarPopupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AthkarPopupActivity.EXTRA_ATHKAR_TYPE, typeExtra)
        }
        context.startActivity(intent)
    }
}
