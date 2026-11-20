package com.example.download.core.resolver

import com.example.download.core.model.DownloadError
import com.example.download.core.model.MediaPlatform
import com.example.download.core.model.ResolveRequest
import com.example.download.core.model.ResolvedMediaFormat
import com.example.download.core.model.ResolvedMediaKind
import com.example.download.core.model.ResolvedMediaResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.Locale

class UnifiedMediaResolver(
    private val httpClient: OkHttpClient,
    ytDlpGateway: YtDlpGateway = NoOpYtDlpGateway,
    private val webViewSniffGateway: WebViewSniffGateway = NoOpWebViewSniffGateway
) {
    private val directAdapter = DirectAndStreamAdapter(httpClient, ytDlpGateway)

    private val platformAdapters: List<MediaProviderAdapter> = listOf(
        YouTubeProviderAdapter(httpClient, ytDlpGateway),
        FacebookProviderAdapter(httpClient, ytDlpGateway),
        InstagramProviderAdapter(httpClient, ytDlpGateway),
        TikTokProviderAdapter(httpClient, ytDlpGateway),
        XTwitterProviderAdapter(httpClient, ytDlpGateway),
        PinterestProviderAdapter(httpClient, ytDlpGateway)
    )

    suspend fun resolve(request: ResolveRequest): ResolvedMediaResult = withContext(Dispatchers.IO) {
        val normalizedUrl = ResolverHttpUtils.extractFirstHttpUrl(request.rawUrl) ?: request.rawUrl.trim()
        if (!normalizedUrl.startsWith("http://", true) && !normalizedUrl.startsWith("https://", true)) {
            return@withContext ResolvedMediaResult.Failure(
                error = DownloadError.UnsupportedPlatform(
                    host = normalizedUrl,
                    technicalDetail = "Invalid URL scheme: $normalizedUrl"
                ),
                diagnosticLog = "Invalid URL scheme: $normalizedUrl"
            )
        }

        val canonicalUrl = if (shouldFollowShortRedirects(normalizedUrl)) {
            ResolverHttpUtils.resolveRedirects(httpClient, normalizedUrl, request.cookiesHeader)
        } else {
            normalizedUrl
        }
        val host = ResolverHttpUtils.normalizeHost(canonicalUrl)

        val matchedAdapter = platformAdapters.firstOrNull { it.canHandle(canonicalUrl, host) }
            ?: directAdapter

        val primaryResult = matchedAdapter.resolve(canonicalUrl, request)
        if (primaryResult !is ResolvedMediaResult.Failure) {
            return@withContext primaryResult
        }

        val shouldTrySniffer = request.allowWebViewSniffer &&
            primaryResult.error !is DownloadError.ProtectedContent &&
            primaryResult.error !is DownloadError.PrivateContent

        if (shouldTrySniffer) {
            val sniffed = webViewSniffGateway.sniff(canonicalUrl)
            if (sniffed != null && sniffed.mediaUrl.startsWith("http", true)) {
                val isAudio = sniffed.mimeType.startsWith("audio/")
                return@withContext ResolvedMediaResult.SingleMedia(
                    originalUrl = request.rawUrl,
                    canonicalUrl = canonicalUrl,
                    title = ResolverHttpUtils.deriveTitleFromUrl(canonicalUrl, "وسائط_هوس_الدراسة"),
                    authorOrSource = matchedAdapter.platform.labelEn,
                    sourceDomain = host,
                    platform = matchedAdapter.platform,
                    kind = when {
                        sniffed.isHlsOrDash -> ResolvedMediaKind.HLS_STREAM
                        isAudio -> ResolvedMediaKind.AUDIO_ONLY
                        else -> ResolvedMediaKind.SINGLE_VIDEO
                    },
                    formats = listOf(
                        ResolvedMediaFormat(
                            id = if (isAudio) "audio_m4a" else "best",
                            labelAr = if (isAudio) "صوت مستخلص" else "جودة الفيديو المتاحة",
                            labelEn = if (isAudio) "Extracted Audio" else "Stream",
                            subLabelAr = if (sniffed.isHlsOrDash) "بث مباشر (FFmpeg)" else "رابط مباشر سريع",
                            containerExt = if (isAudio) "m4a" else "mp4",
                            mimeType = sniffed.mimeType.ifBlank { if (isAudio) "audio/mp4" else "video/mp4" },
                            resolutionHeight = if (isAudio) 0 else 720,
                            streamUrl = sniffed.mediaUrl,
                            isAudioOnly = isAudio,
                            isHls = sniffed.isHlsOrDash,
                            needsMux = sniffed.isHlsOrDash,
                            headers = sniffed.headers
                        )
                    ),
                    headers = sniffed.headers,
                    cookiesHeader = request.cookiesHeader
                )
            }
        }

        primaryResult.copy(
            diagnosticLog = buildString {
                if (primaryResult.diagnosticLog.isNotBlank()) appendLine(primaryResult.diagnosticLog)
                if (shouldTrySniffer) appendLine("[sniffer] No playable stream captured")
            }
        )
    }

    private fun shouldFollowShortRedirects(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return listOf(
            "pin.it", "vm.tiktok.com", "vt.tiktok.com", "fb.watch", "t.co/",
            "youtu.be/", "bit.ly/", "facebook.com/share/", "instagr.am"
        ).any { lower.contains(it) }
    }
}
