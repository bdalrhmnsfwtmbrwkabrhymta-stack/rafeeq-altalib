package com.example.domain.prayer

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.ui.prayer.AthkarPopupActivity
import java.util.Calendar

enum class AdhkarWindow {
    MORNING,
    EVENING,
    NONE
}

/**
 * Coordinates automatic Morning & Evening Adhkar windows anchored dynamically
 * to Fajr (Morning Adhkar start) and Asr (Evening Adhkar start), as well as
 * exact AlarmManager scheduling and seamless launching of AutoAdhkarScreen.
 */
object AdhkarAutoTriggerManager {

    private const val TAG = "AdhkarAutoTrigger"
    private const val FIVE_MINUTES_MS = 5 * 60 * 1000L
    private const val FORTY_FIVE_MINUTES_MS = 45 * 60 * 1000L

    /**
     * Determines which Adhkar window is currently active based on solar prayer times:
     * - Morning Adhkar: [Fajr .. Dhuhr)
     * - Evening Adhkar: [Asr .. Maghrib + 45m)
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
        val morningEnd = dhuhrMs
        if (nowMs in morningStart until morningEnd) {
            return AdhkarWindow.MORNING
        }
        val eveningStart = asrMs
        val eveningEnd = maghribMs + FORTY_FIVE_MINUTES_MS
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
        if (todayMorningMs > nowMs + 15_000L) return todayMorningMs
        var nextMs = tomorrowResult.fajr.timestampMillis + FIVE_MINUTES_MS
        while (nextMs <= nowMs + 15_000L) {
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
        if (todayEveningMs > nowMs + 15_000L) return todayEveningMs
        var nextMs = tomorrowResult.asr.timestampMillis + FIVE_MINUTES_MS
        while (nextMs <= nowMs + 15_000L) {
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
