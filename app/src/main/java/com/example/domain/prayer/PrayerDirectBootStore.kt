package com.example.domain.prayer

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.example.data.preferences.UserSettings

/**
 * Lightweight snapshot of prayer, Azkar, and Salawat scheduling settings stored in
 * Android Device-Protected Storage (`createDeviceProtectedStorageContext()`).
 *
 * This guarantees exact alarms can be re-armed immediately on LOCKED_BOOT_COMPLETED,
 * BOOT_COMPLETED, and USER_UNLOCKED even if the device was rebooted overnight and has
 * not been unlocked or opened yet, without touching credential-encrypted DataStore.
 */
data class PrayerScheduleSnapshot(
    val latitude: Double = 30.0444,
    val longitude: Double = 31.2357,
    val cityLocation: String = "القاهرة",
    val countryLocation: String = "مصر",
    val calculationMethod: String = "EGYPTIAN",
    val madhab: String = "SHAFI",
    val dstMode: String = "AUTO",
    val appLanguage: String = "ar",
    val isPrayerNotificationEnabled: Boolean = true,
    val isAdhanFullAudio: Boolean = true,
    val isSunriseAlertEnabled: Boolean = false,
    val muezzinVoice: String = "الأذان المصري الأصيل (MBC وإذاعة القرآن)",
    val mutedPrayers: Set<String> = emptySet(),
    val isMorningAzkarEnabled: Boolean = true,
    val morningAzkarHour: Int = 9,
    val morningAzkarMinute: Int = 0,
    val isEveningAzkarEnabled: Boolean = true,
    val eveningAzkarHour: Int = 17,
    val eveningAzkarMinute: Int = 0,
    val isSalawatEnabled: Boolean = true,
    val salawatIntervalMinutes: Int = 60,
    val salawatVoiceId: Int = 1,
    val salawatAudioMode: String = "VOICE_FORMULA",
    val customSalawatAudioUri: String = ""
) {
    fun toUserSettings(): UserSettings = UserSettings(
        latitude = latitude,
        longitude = longitude,
        cityLocation = cityLocation,
        countryLocation = countryLocation,
        calculationMethod = calculationMethod,
        madhab = madhab,
        dstMode = dstMode,
        appLanguage = appLanguage,
        isPrayerNotificationEnabled = isPrayerNotificationEnabled,
        isAdhanFullAudio = isAdhanFullAudio,
        isSunriseAlertEnabled = isSunriseAlertEnabled,
        muezzinVoice = muezzinVoice,
        mutedPrayers = mutedPrayers,
        isMorningAzkarEnabled = isMorningAzkarEnabled,
        morningAzkarHour = morningAzkarHour,
        morningAzkarMinute = morningAzkarMinute,
        isEveningAzkarEnabled = isEveningAzkarEnabled,
        eveningAzkarHour = eveningAzkarHour,
        eveningAzkarMinute = eveningAzkarMinute,
        isSalawatEnabled = isSalawatEnabled,
        salawatIntervalMinutes = salawatIntervalMinutes,
        salawatVoiceId = salawatVoiceId,
        salawatAudioMode = salawatAudioMode,
        customSalawatAudioUri = customSalawatAudioUri
    )
}

object PrayerDirectBootStore {
    private const val TAG = "PrayerDirectBootStore"
    private const val PREFS_NAME = "prayer_direct_boot_prefs"

