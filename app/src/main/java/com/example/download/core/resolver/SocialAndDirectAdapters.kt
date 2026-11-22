package com.example.download.core.resolver

import com.example.download.core.model.DownloadError
import com.example.download.core.model.DownloadErrorMapper
import com.example.download.core.model.MediaPlatform
import com.example.download.core.model.ResolveRequest
import com.example.download.core.model.ResolvedMediaFormat
import com.example.download.core.model.ResolvedMediaKind
import com.example.download.core.model.ResolvedMediaResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private fun buildPlatformMediaFromYtDlp(
    originalUrl: String,
    canonicalUrl: String,
    platform: MediaPlatform,
    info: YtDlpExtractedInfo,
    cookiesHeader: String?
): ResolvedMediaResult.SingleMedia {
    val domain = ResolverHttpUtils.normalizeHost(canonicalUrl)
    val kind = when {
        info.formats.all { it.isAudioOnly } -> ResolvedMediaKind.AUDIO_ONLY
        info.formats.any { it.isHls } -> ResolvedMediaKind.HLS_STREAM
        info.formats.any { it.isDash } -> ResolvedMediaKind.DASH_STREAM
        else -> ResolvedMediaKind.SINGLE_VIDEO
    }
    return ResolvedMediaResult.SingleMedia(
        originalUrl = originalUrl,
        canonicalUrl = canonicalUrl,
        title = info.title,
        authorOrSource = info.uploader ?: platform.labelEn,
        sourceDomain = domain,
        platform = platform,
        kind = kind,
        thumbnailUrl = info.thumbnailUrl,
        durationSeconds = info.durationSeconds,
        formats = info.formats,
        headers = info.baseHeaders,
        cookiesHeader = cookiesHeader
    )
}

private fun qualityLabelForHeight(h: Int): String = when {
    h >= 2160 -> "2160p • 4K فائقة الدقة"
    h >= 1440 -> "1440p • 2K QHD"
    h >= 1080 -> "1080p • Full HD"
    h >= 720 -> "720p • HD"
    h >= 480 -> "480p • متوسطة"
    h >= 360 -> "360p • اقتصادية"
    h > 0 -> "${h}p"
    else -> "الجودة الأصلية"
}

