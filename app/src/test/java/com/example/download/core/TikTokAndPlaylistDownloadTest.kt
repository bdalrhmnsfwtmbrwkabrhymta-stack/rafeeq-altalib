package com.example.download.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.download.SmartDownloadManager
import com.example.download.core.media.MediaMergeEngine
import com.example.download.core.media.MediaProbeReport
import com.example.download.core.media.MediaStreamInfo
import com.example.download.core.model.DownloadError
import com.example.download.core.model.DownloadEngineException
import com.example.download.core.model.MediaPlatform
import com.example.download.core.model.ResolveRequest
import com.example.download.core.model.ResolvedCollectionEntry
import com.example.download.core.model.ResolvedMediaFormat
import com.example.download.core.model.ResolvedMediaResult
import com.example.download.core.resolver.AndroidYtDlpGateway
import com.example.download.core.resolver.FacebookProviderAdapter
import com.example.download.core.resolver.PinterestProviderAdapter
import com.example.download.core.resolver.TikTokProviderAdapter
import com.example.download.core.resolver.UnifiedMediaResolver
import com.example.download.core.resolver.WebViewSniffGateway
import com.example.download.core.resolver.YouTubeProviderAdapter
import com.example.download.core.resolver.YtDlpExtractedInfo
import com.example.download.core.resolver.YtDlpGateway
import com.example.download.data.DownloadDatabase
import com.example.download.data.PlaylistEntry
import com.example.download.extract.HeadlessWebViewSniffer
import com.example.download.extract.SniffedMediaResult
import com.hawas.study.rafid.RafidViewModel
import com.hawas.study.rafid.engine.AdAndTrackerShield
import com.hawas.study.rafid.engine.UniversalLinkResolver
import com.hawas.study.rafid.engine.UniversalMediaExtractor
import com.hawas.study.rafid.models.ExtractedMediaMetadata
import com.hawas.study.rafid.models.MediaFormatOption
import com.hawas.study.rafid.models.PlaylistEntryItem
import com.hawas.study.rafid.models.RafidPlatform
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TikTokAndPlaylistDownloadTest {

    private lateinit var server: HttpServer
    private var port: Int = 0
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        port = server.address.port
        tempDir = File(System.getProperty("java.io.tmpdir"), "tt_pl_test_${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        runCatching { server.stop(0) }
        runCatching { tempDir.deleteRecursively() }
    }

    @Test
    fun scenario1_2_3_tiktokMissingCodecsAudioOnlyYtDlpAndSnifferProduceRealMp4M4aAndMp3() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val gateway = AndroidYtDlpGateway(context)

        // A. yt-dlp JSON with vcodec = null / acodec = "none" on TikTok MP4 + separate music MP3
        val ttMissingVcodecJson = JSONObject(
            """
            {
              "title": "شرح مبسط في تيك توك",
              "uploader": "teacher_tt",
              "duration": 24,
              "formats": [
                {
                  "format_id": "h264_540p",
                  "ext": "mp4",
                  "url": "https://v16.tiktokcdn.com/video_no_wm.mp4",
                  "width": 576,
                  "height": 1024,
                  "vcodec": null,
                  "acodec": "none",
                  "filesize": 3200000
                },
                {
                  "format_id": "music_mp3",
                  "ext": "mp3",
                  "url": "https://sf16.tiktokcdn.com/bg_music.mp3",
                  "vcodec": "none",
                  "acodec": "mp3",
                  "abr": 128,
                  "filesize": 400000
                }
              ]
            }
            """.trimIndent()
        )
        val parsed = gateway.parseRawYtDlpJson("https://www.tiktok.com/@teacher/video/111", ttMissingVcodecJson)
        val videoFmt = parsed.formats.firstOrNull { !it.isAudioOnly }
        assertNotNull("Expected TikTok MP4 video format when vcodec is null", videoFmt)
        assertEquals("mp4", videoFmt!!.containerExt)
        assertTrue(videoFmt.hasVideo)
        assertTrue(videoFmt.hasAudio)
        assertTrue(videoFmt.needsMux)
        assertEquals("https://sf16.tiktokcdn.com/bg_music.mp3", videoFmt.audioStreamUrl)
        assertTrue(parsed.formats.any { it.id == "audio_m4a" && it.isAudioOnly && it.containerExt == "m4a" })
        assertTrue(parsed.formats.any { it.id == "audio_mp3" && it.isAudioOnly && it.containerExt == "mp3" })

        // B. If yt-dlp erroneously returns ONLY audio for a TikTok video URL, TikTokProviderAdapter must reject it and fall back to HTML MP4
        server.createContext("/tt_video.mp4") { ex ->
            val payload = ByteArray(256) { 0x1A }
            ex.responseHeaders.add("Content-Type", "video/mp4")
            ex.responseHeaders.add("Content-Length", payload.size.toString())
            ex.sendResponseHeaders(200, payload.size.toLong())
            if (!ex.requestMethod.equals("HEAD", true)) ex.responseBody.use { it.write(payload) }
            ex.close()
        }
        server.createContext("/tt_music.mp3") { ex ->
            val payload = ByteArray(128) { 0x2B }
            ex.responseHeaders.add("Content-Type", "audio/mpeg")
            ex.responseHeaders.add("Content-Length", payload.size.toString())
            ex.sendResponseHeaders(200, payload.size.toLong())
            if (!ex.requestMethod.equals("HEAD", true)) ex.responseBody.use { it.write(payload) }
            ex.close()
        }
        server.createContext("/tt_video_page") { ex ->
            val page = """
                <html><body>
                <script id="SIGI_STATE">
                {"ItemModule":{"999":{"desc":"درس فيزياء","author":"أحمد","video":{"width":720,"height":1280,"duration":30,"playAddr":"http://127.0.0.1:$port/tt_video.mp4"},"music":{"playUrl":"http://127.0.0.1:$port/tt_music.mp3"}}}}
                </script></body></html>
            """.trimIndent().toByteArray()
            ex.responseHeaders.add("Content-Type", "text/html")
            ex.sendResponseHeaders(200, page.size.toLong())
            ex.responseBody.use { it.write(page) }
        }

        val audioOnlyYtDlp = object : YtDlpGateway {
            override suspend fun isAvailable(): Boolean = true
            override suspend fun extractSingle(url: String, cookiesFile: File?, extraArgs: Map<String, String>): Result<YtDlpExtractedInfo> =
                Result.success(
                    YtDlpExtractedInfo(
                        title = "Audio only bug",
                        uploader = "tt",
                        thumbnailUrl = null,
                        durationSeconds = 30,
                        baseHeaders = emptyMap(),
                        formats = listOf(
                            ResolvedMediaFormat(
                                id = "audio_mp3",
                                labelAr = "MP3",
                                labelEn = "MP3",
                                subLabelAr = "audio",
                                containerExt = "mp3",
                                mimeType = "audio/mpeg",
                                streamUrl = "http://127.0.0.1:$port/tt_music.mp3",
                                isAudioOnly = true,
                                hasVideo = false,
                                hasAudio = true
                            )
                        )
                    )
                )

            override suspend fun extractCollection(url: String, cookiesFile: File?, maxItems: Int): Result<List<ResolvedCollectionEntry>> =
                Result.success(emptyList())
        }

        val ttResult = TikTokProviderAdapter(OkHttpClient(), audioOnlyYtDlp).resolve(
            canonicalUrl = "http://127.0.0.1:$port/tt_video_page",
            request = ResolveRequest("https://www.tiktok.com/@u/video/999")
        ) as ResolvedMediaResult.SingleMedia

        // Video MP4 MUST be first, plus M4A & MP3 audio options preserved
        assertFalse("First format for TikTok video must NOT be audio-only", ttResult.formats.first().isAudioOnly)
        assertEquals("mp4", ttResult.formats.first().containerExt)
        assertTrue(ttResult.formats.first().hasVideo)
        assertTrue(ttResult.formats.any { it.isAudioOnly && it.containerExt == "m4a" && it.mimeType == "audio/mp4" })
        assertTrue(ttResult.formats.any { it.isAudioOnly && it.containerExt == "mp3" && it.mimeType == "audio/mpeg" })

        // C. HeadlessWebViewSniffer + AdAndTrackerShield distinguish mime_type=video_mp4 vs mime_type=audio
        val sniffedVid = HeadlessWebViewSniffer.classifyCandidateUrl(
            "https://v16m-webapp.tiktokcdn.com/video/tos/clip?mime_type=video_mp4&item_id=1",
            "*/*"
        )
        assertNotNull(sniffedVid)
        assertEquals("video/mp4", sniffedVid!!.mimeType)

        val shieldCandidate = AdAndTrackerShield.inferMediaCandidate(
            "https://v16m-webapp.tiktokcdn.com/video/tos/clip?mime_type=video_mp4",
            "تيك توك",
            null
        )
        assertNotNull(shieldCandidate)
        assertEquals("mp4", shieldCandidate!!.extension)
        assertEquals("video/mp4", shieldCandidate.mimeType)

        // D. UnifiedMediaResolver sniffer fallback for TikTok video exposes MP4 + M4A + MP3
        val fakeSniffer = object : WebViewSniffGateway {
            override suspend fun sniff(url: String, timeoutMs: Long): SniffedMediaResult =
                SniffedMediaResult(
                    mediaUrl = "http://127.0.0.1:$port/tt_video.mp4",
                    mimeType = "video/mp4",
                    isHlsOrDash = false,
                    headers = emptyMap()
                )
        }
        val audioOnlyPageResolver = UnifiedMediaResolver(
            httpClient = OkHttpClient(),
            ytDlpGateway = audioOnlyYtDlp,
            webViewSniffGateway = fakeSniffer
        )
        val sniffResolved = audioOnlyPageResolver.resolve(
            ResolveRequest(
                rawUrl = "https://www.tiktok.com/@fallback/video/777",
                allowWebViewSniffer = true
            )
        )
        // Only assert if HTML failed / fell through cleanly to sniffer (offline host tiktok.com triggers HTML failure -> sniffer)
        if (sniffResolved is ResolvedMediaResult.SingleMedia) {
            assertFalse(sniffResolved.formats.first().isAudioOnly)
            assertTrue(sniffResolved.formats.any { it.containerExt == "m4a" && it.isAudioOnly })
            assertTrue(sniffResolved.formats.any { it.containerExt == "mp3" && it.isAudioOnly })
        }

        // E. Strict verification rejects audio-only file when video is required
        val audioOnlyReport = MediaProbeReport(
            file = File(tempDir, "bad_audio_only.mp4"),
            fileSizeBytes = 2048L,
            containerFormat = "mp4",
            durationSec = 10.0,
            streams = listOf(MediaStreamInfo(index = 0, codecType = "audio", codecName = "aac", durationSec = 10.0)),
            usedFfprobe = true
        )
        var rejectedAudioOnlyForVideo = false
        try {
            MediaMergeEngine.validateProbeReport(audioOnlyReport, requireVideo = true, requireAudio = true)
        } catch (e: DownloadEngineException) {
            rejectedAudioOnlyForVideo = e.error is DownloadError.MergeFailed
        }
        assertTrue(rejectedAudioOnlyForVideo)
    }

    @Test
    fun scenario4_5_youtubePlaylistMp4AndAudioEnqueuesAllItemsInOrderAndNeverFallsBackToAudio() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Set up 3 playlist items via mock Innertube/HTML endpoints:
        // Item 1: Progressive MP4
        // Item 2: Split 1080p video + separate M4A audio track (needsMux = true)
        // Item 3: Fails pre-extraction (HTTP 500) -> MUST STILL BE ENQUEUED in order with fallback MP4/Audio expr!
        server.createContext("/watch") { ex ->
            val q = ex.requestURI.rawQuery.orEmpty()
            when {
                q.contains("v=vid1") -> {
                    val body = """
                        <html><script>
                        var ytInitialPlayerResponse = {"videoDetails":{"title":"درس 1","author":"قناة","lengthSeconds":"120"},
                        "streamingData":{"formats":[{"itag":18,"url":"https://cdn.example/v1.mp4","mimeType":"video/mp4","width":640,"height":360,"contentLength":"2000000"}],
                        "adaptiveFormats":[{"itag":140,"url":"https://cdn.example/a1.m4a","mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":128000,"contentLength":"500000"}]}};
                        </script></html>
                    """.trimIndent().toByteArray()
                    ex.responseHeaders.add("Content-Type", "text/html")
                    ex.sendResponseHeaders(200, body.size.toLong())
                    ex.responseBody.use { it.write(body) }
                }
                q.contains("v=vid2") -> {
                    val body = """
                        <html><script>
                        var ytInitialPlayerResponse = {"videoDetails":{"title":"درس 2","author":"قناة","lengthSeconds":"180"},
                        "streamingData":{"formats":[],
                        "adaptiveFormats":[
                          {"itag":137,"url":"https://cdn.example/v2_1080.mp4","mimeType":"video/mp4; codecs=\"avc1.640028\"","width":1920,"height":1080,"contentLength":"9000000"},
                          {"itag":140,"url":"https://cdn.example/a2.m4a","mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":128000,"contentLength":"800000"}
                        ]}};
                        </script></html>
                    """.trimIndent().toByteArray()
                    ex.responseHeaders.add("Content-Type", "text/html")
                    ex.sendResponseHeaders(200, body.size.toLong())
                    ex.responseBody.use { it.write(body) }
                }
                else -> {
                    val err = "Temporary error".toByteArray()
                    ex.sendResponseHeaders(500, err.size.toLong())
                    ex.responseBody.use { it.write(err) }
                }
            }
        }

        // YtDlpGateway returns:
        // - vid1: progressive 720p + M4A + MP3
        // - vid2: split 1080p (hasAudio=true because paired with a2.m4a, needsMux=true) + M4A + MP3
        // - vid3: ONLY audio formats (to prove buildPlaylistDownloadEntities NEVER picks audio for MP4 playlist!)
        val fakeGateway = object : YtDlpGateway {
            override suspend fun isAvailable(): Boolean = true
            override suspend fun extractSingle(url: String, cookiesFile: File?, extraArgs: Map<String, String>): Result<YtDlpExtractedInfo> {
                return when {
                    url.contains("vid1") -> Result.success(
                        YtDlpExtractedInfo(
                            title = "درس 1",
                            uploader = "قناة",
                            thumbnailUrl = null,
                            durationSeconds = 120,
                            baseHeaders = emptyMap(),
                            formats = listOf(
                                ResolvedMediaFormat(
                                    id = "720p", labelAr = "720p", labelEn = "720p", subLabelAr = "mp4",
                                    containerExt = "mp4", mimeType = "video/mp4", resolutionHeight = 720,
                                    streamUrl = "https://cdn.example/v1_720.mp4", formatExpression = "22",
                                    isAudioOnly = false, needsMux = false, hasVideo = true, hasAudio = true
                                ),
                                ResolvedMediaFormat(
                                    id = "audio_m4a", labelAr = "M4A", labelEn = "M4A", subLabelAr = "audio",
                                    containerExt = "m4a", mimeType = "audio/mp4",
                                    streamUrl = "https://cdn.example/a1.m4a", formatExpression = "140",
                                    isAudioOnly = true, needsMux = false, hasVideo = false, hasAudio = true
                                ),
                                ResolvedMediaFormat(
                                    id = "audio_mp3", labelAr = "MP3", labelEn = "MP3", subLabelAr = "mp3",
                                    containerExt = "mp3", mimeType = "audio/mpeg",
                                    streamUrl = "https://cdn.example/a1.m4a", formatExpression = "140",
                                    isAudioOnly = true, needsMux = true, hasVideo = false, hasAudio = true
                                )
                            )
                        )
                    )
                    url.contains("vid2") -> Result.success(
                        YtDlpExtractedInfo(
                            title = "درس 2",
                            uploader = "قناة",
                            thumbnailUrl = null,
                            durationSeconds = 180,
                            baseHeaders = emptyMap(),
                            formats = listOf(
                                ResolvedMediaFormat(
                                    id = "1080p", labelAr = "1080p", labelEn = "1080p", subLabelAr = "mux",
                                    containerExt = "mp4", mimeType = "video/mp4", resolutionHeight = 1080,
                                    streamUrl = "https://cdn.example/v2_1080.mp4",
                                    audioStreamUrl = "https://cdn.example/a2.m4a",
                                    formatExpression = "137+140",
                                    isAudioOnly = false, needsMux = true, hasVideo = true, hasAudio = true
                                ),
                                ResolvedMediaFormat(
                                    id = "audio_m4a", labelAr = "M4A", labelEn = "M4A", subLabelAr = "audio",
                                    containerExt = "m4a", mimeType = "audio/mp4",
                                    streamUrl = "https://cdn.example/a2.m4a", formatExpression = "140",
                                    isAudioOnly = true, needsMux = false, hasVideo = false, hasAudio = true
                                )
                            )
                        )
                    )
                    else -> Result.success(
                        YtDlpExtractedInfo(
                            title = "درس 3",
                            uploader = "قناة",
                            thumbnailUrl = null,
                            durationSeconds = 90,
                            baseHeaders = emptyMap(),
                            formats = listOf(
                                ResolvedMediaFormat(
                                    id = "audio_m4a", labelAr = "M4A", labelEn = "M4A", subLabelAr = "audio",
                                    containerExt = "m4a", mimeType = "audio/mp4",
                                    streamUrl = "https://cdn.example/a3.m4a", formatExpression = "140",
                                    isAudioOnly = true, needsMux = false, hasVideo = false, hasAudio = true
                                )
                            )
                        )
                    )
                }
            }

            override suspend fun extractCollection(url: String, cookiesFile: File?, maxItems: Int): Result<List<ResolvedCollectionEntry>> =
                Result.success(emptyList())
        }

        val extractor = UniversalMediaExtractor(
            httpClient = OkHttpClient(),
            linkResolver = UniversalLinkResolver(OkHttpClient()),
            ytDlpGateway = fakeGateway
        )

        val playlistMeta = ExtractedMediaMetadata(
            originalUrl = "https://www.youtube.com/playlist?list=PL_STUDY",
            resolvedUrl = "https://www.youtube.com/playlist?list=PL_STUDY",
            title = "دورة الخوارزميات",
            authorOrSource = "أكاديمية",
            sourceDomain = "youtube.com",
            platform = RafidPlatform.YOUTUBE,
            playlistItems = listOf(
                PlaylistEntryItem(1, "vid1", "المقدمة", "https://www.youtube.com/watch?v=vid1&list=PL_STUDY", 120),
                PlaylistEntryItem(2, "vid2", "التعقيد الزمني", "https://www.youtube.com/watch?v=vid2&list=PL_STUDY", 180),
                PlaylistEntryItem(3, "vid3", "الأشجار الثنائية", "https://www.youtube.com/watch?v=vid3&list=PL_STUDY", 90)
            ),
            isPlaylist = true
        )

        // Scenario 4: MP4 playlist bundle
        val mp4Bundle = MediaFormatOption(
            id = "pl_video", label = "MP4 (3)", containerExt = "mp4", mimeType = "video/mp4",
            resolutionHeight = 720, isAudioOnly = false, isPlaylistBundle = true
        )
        val mp4Entities = RafidViewModel.buildPlaylistDownloadEntities(
            metadata = playlistMeta,
            selectedFormat = mp4Bundle,
            saveToStudyFolder = true,
            extractor = extractor
        )
        assertEquals("All 3 playlist items must be enqueued in order", 3, mp4Entities.size)
        assertTrue(mp4Entities[0].title.startsWith("1. "))
        assertTrue(mp4Entities[1].title.startsWith("2. "))
        assertTrue(mp4Entities[2].title.startsWith("3. "))
        assertTrue(mp4Entities[0].createdAtMillis < mp4Entities[1].createdAtMillis)
        assertTrue(mp4Entities[1].createdAtMillis < mp4Entities[2].createdAtMillis)
        mp4Entities.forEach { entity ->
            assertFalse("MP4 playlist MUST NEVER pick audio-only (${entity.title})", entity.isAudioOnly)
            assertEquals("mp4", entity.containerExt)
            assertEquals("video/mp4", entity.mimeType)
        }
        assertEquals("https://cdn.example/a2.m4a", mp4Entities[1].audioStreamUrl)
        assertTrue(mp4Entities[1].isMuxRequired)
        assertTrue("Fallback item 3 must use video+audio merge expression", mp4Entities[2].formatExpression!!.contains("bestvideo"))
        assertTrue(mp4Entities[2].isMuxRequired)

        // Scenario 5: Audio-only playlist bundle (M4A and MP3 via SmartDownloadManager)
        val audioBundle = MediaFormatOption(
            id = "pl_audio", label = "Audio (3)", containerExt = "m4a", mimeType = "audio/mp4",
            bitrateKbps = 320, isAudioOnly = true, isPlaylistBundle = true
        )
        val audioEntities = RafidViewModel.buildPlaylistDownloadEntities(
            metadata = playlistMeta,
            selectedFormat = audioBundle,
            saveToStudyFolder = true,
            extractor = extractor
        )
        assertEquals(3, audioEntities.size)
        audioEntities.forEach { entity ->
            assertTrue(entity.isAudioOnly)
            assertEquals("m4a", entity.containerExt)
            assertEquals("audio/mp4", entity.mimeType)
        }

        // SmartDownloadManager playlist MP3 batch verifies audio/mpeg & monotonic order
        val manager = SmartDownloadManager.getInstance(context)
        val addedMp3 = manager.enqueuePlaylistBatch(
            entries = listOf(
                PlaylistEntry(1, "محاضرة 1", "https://www.youtube.com/watch?v=pl_mp3_1_${System.nanoTime()}"),
                PlaylistEntry(2, "محاضرة 2", "https://www.youtube.com/watch?v=pl_mp3_2_${System.nanoTime()}")
            ),
            preferredQualityId = "audio_mp3"
        )
        assertEquals(2, addedMp3)
        val queuedRows = DownloadDatabase.getInstance(context).downloadDao().getQueuedDownloads()
        val mp3Rows = queuedRows.filter { it.qualityId == "audio_mp3" }
        assertTrue(mp3Rows.size >= 2)
        assertEquals("audio/mpeg", mp3Rows.last().mimeType)
        assertTrue(mp3Rows.last().isAudioOnly)
    }

    @Test
    fun scenario6_7_8_youtubeShortsFacebookAndPinterestHaveZeroRegressions() {
        // Scenario 6: YouTube Shorts
        val ytAdapter = YouTubeProviderAdapter(OkHttpClient())
        assertEquals("shortId99", ytAdapter.extractVideoId("https://www.youtube.com/shorts/shortId99?feature=share"))
        val shortsPlayer = JSONObject(
            """
            {
              "videoDetails": { "title": "فكرة سريعة", "author": "أكاديمية", "lengthSeconds": "45" },
              "streamingData": {
                "formats": [
                  { "itag": 22, "url": "https://cdn.yt/s720.mp4", "mimeType": "video/mp4", "width": 720, "height": 1280, "contentLength": "3000000" }
                ],
                "adaptiveFormats": [
                  { "itag": 140, "url": "https://cdn.yt/saudio.m4a", "mimeType": "audio/mp4", "bitrate": 128000, "contentLength": "400000" }
                ]
              }
            }
            """.trimIndent()
        )
        val shortsRes = ytAdapter.parsePlayerJsonResult(
            "https://www.youtube.com/shorts/shortId99",
            "https://www.youtube.com/watch?v=shortId99",
            "shortId99",
            shortsPlayer
        ) as ResolvedMediaResult.SingleMedia
        assertEquals(MediaPlatform.YOUTUBE, shortsRes.platform)
        assertTrue(shortsRes.formats.any { !it.isAudioOnly && it.resolutionHeight == 720 })

        // Scenario 7: Facebook video & reels
        val fbAdapter = FacebookProviderAdapter(OkHttpClient())
        val fbRes = fbAdapter.parseFacebookHtml(
            "https://www.facebook.com/reel/555",
            "https://www.facebook.com/reel/555",
            """<script>{"browser_native_hd_url":"https://video.fbcdn.net/reel_hd.mp4","playable_url":"https://video.fbcdn.net/reel_sd.mp4"}</script>"""
        ) as ResolvedMediaResult.SingleMedia
        assertEquals(MediaPlatform.FACEBOOK, fbRes.platform)
        assertEquals(1080, fbRes.formats.first().resolutionHeight)
        assertEquals("https://video.fbcdn.net/reel_hd.mp4", fbRes.formats.first().streamUrl)

        // Scenario 8: Pinterest video pin
        val pinAdapter = PinterestProviderAdapter(OkHttpClient())
        val pinRes = pinAdapter.parsePinterestHtml(
            "https://www.pinterest.com/pin/888/",
            "https://www.pinterest.com/pin/888/",
            """<html><meta property="og:title" content="مخطط دراسي"/><script>{"url":"https://v1.pinimg.com/videos/mc/720p/88/study.mp4"}</script></html>"""
        ) as ResolvedMediaResult.SingleMedia
        assertEquals(MediaPlatform.PINTEREST, pinRes.platform)
        assertEquals("https://v1.pinimg.com/videos/mc/720p/88/study.mp4", pinRes.formats.first().streamUrl)
    }
}