    private const val KEY_LAT = "lat"
    private const val KEY_LNG = "lng"
    private const val KEY_CITY = "city"
    private const val KEY_COUNTRY = "country"
    private const val KEY_METHOD = "method"
    private const val KEY_MADHAB = "madhab"
    private const val KEY_DST = "dst"
    private const val KEY_LANG = "lang"
    private const val KEY_PRAYER_ENABLED = "prayer_enabled"
    private const val KEY_ADHAN_FULL = "adhan_full"
    private const val KEY_SUNRISE_ENABLED = "sunrise_enabled"
    private const val KEY_MUEZZIN = "muezzin"
    private const val KEY_MUTED = "muted_prayers"
    private const val KEY_MORNING_AZKAR_ENABLED = "morning_azkar_enabled"
    private const val KEY_MORNING_AZKAR_HOUR = "morning_azkar_hour"
    private const val KEY_MORNING_AZKAR_MINUTE = "morning_azkar_minute"
    private const val KEY_EVENING_AZKAR_ENABLED = "evening_azkar_enabled"
    private const val KEY_EVENING_AZKAR_HOUR = "evening_azkar_hour"
    private const val KEY_EVENING_AZKAR_MINUTE = "evening_azkar_minute"
    private const val KEY_SALAWAT_ENABLED = "salawat_enabled"
    private const val KEY_SALAWAT_INTERVAL = "salawat_interval"
    private const val KEY_SALAWAT_VOICE_ID = "salawat_voice_id"
    private const val KEY_SALAWAT_AUDIO_MODE = "salawat_audio_mode"
    private const val KEY_SALAWAT_CUSTOM_URI = "salawat_custom_uri"
    private const val KEY_NEXT_SALAWAT_TRIGGER_MS = "next_salawat_trigger_ms"
    private const val KEY_SAVED_SALAWAT_INTERVAL = "saved_salawat_interval"
    private const val KEY_LAST_RESCHEDULE_MS = "last_reschedule_ms"

