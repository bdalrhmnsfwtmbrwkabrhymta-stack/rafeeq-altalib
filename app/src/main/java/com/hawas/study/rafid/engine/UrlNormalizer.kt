package com.hawas.study.rafid.engine

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

object UrlNormalizer {

    private val TRACKING_PARAMS_YOUTUBE = setOf(
        "si", "feature", "pp", "ab_channel", "fbclid", "gclid", "igshid",
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "app"
    )

    private val TRACKING_PARAMS_SOCIAL = setOf(
        "mibextid", "sfnsn", "ref", "refid", "rdid", "fbclid", "gclid",
        "__tn__", "igsh", "igshid", "invite_code", "sfo", "mt", "epik",
        "is_from_webapp", "sender_device", "_r", "_t", "checksum", "sec_uid", "u_code",
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content"
    )

    private val SHORT_REDIRECT_MARKERS = listOf(
        "pin.it/", "vm.tiktok.com/", "vt.tiktok.com/", "fb.watch/",
        "facebook.com/share/", "fb.com/share/", "fb.me/", "instagr.am/",
        "t.co/", "youtu.be/", "bit.ly/"
    )

    fun isShortRedirectUrl(url: String): Boolean {
        val lower = url.trim().lowercase(Locale.ROOT)
        return SHORT_REDIRECT_MARKERS.any { lower.contains(it) } ||
            lower.endsWith("fb.watch") ||
            lower.contains("//pin.it") ||
            lower.contains("//instagr.am")
    }

    fun normalize(rawInput: String): String {
        val extracted = AdAndTrackerShield.extractFirstUrl(rawInput) ?: rawInput.trim()
        if (extracted.isBlank()) return extracted

        val parsed = extracted.toHttpUrlOrNull() ?: return extracted
        val host = parsed.host.lowercase(Locale.ROOT)

        // Never alter local loopback test server URLs
        if (host == "127.0.0.1" || host == "localhost" || host == "10.0.2.2" || host == "::1") {
            return extracted
        }

        // 1. YouTube canonicalization: shorts, youtu.be, m.youtube, embed, live -> watch?v=ID
        if (isYouTubeHost(host)) {
            val segments = parsed.pathSegments.filter { it.isNotBlank() }
            val videoId = extractYouTubeVideoId(host, segments, parsed.queryParameter("v"))
            val listId = parsed.queryParameter("list")?.takeIf { it.isNotBlank() }

            if (!videoId.isNullOrBlank()) {
                return buildString {
                    append("https://www.youtube.com/watch?v=").append(videoId)
                    if (!listId.isNullOrBlank()) {
                        append("&list=").append(listId)
                    }
                }
            }

            if (segments.firstOrNull().equals("playlist", ignoreCase = true) && !listId.isNullOrBlank()) {
                return "https://www.youtube.com/playlist?list=$listId"
            }

            val builder = parsed.newBuilder().scheme("https")
            if (host == "m.youtube.com" || host == "youtube.com") {
                builder.host("www.youtube.com")
            }
            parsed.queryParameterNames.forEach { key ->
                val lowerKey = key.lowercase(Locale.ROOT)
                if (lowerKey in TRACKING_PARAMS_YOUTUBE || lowerKey.startsWith("utm_")) {
                    builder.removeAllQueryParameters(key)
                }
            }
            return builder.build().toString()
        }

        // 2. Facebook canonicalization
        if (isFacebookHost(host)) {
            if (host == "fb.watch" || host == "fb.me") {
                return stripSocialTracking(parsed, hostOverride = null)
            }
            val normalizedHost = when {
                host == "m.facebook.com" || host == "mbasic.facebook.com" ||
                    host == "touch.facebook.com" || host == "web.facebook.com" ||
                    host == "facebook.com" -> "www.facebook.com"
                else -> null
            }
            return stripSocialTracking(parsed, hostOverride = normalizedHost)
        }

        // 3. Instagram canonicalization
        if (isInstagramHost(host)) {
            if (host == "instagr.am") {
                return stripSocialTracking(parsed, hostOverride = null)
            }
            val normalizedHost = if (host == "m.instagram.com" || host == "instagram.com") {
                "www.instagram.com"
            } else {
                null
            }
            return stripSocialTracking(parsed, hostOverride = normalizedHost)
        }

        // 4. Pinterest canonicalization
        if (isPinterestHost(host)) {
            return stripSocialTracking(parsed, hostOverride = null)
        }

        // 5. TikTok canonicalization
        if (isTikTokHost(host)) {
            val normalizedHost = if (host == "m.tiktok.com" || host == "tiktok.com") {
                "www.tiktok.com"
            } else {
                null
            }
            return stripSocialTracking(parsed, hostOverride = normalizedHost)
        }

        return extracted
    }

    private fun stripSocialTracking(
        parsed: okhttp3.HttpUrl,
        hostOverride: String?
    ): String {
        val builder = parsed.newBuilder()
        if (parsed.scheme == "http" && !isLoopbackHost(parsed.host)) {
            builder.scheme("https")
        }
        if (!hostOverride.isNullOrBlank()) {
            builder.host(hostOverride)
        }
        parsed.queryParameterNames.forEach { key ->
            val lower = key.lowercase(Locale.ROOT)
            if (lower in TRACKING_PARAMS_SOCIAL || lower.startsWith("utm_") || lower.startsWith("__cft__")) {
                builder.removeAllQueryParameters(key)
            }
        }
        return builder.build().toString()
    }

    private fun extractYouTubeVideoId(host: String, segments: List<String>, vParam: String?): String? {
        if (host == "youtu.be") {
            return segments.firstOrNull()?.takeIf { it.isNotBlank() }
        }
        val first = segments.firstOrNull()?.lowercase(Locale.ROOT)
        if (first in setOf("shorts", "embed", "live", "v")) {
            return segments.getOrNull(1)?.takeIf { it.isNotBlank() }
        }
        return vParam?.takeIf { it.isNotBlank() }
    }

    private fun isLoopbackHost(host: String): Boolean {
        val h = host.lowercase(Locale.ROOT)
        return h == "127.0.0.1" || h == "localhost" || h == "10.0.2.2" || h == "::1"
    }

    private fun isYouTubeHost(host: String): Boolean =
        host == "youtu.be" ||
            host == "youtube.com" ||
            host.endsWith(".youtube.com") ||
            host.contains("youtube-nocookie.com")

    private fun isFacebookHost(host: String): Boolean =
        host == "fb.watch" ||
            host == "fb.me" ||
            host == "fb.com" ||
            host.endsWith(".fb.com") ||
            host == "facebook.com" ||
            host.endsWith(".facebook.com")

    private fun isInstagramHost(host: String): Boolean =
        host == "instagram.com" ||
            host.endsWith(".instagram.com") ||
            host == "instagr.am"

    private fun isPinterestHost(host: String): Boolean =
        host == "pin.it" || host.contains("pinterest.")

    private fun isTikTokHost(host: String): Boolean =
        host == "tiktok.com" || host.endsWith(".tiktok.com")
}
