package com.example.download.core.model

import java.io.File

enum class MediaPlatform(val labelAr: String, val labelEn: String) {
    YOUTUBE("يوتيوب", "YouTube"),
    FACEBOOK("فيسبوك", "Facebook"),
    INSTAGRAM("إنستغرام", "Instagram"),
    TIKTOK("تيك توك", "TikTok"),
    X_TWITTER("X (تويتر)", "X (Twitter)"),
    PINTEREST("بينتيريست", "Pinterest"),
    STUDY_PLATFORM("منصات الدراسة", "Study Platforms"),
    DIRECT("رابط مباشر", "Direct Link"),
    UNKNOWN("غير معروف", "Unknown")
}

enum class ResolvedMediaKind {
    SINGLE_VIDEO,
    AUDIO_ONLY,
    HLS_STREAM,
    DASH_STREAM,
    PLAYLIST,
    CHANNEL,
    DIRECT_FILE,
    IMAGE,
    DOCUMENT
}

data class ResolvedMediaFormat(
    val id: String,
    val labelAr: String,
    val labelEn: String,
    val subLabelAr: String,
    val containerExt: String,
    val mimeType: String,
    val resolutionHeight: Int = 0,
    val bitrateKbps: Int = 0,
    val streamUrl: String,
    val audioStreamUrl: String? = null,
    val formatExpression: String? = null,
    val exactBytes: Long = -1L,
    val isApproximateBytes: Boolean = false,
    val isAudioOnly: Boolean = false,
    val isHls: Boolean = false,
    val isDash: Boolean = false,
    val needsMux: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val badgeAr: String? = null,
    val badgeEn: String? = null,
    val width: Int = 0,
    val fps: Int = 0,
    val vcodec: String = "",
    val acodec: String = "",
    val protocol: String = "",
    val languageCode: String = "",
    val languageLabel: String = "",
    val isAutoDubbed: Boolean = false,
    val isOriginalAudio: Boolean = false,
    val hasVideo: Boolean = !isAudioOnly,
    val hasAudio: Boolean = true
) {
    val isHlsOrDash: Boolean get() = isHls || isDash
}

data class ResolvedCollectionEntry(
    val index: Int,
    val itemId: String,
    val title: String,
    val url: String,
    val durationSeconds: Int = 0,
    val thumbnailUrl: String? = null,
    val selected: Boolean = true
)

data class ResolveRequest(
    val rawUrl: String,
    val checkCollection: Boolean = true,
    val cookiesHeader: String? = null,
    val cookiesFile: File? = null,
    val userAgent: String? = null,
    val allowWebViewSniffer: Boolean = true
)

sealed class ResolvedMediaResult {
    data class SingleMedia(
        val originalUrl: String,
        val canonicalUrl: String,
        val title: String,
        val authorOrSource: String,
        val sourceDomain: String,
        val platform: MediaPlatform,
        val kind: ResolvedMediaKind,
        val thumbnailUrl: String? = null,
        val durationSeconds: Int = 0,
        val formats: List<ResolvedMediaFormat>,
        val headers: Map<String, String> = emptyMap(),
        val cookiesHeader: String? = null
    ) : ResolvedMediaResult()

    data class MediaCollection(
        val originalUrl: String,
        val canonicalUrl: String,
        val title: String,
        val authorOrSource: String,
        val platform: MediaPlatform,
        val kind: ResolvedMediaKind,
        val entries: List<ResolvedCollectionEntry>,
        val headers: Map<String, String> = emptyMap(),
        val cookiesHeader: String? = null
    ) : ResolvedMediaResult()

    data class Failure(
        val error: DownloadError,
        val diagnosticLog: String
    ) : ResolvedMediaResult()
}

data class HttpProbeInfo(
    val requestedUrl: String,
    val finalUrl: String,
    val statusCode: Int,
    val contentLength: Long,
    val contentType: String,
    val acceptRanges: String?,
    val supportsRange: Boolean,
    val etag: String?,
    val lastModified: String?,
    val isHls: Boolean,
    val isDash: Boolean,
    val isHtmlPage: Boolean
)
