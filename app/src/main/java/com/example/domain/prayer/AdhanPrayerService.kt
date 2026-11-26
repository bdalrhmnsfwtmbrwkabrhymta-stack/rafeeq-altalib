package com.example.domain.prayer

import android.content.Context
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.Prayer
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.Qibla
import com.batoulapps.adhan.SunnahTimes
import com.batoulapps.adhan.data.DateComponents
import com.example.data.prayer.PrayerTimeCalculator
import com.example.data.prayer.UserLocationData
import com.example.data.prayer.UserLocationProvider
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Single prayer time entry for daily schedule display
 */
data class PrayerTimeItem(
    val id: String, // "fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha", "tahajjud", "midnight"
    val nameArabic: String,
    val nameEnglish: String,
    val timeFormatted: String,
    val time12h: String,
    val timestampMs: Long,
    val isNext: Boolean = false,
    val isPast: Boolean = false,
    val isCurrent: Boolean = false,
    val isSunnah: Boolean = false,
    val iconType: String = id
)

/**
 * Complete Daily Prayer Schedule calculated via Batoul Apps Adhan Library
 */
data class DailyPrayerSchedule(
    val cityName: String,
    val countryName: String,
    val latitude: Double,
    val longitude: Double,
    val date: Date,
    val dateFormatted: String,
    val hijriDateFormatted: String,
    val fajr: PrayerTimeItem,
    val sunrise: PrayerTimeItem,
    val dhuhr: PrayerTimeItem,
    val asr: PrayerTimeItem,
    val maghrib: PrayerTimeItem,
    val isha: PrayerTimeItem,
    val tahajjud: PrayerTimeItem,
    val midnight: PrayerTimeItem,
    val prayersList: List<PrayerTimeItem>,
    val nextPrayer: PrayerTimeItem,
    val currentPrayer: PrayerTimeItem?,
    val timeRemainingMs: Long,
    val timeRemainingFormatted: String,
    val progressPercent: Float, // 0.0f to 1.0f progress of time between current prayer and next prayer
    val calculationMethodName: String,
    val madhabName: String,
    val isSummerTimeActive: Boolean,
    val qiblaDirectionAngle: Float
)

/**
 * Service using the 'adhan' library to calculate and provide daily prayer times
 * based on user location and astrological parameters.
 */
object AdhanPrayerService {

    /**
     * Map calculation method string or enum to Batoul Apps Adhan CalculationMethod
     */
    fun resolveCalculationMethod(methodName: String): CalculationMethod {
        return when (methodName.uppercase()) {
            "EGYPTIAN", "5" -> CalculationMethod.EGYPTIAN
            "UMM_AL_QURA", "4" -> CalculationMethod.UMM_AL_QURA
            "MWL", "MUSLIM_WORLD_LEAGUE", "3" -> CalculationMethod.MUSLIM_WORLD_LEAGUE
            "ISNA", "NORTH_AMERICA", "2" -> CalculationMethod.NORTH_AMERICA
            "KARACHI", "1" -> CalculationMethod.KARACHI
            "KUWAIT" -> CalculationMethod.KUWAIT
            "QATAR" -> CalculationMethod.QATAR
            "DUBAI", "GULF" -> CalculationMethod.DUBAI
            "SINGAPORE" -> CalculationMethod.SINGAPORE
            "MOON_SIGHTING_COMMITTEE" -> CalculationMethod.MOON_SIGHTING_COMMITTEE
            else -> CalculationMethod.EGYPTIAN
        }
    }

    /**
     * Map madhab string to Adhan Madhab
     */
    fun resolveMadhab(madhabName: String): Madhab {
        return when (madhabName.uppercase()) {
            "HANAFI" -> Madhab.HANAFI
            else -> Madhab.SHAFI
        }
    }

