package com.example.data.prayer

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import com.example.domain.prayer.AlAdhanTimingResult
import com.example.domain.prayer.PrayerTime
import com.example.domain.prayer.PrayerTimesResult
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class CalculatedPrayerTimes(
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val fajrDate: Date?,
    val sunriseDate: Date?,
    val dhuhrDate: Date?,
    val asrDate: Date?,
    val maghribDate: Date?,
    val ishaDate: Date?,
    val nextPrayerName: String = "",
    val timeRemainingString: String = "",
    val isSummerTimeActive: Boolean = false
)

/**
 * حاسبة مواقيت الصلاة الدقيقة بالثانية والدقيقة لمصر وكافة المحافظات والعواصم
 * تدعم التوقيت الصيفي والتوقيت الشتوي المصري تلقائياً ومعادلات الهيئة المصرية العامة للمساحة
 */
object PrayerTimeCalculator {

    // إحداثيات المحافظات المصرية الدقيقة
    val EGYPT_CITY_COORDINATES = mapOf(
        "القاهرة" to Pair(30.0444, 31.2357),
        "Cairo" to Pair(30.0444, 31.2357),
        "الإسكندرية" to Pair(31.2001, 29.9187),
        "Alexandria" to Pair(31.2001, 29.9187),
        "الجيزة" to Pair(30.0131, 31.2089),
        "Giza" to Pair(30.0131, 31.2089),
        "أسيوط" to Pair(27.1783, 31.1859),
        "Asyut" to Pair(27.1783, 31.1859),
        "المنصورة" to Pair(31.0409, 31.3785),
        "المنصورة (الدقهلية)" to Pair(31.0409, 31.3785),
        "Mansoura" to Pair(31.0409, 31.3785),
        "طنطا" to Pair(30.7865, 31.0004),
        "طنطا (الغربية)" to Pair(30.7865, 31.0004),
        "Tanta" to Pair(30.7865, 31.0004),
        "الزقازيق" to Pair(30.5877, 31.5020),
        "الزقازيق (الشرقية)" to Pair(30.5877, 31.5020),
        "Zagazig" to Pair(30.5877, 31.5020),
        "بنها" to Pair(30.4660, 31.1853),
        "بنها (القليوبية)" to Pair(30.4660, 31.1853),
        "Banha" to Pair(30.4660, 31.1853),
        "شبين الكوم" to Pair(30.5593, 31.0109),
        "شبين الكوم (المنوفية)" to Pair(30.5593, 31.0109),
        "Shibin El Kom" to Pair(30.5593, 31.0109),
        "دمنهور" to Pair(31.0400, 30.4700),
        "دمنهور (البحيرة)" to Pair(31.0400, 30.4700),
        "المحمودية" to Pair(31.1833, 30.5250),
        "المحمودية (البحيرة)" to Pair(31.1833, 30.5250),
        "المحمودية - مصر" to Pair(31.1833, 30.5250),
        "Mahmoudiyah" to Pair(31.1833, 30.5250),
        "Damanhur" to Pair(31.0400, 30.4700),
        "كفر الشيخ" to Pair(31.1107, 30.9388),
        "Kafr El Sheikh" to Pair(31.1107, 30.9388),
        "دمياط" to Pair(31.4175, 31.8144),
        "Damietta" to Pair(31.4175, 31.8144),
        "بور سعيد" to Pair(31.2653, 32.3019),
        "Port Said" to Pair(31.2653, 32.3019),
        "الإسماعيلية" to Pair(30.6043, 32.2723),
        "Ismailia" to Pair(30.6043, 32.2723),
        "السويس" to Pair(29.9668, 32.5498),
        "Suez" to Pair(29.9668, 32.5498),
        "الفيوم" to Pair(29.3084, 30.8428),
        "Fayoum" to Pair(29.3084, 30.8428),
        "بني سويف" to Pair(29.0661, 31.0994),
        "Beni Suef" to Pair(29.0661, 31.0994),
        "المنيا" to Pair(28.0871, 30.7618),
        "Minya" to Pair(28.0871, 30.7618),
        "سوهاج" to Pair(26.5570, 31.6948),
        "Sohag" to Pair(26.5570, 31.6948),
        "قنا" to Pair(26.1551, 32.7160),
        "Qena" to Pair(26.1551, 32.7160),
        "الأقصر" to Pair(25.6872, 32.6396),
        "Luxor" to Pair(25.6872, 32.6396),
        "أسوان" to Pair(24.0889, 32.8998),
        "Aswan" to Pair(24.0889, 32.8998),
        "الغردقة" to Pair(27.2579, 33.8116),
        "الغردقة (البحر الأحمر)" to Pair(27.2579, 33.8116),
        "Hurghada" to Pair(27.2579, 33.8116),
        "شرم الشيخ" to Pair(27.9158, 34.3299),
        "شرم الشيخ (جنوب سيناء)" to Pair(27.9158, 34.3299),
        "Sharm El Sheikh" to Pair(27.9158, 34.3299),
        "العريش" to Pair(31.1325, 33.7989),
        "العريش (شمال سيناء)" to Pair(31.1325, 33.7989),
        "El Arish" to Pair(31.1325, 33.7989),
        "مرسى مطروح" to Pair(31.3543, 27.2373),
        "Marsa Matrouh" to Pair(31.3543, 27.2373),
        "الخارجة" to Pair(25.4514, 30.5472),
        "الخارجة (الوادي الجديد)" to Pair(25.4514, 30.5472),
        "El Kharga" to Pair(25.4514, 30.5472)
    )

