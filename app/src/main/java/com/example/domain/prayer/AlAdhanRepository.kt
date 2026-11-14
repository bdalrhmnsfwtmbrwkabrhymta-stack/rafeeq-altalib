package com.example.domain.prayer

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
        date: Date = Date()
    ): Result<AlAdhanTimingResult> = withContext(Dispatchers.IO) {
        try {
            val dateStr = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(date)
            // Try fetching by coordinates first for precise location, or by city/country
            val urlString = if (latitude != 0.0 && longitude != 0.0) {
                "https://api.aladhan.com/v1/timings/$dateStr?latitude=$latitude&longitude=$longitude&method=$methodId"
            } else {
                val cleanCity = city.replace(" ", "%20")
                val cleanCountry = country.replace(" ", "%20")
                "https://api.aladhan.com/v1/timingsByCity/$dateStr?city=$cleanCity&country=$cleanCountry&method=$methodId"
            }

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 7000
            connection.readTimeout = 7000

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

                    fun timeToMillis(time24: String): Long {
                        val parts = time24.split(":")
                        val h = parts.getOrNull(0)?.toIntOrNull() ?: 12
                        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        val cal = Calendar.getInstance()
                        cal.time = date
                        cal.set(Calendar.HOUR_OF_DAY, h)
                        cal.set(Calendar.MINUTE, m)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
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
}