class FacebookProviderAdapter(
    private val httpClient: OkHttpClient,
    private val ytDlpGateway: YtDlpGateway = NoOpYtDlpGateway
) : MediaProviderAdapter {

    override val platform: MediaPlatform = MediaPlatform.FACEBOOK

    override fun canHandle(canonicalUrl: String, host: String): Boolean {
        val h = host.lowercase(Locale.ROOT)
        return h == "fb.watch" ||
            h == "fb.com" ||
            h.endsWith(".fb.com") ||
            h == "facebook.com" ||
            h.endsWith(".facebook.com") ||
            h.contains("fbcdn.net")
    }

    override suspend fun resolve(canonicalUrl: String, request: ResolveRequest): ResolvedMediaResult {
        val diag = StringBuilder()

        if (ytDlpGateway.isAvailable()) {
            ytDlpGateway.extractSingle(canonicalUrl, request.cookiesFile).onSuccess { info ->
                if (info.formats.isNotEmpty()) {
                    return buildPlatformMediaFromYtDlp(
                        originalUrl = request.rawUrl,
                        canonicalUrl = canonicalUrl,
                        platform = MediaPlatform.FACEBOOK,
                        info = info,
                        cookiesHeader = request.cookiesHeader
                    )
                }
            }.onFailure { err ->
                diag.appendLine("[yt-dlp-fb] ${err.message}")
            }
        }

        val resp = ResolverHttpUtils.fetchTextWithStatus(
            client = httpClient,
            url = canonicalUrl,
            cookiesHeader = request.cookiesHeader,
            userAgent = request.userAgent ?: ResolverHttpUtils.DESKTOP_UA,
            extraHeaders = mapOf(
                "Sec-Fetch-Dest" to "document",
                "Sec-Fetch-Mode" to "navigate"
            )
        )
        if (resp == null) {
            return ResolvedMediaResult.Failure(
                DownloadError.NetworkUnavailable("Failed to reach Facebook URL: $canonicalUrl"),
                diag.toString()
            )
        }

        val mergedCookies = ResolverHttpUtils.mergeCookieStrings(request.cookiesHeader, resp.setCookiesHeader)
        if (resp.statusCode == 400 || resp.statusCode == 401 || resp.statusCode == 403) {
            diag.appendLine("[fb-http] HTTP ${resp.statusCode} at ${resp.finalUrl}")
            // Try mbasic fallback once before failing if HTTP 400/403 on www
            val mbasicUrl = canonicalUrl
                .replace("://www.facebook.com", "://mbasic.facebook.com", ignoreCase = true)
                .replace("://m.facebook.com", "://mbasic.facebook.com", ignoreCase = true)
            if (mbasicUrl != canonicalUrl) {
                val mbasicResp = ResolverHttpUtils.fetchTextWithStatus(
                    client = httpClient,
                    url = mbasicUrl,
                    cookiesHeader = mergedCookies,
                    userAgent = ResolverHttpUtils.MOBILE_UA
                )
                if (mbasicResp != null && mbasicResp.statusCode < 400) {
                    val mbasicCookies = ResolverHttpUtils.mergeCookieStrings(mergedCookies, mbasicResp.setCookiesHeader)
                    val parsed = parseFacebookHtml(request.rawUrl, mbasicResp.finalUrl, mbasicResp.body, mbasicCookies)
                    if (parsed is ResolvedMediaResult.SingleMedia) return parsed
                }
            }
            val err = if (resp.statusCode == 401 || resp.finalUrl.contains("/login", true)) {
                DownloadError.AuthenticationRequired("فيسبوك", "Facebook returned HTTP ${resp.statusCode} / login required")
            } else {
                DownloadError.HttpError(resp.statusCode, "Facebook returned HTTP ${resp.statusCode} (cookie/browser session recommended)")
            }
            return ResolvedMediaResult.Failure(err, diag.toString())
        }

        if (resp.statusCode >= 400) {
            return ResolvedMediaResult.Failure(
                DownloadErrorMapper.fromHttpStatus(resp.statusCode, resp.finalUrl),
                diag.toString()
            )
        }

        return parseFacebookHtml(request.rawUrl, resp.finalUrl, resp.body, mergedCookies, diag)
    }

    fun parseFacebookHtml(
        originalUrl: String,
        canonicalUrl: String,
        html: String,
        cookiesHeader: String? = null,
        diag: StringBuilder = StringBuilder()
    ): ResolvedMediaResult {
        val candidates = linkedMapOf<String, Pair<Int, String>>() // url -> (height, label)

        fun addCandidate(raw: String?, height: Int, label: String) {
            val cleaned = raw?.let(ResolverHttpUtils::decodeHtmlEntities)?.trim() ?: return
            if (cleaned.startsWith("http", true) && !candidates.containsKey(cleaned)) {
                candidates[cleaned] = height to label
            }
        }

        val jsonStreamPatterns = listOf(
            Triple(Regex(""""browser_native_hd_url"\s*:\s*"([^"]+)""""), 1080, "1080p HD"),
            Triple(Regex(""""playable_url_quality_hd"\s*:\s*"([^"]+)""""), 1080, "1080p HD"),
            Triple(Regex(""""hd_src_no_ratelimit"\s*:\s*"([^"]+)""""), 1080, "1080p HD"),
            Triple(Regex(""""hd_src"\s*:\s*"([^"]+)""""), 720, "720p HD"),
            Triple(Regex(""""browser_native_sd_url"\s*:\s*"([^"]+)""""), 480, "480p SD"),
            Triple(Regex(""""playable_url"\s*:\s*"([^"]+)""""), 480, "480p SD"),
            Triple(Regex(""""sd_src"\s*:\s*"([^"]+)""""), 360, "360p SD")
        )
        for ((regex, height, label) in jsonStreamPatterns) {
            regex.findAll(html).forEach { m ->
                addCandidate(m.groupValues[1], height, label)
            }
        }

        addCandidate(ResolverHttpUtils.extractMetaContent(html, "og:video:secure_url"), 720, "720p")
        addCandidate(ResolverHttpUtils.extractMetaContent(html, "og:video"), 720, "720p")

        if (candidates.isEmpty()) {
            val lowerHtml = html.lowercase(Locale.ROOT)
            val isLoginWall = lowerHtml.contains("login_form") ||
                lowerHtml.contains("/login.php") ||
                lowerHtml.contains("you must log in") ||
                lowerHtml.contains("تسجيل الدخول")
            return ResolvedMediaResult.Failure(
                error = if (isLoginWall) {
                    DownloadError.AuthenticationRequired("فيسبوك", "Facebook page requires login session")
                } else {
                    DownloadError.ExtractionFailed("فيسبوك", "No playable Facebook stream keys found in HTML")
                },
                diagnosticLog = diag.toString()
            )
        }

        val headers = buildMap {
            put("Referer", canonicalUrl)
            if (!cookiesHeader.isNullOrBlank()) put("Cookie", cookiesHeader)
        }
        val formats = candidates.entries.mapIndexed { idx, (streamUrl, meta) ->
            val (h, lbl) = meta
            val exactBytes = ResolverHttpUtils.probeContentLength(httpClient, streamUrl, headers)
            ResolvedMediaFormat(
                id = "fb_${h}_$idx",
                labelAr = qualityLabelForHeight(h),
                labelEn = lbl,
                subLabelAr = "فيديو فيسبوك مباشر • MP4",
                containerExt = "mp4",
                mimeType = "video/mp4",
                resolutionHeight = h,
                streamUrl = streamUrl,
                exactBytes = exactBytes,
                isApproximateBytes = exactBytes <= 0L,
                headers = headers,
                badgeAr = if (h >= 720) "HD" else null
            )
        }.sortedByDescending { it.resolutionHeight }

        val title = ResolverHttpUtils.extractMetaContent(html, "og:title")
            ?: ResolverHttpUtils.extractHtmlTitle(html)
            ?: "فيديو فيسبوك"
        val thumb = ResolverHttpUtils.extractMetaContent(html, "og:image")

        return ResolvedMediaResult.SingleMedia(
            originalUrl = originalUrl,
            canonicalUrl = canonicalUrl,
            title = title,
            authorOrSource = "Facebook",
            sourceDomain = ResolverHttpUtils.normalizeHost(canonicalUrl),
            platform = MediaPlatform.FACEBOOK,
            kind = ResolvedMediaKind.SINGLE_VIDEO,
            thumbnailUrl = thumb,
            formats = formats,
            headers = headers,
            cookiesHeader = cookiesHeader
        )
    }
}

class InstagramProviderAdapter(
    private val httpClient: OkHttpClient,
    private val ytDlpGateway: YtDlpGateway = NoOpYtDlpGateway
) : MediaProviderAdapter {

    override val platform: MediaPlatform = MediaPlatform.INSTAGRAM

    override fun canHandle(canonicalUrl: String, host: String): Boolean {
        val h = host.lowercase(Locale.ROOT)
        return h == "instagram.com" || h.endsWith(".instagram.com") || h == "instagr.am" || h.contains("cdninstagram.com")
    }

    override suspend fun resolve(canonicalUrl: String, request: ResolveRequest): ResolvedMediaResult {
        val diag = StringBuilder()

        if (ytDlpGateway.isAvailable()) {
            ytDlpGateway.extractSingle(canonicalUrl, request.cookiesFile).onSuccess { info ->
                if (info.formats.isNotEmpty()) {
                    return buildPlatformMediaFromYtDlp(
                        originalUrl = request.rawUrl,
                        canonicalUrl = canonicalUrl,
                        platform = MediaPlatform.INSTAGRAM,
                        info = info,
                        cookiesHeader = request.cookiesHeader
                    )
                }
            }.onFailure { diag.appendLine("[yt-dlp-ig] ${it.message}") }
        }

        val resp = ResolverHttpUtils.fetchTextWithStatus(
            client = httpClient,
            url = canonicalUrl,
            cookiesHeader = request.cookiesHeader,
            userAgent = request.userAgent ?: ResolverHttpUtils.DESKTOP_UA
        ) ?: return ResolvedMediaResult.Failure(
            DownloadError.NetworkUnavailable("Failed to connect to Instagram"),
            diag.toString()
        )

        if (resp.statusCode >= 400) {
            return ResolvedMediaResult.Failure(
                DownloadErrorMapper.fromHttpStatus(resp.statusCode, resp.finalUrl),
                diag.toString()
            )
        }

        val mergedCookies = ResolverHttpUtils.mergeCookieStrings(request.cookiesHeader, resp.setCookiesHeader)
        return parseInstagramHtml(request.rawUrl, resp.finalUrl, resp.body, mergedCookies, diag)
    }

    fun parseInstagramHtml(
        originalUrl: String,
        canonicalUrl: String,
        html: String,
        cookiesHeader: String? = null,
        diag: StringBuilder = StringBuilder()
    ): ResolvedMediaResult {
        data class IgVideoCandidate(val url: String, val height: Int, val width: Int)

        val candidatesByUrl = linkedMapOf<String, IgVideoCandidate>()
        fun addCandidate(rawUrl: String?, width: Int = 0, height: Int = 0) {
            val cleaned = rawUrl?.let(ResolverHttpUtils::decodeHtmlEntities)?.trim() ?: return
            if (!cleaned.startsWith("http", true)) return
            val existing = candidatesByUrl[cleaned]
            val effH = if (width > 0 && height > 0) minOf(width, height) else height.coerceAtLeast(0)
            if (existing == null || effH > existing.height) {
                candidatesByUrl[cleaned] = IgVideoCandidate(cleaned, effH, width)
            }
        }

        // Parse real video_versions JSON entries so all actual qualities are preserved instead of only highest
        Regex(""""video_versions"\s*:\s*\[(.*?)\]""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(html)
            .forEach { block ->
                val arr = runCatching { JSONArray("[${block.groupValues[1]}]") }.getOrNull() ?: return@forEach
                for (i in 0 until arr.length()) {
                    val v = arr.optJSONObject(i) ?: continue
                    addCandidate(
                        rawUrl = v.optString("url"),
                        width = v.optInt("width", 0),
                        height = v.optInt("height", 0)
                    )
                }
            }

        ResolverHttpUtils.extractMetaContent(html, "og:video:secure_url")?.let { addCandidate(it) }
        ResolverHttpUtils.extractMetaContent(html, "og:video")?.let { addCandidate(it) }
        Regex(""""video_url"\s*:\s*"([^"]+)"""").findAll(html).forEach {
            addCandidate(it.groupValues[1])
        }
        Regex(""""url"\s*:\s*"(https:[^"]*cdninstagram[^"]*\.mp4[^"]*)"""").findAll(html).forEach {
            addCandidate(it.groupValues[1])
        }

        val cleanVideos = candidatesByUrl.values.toList()
        if (cleanVideos.isEmpty()) {
            val lower = html.lowercase(Locale.ROOT)
            val needsAuth = lower.contains("loginandsignuppage") ||
                lower.contains("/accounts/login") ||
                lower.contains("log in to continue")
            return ResolvedMediaResult.Failure(
                error = if (needsAuth) {
                    DownloadError.AuthenticationRequired("إنستغرام", "Instagram redirected to login gate")
                } else {
                    DownloadError.ExtractionFailed("إنستغرام", "No playable Instagram video URL found in HTML")
                },
                diagnosticLog = diag.toString()
            )
        }

        val headers = buildMap {
            put("Referer", "https://www.instagram.com/")
            put("User-Agent", ResolverHttpUtils.DESKTOP_UA)
            if (!cookiesHeader.isNullOrBlank()) put("Cookie", cookiesHeader)
        }

        val seenRes = hashSetOf<Int>()
        val sortedCandidates = cleanVideos.sortedByDescending { it.height }
        val formats = sortedCandidates.mapIndexedNotNull { idx, cand ->
            val len = ResolverHttpUtils.probeContentLength(httpClient, cand.url, headers)
            val effH = cand.height
            // De-duplicate exact same real resolution only; preserve distinct qualities & extensions
            if (effH > 0 && !seenRes.add(effH)) return@mapIndexedNotNull null
            ResolvedMediaFormat(
                id = if (effH > 0) "${effH}p" else "ig_$idx",
                labelAr = if (effH > 0) qualityLabelForHeight(effH) else "الجودة المتاحة • MP4",
                labelEn = if (effH > 0) "${effH}p" else "Original MP4",
                subLabelAr = "إنستغرام مباشر • MP4",
                containerExt = "mp4",
                mimeType = "video/mp4",
                resolutionHeight = effH,
                width = cand.width,
                streamUrl = cand.url,
                exactBytes = len,
                isApproximateBytes = len <= 0L,
                headers = headers,
                badgeAr = if (effH >= 1080) "Full HD" else if (effH >= 720) "HD" else null,
                hasVideo = true,
                hasAudio = true
            )
        }

        val title = ResolverHttpUtils.extractMetaContent(html, "og:title")
            ?: ResolverHttpUtils.extractHtmlTitle(html)
            ?: "مقطع إنستغرام"
        val thumb = ResolverHttpUtils.extractMetaContent(html, "og:image")

        return ResolvedMediaResult.SingleMedia(
            originalUrl = originalUrl,
            canonicalUrl = canonicalUrl,
            title = title,
            authorOrSource = "Instagram",
            sourceDomain = "instagram.com",
            platform = MediaPlatform.INSTAGRAM,
            kind = ResolvedMediaKind.SINGLE_VIDEO,
            thumbnailUrl = thumb,
            formats = formats,
            headers = headers,
            cookiesHeader = cookiesHeader
        )
    }
}

class TikTokProviderAdapter(
    private val httpClient: OkHttpClient,
    private val ytDlpGateway: YtDlpGateway = NoOpYtDlpGateway
) : MediaProviderAdapter {

    override val platform: MediaPlatform = MediaPlatform.TIKTOK

    override fun canHandle(canonicalUrl: String, host: String): Boolean {
        val h = host.lowercase(Locale.ROOT)
        return h == "tiktok.com" ||
            h.endsWith(".tiktok.com") ||
            h.contains("tiktokv.com") ||
            h.contains("tiktokcdn")
    }

    override suspend fun resolve(canonicalUrl: String, request: ResolveRequest): ResolvedMediaResult {
        val diag = StringBuilder()
        val isExplicitMusicPage = canonicalUrl.contains("/music/", ignoreCase = true)

        if (ytDlpGateway.isAvailable()) {
            // Leave extraArgs empty so AndroidYtDlpGateway executes its default + multi-hostname fallback cascade cleanly
            ytDlpGateway.extractSingle(
                url = canonicalUrl,
                cookiesFile = request.cookiesFile
            ).onSuccess { info ->
                if (info.formats.isNotEmpty()) {
                    val sanitizedFormats = info.formats.map { fmt ->
                        if (!fmt.isAudioOnly && isLikelyAudioOnlyUrl(fmt.streamUrl)) {
                            fmt.copy(
                                isAudioOnly = true,
                                hasVideo = false,
                                hasAudio = true,
                                containerExt = if (fmt.streamUrl.contains(".mp3", true)) "mp3" else "m4a",
                                mimeType = if (fmt.streamUrl.contains(".mp3", true)) "audio/mpeg" else "audio/mp4"
                            )
                        } else {
                            fmt
                        }
                    }
                    val candidateMedia = buildPlatformMediaFromYtDlp(
                        originalUrl = request.rawUrl,
                        canonicalUrl = canonicalUrl,
                        platform = MediaPlatform.TIKTOK,
                        info = info.copy(formats = sanitizedFormats),
                        cookiesHeader = request.cookiesHeader
                    )
                    val verified = filterPlayableFormatsOrFail(candidateMedia, diag)
                    if (verified is ResolvedMediaResult.SingleMedia) {
                        val hasPlayableVideo = verified.formats.any { !it.isAudioOnly }
                        if (hasPlayableVideo || isExplicitMusicPage) {
                            return verified
                        }
                        diag.appendLine("[yt-dlp-tt] Returned audio-only formats for TikTok video URL; falling back to HTML/sniffer")
                    }
                }
            }.onFailure { diag.appendLine("[yt-dlp-tt] ${it.message}") }
        }

        val resp = ResolverHttpUtils.fetchTextWithStatus(
            client = httpClient,
            url = canonicalUrl,
            cookiesHeader = request.cookiesHeader,
            userAgent = request.userAgent ?: ResolverHttpUtils.DESKTOP_UA
        ) ?: return ResolvedMediaResult.Failure(
            DownloadError.NetworkUnavailable("Failed to connect to TikTok"),
            diag.toString()
        )

        if (resp.statusCode >= 400) {
            return ResolvedMediaResult.Failure(
                DownloadErrorMapper.fromHttpStatus(resp.statusCode, resp.finalUrl),
                diag.toString()
            )
        }

        val mergedCookies = ResolverHttpUtils.mergeCookieStrings(request.cookiesHeader, resp.setCookiesHeader)
        val parsed = parseTikTokHtml(request.rawUrl, resp.finalUrl, resp.body, mergedCookies, diag)
        return if (parsed is ResolvedMediaResult.SingleMedia) {
            val verified = filterPlayableFormatsOrFail(parsed, diag)
            if (!isExplicitMusicPage &&
                verified is ResolvedMediaResult.SingleMedia &&
                verified.formats.none { !it.isAudioOnly }
            ) {
                ResolvedMediaResult.Failure(
                    DownloadError.ExtractionFailed("تيك توك", "TikTok HTML returned audio only without video stream"),
                    diag.toString()
                )
            } else {
                verified
            }
        } else {
            parsed
        }
    }

    private fun isLikelyAudioOnlyUrl(url: String?): Boolean {
        val lower = url?.lowercase(Locale.ROOT).orEmpty()
        if (lower.isBlank()) return false
        val path = lower.substringBefore('?').substringBefore('#')
        return path.endsWith(".mp3") ||
            path.endsWith(".m4a") ||
            path.endsWith(".aac") ||
            path.endsWith(".opus") ||
            lower.contains("mime_type=audio") ||
            lower.contains("mime=audio")
    }

    private fun filterPlayableFormatsOrFail(
        media: ResolvedMediaResult.SingleMedia,
        diag: StringBuilder
    ): ResolvedMediaResult {
        var sawHtmlResponse = false
        var lastStatusCode = 0
        val verifiedFormats = mutableListOf<ResolvedMediaFormat>()

        for (fmt in media.formats) {
            if (fmt.isHlsOrDash || ResolverHttpUtils.isManifestUrl(fmt.streamUrl)) {
                verifiedFormats += fmt
                continue
            }
            val mergedHeaders = buildMap {
                putAll(media.headers)
                putAll(fmt.headers)
                if (!media.cookiesHeader.isNullOrBlank() && keys.none { it.equals("Cookie", true) }) {
                    put("Cookie", media.cookiesHeader)
                }
            }
            val probe = ResolverHttpUtils.probeStreamMedia(httpClient, fmt.streamUrl, mergedHeaders)
            val audioProbe = fmt.audioStreamUrl?.takeIf {
                it.startsWith("http", true) && !ResolverHttpUtils.isManifestUrl(it)
            }?.let { audioUrl ->
                ResolverHttpUtils.probeStreamMedia(httpClient, audioUrl, mergedHeaders)
            }

            if (!probe.reachable) {
                // Offline / no direct network reachability in probe -> preserve candidate for downstream engine
                verifiedFormats += fmt
            } else if (probe.isHtmlPage || (audioProbe != null && audioProbe.reachable && audioProbe.isHtmlPage)) {
                sawHtmlResponse = true
                diag.appendLine("[tt-probe] Rejected HTML response for ${fmt.id} (${probe.contentType})")
            } else if (probe.statusCode >= 400 || (audioProbe != null && audioProbe.reachable && audioProbe.statusCode >= 400)) {
                lastStatusCode = maxOf(probe.statusCode, audioProbe?.statusCode ?: 0)
                diag.appendLine("[tt-probe] Rejected HTTP $lastStatusCode for ${fmt.id}")
            } else {
                val isAudioActual = probe.contentType.startsWith("audio/", ignoreCase = true)
                val updatedBytes = if ((fmt.exactBytes <= 0L || fmt.isApproximateBytes) && probe.contentLength > 0L) {
                    probe.contentLength
                } else {
                    fmt.exactBytes
                }
                verifiedFormats += fmt.copy(
                    exactBytes = updatedBytes,
                    isApproximateBytes = updatedBytes <= 0L,
                    isAudioOnly = fmt.isAudioOnly || isAudioActual,
                    hasVideo = !fmt.isAudioOnly && !isAudioActual,
                    hasAudio = fmt.hasAudio || isAudioActual,
                    headers = mergedHeaders
                )
            }
        }

        if (verifiedFormats.isNotEmpty()) {
            val sortedVerified = verifiedFormats.sortedWith(
                compareBy<ResolvedMediaFormat> { it.isAudioOnly }
                    .thenBy { fmt ->
                        val hint = "${fmt.id} ${fmt.labelEn} ${fmt.streamUrl}".lowercase(Locale.ROOT)
                        hint.contains("wmplay") || (hint.contains("watermark") && !hint.contains("no watermark"))
                    }
                    .thenByDescending { it.exactBytes.coerceAtLeast(0L) }
                    .thenByDescending { it.resolutionHeight }
                    .thenByDescending { it.bitrateKbps }
            )
            return media.copy(formats = sortedVerified)
        }
        return if (sawHtmlResponse) {
            ResolvedMediaResult.Failure(
                DownloadError.UnsupportedMedia(
                    mediaType = "text/html",
                    technicalDetail = "All TikTok candidate URLs returned HTML instead of media stream"
                ),
                diag.toString()
            )
        } else if (lastStatusCode == 403 || lastStatusCode == 410) {
            ResolvedMediaResult.Failure(
                DownloadError.ExpiredMediaURL(lastStatusCode, "TikTok CDN URL expired or blocked (HTTP $lastStatusCode)"),
                diag.toString()
            )
        } else {
            ResolvedMediaResult.Failure(
                DownloadError.ExtractionFailed("تيك توك", "No verified playable TikTok streams remained"),
                diag.toString()
            )
        }
    }

    fun parseTikTokHtml(
        originalUrl: String,
        canonicalUrl: String,
        html: String,
        cookiesHeader: String? = null,
        diag: StringBuilder = StringBuilder()
    ): ResolvedMediaResult {
        var title = ResolverHttpUtils.extractMetaContent(html, "og:title")
            ?: ResolverHttpUtils.extractHtmlTitle(html)
            ?: "فيديو تيك توك"
        var author = "TikTok"
        var thumb = ResolverHttpUtils.extractMetaContent(html, "og:image")
        var duration = 0
        val formats = mutableListOf<ResolvedMediaFormat>()

        val headers = buildMap {
            put("Referer", "https://www.tiktok.com/")
            put("User-Agent", ResolverHttpUtils.DESKTOP_UA)
            if (!cookiesHeader.isNullOrBlank()) put("Cookie", cookiesHeader)
        }

        val rehydrationJson = ResolverHttpUtils.extractScriptJsonById(html, "__UNIVERSAL_DATA_FOR_REHYDRATION__")
        val rehydrationItem = rehydrationJson?.optJSONObject("__DEFAULT_SCOPE__")
            ?.optJSONObject("webapp.video-detail")
            ?.optJSONObject("itemInfo")
            ?.optJSONObject("itemStruct")

        val sigiItem = if (rehydrationItem == null) {
            val sigiJson = ResolverHttpUtils.extractScriptJsonById(html, "SIGI_STATE")
            val itemModule = sigiJson?.optJSONObject("ItemModule")
            val firstKey = itemModule?.keys()?.let { if (it.hasNext()) it.next() else null }
            firstKey?.let { itemModule.optJSONObject(it) }
        } else {
            null
        }

        val itemStruct = rehydrationItem ?: sigiItem
        if (itemStruct != null) {
            title = itemStruct.optString("desc").takeIf { it.isNotBlank() } ?: title
            author = itemStruct.optJSONObject("author")?.optString("nickname")
                ?.takeIf { it.isNotBlank() }
                ?: itemStruct.optString("author").takeIf { it.isNotBlank() && !it.startsWith("{") }
                ?: author
            val videoObj = itemStruct.optJSONObject("video")
            if (videoObj != null) {
                duration = videoObj.optInt("duration", 0)
                thumb = videoObj.optString("cover").takeIf { it.isNotBlank() } ?: thumb

                val vidW = videoObj.optInt("width", 0)
                val vidH = videoObj.optInt("height", 0)
                val baseEffH = if (vidW > 0 && vidH > 0) minOf(vidW, vidH) else vidH.coerceAtLeast(0)

                val bitrateInfo = videoObj.optJSONArray("bitrateInfo") ?: JSONArray()
                val seenUrls = hashSetOf<String>()
                for (i in 0 until bitrateInfo.length()) {
                    val bObj = bitrateInfo.optJSONObject(i) ?: continue
                    val playAddrObj = bObj.optJSONObject("PlayAddr")
                    val urlList = playAddrObj?.optJSONArray("UrlList") ?: continue
                    var chosenStreamUrl: String? = null
                    for (j in 0 until urlList.length()) {
                        val cand = urlList.optString(j).takeIf { it.startsWith("http", true) } ?: continue
                        if (!isLikelyAudioOnlyUrl(cand)) {
                            chosenStreamUrl = cand
                            break
                        }
                    }
                    val streamUrl = chosenStreamUrl ?: continue
                    if (!seenUrls.add(streamUrl)) continue
                    val pW = playAddrObj.optInt("Width", 0)
                    val pH = playAddrObj.optInt("Height", 0)
                    val height = when {
                        pW > 0 && pH > 0 -> minOf(pW, pH)
                        pH > 0 -> pH
                        else -> baseEffH
                    }
                    val dataSize = playAddrObj.optLong("DataSize", -1L)
                    formats += ResolvedMediaFormat(
                        id = "tt_br_$i",
                        labelAr = qualityLabelForHeight(height),
                        labelEn = if (height > 0) "${height}p MP4" else "MP4",
                        subLabelAr = "بدون علامة مائية • MP4",
                        containerExt = "mp4",
                        mimeType = "video/mp4",
                        resolutionHeight = height,
                        width = pW,
                        bitrateKbps = bObj.optInt("Bitrate", 0) / 1000,
                        streamUrl = streamUrl,
                        exactBytes = dataSize,
                        isApproximateBytes = dataSize <= 0L,
                        headers = headers,
                        badgeAr = "بدون علامة مائية",
                        badgeEn = "No Watermark",
                        hasVideo = true,
                        hasAudio = true
                    )
                }

                val playAddr = videoObj.optString("playAddr").ifBlank { videoObj.optString("downloadAddr") }
                if (playAddr.startsWith("http", true) && !isLikelyAudioOnlyUrl(playAddr) && seenUrls.add(playAddr)) {
                    val size = ResolverHttpUtils.probeContentLength(httpClient, playAddr, headers)
                    formats += ResolvedMediaFormat(
                        id = "tt_hd_nowm",
                        labelAr = qualityLabelForHeight(baseEffH),
                        labelEn = if (baseEffH > 0) "${baseEffH}p MP4" else "MP4",
                        subLabelAr = "بدون علامة مائية • MP4",
                        containerExt = "mp4",
                        mimeType = "video/mp4",
                        resolutionHeight = baseEffH,
                        width = vidW,
                        streamUrl = playAddr,
                        exactBytes = size,
                        isApproximateBytes = size <= 0L,
                        headers = headers,
                        badgeAr = "بدون علامة مائية",
                        badgeEn = "No Watermark",
                        hasVideo = true,
                        hasAudio = true
                    )
                }
            }

            val musicUrl = itemStruct.optJSONObject("music")?.optString("playUrl")
            if (!musicUrl.isNullOrBlank() && musicUrl.startsWith("http", true)) {
                val aSize = ResolverHttpUtils.probeContentLength(httpClient, musicUrl, headers)
                val primaryVidUrl = formats.firstOrNull { !it.isAudioOnly }?.streamUrl
                formats += ResolvedMediaFormat(
                    id = "audio_m4a",
                    labelAr = "صوت فقط • M4A عالي النقاء",
                    labelEn = "M4A",
                    subLabelAr = "صوت أصلي سريع بدون فيديو",
                    containerExt = "m4a",
                    mimeType = "audio/mp4",
                    bitrateKbps = 192,
                    streamUrl = primaryVidUrl ?: musicUrl,
                    formatExpression = "bestaudio/best",
                    exactBytes = aSize,
                    isApproximateBytes = aSize <= 0L,
                    isAudioOnly = true,
                    needsMux = true,
                    headers = headers,
                    badgeAr = "صوت أصلي",
                    badgeEn = "M4A Audio",
                    hasVideo = false,
                    hasAudio = true
                )
                formats += ResolvedMediaFormat(
                    id = "tt_audio",
                    labelAr = "صوت الفيديو فقط • MP3",
                    labelEn = "MP3 Audio",
                    subLabelAr = "صوت أصلي سريع بدون فيديو",
                    containerExt = "mp3",
                    mimeType = "audio/mpeg",
                    bitrateKbps = 320,
                    streamUrl = musicUrl,
                    exactBytes = aSize,
                    isAudioOnly = true,
                    headers = headers,
                    badgeAr = "صوت فقط",
                    badgeEn = "Audio Only",
                    hasVideo = false,
                    hasAudio = true
                )
            }
        }

        if (formats.none { !it.isAudioOnly }) {
            val ogVid = ResolverHttpUtils.extractMetaContent(html, "og:video:secure_url")
                ?: ResolverHttpUtils.extractMetaContent(html, "og:video")
                ?: Regex(""""(?:playAddr|downloadAddr)"\s*:\s*"(https?:[^"]+)"""")
                    .find(html)?.groupValues?.getOrNull(1)?.let {
                        ResolverHttpUtils.decodeHtmlEntities(it.replace("\\u0026", "&"))
                    }
                ?: Regex("""https://[^\s"'<>\\]*tiktokcdn[^\s"'<>\\]*""", RegexOption.IGNORE_CASE)
                    .findAll(html)
                    .map { ResolverHttpUtils.decodeHtmlEntities(it.value) }
                    .firstOrNull { !isLikelyAudioOnlyUrl(it) }
            if (!ogVid.isNullOrBlank() && ogVid.startsWith("http", true) && !isLikelyAudioOnlyUrl(ogVid)) {
                val size = ResolverHttpUtils.probeContentLength(httpClient, ogVid, headers)
                formats += ResolvedMediaFormat(
                    id = "tt_og_video",
                    labelAr = "الجودة المتاحة • MP4",
                    labelEn = "MP4",
                    subLabelAr = "بدون علامة مائية • MP4",
                    containerExt = "mp4",
                    mimeType = "video/mp4",
                    resolutionHeight = 0,
                    streamUrl = ogVid,
                    exactBytes = size,
                    isApproximateBytes = size <= 0L,
                    headers = headers,
                    badgeAr = "بدون علامة مائية",
                    badgeEn = "No Watermark",
                    hasVideo = true,
                    hasAudio = true
                )
            }
        }

        val bestVideoFormat = formats.firstOrNull { !it.isAudioOnly }
        if (bestVideoFormat != null && formats.none { it.isAudioOnly && it.containerExt.equals("m4a", true) }) {
            formats += ResolvedMediaFormat(
                id = "audio_m4a",
                labelAr = "صوت فقط • M4A عالي النقاء",
                labelEn = "M4A",
                subLabelAr = "استخلاص الصوت الأصلي من الفيديو",
                containerExt = "m4a",
                mimeType = "audio/mp4",
                bitrateKbps = 192,
                streamUrl = bestVideoFormat.streamUrl,
                formatExpression = "bestaudio/best",
                isApproximateBytes = true,
                isAudioOnly = true,
                needsMux = true,
                headers = headers,
                badgeAr = "صوت أصلي",
                badgeEn = "M4A Audio",
                hasVideo = false,
                hasAudio = true
            )
        }
        if (bestVideoFormat != null && formats.none { it.isAudioOnly && it.containerExt.equals("mp3", true) }) {
            formats += ResolvedMediaFormat(
                id = "tt_audio",
                labelAr = "صوت الفيديو فقط • MP3",
                labelEn = "MP3 Audio",
                subLabelAr = "تحويل تلقائي متوافق مع كل المشغلات",
                containerExt = "mp3",
                mimeType = "audio/mpeg",
                bitrateKbps = 320,
                streamUrl = bestVideoFormat.streamUrl,
                formatExpression = "bestaudio/best",
                isApproximateBytes = true,
                isAudioOnly = true,
                needsMux = true,
                headers = headers,
                badgeAr = "صوت فقط",
                badgeEn = "MP3 Audio",
                hasVideo = false,
                hasAudio = true
            )
        }

        if (formats.isEmpty()) {
            return ResolvedMediaResult.Failure(
                DownloadError.ExtractionFailed("تيك توك", "No playable TikTok streams found in HTML"),
                diag.toString()
            )
        }

        val sorted = formats.sortedWith(
            compareBy<ResolvedMediaFormat> { it.isAudioOnly }
                .thenByDescending { it.exactBytes.coerceAtLeast(0L) }
                .thenByDescending { it.resolutionHeight }
                .thenByDescending { it.bitrateKbps }
        )
        return ResolvedMediaResult.SingleMedia(
            originalUrl = originalUrl,
            canonicalUrl = canonicalUrl,
            title = title,
            authorOrSource = author,
            sourceDomain = ResolverHttpUtils.normalizeHost(canonicalUrl),
            platform = MediaPlatform.TIKTOK,
            kind = ResolvedMediaKind.SINGLE_VIDEO,
            thumbnailUrl = thumb,
            durationSeconds = duration,
            formats = sorted,
            headers = headers,
            cookiesHeader = cookiesHeader
        )
    }
}

class XTwitterProviderAdapter(
    private val httpClient: OkHttpClient,
    private val ytDlpGateway: YtDlpGateway = NoOpYtDlpGateway
) : MediaProviderAdapter {

    override val platform: MediaPlatform = MediaPlatform.X_TWITTER

    override fun canHandle(canonicalUrl: String, host: String): Boolean {
        val h = host.lowercase(Locale.ROOT)
        return h == "x.com" ||
            h.endsWith(".x.com") ||
            h == "twitter.com" ||
            h.endsWith(".twitter.com") ||
            h == "t.co" ||
            h.contains("twimg.com")
    }

    override suspend fun resolve(canonicalUrl: String, request: ResolveRequest): ResolvedMediaResult {
        val diag = StringBuilder()

        if (ytDlpGateway.isAvailable()) {
            ytDlpGateway.extractSingle(canonicalUrl, request.cookiesFile).onSuccess { info ->
                if (info.formats.isNotEmpty()) {
                    return buildPlatformMediaFromYtDlp(
                        originalUrl = request.rawUrl,
                        canonicalUrl = canonicalUrl,
                        platform = MediaPlatform.X_TWITTER,
                        info = info,
                        cookiesHeader = request.cookiesHeader
                    )
                }
            }.onFailure { diag.appendLine("[yt-dlp-x] ${it.message}") }
        }

        val tweetId = extractTweetId(canonicalUrl)
        if (!tweetId.isNullOrBlank()) {
            val synUrl = "https://cdn.syndication.twimg.com/tweet-result?id=$tweetId&lang=ar"
            val synResp = ResolverHttpUtils.fetchTextWithStatus(
                client = httpClient,
                url = synUrl,
                userAgent = ResolverHttpUtils.DESKTOP_UA
            )
            if (synResp != null && synResp.statusCode < 400 && synResp.body.startsWith("{")) {
                val parsed = parseSyndicationJson(
                    originalUrl = request.rawUrl,
                    canonicalUrl = canonicalUrl,
                    jsonStr = synResp.body,
                    cookiesHeader = request.cookiesHeader
                )
                if (parsed != null) return parsed
            } else if (synResp != null) {
                diag.appendLine("[x-syndication] HTTP ${synResp.statusCode}")
            }
        }

        val htmlResp = ResolverHttpUtils.fetchTextWithStatus(
            client = httpClient,
            url = canonicalUrl,
            cookiesHeader = request.cookiesHeader,
            userAgent = ResolverHttpUtils.DESKTOP_UA
        ) ?: return ResolvedMediaResult.Failure(
            DownloadError.NetworkUnavailable("Failed to connect to X / Twitter"),
            diag.toString()
        )

        if (htmlResp.statusCode >= 400) {
            return ResolvedMediaResult.Failure(
                DownloadErrorMapper.fromHttpStatus(htmlResp.statusCode, htmlResp.finalUrl),
                diag.toString()
            )
        }

        val ogVid = ResolverHttpUtils.extractMetaContent(htmlResp.body, "og:video:secure_url")
            ?: ResolverHttpUtils.extractMetaContent(htmlResp.body, "og:video")
            ?: ResolverHttpUtils.extractMetaContent(htmlResp.body, "twitter:player:stream")

        if (!ogVid.isNullOrBlank() && ogVid.startsWith("http", true)) {
            val isHls = ResolverHttpUtils.isManifestUrl(ogVid)
            val len = if (!isHls) ResolverHttpUtils.probeContentLength(httpClient, ogVid) else -1L
            return ResolvedMediaResult.SingleMedia(
                originalUrl = request.rawUrl,
                canonicalUrl = htmlResp.finalUrl,
                title = ResolverHttpUtils.extractMetaContent(htmlResp.body, "og:title") ?: "منشور X",
                authorOrSource = "X (Twitter)",
                sourceDomain = "x.com",
                platform = MediaPlatform.X_TWITTER,
                kind = if (isHls) ResolvedMediaKind.HLS_STREAM else ResolvedMediaKind.SINGLE_VIDEO,
                thumbnailUrl = ResolverHttpUtils.extractMetaContent(htmlResp.body, "og:image"),
                formats = listOf(
                    ResolvedMediaFormat(
                        id = if (isHls) "x_hls" else "x_og",
                        labelAr = if (isHls) "بث فيديو X (HLS)" else "720p • MP4",
                        labelEn = if (isHls) "HLS" else "720p",
                        subLabelAr = "منصة X",
                        containerExt = "mp4",
                        mimeType = "video/mp4",
                        resolutionHeight = 720,
                        streamUrl = ogVid,
                        exactBytes = len,
                        isHls = isHls,
                        needsMux = isHls
                    )
                ),
                cookiesHeader = request.cookiesHeader
            )
        }

        return ResolvedMediaResult.Failure(
            DownloadError.ExtractionFailed("X (تويتر)", "No playable video variants found in X tweet"),
            diag.toString()
        )
    }

    fun parseSyndicationJson(
        originalUrl: String,
        canonicalUrl: String,
        jsonStr: String,
        cookiesHeader: String? = null
    ): ResolvedMediaResult.SingleMedia? {
        val obj = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return null
        val mediaDetails = obj.optJSONArray("mediaDetails") ?: return null
        val formats = mutableListOf<ResolvedMediaFormat>()
        var thumb: String? = null
        var variantIndex = 0

        for (i in 0 until mediaDetails.length()) {
            val detail = mediaDetails.optJSONObject(i) ?: continue
            if (thumb.isNullOrBlank()) {
                thumb = detail.optString("media_url_https").takeIf { it.isNotBlank() }
            }
            val variants = detail.optJSONObject("video_info")?.optJSONArray("variants") ?: continue
            for (j in 0 until variants.length()) {
                val v = variants.optJSONObject(j) ?: continue
                val url = v.optString("url")
                val cType = v.optString("content_type").lowercase(Locale.ROOT)
                if (!url.startsWith("http", true)) continue
                if (cType.contains("mp4")) {
                    val bitrate = v.optInt("bitrate", 0)
                    val estHeight = when {
                        bitrate >= 2_000_000 -> 1080
                        bitrate >= 800_000 -> 720
                        bitrate >= 400_000 -> 480
                        else -> 360
                    }
                    val size = ResolverHttpUtils.probeContentLength(httpClient, url)
                    formats += ResolvedMediaFormat(
                        id = "x_mp4_${variantIndex++}",
                        labelAr = qualityLabelForHeight(estHeight),
                        labelEn = "${estHeight}p",
                        subLabelAr = "MP4 مباشر • X",
                        containerExt = "mp4",
                        mimeType = "video/mp4",
                        resolutionHeight = estHeight,
                        bitrateKbps = bitrate / 1000,
                        streamUrl = url,
                        exactBytes = size,
                        isApproximateBytes = size <= 0L
                    )
                } else if (cType.contains("mpegurl") || ResolverHttpUtils.isManifestUrl(url)) {
                    formats += ResolvedMediaFormat(
                        id = "x_hls_${variantIndex++}",
                        labelAr = "بث مباشر متكيف (HLS)",
                        labelEn = "HLS",
                        subLabelAr = "دمج HLS تلقائي",
                        containerExt = "mp4",
                        mimeType = "video/mp4",
                        resolutionHeight = 720,
                        streamUrl = url,
                        isHls = true,
                        needsMux = true
                    )
                }
            }
        }

        if (formats.isEmpty()) return null
        val text = obj.optString("text").takeIf { it.isNotBlank() } ?: "فيديو X"
        val user = obj.optJSONObject("user")?.optString("name")?.takeIf { it.isNotBlank() } ?: "X (Twitter)"

        return ResolvedMediaResult.SingleMedia(
            originalUrl = originalUrl,
            canonicalUrl = canonicalUrl,
            title = text.take(80),
            authorOrSource = user,
            sourceDomain = "x.com",
            platform = MediaPlatform.X_TWITTER,
            kind = if (formats.all { it.isHls }) ResolvedMediaKind.HLS_STREAM else ResolvedMediaKind.SINGLE_VIDEO,
            thumbnailUrl = thumb,
            formats = formats.sortedByDescending { it.resolutionHeight },
            cookiesHeader = cookiesHeader
        )
    }

    private fun extractTweetId(url: String): String? =
        Regex("""/status/(\d+)""").find(url)?.groupValues?.getOrNull(1)
}

class PinterestProviderAdapter(
    private val httpClient: OkHttpClient,
    private val ytDlpGateway: YtDlpGateway = NoOpYtDlpGateway
) : MediaProviderAdapter {

    override val platform: MediaPlatform = MediaPlatform.PINTEREST

    override fun canHandle(canonicalUrl: String, host: String): Boolean {
        val h = host.lowercase(Locale.ROOT)
        return h == "pin.it" || h.contains("pinterest.") || h.contains("pinimg.com")
    }

    override suspend fun resolve(canonicalUrl: String, request: ResolveRequest): ResolvedMediaResult {
        val diag = StringBuilder()

        val resp = ResolverHttpUtils.fetchTextWithStatus(
            client = httpClient,
            url = canonicalUrl,
            cookiesHeader = request.cookiesHeader,
            userAgent = request.userAgent ?: ResolverHttpUtils.DESKTOP_UA
        )
        if (resp != null && resp.statusCode < 400 && resp.body.isNotBlank()) {
            val parsed = parsePinterestHtml(
                originalUrl = request.rawUrl,
                canonicalUrl = resp.finalUrl,
                html = resp.body,
                cookiesHeader = request.cookiesHeader,
                diag = diag
            )
            if (parsed is ResolvedMediaResult.SingleMedia) {
                return parsed
            }
        } else if (resp != null) {
            diag.appendLine("[pin-html] HTTP ${resp.statusCode}")
            if (resp.statusCode >= 400 && !ytDlpGateway.isAvailable()) {
                return ResolvedMediaResult.Failure(
                    DownloadErrorMapper.fromHttpStatus(resp.statusCode, resp.finalUrl),
                    diag.toString()
                )
            }
        }

        if (ytDlpGateway.isAvailable()) {
            ytDlpGateway.extractSingle(canonicalUrl, request.cookiesFile).onSuccess { info ->
                if (info.formats.isNotEmpty()) {
                    return buildPlatformMediaFromYtDlp(
                        originalUrl = request.rawUrl,
                        canonicalUrl = canonicalUrl,
                        platform = MediaPlatform.PINTEREST,
                        info = info,
                        cookiesHeader = request.cookiesHeader
                    )
                }
            }.onFailure { diag.appendLine("[yt-dlp-pin] ${it.message}") }
        }

        return ResolvedMediaResult.Failure(
            DownloadError.ExtractionFailed("بنترست", "Could not extract video or original image from Pinterest pin"),
            diag.toString()
        )
    }

    fun parsePinterestHtml(
        originalUrl: String,
        canonicalUrl: String,
        html: String,
        cookiesHeader: String? = null,
        diag: StringBuilder = StringBuilder()
    ): ResolvedMediaResult {
        val title = ResolverHttpUtils.extractMetaContent(html, "og:title")
            ?: ResolverHttpUtils.extractHtmlTitle(html)
            ?: "وسائط بينتيريست"
        val ogImage = ResolverHttpUtils.extractMetaContent(html, "og:image")
        val videoUrls = LinkedHashSet<String>()
        val imageUrls = LinkedHashSet<String>()

        ResolverHttpUtils.extractMetaContent(html, "og:video:secure_url")?.let { videoUrls.add(it) }
        ResolverHttpUtils.extractMetaContent(html, "og:video")?.let { videoUrls.add(it) }

        Regex("""https://v\d*\.pinimg\.com/videos/[^\s"'<>\\]+\.(?:mp4|m3u8)""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEach { videoUrls.add(ResolverHttpUtils.decodeHtmlEntities(it.value)) }

        Regex("""https://i\.pinimg\.com/originals/[^\s"'<>\\]+\.(?:jpg|jpeg|png|webp)""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEach { imageUrls.add(ResolverHttpUtils.decodeHtmlEntities(it.value)) }

        if (!ogImage.isNullOrBlank() && ogImage.startsWith("http", true)) {
            imageUrls.add(ogImage)
        }

        val formats = mutableListOf<ResolvedMediaFormat>()
        videoUrls.filter { it.startsWith("http", true) }.forEachIndexed { idx, vUrl ->
            val isHls = ResolverHttpUtils.isManifestUrl(vUrl)
            val height = when {
                vUrl.contains("1080") -> 1080
                vUrl.contains("720") -> 720
                else -> if (idx == 0) 1080 else 720
            }
            val size = if (!isHls) ResolverHttpUtils.probeContentLength(httpClient, vUrl) else -1L
            formats += ResolvedMediaFormat(
                id = "pin_v_$idx",
                labelAr = qualityLabelForHeight(height),
                labelEn = "${height}p",
                subLabelAr = if (isHls) "بث بنترست (HLS)" else "فيديو بنترست مباشر • MP4",
                containerExt = "mp4",
                mimeType = "video/mp4",
                resolutionHeight = height,
                streamUrl = vUrl,
                exactBytes = size,
                isApproximateBytes = size <= 0L,
                isHls = isHls,
                needsMux = isHls,
                badgeAr = if (height >= 1080) "الأوضح" else null
            )
        }

        val bestImage = imageUrls.firstOrNull { it.contains("/originals/", true) } ?: imageUrls.firstOrNull()
        if (formats.isEmpty() && !bestImage.isNullOrBlank()) {
            val ext = bestImage.substringBefore('?').substringAfterLast('.', "jpg")
                .lowercase(Locale.ROOT)
                .takeIf { it in setOf("jpg", "jpeg", "png", "webp") } ?: "jpg"
            val mime = if (ext == "png") "image/png" else if (ext == "webp") "image/webp" else "image/jpeg"
            val size = ResolverHttpUtils.probeContentLength(httpClient, bestImage)
            formats += ResolvedMediaFormat(
                id = "pin_img_orig",
                labelAr = "صورة عالية الدقة (${ext.uppercase(Locale.ROOT)})",
                labelEn = "Original Image",
                subLabelAr = "حفظ الصورة الأصلية من بنترست",
                containerExt = ext,
                mimeType = mime,
                streamUrl = bestImage,
                exactBytes = size,
                isApproximateBytes = size <= 0L,
                badgeAr = "الأصلية"
            )
        }

        if (formats.isEmpty()) {
            return ResolvedMediaResult.Failure(
                DownloadError.ExtractionFailed("بنترست", "No Pinterest video or image URLs found"),
                diag.toString()
            )
        }

        val kind = when {
            videoUrls.any { ResolverHttpUtils.isManifestUrl(it) } && formats.all { it.isHls } -> ResolvedMediaKind.HLS_STREAM
            videoUrls.isNotEmpty() -> ResolvedMediaKind.SINGLE_VIDEO
            else -> ResolvedMediaKind.IMAGE
        }

        return ResolvedMediaResult.SingleMedia(
            originalUrl = originalUrl,
            canonicalUrl = canonicalUrl,
            title = title,
            authorOrSource = "Pinterest",
            sourceDomain = ResolverHttpUtils.normalizeHost(canonicalUrl),
            platform = MediaPlatform.PINTEREST,
            kind = kind,
            thumbnailUrl = ogImage ?: bestImage,
            formats = formats.sortedByDescending { it.resolutionHeight },
            cookiesHeader = cookiesHeader
        )
    }
}

class DirectAndStreamAdapter(
    private val httpClient: OkHttpClient,
    private val ytDlpGateway: YtDlpGateway = NoOpYtDlpGateway
) : MediaProviderAdapter {

    override val platform: MediaPlatform = MediaPlatform.DIRECT

    override fun canHandle(canonicalUrl: String, host: String): Boolean = true

    override suspend fun resolve(canonicalUrl: String, request: ResolveRequest): ResolvedMediaResult {
        val diag = StringBuilder()
        val host = ResolverHttpUtils.normalizeHost(canonicalUrl)
        val detectedPlatform = detectStudyOrDirectPlatform(host)

        val directAttempt = probeDirectOrManifest(request.rawUrl, canonicalUrl, detectedPlatform, request.cookiesHeader)
        if (directAttempt != null) return directAttempt

        if (ytDlpGateway.isAvailable()) {
            ytDlpGateway.extractSingle(canonicalUrl, request.cookiesFile).onSuccess { info ->
                if (info.formats.isNotEmpty()) {
                    return buildPlatformMediaFromYtDlp(
                        originalUrl = request.rawUrl,
                        canonicalUrl = canonicalUrl,
                        platform = detectedPlatform,
                        info = info,
                        cookiesHeader = request.cookiesHeader
                    )
                }
            }.onFailure { diag.appendLine("[yt-dlp-direct] ${it.message}") }
        }

        val resp = ResolverHttpUtils.fetchTextWithStatus(
            client = httpClient,
            url = canonicalUrl,
            cookiesHeader = request.cookiesHeader,
            userAgent = request.userAgent ?: ResolverHttpUtils.DESKTOP_UA
        ) ?: return ResolvedMediaResult.Failure(
            DownloadError.NetworkUnavailable("Failed to connect to $canonicalUrl"),
            diag.toString()
        )

        if (resp.statusCode >= 400) {
            return ResolvedMediaResult.Failure(
                DownloadErrorMapper.fromHttpStatus(resp.statusCode, resp.finalUrl),
                diag.toString()
            )
        }

        return parseHtmlMedia(
            originalUrl = request.rawUrl,
            canonicalUrl = resp.finalUrl,
            platform = detectedPlatform,
            html = resp.body,
            cookiesHeader = request.cookiesHeader,
            diag = diag
        )
    }

    fun probeDirectOrManifest(
        originalUrl: String,
        canonicalUrl: String,
        detectedPlatform: MediaPlatform,
        cookiesHeader: String? = null
    ): ResolvedMediaResult.SingleMedia? {
        val lowerPath = canonicalUrl.lowercase(Locale.ROOT).substringBefore('?').substringBefore('#')
        if (lowerPath.endsWith(".m3u8") || lowerPath.endsWith(".mpd")) {
            val isDash = lowerPath.endsWith(".mpd")
            return ResolvedMediaResult.SingleMedia(
                originalUrl = originalUrl,
                canonicalUrl = canonicalUrl,
                title = ResolverHttpUtils.deriveTitleFromUrl(canonicalUrl, "بث_وسائط"),
                authorOrSource = ResolverHttpUtils.normalizeHost(canonicalUrl),
                sourceDomain = ResolverHttpUtils.normalizeHost(canonicalUrl),
                platform = detectedPlatform,
                kind = if (isDash) ResolvedMediaKind.DASH_STREAM else ResolvedMediaKind.HLS_STREAM,
                formats = listOf(
                    ResolvedMediaFormat(
                        id = if (isDash) "dash_best" else "hls_best",
                        labelAr = if (isDash) "بث DASH متكيف" else "بث HLS متكيف",
                        labelEn = if (isDash) "DASH" else "HLS",
                        subLabelAr = "معالجة ودمج تلقائي",
                        containerExt = "mp4",
                        mimeType = "video/mp4",
                        resolutionHeight = 1080,
                        streamUrl = canonicalUrl,
                        isHls = !isDash,
                        isDash = isDash,
                        needsMux = true
                    )
                ),
                cookiesHeader = cookiesHeader
            )
        }

        val knownDirectExts = listOf(
            ".mp4", ".mkv", ".webm", ".mov", ".m4v",
            ".mp3", ".m4a", ".aac", ".flac", ".wav", ".ogg", ".opus",
            ".pdf", ".jpg", ".jpeg", ".png", ".webp", ".zip"
        )
        val matchedExt = knownDirectExts.firstOrNull { lowerPath.endsWith(it) }?.removePrefix(".")

        val probeInfo = runCatching {
            val req = Request.Builder().url(canonicalUrl).head()
                .header("User-Agent", ResolverHttpUtils.MOBILE_UA)
                .apply { if (!cookiesHeader.isNullOrBlank()) header("Cookie", cookiesHeader) }
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val cType = resp.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                val cLen = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                Triple(resp.code, cType, cLen)
            }
        }.getOrNull()

        val statusCode = probeInfo?.first ?: 0
        val contentType = probeInfo?.second.orEmpty()
        var contentLen = probeInfo?.third ?: -1L

        val isHtml = contentType.contains("text/html") || contentType.contains("application/xhtml+xml")
        if (isHtml) return null

        val isBinaryFromHeader = contentType.startsWith("video/") ||
            contentType.startsWith("audio/") ||
            contentType.startsWith("image/") ||
            contentType.contains("pdf") ||
            contentType.contains("octet-stream") ||
            contentType.contains("mpegurl") ||
            contentType.contains("dash+xml")

        if (statusCode in 200..299 && (isBinaryFromHeader || matchedExt != null)) {
            if (contentType.contains("mpegurl")) {
                return probeDirectOrManifest(originalUrl, "$canonicalUrl#.m3u8", detectedPlatform, cookiesHeader)
            }
            if (contentLen <= 0L) {
                contentLen = probeRangeTotalLength(canonicalUrl, cookiesHeader)
            }
            return buildDirectBinaryResult(
                originalUrl = originalUrl,
                canonicalUrl = canonicalUrl,
                platform = detectedPlatform,
                contentType = contentType,
                matchedExt = matchedExt,
                contentLen = contentLen,
                cookiesHeader = cookiesHeader
            )
        }

        if (matchedExt != null && (statusCode == 0 || statusCode == 405)) {
            val rangeLen = probeRangeTotalLength(canonicalUrl, cookiesHeader)
            if (rangeLen > 0L) {
                return buildDirectBinaryResult(
                    originalUrl = originalUrl,
                    canonicalUrl = canonicalUrl,
                    platform = detectedPlatform,
                    contentType = "",
                    matchedExt = matchedExt,
                    contentLen = rangeLen,
                    cookiesHeader = cookiesHeader
                )
            }
        }
        return null
    }

    private fun probeRangeTotalLength(url: String, cookiesHeader: String?): Long {
        return runCatching {
            val req = Request.Builder().url(url).get()
                .header("Range", "bytes=0-0")
                .header("User-Agent", ResolverHttpUtils.MOBILE_UA)
                .apply { if (!cookiesHeader.isNullOrBlank()) header("Cookie", cookiesHeader) }
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.header("Content-Type").orEmpty().contains("text/html", true)) return@use -1L
                resp.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                    ?: resp.header("Content-Length")?.toLongOrNull()
                    ?: -1L
            }
        }.getOrDefault(-1L)
    }

    private fun buildDirectBinaryResult(
        originalUrl: String,
        canonicalUrl: String,
        platform: MediaPlatform,
        contentType: String,
        matchedExt: String?,
        contentLen: Long,
        cookiesHeader: String?
    ): ResolvedMediaResult.SingleMedia {
        val ext = matchedExt ?: when {
            contentType.contains("audio/mpeg") -> "mp3"
            contentType.startsWith("audio/") -> "m4a"
            contentType.contains("pdf") -> "pdf"
            contentType.startsWith("image/png") -> "png"
            contentType.startsWith("image/webp") -> "webp"
            contentType.startsWith("image/") -> "jpg"
            contentType.contains("webm") -> "webm"
            contentType.contains("matroska") -> "mkv"
            else -> "mp4"
        }
        val isAudio = contentType.startsWith("audio/") || ext in setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus")
        val isPdf = ext == "pdf" || contentType.contains("pdf")
        val isImage = ext in setOf("jpg", "jpeg", "png", "webp") || contentType.startsWith("image/")
        val kind = when {
            isAudio -> ResolvedMediaKind.AUDIO_ONLY
            isPdf -> ResolvedMediaKind.DOCUMENT
            isImage -> ResolvedMediaKind.IMAGE
            else -> ResolvedMediaKind.DIRECT_FILE
        }
        val effectiveMime = contentType.substringBefore(';').trim().ifBlank {
            when {
                isPdf -> "application/pdf"
                isImage -> "image/$ext"
                isAudio && ext == "mp3" -> "audio/mpeg"
                isAudio -> "audio/mp4"
                else -> "video/mp4"
            }
        }
        val labelAr = when {
            isPdf -> "مستند PDF مباشر"
            isImage -> "صورة عالية الدقة (${ext.uppercase(Locale.ROOT)})"
            isAudio -> "ملف صوتي مباشر (${ext.uppercase(Locale.ROOT)})"
            else -> "تحميل مباشر سريع (${ext.uppercase(Locale.ROOT)})"
        }

        return ResolvedMediaResult.SingleMedia(
            originalUrl = originalUrl,
            canonicalUrl = canonicalUrl,
            title = ResolverHttpUtils.deriveTitleFromUrl(canonicalUrl, "ملف"),
            authorOrSource = ResolverHttpUtils.normalizeHost(canonicalUrl),
            sourceDomain = ResolverHttpUtils.normalizeHost(canonicalUrl),
            platform = platform,
            kind = kind,
            formats = listOf(
                ResolvedMediaFormat(
                    id = "direct_$ext",
                    labelAr = labelAr,
                    labelEn = ext.uppercase(Locale.ROOT),
                    subLabelAr = "تحميل متجزئ سريع",
                    containerExt = ext,
                    mimeType = effectiveMime,
                    resolutionHeight = if (isAudio || isPdf || isImage) 0 else 1080,
                    streamUrl = canonicalUrl,
                    exactBytes = contentLen,
                    isApproximateBytes = contentLen <= 0L,
                    isAudioOnly = isAudio,
                    badgeAr = "مباشر",
                    badgeEn = "Direct"
                )
            ),
            cookiesHeader = cookiesHeader
        )
    }

    fun parseHtmlMedia(
        originalUrl: String,
        canonicalUrl: String,
        platform: MediaPlatform,
        html: String,
        cookiesHeader: String? = null,
        diag: StringBuilder = StringBuilder()
    ): ResolvedMediaResult {
        val title = ResolverHttpUtils.extractMetaContent(html, "og:title")
            ?: ResolverHttpUtils.extractMetaContent(html, "twitter:title")
            ?: ResolverHttpUtils.extractHtmlTitle(html)
            ?: ResolverHttpUtils.deriveTitleFromUrl(canonicalUrl)
        val author = ResolverHttpUtils.extractMetaContent(html, "og:site_name")
            ?: ResolverHttpUtils.normalizeHost(canonicalUrl)
        val thumb = ResolverHttpUtils.extractMetaContent(html, "og:image")
            ?: ResolverHttpUtils.extractMetaContent(html, "twitter:image")

        val candidates = LinkedHashSet<String>()
        listOf("og:video:secure_url", "og:video:url", "og:video", "twitter:player:stream").forEach { prop ->
            ResolverHttpUtils.extractMetaContent(html, prop)?.let { candidates.add(it) }
        }
        Regex("""<source[^>]+src=["']([^"']+\.(?:mp4|webm|m4a|mp3|m3u8|mpd)[^"']*)["']""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEach { candidates.add(ResolverHttpUtils.resolveRelativeUrl(canonicalUrl, it.groupValues[1])) }
        Regex("""https?://[^\s"'<>\\]+\.(?:mp4|m4a|mp3|m3u8|pdf)(?:\?[^\s"'<>\\]*)?""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .take(8)
            .forEach { candidates.add(ResolverHttpUtils.decodeHtmlEntities(it.value)) }

        val validCandidates = candidates.filter { it.startsWith("http", true) }
        if (validCandidates.isEmpty()) {
            return ResolvedMediaResult.Failure(
                DownloadError.UnsupportedMedia(
                    mediaType = "text/html",
                    technicalDetail = "No playable video, audio, or document links found in $canonicalUrl"
                ),
                diag.toString()
            )
        }

        val formats = validCandidates.mapIndexed { idx, candidate ->
            val lower = candidate.lowercase(Locale.ROOT)
            val isHls = ResolverHttpUtils.isManifestUrl(lower)
            val isAudio = lower.contains(".mp3") || lower.contains(".m4a")
            val isPdf = lower.contains(".pdf")
            val ext = when {
                lower.contains(".mp3") -> "mp3"
                lower.contains(".m4a") -> "m4a"
                lower.contains(".pdf") -> "pdf"
                lower.contains(".webm") -> "webm"
                else -> "mp4"
            }
            val mime = when (ext) {
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "pdf" -> "application/pdf"
                "webm" -> "video/webm"
                else -> "video/mp4"
            }
            val size = if (!isHls) ResolverHttpUtils.probeContentLength(httpClient, candidate) else -1L
            ResolvedMediaFormat(
                id = "html_${ext}_$idx",
                labelAr = when {
                    isPdf -> "مستند PDF"
                    isAudio -> "صوت المحاضرة (${ext.uppercase(Locale.ROOT)})"
                    isHls -> "بث متكيف (HLS)"
                    else -> "ملف فيديو (${ext.uppercase(Locale.ROOT)})"
                },
                labelEn = ext.uppercase(Locale.ROOT),
                subLabelAr = if (isHls) "دمج HLS تلقائي" else "مستخلص من الصفحة",
                containerExt = ext,
                mimeType = mime,
                resolutionHeight = 0,
                streamUrl = candidate,
                exactBytes = size,
                isApproximateBytes = size <= 0L,
                isAudioOnly = isAudio,
                isHls = isHls,
                needsMux = isHls
            )
        }

        return ResolvedMediaResult.SingleMedia(
            originalUrl = originalUrl,
            canonicalUrl = canonicalUrl,
            title = title,
            authorOrSource = author,
            sourceDomain = ResolverHttpUtils.normalizeHost(canonicalUrl),
            platform = platform,
            kind = if (formats.any { it.isHls }) ResolvedMediaKind.HLS_STREAM else ResolvedMediaKind.SINGLE_VIDEO,
            thumbnailUrl = thumb,
            formats = formats,
            cookiesHeader = cookiesHeader
        )
    }

    private fun detectStudyOrDirectPlatform(host: String): MediaPlatform {
        val h = host.lowercase(Locale.ROOT)
        return if (h.contains("khanacademy.org") || h.contains("coursera.org") ||
            h.contains("udemy.com") || h.contains("edx.org") ||
            h.contains("nafham.com") || h.contains("noonacademy") ||
            h.contains("drive.google.com") || h.contains("archive.org")
        ) {
            MediaPlatform.STUDY_PLATFORM
        } else {
            MediaPlatform.DIRECT
        }
    }
}
