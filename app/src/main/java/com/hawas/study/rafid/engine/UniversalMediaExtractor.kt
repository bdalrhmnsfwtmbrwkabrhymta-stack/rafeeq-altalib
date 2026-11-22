package com.hawas.study.rafid.engine

import com.example.download.core.model.DownloadEngineException
import com.example.download.core.model.DownloadError
import com.example.download.core.model.MediaPlatform
import com.example.download.core.model.ResolveRequest
import com.example.download.core.model.ResolvedMediaResult
import com.example.download.core.resolver.DirectAndStreamAdapter
import com.example.download.core.resolver.NoOpWebViewSniffGateway
import com.example.download.core.resolver.NoOpYtDlpGateway
import com.example.download.core.resolver.PinterestProviderAdapter
import com.example.download.core.resolver.ResolverHttpUtils
import com.example.download.core.resolver.TikTokProviderAdapter
import com.example.download.core.resolver.UnifiedMediaResolver
import com.example.download.core.resolver.WebViewSniffGateway
import com.example.download.core.resolver.YouTubeProviderAdapter
import com.example.download.core.resolver.YtDlpGateway
import com.hawas.study.rafid.models.ExtractedMediaMetadata
import com.hawas.study.rafid.models.MediaFormatOption
import com.hawas.study.rafid.models.PlaylistEntryItem
import com.hawas.study.rafid.models.RafidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class UniversalMediaExtractor(
    private val httpClient: OkHttpClient = defaultClient(),
    private val linkResolver: UniversalLinkResolver = UniversalLinkResolver(httpClient),
    private val ytDlpGateway: YtDlpGateway = NoOpYtDlpGateway,
    private val webViewSniffGateway: WebViewSniffGateway = NoOpWebViewSniffGateway
) {
    private val unifiedResolver = UnifiedMediaResolver(
        httpClient = httpClient,
        ytDlpGateway = ytDlpGateway,
        webViewSniffGateway = webViewSniffGateway
    )

    companion object {
        val STANDARD_VIDEO_HEIGHTS = listOf(2160, 1440, 1080, 720, 480, 360, 240, 144)

        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .build()
        }
    }

    suspend fun extract(
        rawUrl: String,
        cookiesHeader: String? = null,
        userAgent: String? = null,
        checkCollection: Boolean = true
    ): Result<ExtractedMediaMetadata> = withContext(Dispatchers.IO) {
        val cleanInput = AdAndTrackerShield.extractFirstUrl(rawUrl) ?: rawUrl.trim()
        val preResolved = runCatching { linkResolver.resolve(cleanInput, cookiesHeader) }.getOrNull()
        val targetUrl = preResolved?.resolvedUrl ?: cleanInput

        when (val res = unifiedResolver.resolve(
            ResolveRequest(
                rawUrl = targetUrl,
                checkCollection = checkCollection,
                cookiesHeader = cookiesHeader,
                userAgent = userAgent,
                allowWebViewSniffer = webViewSniffGateway != NoOpWebViewSniffGateway
            )
        )) {
            is ResolvedMediaResult.SingleMedia -> {
                Result.success(mapSingleToRafid(cleanInput, res, userAgent))
            }
            is ResolvedMediaResult.MediaCollection -> {
                Result.success(mapCollectionToRafid(cleanInput, res, userAgent))
            }
            is ResolvedMediaResult.Failure -> {
                Result.failure(DownloadEngineException(res.error))
            }
        }
    }

    internal fun parseYouTubePlayerJson(
        originalUrl: String,
        resolvedUrl: String,
        videoId: String,
        playerJson: JSONObject,
        cookiesHeader: String? = null,
        userAgent: String? = null
    ): ExtractedMediaMetadata {
        val adapter = YouTubeProviderAdapter(httpClient, ytDlpGateway)
        val res = adapter.parsePlayerJsonResult(originalUrl, resolvedUrl, videoId, playerJson, cookiesHeader)
        return when (res) {
            is ResolvedMediaResult.SingleMedia -> mapSingleToRafid(originalUrl, res, userAgent)
            is ResolvedMediaResult.Failure -> throw DownloadEngineException(res.error)
            else -> throw DownloadEngineException(DownloadError.ExtractionFailed("يوتيوب", "Unexpected collection result"))
        }
    }

    internal fun parseYouTubePlaylistJson(
        originalUrl: String,
        resolvedUrl: String,
        listId: String,
        initialData: JSONObject,
        cookiesHeader: String? = null,
        userAgent: String? = null
    ): ExtractedMediaMetadata {
        val adapter = YouTubeProviderAdapter(httpClient, ytDlpGateway)
        val res = adapter.parsePlaylistInitialData(originalUrl, resolvedUrl, listId, initialData, cookiesHeader)
        return when (res) {
            is ResolvedMediaResult.MediaCollection -> mapCollectionToRafid(originalUrl, res, userAgent)
            is ResolvedMediaResult.Failure -> throw DownloadEngineException(res.error)
            else -> throw DownloadEngineException(DownloadError.ExtractionFailed("يوتيوب", "Unexpected result"))
        }
    }

    internal fun parsePinterestHtml(
        originalUrl: String,
        resolvedUrl: String,
        html: String,
        cookiesHeader: String? = null,
        userAgent: String? = null
    ): ExtractedMediaMetadata {
        val adapter = PinterestProviderAdapter(httpClient, ytDlpGateway)
        val res = adapter.parsePinterestHtml(originalUrl, resolvedUrl, html, cookiesHeader)
        return when (res) {
            is ResolvedMediaResult.SingleMedia -> mapSingleToRafid(originalUrl, res, userAgent)
            is ResolvedMediaResult.Failure -> throw DownloadEngineException(res.error)
            else -> throw DownloadEngineException(DownloadError.ExtractionFailed("بنترست", "Unexpected result"))
        }
    }

    internal fun parseTikTokHtml(
        originalUrl: String,
        resolvedUrl: String,
        html: String,
        cookiesHeader: String? = null,
        userAgent: String? = null
    ): ExtractedMediaMetadata {
        val adapter = TikTokProviderAdapter(httpClient, ytDlpGateway)
        val res = adapter.parseTikTokHtml(originalUrl, resolvedUrl, html, cookiesHeader)
        return when (res) {
            is ResolvedMediaResult.SingleMedia -> mapSingleToRafid(originalUrl, res, userAgent)
            is ResolvedMediaResult.Failure -> throw DownloadEngineException(res.error)
            else -> throw DownloadEngineException(DownloadError.ExtractionFailed("تيك توك", "Unexpected result"))
        }
    }

    internal fun parseGenericHtmlMedia(
        originalUrl: String,
        resolvedUrl: String,
        platform: RafidPlatform,
        html: String,
        cookiesHeader: String? = null,
        userAgent: String? = null
    ): ExtractedMediaMetadata {
        val adapter = DirectAndStreamAdapter(httpClient, ytDlpGateway)
        val mappedPlatform = when (platform) {
            RafidPlatform.YOUTUBE -> MediaPlatform.YOUTUBE
            RafidPlatform.PINTEREST -> MediaPlatform.PINTEREST
            RafidPlatform.TIKTOK -> MediaPlatform.TIKTOK
            RafidPlatform.INSTAGRAM -> MediaPlatform.INSTAGRAM
            RafidPlatform.FACEBOOK -> MediaPlatform.FACEBOOK
            RafidPlatform.X_TWITTER -> MediaPlatform.X_TWITTER
            RafidPlatform.STUDY_PLATFORM -> MediaPlatform.STUDY_PLATFORM
            RafidPlatform.DIRECT -> MediaPlatform.DIRECT
        }
        val res = adapter.parseHtmlMedia(originalUrl, resolvedUrl, mappedPlatform, html, cookiesHeader)
        return when (res) {
            is ResolvedMediaResult.SingleMedia -> mapSingleToRafid(originalUrl, res, userAgent)
            is ResolvedMediaResult.Failure -> throw DownloadEngineException(res.error)
            else -> throw DownloadEngineException(DownloadError.ExtractionFailed(platform.labelAr, "Unexpected result"))
        }
    }

    private fun mapSingleToRafid(
        originalUrl: String,
        res: ResolvedMediaResult.SingleMedia,
        userAgent: String?
    ): ExtractedMediaMetadata {
        val rafidFormats = res.formats.map { fmt ->
            val shortLabel = when {
                fmt.isAudioOnly -> fmt.labelEn.ifBlank { fmt.containerExt.uppercase() }
                fmt.languageCode.isNotBlank() && fmt.labelEn.contains("(") -> fmt.labelEn
                fmt.resolutionHeight > 0 -> "${fmt.resolutionHeight}p"
                else -> fmt.labelEn.ifBlank { fmt.labelAr }
            }
            val effectiveStreamUrl = if (fmt.isHlsOrDash &&
                !ResolverHttpUtils.isManifestUrl(fmt.streamUrl) &&
                res.canonicalUrl.startsWith("http", true)
            ) {
                res.canonicalUrl
            } else {
                fmt.streamUrl
            }
            MediaFormatOption(
                id = fmt.id,
                label = shortLabel,
                containerExt = fmt.containerExt,
                mimeType = fmt.mimeType,
                resolutionHeight = fmt.resolutionHeight.takeIf { it > 0 },
                bitrateKbps = fmt.bitrateKbps.takeIf { it > 0 },
                videoUrl = effectiveStreamUrl,
                audioUrl = fmt.audioStreamUrl,
                estimatedSizeBytes = fmt.exactBytes.coerceAtLeast(0L),
                isAudioOnly = fmt.isAudioOnly,
                isMuxRequired = fmt.needsMux || fmt.isHlsOrDash,
                isPlaylistBundle = false,
                badgeTagAr = fmt.badgeAr,
                badgeTagEn = fmt.badgeEn,
                languageLabel = fmt.languageLabel.ifBlank { "الأصلي" },
                formatExpression = fmt.formatExpression,
                headers = res.headers + fmt.headers,
                languageCode = fmt.languageCode,
                hasAudio = fmt.hasAudio,
                isAutoDubbed = fmt.isAutoDubbed,
                isOriginalAudio = fmt.isOriginalAudio || !fmt.isAutoDubbed
            )
        }

        val resolvedCookieHeader = res.cookiesHeader
            ?: res.headers.entries.firstOrNull { it.key.equals("Cookie", ignoreCase = true) }?.value

        return ExtractedMediaMetadata(
            originalUrl = originalUrl,
            resolvedUrl = res.canonicalUrl,
            title = res.title,
            authorOrSource = res.authorOrSource,
            sourceDomain = res.sourceDomain,
            platform = mapPlatform(res.platform),
            thumbnailUrl = res.thumbnailUrl,
            durationSeconds = res.durationSeconds,
            formats = rafidFormats,
            cookiesHeader = resolvedCookieHeader,
            userAgent = userAgent ?: UniversalLinkResolver.DESKTOP_USER_AGENT
        )
    }

    private fun mapCollectionToRafid(
        originalUrl: String,
        res: ResolvedMediaResult.MediaCollection,
        userAgent: String?
    ): ExtractedMediaMetadata {
        val items = res.entries.map {
            PlaylistEntryItem(
                index = it.index,
                videoId = it.itemId,
                title = it.title,
                videoUrl = it.url,
                durationSeconds = it.durationSeconds,
                thumbnailUrl = it.thumbnailUrl
            )
        }
        val bundleFormats = listOf(
            MediaFormatOption(
                id = "pl_video",
                label = "تحميل المجموعة كاملة فيديو MP4 (${items.size} عنصر)",
                containerExt = "mp4",
                mimeType = "video/mp4",
                resolutionHeight = 720,
                isAudioOnly = false,
                isPlaylistBundle = true,
                badgeTagAr = "${items.size} عنصر",
                badgeTagEn = "${items.size} items"
            ),
            MediaFormatOption(
                id = "pl_audio",
                label = "تحميل المجموعة صوت فقط للمحاضرات (${items.size} عنصر)",
                containerExt = "m4a",
                mimeType = "audio/mp4",
                bitrateKbps = 320,
                isAudioOnly = true,
                isPlaylistBundle = true,
                badgeTagAr = "محاضرات",
                badgeTagEn = "Lectures"
            )
        )
        return ExtractedMediaMetadata(
            originalUrl = originalUrl,
            resolvedUrl = res.canonicalUrl,
            title = res.title,
            authorOrSource = res.authorOrSource,
            sourceDomain = if (res.platform == MediaPlatform.YOUTUBE) "youtube.com" else "web",
            platform = mapPlatform(res.platform),
            thumbnailUrl = items.firstOrNull()?.thumbnailUrl,
            durationSeconds = items.sumOf { it.durationSeconds },
            formats = bundleFormats,
            playlistItems = items,
            isPlaylist = true,
            cookiesHeader = res.cookiesHeader,
            userAgent = userAgent ?: UniversalLinkResolver.DESKTOP_USER_AGENT
        )
    }

    private fun mapPlatform(platform: MediaPlatform): RafidPlatform = when (platform) {
        MediaPlatform.YOUTUBE -> RafidPlatform.YOUTUBE
        MediaPlatform.PINTEREST -> RafidPlatform.PINTEREST
        MediaPlatform.TIKTOK -> RafidPlatform.TIKTOK
        MediaPlatform.INSTAGRAM -> RafidPlatform.INSTAGRAM
        MediaPlatform.FACEBOOK -> RafidPlatform.FACEBOOK
        MediaPlatform.X_TWITTER -> RafidPlatform.X_TWITTER
        MediaPlatform.STUDY_PLATFORM -> RafidPlatform.STUDY_PLATFORM
        MediaPlatform.DIRECT, MediaPlatform.UNKNOWN -> RafidPlatform.DIRECT
    }
}
