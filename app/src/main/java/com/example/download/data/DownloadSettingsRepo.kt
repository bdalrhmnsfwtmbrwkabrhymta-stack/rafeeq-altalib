package com.example.download.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

private val Context.smartDownloadDataStore by preferencesDataStore(name = "smart_download_settings")

class DownloadSettingsRepo(private val context: Context) {

    private object Keys {
        val CHUNK_COUNT = intPreferencesKey("chunk_count")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val MAX_CONCURRENT = intPreferencesKey("max_concurrent")
        val SPEED_LIMIT_KBPS = intPreferencesKey("speed_limit_kbps")
        val LAST_QUALITY_ID = stringPreferencesKey("last_quality_id")
        val LAST_YTDLP_UPDATE_MS = longPreferencesKey("last_ytdlp_update_ms")
    }

    val settingsFlow: Flow<DownloadSettings> = context.smartDownloadDataStore.data.map { prefs ->
        DownloadSettings(
            chunkCount = (prefs[Keys.CHUNK_COUNT] ?: 12).coerceIn(8, 16),
            wifiOnly = prefs[Keys.WIFI_ONLY] ?: false,
            maxConcurrent = (prefs[Keys.MAX_CONCURRENT] ?: 3).coerceIn(1, 5),
            speedLimitKBps = (prefs[Keys.SPEED_LIMIT_KBPS] ?: 0).coerceAtLeast(0),
            lastSelectedQualityId = prefs[Keys.LAST_QUALITY_ID] ?: "1080p"
        )
    }

    suspend fun getSettings(): DownloadSettings = settingsFlow.first()

    suspend fun setChunkCount(count: Int) {
        context.smartDownloadDataStore.edit { it[Keys.CHUNK_COUNT] = count.coerceIn(8, 16) }
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.smartDownloadDataStore.edit { it[Keys.WIFI_ONLY] = enabled }
    }

    suspend fun setMaxConcurrent(count: Int) {
        context.smartDownloadDataStore.edit { it[Keys.MAX_CONCURRENT] = count.coerceIn(1, 5) }
    }

    suspend fun setSpeedLimitKBps(kbps: Int) {
        context.smartDownloadDataStore.edit { it[Keys.SPEED_LIMIT_KBPS] = kbps.coerceAtLeast(0) }
    }

    suspend fun setLastSelectedQualityId(qualityId: String) {
        context.smartDownloadDataStore.edit { it[Keys.LAST_QUALITY_ID] = qualityId }
    }

    suspend fun getLastYtDlpUpdateMs(): Long =
        context.smartDownloadDataStore.data.first()[Keys.LAST_YTDLP_UPDATE_MS] ?: 0L

    suspend fun setLastYtDlpUpdateMs(timestampMs: Long) {
        context.smartDownloadDataStore.edit { it[Keys.LAST_YTDLP_UPDATE_MS] = timestampMs }
    }

    fun getCookiesFile(): File {
        val dir = File(context.filesDir, "smart_download_cookies")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "cookies.txt")
    }

    fun hasSavedCookies(): Boolean {
        val file = getCookiesFile()
        return file.exists() && file.length() > 20L
    }

    fun clearCookies() {
        val file = getCookiesFile()
        if (file.exists()) file.delete()
    }
}
