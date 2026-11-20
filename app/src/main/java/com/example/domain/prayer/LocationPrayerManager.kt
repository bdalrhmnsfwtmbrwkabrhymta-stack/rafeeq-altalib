package com.example.domain.prayer

import android.content.Context
import android.util.Log
import com.example.data.prayer.UserLocationData
import com.example.data.prayer.UserLocationProvider
import com.example.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class SavedPrayerLocation(
    val latitude: Double,
    val longitude: Double,
    val cityName: String,
    val countryName: String,
    val isAutoLocked: Boolean
)

data class CityLocationPreset(
    val cityNameAr: String,
    val countryNameAr: String,
    val latitude: Double,
    val longitude: Double,
    val recommendedMethod: CalculationMethod = CalculationMethod.EGYPTIAN
)

object LocationPrayerManager {

    private const val TAG = "LocationPrayerManager"
    private const val PREFS_NAME = "hawas_location_prayer_prefs"
    private const val KEY_IS_LOCATION_CONFIGURED = "is_location_configured"
    private const val KEY_AUTO_LOCK_LOCATION = "auto_lock_location"
    private const val KEY_LATITUDE = "saved_latitude"
    private const val KEY_LONGITUDE = "saved_longitude"
    private const val KEY_CITY_NAME = "saved_city_name"
    private const val KEY_COUNTRY_NAME = "saved_country_name"

    val presetCities: List<CityLocationPreset> = listOf(
        CityLocationPreset("القاهرة", "مصر", 30.0444, 31.2357, CalculationMethod.EGYPTIAN),
        CityLocationPreset("الجيزة", "مصر", 30.0131, 31.2089, CalculationMethod.EGYPTIAN),
        CityLocationPreset("الإسكندرية", "مصر", 31.2001, 29.9187, CalculationMethod.EGYPTIAN),
        CityLocationPreset("المنصورة", "مصر", 31.0409, 31.3785, CalculationMethod.EGYPTIAN),
        CityLocationPreset("طنطا", "مصر", 30.7865, 31.0004, CalculationMethod.EGYPTIAN),
        CityLocationPreset("الزقازيق", "مصر", 30.5877, 31.5020, CalculationMethod.EGYPTIAN),
        CityLocationPreset("بنها", "مصر", 30.4660, 31.1848, CalculationMethod.EGYPTIAN),
        CityLocationPreset("المنوفية (شبين الكوم)", "مصر", 30.5503, 31.0106, CalculationMethod.EGYPTIAN),
        CityLocationPreset("دمنهور", "مصر", 31.0409, 30.4700, CalculationMethod.EGYPTIAN),
        CityLocationPreset("كفر الشيخ", "مصر", 31.1107, 30.9388, CalculationMethod.EGYPTIAN),
        CityLocationPreset("بورسعيد", "مصر", 31.2653, 32.3019, CalculationMethod.EGYPTIAN),
        CityLocationPreset("الإسماعيلية", "مصر", 30.5965, 32.2715, CalculationMethod.EGYPTIAN),
        CityLocationPreset("السويس", "مصر", 29.9668, 32.5498, CalculationMethod.EGYPTIAN),
        CityLocationPreset("الفيوم", "مصر", 29.3084, 30.8428, CalculationMethod.EGYPTIAN),
        CityLocationPreset("بني سويف", "مصر", 29.0661, 31.0994, CalculationMethod.EGYPTIAN),
        CityLocationPreset("المنيا", "مصر", 28.1099, 30.7503, CalculationMethod.EGYPTIAN),
        CityLocationPreset("أسيوط", "مصر", 27.1783, 31.1859, CalculationMethod.EGYPTIAN),
        CityLocationPreset("سوهاج", "مصر", 26.5590, 31.6957, CalculationMethod.EGYPTIAN),
        CityLocationPreset("قنا", "مصر", 26.1551, 32.7160, CalculationMethod.EGYPTIAN),
        CityLocationPreset("الأقصر", "مصر", 25.6872, 32.6396, CalculationMethod.EGYPTIAN),
        CityLocationPreset("أسوان", "مصر", 24.0889, 32.8998, CalculationMethod.EGYPTIAN),
        CityLocationPreset("مكة المكرمة", "السعودية", 21.3891, 39.8579, CalculationMethod.UMM_AL_QURA),
        CityLocationPreset("المدينة المنورة", "السعودية", 24.5247, 39.5692, CalculationMethod.UMM_AL_QURA),
        CityLocationPreset("الرياض", "السعودية", 24.7136, 46.6753, CalculationMethod.UMM_AL_QURA),
        CityLocationPreset("جدة", "السعودية", 21.4858, 39.1925, CalculationMethod.UMM_AL_QURA),
        CityLocationPreset("دبي", "الإمارات", 25.2048, 55.2708, CalculationMethod.MWL),
        CityLocationPreset("عمّان", "الأردن", 31.9454, 35.9284, CalculationMethod.MWL),
        CityLocationPreset("بغداد", "العراق", 33.3152, 44.3661, CalculationMethod.MWL),
        CityLocationPreset("الرباط", "المغرب", 34.0209, -6.8416, CalculationMethod.MWL),
        CityLocationPreset("الجزائر", "الجزائر", 36.7538, 3.0588, CalculationMethod.MWL),
        CityLocationPreset("تونس", "تونس", 36.8065, 10.1815, CalculationMethod.MWL)
    )

