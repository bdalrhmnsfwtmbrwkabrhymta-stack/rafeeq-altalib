package com.example.download.engine

import com.example.download.data.ChunkEntity
import com.example.download.data.DownloadDao
import com.example.download.data.DownloadEntity
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

class ExpiredDownloadUrlException(val statusCode: Int) : IOException("HTTP $statusCode")

data class ProbeMetadata(
    val totalBytes: Long,
    val supportsRange: Boolean,
    val etag: String?,
    val lastModified: String?
)

class ParallelChunkDownloader(
    private val dao: DownloadDao
) {
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private class MutableChunk(
        val index: Int,
        val startByte: Long,
        @Volatile var endByte: Long,
        @Volatile var downloadedBytes: Long,
        @Volatile var completed: Boolean
    ) {
        fun currentOffset(): Long = startByte + downloadedBytes
        fun remainingBytes(): Long = if (completed || endByte < 0L) 0L else max(0L, (endByte - currentOffset() + 1L))
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

    suspend fun download(
        entity: DownloadEntity,
        headers: Map<String, String>,
        requestedChunks: Int,
        speedLimitKBps: Int,
        onRefreshDirectUrl: suspend () -> Pair<String, Map<String, String>>?,
        onProgress: (downloadedBytes: Long, totalBytes: Long, snapshot: LiveProgressSnapshot) -> Unit
    ): File = withContext(Dispatchers.IO) {
        var activeUrl = entity.resolvedUrl.ifBlank { entity.originalUrl }
        var activeHeaders = headers
        val partFile = File(entity.tempPartPath).apply { parentFile?.mkdirs() }

        val probe = probeServer(activeUrl, activeHeaders)
        val totalBytes = probe.totalBytes

        val existingChunks = dao.getChunksForDownload(entity.id)
        val etagChanged = !entity.etag.isNullOrBlank() && !probe.etag.isNullOrBlank() && entity.etag != probe.etag
        val resetNeeded = etagChanged || !partFile.exists() || existingChunks.isEmpty() ||
            (totalBytes > 0L && partFile.length() != totalBytes)

        val chunks: MutableList<MutableChunk> = if (!resetNeeded && probe.supportsRange && totalBytes > 0L) {
            existingChunks.map {
                MutableChunk(
                    index = it.chunkIndex,
                    startByte = it.startByte,
                    endByte = it.endByte,
                    downloadedBytes = it.downloadedBytes,
                    completed = it.completed
                )
            }.toMutableList()
        } else {
            if (partFile.exists()) partFile.delete()
            val workerCount = computeOptimalChunkCount(totalBytes, probe.supportsRange, requestedChunks)
            if (totalBytes > 0L) {
                RandomAccessFile(partFile, "rw").use { raf ->
                    raf.setLength(totalBytes)
                }
            }
            val newEntities = buildChunkEntities(entity.id, totalBytes, workerCount, probe.etag)
            dao.replaceChunksForDownload(entity.id, newEntities)
            dao.updateResolvedUrl(entity.id, activeUrl, entity.headersJson, probe.etag, probe.lastModified)
            newEntities.map {
                MutableChunk(it.chunkIndex, it.startByte, it.endByte, 0L, false)
            }.toMutableList()
        }

        if (!probe.supportsRange || totalBytes <= 0L) {
            return@withContext downloadSingleStream(
                downloadId = entity.id,
                url = activeUrl,
                headers = activeHeaders,
                partFile = partFile,
                knownTotalBytes = totalBytes,
                speedLimitKBps = speedLimitKBps,
                onRefreshDirectUrl = onRefreshDirectUrl,
                onProgress = onProgress
            )
        }

        val queueMutex = Mutex()
        val pendingQueue = ArrayDeque(chunks.filter { !it.completed })
        val nextStealIndex = AtomicInteger((chunks.maxOfOrNull { it.index } ?: 0) + 1)
        val totalDownloadedAtomic = AtomicLong(chunks.sumOf { it.downloadedBytes })
        val activeWorkersAtomic = AtomicInteger(0)
        val speedBucket = SharedSpeedBucket(speedLimitKBps)

        val speedSamples = ArrayDeque<Pair<Long, Long>>()
        var lastUiEmitMs = 0L
        var lastDbFlushMs = System.currentTimeMillis()
        val workerConcurrency = chunks.size.coerceIn(1, 16)

        val urlRefreshMutex = Mutex()
        var lastRefreshMs = 0L

        suspend fun tryRefreshUrlLocked(): Boolean {
            return urlRefreshMutex.withLock {
                val now = System.currentTimeMillis()
                if (now - lastRefreshMs < 4000L) return@withLock true
                val refreshed = onRefreshDirectUrl() ?: return@withLock false
                activeUrl = refreshed.first
                activeHeaders = refreshed.second
                lastRefreshMs = System.currentTimeMillis()
                dao.updateResolvedUrl(entity.id, activeUrl, entity.headersJson, probe.etag, probe.lastModified)
                true
            }
        }

        suspend fun flushChunksToDb() {
            val snapshot = queueMutex.withLock {
                chunks.map {
                    ChunkEntity(
                        downloadId = entity.id,
                        chunkIndex = it.index,
                        startByte = it.startByte,
                        endByte = it.endByte,
                        downloadedBytes = it.downloadedBytes,
                        completed = it.completed,
                        etag = probe.etag
                    )
                }
            }
            dao.replaceChunksForDownload(entity.id, snapshot)
            dao.updateProgress(
                id = entity.id,
                downloaded = totalDownloadedAtomic.get().coerceAtMost(totalBytes),
                total = totalBytes,
                threads = activeWorkersAtomic.get()
            )
        }

        coroutineScope {
            val monitorJob = launch {
                while (isActive) {
                    delay(500L)
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

                    if (now - lastUiEmitMs >= 500L) {
                        lastUiEmitMs = now
                        onProgress(
                            currentDone,
                            totalBytes,
                            LiveProgressSnapshot(
                                speedBytesPerSec = speedBps,
                                etaSeconds = etaSec,
                                activeThreads = activeWorkersAtomic.get()
                            )
                        )
                    }
                    if (now - lastDbFlushMs >= 1000L) {
                        lastDbFlushMs = now
                        flushChunksToDb()
                    }
                }
            }

            try {
                val workerJobs = List(workerConcurrency) {
                    launch(Dispatchers.IO) {
                        while (isActive) {
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
                                    chunk = chunk,
                                    partFile = partFile,
                                    urlProvider = { activeUrl },
                                    headersProvider = { activeHeaders },
                                    etag = probe.etag,
                                    speedBucket = speedBucket,
                                    totalCounter = totalDownloadedAtomic,
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
                runCatching { flushChunksToDb() }
            }
        }

        val incomplete = chunks.any { !it.completed && it.remainingBytes() > 0L }
        if (incomplete) {
            throw IOException("لم يكتمل تنزيل كافة أجزاء الملف")
        }

        partFile
    }

    private suspend fun downloadSingleChunkWithRetries(
        chunk: MutableChunk,
        partFile: File,
        urlProvider: () -> String,
        headersProvider: () -> Map<String, String>,
        etag: String?,
        speedBucket: SharedSpeedBucket,
        totalCounter: AtomicLong,
        onExpiredUrl: suspend () -> Boolean
    ) = coroutineScope {
        val backoffMs = longArrayOf(1000L, 2000L, 4000L, 8000L, 16000L)
        var attempt = 0
        var refreshedUrlAttempts = 0

        while (isActive && !chunk.completed) {
            val currentStart = chunk.currentOffset()
            val targetEnd = chunk.endByte
            if (currentStart > targetEnd) {
                chunk.completed = true
                return@coroutineScope
            }

            try {
                val reqBuilder = Request.Builder()
                    .url(urlProvider())
                    .header("Range", "bytes=$currentStart-$targetEnd")
                if (!etag.isNullOrBlank()) {
                    reqBuilder.header("If-Range", etag)
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
                        throw ExpiredDownloadUrlException(code)
                    }
                    if (!response.isSuccessful && code != 206) {
                        throw IOException("HTTP $code")
                    }
                    val body = response.body ?: throw IOException("Empty response body")
                    val stream = body.byteStream()
                    RandomAccessFile(partFile, "rw").use { raf ->
                        val channel = raf.channel
                        var writePos = currentStart
                        val buf = ByteArray(128 * 1024)
                        val byteBuf = ByteBuffer.wrap(buf)

                        while (isActive) {
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
            } catch (expired: ExpiredDownloadUrlException) {
                val refreshed = onExpiredUrl()
                if (!refreshed) throw expired
            } catch (io: IOException) {
                if (attempt >= backoffMs.size) throw io
                delay(backoffMs[attempt])
                attempt++
            }
        }
    }

    private suspend fun downloadSingleStream(
        downloadId: String,
        url: String,
        headers: Map<String, String>,
        partFile: File,
        knownTotalBytes: Long,
        speedLimitKBps: Int,
        onRefreshDirectUrl: suspend () -> Pair<String, Map<String, String>>?,
        onProgress: (Long, Long, LiveProgressSnapshot) -> Unit
    ): File = coroutineScope {
        val backoffMs = longArrayOf(1000L, 2000L, 4000L, 8000L, 16000L)
        val speedBucket = SharedSpeedBucket(speedLimitKBps)
        var activeUrl = url
        var activeHeaders = headers
        var attempt = 0
        var urlRefreshed = false

        while (isActive) {
            try {
                val reqBuilder = Request.Builder().url(activeUrl)
                activeHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }
                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    if ((resp.code == 403 || resp.code == 410) && !urlRefreshed) {
                        urlRefreshed = true
                        val fresh = onRefreshDirectUrl()
                        if (fresh != null) {
                            activeUrl = fresh.first
                            activeHeaders = fresh.second
                            throw ExpiredDownloadUrlException(resp.code)
                        }
                    }
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
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
                            while (isActive) {
                                val read = input.read(buf)
                                if (read == -1) break
                                if (read > 0) {
                                    speedBucket.consume(read)
                                    out.write(buf, 0, read)
                                    downloaded += read
                                    winBytes += read
                                    val now = System.currentTimeMillis()
                                    if (now - lastEmit >= 500L) {
                                        val elapsed = max(1L, now - winStart)
                                        val bps = (winBytes * 1000L) / elapsed
                                        if (elapsed >= 2000L) {
                                            winStart = now
                                            winBytes = 0L
                                        }
                                        val eta = if (total > 0L && bps > 0L) (total - downloaded) / bps else -1L
                                        onProgress(downloaded, total, LiveProgressSnapshot(bps, eta, 1))
                                        lastEmit = now
                                    }
                                    if (now - lastDb >= 1000L) {
                                        dao.updateProgress(downloadId, downloaded, total, 1)
                                        lastDb = now
                                    }
                                }
                            }
                            out.flush()
                        }
                    }
                    dao.updateProgress(downloadId, downloaded, max(downloaded, total), 1)
                    return@coroutineScope partFile
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: ExpiredDownloadUrlException) {
                continue
            } catch (io: IOException) {
                if (attempt >= backoffMs.size) throw io
                delay(backoffMs[attempt])
                attempt++
            }
        }
        partFile
    }

    private fun probeServer(url: String, headers: Map<String, String>): ProbeMetadata {
        runCatching {
            val headReq = Request.Builder().url(url).head()
            headers.forEach { (k, v) -> headReq.header(k, v) }
            httpClient.newCall(headReq.build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val len = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    val acceptRanges = resp.header("Accept-Ranges")?.lowercase(Locale.ROOT)
                    val supportsRange = acceptRanges == "bytes" && len > 0L
                    val etag = resp.header("ETag")
                    val lastMod = resp.header("Last-Modified")
                    if (len > 0L && supportsRange) {
                        return ProbeMetadata(len, true, etag, lastMod)
                    }
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
                val contentRange = resp.header("Content-Range")
                val totalFromRange = contentRange?.substringAfter('/')?.toLongOrNull()
                val etag = resp.header("ETag")
                val lastMod = resp.header("Last-Modified")
                if (resp.code == 206 && totalFromRange != null && totalFromRange > 0L) {
                    ProbeMetadata(totalFromRange, true, etag, lastMod)
                } else {
                    val len = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    ProbeMetadata(len, false, etag, lastMod)
                }
            }
        }.getOrDefault(ProbeMetadata(-1L, false, null, null))
    }

    companion object {
        private const val MIN_CHUNK_BYTES = 1024L * 1024L

        fun computeOptimalChunkCount(totalBytes: Long, supportsRange: Boolean, requestedChunks: Int): Int {
            if (!supportsRange || totalBytes < 2L * MIN_CHUNK_BYTES) return 1
            val maxBySize = (totalBytes / MIN_CHUNK_BYTES).toInt().coerceAtLeast(1)
            return requestedChunks.coerceIn(8, 16).coerceAtMost(maxBySize)
        }

        fun buildChunkEntities(
            downloadId: String,
            totalBytes: Long,
            chunkCount: Int,
            etag: String?
        ): List<ChunkEntity> {
            if (totalBytes <= 0L || chunkCount <= 1) {
                val end = if (totalBytes > 0L) totalBytes - 1L else -1L
                return listOf(
                    ChunkEntity(
                        downloadId = downloadId,
                        chunkIndex = 0,
                        startByte = 0L,
                        endByte = end,
                        downloadedBytes = 0L,
                        completed = false,
                        etag = etag
                    )
                )
            }
            val sizePerChunk = totalBytes / chunkCount
            return (0 until chunkCount).map { idx ->
                val start = idx * sizePerChunk
                val end = if (idx == chunkCount - 1) totalBytes - 1L else ((idx + 1) * sizePerChunk) - 1L
                ChunkEntity(
                    downloadId = downloadId,
                    chunkIndex = idx,
                    startByte = start,
                    endByte = end,
                    downloadedBytes = 0L,
                    completed = false,
                    etag = etag
                )
            }
        }
    }
}
