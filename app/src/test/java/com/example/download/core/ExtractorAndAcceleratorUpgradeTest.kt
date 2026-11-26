package com.example.download.core

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.download.core.downloader.DownloadJobSpec
import com.example.download.core.downloader.InMemoryChunkMetadataStore
import com.example.download.core.downloader.SegmentedDownloadEngine
import com.example.download.core.model.ResolvedMediaResult
import com.example.download.core.resolver.TikTokProviderAdapter
import com.example.download.core.resolver.YouTubeProviderAdapter
import com.example.download.data.DownloadSettings
import com.hawas.study.rafid.data.RafidDatabase
import com.hawas.study.rafid.engine.UniversalMediaExtractor
import com.hawas.study.rafid.models.RafidConfigState
import com.hawas.study.rafid.models.RafidDownloadEntity
import com.hawas.study.rafid.models.RafidDownloadStatus
import com.hawas.study.rafid.models.RafidPlatform
import com.hawas.study.rafid.worker.DownloadQueueManager
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
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
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExtractorAndAcceleratorUpgradeTest {

    private lateinit var tempDir: File
    private lateinit var server: HttpServer
    private var port: Int = 0

    @Before
    fun setup() {
        tempDir = Files.createTempDirectory("extractor_accel_test").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newFixedThreadPool(12)
        server.start()
        port = server.address.port
    }

    @After
    fun teardown() {
        runCatching { server.stop(0) }
        tempDir.deleteRecursively()
    }

    @Test
    fun youtubeAdapter_exposesOnlyRealResolutions_usesMinDimensionForPortrait_andNeverFabricatesMissingQualities() {
        val adapter = YouTubeProviderAdapter(OkHttpClient())

        // 1. Portrait Shorts capped at 1080x1920 -> must expose 1080p, 720p, 360p (NOT 1920p, NOT fake 1440p/2160p)
        val portraitShortsJson = JSONObject(
            """
            {
              "playabilityStatus": { "status": "OK" },
              "videoDetails": {
                "videoId": "shortPort1",
                "title": "Portrait Math Trick",
                "author": "HawasMath",
                "lengthSeconds": "45"
              },
              "streamingData": {
                "formats": [
                  { "itag": 18, "mimeType": "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"", "width": 360, "height": 640, "contentLength": "2500000", "url": "https://cdn.yt.test/s360.mp4" }
                ],
                "adaptiveFormats": [
                  { "itag": 137, "mimeType": "video/mp4; codecs=\"avc1.640028\"", "width": 1080, "height": 1920, "fps": 60, "bitrate": 2600000, "contentLength": "14000000", "url": "https://cdn.yt.test/s1080.mp4" },
                  { "itag": 136, "mimeType": "video/mp4; codecs=\"avc1.4d401f\"", "width": 720, "height": 1280, "fps": 30, "bitrate": 1400000, "contentLength": "8000000", "url": "https://cdn.yt.test/s720.mp4" },
                  { "itag": 140, "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"", "bitrate": 128000, "contentLength": "720000", "url": "https://cdn.yt.test/saudio.m4a" }
                ]
              }
            }
            """.trimIndent()
        )
        val portraitRes = adapter.parsePlayerJsonResult(
            originalUrl = "https://youtube.com/shorts/shortPort1",
            canonicalUrl = "https://www.youtube.com/watch?v=shortPort1",
            videoId = "shortPort1",
            playerJson = portraitShortsJson
        ) as ResolvedMediaResult.SingleMedia

        val videoHeights = portraitRes.formats.filter { !it.isAudioOnly }.map { it.resolutionHeight }
        assertEquals(listOf(1080, 720, 360), videoHeights)
        assertFalse(videoHeights.contains(1920))
        assertFalse(videoHeights.contains(2160))
        assertFalse(videoHeights.contains(1440))

        // 2. Video capped at 720p must NOT fabricate 1080p/1440p/2160p
        val max720Json = JSONObject(
            """
            {
              "playabilityStatus": { "status": "OK" },
              "videoDetails": { "videoId": "cap720", "title": "720p Lecture", "author": "Prof", "lengthSeconds": "300" },
              "streamingData": {
                "formats": [
                  { "itag": 22, "mimeType": "video/mp4", "width": 1280, "height": 720, "contentLength": "42000000", "url": "https://cdn.yt.test/v720.mp4" },
                  { "itag": 18, "mimeType": "video/mp4", "width": 640, "height": 360, "contentLength": "18000000", "url": "https://cdn.yt.test/v360.mp4" }
                ]
              }
            }
            """.trimIndent()
        )
        val res720 = adapter.parsePlayerJsonResult(
            "https://www.youtube.com/watch?v=cap720",
            "https://www.youtube.com/watch?v=cap720",
            "cap720",
            max720Json
        ) as ResolvedMediaResult.SingleMedia
        val heights720 = res720.formats.filter { !it.isAudioOnly }.map { it.resolutionHeight ?: 0 }
        assertEquals(listOf(720, 360), heights720)
        assertFalse(heights720.any { it >= 1080 })
    }

    @Test
    fun tiktokAdapter_parseTikWmDataJson_readsRealBitRateStreamsAndAvoidsHardcodedQualities() {
        val adapter = TikTokProviderAdapter(OkHttpClient())

        // A. TikWM response with real bit_rate entries (1080p portrait, 720p portrait, 540p portrait)
        val tikWmMulti = JSONObject(
            """
            {
              "id": "73000001",
              "title": "شرح فيزياء سريع",
              "duration": 22,
              "author": { "nickname": "Teacher" },
              "hdplay": "https://cdn.tiktok.test/hd_1080.mp4",
              "play": "https://cdn.tiktok.test/sd_540.mp4",
              "bit_rate": [
                {
                  "gear_name": "normal_1080_0",
                  "bit_rate": 2400000,
                  "play_addr": { "width": 1080, "height": 1920, "data_size": 6500000, "url_list": ["https://cdn.tiktok.test/hd_1080.mp4"] }
                },
                {
                  "gear_name": "normal_720_0",
                  "bit_rate": 1300000,
                  "play_addr": { "width": 720, "height": 1280, "data_size": 3600000, "url_list": ["https://cdn.tiktok.test/md_720.mp4"] }
                },
                {
                  "gear_name": "normal_540_0",
                  "bit_rate": 750000,
                  "play_addr": { "width": 540, "height": 960, "data_size": 2100000, "url_list": ["https://cdn.tiktok.test/sd_540.mp4"] }
                }
              ]
            }
            """.trimIndent()
        )
        val multiResult = adapter.parseTikWmDataJson(
            originalUrl = "https://www.tiktok.com/@teacher/video/73000001",
            canonicalUrl = "https://www.tiktok.com/@teacher/video/73000001",
            data = tikWmMulti
        ) as ResolvedMediaResult.SingleMedia
        val multiFormats = multiResult.formats
        val multiHeights = multiFormats.filter { !it.isAudioOnly }.map { it.resolutionHeight }
        assertEquals(listOf(1080, 720, 540), multiHeights)
        assertEquals(6500000L, multiFormats.first().exactBytes)

        // B. TikWM response with NO bit_rate and only 540x960 dimensions must NOT fabricate 1080p or 720p
        val tikWmSingleOnly = JSONObject(
            """
            {
              "id": "73000002",
              "title": "مقطع قصير 540p فقط",
              "duration": 15,
              "width": 540,
              "height": 960,
              "play": "https://cdn.tiktok.test/only_540.mp4",
              "size": 1950000
            }
            """.trimIndent()
        )
        val singleResult = adapter.parseTikWmDataJson(
            originalUrl = "https://www.tiktok.com/@teacher/video/73000002",
            canonicalUrl = "https://www.tiktok.com/@teacher/video/73000002",
            data = tikWmSingleOnly
        ) as ResolvedMediaResult.SingleMedia
        val singleFormats = singleResult.formats
        val singleVideos = singleFormats.filter { !it.isAudioOnly }
        assertEquals(1, singleVideos.size)
        assertEquals(540, singleVideos.first().resolutionHeight)
        assertFalse(singleVideos.any { (it.resolutionHeight ?: 0) >= 720 })
    }

    @Test
    fun dynamicRangeBursts_andGoogleVideo8ParallelRangeBypass_reconstructExactSha256() = runBlocking {
        // 1. Verify dynamic 2MB-4MB burst chunk sizing and full contiguous coverage
        val chunks10Mb = SegmentedDownloadEngine.buildDynamicRangeSegments(10L * 1024L * 1024L, 8, null)
        assertEquals(5, chunks10Mb.size) // 5 x 2 MiB
        assertTrue(SegmentedDownloadEngine.validateChunkLayout(chunks10Mb, 10L * 1024L * 1024L))

        val chunks100Mb = SegmentedDownloadEngine.buildDynamicRangeSegments(100L * 1024L * 1024L, 8, null)
        assertEquals(3L * 1024L * 1024L, chunks100Mb.first().endByte - chunks100Mb.first().startByte + 1L)
        assertTrue(SegmentedDownloadEngine.validateChunkLayout(chunks100Mb, 100L * 1024L * 1024L))

        val chunks250Mb = SegmentedDownloadEngine.buildDynamicRangeSegments(250L * 1024L * 1024L, 8, null)
        assertEquals(4L * 1024L * 1024L, chunks250Mb.first().endByte - chunks250Mb.first().startByte + 1L)
        assertTrue(SegmentedDownloadEngine.validateChunkLayout(chunks250Mb, 250L * 1024L * 1024L))

        // 2. End-to-end 8-worker parallel download against simulated googlevideo.com &range= endpoint returning HTTP 200 slices
        val totalSize = 18 * 1024 * 1024 + 333 // ~18 MiB -> 10 dynamic 2 MiB bursts across 8 parallel workers
        val payload = ByteArray(totalSize) { idx -> ((idx * 29 + 13) and 0xFF).toByte() }
        val expectedSha = sha256(payload)
        val rangeCalls = AtomicInteger(0)
        val observedRanges = ConcurrentHashMap.newKeySet<String>()

        server.createContext("/videoplayback") { ex ->
            val query = ex.requestURI.rawQuery.orEmpty()
            val rangeParam = query.split("&")
                .firstOrNull { it.startsWith("range=") }
                ?.substringAfter("range=")

            if (ex.requestMethod.equals("HEAD", true)) {
                ex.responseHeaders.add("Content-Type", "video/mp4")
                ex.responseHeaders.add("Content-Length", payload.size.toString())
                ex.sendResponseHeaders(200, -1)
                ex.close()
                return@createContext
            }

            assertNotNull("Expected googlevideo &range= parameter on chunk request", rangeParam)
            observedRanges.add(rangeParam!!)
            rangeCalls.incrementAndGet()

            val bounds = rangeParam.split("-")
            val start = bounds[0].toInt()
            val end = bounds[1].toInt().coerceAtMost(payload.size - 1)
            val len = end - start + 1
            ex.responseHeaders.add("Content-Type", "video/mp4")
            ex.responseHeaders.add("Content-Length", len.toString())
            // Real googlevideo CDN responds with HTTP 200 and exact byte slice for &range=start-end
            ex.sendResponseHeaders(200, len.toLong())
            ex.responseBody.use { it.write(payload, start, len) }
        }

        val loopbackDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                listOf(InetAddress.getByName("127.0.0.1"))
        }
        val gvClient = OkHttpClient.Builder()
            .dns(loopbackDns)
            .dispatcher(Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 24 })
            .connectionPool(ConnectionPool(24, 5, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()

        val engine = SegmentedDownloadEngine(InMemoryChunkMetadataStore(), gvClient)
        val partFile = File(tempDir, "gv_burst_test.mp4.part")
        val gvUrl = "http://rr1---sn-test.googlevideo.com:$port/videoplayback?itag=137&clen=${payload.size}&range=0-${payload.size - 1}"

        val outFile = engine.download(
            DownloadJobSpec(
                downloadId = "gv_test_1",
                url = gvUrl,
                targetPartFile = partFile,
                requestedChunks = 8
            )
        )

        assertTrue("Expected >= 9 2MB range burst calls, got ${rangeCalls.get()}", rangeCalls.get() >= 9)
        assertEquals(payload.size.toLong(), outFile.length())
        assertEquals(expectedSha, sha256(outFile.readBytes()))
    }

    @Test
    fun playlistPreExtraction_populatesNextQueuedItemStreamsAndDefaultsConcurrencyTo2() = runBlocking {
        // Default concurrency checks
        assertEquals(2, RafidConfigState().maxConcurrentDownloads)
        assertEquals(2, DownloadSettings().maxConcurrent)
        assertEquals(8, DownloadSettings().chunkCount)

        // Mock YouTube / media watch page returning MP4 video + M4A audio in HTML/JSON for pre-extraction
        val nextWatchPath = "/watch_next_item"
        server.createContext(nextWatchPath) { ex ->
            val html = """
                <html><body>
                <script>
                var ytInitialPlayerResponse = {
                  "playabilityStatus": { "status": "OK" },
                  "videoDetails": { "videoId": "nextVid99", "title": "Next Lecture", "author": "Dr. Ali", "lengthSeconds": "420" },
                  "streamingData": {
                    "adaptiveFormats": [
                      { "itag": 136, "mimeType": "video/mp4; codecs=\"avc1.4d401f\"", "width": 1280, "height": 720, "fps": 30, "contentLength": "24000000", "url": "http://127.0.0.1:$port/stream_720.mp4" },
                      { "itag": 140, "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"", "contentLength": "4200000", "url": "http://127.0.0.1:$port/stream_audio.m4a", "audioTrack": { "id": "ar.0", "displayName": "العربية", "audioIsDefault": true } }
                    ]
                  }
                };
                </script>
                </body></html>
            """.trimIndent().toByteArray()
            ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            ex.sendResponseHeaders(200, html.size.toLong())
            ex.responseBody.use { it.write(html) }
        }
        server.createContext("/stream_720.mp4") { ex ->
            ex.responseHeaders.add("Content-Type", "video/mp4")
            ex.responseHeaders.add("Content-Length", "24000000")
            ex.sendResponseHeaders(200, -1)
            ex.close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, RafidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.rafidDao()
            val watchUrl = "http://127.0.0.1:$port$nextWatchPath"
            val queuedEntity = RafidDownloadEntity(
                id = "pl_next_item_1",
                originalUrl = watchUrl,
                resolvedUrl = watchUrl,
                videoStreamUrl = watchUrl,
                audioStreamUrl = null,
                title = "3. Next Lecture",
                sourceDomain = "127.0.0.1",
                platformName = RafidPlatform.DIRECT.name,
                qualityLabel = "720p",
                containerExt = "mp4",
                mimeType = "video/mp4",
                categoryName = "VIDEO",
                isAudioOnly = false,
                isMuxRequired = true,
                isPlaylistItem = true,
                playlistTitle = "Course Playlist",
                status = RafidDownloadStatus.QUEUED.name,
                totalBytes = -1L
            )
            dao.upsertDownload(queuedEntity)

            val extractor = UniversalMediaExtractor()
            val updated = DownloadQueueManager.preExtractQueuedItem(dao, queuedEntity, extractor)
            assertTrue("Expected queued item pre-extraction to succeed", updated)

            val stored = dao.getDownloadById("pl_next_item_1")
            assertNotNull(stored)
            assertEquals("http://127.0.0.1:$port/stream_720.mp4", stored!!.videoStreamUrl)
            assertEquals(RafidDownloadStatus.QUEUED.name, stored.status)
            assertTrue(stored.totalBytes > 0L)
        } finally {
            db.close()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
