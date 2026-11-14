package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

data class UserSettings(
    val userName: String = "عبد الرحمن صفوت",
    val whatsappNumber: String = "",
    val telegramNumber: String = "",
    val isTelegramLinked: Boolean = false,
    val telegramChatId: Long = 0L,
    val teacherWhatsappNumber: String = "",
    val studentPassword: String = "",
    val pinCode: String = "",
    val adminPin: String = "1234",
    val isBiometricEnabled: Boolean = false,
    val isPinLockEnabled: Boolean = false,
    val isAppLockedByAdmin: Boolean = false,
    val adminLockMessage: String = "التطبيق مقفل مؤقتاً بواسطة المشرف.",
    val isOnboardingCompleted: Boolean = false,
    val isSetupCompleted: Boolean = false,
    val educationalStage: String = "ثانوي",
    val studentGradeYear: String = "الصف الثالث الثانوي",
    val highSchoolBranch: String = "علمي علوم",
    val studentAge: Int = 17,
    val savedLoginPhone: String = "",
    val savedLoginPin: String = "",
    val dailyGoal: String = "إنهاء 4 مهام دراسية ومراجعة البطاقات",
    val studentPoints: Int = 100,
    val appLanguage: String = "ar",
    val isDarkMode: Boolean? = false,
    val appThemePreset: String = "ROYAL_BLUE",
    val customAppColorHex: String = "#2563EB",
    val customBgColorHex: String = "#F8FAFC",
    val customCardColorHex: String = "#FFFFFF",
    val customTextColorHex: String = "#0F172A",
    val firebaseWebClientId: String = "",
    val userGmail: String = "",
    val geminiApiKey: String = "",
    val firebaseLastUpdateTime: Long = 0L,
    val dailyWaterCups: Int = 0,
    val dailyWaterGoalCups: Int = 8,
    val fridaySurahKahfRead: Boolean = false,
    val fridaySurahJumuahRead: Boolean = false,
    val isDailyWhatsAppReminderEnabled: Boolean = false,
    val whatsappDailyReminderHour: Int = 20,
    val whatsappDailyReminderMinute: Int = 0,
    
    // Prayer & Azkar settings
    val calculationMethod: String = "EGYPTIAN",
    val madhab: String = "SHAFI",
    val latitude: Double = 30.0444,
    val longitude: Double = 31.2357,
    val cityLocation: String = "القاهرة",
    val countryLocation: String = "مصر",
    val dstMode: String = "AUTO",
    val isPrayerNotificationEnabled: Boolean = true,
    val isAdhanFullAudio: Boolean = true,
    val muezzinVoice: String = "أذان مكة المكرمة",
    val mutedPrayers: Set<String> = emptySet(),
    val isMorningAzkarEnabled: Boolean = true,
    val morningAzkarHour: Int = 6,
    val morningAzkarMinute: Int = 0,
    val isEveningAzkarEnabled: Boolean = true,
    val eveningAzkarHour: Int = 17,
    val eveningAzkarMinute: Int = 30,
    val isSalawatEnabled: Boolean = true,
    val salawatIntervalMinutes: Int = 30,
    val salawatVoiceId: Int = 1,
    val salawatAudioMode: String = "VOICE_FORMULA",
    val customSalawatAudioUri: String = "",
    val customSalawatAudioName: String = "",
    
    // Schedule settings
    val isLectureNotificationEnabled: Boolean = true,
    val lectureReminderMinutesBefore: Int = 15,
    
    // Quran settings
    val quranDailyPagesGoal: Int = 4,
    val quranBookmarkSurah: Int = 1,
    val quranBookmarkAyah: Int = 1,
    val quranBookmarkSurahName: String = "الفاتحة",
    val quranFontSizeSp: Int = 22,
    val quranPagesReadToday: Int = 0,
    
    // Pomodoro sounds
    val pomodoroStartSoundUri: String = "",
    val pomodoroBreakSoundUri: String = "",
    val pomodoroFinishSoundUri: String = "",
    val pomodoroFinishSoundName: String = "Default Bell",
    
    // Gamification & Quest System
    val playerLevel: Int = 1,
    val playerXp: Int = 0,
    val playerRank: String = "BRONZE",
    val streakShieldsCount: Int = 2,
    val currentGlobalStreak: Int = 0,
    val bestGlobalStreak: Int = 0,
    val lastActiveDayString: String = "",
    val isSoundEffectsEnabled: Boolean = true,
    val equippedTitle: String = "طالب مجتهد",
    val equippedFrame: String = "BRONZE_RING",
    val equippedAvatar: String = "DEFAULT_STUDENT",
    val dailyWakeUpHour: Int = 5,
    val dailyWakeUpMinute: Int = 30,
    val dailyDeadlineHour: Int = 23,
    val dailyDeadlineMinute: Int = 59
) {
    val lastReadSurahName: String get() = quranBookmarkSurahName
    val lastReadAyahNumber: Int get() = quranBookmarkAyah
}

