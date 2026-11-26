package com.example.download.engine

import com.example.download.core.data.RoomChunkMetadataStore
import com.example.download.core.downloader.DownloadJobSpec
import com.example.download.core.downloader.SegmentedDownloadEngine
import com.example.download.core.model.EngineDownloadState
import com.example.download.data.ChunkEntity
import com.example.download.data.DownloadDao
import com.example.download.data.DownloadEntity
import com.example.download.data.DownloadStatus
import com.example.download.data.LiveProgressSnapshot
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class ExpiredDownloadUrlException(val statusCode: Int) : IOException("HTTP $statusCode")

data class ProbeMetadata(
    val totalBytes: Long,
    val supportsRange: Boolean,
    val etag: String?,
    val lastModified: String?
)

/**
 * Compatibility facade preserving public API signatures while delegating all chunked HTTP
 * transfers, ETag/Last-Modified server-change checks, strict HTTP 206 verification, and
 * final integrity checks to [SegmentedDownloadEngine].
 */
class ParallelChunkDownloader(
    private val dao: DownloadDao
) {
    private val httpClient: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 24
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(24, 5, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun download(
        entity: DownloadEntity,
        headers: Map<String, String>,
        requestedChunks: Int,
        speedLimitKBps: Int,
        onRefreshDirectUrl: suspend () -> Pair<String, Map<String, String>>?,
        onProgress: (downloadedBytes: Long, totalBytes: Long, snapshot: LiveProgressSnapshot) -> Unit
    ): File {
        val store = RoomChunkMetadataStore(dao) { downloadId, state, err ->
            val legacyStatus = when (state) {
                EngineDownloadState.RETRYING -> DownloadStatus.DOWNLOADING
                else -> state.toSmartDownloadStatus()
            }
            if (state == EngineDownloadState.DOWNLOADING ||
                state == EngineDownloadState.RETRYING ||
                state == EngineDownloadState.VERIFYING ||
                state == EngineDownloadState.MERGING
            ) {
                dao.updateStatus(
                    id = downloadId,
                    status = legacyStatus,
                    threads = if (state == EngineDownloadState.VERIFYING || state == EngineDownloadState.MERGING) 1 else requestedChunks,
                    errorMsg = err?.userMessageAr,
                    diagnostic = err?.technicalDetail,
                    engineState = state.name,
                    errorCode = err?.code
                )
            }
        }
        val engine = SegmentedDownloadEngine(store, httpClient)
        return engine.download(
            DownloadJobSpec(
                downloadId = entity.id,
                url = entity.resolvedUrl.ifBlank { entity.originalUrl },
                headers = headers,
                targetPartFile = File(entity.tempPartPath),
                expectedMimeType = entity.mimeType,
                requestedChunks = requestedChunks,
                speedLimitKBps = speedLimitKBps,
                savedEtag = entity.etag,
                savedLastModified = entity.lastModified,
                savedTotalBytes = entity.totalBytes,
                failIfFileChangedOnServer = true,
                onRefreshExpiredUrl = onRefreshDirectUrl,
                onProgress = onProgress
            )
        )
    }

    suspend fun downloadSideTrack(
        downloadId: String,
        url: String,
        targetFile: File,
        expectedMimeType: String,
        headers: Map<String, String>,
        requestedChunks: Int,
        speedLimitKBps: Int,
        onRefreshDirectUrl: suspend () -> Pair<String, Map<String, String>>?,
        onProgress: (downloadedBytes: Long, totalBytes: Long, snapshot: LiveProgressSnapshot) -> Unit
    ): File {
        val engine = SegmentedDownloadEngine(com.example.download.core.downloader.InMemoryChunkMetadataStore(), httpClient)
        return engine.download(
            DownloadJobSpec(
                downloadId = downloadId,
                url = url,
                headers = headers,
                targetPartFile = targetFile,
                expectedMimeType = expectedMimeType,
                requestedChunks = requestedChunks,
                speedLimitKBps = speedLimitKBps,
                failIfFileChangedOnServer = false,
                onRefreshExpiredUrl = onRefreshDirectUrl,
                onProgress = onProgress
            )
        )
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
            return SegmentedDownloadEngine.buildSegmentChunks(totalBytes, chunkCount, etag).map {
                ChunkEntity(
                    downloadId = downloadId,
                    chunkIndex = it.index,
                    startByte = it.startByte,
                    endByte = it.endByte,
                    downloadedBytes = it.downloadedBytes,
                    completed = it.completed,
                    etag = it.etag
                )
            }
        }
    }
}