    fun isLocationConfigured(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_LOCATION_CONFIGURED, false)
    }

    fun getSavedLocation(context: Context): SavedPrayerLocation? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_IS_LOCATION_CONFIGURED, false)) return null
        val latStr = prefs.getString(KEY_LATITUDE, null) ?: return null
        val lngStr = prefs.getString(KEY_LONGITUDE, null) ?: return null
        val lat = latStr.toDoubleOrNull() ?: return null
        val lng = lngStr.toDoubleOrNull() ?: return null
        val city = prefs.getString(KEY_CITY_NAME, "القاهرة") ?: "القاهرة"
        val country = prefs.getString(KEY_COUNTRY_NAME, "مصر") ?: "مصر"
        val autoLock = prefs.getBoolean(KEY_AUTO_LOCK_LOCATION, true)
        return SavedPrayerLocation(
            latitude = lat,
            longitude = lng,
            cityName = city,
            countryName = country,
            isAutoLocked = autoLock
        )
    }

    fun saveConfiguredLocation(
        context: Context,
        latitude: Double,
        longitude: Double,
        cityName: String,
        countryName: String,
        autoLock: Boolean = true
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_IS_LOCATION_CONFIGURED, true)
            .putBoolean(KEY_AUTO_LOCK_LOCATION, autoLock)
            .putString(KEY_LATITUDE, latitude.toString())
            .putString(KEY_LONGITUDE, longitude.toString())
            .putString(KEY_CITY_NAME, cityName)
            .putString(KEY_COUNTRY_NAME, countryName)
            .apply()
    }

    suspend fun detectGpsAndSave(
        context: Context,
        prefsRepo: UserPreferencesRepository,
        currentCalcMethod: String,
        autoLock: Boolean = true
    ): Result<UserLocationData> = withContext(Dispatchers.IO) {
        try {
            val result = withTimeoutOrNull(12_000L) {
                UserLocationProvider.getCurrentLocation(context)
            } ?: return@withContext Result.failure(
                Exception("تعذر التقاط إشارة GPS سريعاً. يمكنك اختيار محافظتك أو مدينتك يدوياً بالأسفل.")
            )

            if (result.isFailure) {
                return@withContext result
            }

            val loc = result.getOrThrow()
            val resolvedMethod = inferCalculationMethod(loc.countryName, currentCalcMethod)

            saveConfiguredLocation(
                context = context,
                latitude = loc.latitude,
                longitude = loc.longitude,
                cityName = loc.cityName,
                countryName = loc.countryName,
                autoLock = autoLock
            )

            prefsRepo.updatePrayerLocation(
                city = loc.cityName,
                country = loc.countryName,
                lat = loc.latitude,
                lng = loc.longitude,
                method = resolvedMethod
            )

            PrayerAlarmScheduler.rescheduleAllSuspending(context)
            Result.success(loc)
        } catch (t: Throwable) {
            Log.e(TAG, "detectGpsAndSave failed", t)
            Result.failure(t)
        }
    }

    suspend fun saveManualCityAndSchedule(
        context: Context,
        prefsRepo: UserPreferencesRepository,
        preset: CityLocationPreset,
        autoLock: Boolean = true
    ) = withContext(Dispatchers.IO) {
        saveConfiguredLocation(
            context = context,
            latitude = preset.latitude,
            longitude = preset.longitude,
            cityName = preset.cityNameAr,
            countryName = preset.countryNameAr,
            autoLock = autoLock
        )
        prefsRepo.updatePrayerLocation(
            city = preset.cityNameAr,
            country = preset.countryNameAr,
            lat = preset.latitude,
            lng = preset.longitude,
            method = preset.recommendedMethod.name
        )
        PrayerAlarmScheduler.rescheduleAllSuspending(context)
    }

    suspend fun syncSavedLocationSilently(
        context: Context,
        prefsRepo: UserPreferencesRepository
    ) = withContext(Dispatchers.IO) {
        try {
            val saved = getSavedLocation(context) ?: return@withContext
            val method = inferCalculationMethod(saved.countryName, CalculationMethod.EGYPTIAN.name)
            prefsRepo.updatePrayerLocation(
                city = saved.cityName,
                country = saved.countryName,
                lat = saved.latitude,
                lng = saved.longitude,
                method = method
            )
            PrayerAlarmScheduler.rescheduleAllSuspending(context)
        } catch (t: Throwable) {
            Log.w(TAG, "Silent saved location sync skipped: ${t.message}")
        }
    }

    private fun inferCalculationMethod(countryName: String, fallback: String): String {
        val norm = countryName.lowercase()
        return when {
            norm.contains("مصر") || norm.contains("egypt") -> CalculationMethod.EGYPTIAN.name
            norm.contains("سعود") || norm.contains("saudi") -> CalculationMethod.UMM_AL_QURA.name
            else -> fallback.ifBlank { CalculationMethod.EGYPTIAN.name }
        }
    }
}
