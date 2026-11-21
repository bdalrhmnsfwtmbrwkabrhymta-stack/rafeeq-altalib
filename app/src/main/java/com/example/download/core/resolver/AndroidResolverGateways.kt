package com.example.download.core.resolver

import android.content.Context
import com.example.download.core.media.MediaMergeEngine
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
import org.json.JSONArray
import org.json.JSONObject
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
                    runCatching { MediaMergeEngine.ensureRuntimeReady(appContext) }
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

        val variantsToTry: List<Map<String, String>> = if (
            url.contains("tiktok.com", ignoreCase = true) &&
            !extraArgs.containsKey("extractor-args")
        ) {
            listOf(
                extraArgs,
                extraArgs + ("extractor-args" to "tiktok:api_hostname=api22-normal-c-useast2a.tiktokv.com"),
                extraArgs + ("extractor-args" to "tiktok:api_hostname=api16-normal-c-useast1a.tiktokv.com")
            )
        } else {
            listOf(extraArgs)
        }

        var lastError: Throwable? = null
        for (variantArgs in variantsToTry) {
            val attempt = runCatching {
                extractWithDumpSingleJson(url, cookiesFile, variantArgs)
                    ?: extractFallbackGetInfo(url, cookiesFile, variantArgs)
            }
            val info = attempt.getOrNull()
            if (info != null && info.formats.isNotEmpty()) {
                return@withContext Result.success(info)
            }
            lastError = attempt.exceptionOrNull()
        }
        Result.failure(lastError ?: IllegalStateException("yt-dlp returned no formats for $url"))
    }

    private fun extractWithDumpSingleJson(
        url: String,
        cookiesFile: File?,
        args: Map<String, String>
    ): YtDlpExtractedInfo? {
        val req = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--dump-single-json")
            args.forEach { (k, v) -> addOption("--$k", v) }
            if (cookiesFile != null && cookiesFile.exists() && cookiesFile.length() > 20L) {
                addOption("--cookies", cookiesFile.absolutePath)
            }
        }
        val stdoutLines = mutableListOf<String>()
        YoutubeDL.getInstance().execute(req, "probe_${System.nanoTime()}") { _, _, line ->
            val trimmed = line?.trim().orEmpty()
            if (trimmed.startsWith("{")) {
                stdoutLines.add(trimmed)
            }
        }
        val jsonLine = stdoutLines.lastOrNull() ?: return null
        val jsonObj = JSONObject(jsonLine)
        return parseRawYtDlpJson(url, jsonObj)
    }

    private fun extractFallbackGetInfo(
        url: String,
        cookiesFile: File?,
        args: Map<String, String>
    ): YtDlpExtractedInfo {
        val req = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            args.forEach { (k, v) -> addOption("--$k", v) }
            if (cookiesFile != null && cookiesFile.exists() && cookiesFile.length() > 20L) {
                addOption("--cookies", cookiesFile.absolutePath)
            }
        }
        val info = YoutubeDL.getInstance().getInfo(req)
        return mapVideoInfo(url, info)
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

    internal data class RawFormatNode(
        val formatId: String,
        val ext: String,
        val url: String,
        val width: Int,
        val height: Int,
        val fps: Int,
        val vcodec: String,
        val acodec: String,
        val protocol: String,
        val formatNote: String,
        val language: String,
        val languagePreference: Int,
        val isDubbed: Boolean,
        val isOriginal: Boolean,
        val tbrKbps: Double,
        val abrKbps: Double,
        val vbrKbps: Double,
        val filesize: Long,
        val filesizeApprox: Long,
        val headers: Map<String, String>
    ) {
        val hasVideo: Boolean get() = vcodec.isNotBlank() && vcodec != "none"
        val hasAudio: Boolean get() = acodec.isNotBlank() && acodec != "none"
        val isVideoOnly: Boolean get() = hasVideo && !hasAudio
        val isAudioOnly: Boolean get() = !hasVideo && hasAudio
        val effectiveHeight: Int
            get() = when {
                width > 0 && height > 0 -> minOf(width, height)
                height > 0 -> height
                else -> 0
            }

        fun resolvedSizeBytes(durationSec: Int): Pair<Long, Boolean> {
            if (filesize > 0L) return filesize to false
            if (filesizeApprox > 0L) return filesizeApprox to true
            if (tbrKbps > 0.0 && durationSec > 0) {
                val est = ((tbrKbps * 1000.0 / 8.0) * durationSec).toLong()
                if (est > 0L) return est to true
            }
            return -1L to true
        }
    }

    internal fun parseRawYtDlpJson(canonicalUrl: String, info: JSONObject): YtDlpExtractedInfo {
        val durationSec = info.optDouble("duration", 0.0).toInt().coerceAtLeast(0)
        val baseHeaders = jsonObjectToHeaderMap(info.optJSONObject("http_headers"))
        val rawArr = info.optJSONArray("formats") ?: JSONArray()

        val nodes = buildList {
            for (i in 0 until rawArr.length()) {
                val f = rawArr.optJSONObject(i) ?: continue
                val streamUrl = f.optString("url").trim()
                if (!streamUrl.startsWith("http", true)) continue
                val protocol = f.optString("protocol").lowercase(Locale.ROOT)
                // Skip non-media storyboard image grids
                val ext = f.optString("ext").lowercase(Locale.ROOT)
                if (ext == "mhtml" || protocol == "mhtml") continue

                val note = f.optString("format_note").trim()
                val lang = f.optString("language").trim().lowercase(Locale.ROOT)
                val langPref = f.optInt("language_preference", 0)
                val lowerNote = note.lowercase(Locale.ROOT)
                val isDubbed = lowerNote.contains("dub") || lowerNote.contains("مدبلج") || lowerNote.contains("auto")
                val isOrig = lowerNote.contains("original") || langPref >= 10

                add(
                    RawFormatNode(
                        formatId = f.optString("format_id").ifBlank { "fmt_$i" },
                        ext = ext.ifBlank { "mp4" },
                        url = streamUrl,
                        width = f.optInt("width", 0),
                        height = f.optInt("height", 0),
                        fps = f.optDouble("fps", 0.0).toInt().coerceAtLeast(0),
                        vcodec = f.optString("vcodec").lowercase(Locale.ROOT).ifBlank { "none" },
                        acodec = f.optString("acodec").lowercase(Locale.ROOT).ifBlank { "none" },
                        protocol = protocol,
                        formatNote = note,
                        language = lang,
                        languagePreference = langPref,
                        isDubbed = isDubbed,
                        isOriginal = isOrig,
                        tbrKbps = f.optDouble("tbr", 0.0).takeIf { !it.isNaN() } ?: 0.0,
                        abrKbps = f.optDouble("abr", 0.0).takeIf { !it.isNaN() } ?: 0.0,
                        vbrKbps = f.optDouble("vbr", 0.0).takeIf { !it.isNaN() } ?: 0.0,
                        filesize = f.optLong("filesize", -1L).takeIf { it > 0L } ?: -1L,
                        filesizeApprox = f.optLong("filesize_approx", -1L).takeIf { it > 0L } ?: -1L,
                        headers = jsonObjectToHeaderMap(f.optJSONObject("http_headers")).ifEmpty { baseHeaders }
                    )
                )
            }
        }

        val audioNodes = nodes.filter { it.isAudioOnly }
        val distinctAudioByLang = selectDistinctAudioByLanguage(audioNodes, durationSec)
        val primaryAudio = selectPrimaryAudioTrack(audioNodes, durationSec)

        // Check if multiple distinct language audio tracks exist (e.g. MrBeast dubbed Arabic + Original English)
        val multiLangAudioTracks = if (distinctAudioByLang.size > 1) distinctAudioByLang else emptyList()

        // Group real video streams by (effectiveHeight, fpsBucket) keeping every authentic resolution
        val videoNodes = nodes.filter { it.hasVideo }
        val builtOptions = mutableListOf<ResolvedMediaFormat>()
        val seenFormatKeys = HashSet<String>()

        val groupedVideos = videoNodes
            .groupBy { node ->
                val res = node.effectiveHeight
                val fpsTag = if (node.fps >= 48) 60 else 30
                res to fpsTag
            }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<Pair<Int, Int>, List<RawFormatNode>>> { it.key.first }
                    .thenByDescending { it.key.second }
            )

        for ((key, group) in groupedVideos) {
            val (resHeight, fpsTag) = key
            // Prefer muxed (hasAudio && hasVideo) non-manifest progressive MP4 first ONLY if we don't need DASH higher quality
            val progressive = group
                .filter { it.hasAudio && !ResolverHttpUtils.isManifestUrl(it.url) }
                .maxByOrNull { (if (it.ext == "mp4") 1_000_000_000L else 0L) + it.resolvedSizeBytes(durationSec).first.coerceAtLeast(0L) + (it.tbrKbps * 100).toLong() }

            val bestVideoOnly = group
                .maxByOrNull {
                    val h264Bonus = if (it.vcodec.startsWith("avc")) 2_000_000_000L else if (it.ext == "mp4") 1_000_000_000L else 0L
                    h264Bonus + it.resolvedSizeBytes(durationSec).first.coerceAtLeast(0L) + (it.tbrKbps * 100).toLong()
                }

            val chosen = progressive ?: bestVideoOnly ?: continue
            val chosenHasOwnAudio = chosen.hasAudio
            val pairedAudio = if (!chosenHasOwnAudio) primaryAudio else null

            val isHls = chosen.protocol.contains("m3u8") || ResolverHttpUtils.isManifestUrl(chosen.url) && !chosen.url.contains(".mpd", true)
            val isDash = chosen.protocol.contains("dash") || chosen.url.contains(".mpd", true)
            val needsMux = !chosenHasOwnAudio && pairedAudio != null || isHls || isDash
            val effectivelyHasAudio = chosenHasOwnAudio || pairedAudio != null

            val (vSize, vApprox) = chosen.resolvedSizeBytes(durationSec)
            val (aSize, aApprox) = pairedAudio?.resolvedSizeBytes(durationSec) ?: (0L to false)
            val totalEst = when {
                vSize > 0L && aSize > 0L -> vSize + aSize
                vSize > 0L -> vSize
                else -> -1L
            }

            val fpsSuffix = if (fpsTag >= 60 && chosen.fps >= 48) " ${chosen.fps}fps" else ""
            val resLabel = if (resHeight > 0) "${resHeight}p$fpsSuffix" else chosen.formatNote.ifBlank { chosen.ext.uppercase(Locale.ROOT) }
            val primaryLangLabel = buildLanguageDisplayLabel(pairedAudio ?: chosen)

            val formatExpr = if (!chosenHasOwnAudio && pairedAudio != null) {
                "${chosen.formatId}+${pairedAudio.formatId}"
            } else {
                chosen.formatId
            }

            val dedupKey = "v_${resHeight}_${fpsTag}_${pairedAudio?.language.orEmpty()}_${chosen.ext}"
            if (seenFormatKeys.add(dedupKey)) {
                val baseId = if (resHeight > 0) {
                    if (fpsTag >= 60 && chosen.fps >= 48 && builtOptions.any { it.resolutionHeight == resHeight }) {
                        "${resHeight}p_${chosen.fps}"
                    } else "${resHeight}p"
                } else {
                    chosen.formatId
                }

                builtOptions += ResolvedMediaFormat(
                    id = baseId,
                    labelAr = if (resHeight > 0) qualityLabelAr(resHeight, chosen.fps) else resLabel,
                    labelEn = resLabel,
                    subLabelAr = when {
                        !effectivelyHasAudio -> "فيديو فقط (بدون صوت في المصدر)"
                        isHls || isDash -> "بث مجزأ + دمج تلقائي"
                        needsMux -> "دمج فيديو + صوت (FFmpeg)"
                        else -> "فيديو + صوت • تحميل مباشر"
                    },
                    containerExt = if (needsMux) "mp4" else chosen.ext.takeIf { it in setOf("mp4", "webm", "mkv") } ?: "mp4",
                    mimeType = "video/mp4",
                    resolutionHeight = resHeight,
                    bitrateKbps = chosen.tbrKbps.toInt(),
                    streamUrl = chosen.url,
                    audioStreamUrl = if (!chosenHasOwnAudio) pairedAudio?.url else null,
                    formatExpression = formatExpr,
                    exactBytes = totalEst,
                    isApproximateBytes = vApprox || aApprox || totalEst <= 0L,
                    isAudioOnly = false,
                    isHls = isHls,
                    isDash = isDash,
                    needsMux = needsMux,
                    headers = chosen.headers,
                    badgeAr = badgeArForHeight(resHeight),
                    badgeEn = badgeEnForHeight(resHeight),
                    width = chosen.width,
                    fps = chosen.fps,
                    vcodec = chosen.vcodec,
                    acodec = pairedAudio?.acodec ?: chosen.acodec,
                    protocol = chosen.protocol,
                    languageCode = pairedAudio?.language ?: chosen.language,
                    languageLabel = primaryLangLabel,
                    isAutoDubbed = pairedAudio?.isDubbed ?: chosen.isDubbed,
                    isOriginalAudio = pairedAudio?.isOriginal ?: chosen.isOriginal,
                    hasVideo = true,
                    hasAudio = effectivelyHasAudio
                )
            }

            // If multi-language audio tracks exist (e.g. Arabic dubbed + English original on YouTube),
            // also expose alternate language variants cleanly for HD/FullHD tiers so user can pick dubbed or original.
            if (multiLangAudioTracks.isNotEmpty() && bestVideoOnly != null && resHeight >= 720) {
                for (altAudio in multiLangAudioTracks) {
                    if (pairedAudio != null && altAudio.formatId == pairedAudio.formatId) continue
                    if (altAudio.language == pairedAudio?.language && altAudio.isDubbed == pairedAudio.isDubbed) continue
                    val altLang = altAudio.language.ifBlank { "alt" }
                    val altKey = "v_${resHeight}_${fpsTag}_${altLang}_${altAudio.isDubbed}"
                    if (!seenFormatKeys.add(altKey)) continue
                    val (avSz, avApprox) = bestVideoOnly.resolvedSizeBytes(durationSec)
                    val (aaSz, aaApprox) = altAudio.resolvedSizeBytes(durationSec)
                    val altEst = if (avSz > 0L && aaSz > 0L) avSz + aaSz else avSz
                    val altLabel = buildLanguageDisplayLabel(altAudio)
                    builtOptions += ResolvedMediaFormat(
                        id = "${resHeight}p_${altLang}",
                        labelAr = "${qualityLabelAr(resHeight, bestVideoOnly.fps)} • $altLabel",
                        labelEn = "${resHeight}p ($altLang)",
                        subLabelAr = "دمج فيديو + مسار $altLabel (FFmpeg)",
                        containerExt = "mp4",
                        mimeType = "video/mp4",
                        resolutionHeight = resHeight,
                        bitrateKbps = bestVideoOnly.tbrKbps.toInt(),
                        streamUrl = bestVideoOnly.url,
                        audioStreamUrl = altAudio.url,
                        formatExpression = "${bestVideoOnly.formatId}+${altAudio.formatId}",
                        exactBytes = altEst,
                        isApproximateBytes = avApprox || aaApprox || altEst <= 0L,
                        isAudioOnly = false,
                        needsMux = true,
                        headers = bestVideoOnly.headers,
                        badgeAr = if (altAudio.language.startsWith("ar")) "مدبلج عربي" else altLang.uppercase(Locale.ROOT),
                        badgeEn = altLang.uppercase(Locale.ROOT),
                        width = bestVideoOnly.width,
                        fps = bestVideoOnly.fps,
                        vcodec = bestVideoOnly.vcodec,
                        acodec = altAudio.acodec,
                        protocol = bestVideoOnly.protocol,
                        languageCode = altAudio.language,
                        languageLabel = altLabel,
                        isAutoDubbed = altAudio.isDubbed,
                        isOriginalAudio = altAudio.isOriginal,
                        hasVideo = true,
                        hasAudio = true
                    )
                }
            }
        }

        // Single-URL fallback when formats array is empty in yt-dlp payload
        if (builtOptions.isEmpty()) {
            val topUrl = info.optString("url").takeIf { it.startsWith("http", true) }
            if (topUrl != null) {
                val topW = info.optInt("width", 0)
                val topH = info.optInt("height", 0)
                val effH = if (topW > 0 && topH > 0) minOf(topW, topH) else topH.coerceAtLeast(0)
                val isHls = ResolverHttpUtils.isManifestUrl(topUrl)
                val topVcodec = info.optString("vcodec").lowercase(Locale.ROOT)
                val topAcodec = info.optString("acodec").lowercase(Locale.ROOT)
                builtOptions += ResolvedMediaFormat(
                    id = if (effH > 0) "${effH}p" else "best",
                    labelAr = if (effH > 0) qualityLabelAr(effH, 0) else "أفضل جودة متاحة",
                    labelEn = if (effH > 0) "${effH}p" else "Best",
                    subLabelAr = if (isHls) "بث مجزأ + دمج تلقائي" else "تحميل مباشر سريع",
                    containerExt = info.optString("ext").takeIf { it in setOf("mp4", "webm", "mkv") } ?: "mp4",
                    mimeType = "video/mp4",
                    resolutionHeight = effH,
                    streamUrl = topUrl,
                    formatExpression = info.optString("format_id").ifBlank { "best" },
                    isHls = isHls,
                    needsMux = isHls,
                    headers = baseHeaders,
                    width = topW,
                    vcodec = topVcodec,
                    acodec = topAcodec,
                    hasVideo = true,
                    hasAudio = topAcodec != "none"
                )
            }
        }

        // Audio-only options (preserving multi-language choices if present + M4A & MP3)
        if (primaryAudio != null || builtOptions.isNotEmpty()) {
            val mainAudio = primaryAudio
            val (audioSz, audioApprox) = mainAudio?.resolvedSizeBytes(durationSec) ?: (-1L to true)
            val directAudioUrl = mainAudio?.url?.takeIf { !ResolverHttpUtils.isManifestUrl(it) }
            builtOptions += ResolvedMediaFormat(
                id = "audio_m4a",
                labelAr = "صوت فقط • M4A عالي النقاء",
                labelEn = "M4A",
                subLabelAr = "صوت أصلي سريع بدون فيديو",
                containerExt = "m4a",
                mimeType = "audio/mp4",
                streamUrl = directAudioUrl ?: canonicalUrl,
                formatExpression = mainAudio?.formatId ?: "bestaudio/best",
                exactBytes = audioSz,
                isApproximateBytes = audioApprox,
                isAudioOnly = true,
                isHls = directAudioUrl == null,
                needsMux = directAudioUrl == null,
                headers = mainAudio?.headers ?: baseHeaders,
                badgeAr = "صوت أصلي",
                badgeEn = "Original Audio",
                acodec = mainAudio?.acodec.orEmpty(),
                languageCode = mainAudio?.language.orEmpty(),
                languageLabel = buildLanguageDisplayLabel(mainAudio),
                hasVideo = false,
                hasAudio = true
            )

            for (langTrack in multiLangAudioTracks) {
                if (mainAudio != null && langTrack.formatId == mainAudio.formatId) continue
                val (lsz, lapprox) = langTrack.resolvedSizeBytes(durationSec)
                val langLabel = buildLanguageDisplayLabel(langTrack)
                val code = langTrack.language.ifBlank { langTrack.formatId }
                builtOptions += ResolvedMediaFormat(
                    id = "audio_m4a_$code",
                    labelAr = "صوت ($langLabel) • M4A",
                    labelEn = "M4A ($code)",
                    subLabelAr = "مسار صوتي بلغة $langLabel",
                    containerExt = "m4a",
                    mimeType = "audio/mp4",
                    streamUrl = langTrack.url,
                    formatExpression = langTrack.formatId,
                    exactBytes = lsz,
                    isApproximateBytes = lapprox,
                    isAudioOnly = true,
                    needsMux = !langTrack.ext.equals("m4a", true),
                    headers = langTrack.headers,
                    badgeAr = langLabel,
                    badgeEn = code.uppercase(Locale.ROOT),
                    acodec = langTrack.acodec,
                    languageCode = langTrack.language,
                    languageLabel = langLabel,
                    isAutoDubbed = langTrack.isDubbed,
                    isOriginalAudio = langTrack.isOriginal,
                    hasVideo = false,
                    hasAudio = true
                )
            }

            builtOptions += ResolvedMediaFormat(
                id = "audio_mp3",
                labelAr = "صوت فقط • MP3",
                labelEn = "MP3",
                subLabelAr = "تحويل تلقائي متوافق مع كل المشغلات",
                containerExt = "mp3",
                mimeType = "audio/mpeg",
                bitrateKbps = 320,
                streamUrl = mainAudio?.url ?: canonicalUrl,
                formatExpression = mainAudio?.formatId ?: "bestaudio/best",
                exactBytes = audioSz,
                isApproximateBytes = true,
                isAudioOnly = true,
                isHls = mainAudio == null,
                needsMux = true,
                headers = mainAudio?.headers ?: baseHeaders,
                badgeAr = "للمحاضرات",
                badgeEn = "For Lectures",
                languageCode = mainAudio?.language.orEmpty(),
                languageLabel = buildLanguageDisplayLabel(mainAudio),
                hasVideo = false,
                hasAudio = true
            )
        }

        return YtDlpExtractedInfo(
            title = info.optString("title").takeIf { it.isNotBlank() }
                ?: ResolverHttpUtils.deriveTitleFromUrl(canonicalUrl),
            uploader = info.optString("uploader").takeIf { it.isNotBlank() }
                ?: info.optString("channel").takeIf { it.isNotBlank() },
            thumbnailUrl = info.optString("thumbnail").takeIf { it.startsWith("http", true) },
            durationSeconds = durationSec,
            baseHeaders = baseHeaders,
            formats = builtOptions
        )
    }

    private fun selectDistinctAudioByLanguage(
        audioNodes: List<RawFormatNode>,
        durationSec: Int
    ): List<RawFormatNode> {
        if (audioNodes.isEmpty()) return emptyList()
        return audioNodes
            .groupBy { "${it.language.ifBlank { "default" }}_${it.isDubbed}" }
            .values
            .mapNotNull { list ->
                list.maxByOrNull { node ->
                    val m4aBonus = if (node.ext == "m4a" || node.acodec.startsWith("mp4a")) 1_000_000_000L else 0L
                    m4aBonus + node.resolvedSizeBytes(durationSec).first.coerceAtLeast(0L) + (node.abrKbps * 1000).toLong()
                }
            }
            .sortedWith(
                compareByDescending<RawFormatNode> { it.isOriginal }
                    .thenByDescending { it.language == "ar" }
                    .thenByDescending { it.languagePreference }
                    .thenByDescending { it.abrKbps }
            )
    }

    private fun selectPrimaryAudioTrack(
        audioNodes: List<RawFormatNode>,
        durationSec: Int
    ): RawFormatNode? {
        if (audioNodes.isEmpty()) return null
        return audioNodes.maxByOrNull { node ->
            val arOrOriginalScore = when {
                node.isOriginal -> 3_000_000_000L
                node.language == "ar" -> 2_000_000_000L
                !node.isDubbed -> 1_500_000_000L
                else -> 0L
            }
            val m4aBonus = if (node.ext == "m4a" || node.acodec.startsWith("mp4a")) 500_000_000L else 0L
            arOrOriginalScore + m4aBonus + (node.abrKbps * 1000).toLong() + node.resolvedSizeBytes(durationSec).first.coerceAtLeast(0L)
        }
    }

    private fun buildLanguageDisplayLabel(node: RawFormatNode?): String {
        if (node == null) return "الأصلي"
        val lang = node.language
        val langNameAr = when {
            lang.startsWith("ar") -> "العربية"
            lang.startsWith("en") -> "الإنجليزية"
            lang.startsWith("fr") -> "الفرنسية"
            lang.startsWith("es") -> "الإسبانية"
            lang.isNotBlank() -> lang.uppercase(Locale.ROOT)
            else -> ""
        }
        return when {
            node.isDubbed && langNameAr.isNotBlank() -> "$langNameAr (مدبلج)"
            node.isDubbed -> "مسار مدبلج"
            langNameAr.isNotBlank() && node.isOriginal -> "الأصلي ($langNameAr)"
            langNameAr.isNotBlank() -> langNameAr
            else -> "الأصلي"
        }
    }

    private fun jsonObjectToHeaderMap(obj: JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        return buildMap {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.optString(k)
                if (k.isNotBlank() && v.isNotBlank()) put(k, v)
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

        val builtOptions = mutableListOf<ResolvedMediaFormat>()
        val videoByRes = rawFormats
            .filter { fmt ->
                val fh = fmt.height
                val vcodec = fmt.vcodec?.lowercase(Locale.ROOT) ?: ""
                vcodec != "none" && fh > 0 && !fmt.url.isNullOrBlank()
            }
            .groupBy { fmt ->
                val fw = fmt.width
                val fh = fmt.height
                if (fw > 0 && fh > 0) minOf(fw, fh) else fh
            }
            .entries
            .sortedByDescending { it.key }

        for ((h, matching) in videoByRes) {
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
            val effectivelyHasAudio = hasAudio || bestAudio != null
            val needsMux = (!hasAudio && bestAudio != null) || isHls || isDash
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

            val formatExpr = if (!hasAudio && bestAudio?.formatId != null && chosen.formatId != null) {
                "${chosen.formatId}+${bestAudio.formatId}"
            } else {
                chosen.formatId ?: "best[height<=$h]"
            }

            builtOptions += ResolvedMediaFormat(
                id = "${h}p",
                labelAr = qualityLabelAr(h, 0),
                labelEn = "${h}p",
                subLabelAr = when {
                    !effectivelyHasAudio -> "فيديو فقط (بدون صوت في المصدر)"
                    isHls || isDash -> "بث مجزأ + دمج تلقائي"
                    needsMux -> "دمج فيديو + صوت (FFmpeg)"
                    else -> "فيديو + صوت • تحميل فائق متعدد الأجزاء"
                },
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
                badgeAr = badgeArForHeight(h),
                badgeEn = badgeEnForHeight(h),
                width = chosen.width,
                vcodec = chosen.vcodec.orEmpty(),
                acodec = if (!hasAudio) bestAudio?.acodec.orEmpty() else chosen.acodec.orEmpty(),
                hasVideo = true,
                hasAudio = effectivelyHasAudio
            )
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
                badgeEn = "Original Audio",
                hasVideo = false,
                hasAudio = true
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

    private fun qualityLabelAr(h: Int, fps: Int): String {
        val fpsText = if (fps >= 48) " • ${fps}fps" else ""
        return when {
            h >= 2160 -> "${h}p • 4K فائقة الدقة$fpsText"
            h >= 1440 -> "${h}p • 2K QHD$fpsText"
            h >= 1080 -> "${h}p • Full HD$fpsText"
            h >= 720 -> "${h}p • HD$fpsText"
            h >= 480 -> "${h}p • متوسطة"
            h >= 360 -> "${h}p • اقتصادية"
            h >= 240 -> "${h}p • منخفضة"
            h > 0 -> "${h}p • أقل بيانات"
            else -> "الجودة الأصلية"
        }
    }

    private fun badgeArForHeight(h: Int): String? = when {
        h >= 2160 -> "الأوضح"
        h == 1440 -> "2K فائق"
        h == 1080 -> "Full HD"
        h == 720 -> "HD"
        h in 1..144 -> "أقل استهلاكاً للبيانات"
        else -> null
    }

    private fun badgeEnForHeight(h: Int): String? = when {
        h >= 2160 -> "Highest"
        h == 1440 -> "2K QHD"
        h == 1080 -> "Full HD"
        h == 720 -> "HD"
        h in 1..144 -> "Data Saver"
        else -> null
    }
}

class AndroidWebViewSniffGateway(private val context: Context) : WebViewSniffGateway {
    private val appContext = context.applicationContext
    override suspend fun sniff(url: String, timeoutMs: Long): SniffedMediaResult? =
        HeadlessWebViewSniffer.sniffMediaUrl(appContext, url, timeoutMs)
}
