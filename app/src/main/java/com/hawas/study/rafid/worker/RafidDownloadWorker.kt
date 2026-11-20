package com.hawas.study.rafid.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.download.core.downloader.ChunkMetadataStore
import com.example.download.core.downloader.DownloadJobSpec
import com.example.download.core.downloader.SegmentChunk
import com.example.download.core.downloader.SegmentedDownloadEngine
import com.example.download.core.model.DownloadEngineException
import com.example.download.core.model.DownloadError
import com.example.download.core.model.DownloadErrorMapper
import com.example.download.core.model.EngineDownloadState
import com.example.download.core.resolver.AndroidYtDlpGateway
import com.example.download.core.resolver.ResolverHttpUtils
import com.hawas.study.rafid.data.RafidDao
import com.hawas.study.rafid.data.RafidDatabase
import com.hawas.study.rafid.data.RafidPreferencesRepository
import com.hawas.study.rafid.engine.AdAndTrackerShield
import com.hawas.study.rafid.engine.RafidMediaStoreSaver
import com.hawas.study.rafid.engine.UniversalLinkResolver
import com.hawas.study.rafid.engine.UniversalMediaExtractor
import com.hawas.study.rafid.models.RafidDownloadStatus
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.File
import java.util.concurrent.TimeUnit

class RafidDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"

        fun uniqueWorkName(downloadId: String): String = "rafid_dl_$downloadId"

        fun enqueueDownload(
            context: Context,
            downloadId: String,
            wifiOnly: Boolean,
            initialDelayMillis: Long = 0L
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()

            val requestBuilder = OneTimeWorkRequestBuilder<RafidDownloadWorker>()
                .setConstraints(constraints)
                .setInputData(Data.Builder().putString(KEY_DOWNLOAD_ID, downloadId).build())
                .addTag("rafid_download")
                .addTag(uniqueWorkName(downloadId))

            if (initialDelayMillis > 0L) {
                requestBuilder.setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            }

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(downloadId),
                ExistingWorkPolicy.REPLACE,
                requestBuilder.build()
            )
        }

        fun cancelDownloadWork(context: Context, downloadId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(downloadId))
            runCatching { YoutubeDL.getInstance().destroyProcessById("rafid_$downloadId") }
        }
    }

    private val client = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return@withContext Result.failure()
        val dao = RafidDatabase.getInstance(applicationContext).rafidDao()
        val prefs = RafidPreferencesRepository(applicationContext).getSnapshot()
        val record = dao.getDownloadById(downloadId) ?: return@withContext Result.failure()

        if (record.status == RafidDownloadStatus.PAUSED.name ||
            record.status == RafidDownloadStatus.CANCELLED.name ||
            prefs.isQueuePaused
        ) {
            return@withContext Result.success()
        }

        if (prefs.wifiOnly && !isOnUnmeteredNetwork(applicationContext)) {
            dao.updateDownloadStatus(downloadId, RafidDownloadStatus.QUEUED.name, null)
            return@withContext Result.retry()
        }

        val tempDir = File(applicationContext.cacheDir, "rafid_tmp/$downloadId").apply { mkdirs() }
        val primaryTemp = File(tempDir, "primary.${record.containerExt.ifBlank { "mp4" }}")
        val ytDlpGateway = AndroidYtDlpGateway(applicationContext)
        val extractor = UniversalMediaExtractor(client, ytDlpGateway = ytDlpGateway)

        var resolvedStreamUrl = record.videoStreamUrl
        var resolvedAudioUrl = record.audioStreamUrl

        if (resolvedStreamUrl == record.resolvedUrl) {
            val reExtracted = extractor.extract(record.resolvedUrl, record.cookiesHeader, record.userAgent).getOrNull()
            val candidate = reExtracted?.formats?.firstOrNull {
                !it.videoUrl.isNullOrBlank() && it.videoUrl != record.resolvedUrl &&
                    (it.isAudioOnly == record.isAudioOnly)
            }
            if (candidate != null && !candidate.videoUrl.isNullOrBlank()) {
                resolvedStreamUrl = candidate.videoUrl
                if (resolvedAudioUrl.isNullOrBlank()) {
                    resolvedAudioUrl = candidate.audioUrl
                }
            }
        }

        val headers = buildMap {
            put("User-Agent", record.userAgent ?: UniversalLinkResolver.DESKTOP_USER_AGENT)
            put("Referer", record.resolvedUrl)
            if (!record.cookiesHeader.isNullOrBlank()) put("Cookie", record.cookiesHeader)
        }

        val isPausedCheck = {
            if (isStopped) true
            else runBlocking(Dispatchers.IO) {
                val r = dao.getDownloadById(downloadId)
                r == null || r.status == RafidDownloadStatus.PAUSED.name || r.status == RafidDownloadStatus.CANCELLED.name
            }
        }

        return@withContext try {
            dao.updateDownloadProgress(
                id = downloadId,
                status = RafidDownloadStatus.DOWNLOADING.name,
                downloadedBytes = primaryTemp.length(),
                totalBytes = record.totalBytes,
                speedBytesPerSec = 0L,
                etaSeconds = 0L
            )

            // Handle HLS / DASH manifests through yt-dlp + FFmpeg cleanly
            if (ResolverHttpUtils.isManifestUrl(resolvedStreamUrl)) {
                val downloadedManifestFile = downloadManifestWithYtDlp(
                    downloadId = downloadId,
                    url = resolvedStreamUrl,
                    outDir = tempDir,
                    isAudioOnly = record.isAudioOnly,
                    targetExt = record.containerExt.ifBlank { "mp4" },
                    knownTotalBytes = record.totalBytes,
                    ytDlpGateway = ytDlpGateway,
                    dao = dao
                )
                val category = AdAndTrackerShield.categoryFromMimeOrExt(record.mimeType, record.containerExt)
                val saved = RafidMediaStoreSaver.saveFileToRafidDownloads(
                    context = applicationContext,
                    sourceFile = downloadedManifestFile,
                    title = record.title,
                    extension = record.containerExt,
                    mimeType = record.mimeType,
                    category = category,
                    saveToStudyFolder = record.saveToStudyFolder
                )
                dao.markDownloadCompleted(
                    id = downloadId,
                    finalSize = saved.fileSizeBytes,
                    mediaStoreUri = saved.uriString,
                    displayPath = saved.displayPath
                )
                runCatching { tempDir.deleteRecursively() }
                return@withContext Result.success()
            }

            val chunkStore = RafidFileChunkStore(File(tempDir, "chunks.meta"), dao, CoroutineScope(Dispatchers.IO))
            val engine = SegmentedDownloadEngine(chunkStore, client)

            val downloadedPrimary = engine.download(
                DownloadJobSpec(
                    downloadId = downloadId,
                    url = resolvedStreamUrl,
                    headers = headers,
                    targetPartFile = primaryTemp,
                    expectedMimeType = record.mimeType,
                    requestedChunks = 8,
                    savedTotalBytes = record.totalBytes,
                    isPausedOrCanceled = isPausedCheck,
                    onRefreshExpiredUrl = {
                        val re = extractor.extract(record.resolvedUrl, record.cookiesHeader, record.userAgent).getOrNull()
                        val cand = re?.formats?.firstOrNull { !it.videoUrl.isNullOrBlank() && it.isAudioOnly == record.isAudioOnly }
                        cand?.videoUrl?.let { it to headers }
                    },
                    onProgress = { done, total, snap ->
                        CoroutineScope(Dispatchers.IO).launch {
                            dao.updateDownloadProgress(
                                id = downloadId,
                                status = RafidDownloadStatus.DOWNLOADING.name,
                                downloadedBytes = done,
                                totalBytes = maxOf(0L, total),
                                speedBytesPerSec = snap.speedBytesPerSec,
                                etaSeconds = snap.etaSeconds.coerceAtLeast(0L)
                            )
                        }
                    }
                )
            )

            val secondaryAudioTemp = if (!resolvedAudioUrl.isNullOrBlank()) {
                val audioFile = File(tempDir, "audio_track.m4a")
                val audioStore = RafidFileChunkStore(File(tempDir, "audio_chunks.meta"), dao, CoroutineScope(Dispatchers.IO))
                val audioEngine = SegmentedDownloadEngine(audioStore, client)
                audioEngine.download(
                    DownloadJobSpec(
                        downloadId = "${downloadId}_audio",
                        url = resolvedAudioUrl!!,
                        headers = headers,
                        targetPartFile = audioFile,
                        expectedMimeType = "audio/mp4",
                        requestedChunks = 4,
                        isPausedOrCanceled = isPausedCheck,
                        onRefreshExpiredUrl = {
                            val re = extractor.extract(record.resolvedUrl, record.cookiesHeader, record.userAgent).getOrNull()
                            val candAudio = re?.formats?.firstNotNullOfOrNull { it.audioUrl }
                            candAudio?.let { it to headers }
                        }
                    )
                )
                audioFile
            } else {
                null
            }

            if (record.isMuxRequired || secondaryAudioTemp != null) {
                dao.updateDownloadStatus(downloadId, RafidDownloadStatus.MUXING.name, null)
                SerialFFmpegMuxerWorker.enqueueSerialMux(
                    context = applicationContext,
                    downloadId = downloadId,
                    videoFilePath = downloadedPrimary.absolutePath,
                    audioFilePath = secondaryAudioTemp?.absolutePath,
                    isAudioOnly = record.isAudioOnly
                )
            } else {
                val category = AdAndTrackerShield.categoryFromMimeOrExt(record.mimeType, record.containerExt)
                val saved = RafidMediaStoreSaver.saveFileToRafidDownloads(
                    context = applicationContext,
                    sourceFile = downloadedPrimary,
                    title = record.title,
                    extension = record.containerExt,
                    mimeType = record.mimeType,
                    category = category,
                    saveToStudyFolder = record.saveToStudyFolder
                )
                dao.markDownloadCompleted(
                    id = downloadId,
                    finalSize = saved.fileSizeBytes,
                    mediaStoreUri = saved.uriString,
                    displayPath = saved.displayPath
                )
                runCatching { tempDir.deleteRecursively() }
            }

            Result.success()
        } catch (_: CancellationException) {
            Result.success()
        } catch (t: Throwable) {
            val latest = dao.getDownloadById(downloadId)
            if (latest?.status != RafidDownloadStatus.PAUSED.name &&
                latest?.status != RafidDownloadStatus.CANCELLED.name
            ) {
                val mapped = DownloadErrorMapper.fromThrowable(t)
                dao.updateDownloadStatus(
                    id = downloadId,
                    status = RafidDownloadStatus.FAILED.name,
                    errorMessage = mapped.userMessageAr
                )
            }
            Result.failure()
        }
    }

    private suspend fun downloadManifestWithYtDlp(
        downloadId: String,
        url: String,
        outDir: File,
        isAudioOnly: Boolean,
        targetExt: String,
        knownTotalBytes: Long,
        ytDlpGateway: AndroidYtDlpGateway,
        dao: RafidDao
    ): File {
        if (!ytDlpGateway.ensureInitialized()) {
            throw DownloadEngineException(DownloadError.EngineUpdateFailed("yt-dlp/FFmpeg unavailable for HLS/DASH"))
        }
        val outBase = File(outDir, "hls_out")
        val req = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--continue")
            addOption("--concurrent-fragments", "8")
            addOption("-o", "${outBase.absolutePath}.%(ext)s")
            if (isAudioOnly) {
                addOption("-x")
                addOption("--audio-format", targetExt)
            } else {
                addOption("--merge-output-format", "mp4")
            }
        }
        var mergedSent = false
        YoutubeDL.getInstance().execute(req, "rafid_$downloadId") { progress, eta, line ->
            val cleanLine = line ?: ""
            if (!mergedSent && (cleanLine.contains("[Merger]", true) || cleanLine.contains("Fixing", true))) {
                mergedSent = true
                runBlocking { dao.updateDownloadStatus(downloadId, RafidDownloadStatus.MUXING.name, null) }
            } else {
                val estDone = if (knownTotalBytes > 0L) ((progress.coerceIn(0f, 100f) / 100f) * knownTotalBytes).toLong() else 0L
                runBlocking {
                    dao.updateDownloadProgress(
                        id = downloadId,
                        status = RafidDownloadStatus.DOWNLOADING.name,
                        downloadedBytes = estDone,
                        totalBytes = knownTotalBytes,
                        speedBytesPerSec = 0L,
                        etaSeconds = eta.coerceAtLeast(0L)
                    )
                }
            }
        }
        return outDir.listFiles()
            ?.firstOrNull { it.name.startsWith("hls_out.") && !it.name.endsWith(".part") && it.length() > 0L }
            ?: throw DownloadEngineException(DownloadError.MergeFailed("HLS/DASH output file missing after FFmpeg merge"))
    }

    private class RafidFileChunkStore(
        private val metaFile: File,
        private val dao: RafidDao,
        private val scope: CoroutineScope
    ) : ChunkMetadataStore {
        private val inMem = mutableMapOf<String, List<SegmentChunk>>()

        override suspend fun getChunks(downloadId: String): List<SegmentChunk> {
            inMem[downloadId]?.let { return it }
            if (!metaFile.exists()) return emptyList()
            val parsed = runCatching {
                metaFile.readLines().mapNotNull { line ->
                    val p = line.split("|")
                    if (p.size >= 5) {
                        SegmentChunk(
                            index = p[0].toInt(),
                            startByte = p[1].toLong(),
                            endByte = p[2].toLong(),
                            downloadedBytes = p[3].toLong(),
                            completed = p[4].toBoolean(),
                            etag = p.getOrNull(5)?.takeIf { it.isNotBlank() }
                        )
                    } else null
                }
            }.getOrDefault(emptyList())
            inMem[downloadId] = parsed
            return parsed
        }

        override suspend fun saveChunks(downloadId: String, chunks: List<SegmentChunk>) {
            inMem[downloadId] = chunks
            runCatching {
                metaFile.writeText(
                    chunks.joinToString("\n") {
                        "${it.index}|${it.startByte}|${it.endByte}|${it.downloadedBytes}|${it.completed}|${it.etag.orEmpty()}"
                    }
                )
            }
        }

        override suspend fun clearChunks(downloadId: String) {
            inMem.remove(downloadId)
            runCatching { metaFile.delete() }
        }

        override suspend fun updateProgress(downloadId: String, downloadedBytes: Long, totalBytes: Long, activeThreads: Int) {
            if (!downloadId.endsWith("_audio")) {
                scope.launch {
                    dao.updateDownloadProgress(downloadId, RafidDownloadStatus.DOWNLOADING.name, downloadedBytes, totalBytes.coerceAtLeast(0L), 0L, 0L)
                }
            }
        }

        override suspend fun updateServerMetadata(downloadId: String, finalUrl: String, etag: String?, lastModified: String?) {}

        override suspend fun onStateChanged(downloadId: String, state: EngineDownloadState, error: DownloadError?) {
            if (!downloadId.endsWith("_audio") && state == EngineDownloadState.VERIFYING) {
                dao.updateDownloadStatus(downloadId, RafidDownloadStatus.MUXING.name, null)
            }
        }
    }

    private fun isOnUnmeteredNetwork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
