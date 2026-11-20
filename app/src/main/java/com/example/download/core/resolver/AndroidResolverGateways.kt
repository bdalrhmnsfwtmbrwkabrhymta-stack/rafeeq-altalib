package com.example.download.core.resolver

import android.content.Context
import com.example.download.core.model.ResolvedCollectionEntry
import com.example.download.core.model.ResolvedMediaFormat
import com.example.download.extract.HeadlessWebViewSniffer
import com.example.download.extract.SniffedMediaResult
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
import java.io.File
import java.util.Locale

class AndroidYtDlpGateway(private val context: Context) : YtDlpGateway {

    private val appContext = context.applicationContext
    private val initMutex = Mutex()
    @Volatile
    private var initialized = false

    suspend fun ensureInitialized(): Boolean {
        if (initialized) return true
        return initMutex.withLock {
            if (initialized) return@withLock true
            withContext(Dispatchers.IO) {
                try {
                    YoutubeDL.getInstance().init(appContext)
                    runCatching { FFmpeg.getInstance().init(appContext) }
                    runCatching { Aria2c.getInstance().init(appContext) }
                    initialized = true
                    true
                } catch (_: Throwable) {
                    false
                }
            }
        }
    }

    override suspend fun isAvailable(): Boolean = ensureInitialized()