    /**
     * فحص سريان التوقيت الصيفي المصري تلقائياً (من الجمعة الأخيرة من أبريل حتى الخميس الأخير من أكتوبر)
     */
    fun isEgyptDaylightSavingTime(date: Date = Date()): Boolean {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Africa/Cairo"))
        cal.time = date
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) // 0-based: 0=Jan, 3=Apr, 9=Oct

        // قبل أبريل أو بعد أكتوبر -> شتوي
        if (month < Calendar.APRIL || month > Calendar.OCTOBER) return false
        // من مايو حتى سبتمبر -> صيفي
        if (month > Calendar.APRIL && month < Calendar.OCTOBER) return true

        if (month == Calendar.APRIL) {
            // آخر جمعة في أبريل
            val testCal = Calendar.getInstance(TimeZone.getTimeZone("Africa/Cairo"))
            testCal.set(year, Calendar.APRIL, 30, 0, 0, 0)
            while (testCal.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) {
                testCal.add(Calendar.DAY_OF_MONTH, -1)
            }
            return !cal.before(testCal)
        }

        if (month == Calendar.OCTOBER) {
            // آخر خميس في أكتوبر (ينتهي التوقيت الصيفي عند منتصف ليل الخميس/الجمعة)
            val testCal = Calendar.getInstance(TimeZone.getTimeZone("Africa/Cairo"))
            testCal.set(year, Calendar.OCTOBER, 31, 23, 59, 59)
            while (testCal.get(Calendar.DAY_OF_WEEK) != Calendar.THURSDAY) {
                testCal.add(Calendar.DAY_OF_MONTH, -1)
            }
            return !cal.after(testCal)
        }

