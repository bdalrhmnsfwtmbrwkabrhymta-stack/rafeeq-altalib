package com.example.download.core

import com.example.download.core.downloader.DownloadJobSpec
import com.example.download.core.downloader.InMemoryChunkMetadataStore
import com.example.download.core.downloader.SegmentedDownloadEngine
import com.example.download.core.model.DownloadEngineException
import com.example.download.core.model.DownloadError
import com.example.download.core.model.EngineDownloadState
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class SegmentedDownloadEngineIntegrationTest {

    private lateinit var tempDir: File
    private lateinit var server: HttpServer
    private var port: Int = 0

    @Before
    fun setup() {
        tempDir = Files.createTempDirectory("seg_engine_test").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newFixedThreadPool(8)
        server.start()
        port = server.address.port
    }

    @After
    fun teardown() {
        runCatching { server.stop(0) }
        tempDir.deleteRecursively()
    }

    @Test
    fun fullRangeDownload_reconstructsExactSha256Bytes() = runBlocking {
        val payload = deterministicBytes(3 * 1024 * 1024 + 137) // >3 MiB
        val etag = "\"etag-full-v1\""
        val lastMod = "Wed, 18 Nov 2026 10:00:00 GMT"

        registerBinaryEndpoint("/video.mp4", payload, supportsRange = true, etag = etag, lastModified = lastMod)

        val store = InMemoryChunkMetadataStore()
        val engine = SegmentedDownloadEngine(store)
        val partFile = File(tempDir, "full_test.mp4.part")

        val out = engine.download(
            DownloadJobSpec(
                downloadId = "dl_full",
                url = "http://127.0.0.1:$port/video.mp4",
                targetPartFile = partFile,
                requestedChunks = 4
            )
        )

        assertEquals(payload.size.toLong(), out.length())
        assertEquals(sha256(payload), sha256(out.readBytes()))
        assertTrue(store.states.any { it.second == EngineDownloadState.DOWNLOADING })
        assertTrue(store.states.any { it.second == EngineDownloadState.VERIFYING })
    }

    @Test
    fun nonRangeServer_fallsBackToSingleStreamWithExactBytes() = runBlocking {
        val payload = deterministicBytes(600 * 1024 + 55)
        registerBinaryEndpoint("/direct.bin", payload, supportsRange = false, etag = "\"no-range\"", lastModified = null)

        val store = InMemoryChunkMetadataStore()
        val engine = SegmentedDownloadEngine(store)
        val partFile = File(tempDir, "single_stream.bin.part")

        val out = engine.download(
            DownloadJobSpec(
                downloadId = "dl_single",
                url = "http://127.0.0.1:$port/direct.bin",
                expectedMimeType = "application/octet-stream",
                targetPartFile = partFile,
                requestedChunks = 8
            )
        )

        assertEquals(payload.size.toLong(), out.length())
        assertArrayEquals(payload, out.readBytes())
    }

    @Test
    fun pauseAndResume_andSimulatedRestart_resumeWithoutCorruption() = runBlocking {
        val payload = deterministicBytes(4 * 1024 * 1024 + 911)
        val etag = "\"resume-v1\""
        val pauseFlag = AtomicBoolean(false)
        val requestsServed = AtomicInteger(0)

        server.createContext("/resume.mp4") { exchange ->
            if (exchange.requestMethod.equals("HEAD", true)) {
                exchange.responseHeaders.add("Content-Type", "video/mp4")
                exchange.responseHeaders.add("Accept-Ranges", "bytes")
                exchange.responseHeaders.add("Content-Length", payload.size.toString())
                exchange.responseHeaders.add("ETag", etag)
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
                return@createContext
            }
            val rangeHdr = exchange.requestHeaders.getFirst("Range")
            if (rangeHdr == null) {
                exchange.responseHeaders.add("Content-Type", "video/mp4")
                exchange.responseHeaders.add("ETag", etag)
                exchange.sendResponseHeaders(200, payload.size.toLong())
                exchange.responseBody.use { it.write(payload) }
                return@createContext
            }
            val count = requestsServed.incrementAndGet()
            val bounds = rangeHdr.removePrefix("bytes=").split("-")
            val start = bounds[0].toInt()
            val end = (bounds.getOrNull(1)?.toIntOrNull() ?: (payload.size - 1)).coerceAtMost(payload.size - 1)
            val sliceLen = end - start + 1
            exchange.responseHeaders.add("Content-Type", "video/mp4")
            exchange.responseHeaders.add("Accept-Ranges", "bytes")
            exchange.responseHeaders.add("ETag", etag)
            exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${payload.size}")
            exchange.sendResponseHeaders(206, sliceLen.toLong())
            exchange.responseBody.use { out ->
                // Trigger simulated pause after chunk data begins streaming
                val firstHalf = sliceLen / 3
                out.write(payload, start, firstHalf)
                out.flush()
                if (count == 2) {
                    pauseFlag.set(true)
                }
                Thread.sleep(30L)
                out.write(payload, start + firstHalf, sliceLen - firstHalf)
            }
        }

        val store = InMemoryChunkMetadataStore()
        val engine = SegmentedDownloadEngine(store)
        val partFile = File(tempDir, "resume.mp4.part")

        var pausedCaught = false
        try {
            engine.download(
                DownloadJobSpec(
                    downloadId = "dl_resume",
                    url = "http://127.0.0.1:$port/resume.mp4",
                    targetPartFile = partFile,
                    requestedChunks = 3,
                    savedEtag = etag,
                    isPausedOrCanceled = { pauseFlag.get() }
                )
            )
        } catch (_: kotlinx.coroutines.CancellationException) {
            pausedCaught = true
        }
        assertTrue("Expected first download attempt to pause mid-transfer", pausedCaught)
        val savedChunksAfterPause = store.getChunks("dl_resume")
        assertTrue(savedChunksAfterPause.isNotEmpty())

        // Simulate fresh engine instance after app restart reading persisted ChunkMetadataStore
        val restartedEngine = SegmentedDownloadEngine(store)
        val finalFile = restartedEngine.download(
            DownloadJobSpec(
                downloadId = "dl_resume",
                url = "http://127.0.0.1:$port/resume.mp4",
                targetPartFile = partFile,
                requestedChunks = 3,
                savedEtag = etag,
                savedTotalBytes = payload.size.toLong(),
                isPausedOrCanceled = { false }
            )
        )

        assertEquals(sha256(payload), sha256(finalFile.readBytes()))
    }

    @Test
    fun transient503Failure_recoversViaRetry() = runBlocking {
        val payload = deterministicBytes(2 * 1024 * 1024 + 500)
        val getAttempts = AtomicInteger(0)

        server.createContext("/flaky.mp4") { exchange ->
            if (exchange.requestMethod.equals("HEAD", true)) {
                exchange.responseHeaders.add("Content-Type", "video/mp4")
                exchange.responseHeaders.add("Accept-Ranges", "bytes")
                exchange.responseHeaders.add("Content-Length", payload.size.toString())
                exchange.responseHeaders.add("ETag", "\"flaky-v1\"")
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
                return@createContext
            }
            if (getAttempts.incrementAndGet() == 1) {
                exchange.sendResponseHeaders(206, 1024L * 1024L)
                // Simulate sudden socket drop mid-chunk so IOException triggers retry
                exchange.responseBody.use { out ->
                    out.write(payload, 0, 1024)
                    out.flush()
                }
                return@createContext
            }
            serveRangeBytes(exchange, payload, "\"flaky-v1\"", null)
        }

        val store = InMemoryChunkMetadataStore()
        val engine = SegmentedDownloadEngine(store)
        val out = engine.download(
            DownloadJobSpec(
                downloadId = "dl_flaky",
                url = "http://127.0.0.1:$port/flaky.mp4",
                targetPartFile = File(tempDir, "flaky.mp4.part"),
                requestedChunks = 2
            )
        )

        assertEquals(sha256(payload), sha256(out.readBytes()))
        assertTrue(store.states.any { it.second == EngineDownloadState.RETRYING })
    }

    @Test
    fun etagChangedBeforeResume_failsWithFileChangedOnServer() = runBlocking {
        val v1 = deterministicBytes(3 * 1024 * 1024)
        val v2 = deterministicBytes(3 * 1024 * 1024, seed = 99)
        val activePayload = AtomicReference(v1)
        val activeEtag = AtomicReference("\"v1-etag\"")
        val pauseFlag = AtomicBoolean(false)
        val calls = AtomicInteger(0)

        server.createContext("/mutating.mp4") { exchange ->
            if (exchange.requestMethod.equals("HEAD", true)) {
                exchange.responseHeaders.add("Content-Type", "video/mp4")
                exchange.responseHeaders.add("Accept-Ranges", "bytes")
                exchange.responseHeaders.add("Content-Length", activePayload.get().size.toString())
                exchange.responseHeaders.add("ETag", activeEtag.get())
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
                return@createContext
            }
            if (calls.incrementAndGet() == 1) pauseFlag.set(true)
            serveRangeBytes(exchange, activePayload.get(), activeEtag.get(), null)
        }

        val store = InMemoryChunkMetadataStore()
        val engine = SegmentedDownloadEngine(store)
        val partFile = File(tempDir, "mutating.mp4.part")

        try {
            engine.download(
                DownloadJobSpec(
                    downloadId = "dl_mut",
                    url = "http://127.0.0.1:$port/mutating.mp4",
                    targetPartFile = partFile,
                    requestedChunks = 2,
                    savedEtag = "\"v1-etag\"",
                    isPausedOrCanceled = { pauseFlag.get() }
                )
            )
        } catch (_: kotlinx.coroutines.CancellationException) {
        }

        // Server replaces file with new ETag
        activePayload.set(v2)
        activeEtag.set("\"v2-etag\"")

        try {
            engine.download(
                DownloadJobSpec(
                    downloadId = "dl_mut",
                    url = "http://127.0.0.1:$port/mutating.mp4",
                    targetPartFile = partFile,
                    requestedChunks = 2,
                    savedEtag = "\"v1-etag\"",
                    savedTotalBytes = v1.size.toLong(),
                    failIfFileChangedOnServer = true
                )
            )
            fail("Expected FileChangedOnServer exception")
        } catch (ex: DownloadEngineException) {
            assertTrue(ex.error is DownloadError.FileChangedOnServer)
        }
    }

    @Test
    fun serverIgnoringRangeWithHttp200_failsSafelyInsteadOfCorruptingFile() = runBlocking {
        val payload = deterministicBytes(2 * 1024 * 1024 + 512)
        server.createContext("/broken_range.mp4") { exchange ->
            if (exchange.requestMethod.equals("HEAD", true)) {
                exchange.responseHeaders.add("Content-Type", "video/mp4")
                exchange.responseHeaders.add("Accept-Ranges", "bytes")
                exchange.responseHeaders.add("Content-Length", payload.size.toString())
                exchange.responseHeaders.add("ETag", "\"br-1\"")
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
                return@createContext
            }
            // Server ignores Range header and illegally responds with 200 OK full payload
            exchange.responseHeaders.add("Content-Type", "video/mp4")
            exchange.responseHeaders.add("ETag", "\"br-1\"")
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }

        val engine = SegmentedDownloadEngine(InMemoryChunkMetadataStore())
        try {
            engine.download(
                DownloadJobSpec(
                    downloadId = "dl_bad200",
                    url = "http://127.0.0.1:$port/broken_range.mp4",
                    targetPartFile = File(tempDir, "bad200.mp4.part"),
                    requestedChunks = 2
                )
            )
            fail("Expected InvalidRangeResponse instead of writing byte 0 at chunk offset")
        } catch (ex: DownloadEngineException) {
            assertTrue(ex.error is DownloadError.InvalidRangeResponse)
        }
    }

    @Test
    fun htmlPayload_isRejectedDuringProbeAndVerification() = runBlocking {
        val htmlBytes = "<!DOCTYPE html><html><body>Login required</body></html>".toByteArray()
        server.createContext("/page.mp4") { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.responseHeaders.add("Content-Length", htmlBytes.size.toString())
            exchange.sendResponseHeaders(200, htmlBytes.size.toLong())
            if (!exchange.requestMethod.equals("HEAD", true)) {
                exchange.responseBody.use { it.write(htmlBytes) }
            }
            exchange.close()
        }

        val engine = SegmentedDownloadEngine(InMemoryChunkMetadataStore())
        try {
            engine.download(
                DownloadJobSpec(
                    downloadId = "dl_html",
                    url = "http://127.0.0.1:$port/page.mp4",
                    expectedMimeType = "video/mp4",
                    targetPartFile = File(tempDir, "fake.mp4.part"),
                    requestedChunks = 2
                )
            )
            fail("Expected UnsupportedMedia for HTML response")
        } catch (ex: DownloadEngineException) {
            assertTrue(ex.error is DownloadError.UnsupportedMedia)
        }
    }

    @Test
    fun concurrentDownloads_andSpeedThrottle_produceExactIndependentOutputs() = runBlocking {
        val dataA = deterministicBytes(2 * 1024 * 1024 + 100, seed = 11)
        val dataB = deterministicBytes(2 * 1024 * 1024 + 200, seed = 29)
        registerBinaryEndpoint("/a.mp4", dataA, true, "\"a\"", null)
        registerBinaryEndpoint("/b.mp4", dataB, true, "\"b\"", null)

        val engine = SegmentedDownloadEngine(InMemoryChunkMetadataStore())
        coroutineScope {
            val jobA = async {
                engine.download(
                    DownloadJobSpec(
                        downloadId = "dl_a",
                        url = "http://127.0.0.1:$port/a.mp4",
                        targetPartFile = File(tempDir, "a.mp4.part"),
                        requestedChunks = 2,
                        speedLimitKBps = 8192
                    )
                )
            }
            val jobB = async {
                engine.download(
                    DownloadJobSpec(
                        downloadId = "dl_b",
                        url = "http://127.0.0.1:$port/b.mp4",
                        targetPartFile = File(tempDir, "b.mp4.part"),
                        requestedChunks = 2,
                        speedLimitKBps = 8192
                    )
                )
            }
            val (outA, outB) = awaitAll(jobA, jobB)
            assertEquals(sha256(dataA), sha256(outA.readBytes()))
            assertEquals(sha256(dataB), sha256(outB.readBytes()))
        }
    }

    private fun registerBinaryEndpoint(
        path: String,
        payload: ByteArray,
        supportsRange: Boolean,
        etag: String?,
        lastModified: String?
    ) {
        server.createContext(path) { exchange ->
            if (exchange.requestMethod.equals("HEAD", true)) {
                exchange.responseHeaders.add("Content-Type", "video/mp4")
                if (supportsRange) exchange.responseHeaders.add("Accept-Ranges", "bytes")
                exchange.responseHeaders.add("Content-Length", payload.size.toString())
                etag?.let { exchange.responseHeaders.add("ETag", it) }
                lastModified?.let { exchange.responseHeaders.add("Last-Modified", it) }
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
                return@createContext
            }
            if (!supportsRange) {
                exchange.responseHeaders.add("Content-Type", "application/octet-stream")
                exchange.responseHeaders.add("Content-Length", payload.size.toString())
                etag?.let { exchange.responseHeaders.add("ETag", it) }
                exchange.sendResponseHeaders(200, payload.size.toLong())
                exchange.responseBody.use { it.write(payload) }
                return@createContext
            }
            serveRangeBytes(exchange, payload, etag, lastModified)
        }
    }

    private fun serveRangeBytes(
        exchange: com.sun.net.httpserver.HttpExchange,
        payload: ByteArray,
        etag: String?,
        lastModified: String?
    ) {
        val rangeHdr = exchange.requestHeaders.getFirst("Range")
        if (rangeHdr.isNullOrBlank()) {
            exchange.responseHeaders.add("Content-Type", "video/mp4")
            etag?.let { exchange.responseHeaders.add("ETag", it) }
            lastModified?.let { exchange.responseHeaders.add("Last-Modified", it) }
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
            return
        }
        val parts = rangeHdr.removePrefix("bytes=").split("-")
        val start = parts[0].toInt()
        val end = (parts.getOrNull(1)?.toIntOrNull() ?: (payload.size - 1)).coerceAtMost(payload.size - 1)
        val len = end - start + 1
        exchange.responseHeaders.add("Content-Type", "video/mp4")
        exchange.responseHeaders.add("Accept-Ranges", "bytes")
        etag?.let { exchange.responseHeaders.add("ETag", it) }
        lastModified?.let { exchange.responseHeaders.add("Last-Modified", it) }
        exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${payload.size}")
        exchange.sendResponseHeaders(206, len.toLong())
        exchange.responseBody.use { it.write(payload, start, len) }
    }

    private fun deterministicBytes(size: Int, seed: Int = 7): ByteArray {
        return ByteArray(size) { idx -> ((idx * 31 + seed) and 0xFF).toByte() }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
