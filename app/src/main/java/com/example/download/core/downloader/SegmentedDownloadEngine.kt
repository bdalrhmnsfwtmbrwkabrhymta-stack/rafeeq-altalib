package com.example.download.core.downloader

import com.example.download.core.model.DownloadEngineException
import com.example.download.core.model.DownloadError
import com.example.download.core.model.DownloadErrorMapper
import com.example.download.core.model.EngineDownloadState
import com.example.download.core.model.HttpProbeInfo
import com.example.download.data.LiveProgressSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

data class SegmentChunk(
    val index: Int,
    val startByte: Long,
    val endByte: Long,
    val downloadedBytes: Long = 0L,
    val completed: Boolean = false,
    val etag: String? = null
)

interface ChunkMetadataStore {
    suspend fun getChunks(downloadId: String): List<SegmentChunk>
    suspend fun saveChunks(downloadId: String, chunks: List<SegmentChunk>)
    suspend fun clearChunks(downloadId: String)
    suspend fun updateProgress(downloadId: String, downloadedBytes: Long, totalBytes: Long, activeThreads: Int)
    suspend fun updateServerMetadata(downloadId: String, finalUrl: String, etag: String?, lastModified: String?)
    suspend fun onStateChanged(downloadId: String, state: EngineDownloadState, error: DownloadError? = null)
}

class InMemoryChunkMetadataStore : ChunkMetadataStore {
    private val chunkMap = mutableMapOf<String, List<SegmentChunk>>()
    val states = mutableListOf<Pair<String, EngineDownloadState>>()
    val progressEvents = mutableListOf<Triple<String, Long, Long>>()
    val metadataMap = mutableMapOf<String, Triple<String, String?, String?>>()

    override suspend fun getChunks(downloadId: String): List<SegmentChunk> =
        chunkMap[downloadId].orEmpty()

    override suspend fun saveChunks(downloadId: String, chunks: List<SegmentChunk>) {
        chunkMap[downloadId] = chunks.toList()
    }

    override suspend fun clearChunks(downloadId: String) {
        chunkMap.remove(downloadId)
    }

    override suspend fun updateProgress(downloadId: String, downloadedBytes: Long, totalBytes: Long, activeThreads: Int) {
        progressEvents.add(Triple(downloadId, downloadedBytes, totalBytes))
    }

    override suspend fun updateServerMetadata(downloadId: String, finalUrl: String, etag: String?, lastModified: String?) {
        metadataMap[downloadId] = Triple(finalUrl, etag, lastModified)
    }

    override suspend fun onStateChanged(downloadId: String, state: EngineDownloadState, error: DownloadError?) {
        states.add(downloadId to state)
    }
}

data class DownloadJobSpec(
    val downloadId: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val targetPartFile: File,
    val expectedMimeType: String = "video/mp4",
    val requestedChunks: Int = 8,
    val speedLimitKBps: Int = 0,
    val savedEtag: String? = null,
    val savedLastModified: String? = null,
    val savedTotalBytes: Long = -1L,
    val failIfFileChangedOnServer: Boolean = true,
    val isPausedOrCanceled: () -> Boolean = { false },
    val onRefreshExpiredUrl: suspend () -> Pair<String, Map<String, String>>? = { null },
    val onProgress: (downloadedBytes: Long, totalBytes: Long, snapshot: LiveProgressSnapshot) -> Unit = { _, _, _ -> }
)

