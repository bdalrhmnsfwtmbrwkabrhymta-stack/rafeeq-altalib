package com.example.domain.prayer

import com.example.data.prayer.PrayerTimeCalculator
import java.util.Date

enum class CalculationMethod(val titleArabic: String, val fajrAngle: Double, val ishaAngle: Double) {
    EGYPTIAN("الهيئة المصرية العامة للمساحة", 19.5, 17.5),
    UMM_AL_QURA("أم القرى - مكة المكرمة", 18.5, 0.0),
    MWL("رابطة العالم الإسلامي", 18.0, 17.0),
    ISNA("الجمعية الإسلامية لأمريكا الشمالية (ISNA)", 15.0, 15.0),
    KARACHI("جامعة العلوم الإسلامية بكراتشي", 18.0, 18.0)
}

data class PrayerTime(
    val id: String,
    val nameArabic: String,
    val nameEnglish: String,
    val timeString: String,
    val timestampMillis: Long,
    val isNext: Boolean = false
)

data class PrayerTimesResult(
    val fajr: PrayerTime,
    val sunrise: PrayerTime,
    val dhuhr: PrayerTime,
    val asr: PrayerTime,
    val maghrib: PrayerTime,
    val isha: PrayerTime,
    val nextPrayerName: String,
    val nextPrayerTimeMs: Long,
    val timeRemainingString: String,
    val hijriDateString: String,
    val isOnlineSynced: Boolean = false,
    val isSummerTimeActive: Boolean = false,
    val dstMode: String = "AUTO",
    val dstStatusText: String = ""
)

/**
 * Adapter delegating to the Adhan-powered PrayerTimeCalculator
 */
object PrayerTimesCalculator {

    fun calculatePrayerTimes(
        latitude: Double = 30.0444,
        longitude: Double = 31.2357,
        date: Date = Date(),
        method: CalculationMethod = CalculationMethod.EGYPTIAN,
        onlineResult: AlAdhanTimingResult? = null,
        isEnglish: Boolean = false,
        dstMode: String = "AUTO",
        madhabName: String = "SHAFI"
    ): PrayerTimesResult {
        return PrayerTimeCalculator.calculatePrayerTimes(
            latitude = latitude,
            longitude = longitude,
            date = date,
            methodName = method.name,
            madhabName = madhabName,
            onlineResult = onlineResult,
            isEnglish = isEnglish,
            dstMode = dstMode
        )
    }
}
