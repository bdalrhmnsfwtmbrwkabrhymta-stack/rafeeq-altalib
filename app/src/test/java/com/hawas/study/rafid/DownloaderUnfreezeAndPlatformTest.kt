package com.hawas.study.rafid

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.download.core.downloader.DownloadJobSpec
import com.example.download.core.downloader.InMemoryChunkMetadataStore
import com.example.download.core.downloader.SegmentedDownloadEngine
import com.example.download.core.model.ResolveRequest
import com.example.download.core.model.ResolvedMediaResult
import com.example.download.core.resolver.FacebookProviderAdapter
import com.example.download.core.resolver.InstagramProviderAdapter
import com.example.download.core.resolver.PinterestProviderAdapter
import com.example.download.core.resolver.ResolverHttpUtils
import com.example.download.core.resolver.YouTubeProviderAdapter
import com.example.download.service.DownloadForegroundService
import com.hawas.study.rafid.data.RafidDatabase
import com.hawas.study.rafid.data.RafidPreferencesRepository
import com.hawas.study.rafid.engine.UrlNormalizer
import com.hawas.study.rafid.models.RafidDownloadEntity
import com.hawas.study.rafid.models.RafidDownloadStatus
import com.hawas.study.rafid.models.RafidMediaCategory
import com.hawas.study.rafid.models.RafidPlatform
import com.hawas.study.rafid.worker.DownloadQueueManager
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloaderUnfreezeAndPlatformTest {

    @Test
    fun urlNormalizerCanonicalizesYouTubeVariantsAndStripsSocialTracking() {
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            UrlNormalizer.normalize("https://www.youtube.com/shorts/dQw4w9WgXcQ?feature=share&si=abc123")
        )
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLstudy123",
            UrlNormalizer.normalize("https://youtu.be/dQw4w9WgXcQ?si=track&list=PLstudy123&t=42")
        )
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            UrlNormalizer.normalize("https://m.youtube.com/watch?v=dQw4w9WgXcQ&pp=ygUIbGVjdHVyZQ%3D%3D")
        )
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            UrlNormalizer.normalize("https://www.youtube.com/embed/dQw4w9WgXcQ?autoplay=1")
        )
        assertEquals(
            "https://www.facebook.com/reel/1234567890",
            UrlNormalizer.normalize("https://www.facebook.com/reel/1234567890?fbclid=abc&mibextid=xyz")
        )
        assertEquals(
            "https://www.instagram.com/reel/C9AbCdEf123/",
            UrlNormalizer.normalize("https://www.instagram.com/reel/C9AbCdEf123/?igsh=MTR0abc&utm_source=ig_web")
        )
        assertEquals(
            "https://www.pinterest.com/pin/987654321/",
            UrlNormalizer.normalize("https://www.pinterest.com/pin/987654321/?invite_code=123&sfo=1")
        )
        assertEquals(
            "https://www.tiktok.com/@teacher/video/739911223344",
            UrlNormalizer.normalize("https://www.tiktok.com/@teacher/video/739911223344?_t=8o&_r=1&is_from_webapp=1")
        )
        // Loopback test URLs must remain untouched
        assertEquals(
            "http://127.0.0.1:8080/video.mp4?token=1",
            UrlNormalizer.normalize("http://127.0.0.1:8080/video.mp4?token=1")
        )
        assertTrue(UrlNormalizer.isShortRedirectUrl("https://vm.tiktok.com/ZM12345/"))
        assertTrue(UrlNormalizer.isShortRedirectUrl("https://pin.it/AbC123"))
        assertTrue(UrlNormalizer.isShortRedirectUrl("https://fb.watch/xyz123/"))
        assertFalse(UrlNormalizer.isShortRedirectUrl("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
    }

    @Test
    fun youTubeAdapterFallsBackAcrossInnertubeClientsAndAttachesStreamHeaders() = runBlocking {
        val seenClientHeaders = CopyOnWriteArrayList<String?>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                if (url.contains("/youtubei/v1/player")) {
                    val clientNameHeader = req.header("X-YouTube-Client-Name")
                    seenClientHeaders.add(clientNameHeader)
                    val body = if (clientNameHeader == "3") {
                        // ANDROID returns blocked/no streams -> must fall through to IOS ("5")
                        """{"playabilityStatus":{"status":"LOGIN_REQUIRED"}}"""
                    } else {
                        """
                        {
                          "videoDetails": {"title": "محاضرة الفيزياء النووية", "author": "أكاديمية العلوم", "lengthSeconds": "320"},
                          "streamingData": {
                            "formats": [
                              {"itag": 22, "url": "https://rr1---sn-test.googlevideo.com/videoplayback?itag=22", "mimeType": "video/mp4; codecs=\"avc1.64001F, mp4a.40.2\"", "height": 720, "contentLength": "2400000"}
                            ]
                          }
                        }
                        """.trimIndent()
                    }
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody(null))
                        .build()
                }
            }
            .build()

        val adapter = YouTubeProviderAdapter(client)
        val res = adapter.resolve(
            "https://www.youtube.com/shorts/dQw4w9WgXcQ?feature=share",
            ResolveRequest(rawUrl = "https://www.youtube.com/shorts/dQw4w9WgXcQ?feature=share")
        )
        assertTrue(res is ResolvedMediaResult.SingleMedia)
        val single = res as ResolvedMediaResult.SingleMedia
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", single.canonicalUrl)
        assertTrue(seenClientHeaders.contains("3"))
        assertTrue(seenClientHeaders.contains("5"))
        val fmt = single.formats.first()
        assertEquals("identity", (single.headers + fmt.headers)["Accept-Encoding"])
        assertEquals("*/*", (single.headers + fmt.headers)["Accept"])
        assertTrue((single.headers + fmt.headers)["Referer"]?.contains("youtube.com") == true)
    }

    @Test
    fun socialAdaptersAndSegmentedEngineInjectChrome128RefererAndIdentityHeaders() = runBlocking {
        val capturedRequestHeaders = CopyOnWriteArrayList<Map<String, String>>()
        val sampleBytes = ByteArray(1024) { (it % 251).toByte() }

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val headerMap = buildMap {
                    req.headers.names().forEach { name ->
                        put(name.lowercase(), req.header(name).orEmpty())
                    }
                }
                capturedRequestHeaders.add(headerMap)
                val u = req.url.toString()
                when {
                    u.contains("facebook.com") && !u.endsWith(".mp4") -> {
                        val html = """
                            <html><head><title>شرح الكيمياء</title></head>
                            <body><script>{"browser_native_hd_url":"https://video.xx.fbcdn.net/v/test_fb.mp4"}</script></body></html>
                        """.trimIndent()
                        Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                            .body(html.toResponseBody("text/html".toMediaType())).build()
                    }
                    u.contains("instagram.com") && !u.endsWith(".mp4") -> {
                        val html = """
                            <html><head><meta property="og:title" content="ملخص الرياضيات"/>
                            <meta property="og:video" content="https://scontent.cdninstagram.com/v/test_ig.mp4"/></head></html>
                        """.trimIndent()
                        Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                            .body(html.toResponseBody("text/html".toMediaType())).build()
                    }
                    u.contains("pinterest.com") -> {
                        val html = """
                            <html><head><title>خريطة ذهنية</title></head>
                            <body><script>{"url":"https://v1.pinimg.com/videos/mc/720p/test_pin.mp4"}</script></body></html>
                        """.trimIndent()
                        Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                            .body(html.toResponseBody("text/html".toMediaType())).build()
                    }
                    u.contains("fbcdn.net") || u.contains("cdninstagram.com") || u.contains("pinimg.com") -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .header("Content-Type", "video/mp4")
                            .header("Content-Length", sampleBytes.size.toString())
                            .body(sampleBytes.toResponseBody("video/mp4".toMediaType()))
                            .build()
                    }
                    else -> {
                        Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(404).message("NF")
                            .body("".toResponseBody(null)).build()
                    }
                }
            }
            .build()

        val fbRes = FacebookProviderAdapter(client).resolve("https://www.facebook.com/reel/123", ResolveRequest("https://www.facebook.com/reel/123"))
        assertTrue(fbRes is ResolvedMediaResult.SingleMedia)
        val fbSingle = fbRes as ResolvedMediaResult.SingleMedia
        assertEquals(ResolverHttpUtils.PIXEL8_ANDROID14_CHROME128_UA, fbSingle.headers["User-Agent"])
        assertEquals("https://www.facebook.com/", fbSingle.headers["Referer"])
        assertEquals("identity", fbSingle.headers["Accept-Encoding"])

        val igRes = InstagramProviderAdapter(client).resolve("https://www.instagram.com/reel/C9123/", ResolveRequest("https://www.instagram.com/reel/C9123/"))
        assertTrue(igRes is ResolvedMediaResult.SingleMedia)
        val igSingle = igRes as ResolvedMediaResult.SingleMedia
        assertEquals("https://www.instagram.com/", igSingle.headers["Referer"])

        val pinRes = PinterestProviderAdapter(client).resolve("https://www.pinterest.com/pin/555/", ResolveRequest("https://www.pinterest.com/pin/555/"))
        assertTrue(pinRes is ResolvedMediaResult.SingleMedia)
        val pinSingle = pinRes as ResolvedMediaResult.SingleMedia
        assertEquals("https://www.pinterest.com/", pinSingle.headers["Referer"])

        // Download fbcdn stream via SegmentedDownloadEngine and verify exact outbound headers
        capturedRequestHeaders.clear()
        val outPart = File.createTempFile("fb_cdn_test", ".part")
        try {
            val engine = SegmentedDownloadEngine(InMemoryChunkMetadataStore(), client)
            val downloaded = engine.download(
                DownloadJobSpec(
                    downloadId = "fb_cdn_1",
                    url = fbSingle.formats.first().streamUrl,
                    headers = emptyMap(), // rely on automatic defaultHeadersForUrl injection
                    targetPartFile = outPart,
                    expectedMimeType = "video/mp4"
                )
            )
            assertEquals(sampleBytes.size.toLong(), downloaded.length())
            assertTrue(capturedRequestHeaders.any { hdrs ->
                hdrs["user-agent"] == ResolverHttpUtils.PIXEL8_ANDROID14_CHROME128_UA &&
                    hdrs["referer"] == "https://www.facebook.com/" &&
                    hdrs["accept"] == "*/*" &&
                    hdrs["accept-encoding"] == "identity"
            })
        } finally {
            outPart.delete()
        }
    }

    @Test
    fun startQueueIntentCarriesActionAndTaskId_andServiceQueueProcessesAllQueuedTasksToCompletion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (!WorkManager.isInitialized()) {
            WorkManager.initialize(context, Configuration.Builder().build())
        }

        val intent = DownloadForegroundService.buildStartQueueIntent(context, "task_abc_99")
        assertEquals(DownloadForegroundService.ACTION_START_QUEUE, intent.action)
        assertEquals("task_abc_99", intent.getStringExtra(DownloadForegroundService.EXTRA_TASK_ID))

        val payload = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n".repeat(40).toByteArray()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/lecture1.pdf") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/pdf")
            exchange.responseHeaders.add("Accept-Ranges", "bytes")
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.createContext("/lecture2.pdf") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/pdf")
            exchange.responseHeaders.add("Accept-Ranges", "bytes")
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()

        val dao = RafidDatabase.getInstance(context).rafidDao()
        val prefs = RafidPreferencesRepository(context)
        val prevConcurrent = prefs.getSnapshot().maxConcurrentDownloads
        prefs.setMaxConcurrentDownloads(1)
        prefs.setQueuePaused(false)
        prefs.setWifiOnly(false)
        prefs.setScheduledStart(false)

        val port = server.address.port
        val id1 = "svc_q1_${System.currentTimeMillis()}"
        val id2 = "svc_q2_${System.currentTimeMillis()}"
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        try {
            dao.upsertDownloads(
                listOf(
                    RafidDownloadEntity(
                        id = id1,
                        originalUrl = "http://127.0.0.1:$port/lecture1.pdf",
                        resolvedUrl = "http://127.0.0.1:$port/lecture1.pdf",
                        videoStreamUrl = "http://127.0.0.1:$port/lecture1.pdf",
                        title = "ملخص الدرس الأول",
                        sourceDomain = "127.0.0.1",
                        platformName = RafidPlatform.DIRECT.name,
                        qualityLabel = "PDF",
                        containerExt = "pdf",
                        mimeType = "application/pdf",
                        categoryName = RafidMediaCategory.DOCUMENT.name,
                        isAudioOnly = false,
                        isMuxRequired = false,
                        saveToStudyFolder = true,
                        status = RafidDownloadStatus.QUEUED.name,
                        totalBytes = payload.size.toLong(),
                        createdAtMillis = 1000L
                    ),
                    RafidDownloadEntity(
                        id = id2,
                        originalUrl = "http://127.0.0.1:$port/lecture2.pdf",
                        resolvedUrl = "http://127.0.0.1:$port/lecture2.pdf",
                        videoStreamUrl = "http://127.0.0.1:$port/lecture2.pdf",
                        title = "ملخص الدرس الثاني",
                        sourceDomain = "127.0.0.1",
                        platformName = RafidPlatform.DIRECT.name,
                        qualityLabel = "PDF",
                        containerExt = "pdf",
                        mimeType = "application/pdf",
                        categoryName = RafidMediaCategory.DOCUMENT.name,
                        isAudioOnly = false,
                        isMuxRequired = false,
                        saveToStudyFolder = true,
                        status = RafidDownloadStatus.QUEUED.name,
                        totalBytes = payload.size.toLong(),
                        createdAtMillis = 2000L
                    )
                )
            )

            DownloadQueueManager.processQueueInService(context, serviceScope, id1)

            val deadline = System.currentTimeMillis() + 10_000L
            while (System.currentTimeMillis() < deadline) {
                val r1 = dao.getDownloadById(id1)
                val r2 = dao.getDownloadById(id2)
                if (r1?.status == RafidDownloadStatus.COMPLETED.name &&
                    r2?.status == RafidDownloadStatus.COMPLETED.name
                ) {
                    break
                }
                delay(100L)
            }

            val final1 = dao.getDownloadById(id1)
            val final2 = dao.getDownloadById(id2)
            assertNotNull(final1)
            assertNotNull(final2)
            assertEquals(RafidDownloadStatus.COMPLETED.name, final1?.status)
            assertEquals(RafidDownloadStatus.COMPLETED.name, final2?.status)
            assertEquals(payload.size.toLong(), final1?.downloadedBytes)
            assertEquals(payload.size.toLong(), final2?.downloadedBytes)
            assertTrue(final1?.displayPath?.startsWith("Download/HawasElDirasa/Study/") == true)
            assertTrue(final2?.displayPath?.startsWith("Download/HawasElDirasa/Study/") == true)
        } finally {
            serviceScope.cancel()
            prefs.setMaxConcurrentDownloads(prevConcurrent)
            WorkManager.getInstance(context).cancelAllWork()
            dao.deleteDownloadById(id1)
            dao.deleteDownloadById(id2)
            server.stop(0)
        }
    }
}