    override suspend fun extractSingle(
        url: String,
        cookiesFile: File?,
        extraArgs: Map<String, String>
    ): Result<YtDlpExtractedInfo> = withContext(Dispatchers.IO) {
        if (!ensureInitialized()) {
            return@withContext Result.failure(IllegalStateException("yt-dlp initialization failed"))
        }
        runCatching {
            val req = YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                addOption("--no-warnings")
                if (url.contains("tiktok.com", ignoreCase = true) && !extraArgs.containsKey("extractor-args")) {
                    addOption("--extractor-args", "tiktok:api_hostname=api22-normal-c-useast2a.tiktokv.com")
                }
                extraArgs.forEach { (k, v) -> addOption("--$k", v) }
                if (cookiesFile != null && cookiesFile.exists() && cookiesFile.length() > 20L) {
                    addOption("--cookies", cookiesFile.absolutePath)
                }
            }
            val info = YoutubeDL.getInstance().getInfo(req)
            mapVideoInfo(url, info)
        }
    }

    override suspend fun extractCollection(
        url: String,
        cookiesFile: File?,
        maxItems: Int
    ): Result<List<ResolvedCollectionEntry>> = withContext(Dispatchers.IO) {
        if (!ensureInitialized()) {
            return@withContext Result.failure(IllegalStateException("yt-dlp initialization failed"))
        }
        runCatching {
            val req = YoutubeDLRequest(url).apply {
                addOption("--flat-playlist")
                addOption("--no-warnings")
                addOption("--playlist-end", maxItems.coerceIn(1, 200).toString())
                addOption("--print", "%(id)s\t%(title)s\t%(webpage_url)s\t%(duration)s")
                if (cookiesFile != null && cookiesFile.exists() && cookiesFile.length() > 20L) {
                    addOption("--cookies", cookiesFile.absolutePath)
                }
            }
            val lines = mutableListOf<String>()
            YoutubeDL.getInstance().execute(req, "col_${System.currentTimeMillis()}") { _, _, line ->
                if (line.contains("\thttp")) {
                    lines.add(line.trim())
                }
            }
            lines.mapIndexedNotNull { idx, raw ->
                val parts = raw.split("\t")
                val entryUrl = parts.getOrNull(2)?.trim() ?: return@mapIndexedNotNull null
                if (!entryUrl.startsWith("http", true)) return@mapIndexedNotNull null
                val itemId = parts.getOrNull(0)?.takeIf { it.isNotBlank() && it != "NA" } ?: "item_${idx + 1}"
                val title = parts.getOrNull(1)?.takeIf { it.isNotBlank() && it != "NA" } ?: "فيديو رقم ${idx + 1}"
                val dur = parts.getOrNull(3)?.toDoubleOrNull()?.toInt() ?: 0
                ResolvedCollectionEntry(
                    index = idx + 1,
                    itemId = itemId,
                    title = title,
                    url = entryUrl,
                    durationSeconds = dur,
                    selected = true
                )
            }
        }
    }

    private fun mapVideoInfo(canonicalUrl: String, info: VideoInfo): YtDlpExtractedInfo {
        val rawFormats = info.formats.orEmpty()
        val durationSec = info.duration
        val baseHeaders = info.httpHeaders ?: emptyMap()

        val audioCandidates = rawFormats.filter { fmt ->
            val vcodec = fmt.vcodec?.lowercase(Locale.ROOT) ?: "none"
            val acodec = fmt.acodec?.lowercase(Locale.ROOT) ?: "none"
            (vcodec == "none" || vcodec.isBlank()) && acodec != "none" && !fmt.url.isNullOrBlank()
        }
        val bestAudio = audioCandidates.maxByOrNull { parseSize(it, durationSec).first }

        val targetHeights = listOf(2160, 1440, 1080, 720, 480, 360, 240, 144)
        val builtOptions = mutableListOf<ResolvedMediaFormat>()

        for (h in targetHeights) {
            val matching = rawFormats.filter { fmt ->
                val fh = fmt.height
                val vcodec = fmt.vcodec?.lowercase(Locale.ROOT) ?: ""
                val hasVideo = vcodec != "none" && fh > 0
                hasVideo && fh in (h - 60)..(h + 60) && !fmt.url.isNullOrBlank()
            }
            if (matching.isEmpty()) continue

            val progressive = matching.filter {
                val ac = it.acodec?.lowercase(Locale.ROOT) ?: "none"
                ac != "none" && !ResolverHttpUtils.isManifestUrl(it.url)
            }.maxByOrNull { (if (it.ext == "mp4") 1L else 0L) * 1_000_000_000L + parseSize(it, durationSec).first }

            val chosen = progressive ?: matching.maxByOrNull {
                val mp4Bonus = if (it.ext == "mp4") 1_000_000_000L else 0L
                mp4Bonus + parseSize(it, durationSec).first
            } ?: continue

            val isHls = ResolverHttpUtils.isManifestUrl(chosen.url)
            val isDash = chosen.url?.contains(".mpd", ignoreCase = true) == true
            val hasAudio = (chosen.acodec?.lowercase(Locale.ROOT) ?: "none") != "none"
            val needsMux = !hasAudio || isHls || isDash
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

            val labelAr = when (h) {
                2160 -> "2160p • 4K فائقة الدقة"
                1440 -> "1440p • 2K QHD"
                1080 -> "1080p • Full HD"
                720 -> "720p • HD"
                480 -> "480p • متوسطة"
                360 -> "360p • اقتصادية"
                240 -> "240p • منخفضة"
                else -> "144p • أقل بيانات"
            }
            val subAr = when {
                isHls || isDash -> "بث مجزأ + دمج تلقائي"
                needsMux -> "دمج فيديو + صوت (FFmpeg)"
                else -> "فيديو + صوت • تحميل فائق متعدد الأجزاء"
            }
            val formatExpr = if (!hasAudio && bestAudio?.formatId != null && chosen.formatId != null) {
                "${chosen.formatId}+${bestAudio.formatId}/bestvideo[height<=$h]+bestaudio/best[height<=$h]"
            } else {
                chosen.formatId ?: "best[height<=$h]"
            }

            builtOptions += ResolvedMediaFormat(
                id = "${h}p",
                labelAr = labelAr,
                labelEn = "${h}p",
                subLabelAr = subAr,
                containerExt = "mp4",
                mimeType = "video/mp4",
                resolutionHeight = h,
                bitrateKbps = (chosen.tbr.toString().toDoubleOrNull() ?: 0.0).toInt(),
                streamUrl = chosen.url.orEmpty(),
                audioStreamUrl = if (!hasAudio) bestAudio?.url else null,
                formatExpression = formatExpr,
                exactBytes = totalEst,
                isApproximateBytes = videoApprox || audioApprox,
                isAudioOnly = false,
                isHls = isHls,
                isDash = isDash,
                needsMux = needsMux,
                headers = chosen.httpHeaders ?: baseHeaders,
                badgeAr = when {
                    h >= 2160 -> "الأوضح"
                    h == 1440 -> "2K فائق"
                    h == 1080 -> "Full HD"
                    h == 720 -> "HD"
                    h <= 144 -> "أقل استهلاكاً للبيانات"
                    else -> null
                },
                badgeEn = when {
                    h >= 2160 -> "Highest"
                    h == 1440 -> "2K QHD"
                    h == 1080 -> "Full HD"
                    h == 720 -> "HD"
                    h <= 144 -> "Data Saver"
                    else -> null
                }
            )
        }

        if (builtOptions.isEmpty()) {
            val bestSingle = rawFormats
                .filter { !it.url.isNullOrBlank() && (it.vcodec?.lowercase(Locale.ROOT) != "none" || rawFormats.size == 1) }
                .maxByOrNull { it.height * 1000L + parseSize(it, durationSec).first }
            val fallbackUrl = bestSingle?.url ?: info.url
            if (!fallbackUrl.isNullOrBlank()) {
                val isHls = ResolverHttpUtils.isManifestUrl(fallbackUrl)
                val (sz, approx) = if (bestSingle != null) parseSize(bestSingle, durationSec) else (-1L to true)
                builtOptions += ResolvedMediaFormat(
                    id = if (bestSingle != null && bestSingle.height > 0) "${bestSingle.height}p" else "best",
                    labelAr = if (bestSingle != null && bestSingle.height > 0) "${bestSingle.height}p • الجودة الأصلية" else "أفضل جودة متاحة",
                    labelEn = if (bestSingle != null && bestSingle.height > 0) "${bestSingle.height}p" else "Best",
                    subLabelAr = if (isHls) "بث مجزأ + دمج تلقائي" else "تحميل مباشر سريع",
                    containerExt = bestSingle?.ext?.takeIf { it in listOf("mp4", "webm", "mkv") } ?: "mp4",
                    mimeType = "video/mp4",
                    resolutionHeight = bestSingle?.height?.takeIf { it > 0 } ?: 720,
                    streamUrl = fallbackUrl,
                    formatExpression = bestSingle?.formatId ?: "best",
                    exactBytes = sz,
                    isApproximateBytes = approx,
                    isHls = isHls,
                    needsMux = isHls,
                    headers = bestSingle?.httpHeaders ?: baseHeaders
                )
            }
        }

        if (builtOptions.isNotEmpty() || bestAudio != null) {
            val (audioSz, audioApprox) = bestAudio?.let { parseSize(it, durationSec) } ?: (-1L to true)
            val directAudioUrl = bestAudio?.url?.takeIf { !ResolverHttpUtils.isManifestUrl(it) }
            builtOptions += ResolvedMediaFormat(
                id = "audio_m4a",
                labelAr = "صوت فقط • M4A عالي النقاء",
                labelEn = "M4A",
                subLabelAr = "صوت أصلي سريع بدون فيديو",
                containerExt = "m4a",
                mimeType = "audio/mp4",
                streamUrl = directAudioUrl ?: canonicalUrl,
                formatExpression = bestAudio?.formatId ?: "bestaudio/best",
                exactBytes = audioSz,
                isApproximateBytes = audioApprox,
                isAudioOnly = true,
                isHls = directAudioUrl == null,
                needsMux = directAudioUrl == null,
                headers = bestAudio?.httpHeaders ?: baseHeaders,
                badgeAr = "صوت أصلي",
                badgeEn = "Original Audio"
            )
            builtOptions += ResolvedMediaFormat(
                id = "audio_mp3",
                labelAr = "صوت فقط • MP3",
                labelEn = "MP3",
                subLabelAr = "تحويل تلقائي متوافق مع كل المشغلات",
                containerExt = "mp3",
                mimeType = "audio/mpeg",
                bitrateKbps = 320,
                streamUrl = canonicalUrl,
                formatExpression = "bestaudio/best",
                exactBytes = audioSz,
                isApproximateBytes = true,
                isAudioOnly = true,
                isHls = true,
                needsMux = true,
                headers = baseHeaders,
                badgeAr = "للمحاضرات",
                badgeEn = "For Lectures"
            )
        }

        return YtDlpExtractedInfo(
            title = info.title?.takeIf { it.isNotBlank() } ?: ResolverHttpUtils.deriveTitleFromUrl(canonicalUrl),
            uploader = info.uploader,
            thumbnailUrl = info.thumbnail,
            durationSeconds = durationSec,
            baseHeaders = baseHeaders,
            formats = builtOptions
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
}

class AndroidWebViewSniffGateway(private val context: Context) : WebViewSniffGateway {
    private val appContext = context.applicationContext
    override suspend fun sniff(url: String, timeoutMs: Long): SniffedMediaResult? =
        HeadlessWebViewSniffer.sniffMediaUrl(appContext, url, timeoutMs)
}
