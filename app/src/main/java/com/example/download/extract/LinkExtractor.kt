package com.example.download.extract

import android.content.Context
import com.example.download.core.model.DownloadError
import com.example.download.core.model.DownloadErrorMapper
import com.example.download.core.model.ResolveRequest
import com.example.download.core.model.ResolvedMediaResult
import com.example.download.core.resolver.AndroidWebViewSniffGateway
import com.example.download.core.resolver.AndroidYtDlpGateway
import com.example.download.core.resolver.ResolverHttpUtils
import com.example.download.core.resolver.UnifiedMediaResolver
import com.example.download.data.DownloadSettingsRepo
import com.example.download.data.ExtractionResult
import com.example.download.data.MediaQualityOption
import com.example.download.data.PlaylistEntry
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.concurrent.TimeUnit

object LinkExtractor {

    private val probeHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun ensureInitialized(context: Context): Boolean =
        AndroidYtDlpGateway(context.applicationContext).ensureInitialized()

    /**
     * Safe video extraction engine update check:
     * Only refreshes the isolated yt-dlp script when online and interval >= 24h,
     * preserving bundled working FFmpeg/aria2c/yt-dlp if offline or failed.
     */
    suspend fun updateYtDlpIfStale(context: Context, settingsRepo: DownloadSettingsRepo) {
        val now = System.currentTimeMillis()
        val lastUpdate = runCatching { settingsRepo.getLastYtDlpUpdateMs() }.getOrDefault(0L)
        if (now - lastUpdate < 24L * 60L * 60L * 1000L) return
        if (!ensureInitialized(context)) return
        withContext(Dispatchers.IO) {
            runCatching {
                YoutubeDL.getInstance().updateYoutubeDL(
                    context.applicationContext,
                    YoutubeDL.UpdateChannel._STABLE
                )
                settingsRepo.setLastYtDlpUpdateMs(System.currentTimeMillis())
            }
        }
    }

    fun extractFirstHttpUrl(rawText: String?): String? =
        ResolverHttpUtils.extractFirstHttpUrl(rawText)

    suspend fun extract(
        context: Context,
        rawInputUrl: String,
        settingsRepo: DownloadSettingsRepo,
        checkPlaylist: Boolean = true
    ): ExtractionResult = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val resolver = UnifiedMediaResolver(
            httpClient = probeHttpClient,
            ytDlpGateway = AndroidYtDlpGateway(appCtx),
            webViewSniffGateway = AndroidWebViewSniffGateway(appCtx)
        )
        val cookiesFile = settingsRepo.getCookiesFile().takeIf { it.exists() && it.length() > 20L }
        val result = resolver.resolve(
            ResolveRequest(
                rawUrl = rawInputUrl,
                checkCollection = checkPlaylist,
                cookiesFile = cookiesFile,
                allowWebViewSniffer = true
            )
        )
        mapToLegacyResult(result)
    }

    private fun mapToLegacyResult(result: ResolvedMediaResult): ExtractionResult = when (result) {
        is ResolvedMediaResult.SingleMedia -> {
            ExtractionResult.SingleVideo(
                originalUrl = result.originalUrl,
                resolvedUrl = result.canonicalUrl,
                title = result.title,
                thumbnailUrl = result.thumbnailUrl,
                platform = result.platform.labelAr,
                durationSeconds = result.durationSeconds,
                qualities = result.formats.map { fmt ->
                    MediaQualityOption(
                        id = fmt.id,
                        labelAr = fmt.labelAr,
                        subLabel = fmt.subLabelAr,
                        resolutionHeight = fmt.resolutionHeight,
                        isAudioOnly = fmt.isAudioOnly,
                        formatId = fmt.formatExpression,
                        ext = fmt.containerExt,
                        directUrl = fmt.streamUrl,
                        audioUrl = fmt.audioStreamUrl,
                        estimatedBytes = fmt.exactBytes,
                        isApproximateSize = fmt.isApproximateBytes,
                        needsMux = fmt.needsMux,
                        isHlsOrDash = fmt.isHlsOrDash,
                        headers = fmt.headers
                    )
                },
                referer = result.canonicalUrl,
                headers = result.headers
            )
        }
        is ResolvedMediaResult.MediaCollection -> {
            ExtractionResult.Playlist(
                originalUrl = result.originalUrl,
                title = result.title,
                platform = result.platform.labelAr,
                entries = result.entries.map { entry ->
                    PlaylistEntry(
                        index = entry.index,
                        title = entry.title,
                        url = entry.url,
                        durationSeconds = entry.durationSeconds,
                        thumbnailUrl = entry.thumbnailUrl,
                        selected = entry.selected
                    )
                }
            )
        }
        is ResolvedMediaResult.Failure -> {
            if (result.error is DownloadError.ProtectedContent) {
                ExtractionResult.DrmProtected(
                    messageAr = result.error.userMessageAr,
                    diagnostic = result.diagnosticLog
                )
            } else {
                ExtractionResult.Failed(
                    friendlyMessageAr = result.error.userMessageAr,
                    diagnostic = result.diagnosticLog.ifBlank { result.error.technicalDetail },
                    suggestCookieLogin = result.error.suggestLoginOrBrowser
                )
            }
        }
    }

    fun detectPlatformName(url: String): String {
        val lower = url.lowercase(Locale.ROOT)
        return when {
            lower.contains("youtube.com") || lower.contains("youtu.be") -> "يوتيوب"
            lower.contains("tiktok.com") -> "تيك توك"
            lower.contains("pinterest.") || lower.contains("pin.it") -> "بنترست"
            lower.contains("facebook.com") || lower.contains("fb.watch") || lower.contains("fb.com") -> "فيسبوك"
            lower.contains("instagram.com") -> "إنستغرام"
            lower.contains("twitter.com") || lower.contains("x.com") || lower.contains("t.co/") -> "X (تويتر)"
            else -> "رابط ويب"
        }
    }

    fun mapThrowableToFriendlyError(t: Throwable): Pair<String, String> {
        val mapped = DownloadErrorMapper.fromThrowable(t)
        val diag = buildString {
            appendLine("[${mapped.code}] ${mapped.technicalDetail}")
            appendLine(t.javaClass.name + ": " + (t.message ?: ""))
            t.cause?.let { appendLine("Cause: ${it.javaClass.name}: ${it.message}") }
        }
        return mapped.userMessageAr to diag
    }
}