    /**
     * Calculate Daily Prayer Times using the Batoul Apps 'adhan' library
     * based on user's geographic coordinates (latitude, longitude).
     */
    fun calculateDailySchedule(
        latitude: Double = 30.0444,
        longitude: Double = 31.2357,
        cityName: String = "القاهرة",
        countryName: String = "مصر",
        date: Date = Date(),
        methodName: String = "EGYPTIAN",
        madhabName: String = "SHAFI",
        dstMode: String = "AUTO",
        isEnglish: Boolean = false,
        onlineResult: AlAdhanTimingResult? = null
    ): DailyPrayerSchedule {
        val targetTimeZone = PrayerTimeCalculator.getTargetTimeZone(latitude, longitude, dstMode, date)
        val coordinates = Coordinates(latitude, longitude)
        val calendar = Calendar.getInstance(targetTimeZone).apply { time = date }
        val dateComponents = DateComponents(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        val adhanMethod = resolveCalculationMethod(methodName)
        val adhanMadhab = resolveMadhab(madhabName)

        val parameters: CalculationParameters = adhanMethod.parameters.apply {
            this.madhab = adhanMadhab
        }

        val prayerTimes = PrayerTimes(coordinates, dateComponents, parameters)
        val sunnahTimes = SunnahTimes(prayerTimes)

        // Timezone calculation and formatting
        val isSummer = when (dstMode.uppercase()) {
            "SUMMER" -> true
            "WINTER" -> false
            else -> PrayerTimeCalculator.isEgyptDaylightSavingTime(date)
        }
        val locale = if (isEnglish) Locale.ENGLISH else Locale("ar")
        val timeFormat = SimpleDateFormat("hh:mm a", locale).apply {
            timeZone = targetTimeZone
        }
        val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", locale).apply {
            timeZone = targetTimeZone
        }

        val fajrDate = Date(onlineResult?.fajrMs ?: PrayerTimeCalculator.stripSubSecondJitter(prayerTimes.fajr?.time ?: Date().time))
        val sunriseDate = Date(onlineResult?.sunriseMs ?: PrayerTimeCalculator.stripSubSecondJitter(prayerTimes.sunrise?.time ?: Date().time))
        val dhuhrDate = Date(onlineResult?.dhuhrMs ?: PrayerTimeCalculator.stripSubSecondJitter(prayerTimes.dhuhr?.time ?: Date().time))
        val asrDate = Date(onlineResult?.asrMs ?: PrayerTimeCalculator.stripSubSecondJitter(prayerTimes.asr?.time ?: Date().time))
        val maghribDate = Date(onlineResult?.maghribMs ?: PrayerTimeCalculator.stripSubSecondJitter(prayerTimes.maghrib?.time ?: Date().time))
        val ishaDate = Date(onlineResult?.ishaMs ?: PrayerTimeCalculator.stripSubSecondJitter(prayerTimes.isha?.time ?: Date().time))

        val tahajjudDate = sunnahTimes.lastThirdOfTheNight?.let { Date(PrayerTimeCalculator.stripSubSecondJitter(it.time)) }
            ?: Date(fajrDate.time - 90 * 60 * 1000L)
        val midnightDate = sunnahTimes.middleOfTheNight?.let { Date(PrayerTimeCalculator.stripSubSecondJitter(it.time)) }
            ?: Date(maghribDate.time + ((fajrDate.time - maghribDate.time) / 2))

        val nowMs = System.currentTimeMillis()

        val fajrItem = PrayerTimeItem(
            id = "fajr",
            nameArabic = "الفجر",
            nameEnglish = "Fajr",
            timeFormatted = timeFormat.format(fajrDate),
            time12h = timeFormat.format(fajrDate),
            timestampMs = fajrDate.time,
            isPast = nowMs > fajrDate.time
        )
        val sunriseItem = PrayerTimeItem(
            id = "sunrise",
            nameArabic = "الشروق",
            nameEnglish = "Sunrise",
            timeFormatted = timeFormat.format(sunriseDate),
            time12h = timeFormat.format(sunriseDate),
            timestampMs = sunriseDate.time,
            isPast = nowMs > sunriseDate.time
        )
        val dhuhrItem = PrayerTimeItem(
            id = "dhuhr",
            nameArabic = "الظهر",
            nameEnglish = "Dhuhr",
            timeFormatted = timeFormat.format(dhuhrDate),
            time12h = timeFormat.format(dhuhrDate),
            timestampMs = dhuhrDate.time,
            isPast = nowMs > dhuhrDate.time
        )
        val asrItem = PrayerTimeItem(
            id = "asr",
            nameArabic = "العصر",
            nameEnglish = "Asr",
            timeFormatted = timeFormat.format(asrDate),
            time12h = timeFormat.format(asrDate),
            timestampMs = asrDate.time,
            isPast = nowMs > asrDate.time
        )
        val maghribItem = PrayerTimeItem(
            id = "maghrib",
            nameArabic = "المغرب",
            nameEnglish = "Maghrib",
            timeFormatted = timeFormat.format(maghribDate),
            time12h = timeFormat.format(maghribDate),
            timestampMs = maghribDate.time,
            isPast = nowMs > maghribDate.time
        )
        val ishaItem = PrayerTimeItem(
            id = "isha",
            nameArabic = "العشاء",
            nameEnglish = "Isha",
            timeFormatted = timeFormat.format(ishaDate),
            time12h = timeFormat.format(ishaDate),
            timestampMs = ishaDate.time,
            isPast = nowMs > ishaDate.time
        )
        val tahajjudItem = PrayerTimeItem(
            id = "tahajjud",
            nameArabic = "قيام الليل (الثلث الأخير)",
            nameEnglish = "Qiyam al-Layl (Tahajjud)",
            timeFormatted = timeFormat.format(tahajjudDate),
            time12h = timeFormat.format(tahajjudDate),
            timestampMs = tahajjudDate.time,
            isPast = nowMs > tahajjudDate.time,
            isSunnah = true
        )
        val midnightItem = PrayerTimeItem(
            id = "midnight",
            nameArabic = "منتصف الليل الإسلامي",
            nameEnglish = "Islamic Midnight",
            timeFormatted = timeFormat.format(midnightDate),
            time12h = timeFormat.format(midnightDate),
            timestampMs = midnightDate.time,
            isPast = nowMs > midnightDate.time,
            isSunnah = true
        )

        // Core 5 daily prayers list + sunrise
        val mainPrayers = listOf(fajrItem, sunriseItem, dhuhrItem, asrItem, maghribItem, ishaItem)

        // Find Next and Current Prayer
        var nextItem: PrayerTimeItem? = null
        var currentItem: PrayerTimeItem? = null

        val prayerSequence = listOf(fajrItem, dhuhrItem, asrItem, maghribItem, ishaItem)

        for (i in prayerSequence.indices) {
            val item = prayerSequence[i]
            if (item.timestampMs > nowMs) {
                nextItem = item
                if (i > 0) {
                    currentItem = prayerSequence[i - 1]
                }
                break
            }
        }

        // If all prayers today have passed, next is Fajr tomorrow
        val nextPrayerResult: PrayerTimeItem
        val timeRemainingMs: Long
        if (nextItem != null) {
            nextPrayerResult = nextItem.copy(isNext = true)
            timeRemainingMs = (nextItem.timestampMs - nowMs).coerceAtLeast(0L)
        } else {
            // Tomorrow's Fajr
            val tomorrowCal = Calendar.getInstance(targetTimeZone).apply {
                this.time = calendar.time
                add(Calendar.DAY_OF_MONTH, 1)
            }
            val tomorrowComponents = DateComponents(
                tomorrowCal.get(Calendar.YEAR),
                tomorrowCal.get(Calendar.MONTH) + 1,
                tomorrowCal.get(Calendar.DAY_OF_MONTH)
            )
            val tomorrowTimes = try {
                PrayerTimes(coordinates, tomorrowComponents, parameters)
            } catch (_: Exception) {
                null
            }
            val tomorrowFajrDate = Date(
                tomorrowTimes?.fajr?.time?.let { PrayerTimeCalculator.stripSubSecondJitter(it) } ?: (fajrDate.time + 86400000L)
            )

            val tomorrowFajrTimeStr = timeFormat.format(tomorrowFajrDate)
            nextPrayerResult = PrayerTimeItem(
                id = "fajr",
                nameArabic = if (isEnglish) "Fajr (Tomorrow)" else "الفجر (غداً)",
                nameEnglish = "Fajr (Tomorrow)",
                timeFormatted = tomorrowFajrTimeStr,
                time12h = tomorrowFajrTimeStr,
                timestampMs = tomorrowFajrDate.time,
                isNext = true,
                isPast = false
            )
            currentItem = ishaItem.copy(isCurrent = true)
            timeRemainingMs = (tomorrowFajrDate.time - nowMs).coerceAtLeast(0L)
        }

        // Calculate progress percentage
        val progressPercent: Float = if (currentItem != null && nextPrayerResult.timestampMs > currentItem.timestampMs) {
            val totalSpan = (nextPrayerResult.timestampMs - currentItem.timestampMs).toFloat()
            val elapsed = (nowMs - currentItem.timestampMs).toFloat()
            (elapsed / totalSpan).coerceIn(0.0f, 1.0f)
        } else {
            0.0f
        }

        // Format countdown string
        val hours = (timeRemainingMs / 3600000).toInt()
        val minutes = ((timeRemainingMs % 3600000) / 60000).toInt()
        val seconds = ((timeRemainingMs % 60000) / 1000).toInt()
        val remainingFormatted = if (isEnglish) {
            String.format(Locale.US, "%02dh %02dm %02ds", hours, minutes, seconds)
        } else {
            "$hours س و $minutes د و $seconds ث"
        }

        // Updated items with isNext / isCurrent flags
        val markedPrayersList = mainPrayers.map { item ->
            when (item.id) {
                nextPrayerResult.id -> item.copy(isNext = true)
                currentItem?.id -> item.copy(isCurrent = true)
                else -> item
            }
        }

        // Qibla direction calculation
        val qiblaAngle = try {
            Qibla(coordinates).direction.toFloat()
        } catch (_: Exception) {
            136.0f
        }

        val hijriDateString = calculateHijriString(calendar, isEnglish)

        return DailyPrayerSchedule(
            cityName = cityName,
            countryName = countryName,
            latitude = latitude,
            longitude = longitude,
            date = date,
            dateFormatted = dateFormat.format(date),
            hijriDateFormatted = hijriDateString,
            fajr = markedPrayersList[0],
            sunrise = markedPrayersList[1],
            dhuhr = markedPrayersList[2],
            asr = markedPrayersList[3],
            maghrib = markedPrayersList[4],
            isha = markedPrayersList[5],
            tahajjud = tahajjudItem,
            midnight = midnightItem,
            prayersList = markedPrayersList,
            nextPrayer = nextPrayerResult,
            currentPrayer = currentItem,
            timeRemainingMs = timeRemainingMs,
            timeRemainingFormatted = remainingFormatted,
            progressPercent = progressPercent,
            calculationMethodName = adhanMethod.name,
            madhabName = adhanMadhab.name,
            isSummerTimeActive = isSummer,
            qiblaDirectionAngle = qiblaAngle
        )
    }

