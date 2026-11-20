package com.example.download.core.resolver

import com.example.download.core.model.MediaPlatform
import com.example.download.core.model.ResolveRequest
import com.example.download.core.model.ResolvedCollectionEntry
import com.example.download.core.model.ResolvedMediaFormat
import com.example.download.core.model.ResolvedMediaKind
import com.example.download.core.model.ResolvedMediaResult
import com.example.download.extract.SniffedMediaResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.util.Locale

interface MediaProviderAdapter {
    val platform: MediaPlatform
    fun canHandle(canonicalUrl: String, host: String): Boolean
    suspend fun resolve(canonicalUrl: String, request: ResolveRequest): ResolvedMediaResult
}

/**
 * Abstraction over yt-dlp so platform adapters remain cleanly testable in pure JVM unit tests
 * without requiring Android native binaries when testing parsing and fallback logic.
 */
interface YtDlpGateway {
    suspend fun isAvailable(): Boolean
    suspend fun extractSingle(
        url: String,
        cookiesFile: File?,
        extraArgs: Map<String, String> = emptyMap()
    ): Result<YtDlpExtractedInfo>

    suspend fun extractCollection(
        url: String,
        cookiesFile: File?,
        maxItems: Int = 50
    ): Result<List<ResolvedCollectionEntry>>
}

data class YtDlpExtractedInfo(
    val title: String,
    val uploader: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Int,
    val baseHeaders: Map<String, String>,
    val formats: List<ResolvedMediaFormat>
)

interface WebViewSniffGateway {
    suspend fun sniff(url: String, timeoutMs: Long = 18_000L): SniffedMediaResult?
}

object NoOpYtDlpGateway : YtDlpGateway {
    override suspend fun isAvailable(): Boolean = false
    override suspend fun extractSingle(
        url: String,
        cookiesFile: File?,
        extraArgs: Map<String, String>
    ): Result<YtDlpExtractedInfo> = Result.failure(IllegalStateException("yt-dlp gateway unavailable"))

    override suspend fun extractCollection(
        url: String,
        cookiesFile: File?,
        maxItems: Int
    ): Result<List<ResolvedCollectionEntry>> = Result.failure(IllegalStateException("yt-dlp gateway unavailable"))
}

object NoOpWebViewSniffGateway : WebViewSniffGateway {
    override suspend fun sniff(url: String, timeoutMs: Long): SniffedMediaResult? = null
}

object ResolverHttpUtils {

    const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val URL_REGEX = Regex("""https?://[^\s<>"'\)\]]+""", RegexOption.IGNORE_CASE)

    fun extractFirstHttpUrl(rawText: String?): String? {
        if (rawText.isNullOrBlank()) return null
        val raw = URL_REGEX.find(rawText.trim())?.value ?: return null
        return raw.trimEnd('.', ',', ';', ')', ']', '!', '?')
    }

    fun normalizeHost(url: String): String {
        val lower = url.trim().lowercase(Locale.ROOT)
        return runCatching {
            val host = if (lower.contains("://")) URI(lower).host.orEmpty() else lower.substringBefore('/')
            host.removePrefix("www.")
        }.getOrElse {
            lower.substringAfter("://").substringBefore('/').substringBefore(':').removePrefix("www.")
        }
    }

