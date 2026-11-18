package com.example.download.extract

import android.content.Context
import com.example.download.data.DownloadSettingsRepo
import com.example.download.data.ExtractionResult
import com.example.download.data.MediaQualityOption
import com.example.download.data.PlaylistEntry
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit

object LinkExtractor {

    private val initMutex = Mutex()
    @Volatile
    private var ytdlpInitialized = false

    private const val MOBILE_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    private val probeHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun ensureInitialized(context: Context): Boolean {
        if (ytdlpInitialized) return true
        return initMutex.withLock {
            if (ytdlpInitialized) return@withLock true
            withContext(Dispatchers.IO) {
                try {
                    val appCtx = context.applicationContext
                    YoutubeDL.getInstance().init(appCtx)
                    runCatching { FFmpeg.getInstance().init(appCtx) }
                    runCatching { Aria2c.getInstance().init(appCtx) }
                    ytdlpInitialized = true
                    true
                } catch (_: Throwable) {
                    false
                }
            }
        }
    }

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

    fun extractFirstHttpUrl(rawText: String?): String? {
        if (rawText.isNullOrBlank()) return null
        val regex = Regex("""https?://[^\s<>"'\)\]]+""", RegexOption.IGNORE_CASE)
        return regex.find(rawText.trim())?.value?.trimEnd('.', ',', ';', ')', ']')
    }

    suspend fun extract(
        context: Context,
        rawInputUrl: String,
        settingsRepo: DownloadSettingsRepo,
        checkPlaylist: Boolean = true
    ): ExtractionResult = withContext(Dispatchers.IO) {
        val normalizedUrl = extractFirstHttpUrl(rawInputUrl) ?: rawInputUrl.trim()
        if (!normalizedUrl.startsWith("http://", true) && !normalizedUrl.startsWith("https://", true)) {
            return@withContext ExtractionResult.Failed(
                friendlyMessageAr = "يرجى إدخال رابط يبدأ بـ https:// أو http://",
                diagnostic = "Invalid URL scheme: $normalizedUrl"
            )
        }

        val canonicalUrl = resolveRedirects(normalizedUrl)
        val platform = detectPlatformName(canonicalUrl)

        val directProbe = probeDirectMediaUrl(canonicalUrl)
        if (directProbe != null) {
            return@withContext directProbe
        }

        val cookiesFile = settingsRepo.getCookiesFile().takeIf { it.exists() && it.length() > 20L }
        val diagLog = StringBuilder()

        if (ensureInitialized(context)) {
            if (checkPlaylist && looksLikePlaylistUrl(canonicalUrl)) {
                val playlistResult = tryExtractPlaylist(canonicalUrl, platform, cookiesFile, diagLog)
                if (playlistResult != null) return@withContext playlistResult
            }

            try {
                val info = fetchYtDlpVideoInfo(canonicalUrl, cookiesFile)
                val mapped = mapVideoInfoToResult(normalizedUrl, canonicalUrl, platform, info)
                if (mapped != null) return@withContext mapped
            } catch (t: Throwable) {
                val rawErr = t.message ?: t.javaClass.simpleName
                diagLog.appendLine("[yt-dlp] $rawErr")

                if (isDrmMessage(rawErr)) {
                    return@withContext ExtractionResult.DrmProtected(
                        messageAr = "هذا المحتوى محمي بحماية DRM أو مدفوع ولا يدعم التطبيق تجاوز الحماية.",
                        diagnostic = diagLog.toString()
                    )
                }

                if (isAuthRequiredMessage(rawErr) && cookiesFile == null &&
                    (platform == "فيسبوك" || platform == "إنستغرام")
                ) {
                    val sniffed = HeadlessWebViewSniffer.sniffMediaUrl(context, canonicalUrl)
                    if (sniffed != null) {
                        return@withContext buildSniffedResult(normalizedUrl, canonicalUrl, platform, sniffed)
                    }
                    return@withContext ExtractionResult.Failed(
                        friendlyMessageAr = "يتطلب هذا الفيديو تسجيل الدخول من نفس الحساب ($platform). يمكنك تسجيل الدخول محلياً من الإعدادات ثم إعادة المحاولة.",
                        diagnostic = diagLog.toString(),
                        suggestCookieLogin = true
                    )
                }
            }
        } else {
            diagLog.appendLine("[yt-dlp] Engine initialization failed")
        }

        val sniffed = HeadlessWebViewSniffer.sniffMediaUrl(context, canonicalUrl)
        if (sniffed != null) {
            return@withContext buildSniffedResult(normalizedUrl, canonicalUrl, platform, sniffed)
        }
        diagLog.appendLine("[sniffer] No direct video/m3u8/mpd stream captured within timeout")

        val friendly = when {
            diagLog.contains("UnknownHostException", ignoreCase = true) ||
                diagLog.contains("Unable to resolve host", ignoreCase = true) ->
                "تعذر الاتصال بالخادم. تحقق من اتصال الإنترنت ثم أعد المحاولة."
            diagLog.contains("timed out", ignoreCase = true) ->
                "استغرق الموقع وقتاً طويلاً للرد. اضغط «إعادة المحاولة» أو جرب لاحقاً."
            diagLog.contains("Private video", ignoreCase = true) ||
                diagLog.contains("403", ignoreCase = true) ->
                "الفيديو خاص أو مقيد الوصول حالياً من المصدر."
            else ->
                "تعذر استخلاص رابط الفيديو تلقائياً من هذه الصفحة. يمكنك إعادة المحاولة أو فتحه في المتصفح."
        }

        ExtractionResult.Failed(
            friendlyMessageAr = friendly,
            diagnostic = diagLog.toString(),
            suggestCookieLogin = platform == "فيسبوك" || platform == "إنستغرام"
        )
    }