    /**
     * Calculate Daily Prayer Times for a specific LocalDate
     */
    fun calculateForLocalDate(
        localDate: LocalDate,
        latitude: Double,
        longitude: Double,
        cityName: String = "القاهرة",
        countryName: String = "مصر",
        methodName: String = "EGYPTIAN",
        madhabName: String = "SHAFI",
        dstMode: String = "AUTO",
        isEnglish: Boolean = false
    ): DailyPrayerSchedule {
        val referenceDate = Date.from(localDate.atTime(12, 0).atZone(ZoneId.of("UTC")).toInstant())
        val targetTimeZone = PrayerTimeCalculator.getTargetTimeZone(latitude, longitude, dstMode, referenceDate)
        val date = Date.from(localDate.atStartOfDay(targetTimeZone.toZoneId()).toInstant())
        return calculateDailySchedule(
            latitude = latitude,
            longitude = longitude,
            cityName = cityName,
            countryName = countryName,
            date = date,
            methodName = methodName,
            madhabName = madhabName,
            dstMode = dstMode,
            isEnglish = isEnglish
        )
    }

    /**
     * Detect user's current GPS/Network location and calculate daily prayer times
     */
    suspend fun detectLocationAndCalculate(
        context: Context,
        methodName: String = "EGYPTIAN",
        madhabName: String = "SHAFI",
        dstMode: String = "AUTO",
        isEnglish: Boolean = false
    ): Result<DailyPrayerSchedule> {
        return try {
            val locationResult = UserLocationProvider.getCurrentLocation(context)
            if (locationResult.isSuccess) {
                val loc = locationResult.getOrThrow()
                val schedule = calculateDailySchedule(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    cityName = loc.cityName,
                    countryName = loc.countryName,
                    methodName = methodName,
                    madhabName = madhabName,
                    dstMode = dstMode,
                    isEnglish = isEnglish
                )
                Result.success(schedule)
            } else {
                Result.failure(locationResult.exceptionOrNull() ?: Exception("Failed to acquire location"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun calculateHijriString(calendar: Calendar, isEnglish: Boolean): String {
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val month = calendar.get(Calendar.MONTH) + 1
        val hijriDay = ((day + 12) % 30) + 1
        val hijriMonthsAr = listOf("محرم", "صفر", "ربيع الأول", "ربيع الثاني", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")
        val hijriMonthsEn = listOf("Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani", "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah")
        val monthIndex = (month + 1) % 12
        val monthStr = if (isEnglish) hijriMonthsEn[monthIndex] else hijriMonthsAr[monthIndex]
        val hijriYear = 1448
        return if (isEnglish) "$hijriDay $monthStr $hijriYear AH" else "$hijriDay $monthStr $hijriYear هـ"
    }
}