    fun resolveRedirects(
        client: OkHttpClient,
        url: String,
        cookiesHeader: String? = null,
        maxHops: Int = 8
    ): String {
        val noRedirectClient = client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        var current = url.trim()
        var hops = 0
        while (hops < maxHops) {
            val req = Request.Builder()
                .url(current)
                .get()
                .header("User-Agent", DESKTOP_UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ar,en-US;q=0.9,en;q=0.8")
                .apply {
                    if (!cookiesHeader.isNullOrBlank()) header("Cookie", cookiesHeader)
                }
                .build()
            val next = try {
                noRedirectClient.newCall(req).execute().use { resp ->
                    if (resp.code in 300..399) resp.header("Location") else null
                }
            } catch (_: Throwable) {
                null
            }
            if (next.isNullOrBlank()) break
            val resolved = runCatching { URI(current).resolve(next).toString() }.getOrDefault(next)
            if (resolved == current) break
            current = resolved
            hops++
        }
        return current
    }

    data class HttpTextResponse(
        val statusCode: Int,
        val finalUrl: String,
        val body: String,
        val contentType: String
    )

    fun fetchTextWithStatus(
        client: OkHttpClient,
        url: String,
        cookiesHeader: String? = null,
        userAgent: String = DESKTOP_UA,
        extraHeaders: Map<String, String> = emptyMap()
    ): HttpTextResponse? {
        val req = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "ar,en-US;q=0.9,en;q=0.8")
            .apply {
                if (!cookiesHeader.isNullOrBlank()) header("Cookie", cookiesHeader)
                extraHeaders.forEach { (k, v) -> header(k, v) }
            }
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                HttpTextResponse(
                    statusCode = resp.code,
                    finalUrl = resp.request.url.toString(),
                    body = resp.body?.string().orEmpty(),
                    contentType = resp.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                )
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun probeContentLength(
        client: OkHttpClient,
        url: String,
        headers: Map<String, String> = emptyMap()
    ): Long {
        if (!url.startsWith("http", ignoreCase = true)) return -1L
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .head()
                .apply {
                    if (headers.keys.none { it.equals("User-Agent", true) }) {
                        header("User-Agent", DESKTOP_UA)
                    }
                    headers.forEach { (k, v) -> header(k, v) }
                }
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    resp.header("Content-Length")?.toLongOrNull() ?: -1L
                } else -1L
            }
        }.getOrDefault(-1L)
    }

    fun extractQueryParam(url: String, key: String): String? {
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isBlank()) return null
        return query.split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0 && pair.substring(0, idx) == key) {
                runCatching { URLDecoder.decode(pair.substring(idx + 1), "UTF-8") }.getOrNull()
            } else null
        }.firstOrNull()
    }

    fun extractMetaContent(html: String, propertyOrName: String): String? {
        val escaped = Regex.escape(propertyOrName)
        val p1 = Regex("""<meta[^>]+(?:property|name)=["']$escaped["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        p1.find(html)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { return decodeHtmlEntities(it) }
        val p2 = Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["']$escaped["']""", RegexOption.IGNORE_CASE)
        return p2.find(html)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { decodeHtmlEntities(it) }
    }

    fun extractHtmlTitle(html: String): String? {
        val regex = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        return regex.find(html)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { decodeHtmlEntities(it) }
    }

    fun extractJsonObjectFromHtml(html: String, marker: String): JSONObject? {
        val startMarker = html.indexOf(marker)
        if (startMarker < 0) return null
        val jsonStart = html.indexOf('{', startMarker + marker.length)
        if (jsonStart < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in jsonStart until html.length) {
            val c = html[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (c == '\\' && inString) {
                escaped = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '{') depth++
                else if (c == '}') {
                    depth--
                    if (depth == 0) {
                        val slice = html.substring(jsonStart, i + 1)
                        return runCatching { JSONObject(slice) }.getOrNull()
                    }
                }
            }
        }
        return null
    }

    fun extractScriptJsonById(html: String, id: String): JSONObject? {
        val regex = Regex(
            """<script[^>]+id=["']$id["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val content = regex.find(html)?.groupValues?.getOrNull(1)?.trim() ?: return null
        return runCatching { JSONObject(content) }.getOrNull()
    }

    fun decodeHtmlEntities(input: String): String = input
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("\\u002F", "/")
        .replace("\\/", "/")
        .trim()

    fun isManifestUrl(url: String?): Boolean {
        val lower = url?.lowercase(Locale.ROOT) ?: return false
        return lower.contains(".m3u8") || lower.contains(".mpd") || lower.contains("manifest")
    }

    fun deriveTitleFromUrl(url: String, fallback: String = "وسائط_هوس_الدراسة"): String {
        val segment = url.substringBefore('?').substringBefore('#')
            .substringAfterLast('/')
            .substringBeforeLast('.')
            .replace('-', ' ')
            .replace('_', ' ')
            .trim()
        return segment.takeIf { it.length >= 3 && !it.all { ch -> ch.isDigit() } }?.take(60)
            ?: "${fallback}_${System.currentTimeMillis() % 10000}"
    }

    fun resolveRelativeUrl(baseUrl: String, candidate: String): String =
        runCatching { URI(baseUrl).resolve(candidate).toString() }.getOrDefault(candidate)
}
