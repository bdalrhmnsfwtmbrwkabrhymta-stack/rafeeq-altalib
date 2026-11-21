package com.example.download.core.resolver

import com.example.download.core.model.DownloadError
import com.example.download.core.model.DownloadErrorMapper
import com.example.download.core.model.MediaPlatform
import com.example.download.core.model.ResolveRequest
import com.example.download.core.model.ResolvedCollectionEntry
import com.example.download.core.model.ResolvedMediaFormat
import com.example.download.core.model.ResolvedMediaKind
import com.example.download.core.model.ResolvedMediaResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.util.Locale

class YouTubeProviderAdapter(
    private val httpClient: OkHttpClient,
    private val ytDlpGateway: YtDlpGateway = NoOpYtDlpGateway
) : MediaProviderAdapter {

    override val platform: MediaPlatform = MediaPlatform.YOUTUBE

    override fun canHandle(canonicalUrl: String, host: String): Boolean {
        val h = host.lowercase(Locale.ROOT)
        return h == "youtu.be" ||
            h == "youtube.com" ||
            h.endsWith(".youtube.com") ||
            h.contains("youtube-nocookie.com")
    }

    override suspend fun resolve(canonicalUrl: String, request: ResolveRequest): ResolvedMediaResult {
        val diag = StringBuilder()
        val listId = ResolverHttpUtils.extractQueryParam(canonicalUrl, "list")
        val videoId = extractVideoId(canonicalUrl)
        val isChannel = isChannelUrl(canonicalUrl)

        if (request.checkCollection && isChannel) {
            return resolveChannel(canonicalUrl, request, diag)
        }

        if (request.checkCollection && !listId.isNullOrBlank() && (canonicalUrl.contains("/playlist", true) || videoId.isNullOrBlank())) {
            return resolvePlaylist(canonicalUrl, listId, request, diag)
        }

        if (videoId.isNullOrBlank()) {
            // Try channel resolution as a secondary check before declaring unsupported
            val channelAttempt = if (request.checkCollection) resolveChannel(canonicalUrl, request, diag) else null
            if (channelAttempt is ResolvedMediaResult.MediaCollection) return channelAttempt
            return ResolvedMediaResult.Failure(
                error = DownloadError.UnsupportedMedia(
                    mediaType = "youtube-page",
                    technicalDetail = "Could not identify YouTube video, playlist, or channel ID in $canonicalUrl"
                ),
                diagnosticLog = diag.toString()
            )
        }

        val normalizedWatchUrl = "https://www.youtube.com/watch?v=$videoId"

        // 1. Primary path: yt-dlp gateway (deciphers JS signature/n-challenge on device)
        if (ytDlpGateway.isAvailable()) {
            val ytDlpRes = ytDlpGateway.extractSingle(normalizedWatchUrl, request.cookiesFile)
            ytDlpRes.onSuccess { info ->
                if (info.formats.isNotEmpty()) {
                    var single = ResolvedMediaResult.SingleMedia(
                        originalUrl = request.rawUrl,
                        canonicalUrl = normalizedWatchUrl,
                        title = info.title,
                        authorOrSource = info.uploader ?: "YouTube",
                        sourceDomain = "youtube.com",
                        platform = MediaPlatform.YOUTUBE,
                        kind = if (info.formats.all { it.isAudioOnly }) ResolvedMediaKind.AUDIO_ONLY else ResolvedMediaKind.SINGLE_VIDEO,
                        thumbnailUrl = info.thumbnailUrl ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                        durationSeconds = info.durationSeconds,
                        formats = info.formats,
                        headers = info.baseHeaders,
                        cookiesHeader = request.cookiesHeader
                    )
                    if (request.checkCollection && !listId.isNullOrBlank()) {
                        val pl = resolvePlaylist(canonicalUrl, listId, request, diag)
                        if (pl is ResolvedMediaResult.MediaCollection && pl.entries.size >= 2) {
                            return ResolvedMediaResult.MediaCollection(
                                originalUrl = request.rawUrl,
                                canonicalUrl = pl.canonicalUrl,
                                title = pl.title,
                                authorOrSource = pl.authorOrSource,
                                platform = MediaPlatform.YOUTUBE,
                                kind = ResolvedMediaKind.PLAYLIST,
                                entries = pl.entries,
                                cookiesHeader = request.cookiesHeader
                            )
                        }
                    }
                    return single
                }
            }.onFailure { err ->
                diag.appendLine("[yt-dlp] ${err.message}")
                val mapped = DownloadErrorMapper.fromThrowable(err, "YouTube")
                if (mapped is DownloadError.ProtectedContent) {
                    return ResolvedMediaResult.Failure(mapped, diag.toString())
                }
            }
        }

        // 2. Fallback path: Innertube Player API + HTML ytInitialPlayerResponse
        val playerResult = fetchInnertubeOrHtmlPlayer(normalizedWatchUrl, videoId, request.cookiesHeader, diag)
        if (playerResult != null) {
            return parsePlayerJsonResult(
                originalUrl = request.rawUrl,
                canonicalUrl = normalizedWatchUrl,
                videoId = videoId,
                playerJson = playerResult,
                cookiesHeader = request.cookiesHeader,
                diag = diag
            )
        }

        val err = if (diag.contains("UnknownHostException", true) || diag.contains("ConnectException", true)) {
            DownloadError.NetworkUnavailable(diag.toString().ifBlank { "Network unreachable" })
        } else if (diag.contains("timed out", true)) {
            DownloadError.NetworkTimeout(diag.toString())
        } else {
            DownloadError.ExtractionFailed(
                platformName = "يوتيوب",
                technicalDetail = diag.toString().ifBlank { "No playable YouTube streams extracted for $videoId" }
            )
        }
        return ResolvedMediaResult.Failure(err, diag.toString())
    }

    private suspend fun resolvePlaylist(
        canonicalUrl: String,
        listId: String,
        request: ResolveRequest,
        diag: StringBuilder
    ): ResolvedMediaResult {
        val playlistUrl = "https://www.youtube.com/playlist?list=$listId"
        if (ytDlpGateway.isAvailable()) {
            val entriesRes = ytDlpGateway.extractCollection(playlistUrl, request.cookiesFile, maxItems = 100)
            entriesRes.onSuccess { entries ->
                if (entries.isNotEmpty()) {
                    return ResolvedMediaResult.MediaCollection(
                        originalUrl = request.rawUrl,
                        canonicalUrl = playlistUrl,
                        title = "قائمة تشغيل (${entries.size} عنصر)",
                        authorOrSource = "YouTube Playlist",
                        platform = MediaPlatform.YOUTUBE,
                        kind = ResolvedMediaKind.PLAYLIST,
                        entries = entries,
                        cookiesHeader = request.cookiesHeader
                    )
                }
            }.onFailure { diag.appendLine("[yt-dlp-playlist] ${it.message}") }
        }

        val resp = ResolverHttpUtils.fetchTextWithStatus(
            client = httpClient,
            url = playlistUrl,
            cookiesHeader = request.cookiesHeader,
            userAgent = ResolverHttpUtils.DESKTOP_UA
        ) ?: return ResolvedMediaResult.Failure(
            DownloadError.NetworkUnavailable("Failed to fetch YouTube playlist page $playlistUrl"),
            diag.toString()
        )

        if (resp.statusCode >= 400) {
            return ResolvedMediaResult.Failure(
                DownloadErrorMapper.fromHttpStatus(resp.statusCode, playlistUrl),
                diag.toString()
            )
        }

        val initialData = ResolverHttpUtils.extractJsonObjectFromHtml(resp.body, "var ytInitialData = ")
            ?: ResolverHttpUtils.extractJsonObjectFromHtml(resp.body, "ytInitialData = ")
            ?: return ResolvedMediaResult.Failure(
                DownloadError.ExtractionFailed("يوتيوب", "Missing ytInitialData in playlist page"),
                diag.toString()
            )

        return parsePlaylistInitialData(request.rawUrl, playlistUrl, listId, initialData, request.cookiesHeader)
    }

    private suspend fun resolveChannel(
        canonicalUrl: String,
        request: ResolveRequest,
        diag: StringBuilder
    ): ResolvedMediaResult {
        val channelUrl = normalizeChannelVideosUrl(canonicalUrl)
        if (ytDlpGateway.isAvailable()) {
            val entriesRes = ytDlpGateway.extractCollection(channelUrl, request.cookiesFile, maxItems = 50)
            entriesRes.onSuccess { entries ->
                if (entries.isNotEmpty()) {
                    return ResolvedMediaResult.MediaCollection(
                        originalUrl = request.rawUrl,
                        canonicalUrl = channelUrl,
                        title = "قناة يوتيوب (${entries.size} فيديو)",
                        authorOrSource = "YouTube Channel",
                        platform = MediaPlatform.YOUTUBE,
                        kind = ResolvedMediaKind.CHANNEL,
                        entries = entries,
                        cookiesHeader = request.cookiesHeader
                    )
                }
            }.onFailure { diag.appendLine("[yt-dlp-channel] ${it.message}") }
        }

        val resp = ResolverHttpUtils.fetchTextWithStatus(
            client = httpClient,
            url = channelUrl,
            cookiesHeader = request.cookiesHeader,
            userAgent = ResolverHttpUtils.DESKTOP_UA
        ) ?: return ResolvedMediaResult.Failure(
            DownloadError.NetworkUnavailable("Failed to fetch YouTube channel page $channelUrl"),
            diag.toString()
        )

        if (resp.statusCode >= 400) {
            return ResolvedMediaResult.Failure(
                DownloadErrorMapper.fromHttpStatus(resp.statusCode, channelUrl),
                diag.toString()
            )
        }

        val initialData = ResolverHttpUtils.extractJsonObjectFromHtml(resp.body, "var ytInitialData = ")
            ?: ResolverHttpUtils.extractJsonObjectFromHtml(resp.body, "ytInitialData = ")
            ?: return ResolvedMediaResult.Failure(
                DownloadError.ExtractionFailed("يوتيوب", "Missing ytInitialData in channel page"),
                diag.toString()
            )

        return parseChannelInitialData(request.rawUrl, channelUrl, initialData, request.cookiesHeader)
    }

    fun parsePlaylistInitialData(
        originalUrl: String,
        canonicalUrl: String,
        listId: String,
        initialData: JSONObject,
        cookiesHeader: String? = null
    ): ResolvedMediaResult {
        val title = initialData.optJSONObject("metadata")
            ?.optJSONObject("playlistMetadataRenderer")
            ?.optString("title")
            ?.takeIf { it.isNotBlank() } ?: "قائمة تشغيل يوتيوب ($listId)"

        val entries = mutableListOf<ResolvedCollectionEntry>()
        collectVideoRenderers(initialData, entries, includePlaylistRenderer = true, includeChannelRenderers = false)
        if (entries.isEmpty()) {
            return ResolvedMediaResult.Failure(
                DownloadError.PrivateContent("يوتيوب", "Playlist $listId is empty or private"),
                "Empty playlistVideoRenderer list"
            )
        }
        return ResolvedMediaResult.MediaCollection(
            originalUrl = originalUrl,
            canonicalUrl = canonicalUrl,
            title = title,
            authorOrSource = "YouTube Playlist • ${entries.size}",
            platform = MediaPlatform.YOUTUBE,
            kind = ResolvedMediaKind.PLAYLIST,
            entries = entries,
            cookiesHeader = cookiesHeader
        )
    }

    fun parseChannelInitialData(
        originalUrl: String,
        canonicalUrl: String,
        initialData: JSONObject,
        cookiesHeader: String? = null
    ): ResolvedMediaResult {
        val title = initialData.optJSONObject("metadata")
            ?.optJSONObject("channelMetadataRenderer")
            ?.optString("title")
            ?.takeIf { it.isNotBlank() } ?: "قناة يوتيوب"

        val entries = mutableListOf<ResolvedCollectionEntry>()
        collectVideoRenderers(initialData, entries, includePlaylistRenderer = true, includeChannelRenderers = true)
        if (entries.isEmpty()) {
            return ResolvedMediaResult.Failure(
                DownloadError.ExtractionFailed("يوتيوب", "No videos found on channel page $canonicalUrl"),
                "Empty channel video renderers"
            )
        }
        return ResolvedMediaResult.MediaCollection(
            originalUrl = originalUrl,
            canonicalUrl = canonicalUrl,
            title = "$title (${entries.size} فيديو)",
            authorOrSource = title,
            platform = MediaPlatform.YOUTUBE,
            kind = ResolvedMediaKind.CHANNEL,
            entries = entries,
            cookiesHeader = cookiesHeader
        )
    }

    fun parsePlayerJsonResult(
        originalUrl: String,
        canonicalUrl: String,
        videoId: String,
        playerJson: JSONObject,
        cookiesHeader: String? = null,
        diag: StringBuilder = StringBuilder()
    ): ResolvedMediaResult {
        val playability = playerJson.optJSONObject("playabilityStatus")
        val status = playability?.optString("status").orEmpty().uppercase(Locale.ROOT)
        val reason = playability?.optString("reason").orEmpty()
        if (status == "LOGIN_REQUIRED" || reason.contains("sign in", true)) {
            return ResolvedMediaResult.Failure(
                DownloadError.AuthenticationRequired("يوتيوب", reason.ifBlank { "LOGIN_REQUIRED" }),
                diag.toString()
            )
        }
        if (status == "ERROR" || status == "UNPLAYABLE") {
            val lowerReason = reason.lowercase(Locale.ROOT)
            val err = when {
                lowerReason.contains("private") || lowerReason.contains("unavailable") || lowerReason.contains("removed") ->
                    DownloadError.PrivateContent("يوتيوب", reason)
                lowerReason.contains("members") || lowerReason.contains("purchase") || lowerReason.contains("paid") ->
                    DownloadError.ProtectedContent(reason)
                else ->
                    DownloadError.ExtractionFailed("يوتيوب", reason.ifBlank { status })
            }
            return ResolvedMediaResult.Failure(err, diag.toString())
        }

        val streamingData = playerJson.optJSONObject("streamingData")
        if (streamingData == null) {
            return ResolvedMediaResult.Failure(
                DownloadError.ExtractionFailed("يوتيوب", "Missing streamingData for video $videoId"),
                diag.toString()
            )
        }

        val videoDetails = playerJson.optJSONObject("videoDetails")
        val title = videoDetails?.optString("title")?.takeIf { it.isNotBlank() } ?: "فيديو يوتيوب ($videoId)"
        val author = videoDetails?.optString("author")?.takeIf { it.isNotBlank() } ?: "YouTube"
        val durationSec = videoDetails?.optString("lengthSeconds")?.toIntOrNull()
            ?: videoDetails?.optInt("lengthSeconds", 0) ?: 0
        val thumbnails = videoDetails?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumbUrl = if (thumbnails != null && thumbnails.length() > 0) {
            thumbnails.optJSONObject(thumbnails.length() - 1)?.optString("url")
        } else {
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        }

        val muxedFormats = streamingData.optJSONArray("formats") ?: JSONArray()
        val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: JSONArray()

        data class InnertubeAudioCandidate(
            val json: JSONObject,
            val url: String,
            val bytes: Long,
            val bitrate: Int,
            val isMp4: Boolean,
            val langCode: String,
            val displayName: String,
            val isDefault: Boolean,
            val isDubbed: Boolean,
            val codec: String
        )

        val audioStreams = mutableListOf<InnertubeAudioCandidate>()
        for (i in 0 until adaptiveFormats.length()) {
            val f = adaptiveFormats.optJSONObject(i) ?: continue
            val mime = f.optString("mimeType")
            if (!mime.startsWith("audio/mp4") && !mime.startsWith("audio/webm")) continue
            val resolvedUrl = resolveDirectStreamUrl(f) ?: continue
            f.put("_resolvedUrl", resolvedUrl)
            val audioTrack = f.optJSONObject("audioTrack")
            val rawTrackId = audioTrack?.optString("id").orEmpty()
            val langCode = rawTrackId.substringBefore('.').trim().lowercase(Locale.ROOT)
            val displayName = audioTrack?.optString("displayName").orEmpty().trim()
            val isDefault = audioTrack?.optBoolean("audioIsDefault", false) ?: false
            val isAutoDubbed = f.optBoolean("isAutoDubbed", false) ||
                displayName.contains("dubbed", ignoreCase = true) ||
                displayName.contains("تلقائي", ignoreCase = true) ||
                rawTrackId.contains(".auto", ignoreCase = true)
            val codec = mime.substringAfter("codecs=\"").substringBefore("\"").ifBlank { mime.substringAfter('/') }
            audioStreams.add(
                InnertubeAudioCandidate(
                    json = f,
                    url = resolvedUrl,
                    bytes = f.optString("contentLength").toLongOrNull() ?: -1L,
                    bitrate = f.optInt("bitrate", 0),
                    isMp4 = mime.startsWith("audio/mp4"),
                    langCode = langCode,
                    displayName = displayName,
                    isDefault = isDefault,
                    isDubbed = isAutoDubbed,
                    codec = codec
                )
            )
        }

        val distinctAudioByLang = audioStreams
            .groupBy { "${it.langCode.ifBlank { "default" }}_${it.isDubbed}" }
            .values
            .mapNotNull { list ->
                list.maxByOrNull { (if (it.isMp4) 1_000_000 else 0) + it.bitrate }
            }
            .sortedWith(
                compareByDescending<InnertubeAudioCandidate> { it.isDefault }
                    .thenByDescending { !it.isDubbed }
                    .thenByDescending { it.langCode == "ar" }
                    .thenByDescending { it.isMp4 }
                    .thenByDescending { it.bitrate }
            )

        val primaryAudio = distinctAudioByLang.firstOrNull()
        val bestAudioUrl = primaryAudio?.url?.takeIf { it.isNotBlank() }
        val bestAudioBytes = primaryAudio?.bytes ?: -1L

        fun audioLanguageLabel(track: InnertubeAudioCandidate?): String {
            if (track == null) return "الأصلي"
            val base = track.displayName.takeIf { it.isNotBlank() }
                ?: when {
                    track.langCode.startsWith("ar") -> "العربية"
                    track.langCode.startsWith("en") -> "الإنجليزية"
                    track.langCode.isNotBlank() -> track.langCode.uppercase(Locale.ROOT)
                    else -> "الأصلي"
                }
            return if (track.isDubbed && !base.contains("مدبلج")) "$base (مدبلج)" else base
        }

        val optionsList = mutableListOf<ResolvedMediaFormat>()
        val heightsSeen = linkedSetOf<Int>()

        if (!bestAudioUrl.isNullOrBlank()) {
            val adaptiveVideoCandidates = buildList {
                for (i in 0 until adaptiveFormats.length()) {
                    val f = adaptiveFormats.optJSONObject(i) ?: continue
                    val mime = f.optString("mimeType")
                    if (!mime.startsWith("video/")) continue
                    val streamUrl = f.optString("_resolvedUrl").ifBlank { resolveDirectStreamUrl(f) ?: continue }
                    val height = f.optInt("height", 0)
                    if (height <= 0) continue
                    add(Triple(f, streamUrl, height))
                }
            }.sortedWith(
                compareByDescending<Triple<JSONObject, String, Int>> { it.third }
                    .thenByDescending { it.first.optString("mimeType").startsWith("video/mp4") }
                    .thenByDescending { it.first.optInt("bitrate", 0) }
            )

            for ((f, streamUrl, height) in adaptiveVideoCandidates) {
                if (!heightsSeen.add(height)) continue
                val mime = f.optString("mimeType")
                val vcodec = mime.substringAfter("codecs=\"").substringBefore("\"").ifBlank { mime.substringAfter('/') }
                val vSize = f.optString("contentLength").toLongOrNull() ?: -1L
                val combinedSize = when {
                    vSize > 0L && bestAudioBytes > 0L -> vSize + bestAudioBytes
                    vSize > 0L -> vSize
                    else -> -1L
                }
                val primaryLang = audioLanguageLabel(primaryAudio)
                optionsList += ResolvedMediaFormat(
                    id = "${height}p",
                    labelAr = qualityLabelAr(height),
                    labelEn = "${height}p",
                    subLabelAr = "دمج فيديو + صوت (FFmpeg)",
                    containerExt = "mp4",
                    mimeType = "video/mp4",
                    resolutionHeight = height,
                    bitrateKbps = f.optInt("bitrate", 0) / 1000,
                    streamUrl = streamUrl,
                    audioStreamUrl = bestAudioUrl,
                    formatExpression = "${f.optInt("itag", 0).takeIf { it > 0 } ?: "bestvideo[height<=$height]"}+${primaryAudio?.json?.optInt("itag", 0)?.takeIf { it > 0 } ?: "bestaudio"}",
                    exactBytes = combinedSize,
                    isApproximateBytes = combinedSize <= 0L,
                    isAudioOnly = false,
                    needsMux = true,
                    badgeAr = badgeArForHeight(height),
                    badgeEn = badgeEnForHeight(height),
                    width = f.optInt("width", 0),
                    fps = f.optInt("fps", 0),
                    vcodec = vcodec,
                    acodec = primaryAudio?.codec.orEmpty(),
                    languageCode = primaryAudio?.langCode.orEmpty(),
                    languageLabel = primaryLang,
                    isAutoDubbed = primaryAudio?.isDubbed ?: false,
                    isOriginalAudio = primaryAudio?.isDefault ?: true,
                    hasVideo = true,
                    hasAudio = true
                )

                if (distinctAudioByLang.size > 1 && height >= 720) {
                    for (altTrack in distinctAudioByLang.drop(1)) {
                        val altCode = altTrack.langCode.ifBlank { "alt" }
                        val altLabel = audioLanguageLabel(altTrack)
                        val altBytes = if (vSize > 0L && altTrack.bytes > 0L) vSize + altTrack.bytes else vSize
                        optionsList += ResolvedMediaFormat(
                            id = "${height}p_${altCode}",
                            labelAr = "${qualityLabelAr(height)} • $altLabel",
                            labelEn = "${height}p ($altCode)",
                            subLabelAr = "دمج فيديو + صوت $altLabel (FFmpeg)",
                            containerExt = "mp4",
                            mimeType = "video/mp4",
                            resolutionHeight = height,
                            bitrateKbps = f.optInt("bitrate", 0) / 1000,
                            streamUrl = streamUrl,
                            audioStreamUrl = altTrack.url,
                            formatExpression = "${f.optInt("itag", 0).takeIf { it > 0 } ?: "bestvideo[height<=$height]"}+${altTrack.json.optInt("itag", 0).takeIf { it > 0 } ?: "bestaudio"}",
                            exactBytes = altBytes,
                            isApproximateBytes = altBytes <= 0L,
                            isAudioOnly = false,
                            needsMux = true,
                            badgeAr = if (altCode.startsWith("ar")) "مدبلج عربي" else altCode.uppercase(Locale.ROOT),
                            badgeEn = altCode.uppercase(Locale.ROOT),
                            width = f.optInt("width", 0),
                            fps = f.optInt("fps", 0),
                            vcodec = vcodec,
                            acodec = altTrack.codec,
                            languageCode = altCode,
                            languageLabel = altLabel,
                            isAutoDubbed = altTrack.isDubbed,
                            isOriginalAudio = altTrack.isDefault,
                            hasVideo = true,
                            hasAudio = true
                        )
                    }
                }
            }
        }

        for (i in 0 until muxedFormats.length()) {
            val f = muxedFormats.optJSONObject(i) ?: continue
            val mime = f.optString("mimeType")
            if (!mime.startsWith("video/")) continue
            val streamUrl = resolveDirectStreamUrl(f) ?: continue
            val height = f.optInt("height", 360)
            val size = f.optString("contentLength").toLongOrNull() ?: -1L
            optionsList.removeAll { it.id == "${height}p" }
            optionsList += ResolvedMediaFormat(
                id = "${height}p",
                labelAr = qualityLabelAr(height),
                labelEn = "${height}p",
                subLabelAr = "فيديو + صوت • تحميل فائق متعدد الأجزاء",
                containerExt = "mp4",
                mimeType = "video/mp4",
                resolutionHeight = height,
                bitrateKbps = f.optInt("bitrate", 0) / 1000,
                streamUrl = streamUrl,
                audioStreamUrl = null,
                formatExpression = f.optInt("itag", 0).takeIf { it > 0 }?.toString() ?: "best[height<=$height]",
                exactBytes = size,
                isApproximateBytes = size <= 0L,
                isAudioOnly = false,
                needsMux = false,
                badgeAr = badgeArForHeight(height),
                badgeEn = badgeEnForHeight(height),
                width = f.optInt("width", 0),
                fps = f.optInt("fps", 0),
                languageLabel = "الأصلي",
                hasVideo = true,
                hasAudio = true
            )
        }

        val hlsManifest = streamingData.optString("hlsManifestUrl").takeIf { it.startsWith("http") }
        val sortedFormats = optionsList.sortedWith(
            compareByDescending<ResolvedMediaFormat> { it.resolutionHeight }
                .thenBy { it.isAutoDubbed }
        ).toMutableList()

        if (sortedFormats.isEmpty() && !hlsManifest.isNullOrBlank()) {
            sortedFormats += ResolvedMediaFormat(
                id = "best",
                labelAr = "بث مباشر متكيف (HLS)",
                labelEn = "HLS Adaptive",
                subLabelAr = "بث مجزأ + دمج تلقائي",
                containerExt = "mp4",
                mimeType = "video/mp4",
                resolutionHeight = 1080,
                streamUrl = hlsManifest,
                isHls = true,
                needsMux = true
            )
        }

        if (!bestAudioUrl.isNullOrBlank()) {
            sortedFormats += ResolvedMediaFormat(
                id = "audio_m4a",
                labelAr = "صوت فقط • M4A عالي النقاء",
                labelEn = "M4A",
                subLabelAr = "صوت أصلي سريع بدون فيديو",
                containerExt = "m4a",
                mimeType = "audio/mp4",
                bitrateKbps = (primaryAudio?.bitrate ?: 128000) / 1000,
                streamUrl = bestAudioUrl,
                formatExpression = "bestaudio/best",
                exactBytes = bestAudioBytes,
                isAudioOnly = true,
                needsMux = false,
                badgeAr = "صوت أصلي",
                badgeEn = "Original Audio",
                acodec = primaryAudio?.codec.orEmpty(),
                languageCode = primaryAudio?.langCode.orEmpty(),
                languageLabel = audioLanguageLabel(primaryAudio),
                hasVideo = false,
                hasAudio = true
            )
            distinctAudioByLang.drop(1).forEach { altAudio ->
                val altLabel = audioLanguageLabel(altAudio)
                val altCode = altAudio.langCode.ifBlank { "alt" }
                sortedFormats += ResolvedMediaFormat(
                    id = "audio_m4a_$altCode",
                    labelAr = "صوت ($altLabel) • M4A",
                    labelEn = "M4A ($altCode)",
                    subLabelAr = "مسار صوتي بلغة $altLabel",
                    containerExt = "m4a",
                    mimeType = "audio/mp4",
                    bitrateKbps = altAudio.bitrate / 1000,
                    streamUrl = altAudio.url,
                    formatExpression = altAudio.json.optInt("itag", 0).takeIf { it > 0 }?.toString() ?: "bestaudio/best",
                    exactBytes = altAudio.bytes,
                    isAudioOnly = true,
                    needsMux = false,
                    badgeAr = altLabel,
                    badgeEn = altCode.uppercase(Locale.ROOT),
                    acodec = altAudio.codec,
                    languageCode = altCode,
                    languageLabel = altLabel,
                    isAutoDubbed = altAudio.isDubbed,
                    isOriginalAudio = altAudio.isDefault,
                    hasVideo = false,
                    hasAudio = true
                )
            }
            sortedFormats += ResolvedMediaFormat(
                id = "audio_mp3",
                labelAr = "صوت فقط • MP3",
                labelEn = "MP3",
                subLabelAr = "تحويل تلقائي متوافق مع كل المشغلات",
                containerExt = "mp3",
                mimeType = "audio/mpeg",
                bitrateKbps = 320,
                streamUrl = bestAudioUrl,
                formatExpression = "bestaudio/best",
                exactBytes = bestAudioBytes,
                isApproximateBytes = true,
                isAudioOnly = true,
                needsMux = true,
                badgeAr = "للمحاضرات",
                badgeEn = "For Lectures",
                languageCode = primaryAudio?.langCode.orEmpty(),
                languageLabel = audioLanguageLabel(primaryAudio),
                hasVideo = false,
                hasAudio = true
            )
        }

        if (sortedFormats.isEmpty()) {
            return ResolvedMediaResult.Failure(
                DownloadError.ExtractionFailed(
                    "يوتيوب",
                    "streamingData contained no decipherable direct URLs (cipher requires JS yt-dlp engine)"
                ),
                diag.toString()
            )
        }

        return ResolvedMediaResult.SingleMedia(
            originalUrl = originalUrl,
            canonicalUrl = canonicalUrl,
            title = title,
            authorOrSource = author,
            sourceDomain = "youtube.com",
            platform = MediaPlatform.YOUTUBE,
            kind = if (sortedFormats.any { it.isHls }) ResolvedMediaKind.HLS_STREAM else ResolvedMediaKind.SINGLE_VIDEO,
            thumbnailUrl = thumbUrl,
            durationSeconds = durationSec,
            formats = sortedFormats,
            cookiesHeader = cookiesHeader
        )
    }

    private fun fetchInnertubeOrHtmlPlayer(
        canonicalUrl: String,
        videoId: String,
        cookiesHeader: String?,
        diag: StringBuilder
    ): JSONObject? {
        val clients = listOf(
            """{"context":{"client":{"clientName":"ANDROID","clientVersion":"19.44.38","androidSdkVersion":34,"hl":"ar","gl":"EG"}},"videoId":"$videoId","contentCheckOk":true,"racyCheckOk":true}""",
            """{"context":{"client":{"clientName":"IOS","clientVersion":"19.45.4","deviceMake":"Apple","deviceModel":"iPhone16,2","hl":"ar","gl":"EG"}},"videoId":"$videoId","contentCheckOk":true,"racyCheckOk":true}"""
        )
        for (payload in clients) {
            val req = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("User-Agent", ResolverHttpUtils.MOBILE_UA)
                .header("Content-Type", "application/json")
                .apply {
                    if (!cookiesHeader.isNullOrBlank()) header("Cookie", cookiesHeader)
                }
                .build()
            try {
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            val obj = JSONObject(body)
                            val sd = obj.optJSONObject("streamingData")
                            if (sd != null && ((sd.optJSONArray("formats")?.length() ?: 0) > 0 ||
                                    (sd.optJSONArray("adaptiveFormats")?.length() ?: 0) > 0 ||
                                    sd.optString("hlsManifestUrl").isNotBlank())
                            ) {
                                return obj
                            }
                            if (obj.optJSONObject("playabilityStatus")?.optString("status") in listOf("LOGIN_REQUIRED", "ERROR", "UNPLAYABLE")) {
                                return obj
                            }
                        }
                    } else {
                        diag.appendLine("[innertube] HTTP ${resp.code}")
                    }
                }
            } catch (t: Throwable) {
                diag.appendLine("[innertube] ${t.javaClass.simpleName}: ${t.message}")
            }
        }

        val htmlResp = ResolverHttpUtils.fetchTextWithStatus(
            client = httpClient,
            url = canonicalUrl,
            cookiesHeader = cookiesHeader,
            userAgent = ResolverHttpUtils.DESKTOP_UA
        )
        if (htmlResp != null) {
            if (htmlResp.statusCode >= 400) {
                diag.appendLine("[yt-html] HTTP ${htmlResp.statusCode}")
            } else {
                val player = ResolverHttpUtils.extractJsonObjectFromHtml(htmlResp.body, "var ytInitialPlayerResponse = ")
                    ?: ResolverHttpUtils.extractJsonObjectFromHtml(htmlResp.body, "ytInitialPlayerResponse = ")
                if (player != null) return player
            }
        }
        return null
    }

    private fun resolveDirectStreamUrl(formatObj: JSONObject): String? {
        val direct = formatObj.optString("url")
        if (direct.isNotBlank()) return direct
        val cipher = formatObj.optString("signatureCipher").ifBlank { formatObj.optString("cipher") }
        if (cipher.isNotBlank()) {
            val params = cipher.split("&").mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx > 0) {
                    runCatching {
                        URLDecoder.decode(part.substring(0, idx), "UTF-8") to
                            URLDecoder.decode(part.substring(idx + 1), "UTF-8")
                    }.getOrNull()
                } else null
            }.toMap()
            val baseUrl = params["url"]
            if (!baseUrl.isNullOrBlank() && params["s"].isNullOrBlank()) {
                return baseUrl
            }
        }
        return null
    }

    private fun collectVideoRenderers(
        node: Any?,
        out: MutableList<ResolvedCollectionEntry>,
        includePlaylistRenderer: Boolean,
        includeChannelRenderers: Boolean
    ) {
        when (node) {
            is JSONObject -> {
                if (includePlaylistRenderer) {
                    node.optJSONObject("playlistVideoRenderer")?.let { r ->
                        addRendererEntry(r, out)
                    }
                }
                if (includeChannelRenderers) {
                    node.optJSONObject("videoRenderer")?.let { r -> addRendererEntry(r, out) }
                    node.optJSONObject("gridVideoRenderer")?.let { r -> addRendererEntry(r, out) }
                    node.optJSONObject("reelItemRenderer")?.let { r ->
                        val vId = r.optString("videoId")
                        if (vId.isNotBlank() && out.none { it.itemId == vId }) {
                            val title = r.optJSONObject("headline")?.optString("simpleText")
                                ?: "Short ${out.size + 1}"
                            out += ResolvedCollectionEntry(
                                index = out.size + 1,
                                itemId = vId,
                                title = title,
                                url = "https://www.youtube.com/shorts/$vId",
                                thumbnailUrl = "https://i.ytimg.com/vi/$vId/hqdefault.jpg"
                            )
                        }
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    collectVideoRenderers(node.opt(keys.next()), out, includePlaylistRenderer, includeChannelRenderers)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectVideoRenderers(node.opt(i), out, includePlaylistRenderer, includeChannelRenderers)
                }
            }
        }
    }

    private fun addRendererEntry(r: JSONObject, out: MutableList<ResolvedCollectionEntry>) {
        val vId = r.optString("videoId")
        if (vId.isBlank() || out.any { it.itemId == vId }) return
        val runs = r.optJSONObject("title")?.optJSONArray("runs")
        val vTitle = runs?.optJSONObject(0)?.optString("text")
            ?: r.optJSONObject("title")?.optString("simpleText")
            ?: "Video ${out.size + 1}"
        val lenSec = r.optString("lengthSeconds").toIntOrNull() ?: 0
        val thumb = r.optJSONObject("thumbnail")?.optJSONArray("thumbnails")?.let { arr ->
            if (arr.length() > 0) arr.optJSONObject(arr.length() - 1)?.optString("url") else null
        } ?: "https://i.ytimg.com/vi/$vId/hqdefault.jpg"
        out += ResolvedCollectionEntry(
            index = out.size + 1,
            itemId = vId,
            title = vTitle,
            url = "https://www.youtube.com/watch?v=$vId",
            durationSeconds = lenSec,
            thumbnailUrl = thumb
        )
    }

    fun extractVideoId(url: String): String? {
        return runCatching {
            val uri = URI(url)
            val host = (uri.host ?: "").lowercase(Locale.ROOT)
            val path = uri.path.orEmpty()
            when {
                host == "youtu.be" -> path.trim('/').substringBefore('/').takeIf { it.isNotBlank() }
                path.startsWith("/shorts/") -> path.removePrefix("/shorts/").substringBefore('/').takeIf { it.isNotBlank() }
                path.startsWith("/embed/") -> path.removePrefix("/embed/").substringBefore('/').takeIf { it.isNotBlank() }
                path.startsWith("/live/") -> path.removePrefix("/live/").substringBefore('/').takeIf { it.isNotBlank() }
                else -> ResolverHttpUtils.extractQueryParam(url, "v")
            }
        }.getOrNull()
    }

    fun isChannelUrl(url: String): Boolean {
        val path = runCatching { URI(url).path.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
        return path.startsWith("/@") ||
            path.startsWith("/channel/") ||
            path.startsWith("/c/") ||
            path.startsWith("/user/")
    }

    private fun normalizeChannelVideosUrl(url: String): String {
        val clean = url.substringBefore('?').trimEnd('/')
        return if (clean.endsWith("/videos") || clean.endsWith("/shorts") || clean.endsWith("/streams")) {
            clean
        } else {
            "$clean/videos"
        }
    }

    private fun qualityLabelAr(h: Int): String = when (h) {
        2160 -> "2160p • 4K فائقة الدقة"
        1440 -> "1440p • 2K QHD"
        1080 -> "1080p • Full HD"
        720 -> "720p • HD"
        480 -> "480p • متوسطة"
        360 -> "360p • اقتصادية"
        240 -> "240p • منخفضة"
        144 -> "144p • أقل بيانات"
        else -> "${h}p"
    }

    private fun badgeArForHeight(h: Int): String? = when {
        h >= 2160 -> "الأوضح"
        h == 1440 -> "2K فائق"
        h == 1080 -> "Full HD"
        h == 720 -> "HD"
        h <= 144 -> "أقل استهلاكاً للبيانات"
        else -> null
    }

    private fun badgeEnForHeight(h: Int): String? = when {
        h >= 2160 -> "Highest"
        h == 1440 -> "2K QHD"
        h == 1080 -> "Full HD"
        h == 720 -> "HD"
        h <= 144 -> "Data Saver"
        else -> null
    }
}