class SegmentedDownloadEngine(
    private val chunkStore: ChunkMetadataStore,
    private val httpClient: OkHttpClient = defaultHttpClient()
) {

    private class MutableChunk(
        val index: Int,
        val startByte: Long,
        @Volatile var endByte: Long,
        @Volatile var downloadedBytes: Long,
        @Volatile var completed: Boolean
    ) {
        fun currentOffset(): Long = startByte + downloadedBytes
        fun remainingBytes(): Long = if (completed || endByte < 0L) 0L else max(0L, endByte - currentOffset() + 1L)
    }

    private class SharedSpeedBucket(private val limitKBps: Int) {
        private val lock = Any()
        private var windowStartNs = System.nanoTime()
        private var bytesInWindow = 0L

        fun consume(bytes: Int) {
            if (limitKBps <= 0) return
            val maxBytesPerSec = limitKBps.toLong() * 1024L
            var sleepMs = 0L
            synchronized(lock) {
                val now = System.nanoTime()
                val elapsedNs = now - windowStartNs
                if (elapsedNs >= 1_000_000_000L) {
                    windowStartNs = now
                    bytesInWindow = 0L
                }
                bytesInWindow += bytes
                if (bytesInWindow > maxBytesPerSec) {
                    val remainingNs = 1_000_000_000L - (System.nanoTime() - windowStartNs)
                    if (remainingNs > 0L) {
                        sleepMs = (remainingNs / 1_000_000L).coerceIn(5L, 250L)
                    }
                }
            }
            if (sleepMs > 0L) {
                Thread.sleep(sleepMs)
            }
        }
    }

    suspend fun download(spec: DownloadJobSpec): File = withContext(Dispatchers.IO) {
        var activeUrl = spec.url
        var activeHeaders = spec.headers
        val partFile = spec.targetPartFile.apply { parentFile?.mkdirs() }

        var probe = probeServerWithRefresh(activeUrl, activeHeaders, spec.onRefreshExpiredUrl) { freshUrl, freshHeaders ->
            activeUrl = freshUrl
            activeHeaders = freshHeaders
        }

        if (probe.isHtmlPage) {
            throw DownloadEngineException(
                DownloadError.UnsupportedMedia(
                    mediaType = probe.contentType,
                    technicalDetail = "Probe resolved to HTML document (${probe.finalUrl}) instead of downloadable media"
                )
            )
        }
        if (probe.statusCode >= 400) {
            throw DownloadEngineException(DownloadErrorMapper.fromHttpStatus(probe.statusCode, probe.finalUrl))
        }

        val totalBytes = probe.contentLength
        val existingChunks = chunkStore.getChunks(spec.downloadId)
        val hasResumableProgress = existingChunks.isNotEmpty() &&
            partFile.exists() &&
            existingChunks.any { it.downloadedBytes > 0L }

        if (hasResumableProgress) {
            val etagChanged = !spec.savedEtag.isNullOrBlank() &&
                !probe.etag.isNullOrBlank() &&
                spec.savedEtag != probe.etag
            val lastModChanged = !spec.savedLastModified.isNullOrBlank() &&
                !probe.lastModified.isNullOrBlank() &&
                spec.savedLastModified != probe.lastModified
            val sizeChanged = spec.savedTotalBytes > 0L &&
                totalBytes > 0L &&
                spec.savedTotalBytes != totalBytes

            if (etagChanged || lastModChanged || sizeChanged) {
                if (spec.failIfFileChangedOnServer) {
                    chunkStore.clearChunks(spec.downloadId)
                    runCatching { partFile.delete() }
                    throw DownloadEngineException(
                        DownloadError.FileChangedOnServer(
                            "Remote resource changed before resume " +
                                "(etag ${spec.savedEtag} -> ${probe.etag}, " +
                                "lastMod ${spec.savedLastModified} -> ${probe.lastModified}, " +
                                "size ${spec.savedTotalBytes} -> $totalBytes)"
                        )
                    )
                }
            }
        }

        if (!probe.supportsRange || totalBytes <= 0L) {
            chunkStore.onStateChanged(spec.downloadId, EngineDownloadState.DOWNLOADING)
            chunkStore.updateServerMetadata(spec.downloadId, probe.finalUrl, probe.etag, probe.lastModified)
            val downloadedFile = downloadSingleStream(
                spec = spec,
                initialUrl = activeUrl,
                initialHeaders = activeHeaders,
                partFile = partFile,
                knownTotalBytes = totalBytes
            )
            if (spec.isPausedOrCanceled()) {
                throw CancellationException("Download paused or cancelled")
            }
            verifyCompletedArtifact(downloadedFile, totalBytes, spec.expectedMimeType, emptyList())
            return@withContext downloadedFile
        }

        val fileLengthMatches = totalBytes > 0L && partFile.exists() && partFile.length() == totalBytes
        val canResumeChunks = existingChunks.isNotEmpty() &&
            fileLengthMatches &&
            validateChunkLayout(existingChunks, totalBytes)

        val chunks: MutableList<MutableChunk> = if (canResumeChunks) {
            existingChunks.map {
                MutableChunk(
                    index = it.index,
                    startByte = it.startByte,
                    endByte = it.endByte,
                    downloadedBytes = it.downloadedBytes,
                    completed = it.completed
                )
            }.toMutableList()
        } else {
            if (partFile.exists()) partFile.delete()
            val workerCount = computeAdaptiveChunkCount(totalBytes, supportsRange = true, requestedChunks = spec.requestedChunks)
            RandomAccessFile(partFile, "rw").use { raf ->
                raf.setLength(totalBytes)
            }
            val initialChunks = buildSegmentChunks(totalBytes, workerCount, probe.etag)
            chunkStore.saveChunks(spec.downloadId, initialChunks)
            chunkStore.updateServerMetadata(spec.downloadId, probe.finalUrl, probe.etag, probe.lastModified)
            initialChunks.map {
                MutableChunk(it.index, it.startByte, it.endByte, 0L, false)
            }.toMutableList()
        }

        chunkStore.onStateChanged(spec.downloadId, EngineDownloadState.DOWNLOADING)

        val queueMutex = Mutex()
        val pendingQueue = ArrayDeque(chunks.filter { !it.completed })
        val nextStealIndex = AtomicInteger((chunks.maxOfOrNull { it.index } ?: 0) + 1)
        val totalDownloadedAtomic = AtomicLong(chunks.sumOf { it.downloadedBytes })
        val activeWorkersAtomic = AtomicInteger(0)
        val speedBucket = SharedSpeedBucket(spec.speedLimitKBps)

        val speedSamples = ArrayDeque<Pair<Long, Long>>()
        var lastUiEmitMs = 0L
        var lastDbFlushMs = System.currentTimeMillis()
        // Conservative concurrency cap scaled to actual pending chunk count
        val workerConcurrency = min(chunks.size, spec.requestedChunks.coerceIn(1, 16)).coerceAtLeast(1)

        val urlRefreshMutex = Mutex()
        var lastRefreshMs = 0L

        suspend fun tryRefreshUrlLocked(): Boolean {
            return urlRefreshMutex.withLock {
                val now = System.currentTimeMillis()
                if (now - lastRefreshMs < 4000L) return@withLock true
                val refreshed = spec.onRefreshExpiredUrl() ?: return@withLock false
                activeUrl = refreshed.first
                activeHeaders = refreshed.second
                lastRefreshMs = System.currentTimeMillis()
                chunkStore.updateServerMetadata(spec.downloadId, activeUrl, probe.etag, probe.lastModified)
                true
            }
        }

        suspend fun flushChunksToStore() {
            val snapshot = queueMutex.withLock {
                chunks.map {
                    SegmentChunk(
                        index = it.index,
                        startByte = it.startByte,
                        endByte = it.endByte,
                        downloadedBytes = it.downloadedBytes,
                        completed = it.completed,
                        etag = probe.etag
                    )
                }
            }
            chunkStore.saveChunks(spec.downloadId, snapshot)
            chunkStore.updateProgress(
                downloadId = spec.downloadId,
                downloadedBytes = totalDownloadedAtomic.get().coerceAtMost(totalBytes),
                totalBytes = totalBytes,
                activeThreads = activeWorkersAtomic.get()
            )
        }

        coroutineScope {
            val monitorJob = launch {
                while (isActive && !spec.isPausedOrCanceled()) {
                    delay(450L)
                    val now = System.currentTimeMillis()
                    val currentDone = totalDownloadedAtomic.get().coerceAtMost(totalBytes)
                    speedSamples.addLast(now to currentDone)
                    while (speedSamples.size > 1 && now - speedSamples.first().first > 2000L) {
                        speedSamples.removeFirst()
                    }
                    val firstSample = speedSamples.firstOrNull()
                    val elapsedMs = if (firstSample != null) max(1L, now - firstSample.first) else 1000L
                    val deltaBytes = if (firstSample != null) max(0L, currentDone - firstSample.second) else 0L
                    val speedBps = (deltaBytes * 1000L) / elapsedMs
                    val remaining = max(0L, totalBytes - currentDone)
                    val etaSec = if (speedBps > 0L) remaining / speedBps else -1L

                    if (now - lastUiEmitMs >= 450L) {
                        lastUiEmitMs = now
                        spec.onProgress(
                            currentDone,
                            totalBytes,
                            LiveProgressSnapshot(
                                speedBytesPerSec = speedBps,
                                etaSeconds = etaSec,
                                activeThreads = activeWorkersAtomic.get()
                            )
                        )
                    }
                    if (now - lastDbFlushMs >= 900L) {
                        lastDbFlushMs = now
                        flushChunksToStore()
                    }
                }
            }

            try {
                val workerJobs = List(workerConcurrency) {
                    launch(Dispatchers.IO) {
                        while (isActive && !spec.isPausedOrCanceled()) {
                            val chunk: MutableChunk = queueMutex.withLock {
                                if (pendingQueue.isNotEmpty()) {
                                    pendingQueue.removeFirst()
                                } else {
                                    val donor = chunks
                                        .filter { !it.completed && it.remainingBytes() > 2L * 1024L * 1024L }
                                        .maxByOrNull { it.remainingBytes() }
                                    if (donor != null) {
                                        val rem = donor.remainingBytes()
                                        val splitPoint = donor.currentOffset() + (rem / 2L)
                                        val stolenEnd = donor.endByte
                                        if (splitPoint < stolenEnd) {
                                            donor.endByte = splitPoint
                                            val stolen = MutableChunk(
                                                index = nextStealIndex.getAndIncrement(),
                                                startByte = splitPoint + 1L,
                                                endByte = stolenEnd,
                                                downloadedBytes = 0L,
                                                completed = false
                                            )
                                            chunks.add(stolen)
                                            stolen
                                        } else null
                                    } else null
                                }
                            } ?: break

                            activeWorkersAtomic.incrementAndGet()
                            try {
                                downloadSingleChunkWithRetries(
                                    downloadId = spec.downloadId,
                                    chunk = chunk,
                                    partFile = partFile,
                                    urlProvider = { activeUrl },
                                    headersProvider = { activeHeaders },
                                    expectedEtag = probe.etag,
                                    expectedLastModified = probe.lastModified,
                                    speedBucket = speedBucket,
                                    totalCounter = totalDownloadedAtomic,
                                    isPausedOrCanceled = spec.isPausedOrCanceled,
                                    onExpiredUrl = { tryRefreshUrlLocked() }
                                )
                            } finally {
                                activeWorkersAtomic.decrementAndGet()
                            }
                        }
                    }
                }
                workerJobs.forEach { it.join() }
            } finally {
                monitorJob.cancel()
                runCatching { flushChunksToStore() }
            }
        }

        if (spec.isPausedOrCanceled()) {
            throw CancellationException("Download paused or cancelled")
        }

        chunkStore.onStateChanged(spec.downloadId, EngineDownloadState.VERIFYING)

        val finalSnapshot = chunks.map {
            SegmentChunk(it.index, it.startByte, it.endByte, it.downloadedBytes, it.completed, probe.etag)
        }
        verifyCompletedArtifact(partFile, totalBytes, spec.expectedMimeType, finalSnapshot)

        partFile
    }

    private suspend fun downloadSingleChunkWithRetries(
        downloadId: String,
        chunk: MutableChunk,
        partFile: File,
        urlProvider: () -> String,
        headersProvider: () -> Map<String, String>,
        expectedEtag: String?,
        expectedLastModified: String?,
        speedBucket: SharedSpeedBucket,
        totalCounter: AtomicLong,
        isPausedOrCanceled: () -> Boolean,
        onExpiredUrl: suspend () -> Boolean
    ) = coroutineScope {
        val backoffMs = longArrayOf(600L, 1200L, 2500L, 5000L, 10000L)
        var attempt = 0
        var refreshedUrlAttempts = 0

        while (isActive && !isPausedOrCanceled() && !chunk.completed) {
            val currentStart = chunk.currentOffset()
            val targetEnd = chunk.endByte
            if (currentStart > targetEnd) {
                chunk.completed = true
                return@coroutineScope
            }

            try {
                val rangeHeader = "bytes=$currentStart-$targetEnd"
                val reqBuilder = Request.Builder()
                    .url(urlProvider())
                    .header("Range", rangeHeader)
                val ifRangeValue = expectedEtag?.takeIf { it.isNotBlank() }
                    ?: expectedLastModified?.takeIf { it.isNotBlank() }
                if (ifRangeValue != null) {
                    reqBuilder.header("If-Range", ifRangeValue)
                }
                headersProvider().forEach { (k, v) ->
                    if (!k.equals("Range", true) && !k.equals("Accept-Encoding", true)) {
                        reqBuilder.header(k, v)
                    }
                }

                httpClient.newCall(reqBuilder.build()).execute().use { response ->
                    val code = response.code
                    if ((code == 403 || code == 410) && refreshedUrlAttempts < 2) {
                        refreshedUrlAttempts++
                        val refreshed = onExpiredUrl()
                        if (refreshed) {
                            return@use
                        }
                        throw DownloadEngineException(DownloadError.ExpiredMediaURL(code))
                    }

                    // CRITICAL ANTI-CORRUPTION CHECK:
                    // Ranged chunk requests MUST receive 206 Partial Content.
                    // If server returns 200 OK (e.g. If-Range rejected or Range ignored), do NOT write byte 0 at currentStart!
                    if (code == 200) {
                        val newEtag = response.header("ETag")
                        val newLastMod = response.header("Last-Modified")
                        val changed = (!expectedEtag.isNullOrBlank() && !newEtag.isNullOrBlank() && expectedEtag != newEtag) ||
                            (!expectedLastModified.isNullOrBlank() && !newLastMod.isNullOrBlank() && expectedLastModified != newLastMod)
                        if (changed) {
                            throw DownloadEngineException(
                                DownloadError.FileChangedOnServer("If-Range rejected with HTTP 200; server resource changed")
                            )
                        }
                        throw DownloadEngineException(
                            DownloadError.InvalidRangeResponse(rangeHeader, 200)
                        )
                    }

                    if (code != 206) {
                        throw DownloadEngineException(DownloadErrorMapper.fromHttpStatus(code, urlProvider()))
                    }

                    val contentRange = response.header("Content-Range")
                    if (!contentRange.isNullOrBlank()) {
                        val rangeStart = parseContentRangeStart(contentRange)
                        if (rangeStart != null && rangeStart != currentStart) {
                            throw DownloadEngineException(
                                DownloadError.InvalidRangeResponse(
                                    rangeHeader,
                                    code,
                                    "Mismatched Content-Range start: expected $currentStart got $rangeStart"
                                )
                            )
                        }
                    }

                    val contentType = response.header("Content-Type")?.lowercase(Locale.ROOT).orEmpty()
                    if (contentType.contains("text/html")) {
                        throw DownloadEngineException(DownloadError.UnsupportedMedia("text/html"))
                    }

                    val body = response.body ?: throw IOException("Empty response body")
                    val stream = body.byteStream()
                    RandomAccessFile(partFile, "rw").use { raf ->
                        val channel = raf.channel
                        var writePos = currentStart
                        val buf = ByteArray(128 * 1024)
                        val byteBuf = ByteBuffer.wrap(buf)

                        while (isActive && !isPausedOrCanceled()) {
                            val activeEnd = chunk.endByte
                            val maxReadable = (activeEnd - writePos + 1L)
                            if (maxReadable <= 0L) {
                                chunk.completed = true
                                break
                            }
                            val toRead = min(buf.size.toLong(), maxReadable).toInt()
                            val read = stream.read(buf, 0, toRead)
                            if (read == -1) {
                                if (writePos > chunk.endByte) {
                                    chunk.completed = true
                                } else {
                                    throw IOException("Unexpected EOF at $writePos/${chunk.endByte}")
                                }
                                break
                            }
                            if (read > 0) {
                                speedBucket.consume(read)
                                byteBuf.position(0)
                                byteBuf.limit(read)
                                channel.position(writePos)
                                while (byteBuf.hasRemaining()) {
                                    channel.write(byteBuf)
                                }
                                writePos += read
                                chunk.downloadedBytes += read
                                totalCounter.addAndGet(read.toLong())
                            }
                        }
                    }
                }
                if (chunk.currentOffset() > chunk.endByte) {
                    chunk.completed = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (dee: DownloadEngineException) {
                throw dee
            } catch (io: IOException) {
                if (isPausedOrCanceled()) throw CancellationException("Paused/Canceled")
                if (attempt >= backoffMs.size) {
                    throw DownloadEngineException(DownloadErrorMapper.fromThrowable(io), io)
                }
                chunkStore.onStateChanged(downloadId, EngineDownloadState.RETRYING)
                delay(backoffMs[attempt])
                attempt++
                if (!isPausedOrCanceled()) {
                    chunkStore.onStateChanged(downloadId, EngineDownloadState.DOWNLOADING)
                }
            }
        }
    }

    private suspend fun downloadSingleStream(
        spec: DownloadJobSpec,
        initialUrl: String,
        initialHeaders: Map<String, String>,
        partFile: File,
        knownTotalBytes: Long
    ): File = coroutineScope {
        val backoffMs = longArrayOf(600L, 1200L, 2500L, 5000L, 10000L)
        val speedBucket = SharedSpeedBucket(spec.speedLimitKBps)
        var activeUrl = initialUrl
        var activeHeaders = initialHeaders
        var attempt = 0
        var urlRefreshed = false

        while (isActive && !spec.isPausedOrCanceled()) {
            try {
                val reqBuilder = Request.Builder().url(activeUrl)
                activeHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }
                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    if ((resp.code == 403 || resp.code == 410) && !urlRefreshed) {
                        urlRefreshed = true
                        val fresh = spec.onRefreshExpiredUrl()
                        if (fresh != null) {
                            activeUrl = fresh.first
                            activeHeaders = fresh.second
                            return@use
                        }
                        throw DownloadEngineException(DownloadError.ExpiredMediaURL(resp.code))
                    }
                    if (!resp.isSuccessful) {
                        throw DownloadEngineException(DownloadErrorMapper.fromHttpStatus(resp.code, activeUrl))
                    }
                    val contentType = resp.header("Content-Type")?.lowercase(Locale.ROOT).orEmpty()
                    if (contentType.contains("text/html")) {
                        throw DownloadEngineException(DownloadError.UnsupportedMedia("text/html"))
                    }

                    val body = resp.body ?: throw IOException("Empty stream")
                    val total = body.contentLength().takeIf { it > 0L } ?: knownTotalBytes
                    val buf = ByteArray(128 * 1024)
                    var downloaded = 0L
                    var lastEmit = 0L
                    var lastDb = 0L
                    var winStart = System.currentTimeMillis()
                    var winBytes = 0L

                    partFile.outputStream().use { out ->
                        body.byteStream().use { input ->
                            while (isActive && !spec.isPausedOrCanceled()) {
                                val read = input.read(buf)
                                if (read == -1) break
                                if (read > 0) {
                                    speedBucket.consume(read)
                                    out.write(buf, 0, read)
                                    downloaded += read
                                    winBytes += read
                                    val now = System.currentTimeMillis()
                                    if (now - lastEmit >= 450L) {
                                        val elapsed = max(1L, now - winStart)
                                        val bps = (winBytes * 1000L) / elapsed
                                        if (elapsed >= 2000L) {
                                            winStart = now
                                            winBytes = 0L
                                        }
                                        val eta = if (total > 0L && bps > 0L) (total - downloaded) / bps else -1L
                                        spec.onProgress(downloaded, total, LiveProgressSnapshot(bps, eta, 1))
                                        lastEmit = now
                                    }
                                    if (now - lastDb >= 900L) {
                                        chunkStore.updateProgress(spec.downloadId, downloaded, total, 1)
                                        lastDb = now
                                    }
                                }
                            }
                            out.flush()
                        }
                    }

                    if (spec.isPausedOrCanceled()) {
                        throw CancellationException("Paused or cancelled")
                    }
                    chunkStore.updateProgress(spec.downloadId, downloaded, max(downloaded, total), 1)
                    chunkStore.onStateChanged(spec.downloadId, EngineDownloadState.VERIFYING)
                    return@coroutineScope partFile
                }
            } catch (e: CancellationException) {
                throw e
            } catch (dee: DownloadEngineException) {
                throw dee
            } catch (io: IOException) {
                if (spec.isPausedOrCanceled()) throw CancellationException("Paused/Canceled")
                if (attempt >= backoffMs.size) {
                    throw DownloadEngineException(DownloadErrorMapper.fromThrowable(io), io)
                }
                chunkStore.onStateChanged(spec.downloadId, EngineDownloadState.RETRYING)
                delay(backoffMs[attempt])
                attempt++
                if (!spec.isPausedOrCanceled()) {
                    chunkStore.onStateChanged(spec.downloadId, EngineDownloadState.DOWNLOADING)
                }
            }
        }
        partFile
    }

    private suspend fun probeServerWithRefresh(
        url: String,
        headers: Map<String, String>,
        onRefreshExpiredUrl: suspend () -> Pair<String, Map<String, String>>?,
        onUpdated: (String, Map<String, String>) -> Unit
    ): HttpProbeInfo {
        var currentUrl = url
        var currentHeaders = headers
        var first = probeServer(currentUrl, currentHeaders)
        if ((first.statusCode == 403 || first.statusCode == 410)) {
            val fresh = onRefreshExpiredUrl()
            if (fresh != null) {
                currentUrl = fresh.first
                currentHeaders = fresh.second
                onUpdated(currentUrl, currentHeaders)
                first = probeServer(currentUrl, currentHeaders)
            }
        }
        return first
    }

    fun probeServer(url: String, headers: Map<String, String> = emptyMap()): HttpProbeInfo {
        var headProbe: HttpProbeInfo? = null
        runCatching {
            val headReq = Request.Builder().url(url).head()
            headers.forEach { (k, v) -> headReq.header(k, v) }
            httpClient.newCall(headReq.build()).execute().use { resp ->
                val contentType = resp.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                val len = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                val acceptRanges = resp.header("Accept-Ranges")?.lowercase(Locale.ROOT)
                val supportsRange = acceptRanges == "bytes" && len > 0L
                val etag = resp.header("ETag")
                val lastMod = resp.header("Last-Modified")
                val finalUrl = resp.request.url.toString()
                val lowerPath = finalUrl.lowercase(Locale.ROOT).substringBefore('?').substringBefore('#')
                val isHtml = contentType.contains("text/html") || contentType.contains("application/xhtml+xml")

                headProbe = HttpProbeInfo(
                    requestedUrl = url,
                    finalUrl = finalUrl,
                    statusCode = resp.code,
                    contentLength = len,
                    contentType = contentType,
                    acceptRanges = acceptRanges,
                    supportsRange = supportsRange,
                    etag = etag,
                    lastModified = lastMod,
                    isHls = lowerPath.endsWith(".m3u8") || contentType.contains("mpegurl"),
                    isDash = lowerPath.endsWith(".mpd") || contentType.contains("dash+xml"),
                    isHtmlPage = isHtml
                )
                if (resp.isSuccessful && (supportsRange || isHtml || len > 0L)) {
                    return headProbe!!
                }
            }
        }

        return runCatching {
            val rangeReq = Request.Builder()
                .url(url)
                .get()
                .header("Range", "bytes=0-0")
            headers.forEach { (k, v) ->
                if (!k.equals("Range", true)) rangeReq.header(k, v)
            }
            httpClient.newCall(rangeReq.build()).execute().use { resp ->
                val contentType = resp.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                val contentRange = resp.header("Content-Range")
                val totalFromRange = contentRange?.substringAfter('/')?.toLongOrNull()
                val etag = resp.header("ETag")
                val lastMod = resp.header("Last-Modified")
                val finalUrl = resp.request.url.toString()
                val lowerPath = finalUrl.lowercase(Locale.ROOT).substringBefore('?').substringBefore('#')
                val isHtml = contentType.contains("text/html") || contentType.contains("application/xhtml+xml")
                val is206 = resp.code == 206 && totalFromRange != null && totalFromRange > 0L
                val totalLen = if (is206) totalFromRange!! else (resp.header("Content-Length")?.toLongOrNull() ?: -1L)

                HttpProbeInfo(
                    requestedUrl = url,
                    finalUrl = finalUrl,
                    statusCode = resp.code,
                    contentLength = totalLen,
                    contentType = contentType,
                    acceptRanges = resp.header("Accept-Ranges") ?: if (is206) "bytes" else null,
                    supportsRange = is206,
                    etag = etag,
                    lastModified = lastMod,
                    isHls = lowerPath.endsWith(".m3u8") || contentType.contains("mpegurl"),
                    isDash = lowerPath.endsWith(".mpd") || contentType.contains("dash+xml"),
                    isHtmlPage = isHtml
                )
            }
        }.getOrElse { err ->
            headProbe ?: throw DownloadEngineException(DownloadErrorMapper.fromThrowable(err), err)
        }
    }

    private fun verifyCompletedArtifact(
        partFile: File,
        expectedTotalBytes: Long,
        expectedMimeType: String,
        chunks: List<SegmentChunk>
    ) {
        if (!partFile.exists() || partFile.length() <= 0L) {
            throw DownloadEngineException(
                DownloadError.IntegrityCheckFailed("Output temp file missing or empty: ${partFile.absolutePath}")
            )
        }
        if (expectedTotalBytes > 0L && partFile.length() != expectedTotalBytes) {
            throw DownloadEngineException(
                DownloadError.IntegrityCheckFailed(
                    "File size mismatch: expected=$expectedTotalBytes actual=${partFile.length()}"
                )
            )
        }
        if (chunks.isNotEmpty() && chunks.any { !it.completed }) {
            throw DownloadEngineException(
                DownloadError.IntegrityCheckFailed("Not all byte chunks completed cleanly")
            )
        }
        if (!expectedMimeType.contains("html", ignoreCase = true)) {
            val headBytes = ByteArray(96)
            val readCount = FileInputStream(partFile).use { it.read(headBytes) }
            if (readCount > 0) {
                val headText = String(headBytes, 0, readCount, Charsets.UTF_8).trimStart().lowercase(Locale.ROOT)
                if (headText.startsWith("<!doctype html") ||
                    headText.startsWith("<html") ||
                    headText.startsWith("<head")
                ) {
                    throw DownloadEngineException(
                        DownloadError.IntegrityCheckFailed("Downloaded file starts with HTML markup instead of binary media")
                    )
                }
            }
        }
    }

    companion object {
        private const val MIN_CHUNK_BYTES = 1024L * 1024L

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        /**
         * Conservative adaptive chunk computation:
         * - Non-ranged or < 2 MiB: 1 connection
         * - Respects caller requested chunk count within [1, 16] and never drops below 1 MiB per segment
         */
        fun computeAdaptiveChunkCount(totalBytes: Long, supportsRange: Boolean, requestedChunks: Int): Int {
            if (!supportsRange || totalBytes < 2L * MIN_CHUNK_BYTES) return 1
            val maxBySize = (totalBytes / MIN_CHUNK_BYTES).toInt().coerceAtLeast(1)
            return requestedChunks.coerceIn(1, 16).coerceAtMost(maxBySize)
        }

        fun buildSegmentChunks(
            totalBytes: Long,
            chunkCount: Int,
            etag: String?
        ): List<SegmentChunk> {
            if (totalBytes <= 0L || chunkCount <= 1) {
                val end = if (totalBytes > 0L) totalBytes - 1L else -1L
                return listOf(SegmentChunk(0, 0L, end, 0L, false, etag))
            }
            val sizePerChunk = totalBytes / chunkCount
            return (0 until chunkCount).map { idx ->
                val start = idx * sizePerChunk
                val end = if (idx == chunkCount - 1) totalBytes - 1L else ((idx + 1) * sizePerChunk) - 1L
                SegmentChunk(
                    index = idx,
                    startByte = start,
                    endByte = end,
                    downloadedBytes = 0L,
                    completed = false,
                    etag = etag
                )
            }
        }

        fun validateChunkLayout(chunks: List<SegmentChunk>, totalBytes: Long): Boolean {
            if (chunks.isEmpty() || totalBytes <= 0L) return false
            val sorted = chunks.sortedBy { it.startByte }
            if (sorted.first().startByte != 0L) return false
            if (sorted.last().endByte != totalBytes - 1L) return false
            var covered = 0L
            for (i in sorted.indices) {
                val c = sorted[i]
                if (c.endByte < c.startByte) return false
                if (i > 0 && sorted[i - 1].endByte + 1L != c.startByte) return false
                covered += (c.endByte - c.startByte + 1L)
            }
            return covered == totalBytes
        }

        fun parseContentRangeStart(contentRange: String): Long? {
            // Format: bytes 100-199/500
            val afterBytes = contentRange.trim().removePrefix("bytes").trim()
            val startStr = afterBytes.substringBefore('-').trim()
            return startStr.toLongOrNull()
        }
    }
}
