package com.example.download.extract

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

data class SniffedMediaResult(
    val mediaUrl: String,
    val mimeType: String,
    val isHlsOrDash: Boolean,
    val headers: Map<String, String>
)

object HeadlessWebViewSniffer {

    private val blockedAdHosts = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "google-analytics.com",
        "adservice.google.com",
        "facebook.net",
        "ads-twitter.com",
        "taboola.com",
        "outbrain.com",
        "scorecardresearch.com"
    )

    private val videoExtensions = listOf(".mp4", ".m3u8", ".mpd", ".webm", ".mkv", ".mov", ".m4v")

    private const val MOBILE_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun sniffMediaUrl(
        context: Context,
        pageUrl: String,
        timeoutMs: Long = 20_000L
    ): SniffedMediaResult? {
        val resultDeferred = CompletableDeferred<SniffedMediaResult?>()
        var fallbackAudioCandidate: SniffedMediaResult? = null
        var webView: WebView? = null

        return try {
            withContext(Dispatchers.Main) {
                val created = WebView(context.applicationContext)
                webView = created
                val settings = created.settings
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = true
                settings.loadsImagesAutomatically = false
                settings.blockNetworkImage = true
                settings.userAgentString = MOBILE_USER_AGENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                }

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(created, true)

                created.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUri = request?.url ?: return super.shouldInterceptRequest(view, request)
                        val reqUrl = reqUri.toString()
                        if (isAdOrTracker(reqUri.host)) {
                            return WebResourceResponse("text/plain", "utf-8", null)
                        }
                        val accept = request.requestHeaders["Accept"]?.lowercase(Locale.ROOT).orEmpty()
                        val matched = classifyCandidateUrl(reqUrl, accept)
                        if (matched != null && !resultDeferred.isCompleted) {
                            val headers = buildMap<String, String> {
                                put("Referer", pageUrl)
                                put("User-Agent", MOBILE_USER_AGENT)
                                request.requestHeaders["Cookie"]?.let { put("Cookie", it) }
                                CookieManager.getInstance().getCookie(reqUrl)?.let { cookie ->
                                    if (cookie.isNotBlank()) put("Cookie", cookie)
                                }
                            }
                            val enriched = matched.copy(headers = headers)
                            if (enriched.mimeType.startsWith("audio/")) {
                                // Buffer audio candidate instead of short-circuiting video discovery (e.g. TikTok background audio)
                                if (fallbackAudioCandidate == null) {
                                    fallbackAudioCandidate = enriched
                                }
                            } else {
                                resultDeferred.complete(enriched)
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (resultDeferred.isCompleted || view == null) return
                        view.evaluateJavascript(
                            "(function(){" +
                                "var v=document.querySelector('video');" +
                                "if(v&&v.currentSrc)return v.currentSrc;" +
                                "if(v&&v.src)return v.src;" +
                                "var s=document.querySelector('video source');" +
                                "if(s&&s.src)return s.src;" +
                                "var og=document.querySelector('meta[property=\"og:video\"],meta[property=\"og:video:url\"]');" +
                                "return og?og.content:'';" +
                                "})();"
                        ) { raw ->
                            val clean = raw?.trim()?.trim('"')?.replace("\\/", "/") ?: ""
                            if (clean.startsWith("http") && !resultDeferred.isCompleted) {
                                val classified = classifyCandidateUrl(clean, "video/mp4")
                                    ?: SniffedMediaResult(
                                        mediaUrl = clean,
                                        mimeType = "video/mp4",
                                        isHlsOrDash = clean.contains(".m3u8") || clean.contains(".mpd"),
                                        headers = mapOf("Referer" to pageUrl, "User-Agent" to MOBILE_USER_AGENT)
                                    )
                                resultDeferred.complete(classified)
                            } else if (!resultDeferred.isCompleted && fallbackAudioCandidate != null) {
                                resultDeferred.complete(fallbackAudioCandidate)
                            }
                        }
                    }
                }

                created.loadUrl(pageUrl)
            }

            withTimeoutOrNull(timeoutMs) {
                resultDeferred.await()
            } ?: fallbackAudioCandidate
        } catch (_: Throwable) {
            fallbackAudioCandidate
        } finally {
            withContext(Dispatchers.Main) {
                runCatching {
                    webView?.apply {
                        stopLoading()
                        loadUrl("about:blank")
                        clearHistory()
                        removeAllViews()
                        destroy()
                    }
                }
            }
        }
    }

    private fun isAdOrTracker(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val lower = host.lowercase(Locale.ROOT)
        return blockedAdHosts.any { lower == it || lower.endsWith(".$it") }
    }

    internal fun classifyCandidateUrl(url: String, acceptHeader: String): SniffedMediaResult? {
        val lower = url.lowercase(Locale.ROOT)
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
        if (lower.contains(".js") || lower.contains(".css") || lower.contains(".png") ||
            lower.contains(".jpg") || lower.contains(".webp") || lower.contains(".woff") ||
            lower.contains(".svg")
        ) {
            return null
        }

        val cleanPath = lower.substringBefore('?').substringBefore('#')
        val isHls = cleanPath.endsWith(".m3u8") || lower.contains("mime=application/x-mpegurl") ||
            lower.contains(".m3u8")
        val isDash = cleanPath.endsWith(".mpd") || lower.contains(".mpd")
        val isAudioExt = cleanPath.endsWith(".m4a") || cleanPath.endsWith(".mp3") ||
            cleanPath.endsWith(".aac") || cleanPath.endsWith(".opus") ||
            lower.contains("mime=audio/") || lower.contains("mime_type=audio")
        val isVideoExt = !isAudioExt && (videoExtensions.any { cleanPath.endsWith(it) } ||
            lower.contains("mime=video/") ||
            lower.contains("mime_type=video") ||
            lower.contains("videoplayback"))
        val isVideoAccept = !isAudioExt && acceptHeader.contains("video/") && lower.length > 24

        return when {
            isHls -> SniffedMediaResult(url, "application/x-mpegURL", true, emptyMap())
            isDash -> SniffedMediaResult(url, "application/dash+xml", true, emptyMap())
            isVideoExt || isVideoAccept -> SniffedMediaResult(url, "video/mp4", false, emptyMap())
            isAudioExt -> SniffedMediaResult(url, if (cleanPath.endsWith(".mp3")) "audio/mpeg" else "audio/mp4", false, emptyMap())
            else -> null
        }
    }
}
