package com.hawas.study.rafid.engine

import com.hawas.study.rafid.models.ExtractedMediaMetadata
import com.hawas.study.rafid.models.MediaFormatOption
import com.hawas.study.rafid.models.PlaylistEntryItem
import com.hawas.study.rafid.models.RafidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.TimeUnit

class UniversalMediaExtractor(
    private val httpClient: OkHttpClient = defaultClient(),
    private val linkResolver: UniversalLinkResolver = UniversalLinkResolver(httpClient)
) {
    companion object {
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
        userAgent: String? = null
    ): Result<ExtractedMediaMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val resolved = linkResolver.resolve(rawUrl, cookiesHeader)
            val targetUrl = resolved.resolvedUrl
            val platform = AdAndTrackerShield.detectPlatform(targetUrl)

            when (platform) {
                RafidPlatform.YOUTUBE -> extractYouTube(resolved.originalUrl, targetUrl, cookiesHeader, userAgent)
                RafidPlatform.PINTEREST -> extractPinterest(resolved.originalUrl, targetUrl, cookiesHeader, userAgent)
                RafidPlatform.TIKTOK -> extractTikTok(resolved.originalUrl, targetUrl, cookiesHeader, userAgent)
                RafidPlatform.INSTAGRAM,
                RafidPlatform.FACEBOOK,
                RafidPlatform.X_TWITTER,
                RafidPlatform.STUDY_PLATFORM -> extractOpenGraphAndHtmlMedia(
                    originalUrl = resolved.originalUrl,
                    resolvedUrl = targetUrl,
                    platform = platform,
                    cookiesHeader = cookiesHeader,
                    userAgent = userAgent
                )
                RafidPlatform.DIRECT -> extractDirectOrHtml(
                    originalUrl = resolved.originalUrl,
                    resolvedUrl = targetUrl,
                    cookiesHeader = cookiesHeader,
                    userAgent = userAgent
                )
            }
        }
    }

    private fun extractYouTube(
        originalUrl: String,
        resolvedUrl: String,
        cookiesHeader: String?,
        userAgent: String?
    ): ExtractedMediaMetadata {
        val listId = extractQueryParam(resolvedUrl, "list")
        val videoId = extractYouTubeVideoId(resolvedUrl)

        if (!listId.isNullOrBlank() && (resolvedUrl.contains("/playlist") || videoId.isNullOrBlank())) {
            return extractYouTubePlaylist(originalUrl, resolvedUrl, listId, cookiesHeader, userAgent)
        }

        val effectiveVideoId = videoId ?: throw IllegalStateException("رابط يوتيوب غير مكتمل أو لا يحتوي على معرف فيديو")
        val canonicalUrl = "https://www.youtube.com/watch?v=$effectiveVideoId"

        val playerJson = fetchYouTubeInnertubePlayer(effectiveVideoId, cookiesHeader)
            ?: fetchYouTubePlayerFromHtml(canonicalUrl, cookiesHeader)
            ?: throw IllegalStateException("تعذر قراءة بيانات الفيديو من يوتيوب — تأكد من الاتصال بالإنترنت أو افتح الرابط في المتصفح الذكي")

        val metadata = parseYouTubePlayerJson(
            originalUrl = originalUrl,
            resolvedUrl = canonicalUrl,
            videoId = effectiveVideoId,
            playerJson = playerJson,
            cookiesHeader = cookiesHeader,
            userAgent = userAgent
        )

        if (!listId.isNullOrBlank()) {
            val playlistMeta = runCatching {
                extractYouTubePlaylist(originalUrl, resolvedUrl, listId, cookiesHeader, userAgent)
            }.getOrNull()
            if (playlistMeta != null && playlistMeta.playlistItems.isNotEmpty()) {
                val bundleOption = MediaFormatOption(
                    id = "playlist_bundle_$listId",
                    label = "تحميل قائمة التشغيل بالكامل (${playlistMeta.playlistItems.size} عنصر)",
                    containerExt = "mp4",
                    mimeType = "video/mp4",
                    isPlaylistBundle = true,
                    badgeTagAr = "${playlistMeta.playlistItems.size} فيديو",
                    badgeTagEn = "${playlistMeta.playlistItems.size} videos"
                )
                return metadata.copy(
                    isPlaylist = true,
                    playlistItems = playlistMeta.playlistItems,
                    formats = metadata.formats + bundleOption
                )
            }
        }

        return metadata
    }

    internal fun parseYouTubePlayerJson(
        originalUrl: String,
        resolvedUrl: String,
        videoId: String,
        playerJson: JSONObject,
        cookiesHeader: String? = null,
        userAgent: String? = null
    ): ExtractedMediaMetadata {
        val videoDetails = playerJson.optJSONObject("videoDetails")
        val title = videoDetails?.optString("title")?.takeIf { it.isNotBlank() } ?: "YouTube Video ($videoId)"
        val author = videoDetails?.optString("author")?.takeIf { it.isNotBlank() } ?: "YouTube"
        val durationSec = videoDetails?.optString("lengthSeconds")?.toIntOrNull()
            ?: videoDetails?.optInt("lengthSeconds", 0) ?: 0

        val thumbnails = videoDetails?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumbUrl = if (thumbnails != null && thumbnails.length() > 0) {
            thumbnails.optJSONObject(thumbnails.length() - 1)?.optString("url")
        } else {
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        }

        val streamingData = playerJson.optJSONObject("streamingData")
            ?: throw IllegalStateException("لم يُعثر على تدفقات قابلة للتحميل لهذا الفيديو")

        val muxedFormats = streamingData.optJSONArray("formats") ?: JSONArray()
        val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: JSONArray()

        // Find best AAC/M4A audio stream for muxing + audio-only downloads
        val audioStreams = mutableListOf<JSONObject>()
        for (i in 0 until adaptiveFormats.length()) {
            val f = adaptiveFormats.optJSONObject(i) ?: continue
            val mime = f.optString("mimeType")
            val directUrl = resolveStreamUrl(f) ?: continue
            f.put("_resolvedStreamUrl", directUrl)
            if (mime.startsWith("audio/mp4") || mime.startsWith("audio/webm")) {
                audioStreams.add(f)
            }
        }
        val bestMp4Audio = audioStreams
            .filter { it.optString("mimeType").startsWith("audio/mp4") }
            .maxByOrNull { it.optInt("bitrate", 0) }
            ?: audioStreams.maxByOrNull { it.optInt("bitrate", 0) }

        val bestAudioUrl = bestMp4Audio?.optString("_resolvedStreamUrl")
        val bestAudioSize = bestMp4Audio?.optString("contentLength")?.toLongOrNull() ?: 0L

        val optionsByHeight = linkedMapOf<Int, MediaFormatOption>()

        // Collect adaptive video tracks (paired with audio track -> mux required)
        if (!bestAudioUrl.isNullOrBlank()) {
            for (i in 0 until adaptiveFormats.length()) {
                val f = adaptiveFormats.optJSONObject(i) ?: continue
                val mime = f.optString("mimeType")
                if (!mime.startsWith("video/mp4")) continue
                val streamUrl = f.optString("_resolvedStreamUrl").ifBlank { resolveStreamUrl(f) ?: continue }
                val height = f.optInt("height", 0)
                if (height <= 0) continue
                val vSize = f.optString("contentLength").toLongOrNull() ?: 0L
                val totalSize = if (vSize > 0L && bestAudioSize > 0L) vSize + bestAudioSize else vSize
                val existing = optionsByHeight[height]
                if (existing == null || existing.videoUrl.isNullOrBlank()) {
                    optionsByHeight[height] = MediaFormatOption(
                        id = "yt_v_${height}_${f.optInt("itag")}",
                        label = "${height}p",
                        containerExt = "mp4",
                        mimeType = "video/mp4",
                        resolutionHeight = height,
                        videoUrl = streamUrl,
                        audioUrl = bestAudioUrl,
                        estimatedSizeBytes = totalSize,
                        isAudioOnly = false,
                        isMuxRequired = true,
                        badgeTagAr = when {
                            height >= 2160 -> "الأوضح"
                            height <= 144 -> "أقل استهلاكاً للبيانات"
                            else -> null
                        },
                        badgeTagEn = when {
                            height >= 2160 -> "Highest"
                            height <= 144 -> "Data Saver"
                            else -> null
                        }
                    )
                }
            }
        }

        // Collect muxed formats (ready single-file MP4 video+audio, e.g., 360p/720p)
        for (i in 0 until muxedFormats.length()) {
            val f = muxedFormats.optJSONObject(i) ?: continue
            val mime = f.optString("mimeType")
            if (!mime.startsWith("video/")) continue
            val streamUrl = resolveStreamUrl(f) ?: continue
            val height = f.optInt("height", 360)
            val size = f.optString("contentLength").toLongOrNull() ?: 0L
            optionsByHeight[height] = MediaFormatOption(
                id = "yt_m_${height}_${f.optInt("itag")}",
                label = "${height}p",
                containerExt = "mp4",
                mimeType = "video/mp4",
                resolutionHeight = height,
                videoUrl = streamUrl,
                audioUrl = null,
                estimatedSizeBytes = size,
                isAudioOnly = false,
                isMuxRequired = false,
                badgeTagAr = when {
                    height >= 2160 -> "الأوضح"
                    height <= 144 -> "أقل استهلاكاً للبيانات"
                    else -> null
                },
                badgeTagEn = when {
                    height >= 2160 -> "Highest"
                    height <= 144 -> "Data Saver"
                    else -> null
                }
            )
        }

        val sortedVideoOptions = optionsByHeight.values.sortedByDescending { it.resolutionHeight ?: 0 }

        val audioOptions = mutableListOf<MediaFormatOption>()
        if (!bestAudioUrl.isNullOrBlank()) {
            audioOptions += MediaFormatOption(
                id = "yt_audio_m4a",
                label = "M4A",
                containerExt = "m4a",
                mimeType = "audio/mp4",
                bitrateKbps = (bestMp4Audio?.optInt("bitrate", 128000) ?: 128000) / 1000,
                videoUrl = bestAudioUrl,
                estimatedSizeBytes = bestAudioSize,
                isAudioOnly = true,
                isMuxRequired = false,
                badgeTagAr = "صوت أصلي",
                badgeTagEn = "Original Audio"
            )
            audioOptions += MediaFormatOption(
                id = "yt_audio_lecture_hq",
                label = "صوت المحاضرات عالي الجودة (M4A/AAC)",
                containerExt = "m4a",
                mimeType = "audio/mp4",
                bitrateKbps = 320,
                videoUrl = bestAudioUrl,
                estimatedSizeBytes = bestAudioSize,
                isAudioOnly = true,
                isMuxRequired = true,
                badgeTagAr = "للمحاضرات",
                badgeTagEn = "For Lectures"
            )
        }

        val allFormats = sortedVideoOptions + audioOptions
        if (allFormats.isEmpty()) {
            throw IllegalStateException("الفيديو محمي بتشفير ولا يحتوي على روابط مباشرة حالياً — جرّب فتحه من المتصفح الذكي")
        }

        return ExtractedMediaMetadata(
            originalUrl = originalUrl,
            resolvedUrl = resolvedUrl,
            title = title,
            authorOrSource = author,
            sourceDomain = AdAndTrackerShield.normalizeDomain(resolvedUrl),
            platform = RafidPlatform.YOUTUBE,
            thumbnailUrl = thumbUrl,
            durationSeconds = durationSec,
            formats = allFormats,
            cookiesHeader = cookiesHeader,
            userAgent = userAgent ?: UniversalLinkResolver.DESKTOP_USER_AGENT
        )
    }

    private fun extractYouTubePlaylist(
        originalUrl: String,
        resolvedUrl: String,
        listId: String,
        cookiesHeader: String?,
        userAgent: String?
    ): ExtractedMediaMetadata {
        val playlistUrl = "https://www.youtube.com/playlist?list=$listId"
        val html = fetchText(playlistUrl, cookiesHeader, UniversalLinkResolver.DESKTOP_USER_AGENT)
            ?: throw IllegalStateException("تعذر فتح قائمة التشغيل من يوتيوب")
        val initialData = extractJsonObjectFromHtml(html, "var ytInitialData = ")
            ?: extractJsonObjectFromHtml(html, "ytInitialData = ")
            ?: throw IllegalStateException("تعذر قراءة محتويات قائمة التشغيل")

        return parseYouTubePlaylistJson(originalUrl, playlistUrl, listId, initialData, cookiesHeader, userAgent)
    }

    internal fun parseYouTubePlaylistJson(
        originalUrl: String,
        resolvedUrl: String,
        listId: String,
        initialData: JSONObject,
        cookiesHeader: String? = null,
        userAgent: String? = null
    ): ExtractedMediaMetadata {
        val title = initialData.optJSONObject("metadata")
            ?.optJSONObject("playlistMetadataRenderer")
            ?.optString("title")
            ?.takeIf { it.isNotBlank() } ?: "قائمة تشغيل يوتيوب ($listId)"

        val items = mutableListOf<PlaylistEntryItem>()
        collectPlaylistRenderers(initialData, items)

        if (items.isEmpty()) {
            throw IllegalStateException("قائمة التشغيل فارغة أو خاصة ولا يمكن قراءة عناصرها بدون تسجيل الدخول في المتصفح")
        }

        val bundleFormats = listOf(
            MediaFormatOption(
                id = "yt_pl_video_$listId",
                label = "تحميل قائمة التشغيل كاملة فيديو MP4 (${items.size} عنصر)",
                containerExt = "mp4",
                mimeType = "video/mp4",
                resolutionHeight = 720,
                isAudioOnly = false,
                isPlaylistBundle = true,
                badgeTagAr = "${items.size} عنصر",
                badgeTagEn = "${items.size} items"
            ),
            MediaFormatOption(
                id = "yt_pl_audio_$listId",
                label = "تحميل قائمة التشغيل صوت فقط للمحاضرات (${items.size} عنصر)",
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
            resolvedUrl = resolvedUrl,
            title = title,
            authorOrSource = "YouTube Playlist • ${items.size}",
            sourceDomain = "youtube.com",
            platform = RafidPlatform.YOUTUBE,
            thumbnailUrl = items.firstOrNull()?.thumbnailUrl,
            durationSeconds = items.sumOf { it.durationSeconds },
            formats = bundleFormats,
            playlistItems = items,
            isPlaylist = true,
            cookiesHeader = cookiesHeader,
            userAgent = userAgent ?: UniversalLinkResolver.DESKTOP_USER_AGENT
        )
    }

    private fun collectPlaylistRenderers(node: Any?, out: MutableList<PlaylistEntryItem>) {
        when (node) {
            is JSONObject -> {
                val r = node.optJSONObject("playlistVideoRenderer")
                if (r != null) {
                    val vId = r.optString("videoId")
                    if (vId.isNotBlank() && out.none { it.videoId == vId }) {
                        val runs = r.optJSONObject("title")?.optJSONArray("runs")
                        val vTitle = runs?.optJSONObject(0)?.optString("text")
                            ?: r.optJSONObject("title")?.optString("simpleText")
                            ?: "Video ${out.size + 1}"
                        val lenSec = r.optString("lengthSeconds").toIntOrNull() ?: 0
                        val thumb = r.optJSONObject("thumbnail")?.optJSONArray("thumbnails")?.let { arr ->
                            if (arr.length() > 0) arr.optJSONObject(arr.length() - 1)?.optString("url") else null
                        } ?: "https://i.ytimg.com/vi/$vId/hqdefault.jpg"
                        out += PlaylistEntryItem(
                            index = out.size + 1,
                            videoId = vId,
                            title = vTitle,
                            videoUrl = "https://www.youtube.com/watch?v=$vId",
                            durationSeconds = lenSec,
                            thumbnailUrl = thumb
                        )
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    collectPlaylistRenderers(node.opt(keys.next()), out)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectPlaylistRenderers(node.opt(i), out)
                }
            }
        }
    }

    private fun fetchYouTubeInnertubePlayer(videoId: String, cookiesHeader: String?): JSONObject? {
        val clients = listOf(
            """{"context":{"client":{"clientName":"ANDROID","clientVersion":"19.44.38","androidSdkVersion":34,"hl":"ar","gl":"EG"}},"videoId":"$videoId","contentCheckOk":true,"racyCheckOk":true}""",
            """{"context":{"client":{"clientName":"IOS","clientVersion":"19.45.4","deviceMake":"Apple","deviceModel":"iPhone16,2","hl":"ar","gl":"EG"}},"videoId":"$videoId","contentCheckOk":true,"racyCheckOk":true}"""
        )
        for (payload in clients) {
            val req = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("User-Agent", UniversalLinkResolver.MOBILE_USER_AGENT)
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
                            if (sd != null && ((sd.optJSONArray("formats")?.length() ?: 0) > 0 || (sd.optJSONArray("adaptiveFormats")?.length() ?: 0) > 0)) {
                                return obj
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun fetchYouTubePlayerFromHtml(url: String, cookiesHeader: String?): JSONObject? {
        val html = fetchText(url, cookiesHeader, UniversalLinkResolver.DESKTOP_USER_AGENT) ?: return null
        return extractJsonObjectFromHtml(html, "var ytInitialPlayerResponse = ")
            ?: extractJsonObjectFromHtml(html, "ytInitialPlayerResponse = ")
    }

    private fun resolveStreamUrl(formatObj: JSONObject): String? {
        val direct = formatObj.optString("url")
        if (direct.isNotBlank()) return direct
        val cipher = formatObj.optString("signatureCipher").ifBlank { formatObj.optString("cipher") }
        if (cipher.isNotBlank()) {
            val params = cipher.split("&").mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx > 0) {
                    URLDecoder.decode(part.substring(0, idx), "UTF-8") to URLDecoder.decode(part.substring(idx + 1), "UTF-8")
                } else null
            }.toMap()
            val baseUrl = params["url"]
            // Only usable directly if no obfuscated 's' signature transform is missing
            if (!baseUrl.isNullOrBlank() && params["s"].isNullOrBlank()) {
                return baseUrl
            }
        }
        return null
    }

    private fun extractPinterest(
        originalUrl: String,
        resolvedUrl: String,
        cookiesHeader: String?,
        userAgent: String?
    ): ExtractedMediaMetadata {
        val html = fetchText(resolvedUrl, cookiesHeader, userAgent ?: UniversalLinkResolver.DESKTOP_USER_AGENT)
            ?: throw IllegalStateException("تعذر فتح رابط بينتيريست")
        return parsePinterestHtml(originalUrl, resolvedUrl, html, cookiesHeader, userAgent)
    }

    internal fun parsePinterestHtml(
        originalUrl: String,
        resolvedUrl: String,
        html: String,
        cookiesHeader: String? = null,
        userAgent: String? = null
    ): ExtractedMediaMetadata {
        val title = extractMetaContent(html, "og:title")
            ?: extractHtmlTitle(html)
            ?: "Pinterest Media"
        val ogImage = extractMetaContent(html, "og:image")
        val videoUrls = LinkedHashSet<String>()
        val imageUrls = LinkedHashSet<String>()

        extractMetaContent(html, "og:video:secure_url")?.let { videoUrls.add(it) }
        extractMetaContent(html, "og:video")?.let { videoUrls.add(it) }

        Regex("""https://v\d*\.pinimg\.com/videos/[^\s"'<>\\]+\.mp4""", RegexOption.IGNORE_CASE)
            .findAll(html).forEach { videoUrls.add(it.value.replace("\\/", "/")) }

        Regex("""https://i\.pinimg\.com/originals/[^\s"'<>\\]+\.(jpg|jpeg|png|webp)""", RegexOption.IGNORE_CASE)
            .findAll(html).forEach { imageUrls.add(it.value.replace("\\/", "/")) }

        if (!ogImage.isNullOrBlank()) {
            imageUrls.add(ogImage)
        }

        val formats = mutableListOf<MediaFormatOption>()
        videoUrls.forEachIndexed { idx, vUrl ->
            val size = probeContentLength(vUrl, cookiesHeader)
            val label = when {
                vUrl.contains("720") -> "720p"
                vUrl.contains("1080") -> "1080p"
                else -> if (idx == 0) "فيديو HD بدون علامة مائية" else "MP4 (${idx + 1})"
            }
            formats += MediaFormatOption(
                id = "pin_v_$idx",
                label = label,
                containerExt = "mp4",
                mimeType = "video/mp4",
                resolutionHeight = if (vUrl.contains("1080")) 1080 else 720,
                videoUrl = vUrl,
                estimatedSizeBytes = size,
                isAudioOnly = false,
                badgeTagAr = if (idx == 0) "بدون علامة مائية" else null,
                badgeTagEn = if (idx == 0) "No Watermark" else null
            )
        }

        if (formats.isEmpty() && imageUrls.isNotEmpty()) {
            imageUrls.take(3).forEachIndexed { idx, imgUrl ->
                val ext = imgUrl.substringAfterLast('.').substringBefore('?').lowercase(Locale.US)
                    .takeIf { it in setOf("jpg", "jpeg", "png", "webp") } ?: "jpg"
                val size = probeContentLength(imgUrl, cookiesHeader)
                formats += MediaFormatOption(
                    id = "pin_img_$idx",
                    label = if (idx == 0) "صورة أصلية عالية الدقة (${ext.uppercase(Locale.US)})" else "صورة (${ext.uppercase(Locale.US)})",
                    containerExt = ext,
                    mimeType = "image/$ext",
                    videoUrl = imgUrl,
                    estimatedSizeBytes = size,
                    isAudioOnly = false,
                    badgeTagAr = if (idx == 0) "الأصلية" else null,
                    badgeTagEn = if (idx == 0) "Original" else null
                )
            }
        }

        if (formats.isEmpty()) {
            throw IllegalStateException("لم يتم العثور على فيديو أو صورة مباشرة في هذا الدبوس")
        }

        return ExtractedMediaMetadata(
            originalUrl = originalUrl,
            resolvedUrl = resolvedUrl,
            title = title,
            authorOrSource = "Pinterest",
            sourceDomain = AdAndTrackerShield.normalizeDomain(resolvedUrl),
            platform = RafidPlatform.PINTEREST,
            thumbnailUrl = ogImage ?: imageUrls.firstOrNull(),
            formats = formats,
            cookiesHeader = cookiesHeader,
            userAgent = userAgent ?: UniversalLinkResolver.DESKTOP_USER_AGENT
        )
    }

    private fun extractTikTok(
        originalUrl: String,
        resolvedUrl: String,
        cookiesHeader: String?,
        userAgent: String?
    ): ExtractedMediaMetadata {
        val html = fetchText(resolvedUrl, cookiesHeader, userAgent ?: UniversalLinkResolver.DESKTOP_USER_AGENT)
            ?: throw IllegalStateException("تعذر فتح رابط تيك توك")
        return parseTikTokHtml(originalUrl, resolvedUrl, html, cookiesHeader, userAgent)
    }

    internal fun parseTikTokHtml(
        originalUrl: String,
        resolvedUrl: String,
        html: String,
        cookiesHeader: String? = null,
        userAgent: String? = null
    ): ExtractedMediaMetadata {
        val formats = mutableListOf<MediaFormatOption>()
        var title = extractMetaContent(html, "og:title") ?: extractHtmlTitle(html) ?: "TikTok Video"
        var author = "TikTok"
        var thumb = extractMetaContent(html, "og:image")
        var duration = 0

        val rehydrationJson = extractScriptJsonById(html, "__UNIVERSAL_DATA_FOR_REHYDRATION__")
        if (rehydrationJson != null) {
            val defaultScope = rehydrationJson.optJSONObject("__DEFAULT_SCOPE__")
            val itemStruct = defaultScope?.optJSONObject("webapp.video-detail")
                ?.optJSONObject("itemInfo")
                ?.optJSONObject("itemStruct")
            if (itemStruct != null) {
                title = itemStruct.optString("desc").takeIf { it.isNotBlank() } ?: title
                author = itemStruct.optJSONObject("author")?.optString("nickname")
                    ?.takeIf { it.isNotBlank() } ?: author
                val videoObj = itemStruct.optJSONObject("video")
                if (videoObj != null) {
                    duration = videoObj.optInt("duration", 0)
                    thumb = videoObj.optString("cover").takeIf { it.isNotBlank() } ?: thumb
                    val playAddr = videoObj.optString("playAddr").ifBlank { videoObj.optString("downloadAddr") }
                    if (playAddr.isNotBlank()) {
                        val size = probeContentLength(playAddr, cookiesHeader)
                        formats += MediaFormatOption(
                            id = "tt_hd_nowm",
                            label = "1080p",
                            containerExt = "mp4",
                            mimeType = "video/mp4",
                            resolutionHeight = videoObj.optInt("height", 1080).coerceAtLeast(720),
                            videoUrl = playAddr,
                            estimatedSizeBytes = size,
                            badgeTagAr = "بدون علامة مائية",
                            badgeTagEn = "No Watermark"
                        )
                    }
                }
                val musicUrl = itemStruct.optJSONObject("music")?.optString("playUrl")
                if (!musicUrl.isNullOrBlank()) {
                    val aSize = probeContentLength(musicUrl, cookiesHeader)
                    formats += MediaFormatOption(
                        id = "tt_audio",
                        label = "صوت الفيديو فقط (MP3 / M4A)",
                        containerExt = "mp3",
                        mimeType = "audio/mpeg",
                        bitrateKbps = 320,
                        videoUrl = musicUrl,
                        estimatedSizeBytes = aSize,
                        isAudioOnly = true,
                        badgeTagAr = "صوت فقط",
                        badgeTagEn = "Audio Only"
                    )
                }
            }
        }

        if (formats.isEmpty()) {
            extractMetaContent(html, "og:video:secure_url")
                ?: extractMetaContent(html, "og:video")
                ?: Regex("""https://[^\s"'<>\\]*tiktokcdn[^\s"'<>\\]*""", RegexOption.IGNORE_CASE)
                    .find(html)?.value?.replace("\\u002F", "/")?.replace("\\/", "/")
                ?.let { vUrl ->
                    val size = probeContentLength(vUrl, cookiesHeader)
                    formats += MediaFormatOption(
                        id = "tt_og_video",
                        label = "فيديو MP4 بدون علامة مائية",
                        containerExt = "mp4",
                        mimeType = "video/mp4",
                        resolutionHeight = 720,
                        videoUrl = vUrl,
                        estimatedSizeBytes = size,
                        badgeTagAr = "بدون علامة مائية",
                        badgeTagEn = "No Watermark"
                    )
                }
        }

        if (formats.isEmpty()) {
            throw IllegalStateException("تعذر استخراج الفيديو مباشرة — افتح الرابط في المتصفح الذكي وسجّل الدخول أو شغّل الفيديو ليلتقطه رَافِد تلقائياً")
        }

        return ExtractedMediaMetadata(
            originalUrl = originalUrl,
            resolvedUrl = resolvedUrl,
            title = title,
            authorOrSource = author,
            sourceDomain = AdAndTrackerShield.normalizeDomain(resolvedUrl),
            platform = RafidPlatform.TIKTOK,
            thumbnailUrl = thumb,
            durationSeconds = duration,
            formats = formats,
            cookiesHeader = cookiesHeader,
            userAgent = userAgent ?: UniversalLinkResolver.DESKTOP_USER_AGENT
        )
    }

    private fun extractDirectOrHtml(
        originalUrl: String,
        resolvedUrl: String,
        cookiesHeader: String?,
        userAgent: String?
    ): ExtractedMediaMetadata {
        val headInfo = probeHead(resolvedUrl, cookiesHeader)
        val mime = headInfo.first.lowercase(Locale.US)
        val len = headInfo.second
        val lowerUrl = resolvedUrl.lowercase(Locale.US)

        val isDirectBinary = mime.startsWith("video/") ||
            mime.startsWith("audio/") ||
            mime.startsWith("image/") ||
            mime.contains("pdf") ||
            lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".mp3") || lowerUrl.endsWith(".m4a") ||
            lowerUrl.endsWith(".pdf") || lowerUrl.endsWith(".webm")

        if (isDirectBinary) {
            val ext = when {
                mime.contains("mp4") || lowerUrl.contains(".mp4") -> if (mime.startsWith("audio/")) "m4a" else "mp4"
                mime.contains("mpeg") || lowerUrl.contains(".mp3") -> "mp3"
                mime.contains("m4a") || lowerUrl.contains(".m4a") -> "m4a"
                mime.contains("webm") || lowerUrl.contains(".webm") -> "webm"
                mime.contains("pdf") || lowerUrl.contains(".pdf") -> "pdf"
                mime.startsWith("image/png") || lowerUrl.contains(".png") -> "png"
                mime.startsWith("image/") -> "jpg"
                else -> "mp4"
            }
            val isAudio = mime.startsWith("audio/") || ext in setOf("mp3", "m4a")
            val format = MediaFormatOption(
                id = "direct_$ext",
                label = when {
                    ext == "pdf" -> "مستند PDF"
                    isAudio -> "ملف صوتي ${ext.uppercase(Locale.US)}"
                    ext in setOf("jpg", "png", "webp") -> "صورة عالية الدقة"
                    else -> "فيديو مباشر (${ext.uppercase(Locale.US)})"
                },
                containerExt = ext,
                mimeType = mime.ifBlank { if (isAudio) "audio/mp4" else "video/mp4" },
                videoUrl = resolvedUrl,
                estimatedSizeBytes = len,
                isAudioOnly = isAudio,
                badgeTagAr = "مباشر",
                badgeTagEn = "Direct"
            )
            val fileTitle = resolvedUrl.substringAfterLast('/').substringBefore('?')
                .ifBlank { AdAndTrackerShield.normalizeDomain(resolvedUrl) }
            return ExtractedMediaMetadata(
                originalUrl = originalUrl,
                resolvedUrl = resolvedUrl,
                title = fileTitle,
                authorOrSource = AdAndTrackerShield.normalizeDomain(resolvedUrl),
                sourceDomain = AdAndTrackerShield.normalizeDomain(resolvedUrl),
                platform = RafidPlatform.DIRECT,
                formats = listOf(format),
                cookiesHeader = cookiesHeader,
                userAgent = userAgent
            )
        }

        return extractOpenGraphAndHtmlMedia(
            originalUrl = originalUrl,
            resolvedUrl = resolvedUrl,
            platform = RafidPlatform.DIRECT,
            cookiesHeader = cookiesHeader,
            userAgent = userAgent
        )
    }

    internal fun extractOpenGraphAndHtmlMedia(
        originalUrl: String,
        resolvedUrl: String,
        platform: RafidPlatform,
        cookiesHeader: String?,
        userAgent: String?
    ): ExtractedMediaMetadata {
        val html = fetchText(resolvedUrl, cookiesHeader, userAgent ?: UniversalLinkResolver.DESKTOP_USER_AGENT)
            ?: throw IllegalStateException("تعذر الوصول إلى صفحة المصدر — تأكد من الرابط أو افتحه داخل المتصفح الذكي")
        return parseGenericHtmlMedia(originalUrl, resolvedUrl, platform, html, cookiesHeader, userAgent)
    }

    internal fun parseGenericHtmlMedia(
        originalUrl: String,
        resolvedUrl: String,
        platform: RafidPlatform,
        html: String,
        cookiesHeader: String? = null,
        userAgent: String? = null
    ): ExtractedMediaMetadata {
        val title = extractMetaContent(html, "og:title")
            ?: extractMetaContent(html, "twitter:title")
            ?: extractHtmlTitle(html)
            ?: AdAndTrackerShield.normalizeDomain(resolvedUrl)
        val author = extractMetaContent(html, "og:site_name")
            ?: AdAndTrackerShield.normalizeDomain(resolvedUrl)
        val thumb = extractMetaContent(html, "og:image")
            ?: extractMetaContent(html, "twitter:image")

        val videoCandidates = LinkedHashSet<String>()
        listOf("og:video:secure_url", "og:video:url", "og:video", "twitter:player:stream").forEach { prop ->
            extractMetaContent(html, prop)?.let { videoCandidates.add(it) }
        }
        Regex("""<source[^>]+src=["']([^"']+\.(?:mp4|webm|m4a|mp3)[^"']*)["']""", RegexOption.IGNORE_CASE)
            .findAll(html).forEach { m -> videoCandidates.add(resolveRelativeUrl(resolvedUrl, m.groupValues[1])) }
        Regex("""https?://[^\s"'<>\\]+\.(?:mp4|m4a|mp3|pdf)(?:\?[^\s"'<>\\]*)?""", RegexOption.IGNORE_CASE)
            .findAll(html).take(6).forEach { m -> videoCandidates.add(m.value.replace("\\/", "/")) }

        val formats = mutableListOf<MediaFormatOption>()
        videoCandidates.forEachIndexed { idx, candidate ->
            val lower = candidate.lowercase(Locale.US)
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
            val size = probeContentLength(candidate, cookiesHeader)
            formats += MediaFormatOption(
                id = "og_${ext}_$idx",
                label = when {
                    isPdf -> "ملف محاضرة PDF"
                    isAudio -> "صوت المحاضرة (${ext.uppercase(Locale.US)})"
                    else -> if (idx == 0) "فيديو عالي الدقة (MP4)" else "جودة بديلة #${idx + 1}"
                },
                containerExt = ext,
                mimeType = mime,
                resolutionHeight = if (isAudio || isPdf) null else 720,
                videoUrl = candidate,
                estimatedSizeBytes = size,
                isAudioOnly = isAudio,
                badgeTagAr = if (idx == 0) "بدون علامة مائية" else null,
                badgeTagEn = if (idx == 0) "Direct Stream" else null
            )
        }

        if (formats.isEmpty() && !thumb.isNullOrBlank()) {
            formats += MediaFormatOption(
                id = "og_img_0",
                label = "صورة عالية الدقة",
                containerExt = "jpg",
                mimeType = "image/jpeg",
                videoUrl = thumb,
                estimatedSizeBytes = probeContentLength(thumb, cookiesHeader),
                badgeTagAr = "صورة",
                badgeTagEn = "Image"
            )
        }

        if (formats.isEmpty()) {
            throw IllegalStateException("تتطلب هذه الصفحة فتحها داخل المتصفح الذكي لتشغيل الفيديو والتقاطه بجلسة الدخول الخاصة بك")
        }

        return ExtractedMediaMetadata(
            originalUrl = originalUrl,
            resolvedUrl = resolvedUrl,
            title = title,
            authorOrSource = author,
            sourceDomain = AdAndTrackerShield.normalizeDomain(resolvedUrl),
            platform = platform,
            thumbnailUrl = thumb,
            formats = formats,
            cookiesHeader = cookiesHeader,
            userAgent = userAgent ?: UniversalLinkResolver.DESKTOP_USER_AGENT
        )
    }

    private fun fetchText(url: String, cookiesHeader: String?, userAgent: String): String? {
        val req = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "ar,en-US;q=0.9,en;q=0.8")
            .apply {
                if (!cookiesHeader.isNullOrBlank()) header("Cookie", cookiesHeader)
            }
            .build()
        return try {
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun probeHead(url: String, cookiesHeader: String?): Pair<String, Long> {
        val req = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", UniversalLinkResolver.DESKTOP_USER_AGENT)
            .apply {
                if (!cookiesHeader.isNullOrBlank()) header("Cookie", cookiesHeader)
            }
            .build()
        return try {
            httpClient.newCall(req).execute().use { resp ->
                val mime = resp.header("Content-Type")?.substringBefore(';')?.trim().orEmpty()
                val len = resp.header("Content-Length")?.toLongOrNull() ?: 0L
                mime to len
            }
        } catch (_: Exception) {
            "" to 0L
        }
    }

    private fun probeContentLength(url: String, cookiesHeader: String?): Long {
        if (!url.startsWith("http")) return 0L
        return probeHead(url, cookiesHeader).second
    }

    private fun extractYouTubeVideoId(url: String): String? {
        return try {
            val uri = URI(url)
            val host = (uri.host ?: "").lowercase(Locale.US)
            when {
                host == "youtu.be" -> uri.path?.trim('/')?.substringBefore('/')?.takeIf { it.isNotBlank() }
                uri.path?.startsWith("/shorts/") == true ->
                    uri.path.removePrefix("/shorts/").substringBefore('/').takeIf { it.isNotBlank() }
                uri.path?.startsWith("/embed/") == true ->
                    uri.path.removePrefix("/embed/").substringBefore('/').takeIf { it.isNotBlank() }
                else -> extractQueryParam(url, "v")
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractQueryParam(url: String, key: String): String? {
        val query = url.substringAfter('?', "")
        if (query.isBlank()) return null
        return query.split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0 && pair.substring(0, idx) == key) {
                URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            } else null
        }.firstOrNull()
    }

    private fun extractJsonObjectFromHtml(html: String, marker: String): JSONObject? {
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
                        return try {
                            JSONObject(slice)
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }
        }
        return null
    }

    private fun extractScriptJsonById(html: String, id: String): JSONObject? {
        val regex = Regex("""<script[^>]+id=["']$id["'][^>]*>(.*?)</script>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val match = regex.find(html) ?: return null
        val content = match.groupValues[1].trim()
        return try {
            JSONObject(content)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractMetaContent(html: String, propertyOrName: String): String? {
        val p1 = Regex("""<meta[^>]+(?:property|name)=["']${Regex.escape(propertyOrName)}["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        p1.find(html)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { return decodeHtmlEntities(it) }
        val p2 = Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["']${Regex.escape(propertyOrName)}["']""", RegexOption.IGNORE_CASE)
        return p2.find(html)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { decodeHtmlEntities(it) }
    }

    private fun extractHtmlTitle(html: String): String? {
        val regex = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        return regex.find(html)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { decodeHtmlEntities(it) }
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }

    private fun resolveRelativeUrl(baseUrl: String, candidate: String): String {
        return try {
            URI(baseUrl).resolve(candidate).toString()
        } catch (_: Exception) {
            candidate
        }
    }
}
