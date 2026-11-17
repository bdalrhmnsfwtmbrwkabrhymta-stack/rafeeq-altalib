package com.hawas.study.rafid.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hawas.study.rafid.models.RafidConfigState
import com.hawas.study.rafid.models.RafidLanguageMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.rafidDataStore: DataStore<Preferences> by preferencesDataStore(name = "rafid_settings")

class RafidPreferencesRepository(private val context: Context) {

    companion object {
        private val KEY_DARK_MODE = booleanPreferencesKey("rafid_dark_mode")
        private val KEY_HAS_DARK_OVERRIDE = booleanPreferencesKey("rafid_has_dark_override")
        private val KEY_LANGUAGE_MODE = stringPreferencesKey("rafid_language_mode")
        private val KEY_MAX_CONCURRENT = intPreferencesKey("rafid_max_concurrent")
        private val KEY_WIFI_ONLY = booleanPreferencesKey("rafid_wifi_only")
        private val KEY_SCHEDULE_ENABLED = booleanPreferencesKey("rafid_schedule_enabled")
        private val KEY_SCHEDULE_HOUR = intPreferencesKey("rafid_schedule_hour")
        private val KEY_SCHEDULE_MINUTE = intPreferencesKey("rafid_schedule_minute")
        private val KEY_QUEUE_PAUSED = booleanPreferencesKey("rafid_queue_paused")
        private val KEY_BLOCK_ADS_TRACKERS = booleanPreferencesKey("rafid_block_ads_trackers")
        private val KEY_BLOCK_MALWARE = booleanPreferencesKey("rafid_block_malware")
        private val KEY_SMART_ENGINE_ENABLED = booleanPreferencesKey("rafid_smart_engine_enabled")
        private val KEY_BLOCKED_ADS_COUNT = intPreferencesKey("rafid_blocked_ads_count")
        private val KEY_BLOCKED_TRACKERS_COUNT = intPreferencesKey("rafid_blocked_trackers_count")
        private val KEY_BLOCKED_MALWARE_COUNT = intPreferencesKey("rafid_blocked_malware_count")
        private val KEY_LAST_RULES_UPDATE = longPreferencesKey("rafid_last_rules_update")
        private val KEY_LAST_CLIPBOARD_URL = stringPreferencesKey("rafid_last_clipboard_url")
    }

    val configFlow: Flow<RafidConfigState> = context.rafidDataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            val hasDarkOverride = prefs[KEY_HAS_DARK_OVERRIDE] ?: false
            val darkOverride = if (hasDarkOverride) prefs[KEY_DARK_MODE] else null
            val langRaw = prefs[KEY_LANGUAGE_MODE] ?: RafidLanguageMode.ARABIC.code
            val langMode = RafidLanguageMode.entries.firstOrNull { it.code == langRaw } ?: RafidLanguageMode.ARABIC

            RafidConfigState(
                darkModeOverride = darkOverride,
                languageMode = langMode,
                maxConcurrentDownloads = (prefs[KEY_MAX_CONCURRENT] ?: 3).coerceIn(1, 7),
                wifiOnly = prefs[KEY_WIFI_ONLY] ?: false,
                isScheduledStartEnabled = prefs[KEY_SCHEDULE_ENABLED] ?: false,
                scheduledHour = prefs[KEY_SCHEDULE_HOUR] ?: 23,
                scheduledMinute = prefs[KEY_SCHEDULE_MINUTE] ?: 0,
                isQueuePaused = prefs[KEY_QUEUE_PAUSED] ?: false,
                blockAdsAndTrackers = prefs[KEY_BLOCK_ADS_TRACKERS] ?: true,
                blockMalwareSites = prefs[KEY_BLOCK_MALWARE] ?: true,
                smartEngineEnabled = prefs[KEY_SMART_ENGINE_ENABLED] ?: true,
                blockedAdsCount = prefs[KEY_BLOCKED_ADS_COUNT] ?: 0,
                blockedTrackersCount = prefs[KEY_BLOCKED_TRACKERS_COUNT] ?: 0,
                blockedMalwareCount = prefs[KEY_BLOCKED_MALWARE_COUNT] ?: 0,
                lastRulesUpdateMillis = prefs[KEY_LAST_RULES_UPDATE] ?: 0L,
                lastSeenClipboardUrl = prefs[KEY_LAST_CLIPBOARD_URL] ?: ""
            )
        }

    suspend fun getSnapshot(): RafidConfigState = configFlow.first()

    suspend fun setDarkMode(isDark: Boolean) {
        context.rafidDataStore.edit { prefs ->
            prefs[KEY_HAS_DARK_OVERRIDE] = true
            prefs[KEY_DARK_MODE] = isDark
        }
    }

    suspend fun setLanguageMode(mode: RafidLanguageMode) {
        context.rafidDataStore.edit { it[KEY_LANGUAGE_MODE] = mode.code }
    }

    suspend fun setMaxConcurrentDownloads(count: Int) {
        context.rafidDataStore.edit { it[KEY_MAX_CONCURRENT] = count.coerceIn(1, 7) }
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.rafidDataStore.edit { it[KEY_WIFI_ONLY] = enabled }
    }

    suspend fun setScheduledStart(enabled: Boolean, hour: Int? = null, minute: Int? = null) {
        context.rafidDataStore.edit { prefs ->
            prefs[KEY_SCHEDULE_ENABLED] = enabled
            hour?.let { prefs[KEY_SCHEDULE_HOUR] = it.coerceIn(0, 23) }
            minute?.let { prefs[KEY_SCHEDULE_MINUTE] = it.coerceIn(0, 59) }
        }
    }

    suspend fun setQueuePaused(paused: Boolean) {
        context.rafidDataStore.edit { it[KEY_QUEUE_PAUSED] = paused }
    }

    suspend fun setBlockAdsAndTrackers(enabled: Boolean) {
        context.rafidDataStore.edit { it[KEY_BLOCK_ADS_TRACKERS] = enabled }
    }

    suspend fun setBlockMalwareSites(enabled: Boolean) {
        context.rafidDataStore.edit { it[KEY_BLOCK_MALWARE] = enabled }
    }

    suspend fun setSmartEngineEnabled(enabled: Boolean) {
        context.rafidDataStore.edit { it[KEY_SMART_ENGINE_ENABLED] = enabled }
    }

    suspend fun recordBlockedRequest(category: BlockCategory) {
        context.rafidDataStore.edit { prefs ->
            when (category) {
                BlockCategory.AD -> prefs[KEY_BLOCKED_ADS_COUNT] = (prefs[KEY_BLOCKED_ADS_COUNT] ?: 0) + 1
                BlockCategory.TRACKER -> prefs[KEY_BLOCKED_TRACKERS_COUNT] = (prefs[KEY_BLOCKED_TRACKERS_COUNT] ?: 0) + 1
                BlockCategory.MALWARE -> prefs[KEY_BLOCKED_MALWARE_COUNT] = (prefs[KEY_BLOCKED_MALWARE_COUNT] ?: 0) + 1
            }
        }
    }

    suspend fun touchRulesUpdated(timestampMillis: Long = System.currentTimeMillis()) {
        context.rafidDataStore.edit { it[KEY_LAST_RULES_UPDATE] = timestampMillis }
    }

    suspend fun setLastSeenClipboardUrl(url: String) {
        context.rafidDataStore.edit { it[KEY_LAST_CLIPBOARD_URL] = url }
    }
}

enum class BlockCategory {
    AD, TRACKER, MALWARE
}