    fun getDeviceProtectedContext(context: Context): Context {
        val appCtx = context.applicationContext ?: context
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { appCtx.createDeviceProtectedStorageContext() }.getOrDefault(appCtx)
        } else {
            appCtx
        }
    }

    fun isUserUnlocked(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val um = context.getSystemService(Context.USER_SERVICE) as? UserManager
            um?.isUserUnlocked ?: true
        } else {
            true
        }
    }

    private fun prefs(context: Context): SharedPreferences {
        return getDeviceProtectedContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveFromSettings(context: Context, settings: UserSettings) {
        try {
            prefs(context).edit()
                .putString(KEY_LAT, settings.latitude.toString())
                .putString(KEY_LNG, settings.longitude.toString())
                .putString(KEY_CITY, settings.cityLocation)
                .putString(KEY_COUNTRY, settings.countryLocation)
                .putString(KEY_METHOD, settings.calculationMethod)
                .putString(KEY_MADHAB, settings.madhab)
                .putString(KEY_DST, settings.dstMode)
                .putString(KEY_LANG, settings.appLanguage)
                .putBoolean(KEY_PRAYER_ENABLED, settings.isPrayerNotificationEnabled)
                .putBoolean(KEY_ADHAN_FULL, settings.isAdhanFullAudio)
                .putBoolean(KEY_SUNRISE_ENABLED, settings.isSunriseAlertEnabled)
                .putString(KEY_MUEZZIN, settings.muezzinVoice)
                .putStringSet(KEY_MUTED, settings.mutedPrayers.toSet())
                .putBoolean(KEY_MORNING_AZKAR_ENABLED, settings.isMorningAzkarEnabled)
                .putInt(KEY_MORNING_AZKAR_HOUR, settings.morningAzkarHour)
                .putInt(KEY_MORNING_AZKAR_MINUTE, settings.morningAzkarMinute)
                .putBoolean(KEY_EVENING_AZKAR_ENABLED, settings.isEveningAzkarEnabled)
                .putInt(KEY_EVENING_AZKAR_HOUR, settings.eveningAzkarHour)
                .putInt(KEY_EVENING_AZKAR_MINUTE, settings.eveningAzkarMinute)
                .putBoolean(KEY_SALAWAT_ENABLED, settings.isSalawatEnabled)
                .putInt(KEY_SALAWAT_INTERVAL, settings.salawatIntervalMinutes.coerceAtLeast(1))
                .putInt(KEY_SALAWAT_VOICE_ID, settings.salawatVoiceId)
                .putString(KEY_SALAWAT_AUDIO_MODE, settings.salawatAudioMode)
                .putString(KEY_SALAWAT_CUSTOM_URI, settings.customSalawatAudioUri)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving direct boot prayer snapshot", e)
        }
    }

    fun loadSnapshot(context: Context): PrayerScheduleSnapshot {
        return try {
            val p = prefs(context)
            PrayerScheduleSnapshot(
                latitude = p.getString(KEY_LAT, null)?.toDoubleOrNull() ?: 30.0444,
                longitude = p.getString(KEY_LNG, null)?.toDoubleOrNull() ?: 31.2357,
                cityLocation = p.getString(KEY_CITY, null) ?: "القاهرة",
                countryLocation = p.getString(KEY_COUNTRY, null) ?: "مصر",
                calculationMethod = p.getString(KEY_METHOD, null) ?: "EGYPTIAN",
                madhab = p.getString(KEY_MADHAB, null) ?: "SHAFI",
                dstMode = p.getString(KEY_DST, null) ?: "AUTO",
                appLanguage = p.getString(KEY_LANG, null) ?: "ar",
                isPrayerNotificationEnabled = p.getBoolean(KEY_PRAYER_ENABLED, true),
                isAdhanFullAudio = p.getBoolean(KEY_ADHAN_FULL, true),
                isSunriseAlertEnabled = p.getBoolean(KEY_SUNRISE_ENABLED, false),
                muezzinVoice = p.getString(KEY_MUEZZIN, null) ?: "الأذان المصري الأصيل (MBC وإذاعة القرآن)",
                mutedPrayers = p.getStringSet(KEY_MUTED, emptySet())?.toSet() ?: emptySet(),
                isMorningAzkarEnabled = p.getBoolean(KEY_MORNING_AZKAR_ENABLED, true),
                morningAzkarHour = p.getInt(KEY_MORNING_AZKAR_HOUR, 9),
                morningAzkarMinute = p.getInt(KEY_MORNING_AZKAR_MINUTE, 0),
                isEveningAzkarEnabled = p.getBoolean(KEY_EVENING_AZKAR_ENABLED, true),
                eveningAzkarHour = p.getInt(KEY_EVENING_AZKAR_HOUR, 17),
                eveningAzkarMinute = p.getInt(KEY_EVENING_AZKAR_MINUTE, 0),
                isSalawatEnabled = p.getBoolean(KEY_SALAWAT_ENABLED, true),
                salawatIntervalMinutes = p.getInt(KEY_SALAWAT_INTERVAL, 60).coerceAtLeast(1),
                salawatVoiceId = p.getInt(KEY_SALAWAT_VOICE_ID, 1),
                salawatAudioMode = p.getString(KEY_SALAWAT_AUDIO_MODE, null) ?: "VOICE_FORMULA",
                customSalawatAudioUri = p.getString(KEY_SALAWAT_CUSTOM_URI, null) ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading direct boot prayer snapshot", e)
            PrayerScheduleSnapshot()
        }
    }

    fun getNextSalawatTriggerMs(context: Context): Long =
        runCatching { prefs(context).getLong(KEY_NEXT_SALAWAT_TRIGGER_MS, 0L) }.getOrDefault(0L)

    fun getSavedSalawatIntervalMinutes(context: Context): Int =
        runCatching { prefs(context).getInt(KEY_SAVED_SALAWAT_INTERVAL, 60) }.getOrDefault(60)

    fun saveSalawatScheduleState(context: Context, enabled: Boolean, intervalMinutes: Int, nextTriggerMs: Long) {
        runCatching {
            prefs(context).edit()
                .putBoolean(KEY_SALAWAT_ENABLED, enabled)
                .putInt(KEY_SALAWAT_INTERVAL, intervalMinutes.coerceAtLeast(1))
                .putInt(KEY_SAVED_SALAWAT_INTERVAL, intervalMinutes.coerceAtLeast(1))
                .putLong(KEY_NEXT_SALAWAT_TRIGGER_MS, nextTriggerMs)
                .apply()
        }
    }

    fun saveSalawatVoicePreferences(context: Context, voiceId: Int, audioMode: String, customUri: String) {
        runCatching {
            prefs(context).edit()
                .putInt(KEY_SALAWAT_VOICE_ID, voiceId)
                .putString(KEY_SALAWAT_AUDIO_MODE, audioMode)
                .putString(KEY_SALAWAT_CUSTOM_URI, customUri)
                .apply()
        }
    }

    fun markRescheduledNow(context: Context) {
        runCatching {
            prefs(context).edit().putLong(KEY_LAST_RESCHEDULE_MS, System.currentTimeMillis()).apply()
        }
    }

    fun getLastRescheduleMs(context: Context): Long =
        runCatching { prefs(context).getLong(KEY_LAST_RESCHEDULE_MS, 0L) }.getOrDefault(0L)
}