class UserPreferencesRepository(private val context: Context) {

    companion object {
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_WHATSAPP_NUMBER = stringPreferencesKey("whatsapp_number")
        val KEY_TELEGRAM_NUMBER = stringPreferencesKey("telegram_number")
        val KEY_TEACHER_WHATSAPP = stringPreferencesKey("teacher_whatsapp_number")
        val KEY_STUDENT_PASSWORD = stringPreferencesKey("student_password")
        val KEY_PIN_CODE = stringPreferencesKey("pin_code")
        val KEY_ADMIN_PIN = stringPreferencesKey("admin_pin")
        val KEY_IS_BIOMETRIC_ENABLED = booleanPreferencesKey("is_biometric_enabled")
        val KEY_IS_SETUP_COMPLETED = booleanPreferencesKey("is_setup_completed")
        val KEY_EDUCATIONAL_STAGE = stringPreferencesKey("educational_stage")
        val KEY_STUDENT_GRADE_YEAR = stringPreferencesKey("student_grade_year")
        val KEY_HIGH_SCHOOL_BRANCH = stringPreferencesKey("high_school_branch")
        val KEY_STUDENT_AGE = intPreferencesKey("student_age")
        val KEY_SAVED_LOGIN_PHONE = stringPreferencesKey("saved_login_phone")
        val KEY_SAVED_LOGIN_PIN = stringPreferencesKey("saved_login_pin")
        val KEY_DAILY_GOAL = stringPreferencesKey("daily_goal")
        val KEY_STUDENT_POINTS = intPreferencesKey("student_points")
        val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        val KEY_APP_THEME_PRESET = stringPreferencesKey("app_theme_preset")
        val KEY_CUSTOM_APP_COLOR_HEX = stringPreferencesKey("custom_app_color_hex")
        val KEY_CUSTOM_BG_COLOR_HEX = stringPreferencesKey("custom_bg_color_hex")
        val KEY_CUSTOM_CARD_COLOR_HEX = stringPreferencesKey("custom_card_color_hex")
        val KEY_CUSTOM_TEXT_COLOR_HEX = stringPreferencesKey("custom_text_color_hex")
        val KEY_FIREBASE_LAST_UPDATE = longPreferencesKey("firebase_last_update_time")
        
        val KEY_CALCULATION_METHOD = stringPreferencesKey("calculation_method")
        val KEY_LATITUDE = doublePreferencesKey("latitude")
        val KEY_LONGITUDE = doublePreferencesKey("longitude")
        val KEY_CITY_LOCATION = stringPreferencesKey("city_location")
        val KEY_COUNTRY_LOCATION = stringPreferencesKey("country_location")
        val KEY_DST_MODE = stringPreferencesKey("dst_mode")
        val KEY_IS_PRAYER_NOTIF = booleanPreferencesKey("is_prayer_notification_enabled")
        val KEY_IS_ADHAN_FULL_AUDIO = booleanPreferencesKey("is_adhan_full_audio")
        val KEY_MUEZZIN_VOICE = stringPreferencesKey("muezzin_voice")
        val KEY_MUTED_PRAYERS = stringSetPreferencesKey("muted_prayers")
        val KEY_IS_MORNING_AZKAR = booleanPreferencesKey("is_morning_azkar_enabled")
        val KEY_MORNING_AZKAR_HOUR = intPreferencesKey("morning_azkar_hour")
        val KEY_MORNING_AZKAR_MINUTE = intPreferencesKey("morning_azkar_minute")
        val KEY_IS_EVENING_AZKAR = booleanPreferencesKey("is_evening_azkar_enabled")
        val KEY_EVENING_AZKAR_HOUR = intPreferencesKey("evening_azkar_hour")
        val KEY_EVENING_AZKAR_MINUTE = intPreferencesKey("evening_azkar_minute")
        val KEY_IS_SALAWAT_ENABLED = booleanPreferencesKey("is_salawat_enabled")
        val KEY_SALAWAT_INTERVAL = intPreferencesKey("salawat_interval_minutes")
        val KEY_SALAWAT_VOICE_ID = intPreferencesKey("salawat_voice_id")
        val KEY_SALAWAT_AUDIO_MODE = stringPreferencesKey("salawat_audio_mode")
        val KEY_CUSTOM_SALAWAT_URI = stringPreferencesKey("custom_salawat_audio_uri")
        val KEY_CUSTOM_SALAWAT_NAME = stringPreferencesKey("custom_salawat_audio_name")
        
        val KEY_IS_LECTURE_NOTIF = booleanPreferencesKey("is_lecture_notification_enabled")
        val KEY_LECTURE_REMINDER_MINS = intPreferencesKey("lecture_reminder_minutes_before")
        
        val KEY_QURAN_PAGES_GOAL = intPreferencesKey("quran_daily_pages_goal")
        val KEY_QURAN_SURAH = intPreferencesKey("quran_bookmark_surah")
        val KEY_QURAN_AYAH = intPreferencesKey("quran_bookmark_ayah")
        val KEY_QURAN_SURAH_NAME = stringPreferencesKey("quran_bookmark_surah_name")
        val KEY_QURAN_FONT_SIZE = intPreferencesKey("quran_font_size_sp")
        val KEY_QURAN_PAGES_READ = intPreferencesKey("quran_pages_read_today")
        
        val KEY_POMODORO_START_SOUND = stringPreferencesKey("pomodoro_start_sound_uri")
        val KEY_POMODORO_BREAK_SOUND = stringPreferencesKey("pomodoro_break_sound_uri")
        val KEY_POMODORO_FINISH_SOUND = stringPreferencesKey("pomodoro_finish_sound_uri")
        val KEY_POMODORO_FINISH_NAME = stringPreferencesKey("pomodoro_finish_sound_name")
        
        val KEY_PLAYER_LEVEL = intPreferencesKey("player_level")
        val KEY_PLAYER_XP = intPreferencesKey("player_xp")
        val KEY_PLAYER_RANK = stringPreferencesKey("player_rank")
        val KEY_STREAK_SHIELDS = intPreferencesKey("streak_shields_count")
        val KEY_CURRENT_GLOBAL_STREAK = intPreferencesKey("current_global_streak")
        val KEY_BEST_GLOBAL_STREAK = intPreferencesKey("best_global_streak")
        val KEY_LAST_ACTIVE_DAY = stringPreferencesKey("last_active_day_string")
        val KEY_IS_SOUND_EFFECTS = booleanPreferencesKey("is_sound_effects_enabled")
        val KEY_EQUIPPED_TITLE = stringPreferencesKey("equipped_title")
        val KEY_EQUIPPED_FRAME = stringPreferencesKey("equipped_frame")
        val KEY_EQUIPPED_AVATAR = stringPreferencesKey("equipped_avatar")
        val KEY_DAILY_WAKEUP_HOUR = intPreferencesKey("daily_wakeup_hour")
        val KEY_DAILY_WAKEUP_MIN = intPreferencesKey("daily_wakeup_minute")
        val KEY_IS_PIN_LOCK_ENABLED = booleanPreferencesKey("is_pin_lock_enabled")
        val KEY_IS_APP_LOCKED_ADMIN = booleanPreferencesKey("is_app_locked_admin")
        val KEY_IS_APP_LOCKED_BY_ADMIN = KEY_IS_APP_LOCKED_ADMIN
        val KEY_ADMIN_LOCK_MESSAGE = stringPreferencesKey("admin_lock_message")
        val KEY_IS_ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")
        val KEY_USER_GMAIL = stringPreferencesKey("user_gmail")
        val KEY_FIREBASE_WEB_CLIENT_ID = stringPreferencesKey("firebase_web_client_id")
        val KEY_IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val KEY_DAILY_DEADLINE_HOUR = intPreferencesKey("daily_deadline_hour")
        val KEY_DAILY_DEADLINE_MIN = intPreferencesKey("daily_deadline_minute")
        val KEY_DAILY_WATER_CUPS = intPreferencesKey("daily_water_cups")
        val KEY_FRIDAY_SURAH_KAHF = booleanPreferencesKey("friday_surah_kahf_read")
        val KEY_FRIDAY_SURAH_JUMUAH = booleanPreferencesKey("friday_surah_jumuah_read")
        val KEY_IS_TELEGRAM_LINKED = booleanPreferencesKey("is_telegram_linked")
        val KEY_TELEGRAM_CHAT_ID = longPreferencesKey("telegram_chat_id")
        val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
    }

