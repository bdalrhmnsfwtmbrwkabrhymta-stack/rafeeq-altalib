package com.hawas.study.rafid.engine

import com.hawas.study.rafid.data.BlockCategory
import com.hawas.study.rafid.models.RafidConfigState
import com.hawas.study.rafid.models.RafidMediaCategory
import com.hawas.study.rafid.models.RafidPlatform
import java.net.URI
import java.util.Locale

data class SniffedMediaCandidate(
    val url: String,
    val title: String,
    val mimeType: String,
    val extension: String,
    val qualityLabel: String,
    val cookiesHeader: String? = null
)

object AdAndTrackerShield {

    private val AD_HOST_RULES = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "tpc.googlesyndication.com",
        "ads.youtube.com",
        "adserver.",
        "adnxs.com",
        "taboola.com",
        "outbrain.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "criteo.com",
        "criteo.net",
        "ads.tiktok.com",
        "ads.pinterest.com",
        "an.facebook.com",
        "ads-twitter.com",
        "amazon-adsystem.com",
        "moatads.com",
        "media.net",
        "mgid.com",
        "revcontent.com"
    )

    private val TRACKER_HOST_RULES = setOf(
        "google-analytics.com",
        "analytics.google.com",
        "googletagmanager.com",
        "googletagservices.com",
        "scorecardresearch.com",
        "quantserve.com",
        "analytics.tiktok.com",
        "log-va.tiktokv.com",
        "mon.tiktokv.com",
        "trk.pinterest.com",
        "ct.pinterest.com",
        "pixel.facebook.com",
        "analytics.twitter.com",
        "hotjar.com",
        "mixpanel.com",
        "segment.io",
        "segment.com",
        "amplitude.com",
        "appsflyer.com",
        "adjust.com",
        "branch.io",
        "clarity.ms",
        "newrelic.com"
    )

    private val MALWARE_HOST_RULES = setOf(
        "malware-test",
        "phishing-test",
        "coinhive.com",
        "coin-hive.com",
        "cryptoloot.pro",
        "jsecoin.com",
        "webminepool.com",
        "minero.cc",
        "adf.ly",
        "shorte.st",
        "linkvertise.net",
        "ouo.io",
        "exe.io"
    )

    private val URL_REGEX = Regex("""https?://[^\s<>"'\)\]]+""", RegexOption.IGNORE_CASE)

    fun extractFirstUrl(rawText: String?): String? {
        if (rawText.isNullOrBlank()) return null
        val match = URL_REGEX.find(rawText.trim()) ?: return null
        return match.value.trimEnd('.', ',', ';', '!', '?')
    }

    fun normalizeDomain(urlOrDomain: String): String {
        val trimmed = urlOrDomain.trim().lowercase(Locale.US)
        return try {
            val host = if (trimmed.contains("://")) {
                URI(trimmed).host ?: trimmed
            } else {
                trimmed.substringBefore('/').substringBefore(':')
            }
            host.removePrefix("www.")
        } catch (_: Exception) {
            trimmed.substringAfter("://").substringBefore('/').substringBefore(':').removePrefix("www.")
        }
    }

    fun detectPlatform(url: String): RafidPlatform {
        val host = normalizeDomain(url)
        return when {
            host.contains("youtube.com") || host == "youtu.be" || host.contains("googlevideo.com") -> RafidPlatform.YOUTUBE
            host.contains("pinterest.") || host == "pin.it" || host.contains("pinimg.com") -> RafidPlatform.PINTEREST
            host.contains("tiktok.com") || host.contains("tiktokv.com") || host.contains("tiktokcdn") -> RafidPlatform.TIKTOK
            host.contains("instagram.com") || host.contains("cdninstagram.com") -> RafidPlatform.INSTAGRAM
            host.contains("facebook.com") || host == "fb.watch" || host.contains("fbcdn.net") -> RafidPlatform.FACEBOOK
            host == "x.com" || host.contains("twitter.com") || host == "t.co" || host.contains("twimg.com") -> RafidPlatform.X_TWITTER
            host.contains("khanacademy.org") || host.contains("coursera.org") ||
                host.contains("udemy.com") || host.contains("edx.org") ||
                host.contains("nafham.com") || host.contains("noonacademy") ||
                host.contains("drive.google.com") || host.contains("archive.org") -> RafidPlatform.STUDY_PLATFORM
            else -> RafidPlatform.DIRECT
        }
    }

    fun classifyBlockedRequest(
        requestUrl: String,
        pageHost: String?,
        config: RafidConfigState,
        excludedDomains: Set<String>
    ): BlockCategory? {
        val normalizedPageHost = pageHost?.let(::normalizeDomain).orEmpty()
        val requestHost = normalizeDomain(requestUrl)
        if (requestHost.isBlank()) return null

        // If user whitelisted the host of the active tab or requested domain
        if (excludedDomains.any { excluded ->
                val clean = normalizeDomain(excluded)
                clean.isNotBlank() && (normalizedPageHost == clean || normalizedPageHost.endsWith(".$clean") ||
                    requestHost == clean || requestHost.endsWith(".$clean"))
            }
        ) {
            return null
        }

        if (config.blockMalwareSites && matchesRule(requestHost, MALWARE_HOST_RULES)) {
            return BlockCategory.MALWARE
        }
        if (config.blockAdsAndTrackers) {
            if (matchesRule(requestHost, AD_HOST_RULES) || isAdUrlPath(requestUrl)) {
                return BlockCategory.AD
            }
            if (matchesRule(requestHost, TRACKER_HOST_RULES)) {
                return BlockCategory.TRACKER
            }
        }
        return null
    }

    private fun matchesRule(host: String, rules: Set<String>): Boolean {
        return rules.any { rule ->
            if (rule.endsWith(".")) {
                host.startsWith(rule) || host.contains(".$rule")
            } else {
                host == rule || host.endsWith(".$rule")
            }
        }
    }

    private fun isAdUrlPath(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        return lower.contains("/pagead/") ||
            lower.contains("/ptracking") ||
            lower.contains("youtube.com/api/stats/ads") ||
            lower.contains("doubleclick.net/gampad")
    }

    fun isSniffableMediaUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        if (lower.startsWith("data:") || lower.startsWith("blob:")) return false
        if (lower.contains(".js") || lower.contains(".css") || lower.contains(".woff") || lower.contains(".svg")) return false
        return lower.contains(".mp4") ||
            lower.contains(".m4a") ||
            lower.contains(".mp3") ||
            lower.contains(".webm") ||
            lower.contains(".m3u8") ||
            (lower.contains("googlevideo.com/videoplayback") && lower.contains("mime=")) ||
            lower.contains("v.pinimg.com/videos/") ||
            lower.contains("video.twimg.com/") ||
            (lower.contains("tiktok") && lower.contains("mime_type=video"))
    }

    fun inferMediaCandidate(
        url: String,
        pageTitle: String?,
        cookiesHeader: String?
    ): SniffedMediaCandidate? {
        if (!isSniffableMediaUrl(url)) return null
        val lower = url.lowercase(Locale.US)
        val ext = when {
            lower.contains("mime_type=video") -> "mp4"
            lower.contains(".mp3") -> "mp3"
            lower.contains(".m4a") || lower.contains("mime=audio") || lower.contains("mime_type=audio") -> "m4a"
            lower.contains(".webm") -> "webm"
            lower.contains(".m3u8") -> "m3u8"
            else -> "mp4"
        }
        val mime = when (ext) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "webm" -> if (lower.contains("audio")) "audio/webm" else "video/webm"
            "m3u8" -> "application/x-mpegURL"
            else -> "video/mp4"
        }
        val quality = when {
            ext == "mp3" || ext == "m4a" -> "صوت عالي الجودة"
            lower.contains("1080") -> "1080p"
            lower.contains("720") -> "720p"
            lower.contains("480") -> "480p"
            else -> "فيديو عالي الجودة"
        }
        val cleanTitle = pageTitle?.takeIf { it.isNotBlank() }
            ?: url.substringAfterLast('/').substringBefore('?').ifBlank { "وسائط ملتقطة" }
        return SniffedMediaCandidate(
            url = url,
            title = cleanTitle.take(120),
            mimeType = mime,
            extension = ext,
            qualityLabel = quality,
            cookiesHeader = cookiesHeader
        )
    }

    fun categoryFromMimeOrExt(mime: String, ext: String, isPlaylist: Boolean = false): RafidMediaCategory {
        if (isPlaylist) return RafidMediaCategory.PLAYLIST
        val m = mime.lowercase(Locale.US)
        val e = ext.lowercase(Locale.US)
        return when {
            m.startsWith("audio/") || e in setOf("mp3", "m4a", "aac", "ogg", "wav", "opus") -> RafidMediaCategory.AUDIO
            m.startsWith("image/") || e in setOf("jpg", "jpeg", "png", "webp", "gif") -> RafidMediaCategory.IMAGE
            m.contains("pdf") || e in setOf("pdf", "epub", "docx") -> RafidMediaCategory.DOCUMENT
            else -> RafidMediaCategory.VIDEO
        }
    }
}
