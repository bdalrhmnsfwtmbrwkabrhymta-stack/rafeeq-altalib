package com.example.domain.prayer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

data class AlAdhanTimingResult(
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val fajrMs: Long,
    val sunriseMs: Long,
    val dhuhrMs: Long,
    val asrMs: Long,
    val maghribMs: Long,
    val ishaMs: Long,
    val hijriDateString: String,
    val timezone: String
)

class AlAdhanRepository {

    suspend fun fetchPrayerTimesOnline(
        city: String,
        country: String,
        latitude: Double,
        longitude: Double,
        methodId: Int,
        date: Date = Date(),
        school: Int = 0
    ): Result<AlAdhanTimingResult> = withContext(Dispatchers.IO) {
        try {
            val targetLocationTz = com.example.data.prayer.PrayerTimeCalculator.getTargetTimeZone(latitude, longitude, date = date)
            val dateStr = SimpleDateFormat("dd-MM-yyyy", Locale.US).apply {
                timeZone = targetLocationTz
            }.format(date)
            // Try fetching by coordinates first for precise location, or by city/country
            val urlString = if (latitude != 0.0 && longitude != 0.0) {
                "https://api.aladhan.com/v1/timings/$dateStr?latitude=$latitude&longitude=$longitude&method=$methodId&school=$school"
            } else {
                val cleanCity = city.replace(" ", "%20")
                val cleanCountry = country.replace(" ", "%20")
                "https://api.aladhan.com/v1/timingsByCity/$dateStr?city=$cleanCity&country=$cleanCountry&method=$methodId&school=$school"
            }

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)
                if (root.optInt("code", 0) == 200 && root.has("data")) {
                    val data = root.getJSONObject("data")
                    val timings = data.getJSONObject("timings")
                    val meta = data.optJSONObject("meta")
                    val timezoneStr = meta?.optString("timezone", "Africa/Cairo") ?: "Africa/Cairo"

                    val fajrStr = timings.getString("Fajr").substringBefore(" ")
                    val sunriseStr = timings.getString("Sunrise").substringBefore(" ")
                    val dhuhrStr = timings.getString("Dhuhr").substringBefore(" ")
                    val asrStr = timings.getString("Asr").substringBefore(" ")
                    val maghribStr = timings.getString("Maghrib").substringBefore(" ")
                    val ishaStr = timings.getString("Isha").substringBefore(" ")

                    // Hijri Date parsing
                    var hijriStr = ""
                    if (data.has("date")) {
                        val dateObj = data.getJSONObject("date")
                        if (dateObj.has("hijri")) {
                            val hijriObj = dateObj.getJSONObject("hijri")
                            val day = hijriObj.optString("day", "")
                            val monthObj = hijriObj.optJSONObject("month")
                            val monthAr = monthObj?.optString("ar", "") ?: ""
                            val year = hijriObj.optString("year", "")
                            hijriStr = "$day $monthAr $year هـ"
                        }
                    }

                    val targetTz = TimeZone.getTimeZone(timezoneStr)
                    val baseCal = Calendar.getInstance(targetTz).apply { time = date }
                    fun timeToMillis(time24: String): Long {
                        val parts = time24.split(":")
                        val h = parts.getOrNull(0)?.toIntOrNull() ?: 12
                        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        val cal = Calendar.getInstance(targetTz).apply {
                            set(
                                baseCal.get(Calendar.YEAR),
                                baseCal.get(Calendar.MONTH),
                                baseCal.get(Calendar.DAY_OF_MONTH),
                                h,
                                m,
                                0
                            )
                            set(Calendar.MILLISECOND, 0)
                        }
                        return cal.timeInMillis
                    }

                    val parsed = AlAdhanTimingResult(
                        fajr = fajrStr,
                        sunrise = sunriseStr,
                        dhuhr = dhuhrStr,
                        asr = asrStr,
                        maghrib = maghribStr,
                        isha = ishaStr,
                        fajrMs = timeToMillis(fajrStr),
                        sunriseMs = timeToMillis(sunriseStr),
                        dhuhrMs = timeToMillis(dhuhrStr),
                        asrMs = timeToMillis(asrStr),
                        maghribMs = timeToMillis(maghribStr),
                        ishaMs = timeToMillis(ishaStr),
                        hijriDateString = hijriStr,
                        timezone = timezoneStr
                    )
                    Result.success(parsed)
                } else {
                    Result.failure(Exception("AlAdhan API returned non-200 payload"))
                }
            } else {
                Result.failure(Exception("HTTP Error: ${connection.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchOnlineMuezzins(): List<MuezzinVoice> = withContext(Dispatchers.IO) {
        // Can sync online muezzin voices from network endpoint if available, or return updated catalog
        try {
            PrayerDataConstants.MUEZZIN_VOICES
        } catch (e: Exception) {
            PrayerDataConstants.MUEZZIN_VOICES
        }
    }

    companion object {
        private const val PREFS_CACHE = "aladhan_daily_cache"

        fun mapMethodToAlAdhanId(methodName: String): Int {
            return when (methodName.uppercase()) {
                "EGYPTIAN", "5" -> 5
                "UMM_AL_QURA", "4" -> 4
                "MWL", "MUSLIM_WORLD_LEAGUE", "3" -> 3
                "ISNA", "NORTH_AMERICA", "2" -> 2
                "KARACHI", "1" -> 1
                "KUWAIT" -> 9
                "QATAR" -> 10
                "DUBAI", "GULF" -> 8
                "SINGAPORE" -> 11
                else -> 5
            }
        }

        private fun buildCacheKey(
            date: Date,
            latitude: Double,
            longitude: Double,
            methodName: String,
            madhabName: String
        ): String {
            val tz = com.example.data.prayer.PrayerTimeCalculator.getTargetTimeZone(latitude, longitude, date = date)
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = tz
            }.format(date)
            return "$dateStr|${String.format(Locale.US, "%.3f", latitude)}|${String.format(Locale.US, "%.3f", longitude)}|${methodName.uppercase()}|${madhabName.uppercase()}"
        }

        fun getCachedDailyTimings(
            context: Context,
            date: Date = Date(),
            latitude: Double,
            longitude: Double,
            methodName: String,
            madhabName: String = "SHAFI"
        ): AlAdhanTimingResult? {
            return try {
                val prefs = context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)
                val key = buildCacheKey(date, latitude, longitude, methodName, madhabName)
                val raw = prefs.getString(key, null) ?: return null
                val obj = JSONObject(raw)
                AlAdhanTimingResult(
                    fajr = obj.getString("fajr"),
                    sunrise = obj.getString("sunrise"),
                    dhuhr = obj.getString("dhuhr"),
                    asr = obj.getString("asr"),
                    maghrib = obj.getString("maghrib"),
                    isha = obj.getString("isha"),
                    fajrMs = obj.getLong("fajrMs"),
                    sunriseMs = obj.getLong("sunriseMs"),
                    dhuhrMs = obj.getLong("dhuhrMs"),
                    asrMs = obj.getLong("asrMs"),
                    maghribMs = obj.getLong("maghribMs"),
                    ishaMs = obj.getLong("ishaMs"),
                    hijriDateString = obj.optString("hijriDateString", ""),
                    timezone = obj.optString("timezone", "Africa/Cairo")
                )
            } catch (_: Exception) {
                null
            }
        }

        fun saveCachedDailyTimings(
            context: Context,
            date: Date,
            latitude: Double,
            longitude: Double,
            methodName: String,
            madhabName: String,
            result: AlAdhanTimingResult
        ) {
            try {
                val prefs = context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)
                val key = buildCacheKey(date, latitude, longitude, methodName, madhabName)
                val json = JSONObject().apply {
                    put("fajr", result.fajr)
                    put("sunrise", result.sunrise)
                    put("dhuhr", result.dhuhr)
                    put("asr", result.asr)
                    put("maghrib", result.maghrib)
                    put("isha", result.isha)
                    put("fajrMs", result.fajrMs)
                    put("sunriseMs", result.sunriseMs)
                    put("dhuhrMs", result.dhuhrMs)
                    put("asrMs", result.asrMs)
                    put("maghribMs", result.maghribMs)
                    put("ishaMs", result.ishaMs)
                    put("hijriDateString", result.hijriDateString)
                    put("timezone", result.timezone)
                }
                prefs.edit().putString(key, json.toString()).apply()
            } catch (_: Exception) {}
        }

        suspend fun getOrFetchDailyTimings(
            context: Context,
            city: String,
            country: String,
            latitude: Double,
            longitude: Double,
            methodName: String,
            madhabName: String = "SHAFI",
            date: Date = Date()
        ): AlAdhanTimingResult? {
            getCachedDailyTimings(context, date, latitude, longitude, methodName, madhabName)?.let {
                return it
            }
            val methodId = mapMethodToAlAdhanId(methodName)
            val school = if (madhabName.equals("HANAFI", ignoreCase = true)) 1 else 0
            val res = AlAdhanRepository().fetchPrayerTimesOnline(
                city = city,
                country = country,
                latitude = latitude,
                longitude = longitude,
                methodId = methodId,
                date = date,
                school = school
            )
            val data = res.getOrNull() ?: return null
            saveCachedDailyTimings(context, date, latitude, longitude, methodName, madhabName, data)
            return data
        }
    }
}
