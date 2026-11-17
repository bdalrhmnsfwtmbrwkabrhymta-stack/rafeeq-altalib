package com.hawas.study.rafid.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Locale

enum class RafidPlatform(val labelAr: String, val labelEn: String) {
    YOUTUBE("يوتيوب", "YouTube"),
    PINTEREST("بينتيريست", "Pinterest"),
    TIKTOK("تيك توك", "TikTok"),
    INSTAGRAM("إنستغرام", "Instagram"),
    FACEBOOK("فيسبوك", "Facebook"),
    X_TWITTER("X", "X"),
    STUDY_PLATFORM("منصات الدراسة", "Study Platforms"),
    DIRECT("رابط مباشر", "Direct Link")
}

enum class RafidDownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    MUXING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class RafidMediaCategory(val folderName: String, val labelAr: String, val labelEn: String) {
    VIDEO("Video", "فيديو", "Video"),
    AUDIO("Audio", "صوتيات", "Audio"),
    IMAGE("Images", "صور", "Images"),
    DOCUMENT("Documents", "مستندات", "Documents"),
    PLAYLIST("Playlists", "قوائم تشغيل", "Playlists")
}

enum class RafidLanguageMode(val code: String, val labelAr: String, val labelEn: String) {
    SYSTEM("SYSTEM", "حسب النظام", "System Default"),
    ARABIC("AR", "العربية", "العربية"),
    ENGLISH("EN", "English", "English")
}

enum class RafidScreenTab {
    HOME,
    BROWSER,
    QUEUES,
    PROTECTION,
    PLAYLISTS
}

enum class RafidActivityFilter(val labelAr: String, val labelEn: String) {
    ALL("الكل", "All"),
    ACTIVE("نشطة", "Active"),
    COMPLETED("مكتملة", "Completed"),
    QUEUED("في الانتظار", "Queued")
}

data class MediaFormatOption(
    val id: String,
    val label: String,
    val containerExt: String,
    val mimeType: String,
    val resolutionHeight: Int? = null,
    val bitrateKbps: Int? = null,
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val estimatedSizeBytes: Long = 0L,
    val isAudioOnly: Boolean = false,
    val isMuxRequired: Boolean = false,
    val isPlaylistBundle: Boolean = false,
    val badgeTagAr: String? = null,
    val badgeTagEn: String? = null,
    val languageLabel: String = "الأصلي: العربية"
)

data class PlaylistEntryItem(
    val index: Int,
    val videoId: String,
    val title: String,
    val videoUrl: String,
    val durationSeconds: Int = 0,
    val thumbnailUrl: String? = null
)

data class ExtractedMediaMetadata(
    val originalUrl: String,
    val resolvedUrl: String,
    val title: String,
    val authorOrSource: String,
    val sourceDomain: String,
    val platform: RafidPlatform,
    val thumbnailUrl: String? = null,
    val durationSeconds: Int = 0,
    val formats: List<MediaFormatOption> = emptyList(),
    val playlistItems: List<PlaylistEntryItem> = emptyList(),
    val isPlaylist: Boolean = false,
    val cookiesHeader: String? = null,
    val userAgent: String? = null
)

@Entity(tableName = "rafid_downloads")
data class RafidDownloadEntity(
    @PrimaryKey val id: String,
    val originalUrl: String,
    val resolvedUrl: String,
    val videoStreamUrl: String,
    val audioStreamUrl: String? = null,
    val title: String,
    val sourceDomain: String,
    val platformName: String,
    val thumbnailUrl: String? = null,
    val qualityLabel: String,
    val containerExt: String,
    val mimeType: String,
    val categoryName: String,
    val isAudioOnly: Boolean = false,
    val isMuxRequired: Boolean = false,
    val saveToStudyFolder: Boolean = false,
    val isPlaylistItem: Boolean = false,
    val playlistTitle: String? = null,
    val cookiesHeader: String? = null,
    val userAgent: String? = null,
    val status: String = RafidDownloadStatus.QUEUED.name,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val mediaStoreUri: String? = null,
    val displayPath: String = "Download/Rafid",
    val errorMessage: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val completedAtMillis: Long = 0L
)

@Entity(tableName = "rafid_bookmarks")
data class RafidBookmarkEntity(
    @PrimaryKey val id: String,
    val titleAr: String,
    val titleEn: String,
    val url: String,
    val platformKey: String,
    val sortOrder: Int = 0,
    val isDefault: Boolean = false
)

@Entity(tableName = "rafid_excluded_domains")
data class RafidExcludedDomainEntity(
    @PrimaryKey val domain: String,
    val addedAtMillis: Long = System.currentTimeMillis()
)

data class RafidConfigState(
    val darkModeOverride: Boolean? = null,
    val languageMode: RafidLanguageMode = RafidLanguageMode.ARABIC,
    val maxConcurrentDownloads: Int = 3,
    val wifiOnly: Boolean = false,
    val isScheduledStartEnabled: Boolean = false,
    val scheduledHour: Int = 23,
    val scheduledMinute: Int = 0,
    val isQueuePaused: Boolean = false,
    val blockAdsAndTrackers: Boolean = true,
    val blockMalwareSites: Boolean = true,
    val smartEngineEnabled: Boolean = true,
    val blockedAdsCount: Int = 0,
    val blockedTrackersCount: Int = 0,
    val blockedMalwareCount: Int = 0,
    val lastRulesUpdateMillis: Long = 0L,
    val lastSeenClipboardUrl: String = ""
) {
    val totalBlockedRequests: Int
        get() = blockedAdsCount + blockedTrackersCount + blockedMalwareCount
}

object RafidFormatUtils {
    fun formatFileSize(bytes: Long, isEnglish: Boolean): String {
        if (bytes <= 0L) return if (isEnglish) "Auto size" else "حجم تلقائي"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f %s", gb, if (isEnglish) "GB" else "ج.ب")
            mb >= 1.0 -> String.format(Locale.US, "%.1f %s", mb, if (isEnglish) "MB" else "م.ب")
            kb >= 1.0 -> String.format(Locale.US, "%.0f %s", kb, if (isEnglish) "KB" else "ك.ب")
            else -> "$bytes ${if (isEnglish) "B" else "بايت"}"
        }
    }

    fun formatSpeed(bytesPerSec: Long, isEnglish: Boolean): String {
        if (bytesPerSec <= 0L) return if (isEnglish) "0 KB/s" else "0 ك.ب/ث"
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f %s", mb, if (isEnglish) "MB/s" else "م.ب/ث")
        } else {
            String.format(Locale.US, "%.0f %s", kb, if (isEnglish) "KB/s" else "ك.ب/ث")
        }
    }

    fun formatDuration(seconds: Int): String {
        if (seconds <= 0) return ""
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hrs > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }

    fun formatEta(etaSeconds: Long, isEnglish: Boolean): String {
        if (etaSeconds <= 0L) return if (isEnglish) "Calculating..." else "جاري الحساب"
        val mins = etaSeconds / 60
        val secs = etaSeconds % 60
        return when {
            mins >= 60 -> {
                val hrs = mins / 60
                val remMin = mins % 60
                if (isEnglish) "${hrs}h ${remMin}m" else "باقي ${hrs}س ${remMin}د"
            }
            mins > 0 -> if (isEnglish) "${mins}m ${secs}s" else "باقي ${mins}د ${secs}ث"
            else -> if (isEnglish) "${secs}s" else "باقي ${secs}ث"
        }
    }
}