        return false
    }

    /**
     * جلب إحداثيات المدينة
     */
    fun getCoordinatesForCity(cityName: String, defaultLat: Double = 30.0444, defaultLng: Double = 31.2357): Pair<Double, Double> {
        val trimmed = cityName.trim()
        EGYPT_CITY_COORDINATES[trimmed]?.let { return it }
        for ((key, coords) in EGYPT_CITY_COORDINATES) {
            if (trimmed.contains(key) || key.contains(trimmed)) {
                return coords
            }
        }
        return Pair(defaultLat, defaultLng)
    }

    /**
     * حساب التوقيت الزمني الدقيق لموقع الصلاة (مع مراعاة التوقيت الصيفي والشتوي في مصر)
     */
    fun getTargetTimeZone(
        latitude: Double,
        longitude: Double,
        dstMode: String = "AUTO",
        date: Date = Date()
    ): TimeZone {
        val isSummer = when (dstMode.uppercase()) {
            "SUMMER" -> true
            "WINTER" -> false
            else -> isEgyptDaylightSavingTime(date)
        }
        val isEgypt = (latitude in 22.0..32.0 && longitude in 24.0..37.0)
        if (isEgypt) {
            val offsetHours = if (isSummer) 3 else 2
            return TimeZone.getTimeZone(String.format(Locale.US, "GMT+%02d:00", offsetHours))
        }
        // Saudi Arabia / Gulf
        if (latitude in 16.0..32.5 && longitude in 36.0..56.0) {
            return TimeZone.getTimeZone("GMT+03:00")
        }
        val rawOffsetHours = Math.round(longitude / 15.0).toInt().coerceIn(-12, 14)
        val sign = if (rawOffsetHours >= 0) "+" else "-"
        return TimeZone.getTimeZone(String.format(Locale.US, "GMT%s%02d:00", sign, Math.abs(rawOffsetHours)))
    }

    /**
     * حساب مواقيت الصلاة بناءً على الإحداثيات والتاريخ مع مراعاة التوقيت المحلي للمدينة
     */
    fun getPrayerTimes(
        latitude: Double = 30.0444,
        longitude: Double = 31.2357,
        date: Date = Date(),
        method: CalculationMethod = CalculationMethod.EGYPTIAN,
        madhab: Madhab = Madhab.SHAFI,
        targetTimeZone: TimeZone? = null
    ): PrayerTimes? {
        return try {
            val tz = targetTimeZone ?: getTargetTimeZone(latitude, longitude, date = date)
            val cal = Calendar.getInstance(tz).apply { time = date }
            val coordinates = Coordinates(latitude, longitude)
            val dateComponents = DateComponents(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )

            val parameters: CalculationParameters = method.parameters.apply {
                this.madhab = madhab
            }

            PrayerTimes(coordinates, dateComponents, parameters)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * تعيين طريقة الحساب الفلكية
     */
    fun mapCalculationMethod(methodName: String): CalculationMethod {
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
     * الحساب الكامل لمنظومة المواقيت والتنبيهات والعد التنازلي بالدقيقة والثانية
     */
    fun calculatePrayerTimes(
        latitude: Double = 30.0444,
        longitude: Double = 31.2357,
        date: Date = Date(),
        methodName: String = "EGYPTIAN",
        onlineResult: AlAdhanTimingResult? = null,
        isEnglish: Boolean = false,
        dstMode: String = "AUTO"
    ): PrayerTimesResult {
        val targetTimeZone = getTargetTimeZone(latitude, longitude, dstMode, date)
        val calendar = Calendar.getInstance(targetTimeZone).apply { time = date }
        val locale = if (isEnglish) Locale.ENGLISH else Locale("ar")
        val timeFormat = SimpleDateFormat("hh:mm a", locale).apply {
            timeZone = targetTimeZone
        }

        val isSummer = when (dstMode.uppercase()) {
            "SUMMER" -> true
            "WINTER" -> false
            else -> isEgyptDaylightSavingTime(date)
        }
        val dstStatusText = if (isSummer) (if (isEnglish) "Summer Time" else "توقيت صيفي") else (if (isEnglish) "Winter Time" else "توقيت شتوي")

        val fajrMs: Long
        val sunriseMs: Long
        val dhuhrMs: Long
        val asrMs: Long
        val maghribMs: Long
        val ishaMs: Long
        val hijriDateStr: String
        val isSynced = onlineResult != null

        if (onlineResult != null) {
            fajrMs = onlineResult.fajrMs
            sunriseMs = onlineResult.sunriseMs
            dhuhrMs = onlineResult.dhuhrMs
            asrMs = onlineResult.asrMs
            maghribMs = onlineResult.maghribMs
            ishaMs = onlineResult.ishaMs
            hijriDateStr = if (onlineResult.hijriDateString.isNotBlank()) {
                onlineResult.hijriDateString
            } else {
                calculateOfflineHijriDate(calendar, isEnglish)
            }
        } else {
            val adhanMethod = mapCalculationMethod(methodName)
            val adhanTimes = getPrayerTimes(
                latitude = latitude,
                longitude = longitude,
                date = date,
                method = adhanMethod
            )

            if (adhanTimes != null) {
                fajrMs = adhanTimes.fajr?.time ?: date.time
                sunriseMs = adhanTimes.sunrise?.time ?: date.time
                dhuhrMs = adhanTimes.dhuhr?.time ?: date.time
                asrMs = adhanTimes.asr?.time ?: date.time
                maghribMs = adhanTimes.maghrib?.time ?: date.time
                ishaMs = adhanTimes.isha?.time ?: date.time
            } else {
                val cal = Calendar.getInstance().apply { time = date }
                cal.set(Calendar.HOUR_OF_DAY, 4); cal.set(Calendar.MINUTE, 30); fajrMs = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 6); cal.set(Calendar.MINUTE, 0); sunriseMs = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 12); cal.set(Calendar.MINUTE, 15); dhuhrMs = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 15); cal.set(Calendar.MINUTE, 30); asrMs = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 18); cal.set(Calendar.MINUTE, 30); maghribMs = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 20); cal.set(Calendar.MINUTE, 0); ishaMs = cal.timeInMillis
            }
            hijriDateStr = calculateOfflineHijriDate(calendar, isEnglish)
        }

        val nowMs = System.currentTimeMillis()

        val fajrName = if (isEnglish) "Fajr" else "الفجر"
        val sunriseName = if (isEnglish) "Sunrise" else "الشروق"
        val dhuhrName = if (isEnglish) "Dhuhr" else "الظهر"
        val asrName = if (isEnglish) "Asr" else "العصر"
        val maghribName = if (isEnglish) "Maghrib" else "المغرب"
        val ishaName = if (isEnglish) "Isha" else "العشاء"

        val list = listOf(
            Triple("fajr", fajrName, fajrMs),
            Triple("dhuhr", dhuhrName, dhuhrMs),
            Triple("asr", asrName, asrMs),
            Triple("maghrib", maghribName, maghribMs),
            Triple("isha", ishaName, ishaMs)
        )

        var nextId = "fajr"
        var nextName = fajrName
        var nextMs = fajrMs
        var timeDiff = 0L

        var foundNext = false
        for (item in list) {
            if (item.third > nowMs) {
                nextId = item.first
                nextName = item.second
                nextMs = item.third
                timeDiff = item.third - nowMs
                foundNext = true
                break
            }
        }

        if (!foundNext) {
            nextId = "fajr"
            nextName = if (isEnglish) "Fajr (Tomorrow)" else "الفجر (غداً)"
            val tomorrowCal = Calendar.getInstance(targetTimeZone).apply {
                time = calendar.time
                add(Calendar.DAY_OF_MONTH, 1)
            }
            val tomorrowTimes = getPrayerTimes(
                latitude = latitude,
                longitude = longitude,
                date = tomorrowCal.time,
                method = mapCalculationMethod(methodName),
                targetTimeZone = targetTimeZone
            )
            nextMs = tomorrowTimes?.fajr?.time ?: (fajrMs + 86400000L)
            timeDiff = (nextMs - nowMs).coerceAtLeast(0L)
        }

        val hoursRemaining = (timeDiff / 3600000).toInt().coerceAtLeast(0)
        val minutesRemaining = ((timeDiff % 3600000) / 60000).toInt().coerceAtLeast(0)
        val secondsRemaining = ((timeDiff % 60000) / 1000).toInt().coerceAtLeast(0)
        val remainingStr = if (isEnglish) {
            "${hoursRemaining}h ${minutesRemaining}m ${secondsRemaining}s"
        } else {
            "$hoursRemaining س $minutesRemaining د $secondsRemaining ث"
        }

        return PrayerTimesResult(
            fajr = PrayerTime("fajr", fajrName, "Fajr", timeFormat.format(Date(fajrMs)), fajrMs, nextId == "fajr"),
            sunrise = PrayerTime("sunrise", sunriseName, "Sunrise", timeFormat.format(Date(sunriseMs)), sunriseMs, false),
            dhuhr = PrayerTime("dhuhr", dhuhrName, "Dhuhr", timeFormat.format(Date(dhuhrMs)), dhuhrMs, nextId == "dhuhr"),
            asr = PrayerTime("asr", asrName, "Asr", timeFormat.format(Date(asrMs)), asrMs, nextId == "asr"),
            maghrib = PrayerTime("maghrib", maghribName, "Maghrib", timeFormat.format(Date(maghribMs)), maghribMs, nextId == "maghrib"),
            isha = PrayerTime("isha", ishaName, "Isha", timeFormat.format(Date(ishaMs)), ishaMs, nextId == "isha"),
            nextPrayerName = nextName,
            nextPrayerTimeMs = nextMs,
            timeRemainingString = remainingStr,
            hijriDateString = hijriDateStr,
            isOnlineSynced = isSynced,
            isSummerTimeActive = isSummer,
            dstMode = dstMode,
            dstStatusText = dstStatusText
        )
    }

    /**
     * حساب وعرض أوقات الصلاة للتقويم والبطاقات
     */
    fun calculateTimes(
        latitude: Double = 30.0444,
        longitude: Double = 31.2357,
        date: Date = Date(),
        madhab: Madhab = Madhab.SHAFI,
        isArabic: Boolean = true,
        dstMode: String = "AUTO"
    ): CalculatedPrayerTimes {
        val prayerTimes = getPrayerTimes(
            latitude = latitude,
            longitude = longitude,
            date = date,
            method = CalculationMethod.EGYPTIAN,
            madhab = madhab
        )

        val locale = if (isArabic) Locale("ar") else Locale.ENGLISH
        val formatter = SimpleDateFormat("hh:mm a", locale)

        val isSummer = when (dstMode.uppercase()) {
            "SUMMER" -> true
            "WINTER" -> false
            else -> isEgyptDaylightSavingTime(date)
        }
        val offsetHours = if (isSummer) 3 else 2
        formatter.timeZone = TimeZone.getTimeZone("GMT+0$offsetHours:00")

        val fajrStr = prayerTimes?.fajr?.let { formatter.format(it) } ?: "--:--"
        val sunriseStr = prayerTimes?.sunrise?.let { formatter.format(it) } ?: "--:--"
        val dhuhrStr = prayerTimes?.dhuhr?.let { formatter.format(it) } ?: "--:--"
        val asrStr = prayerTimes?.asr?.let { formatter.format(it) } ?: "--:--"
        val maghribStr = prayerTimes?.maghrib?.let { formatter.format(it) } ?: "--:--"
        val ishaStr = prayerTimes?.isha?.let { formatter.format(it) } ?: "--:--"

        val now = System.currentTimeMillis()
        val prayersList = listOf(
            Pair("الفجر", prayerTimes?.fajr?.time ?: 0L),
            Pair("الشروق", prayerTimes?.sunrise?.time ?: 0L),
            Pair("الظهر", prayerTimes?.dhuhr?.time ?: 0L),
            Pair("العصر", prayerTimes?.asr?.time ?: 0L),
            Pair("المغرب", prayerTimes?.maghrib?.time ?: 0L),
            Pair("العشاء", prayerTimes?.isha?.time ?: 0L)
        )

        var nextName = "الفجر"
        var remainingStr = ""
        for ((name, timeMs) in prayersList) {
            if (timeMs > now) {
                nextName = name
                val diffSec = (timeMs - now) / 1000
                val hours = diffSec / 3600
                val mins = (diffSec % 3600) / 60
                val secs = diffSec % 60
                remainingStr = if (hours > 0) "باقي $hours س $mins د $secs ث" else "باقي $mins د $secs ث"
                break
            }
        }

        return CalculatedPrayerTimes(
            fajr = fajrStr,
            sunrise = sunriseStr,
            dhuhr = dhuhrStr,
            asr = asrStr,
            maghrib = maghribStr,
            isha = ishaStr,
            fajrDate = prayerTimes?.fajr,
            sunriseDate = prayerTimes?.sunrise,
            dhuhrDate = prayerTimes?.dhuhr,
            asrDate = prayerTimes?.asr,
            maghribDate = prayerTimes?.maghrib,
            ishaDate = prayerTimes?.isha,
            nextPrayerName = nextName,
            timeRemainingString = remainingStr,
            isSummerTimeActive = isSummer
        )
    }

    fun calculateForLocalDate(
        localDate: LocalDate,
        latitude: Double = 30.0444,
        longitude: Double = 31.2357,
        dstMode: String = "AUTO"
    ): CalculatedPrayerTimes {
        val date = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
        return calculateTimes(latitude = latitude, longitude = longitude, date = date, dstMode = dstMode)
    }

    private fun calculateOfflineHijriDate(calendar: Calendar, isEnglish: Boolean): String {
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val month = calendar.get(Calendar.MONTH) + 1
        val hijriDay = ((day + 12) % 30) + 1
        val hijriMonthsAr = listOf("محرم", "صفر", "ربيع الأول", "ربيع الثاني", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")
        val hijriMonthsEn = listOf("Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani", "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah")
        val monthStr = if (isEnglish) hijriMonthsEn[(month + 1) % 12] else hijriMonthsAr[(month + 1) % 12]
        val hijriYear = 1448
        return if (isEnglish) "$hijriDay $monthStr $hijriYear AH" else "$hijriDay $monthStr $hijriYear هـ"
    }
}