    val userSettingsFlow: Flow<UserSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            UserSettings(
                userName = prefs[KEY_USER_NAME] ?: "الطالب المثالي",
                whatsappNumber = prefs[KEY_WHATSAPP_NUMBER] ?: "",
                telegramNumber = prefs[KEY_TELEGRAM_NUMBER] ?: "",
                isTelegramLinked = prefs[KEY_IS_TELEGRAM_LINKED] ?: false,
                telegramChatId = prefs[KEY_TELEGRAM_CHAT_ID] ?: 0L,
                teacherWhatsappNumber = prefs[KEY_TEACHER_WHATSAPP] ?: "",
                studentPassword = prefs[KEY_STUDENT_PASSWORD] ?: "",
                pinCode = prefs[KEY_PIN_CODE] ?: "",
                adminPin = prefs[KEY_ADMIN_PIN] ?: "1234",
                isBiometricEnabled = prefs[KEY_IS_BIOMETRIC_ENABLED] ?: false,
                isPinLockEnabled = prefs[KEY_IS_PIN_LOCK_ENABLED] ?: false,
                isAppLockedByAdmin = prefs[KEY_IS_APP_LOCKED_ADMIN] ?: false,
                adminLockMessage = prefs[KEY_ADMIN_LOCK_MESSAGE] ?: "التطبيق مقفل مؤقتاً بواسطة المشرف.",
                isOnboardingCompleted = prefs[KEY_IS_ONBOARDING_COMPLETED] ?: false,
                isSetupCompleted = prefs[KEY_IS_SETUP_COMPLETED] ?: false,
                educationalStage = prefs[KEY_EDUCATIONAL_STAGE] ?: "ثانوي",
                studentGradeYear = prefs[KEY_STUDENT_GRADE_YEAR] ?: "الصف الثالث الثانوي",
                highSchoolBranch = prefs[KEY_HIGH_SCHOOL_BRANCH] ?: "علمي علوم",
                studentAge = prefs[KEY_STUDENT_AGE] ?: 17,
                savedLoginPhone = prefs[KEY_SAVED_LOGIN_PHONE] ?: "",
                savedLoginPin = prefs[KEY_SAVED_LOGIN_PIN] ?: "",
                dailyGoal = prefs[KEY_DAILY_GOAL] ?: "إنهاء 4 مهام دراسية ومراجعة البطاقات",
                studentPoints = prefs[KEY_STUDENT_POINTS] ?: 100,
                appLanguage = prefs[KEY_APP_LANGUAGE] ?: "ar",
                isDarkMode = prefs[KEY_IS_DARK_MODE] ?: false,
                appThemePreset = prefs[KEY_APP_THEME_PRESET] ?: "ROYAL_BLUE",
                customAppColorHex = prefs[KEY_CUSTOM_APP_COLOR_HEX] ?: "#2563EB",
                customBgColorHex = prefs[KEY_CUSTOM_BG_COLOR_HEX] ?: "#F8FAFC",
                customCardColorHex = prefs[KEY_CUSTOM_CARD_COLOR_HEX] ?: "#FFFFFF",
                customTextColorHex = prefs[KEY_CUSTOM_TEXT_COLOR_HEX] ?: "#0F172A",
                firebaseLastUpdateTime = prefs[KEY_FIREBASE_LAST_UPDATE] ?: 0L,
                
                calculationMethod = prefs[KEY_CALCULATION_METHOD] ?: "EGYPTIAN",
                latitude = prefs[KEY_LATITUDE] ?: 30.0444,
                longitude = prefs[KEY_LONGITUDE] ?: 31.2357,
                cityLocation = prefs[KEY_CITY_LOCATION] ?: "القاهرة",
                countryLocation = prefs[KEY_COUNTRY_LOCATION] ?: "مصر",
                dstMode = prefs[KEY_DST_MODE] ?: "AUTO",
                isPrayerNotificationEnabled = prefs[KEY_IS_PRAYER_NOTIF] ?: true,
                isAdhanFullAudio = prefs[KEY_IS_ADHAN_FULL_AUDIO] ?: true,
                muezzinVoice = prefs[KEY_MUEZZIN_VOICE] ?: "أذان مكة المكرمة",
                mutedPrayers = prefs[KEY_MUTED_PRAYERS] ?: emptySet(),
                isMorningAzkarEnabled = prefs[KEY_IS_MORNING_AZKAR] ?: true,
                morningAzkarHour = prefs[KEY_MORNING_AZKAR_HOUR] ?: 6,
                morningAzkarMinute = prefs[KEY_MORNING_AZKAR_MINUTE] ?: 0,
                isEveningAzkarEnabled = prefs[KEY_IS_EVENING_AZKAR] ?: true,
                eveningAzkarHour = prefs[KEY_EVENING_AZKAR_HOUR] ?: 17,
                eveningAzkarMinute = prefs[KEY_EVENING_AZKAR_MINUTE] ?: 30,
                isSalawatEnabled = prefs[KEY_IS_SALAWAT_ENABLED] ?: true,
                salawatIntervalMinutes = prefs[KEY_SALAWAT_INTERVAL] ?: 30,
                salawatVoiceId = prefs[KEY_SALAWAT_VOICE_ID] ?: 1,
                salawatAudioMode = prefs[KEY_SALAWAT_AUDIO_MODE] ?: "VOICE_FORMULA",
                customSalawatAudioUri = prefs[KEY_CUSTOM_SALAWAT_URI] ?: "",
                customSalawatAudioName = prefs[KEY_CUSTOM_SALAWAT_NAME] ?: "",
                
                isLectureNotificationEnabled = prefs[KEY_IS_LECTURE_NOTIF] ?: true,
                lectureReminderMinutesBefore = prefs[KEY_LECTURE_REMINDER_MINS] ?: 15,
                
                quranDailyPagesGoal = prefs[KEY_QURAN_PAGES_GOAL] ?: 4,
                quranBookmarkSurah = prefs[KEY_QURAN_SURAH] ?: 1,
                quranBookmarkAyah = prefs[KEY_QURAN_AYAH] ?: 1,
                quranBookmarkSurahName = prefs[KEY_QURAN_SURAH_NAME] ?: "الفاتحة",
                quranFontSizeSp = prefs[KEY_QURAN_FONT_SIZE] ?: 22,
                quranPagesReadToday = prefs[KEY_QURAN_PAGES_READ] ?: 0,
                
                pomodoroStartSoundUri = prefs[KEY_POMODORO_START_SOUND] ?: "",
                pomodoroBreakSoundUri = prefs[KEY_POMODORO_BREAK_SOUND] ?: "",
                pomodoroFinishSoundUri = prefs[KEY_POMODORO_FINISH_SOUND] ?: "",
                pomodoroFinishSoundName = prefs[KEY_POMODORO_FINISH_NAME] ?: "Default Bell",
                
                playerLevel = prefs[KEY_PLAYER_LEVEL] ?: 1,
                playerXp = prefs[KEY_PLAYER_XP] ?: 0,
                playerRank = prefs[KEY_PLAYER_RANK] ?: "BRONZE",
                streakShieldsCount = prefs[KEY_STREAK_SHIELDS] ?: 2,
                currentGlobalStreak = prefs[KEY_CURRENT_GLOBAL_STREAK] ?: 0,
                bestGlobalStreak = prefs[KEY_BEST_GLOBAL_STREAK] ?: 0,
                lastActiveDayString = prefs[KEY_LAST_ACTIVE_DAY] ?: "",
                isSoundEffectsEnabled = prefs[KEY_IS_SOUND_EFFECTS] ?: true,
                equippedTitle = prefs[KEY_EQUIPPED_TITLE] ?: "طالب مجتهد",
                equippedFrame = prefs[KEY_EQUIPPED_FRAME] ?: "BRONZE_RING",
                equippedAvatar = prefs[KEY_EQUIPPED_AVATAR] ?: "DEFAULT_STUDENT",
                dailyWakeUpHour = prefs[KEY_DAILY_WAKEUP_HOUR] ?: 5,
                dailyWakeUpMinute = prefs[KEY_DAILY_WAKEUP_MIN] ?: 30,
                dailyDeadlineHour = prefs[KEY_DAILY_DEADLINE_HOUR] ?: 23,
                dailyDeadlineMinute = prefs[KEY_DAILY_DEADLINE_MIN] ?: 59,
                dailyWaterCups = prefs[KEY_DAILY_WATER_CUPS] ?: 0,
                fridaySurahKahfRead = prefs[KEY_FRIDAY_SURAH_KAHF] ?: false,
                fridaySurahJumuahRead = prefs[KEY_FRIDAY_SURAH_JUMUAH] ?: false,
                geminiApiKey = prefs[KEY_GEMINI_API_KEY] ?: ""
            )
        }

    suspend fun updateGeminiApiKey(apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_GEMINI_API_KEY] = apiKey.trim()
        }
    }

    suspend fun updateGamification(
        level: Int? = null,
        xp: Int? = null,
        rank: String? = null,
        shields: Int? = null,
        currentStreak: Int? = null,
        bestStreak: Int? = null,
        lastActiveDay: String? = null
    ) {
        context.dataStore.edit { prefs ->
            level?.let { prefs[KEY_PLAYER_LEVEL] = it }
            xp?.let { prefs[KEY_PLAYER_XP] = it }
            rank?.let { prefs[KEY_PLAYER_RANK] = it }
            shields?.let { prefs[KEY_STREAK_SHIELDS] = it }
            currentStreak?.let { prefs[KEY_CURRENT_GLOBAL_STREAK] = it }
            bestStreak?.let { prefs[KEY_BEST_GLOBAL_STREAK] = it }
            lastActiveDay?.let { prefs[KEY_LAST_ACTIVE_DAY] = it }
        }
    }

    suspend fun updateProfile(
        name: String? = null,
        whatsapp: String? = null,
        telegram: String? = null,
        stage: String? = null,
        grade: String? = null,
        branch: String? = null,
        goal: String? = null,
        points: Int? = null,
        language: String? = null
    ) {
        context.dataStore.edit { prefs ->
            name?.let { prefs[KEY_USER_NAME] = it }
            whatsapp?.let { prefs[KEY_WHATSAPP_NUMBER] = it }
            telegram?.let { prefs[KEY_TELEGRAM_NUMBER] = it }
            stage?.let { prefs[KEY_EDUCATIONAL_STAGE] = it }
            grade?.let { prefs[KEY_STUDENT_GRADE_YEAR] = it }
            branch?.let { prefs[KEY_HIGH_SCHOOL_BRANCH] = it }
            goal?.let { prefs[KEY_DAILY_GOAL] = it }
            points?.let { prefs[KEY_STUDENT_POINTS] = it }
            language?.let { prefs[KEY_APP_LANGUAGE] = it }
        }
    }

    suspend fun updateStudentProfile(
        name: String? = null,
        stage: String? = null,
        gradeYear: String? = null,
        branch: String? = null,
        age: Int? = null
    ) {
        context.dataStore.edit { prefs ->
            name?.let { prefs[KEY_USER_NAME] = it }
            stage?.let { prefs[KEY_EDUCATIONAL_STAGE] = it }
            gradeYear?.let { prefs[KEY_STUDENT_GRADE_YEAR] = it }
            branch?.let { prefs[KEY_HIGH_SCHOOL_BRANCH] = it }
            age?.let { prefs[KEY_STUDENT_AGE] = it }
        }
    }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { it[KEY_USER_NAME] = name }
    }

    suspend fun setAppLanguage(lang: String) {
        context.dataStore.edit { it[KEY_APP_LANGUAGE] = lang }
    }

    suspend fun completeStudentLogin(phone: String, pin: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SAVED_LOGIN_PHONE] = phone
            prefs[KEY_SAVED_LOGIN_PIN] = pin
            prefs[KEY_IS_ONBOARDING_COMPLETED] = true
            prefs[KEY_IS_SETUP_COMPLETED] = true
        }
    }

    suspend fun setSoundEffectsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_IS_SOUND_EFFECTS] = enabled }
    }

    suspend fun updateSoundEffectsEnabled(enabled: Boolean) {
        setSoundEffectsEnabled(enabled)
    }

    suspend fun equipCollectionItem(itemId: String, itemType: String) {
        context.dataStore.edit { prefs ->
            when (itemType.uppercase()) {
                "TITLE" -> prefs[KEY_EQUIPPED_TITLE] = itemId
                "FRAME" -> prefs[KEY_EQUIPPED_FRAME] = itemId
                "AVATAR" -> prefs[KEY_EQUIPPED_AVATAR] = itemId
            }
        }
    }

    suspend fun updateEquippedCustomization(title: String? = null, frame: String? = null, avatar: String? = null) {
        context.dataStore.edit { prefs ->
            title?.let { prefs[KEY_EQUIPPED_TITLE] = it }
            frame?.let { prefs[KEY_EQUIPPED_FRAME] = it }
            avatar?.let { prefs[KEY_EQUIPPED_AVATAR] = it }
        }
    }

    suspend fun updateScheduleTimes(wakeUpHour: Int, wakeUpMinute: Int, deadlineHour: Int, deadlineMinute: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DAILY_WAKEUP_HOUR] = wakeUpHour
            prefs[KEY_DAILY_WAKEUP_MIN] = wakeUpMinute
            prefs[KEY_DAILY_DEADLINE_HOUR] = deadlineHour
            prefs[KEY_DAILY_DEADLINE_MIN] = deadlineMinute
        }
    }

    suspend fun updatePrayerLocation(city: String, country: String, lat: Double, lng: Double, method: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CITY_LOCATION] = city
            prefs[KEY_COUNTRY_LOCATION] = country
            prefs[KEY_LATITUDE] = lat
            prefs[KEY_LONGITUDE] = lng
            prefs[KEY_CALCULATION_METHOD] = method
        }
    }

    suspend fun updatePrayerNotificationSettings(enabled: Boolean, fullAudio: Boolean, muezzin: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_PRAYER_NOTIF] = enabled
            prefs[KEY_IS_ADHAN_FULL_AUDIO] = fullAudio
            prefs[KEY_MUEZZIN_VOICE] = muezzin
        }
    }

    suspend fun updatePrayerNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_IS_PRAYER_NOTIF] = enabled }
    }

    suspend fun updateAdhanFullAudio(full: Boolean) {
        context.dataStore.edit { it[KEY_IS_ADHAN_FULL_AUDIO] = full }
    }

    suspend fun updateMuezzinVoice(voice: String) {
        context.dataStore.edit { it[KEY_MUEZZIN_VOICE] = voice }
    }

    suspend fun toggleMutedPrayer(prayer: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_MUTED_PRAYERS]?.toMutableSet() ?: mutableSetOf()
            if (current.contains(prayer)) {
                current.remove(prayer)
            } else {
                current.add(prayer)
            }
            prefs[KEY_MUTED_PRAYERS] = current
        }
    }

    suspend fun setAzkarSettings(
        morningEnabled: Boolean,
        eveningEnabled: Boolean,
        morningHour: Int = 6,
        morningMinute: Int = 0,
        eveningHour: Int = 17,
        eveningMinute: Int = 30
    ) {
        updateAzkarSettings(morningEnabled, morningHour, morningMinute, eveningEnabled, eveningHour, eveningMinute)
    }

    suspend fun updateAzkarSettings(
        morningEnabled: Boolean, morningHour: Int, morningMinute: Int,
        eveningEnabled: Boolean, eveningHour: Int, eveningMinute: Int
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_MORNING_AZKAR] = morningEnabled
            prefs[KEY_MORNING_AZKAR_HOUR] = morningHour
            prefs[KEY_MORNING_AZKAR_MINUTE] = morningMinute
            prefs[KEY_IS_EVENING_AZKAR] = eveningEnabled
            prefs[KEY_EVENING_AZKAR_HOUR] = eveningHour
            prefs[KEY_EVENING_AZKAR_MINUTE] = eveningMinute
        }
    }

    suspend fun updateMorningAzkarTime(hour: Int, minute: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MORNING_AZKAR_HOUR] = hour
            prefs[KEY_MORNING_AZKAR_MINUTE] = minute
        }
    }

    suspend fun updateEveningAzkarTime(hour: Int, minute: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_EVENING_AZKAR_HOUR] = hour
            prefs[KEY_EVENING_AZKAR_MINUTE] = minute
        }
    }

    suspend fun updateSalawatSettings(
        enabled: Boolean, interval: Int, voiceId: Int,
        audioMode: String, uri: String = "", name: String = ""
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_SALAWAT_ENABLED] = enabled
            prefs[KEY_SALAWAT_INTERVAL] = interval
            prefs[KEY_SALAWAT_VOICE_ID] = voiceId
            prefs[KEY_SALAWAT_AUDIO_MODE] = audioMode
            prefs[KEY_CUSTOM_SALAWAT_URI] = uri
            prefs[KEY_CUSTOM_SALAWAT_NAME] = name
        }
    }

    suspend fun updateSalawatVoiceId(voiceId: Int) {
        context.dataStore.edit { it[KEY_SALAWAT_VOICE_ID] = voiceId }
    }

    suspend fun updateSalawatAudioMode(mode: String) {
        context.dataStore.edit { it[KEY_SALAWAT_AUDIO_MODE] = mode }
    }

    suspend fun updateCustomSalawatAudio(uri: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_SALAWAT_URI] = uri
            prefs[KEY_CUSTOM_SALAWAT_NAME] = name
        }
    }

    suspend fun setLectureNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_IS_LECTURE_NOTIF] = enabled }
    }

    suspend fun setLectureReminderMinutes(minutes: Int) {
        context.dataStore.edit { it[KEY_LECTURE_REMINDER_MINS] = minutes }
    }

    suspend fun updateDarkMode(isDark: Boolean?) {
        context.dataStore.edit { prefs ->
            if (isDark != null) {
                prefs[KEY_IS_DARK_MODE] = isDark
            } else {
                prefs.remove(KEY_IS_DARK_MODE)
            }
        }
    }

    suspend fun updateThemePreset(preset: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_APP_THEME_PRESET] = preset
        }
    }

    suspend fun updateCustomColors(appColor: String, bgColor: String, cardColor: String, textColor: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_APP_COLOR_HEX] = appColor
            prefs[KEY_CUSTOM_BG_COLOR_HEX] = bgColor
            prefs[KEY_CUSTOM_CARD_COLOR_HEX] = cardColor
            prefs[KEY_CUSTOM_TEXT_COLOR_HEX] = textColor
        }
    }

    suspend fun updateTheme(preset: String, appColor: String, bgColor: String, cardColor: String, textColor: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_APP_THEME_PRESET] = preset
            prefs[KEY_CUSTOM_APP_COLOR_HEX] = appColor
            prefs[KEY_CUSTOM_BG_COLOR_HEX] = bgColor
            prefs[KEY_CUSTOM_CARD_COLOR_HEX] = cardColor
            prefs[KEY_CUSTOM_TEXT_COLOR_HEX] = textColor
        }
    }

    suspend fun saveQuranBookmark(surah: Int, ayah: Int, surahName: String) {
        updateQuranBookmark(surah, ayah, surahName)
    }

    suspend fun updateQuranBookmark(surah: Int, ayah: Int, surahName: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_QURAN_SURAH] = surah
            prefs[KEY_QURAN_AYAH] = ayah
            prefs[KEY_QURAN_SURAH_NAME] = surahName
        }
    }

    suspend fun setQuranFontSize(sizeSp: Int) {
        context.dataStore.edit { it[KEY_QURAN_FONT_SIZE] = sizeSp }
    }

    suspend fun updateQuranPagesRead(count: Int) {
        context.dataStore.edit { it[KEY_QURAN_PAGES_READ] = count }
    }

    suspend fun resetQuranPagesRead() {
        context.dataStore.edit { it[KEY_QURAN_PAGES_READ] = 0 }
    }

    suspend fun updateSetupCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_IS_SETUP_COMPLETED] = completed }
    }

    suspend fun updateSecurity(pin: String, biometric: Boolean, adminPin: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PIN_CODE] = pin
            prefs[KEY_IS_BIOMETRIC_ENABLED] = biometric
            adminPin?.let { prefs[KEY_ADMIN_PIN] = it }
        }
    }

    suspend fun updateFirebaseLastSync(time: Long) {
        context.dataStore.edit { it[KEY_FIREBASE_LAST_UPDATE] = time }
    }

    suspend fun completeStudentRegistration(
        name: String,
        phone: String,
        pin: String,
        stage: String,
        branch: String,
        age: Int,
        gradeYear: String,
        gmail: String = ""
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_NAME] = name
            prefs[KEY_SAVED_LOGIN_PHONE] = phone
            prefs[KEY_SAVED_LOGIN_PIN] = pin
            prefs[KEY_EDUCATIONAL_STAGE] = stage
            prefs[KEY_HIGH_SCHOOL_BRANCH] = branch
            prefs[KEY_STUDENT_AGE] = age
            prefs[KEY_STUDENT_GRADE_YEAR] = gradeYear
            prefs[KEY_USER_GMAIL] = gmail
            prefs[KEY_IS_ONBOARDING_COMPLETED] = true
            prefs[KEY_IS_SETUP_COMPLETED] = true
        }
    }

    suspend fun completeGuestLogin() {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_NAME] = "طالب زائر"
            prefs[KEY_IS_ONBOARDING_COMPLETED] = true
            prefs[KEY_IS_SETUP_COMPLETED] = true
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean = true) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_ONBOARDING_COMPLETED] = completed
            prefs[KEY_IS_SETUP_COMPLETED] = completed
        }
    }

    suspend fun setAppLockedByAdmin(locked: Boolean, message: String = "التطبيق مقفل مؤقتاً بواسطة المشرف.") {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_APP_LOCKED_BY_ADMIN] = locked
            prefs[KEY_ADMIN_LOCK_MESSAGE] = message
        }
    }

    suspend fun updateLocationAndCountry(city: String, country: String, lat: Double, lng: Double) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CITY_LOCATION] = city
            prefs[KEY_COUNTRY_LOCATION] = country
            prefs[KEY_LATITUDE] = lat
            prefs[KEY_LONGITUDE] = lng
        }
    }

    suspend fun updatePrayerCalculationMethod(method: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CALCULATION_METHOD] = method
        }
    }

    suspend fun updateFirebaseWebClientId(clientId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FIREBASE_WEB_CLIENT_ID] = clientId
        }
    }

    suspend fun updateWaterCups(cups: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DAILY_WATER_CUPS] = cups
        }
    }

    suspend fun updateFridaySurahs(kahf: Boolean, jumuah: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FRIDAY_SURAH_KAHF] = kahf
            prefs[KEY_FRIDAY_SURAH_JUMUAH] = jumuah
        }
    }

    suspend fun updatePomodoroFinishSound(uri: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_POMODORO_FINISH_SOUND] = uri
            prefs[KEY_POMODORO_FINISH_NAME] = name
        }
    }
}