    private fun resolveRedirects(url: String): String {
        val lower = url.lowercase(Locale.ROOT)
        val shouldResolve = listOf(
            "pin.it", "vm.tiktok.com", "vt.tiktok.com", "fb.watch", "t.co/", "youtu.be/", "bit.ly/"
        ).any { lower.contains(it) }
        if (!shouldResolve) return url

        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", MOBILE_USER_AGENT)
                .get()
                .build()
            probeHttpClient.newCall(req).execute().use { resp ->
                resp.request.url.toString().ifBlank { url }
            }
        }.getOrDefault(url)
    }

    private fun probeDirectMediaUrl(url: String): ExtractionResult.SingleVideo? {
        val lowerPath = url.lowercase(Locale.ROOT).substringBefore('?').substringBefore('#')
        if (lowerPath.endsWith(".m3u8") || lowerPath.endsWith(".mpd")) {
            val title = deriveTitleFromUrl(url)
            return ExtractionResult.SingleVideo(
                originalUrl = url,
                resolvedUrl = url,
                title = title,
                thumbnailUrl = null,
                platform = "بث مباشر (HLS/DASH)",
                durationSeconds = 0,
                qualities = listOf(
                    MediaQualityOption(
                        id = "best",
                        labelAr = "أفضل جودة متاحة",
                        subLabel = "بث مباشر متكيف • MP4",
                        resolutionHeight = 1080,
                        ext = "mp4",
                        directUrl = url,
                        isHlsOrDash = true
                    )
                )
            )
        }

        val knownDirectExts = listOf(".mp4", ".mkv", ".webm", ".mov", ".m4v", ".mp3", ".m4a", ".aac", ".flac", ".wav")
        val hasKnownExt = knownDirectExts.any { lowerPath.endsWith(it) }

        return runCatching {
            val headReq = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", MOBILE_USER_AGENT)
                .build()
            probeHttpClient.newCall(headReq).execute().use { resp ->
                val contentType = resp.header("Content-Type")?.lowercase(Locale.ROOT).orEmpty()
                val contentLen = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                val isDirectMedia = contentType.startsWith("video/") ||
                    contentType.startsWith("audio/") ||
                    (contentType.contains("octet-stream") && hasKnownExt) ||
                    (hasKnownExt && !contentType.contains("text/html"))

                if (!resp.isSuccessful || !isDirectMedia) {
                    return@use null
                }

                val isAudio = contentType.startsWith("audio/") || lowerPath.endsWith(".mp3") ||
                    lowerPath.endsWith(".m4a") || lowerPath.endsWith(".aac")
                val ext = knownDirectExts.firstOrNull { lowerPath.endsWith(it) }?.removePrefix(".")
                    ?: if (isAudio) "m4a" else "mp4"

                ExtractionResult.SingleVideo(
                    originalUrl = url,
                    resolvedUrl = resp.request.url.toString(),
                    title = deriveTitleFromUrl(resp.request.url.toString()),
                    thumbnailUrl = null,
                    platform = "رابط مباشر",
                    durationSeconds = 0,
                    qualities = listOf(
                        MediaQualityOption(
                            id = if (isAudio) "audio_$ext" else "direct",
                            labelAr = if (isAudio) "ملف صوتي مباشر (${ext.uppercase(Locale.ROOT)})" else "تحميل مباشر سريع (${ext.uppercase(Locale.ROOT)})",
                            subLabel = "رابط مباشر متعدد الأجزاء",
                            resolutionHeight = if (isAudio) 0 else 1080,
                            isAudioOnly = isAudio,
                            ext = ext,
                            directUrl = resp.request.url.toString(),
                            estimatedBytes = contentLen,
                            isApproximateSize = false
                        )
                    )
                )
            }
        }.getOrNull() ?: if (hasKnownExt) {
            runCatching {
                val rangeReq = Request.Builder()
                    .url(url)
                    .get()
                    .header("Range", "bytes=0-0")
                    .header("User-Agent", MOBILE_USER_AGENT)
                    .build()
                probeHttpClient.newCall(rangeReq).execute().use { resp ->
                    val contentType = resp.header("Content-Type")?.lowercase(Locale.ROOT).orEmpty()
                    if (!resp.isSuccessful || contentType.contains("text/html")) return@use null
                    val rangeTotal = resp.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                        ?: resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    val isAudio = contentType.startsWith("audio/") || lowerPath.endsWith(".mp3") || lowerPath.endsWith(".m4a")
                    val ext = knownDirectExts.firstOrNull { lowerPath.endsWith(it) }?.removePrefix(".") ?: "mp4"
                    ExtractionResult.SingleVideo(
                        originalUrl = url,
                        resolvedUrl = resp.request.url.toString(),
                        title = deriveTitleFromUrl(resp.request.url.toString()),
                        thumbnailUrl = null,
                        platform = "رابط مباشر",
                        durationSeconds = 0,
                        qualities = listOf(
                            MediaQualityOption(
                                id = if (isAudio) "audio_$ext" else "direct",
                                labelAr = if (isAudio) "ملف صوتي مباشر" else "تحميل مباشر سريع",
                                subLabel = ext.uppercase(Locale.ROOT),
                                resolutionHeight = if (isAudio) 0 else 1080,
                                isAudioOnly = isAudio,
                                ext = ext,
                                directUrl = resp.request.url.toString(),
                                estimatedBytes = rangeTotal
                            )
                        )
                    )
                }
            }.getOrNull()
        } else null
    }

    private fun looksLikePlaylistUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return lower.contains("list=") || lower.contains("/playlist") || lower.contains("/sets/")
    }

    private fun tryExtractPlaylist(
        url: String,
        platform: String,
        cookiesFile: File?,
        diagLog: StringBuilder
    ): ExtractionResult.Playlist? {
        return try {
            val req = YoutubeDLRequest(url).apply {
                addOption("--flat-playlist")
                addOption("--no-warnings")
                addOption("--print", "%(title)s\t%(webpage_url)s\t%(duration)s")
                if (cookiesFile != null) addOption("--cookies", cookiesFile.absolutePath)
            }
            val lines = mutableListOf<String>()
            YoutubeDL.getInstance().execute(req, "pl_${System.currentTimeMillis()}") { _, _, line ->
                if (line.contains("\thttp")) {
                    lines.add(line.trim())
                }
            }
            if (lines.size < 2) return null
            val entries = lines.mapIndexedNotNull { idx, raw ->
                val parts = raw.split("\t")
                val entryUrl = parts.getOrNull(1)?.trim() ?: return@mapIndexedNotNull null
                if (!entryUrl.startsWith("http")) return@mapIndexedNotNull null
                val title = parts.getOrNull(0)?.takeIf { it.isNotBlank() && it != "NA" }
                    ?: "فيديو رقم ${idx + 1}"
                val dur = parts.getOrNull(2)?.toDoubleOrNull()?.toInt() ?: 0
                PlaylistEntry(
                    index = idx + 1,
                    title = title,
                    url = entryUrl,
                    durationSeconds = dur,
                    selected = true
                )
            }
            if (entries.size < 2) null else ExtractionResult.Playlist(
                originalUrl = url,
                title = "قائمة تشغيل (${entries.size} عنصر)",
                platform = platform,
                entries = entries
            )
        } catch (t: Throwable) {
            diagLog.appendLine("[playlist] ${t.message}")
            null
        }
    }

    private fun fetchYtDlpVideoInfo(url: String, cookiesFile: File?): VideoInfo {
        val req = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            if (url.contains("tiktok.com", ignoreCase = true)) {
                addOption("--extractor-args", "tiktok:api_hostname=api22-normal-c-useast2a.tiktokv.com")
            }
            if (cookiesFile != null) {
                addOption("--cookies", cookiesFile.absolutePath)
            }
        }
        return YoutubeDL.getInstance().getInfo(req)
    }

    private fun mapVideoInfoToResult(
        originalUrl: String,
        canonicalUrl: String,
        platform: String,
        info: VideoInfo
    ): ExtractionResult.SingleVideo? {
        val formats = info.formats.orEmpty()
        val durationSec = info.duration
        val baseHeaders = info.httpHeaders ?: emptyMap()

        val audioCandidates = formats.filter { fmt ->
            val vcodec = fmt.vcodec?.lowercase(Locale.ROOT) ?: "none"
            val acodec = fmt.acodec?.lowercase(Locale.ROOT) ?: "none"
            (vcodec == "none" || vcodec.isBlank()) && acodec != "none" && !fmt.url.isNullOrBlank()
        }
        val bestAudio = audioCandidates.maxByOrNull { parseSize(it, durationSec).first }

        val targetHeights = listOf(2160, 1440, 1080, 720, 480, 360, 240, 144)
        val builtOptions = mutableListOf<MediaQualityOption>()

        for (h in targetHeights) {
            val matching = formats.filter { fmt ->
                val fh = fmt.height
                val vcodec = fmt.vcodec?.lowercase(Locale.ROOT) ?: ""
                val hasVideo = vcodec != "none" && fh > 0
                hasVideo && fh in (h - 60)..(h + 60) && !fmt.url.isNullOrBlank()
            }
            if (matching.isEmpty()) continue

            val progressive = matching.filter {
                val ac = it.acodec?.lowercase(Locale.ROOT) ?: "none"
                ac != "none" && !isManifestUrl(it.url)
            }.maxByOrNull { (if (it.ext == "mp4") 1L else 0L) * 1_000_000_000L + parseSize(it, durationSec).first }

            val chosen = progressive ?: matching.maxByOrNull {
                val mp4Bonus = if (it.ext == "mp4") 1_000_000_000L else 0L
                mp4Bonus + parseSize(it, durationSec).first
            } ?: continue

            val isHls = isManifestUrl(chosen.url)
            val hasAudio = (chosen.acodec?.lowercase(Locale.ROOT) ?: "none") != "none"
            val needsMux = !hasAudio || isHls
            val (videoSize, videoApprox) = parseSize(chosen, durationSec)
            val (audioSize, audioApprox) = if (!hasAudio && bestAudio != null) {
                parseSize(bestAudio, durationSec)
            } else {
                0L to false
            }
            val totalEst = when {
                videoSize > 0L && audioSize > 0L -> videoSize + audioSize
                videoSize > 0L -> videoSize
                else -> -1L
            }

            val label = when (h) {
                2160 -> "2160p • 4K فائقة الدقة"
                1440 -> "1440p • 2K QHD"
                1080 -> "1080p • Full HD"
                720 -> "720p • HD"
                480 -> "480p • متوسطة"
                360 -> "360p • اقتصادية"
                240 -> "240p • منخفضة"
                else -> "144p • أقل بيانات"
            }
            val modeSub = when {
                isHls -> "بث مجزأ + دمج تلقائي"
                needsMux -> "دمج فيديو + صوت (FFmpeg)"
                else -> "فيديو + صوت • تحميل فائق متعدد الأجزاء"
            }

            builtOptions.add(
                MediaQualityOption(
                    id = "${h}p",
                    labelAr = label,
                    subLabel = modeSub,
                    resolutionHeight = h,
                    isAudioOnly = false,
                    formatId = if (!hasAudio && bestAudio?.formatId != null && chosen.formatId != null) {
                        "${chosen.formatId}+${bestAudio.formatId}/bestvideo[height<=$h]+bestaudio/best[height<=$h]"
                    } else {
                        chosen.formatId ?: "best[height<=$h]"
                    },
                    ext = "mp4",
                    directUrl = if (!needsMux && !isHls) chosen.url.orEmpty() else canonicalUrl,
                    audioUrl = if (!hasAudio) bestAudio?.url else null,
                    estimatedBytes = totalEst,
                    isApproximateSize = videoApprox || audioApprox,
                    needsMux = needsMux,
                    isHlsOrDash = isHls,
                    headers = chosen.httpHeaders ?: baseHeaders
                )
            )
        }

        if (builtOptions.isEmpty()) {
            val bestSingle = formats
                .filter { !it.url.isNullOrBlank() && (it.vcodec?.lowercase(Locale.ROOT) != "none" || formats.size == 1) }
                .maxByOrNull { it.height * 1000L + parseSize(it, durationSec).first }
            val fallbackUrl = bestSingle?.url ?: info.url
            if (fallbackUrl.isNullOrBlank()) return null

            val isHls = isManifestUrl(fallbackUrl)
            val (sz, approx) = if (bestSingle != null) parseSize(bestSingle, durationSec) else (-1L to true)
            builtOptions.add(
                MediaQualityOption(
                    id = if (bestSingle != null && bestSingle.height > 0) "${bestSingle.height}p" else "best",
                    labelAr = if (bestSingle != null && bestSingle.height > 0) "${bestSingle.height}p • الجودة الأصلية" else "أفضل جودة متاحة",
                    subLabel = if (isHls) "بث مجزأ + دمج تلقائي" else "تحميل مباشر سريع",
                    resolutionHeight = bestSingle?.height?.takeIf { it > 0 } ?: 720,
                    isAudioOnly = false,
                    formatId = bestSingle?.formatId ?: "best",
                    ext = bestSingle?.ext?.takeIf { it in listOf("mp4", "webm", "mkv") } ?: "mp4",
                    directUrl = if (!isHls) fallbackUrl else canonicalUrl,
                    estimatedBytes = sz,
                    isApproximateSize = approx,
                    needsMux = isHls,
                    isHlsOrDash = isHls,
                    headers = bestSingle?.httpHeaders ?: baseHeaders
                )
            )
        }

        val (audioSz, audioApprx) = bestAudio?.let { parseSize(it, durationSec) } ?: (-1L to true)
        val directAudioUrl = bestAudio?.url?.takeIf { !isManifestUrl(it) }
        builtOptions.add(
            MediaQualityOption(
                id = "audio_m4a",
                labelAr = "صوت فقط • M4A عالي النقاء",
                subLabel = "صوت أصلي سريع بدون فيديو",
                resolutionHeight = 0,
                isAudioOnly = true,
                formatId = bestAudio?.formatId ?: "bestaudio/best",
                ext = "m4a",
                directUrl = directAudioUrl ?: canonicalUrl,
                estimatedBytes = audioSz,
                isApproximateSize = audioApprx,
                needsMux = directAudioUrl == null,
                isHlsOrDash = directAudioUrl == null,
                headers = bestAudio?.httpHeaders ?: baseHeaders
            )
        )
        builtOptions.add(
            MediaQualityOption(
                id = "audio_mp3",
                labelAr = "صوت فقط • MP3",
                subLabel = "تحويل تلقائي متوافق مع كل المشغلات",
                resolutionHeight = 0,
                isAudioOnly = true,
                formatId = "bestaudio/best",
                ext = "mp3",
                directUrl = canonicalUrl,
                estimatedBytes = audioSz,
                isApproximateSize = true,
                needsMux = true,
                isHlsOrDash = true,
                headers = baseHeaders
            )
        )

        val title = info.title?.takeIf { it.isNotBlank() } ?: deriveTitleFromUrl(canonicalUrl)
        return ExtractionResult.SingleVideo(
            originalUrl = originalUrl,
            resolvedUrl = canonicalUrl,
            title = title,
            thumbnailUrl = info.thumbnail,
            platform = platform,
            durationSeconds = durationSec,
            qualities = builtOptions,
            referer = canonicalUrl,
            headers = baseHeaders
        )
    }

    private fun parseSize(format: VideoFormat, durationSec: Int): Pair<Long, Boolean> {
        val exact = format.fileSize
        if (exact > 0L) return exact to false
        val approx = format.fileSizeApproximate
        if (approx > 0L) return approx to true
        val tbrKbps = format.tbr.toString().toDoubleOrNull() ?: 0.0
        if (tbrKbps > 0.0 && durationSec > 0) {
            val est = ((tbrKbps * 1000.0 / 8.0) * durationSec).toLong()
            if (est > 0L) return est to true
        }
        return -1L to true
    }

    private fun isManifestUrl(url: String?): Boolean {
        val lower = url?.lowercase(Locale.ROOT) ?: return false
        return lower.contains(".m3u8") || lower.contains(".mpd") || lower.contains("manifest")
    }

    private fun buildSniffedResult(
        originalUrl: String,
        canonicalUrl: String,
        platform: String,
        sniffed: SniffedMediaResult
    ): ExtractionResult.SingleVideo {
        val isAudio = sniffed.mimeType.startsWith("audio/")
        return ExtractionResult.SingleVideo(
            originalUrl = originalUrl,
            resolvedUrl = canonicalUrl,
            title = deriveTitleFromUrl(canonicalUrl),
            thumbnailUrl = null,
            platform = platform,
            durationSeconds = 0,
            qualities = listOf(
                MediaQualityOption(
                    id = if (isAudio) "audio_m4a" else "best",
                    labelAr = if (isAudio) "صوت مستخلص" else "جودة الفيديو المتاحة",
                    subLabel = if (sniffed.isHlsOrDash) "بث مباشر (FFmpeg)" else "رابط مباشر سريع",
                    resolutionHeight = if (isAudio) 0 else 720,
                    isAudioOnly = isAudio,
                    formatId = "best",
                    ext = if (isAudio) "m4a" else "mp4",
                    directUrl = if (!sniffed.isHlsOrDash) sniffed.mediaUrl else canonicalUrl,
                    estimatedBytes = -1L,
                    isApproximateSize = true,
                    needsMux = sniffed.isHlsOrDash,
                    isHlsOrDash = sniffed.isHlsOrDash,
                    headers = sniffed.headers
                )
            ),
            referer = canonicalUrl,
            headers = sniffed.headers
        )
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

    private fun deriveTitleFromUrl(url: String): String {
        val segment = url.substringBefore('?').substringBefore('#')
            .substringAfterLast('/')
            .substringBeforeLast('.')
            .replace('-', ' ')
            .replace('_', ' ')
            .trim()
        return segment.takeIf { it.length >= 3 && !it.all { ch -> ch.isDigit() } }?.take(60)
            ?: "فيديو_هوس_الدراسة_${System.currentTimeMillis() % 10000}"
    }

    private fun isDrmMessage(msg: String): Boolean {
        val lower = msg.lowercase(Locale.ROOT)
        return lower.contains("drm") || lower.contains("widevine") ||
            lower.contains("playready") || lower.contains("fairplay") ||
            lower.contains("paid content") || lower.contains("members-only") ||
            lower.contains("purchase")
    }

    private fun isAuthRequiredMessage(msg: String): Boolean {
        val lower = msg.lowercase(Locale.ROOT)
        return lower.contains("login") || lower.contains("cookies") ||
            lower.contains("sign in") || lower.contains("logged in") ||
            lower.contains("authentication")
    }

    fun mapThrowableToFriendlyError(t: Throwable): Pair<String, String> {
        val diag = buildString {
            appendLine(t.javaClass.name + ": " + (t.message ?: ""))
            t.cause?.let { appendLine("Cause: ${it.javaClass.name}: ${it.message}") }
        }
        val msg = (t.message ?: "").lowercase(Locale.ROOT)
        val friendly = when {
            t is UnknownHostException || msg.contains("unable to resolve host") ->
                "انقطع الاتصال بالإنترنت أو تعذر الوصول للخادم."
            t is SocketTimeoutException || msg.contains("timeout") ->
                "انتهت مهلة الاتصال بالخادم، حاول مرة أخرى."
            isDrmMessage(msg) ->
                "هذا المحتوى محمي بحماية DRM أو مدفوع ولا يدعم التطبيق تجاوز الحماية."
            msg.contains("enos") || msg.contains("no space") || msg.contains("space") ->
                "المساحة التخزينية على الهاتف غير كافية لحفظ هذا الملف."
            msg.contains("403") || msg.contains("410") ->
                "انتهت صلاحية رابط التحميل من المصدر، اضغط «إعادة المحاولة» لتحديثه."
            else ->
                "تعذر إكمال العملية حالياً. يمكنك إعادة المحاولة أو الاطلاع على سجل التشخيص."
        }
        return friendly to diag
    }
}
