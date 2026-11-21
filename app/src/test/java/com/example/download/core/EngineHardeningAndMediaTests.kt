package com.example.download.core

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.download.core.downloader.DownloadJobSpec
import com.example.download.core.downloader.InMemoryChunkMetadataStore
import com.example.download.core.downloader.SegmentedDownloadEngine
import com.example.download.core.media.MediaMergeEngine
import com.example.download.core.model.DiagnosticRedactor
import com.example.download.core.model.DownloadEngineException
import com.example.download.core.model.DownloadError
import com.example.download.core.model.MediaPlatform
import com.example.download.core.model.ResolveRequest
import com.example.download.core.model.ResolvedCollectionEntry
import com.example.download.core.model.ResolvedMediaFormat
import com.example.download.core.model.ResolvedMediaResult
import com.example.download.core.resolver.AndroidYtDlpGateway
import com.example.download.core.resolver.InstagramProviderAdapter
import com.example.download.core.resolver.PinterestProviderAdapter
import com.example.download.core.resolver.TikTokProviderAdapter
import com.example.download.core.resolver.YouTubeProviderAdapter
import com.example.download.core.resolver.YtDlpExtractedInfo
import com.example.download.core.resolver.YtDlpGateway
import com.example.download.core.updater.AndroidEngineRuntimeInspector
import com.hawas.study.rafid.data.RafidDatabase
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EngineHardeningAndMediaTests {

    private lateinit var tempDir: File
    private lateinit var server: HttpServer
    private var port: Int = 0

    @Before
    fun setup() {
        tempDir = Files.createTempDirectory("hawas_engine_hardening_test").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newFixedThreadPool(4)
        server.start()
        port = server.address.port
    }

    @After
    fun teardown() {
        runCatching { server.stop(0) }
        tempDir.deleteRecursively()
    }

    // -------------------------------------------------------------------------
    // 1. FFprobe Parsing & Final Artifact Verification Rules
    // -------------------------------------------------------------------------

    @Test
    fun ffprobeVerification_rejectsHtmlMissingStreamsAndDurationMismatch_acceptsDualStreamMedia() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // A. HTML payload disguised as .mp4 must fail with UnsupportedMedia("text/html")
        val fakeHtmlMp4 = File(tempDir, "fake_tiktok.mp4").apply {
            writeText("<!DOCTYPE html><html><head><title>TikTok</title></head><body>Blocked</body></html>")
        }
        assertTrue(MediaMergeEngine.isHtmlOrTextPayload(fakeHtmlMp4))
        try {
            MediaMergeEngine.verifyFinalArtifact(context, fakeHtmlMp4, requireVideo = true, requireAudio = true)
            fail("Expected UnsupportedMedia for HTML payload disguised as MP4")
        } catch (ex: DownloadEngineException) {
            assertTrue(ex.error is DownloadError.UnsupportedMedia)
        }

        // B. 0-byte / missing file must fail with IntegrityCheckFailed
        val emptyFile = File(tempDir, "empty.mp4").apply { createNewFile() }
        try {
            MediaMergeEngine.verifyFinalArtifact(context, emptyFile, requireVideo = true, requireAudio = false)
            fail("Expected IntegrityCheckFailed for 0-byte file")
        } catch (ex: DownloadEngineException) {
            assertTrue(ex.error is DownloadError.IntegrityCheckFailed)
        }

        // C. Real ffprobe JSON: video + audio valid MP4 passes verification cleanly
        val dummyVideoFile = File(tempDir, "valid.mp4").apply { writeBytes(ByteArray(1024) { 0x1A }) }
        val validProbeJson = """
            {
              "format": { "format_name": "mov,mp4,m4a,3gp,3g2,mj2", "duration": "185.40" },
              "streams": [
                { "index": 0, "codec_type": "video", "codec_name": "h264", "width": 1920, "height": 1080, "duration": "185.40" },
                { "index": 1, "codec_type": "audio", "codec_name": "aac", "duration": "185.38", "sample_rate": "44100", "channels": 2, "tags": { "language": "ar" } }
              ]
            }
        """.trimIndent()
        val validReport = MediaMergeEngine.parseFfprobeJson(dummyVideoFile, validProbeJson)
        assertNotNull(validReport)
        val validated = MediaMergeEngine.validateProbeReport(validReport!!, requireVideo = true, requireAudio = true)
        assertTrue(validated.hasVideoStream)
        assertTrue(validated.hasAudioStream)
        assertEquals("ar", validated.audioStreams.first().language)

        // D. Video-only stream when audio is required must FAIL (prevents silent YouTube/Instagram bug)
        val silentProbeJson = """
            {
              "format": { "format_name": "mov,mp4,m4a", "duration": "620.0" },
              "streams": [
                { "index": 0, "codec_type": "video", "codec_name": "h264", "width": 1920, "height": 1080, "duration": "620.0" }
              ]
            }
        """.trimIndent()
        val silentReport = MediaMergeEngine.parseFfprobeJson(dummyVideoFile, silentProbeJson)!!
        try {
            MediaMergeEngine.validateProbeReport(silentReport, requireVideo = true, requireAudio = true)
            fail("Expected MissingAudioStream MergeFailed")
        } catch (ex: DownloadEngineException) {
            assertTrue(ex.error is DownloadError.MergeFailed)
            assertTrue(ex.error.technicalDetail.contains("MissingAudioStream"))
        }

        // E. Audio-only stream when video is required must FAIL with MissingVideoStream
        val audioOnlyJson = """
            {
              "format": { "format_name": "m4a", "duration": "180.0" },
              "streams": [
                { "index": 0, "codec_type": "audio", "codec_name": "aac", "duration": "180.0" }
              ]
            }
        """.trimIndent()
        val audioOnlyReport = MediaMergeEngine.parseFfprobeJson(dummyVideoFile, audioOnlyJson)!!
        try {
            MediaMergeEngine.validateProbeReport(audioOnlyReport, requireVideo = true, requireAudio = true)
            fail("Expected MissingVideoStream MergeFailed")
        } catch (ex: DownloadEngineException) {
            assertTrue(ex.error is DownloadError.MergeFailed)
            assertTrue(ex.error.technicalDetail.contains("MissingVideoStream"))
        }

        // F. Severe audio/video duration mismatch (>15s diff and <0.4 ratio) must FAIL
        val mismatchJson = """
            {
              "format": { "format_name": "mp4", "duration": "600.0" },
              "streams": [
                { "index": 0, "codec_type": "video", "codec_name": "h264", "width": 1280, "height": 720, "duration": "600.0" },
                { "index": 1, "codec_type": "audio", "codec_name": "aac", "duration": "30.0" }
              ]
            }
        """.trimIndent()
        val mismatchReport = MediaMergeEngine.parseFfprobeJson(dummyVideoFile, mismatchJson)!!
        try {
            MediaMergeEngine.validateProbeReport(mismatchReport, requireVideo = true, requireAudio = true)
            fail("Expected MergeFailed for abnormal audio/video duration mismatch")
        } catch (ex: DownloadEngineException) {
            assertTrue(ex.error is DownloadError.MergeFailed)
            assertTrue(ex.error.technicalDetail.contains("duration mismatch", ignoreCase = true))
        }
    }

    // -------------------------------------------------------------------------
    // 2. YouTube Multi-Language, Dubbed Tracks (MrBeast scenario), Shorts, Playlist, Channel
    // -------------------------------------------------------------------------

    @Test
    fun youtubeYtDlpCatalog_pairs1080pWithAudioAndExposesDubbedAndOriginalTracks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val gateway = AndroidYtDlpGateway(context)

        val ytDlpPayload = JSONObject(
            """
            {
              "title": "I Built 100 Houses (Dubbed Test)",
              "uploader": "MrBeast",
              "duration": 980,
              "thumbnail": "https://i.ytimg.com/vi/abc12345678/maxresdefault.jpg",
              "formats": [
                { "format_id": "160", "ext": "mp4", "url": "https://rr1.googlevideo.com/144.mp4", "width": 256, "height": 144, "fps": 30, "vcodec": "avc1.4d400c", "acodec": "none", "filesize": 12000000 },
                { "format_id": "18", "ext": "mp4", "url": "https://rr1.googlevideo.com/360.mp4", "width": 640, "height": 360, "fps": 30, "vcodec": "avc1.42001E", "acodec": "mp4a.40.2", "filesize": 45000000 },
                { "format_id": "137", "ext": "mp4", "url": "https://rr1.googlevideo.com/1080.mp4", "width": 1920, "height": 1080, "fps": 60, "vcodec": "avc1.64002a", "acodec": "none", "filesize": 180000000 },
                { "format_id": "140-en", "ext": "m4a", "url": "https://rr1.googlevideo.com/audio_en.m4a", "vcodec": "none", "acodec": "mp4a.40.2", "language": "en", "language_preference": 10, "format_note": "English original", "abr": 128, "filesize": 15000000 },
                { "format_id": "140-ar", "ext": "m4a", "url": "https://rr1.googlevideo.com/audio_ar.m4a", "vcodec": "none", "acodec": "mp4a.40.2", "language": "ar", "language_preference": 5, "format_note": "Arabic (auto-dubbed)", "abr": 128, "filesize": 15100000 }
              ]
            }
            """.trimIndent()
        )

        val info = gateway.parseRawYtDlpJson("https://www.youtube.com/watch?v=abc12345678", ytDlpPayload)
        // Must preserve 1080p with paired audio + mux flag (never video-only without audio!)
        val f1080Primary = info.formats.firstOrNull { it.resolutionHeight == 1080 && !it.isAudioOnly }
        assertNotNull("Expected 1080p option", f1080Primary)
        assertTrue(f1080Primary!!.needsMux)
        assertTrue(f1080Primary.hasAudio)
        assertNotNull(f1080Primary.audioStreamUrl)
        assertTrue(f1080Primary.formatExpression!!.contains("+"))

        // Must also expose Arabic dubbed variant for 1080p AND Arabic audio-only track
        val dubbed1080 = info.formats.firstOrNull { !it.isAudioOnly && it.resolutionHeight == 1080 && it.languageCode == "ar" }
        assertNotNull("Expected 1080p Arabic dubbed option", dubbed1080)
        assertEquals("https://rr1.googlevideo.com/audio_ar.m4a", dubbed1080!!.audioStreamUrl)
        assertEquals("137+140-ar", dubbed1080.formatExpression)
        assertTrue(dubbed1080.isAutoDubbed)

        // Ensure 144p, 360p, and audio-only M4A & MP3 are also exposed honestly
        assertTrue(info.formats.any { !it.isAudioOnly && it.resolutionHeight == 144 && it.hasAudio })
        assertTrue(info.formats.any { !it.isAudioOnly && it.resolutionHeight == 360 && it.hasAudio })
        assertTrue(info.formats.any { it.isAudioOnly && it.containerExt == "m4a" && it.languageCode == "ar" })
        assertTrue(info.formats.any { it.isAudioOnly && it.containerExt == "mp3" })
    }

    @Test
    fun youtubeInnertubePlayerJson_discoversMultiLanguageAudioTracksAndShorts() {
        val adapter = YouTubeProviderAdapter(OkHttpClient())
        val innertubeDubbed = JSONObject(
            """
            {
              "playabilityStatus": { "status": "OK" },
              "videoDetails": {
                "videoId": "dubTest123",
                "title": "World's Largest Experiment",
                "author": "MrBeast",
                "lengthSeconds": "1240"
              },
              "streamingData": {
                "adaptiveFormats": [
                  { "itag": 137, "mimeType": "video/mp4; codecs=\"avc1.640028\"", "width": 1920, "height": 1080, "fps": 30, "bitrate": 2400000, "contentLength": "220000000", "url": "https://cdn.yt.test/v1080.mp4" },
                  { "itag": 140, "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"", "bitrate": 128000, "contentLength": "19000000", "url": "https://cdn.yt.test/a_en.m4a", "audioTrack": { "id": "en.0", "displayName": "English original", "audioIsDefault": true } },
                  { "itag": 140, "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"", "bitrate": 128000, "contentLength": "19050000", "url": "https://cdn.yt.test/a_ar.m4a", "isAutoDubbed": true, "audioTrack": { "id": "ar.4", "displayName": "العربية", "audioIsDefault": false } }
                ]
              }
            }
            """.trimIndent()
        )
        val res = adapter.parsePlayerJsonResult(
            originalUrl = "https://youtube.com/shorts/dubTest123",
            canonicalUrl = "https://www.youtube.com/watch?v=dubTest123",
            videoId = "dubTest123",
            playerJson = innertubeDubbed
        )
        assertTrue(res is ResolvedMediaResult.SingleMedia)
        res as ResolvedMediaResult.SingleMedia
        assertTrue(res.formats.any { !it.isAudioOnly && it.resolutionHeight == 1080 && it.languageCode == "en" })
        assertTrue(res.formats.any { !it.isAudioOnly && it.resolutionHeight == 1080 && it.languageCode == "ar" && it.isAutoDubbed })
        assertTrue(res.formats.any { it.isAudioOnly && it.languageCode == "ar" })
    }

    // -------------------------------------------------------------------------
    // 3. TikTok Hardening: HTML Rejection, Set-Cookie Forwarding, No Fake 1080p, Large Stream + Expiry Refresh
    // -------------------------------------------------------------------------

    @Test
    fun tiktokAdapter_rejectsTextHtmlStreamAndDoesNotFabricateFake1080p() = runBlocking {
        // Server simulates TikTok video URL resolving to a text/html challenge page
        server.createContext("/tt_html_stream.mp4") { ex ->
            val html = "<!DOCTYPE html><html><body>Access Denied / Captcha</body></html>".toByteArray()
            ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            ex.responseHeaders.add("Content-Length", html.size.toString())
            ex.sendResponseHeaders(200, html.size.toLong())
            if (!ex.requestMethod.equals("HEAD", true)) {
                ex.responseBody.use { it.write(html) }
            }
            ex.close()
        }
        server.createContext("/tt_page") { ex ->
            val page = """
                <html><body>
                <script id="__UNIVERSAL_DATA_FOR_REHYDRATION__">
                {"__DEFAULT_SCOPE__":{"webapp.video-detail":{"itemInfo":{"itemStruct":{
                  "desc":"A quick easy trick",
                  "video":{"duration":18,"playAddr":"http://127.0.0.1:$port/tt_html_stream.mp4"}
                }}}}}
                </script></body></html>
            """.trimIndent().toByteArray()
            ex.responseHeaders.add("Content-Type", "text/html")
            ex.responseHeaders.add("Set-Cookie", "tt_chain_token=secret123; Path=/")
            ex.sendResponseHeaders(200, page.size.toLong())
            ex.responseBody.use { it.write(page) }
        }

        val adapter = TikTokProviderAdapter(OkHttpClient())
        val result = adapter.resolve(
            canonicalUrl = "http://127.0.0.1:$port/tt_page",
            request = ResolveRequest("https://www.tiktok.com/@repair/video/123456")
        )
        assertTrue("Expected Failure(UnsupportedMedia) instead of saving HTML as MP4", result is ResolvedMediaResult.Failure)
        val failure = result as ResolvedMediaResult.Failure
        assertTrue(failure.error is DownloadError.UnsupportedMedia)

        // Verify parseTikTokHtml with missing height NEVER fabricates 1080p
        val parsedUnknownRes = adapter.parseTikTokHtml(
            originalUrl = "https://www.tiktok.com/@u/video/1",
            canonicalUrl = "https://www.tiktok.com/@u/video/1",
            html = """<meta property="og:video" content="https://v16.tiktokcdn.com/clip.mp4"/>"""
        ) as ResolvedMediaResult.SingleMedia
        assertEquals(0, parsedUnknownRes.formats.first().resolutionHeight)
        assertFalse(parsedUnknownRes.formats.first().labelEn.contains("1080"))
    }

    @Test
    fun tiktokLargeStreamingAndExpiredUrlRefresh_recoverAndProduceExactSha256() = runBlocking {
        // 6 MiB payload (exceeds small buffer threshold, tests streaming chunks without RAM bloat)
        val payload = ByteArray(6 * 1024 * 1024 + 777) { idx -> ((idx * 17 + 5) and 0xFF).toByte() }
        val expectedSha = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
        val refreshCount = AtomicInteger(0)

        server.createContext("/expired_tt.mp4") { ex ->
            val html = "<html><body>URL signature expired</body></html>".toByteArray()
            ex.responseHeaders.add("Content-Type", "text/html")
            ex.responseHeaders.add("Content-Length", html.size.toString())
            ex.sendResponseHeaders(200, html.size.toLong())
            if (!ex.requestMethod.equals("HEAD", true)) ex.responseBody.use { it.write(html) }
            ex.close()
        }
        server.createContext("/fresh_tt.mp4") { ex ->
            val hdrCookie = ex.requestHeaders.getFirst("Cookie")
            assertEquals("tt_chain_token=freshCookie", hdrCookie)
            if (ex.requestMethod.equals("HEAD", true)) {
                ex.responseHeaders.add("Content-Type", "video/mp4")
                ex.responseHeaders.add("Accept-Ranges", "bytes")
                ex.responseHeaders.add("Content-Length", payload.size.toString())
                ex.responseHeaders.add("ETag", "\"tt-large-v1\"")
                ex.sendResponseHeaders(200, -1)
                ex.close()
                return@createContext
            }
            val r = ex.requestHeaders.getFirst("Range").removePrefix("bytes=").split("-")
            val start = r[0].toInt()
            val end = (r.getOrNull(1)?.toIntOrNull() ?: (payload.size - 1)).coerceAtMost(payload.size - 1)
            val len = end - start + 1
            ex.responseHeaders.add("Content-Type", "video/mp4")
            ex.responseHeaders.add("Accept-Ranges", "bytes")
            ex.responseHeaders.add("ETag", "\"tt-large-v1\"")
            ex.responseHeaders.add("Content-Range", "bytes $start-$end/${payload.size}")
            ex.sendResponseHeaders(206, len.toLong())
            ex.responseBody.use { it.write(payload, start, len) }
        }

        val engine = SegmentedDownloadEngine(InMemoryChunkMetadataStore())
        val outFile = File(tempDir, "large_tt.mp4.part")
        val finalFile = engine.download(
            DownloadJobSpec(
                downloadId = "tt_large_refresh",
                url = "http://127.0.0.1:$port/expired_tt.mp4",
                headers = mapOf("Cookie" to "tt_chain_token=old"),
                targetPartFile = outFile,
                expectedMimeType = "video/mp4",
                requestedChunks = 4,
                onRefreshExpiredUrl = {
                    refreshCount.incrementAndGet()
                    "http://127.0.0.1:$port/fresh_tt.mp4" to mapOf("Cookie" to "tt_chain_token=freshCookie")
                }
            )
        )

        assertTrue("Expected expired URL refresh to be invoked", refreshCount.get() >= 1)
        assertEquals(payload.size.toLong(), finalFile.length())
        val actualSha = MessageDigest.getInstance("SHA-256").digest(finalFile.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals(expectedSha, actualSha)
    }

    // -------------------------------------------------------------------------
    // 4. Instagram: Full Format Catalog + DASH Audio Pairing + Single-Quality Honesty
    // -------------------------------------------------------------------------

    @Test
    fun instagramAdapter_exposesAllRealVideoVersionsAndPairsDashAudioViaYtDlp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val igAdapter = InstagramProviderAdapter(OkHttpClient())

        // A. Multiple real qualities inside video_versions JSON must ALL be returned
        val multiQualHtml = """
            <html><body>
            <script>
            {"video_versions":[
              {"url":"https://scontent.cdninstagram.com/v/reel_1080.mp4","width":1080,"height":1920},
              {"url":"https://scontent.cdninstagram.com/v/reel_720.mp4","width":720,"height":1280},
              {"url":"https://scontent.cdninstagram.com/v/reel_480.mp4","width":480,"height":854}
            ]}
            </script></body></html>
        """.trimIndent()
        val multiRes = igAdapter.parseInstagramHtml(
            "https://www.instagram.com/reel/multi1/",
            "https://www.instagram.com/reel/multi1/",
            multiQualHtml
        ) as ResolvedMediaResult.SingleMedia
        val heights = multiRes.formats.map { it.resolutionHeight }
        assertEquals(listOf(1080, 720, 480), heights)

        // B. Single-quality Instagram source must expose ONLY 1 quality (zero fabrication)
        val singleHtml = """<meta property="og:video:secure_url" content="https://scontent.cdninstagram.com/v/only_one.mp4"/>"""
        val singleRes = igAdapter.parseInstagramHtml(
            "https://www.instagram.com/reel/single1/",
            "https://www.instagram.com/reel/single1/",
            singleHtml
        ) as ResolvedMediaResult.SingleMedia
        assertEquals(1, singleRes.formats.size)
        assertEquals(0, singleRes.formats.first().resolutionHeight)

        // C. DASH video-only Instagram reel from yt-dlp JSON must pair audio + enable muxing
        val gateway = AndroidYtDlpGateway(context)
        val igDashJson = JSONObject(
            """
            {
              "title": "Instagram Study Reel",
              "uploader": "study_hub",
              "duration": 42,
              "formats": [
                { "format_id": "dash_v_1080", "ext": "mp4", "url": "https://scontent.cdninstagram.com/dash_v.mp4", "width": 1080, "height": 1920, "vcodec": "avc1.64002a", "acodec": "none", "filesize": 8000000 },
                { "format_id": "dash_a_1", "ext": "m4a", "url": "https://scontent.cdninstagram.com/dash_a.m4a", "vcodec": "none", "acodec": "mp4a.40.2", "abr": 128, "filesize": 700000 }
              ]
            }
            """.trimIndent()
        )
        val parsedDash = gateway.parseRawYtDlpJson("https://www.instagram.com/reel/dash1/", igDashJson)
        val vidFormat = parsedDash.formats.first { !it.isAudioOnly }
        assertEquals(1080, vidFormat.resolutionHeight)
        assertTrue(vidFormat.needsMux)
        assertTrue(vidFormat.hasAudio)
        assertEquals("https://scontent.cdninstagram.com/dash_a.m4a", vidFormat.audioStreamUrl)
        assertEquals("dash_v_1080+dash_a_1", vidFormat.formatExpression)
    }

    // -------------------------------------------------------------------------
    // 5. Pinterest Regression, Room MIGRATION_1_2, Redaction & Staged Updater Validation
    // -------------------------------------------------------------------------

    @Test
    fun pinterestZeroRegression_andDatabaseMigration1To2_andDiagnosticRedaction_allWorkSafely() {
        // A. Pinterest HTML extraction remains intact before any yt-dlp call
        val pinAdapter = PinterestProviderAdapter(OkHttpClient())
        val pinVideo = pinAdapter.parsePinterestHtml(
            originalUrl = "https://pin.it/abc",
            canonicalUrl = "https://www.pinterest.com/pin/112233/",
            html = """
                <html>
                <meta property="og:title" content="ملخص قواعد اللغة"/>
                <script>{"url":"https://v1.pinimg.com/videos/mc/720p/11/22/lesson.mp4"}</script>
                </html>
            """.trimIndent()
        ) as ResolvedMediaResult.SingleMedia
        assertEquals(MediaPlatform.PINTEREST, pinVideo.platform)
        assertEquals("https://v1.pinimg.com/videos/mc/720p/11/22/lesson.mp4", pinVideo.formats.first().streamUrl)

        // B. RafidDatabase MIGRATION_1_2 preserves prior download history & adds 6 v2 columns
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "rafid_migration_test_${System.currentTimeMillis()}.db"
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS rafid_downloads (
                            id TEXT NOT NULL PRIMARY KEY,
                            originalUrl TEXT NOT NULL,
                            resolvedUrl TEXT NOT NULL,
                            videoStreamUrl TEXT NOT NULL,
                            audioStreamUrl TEXT,
                            title TEXT NOT NULL,
                            authorOrSource TEXT NOT NULL,
                            sourceDomain TEXT NOT NULL,
                            platformName TEXT NOT NULL,
                            thumbnailUrl TEXT,
                            qualityLabel TEXT NOT NULL,
                            containerExt TEXT NOT NULL,
                            mimeType TEXT NOT NULL,
                            categoryName TEXT NOT NULL,
                            saveToStudyFolder INTEGER NOT NULL,
                            isMuxRequired INTEGER NOT NULL,
                            isAudioOnly INTEGER NOT NULL,
                            status TEXT NOT NULL,
                            downloadedBytes INTEGER NOT NULL,
                            totalBytes INTEGER NOT NULL,
                            speedBytesPerSec INTEGER NOT NULL,
                            etaSeconds INTEGER NOT NULL,
                            displayPath TEXT NOT NULL,
                            mediaStoreUri TEXT,
                            cookiesHeader TEXT,
                            userAgent TEXT,
                            errorMessage TEXT,
                            createdAtMillis INTEGER NOT NULL,
                            completedAtMillis INTEGER
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        INSERT INTO rafid_downloads (
                            id, originalUrl, resolvedUrl, videoStreamUrl, title, authorOrSource, sourceDomain,
                            platformName, qualityLabel, containerExt, mimeType, categoryName, saveToStudyFolder,
                            isMuxRequired, isAudioOnly, status, downloadedBytes, totalBytes, speedBytesPerSec,
                            etaSeconds, displayPath, createdAtMillis
                        ) VALUES (
                            'r_old_1', 'https://pin.it/1', 'https://pinterest.com/pin/1', 'https://v1.pinimg.com/v.mp4',
                            'بنترست محفوظ', 'Pinterest', 'pinterest.com', 'PINTEREST', '720p', 'mp4', 'video/mp4',
                            'VIDEO', 0, 0, 0, 'COMPLETED', 5000, 5000, 0, 0, 'Download/Rafid/Video/v.mp4', 1000
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        RafidDatabase.MIGRATION_1_2.migrate(db)
        db.query("SELECT id, title, status, engineState, headersJson, formatExpression, audioLanguage, errorCode, diagnosticLog FROM rafid_downloads WHERE id = 'r_old_1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("بنترست محفوظ", c.getString(1))
            assertEquals("COMPLETED", c.getString(2))
            assertEquals("QUEUED", c.getString(3))
        }
        helper.close()
        context.deleteDatabase(dbName)

        // C. DiagnosticRedactor strips cookies, tokens, authorization headers & signatures
        val rawDiag = DiagnosticRedactor.buildStructuredDiagnostic(
            platform = "TikTok",
            failStage = "DOWNLOADING_VIDEO",
            error = DownloadError.HttpError(403, "Cookie: tt_chain_token=TOP_SECRET; msToken=ABC123&sig=xyz999\nAuthorization: Bearer SECRET_BEARER")
        )
        assertFalse(rawDiag.contains("TOP_SECRET"))
        assertFalse(rawDiag.contains("ABC123"))
        assertFalse(rawDiag.contains("SECRET_BEARER"))
        assertTrue(rawDiag.contains("[REDACTED]"))

        // D. Updater staged artifact validation rejects HTML pages and accepts PK/shebang payloads
        val validZipStub = File(tempDir, "yt_valid").apply { writeBytes("PK\u0003\u0004".toByteArray() + ByteArray(256)) }
        val invalidHtmlStub = File(tempDir, "yt_html").apply { writeText("<html><body>502 Bad Gateway</body></html>".repeat(10)) }
        assertTrue(AndroidEngineRuntimeInspector.validateStagedYtDlpArtifact(validZipStub))
        assertFalse(AndroidEngineRuntimeInspector.validateStagedYtDlpArtifact(invalidHtmlStub))
    }
}
