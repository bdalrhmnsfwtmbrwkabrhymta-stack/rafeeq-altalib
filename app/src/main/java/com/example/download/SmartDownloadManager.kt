package com.example.download

import android.content.Context
import com.example.download.data.DownloadDatabase
import com.example.download.data.DownloadEntity
import com.example.download.data.DownloadSettings
import com.example.download.data.DownloadSettingsRepo
import com.example.download.data.DownloadStatus
import com.example.download.data.ExtractionResult
import com.example.download.data.LiveProgressSnapshot
import com.example.download.data.MediaQualityOption
import com.example.download.data.PlaylistEntry
import com.example.download.engine.ParallelChunkDownloader
import com.example.download.engine.StorageHelper
import com.example.download.engine.YtDlpRunner
import com.example.download.extract.LinkExtractor
import com.example.download.service.DownloadForegroundService
import com.example.download.service.NetworkAutoResumeMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed class EnqueueOutcome {
    data class Enqueued(val downloadId: String) : EnqueueOutcome()
    data class Duplicate(val existingTitle: String) : EnqueueOutcome()
    data class InsufficientSpace(val requiredBytes: Long) : EnqueueOutcome()
}

class SmartDownloadManager private constructor(private val appContext: Context) {

    private val db = DownloadDatabase.getInstance(appContext)
    private val dao = db.downloadDao()
    val settingsRepo = DownloadSettingsRepo(appContext)
    private val chunkDownloader = ParallelChunkDownloader(dao)
    private val ytDlpRunner = YtDlpRunner(appContext, settingsRepo)
    private val networkMonitor = NetworkAutoResumeMonitor(appContext)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val schedulerMutex = Mutex()
    private val perDownloadMutexes = ConcurrentHashMap<String, Mutex>()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pausedByUserIds = ConcurrentHashMap.newKeySet<String>()
    private val pausedByNetworkIds = ConcurrentHashMap.newKeySet<String>()
    private val canceledByUserIds = ConcurrentHashMap.newKeySet<String>()

    private val _liveStatsFlow = MutableStateFlow<Map<String, LiveProgressSnapshot>>(emptyMap())
    val liveStatsFlow: StateFlow<Map<String, LiveProgressSnapshot>> = _liveStatsFlow.asStateFlow()

    val allDownloadsFlow: Flow<List<DownloadEntity>> = dao.observeAllDownloads()
    val settingsFlow: Flow<DownloadSettings> = settingsRepo.settingsFlow

    init {
        networkMonitor.register()
    }

    private fun mutexFor(id: String): Mutex =
        perDownloadMutexes.getOrPut(id) { Mutex() }

    suspend fun extractLink(url: String, checkPlaylist: Boolean = true): ExtractionResult {
        return LinkExtractor.extract(
            context = appContext,
            rawInputUrl = url,
            settingsRepo = settingsRepo,
            checkPlaylist = checkPlaylist
        )
    }

    suspend fun enqueueSingleDownload(
        video: ExtractionResult.SingleVideo,
        quality: MediaQualityOption
    ): EnqueueOutcome {
        val existing = dao.findDuplicate(video.originalUrl, quality.id)
        if (existing != null) {
            return EnqueueOutcome.Duplicate(existing.title)
        }

        if (quality.estimatedBytes > 0L && !StorageHelper.hasEnoughFreeSpace(appContext, quality.estimatedBytes)) {
            return EnqueueOutcome.InsufficientSpace(quality.estimatedBytes)
        }

        settingsRepo.setLastSelectedQualityId(quality.id)

        val id = UUID.randomUUID().toString()
        val fileName = StorageHelper.sanitizeFileName(video.title, quality.ext)
        val partFile = StorageHelper.prepareTempPartFile(appContext, id, fileName)
        val mergedHeaders = video.headers + quality.headers
        val mimeType = when {
            quality.isAudioOnly && quality.ext == "mp3" -> "audio/mpeg"
            quality.isAudioOnly -> "audio/mp4"
            else -> "video/mp4"
        }

        val initialStatus = if (shouldWaitForWifiOrNetwork()) {
            DownloadStatus.WAITING_NETWORK
        } else {
            DownloadStatus.QUEUED
        }

        val entity = DownloadEntity(
            id = id,
            originalUrl = video.originalUrl,
            resolvedUrl = quality.directUrl.ifBlank { video.resolvedUrl },
            audioUrl = quality.audioUrl,
            title = video.title,
            thumbnailUrl = video.thumbnailUrl,
            platform = video.platform,
            qualityId = quality.id,
            qualityLabel = quality.labelAr,
            formatId = quality.formatId,
            isAudioOnly = quality.isAudioOnly,
            isHlsOrDash = quality.isHlsOrDash,
            needsMux = quality.needsMux,
            fileName = fileName,
            mimeType = mimeType,
            tempPartPath = partFile.absolutePath,
            outputDisplayPath = "Download/HawasElDirasa/${if (quality.isAudioOnly) "Audio" else "Video"}/$fileName",
            totalBytes = quality.estimatedBytes,
            downloadedBytes = 0L,
            referer = video.referer,
            headersJson = encodeHeaders(mergedHeaders),
            status = initialStatus,
            durationSeconds = video.durationSeconds
        )

        mutexFor(id).withLock {
            dao.upsertDownload(entity)
        }

        DownloadForegroundService.startOrRefresh(appContext)
        scheduleNextQueued()
        return EnqueueOutcome.Enqueued(id)
    }

    suspend fun enqueuePlaylistBatch(
        entries: List<PlaylistEntry>,
        preferredQualityId: String = "720p"
    ): Int {
        var addedCount = 0
        for (entry in entries) {
            if (!entry.selected) continue
            val existing = dao.findDuplicate(entry.url, preferredQualityId)
            if (existing != null) continue

            val id = UUID.randomUUID().toString()
            val isAudio = preferredQualityId.startsWith("audio")
            val ext = if (preferredQualityId == "audio_mp3") "mp3" else if (isAudio) "m4a" else "mp4"
            val fileName = StorageHelper.sanitizeFileName(entry.title, ext)
            val partFile = StorageHelper.prepareTempPartFile(appContext, id, fileName)
            val fmtExpr = when (preferredQualityId) {
                "2160p" -> "bestvideo[height<=2160]+bestaudio/best[height<=2160]"
                "1440p" -> "bestvideo[height<=1440]+bestaudio/best[height<=1440]"
                "1080p" -> "bestvideo[height<=1080]+bestaudio/best[height<=1080]"
                "720p" -> "bestvideo[height<=720]+bestaudio/best[height<=720]"
                "480p" -> "bestvideo[height<=480]+bestaudio/best[height<=480]"
                "360p" -> "bestvideo[height<=360]+bestaudio/best[height<=360]"
                "audio_m4a", "audio_mp3" -> "bestaudio/best"
                else -> "bestvideo[height<=720]+bestaudio/best"
            }

            val entity = DownloadEntity(
                id = id,
                originalUrl = entry.url,
                resolvedUrl = entry.url,
                title = entry.title,
                thumbnailUrl = entry.thumbnailUrl,
                platform = "قائمة تشغيل",
                qualityId = preferredQualityId,
                qualityLabel = if (isAudio) "صوت فقط ($ext)" else preferredQualityId,
                formatId = fmtExpr,
                isAudioOnly = isAudio,
                isHlsOrDash = true,
                needsMux = true,
                fileName = fileName,
                mimeType = if (isAudio) "audio/mp4" else "video/mp4",
                tempPartPath = partFile.absolutePath,
                outputDisplayPath = "Download/HawasElDirasa/${if (isAudio) "Audio" else "Video"}/$fileName",
                status = if (shouldWaitForWifiOrNetwork()) DownloadStatus.WAITING_NETWORK else DownloadStatus.QUEUED,
                durationSeconds = entry.durationSeconds
            )
            dao.upsertDownload(entity)
            addedCount++
        }
        if (addedCount > 0) {
            DownloadForegroundService.startOrRefresh(appContext)
            scheduleNextQueued()
        }
        return addedCount
    }

    fun pauseDownload(id: String) {
        scope.launch {
            mutexFor(id).withLock {
                pausedByUserIds.add(id)
                pausedByNetworkIds.remove(id)
                activeJobs[id]?.cancel()
                ytDlpRunner.cancelOrPauseProcess(id)
                val current = dao.getDownloadById(id)
                if (current != null && current.status != DownloadStatus.COMPLETED && current.status != DownloadStatus.CANCELED) {
                    dao.updateStatus(id, DownloadStatus.PAUSED, threads = 0)
                }
                clearLiveStat(id)
            }
            scheduleNextQueued()
        }
    }

    fun pauseAllActive() {
        scope.launch {
            val active = dao.getCurrentlyActiveDownloads() + dao.getQueuedDownloads()
            active.forEach { item ->
                pausedByUserIds.add(item.id)
                activeJobs[item.id]?.cancel()
                ytDlpRunner.cancelOrPauseProcess(item.id)
                dao.updateStatus(item.id, DownloadStatus.PAUSED, threads = 0)
                clearLiveStat(item.id)
            }
        }
    }

    fun resumeDownload(id: String) {
        scope.launch {
            mutexFor(id).withLock {
                pausedByUserIds.remove(id)
                pausedByNetworkIds.remove(id)
                canceledByUserIds.remove(id)
                val item = dao.getDownloadById(id) ?: return@withLock
                if (item.status == DownloadStatus.COMPLETED) return@withLock
                val nextStatus = if (shouldWaitForWifiOrNetwork()) {
                    DownloadStatus.WAITING_NETWORK
                } else {
                    DownloadStatus.QUEUED
                }
                dao.updateStatus(id, nextStatus, threads = 0, errorMsg = null, diagnostic = null)
            }
            DownloadForegroundService.startOrRefresh(appContext)
            scheduleNextQueued()
        }
    }

    fun resumeAllInterrupted() {
        scope.launch {
            val all = dao.getResumableDownloads()
            for (item in all) {
                pausedByUserIds.remove(item.id)
                pausedByNetworkIds.remove(item.id)
                if (item.status == DownloadStatus.WAITING_NETWORK && !shouldWaitForWifiOrNetwork()) {
                    dao.updateStatus(item.id, DownloadStatus.QUEUED)
                }
            }
            if (all.isNotEmpty()) {
                DownloadForegroundService.startOrRefresh(appContext)
                scheduleNextQueued()
            }
        }
    }

    suspend fun resumeInterruptedOnStartup() {
        val resumable = dao.getResumableDownloads()
        if (resumable.isEmpty()) return
        for (item in resumable) {
            if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.EXTRACTING || item.status == DownloadStatus.MERGING) {
                val targetStatus = if (shouldWaitForWifiOrNetwork()) DownloadStatus.WAITING_NETWORK else DownloadStatus.QUEUED
                dao.updateStatus(item.id, targetStatus)
            }
        }
        DownloadForegroundService.startOrRefresh(appContext)
        scheduleNextQueued()
    }

    fun retryDownload(id: String) = resumeDownload(id)

    fun cancelDownload(id: String, deleteTemp: Boolean = true) {
        scope.launch {
            mutexFor(id).withLock {
                canceledByUserIds.add(id)
                pausedByUserIds.remove(id)
                pausedByNetworkIds.remove(id)
                activeJobs[id]?.cancel()
                ytDlpRunner.cancelOrPauseProcess(id)
                val current = dao.getDownloadById(id)
                dao.updateStatus(id, DownloadStatus.CANCELED, threads = 0)
                dao.deleteChunksForDownload(id)
                clearLiveStat(id)
                if (deleteTemp && current != null) {
                    runCatching { File(current.tempPartPath).delete() }
                    runCatching { cleanYtDlpPartials(id) }
                }
            }
            scheduleNextQueued()
        }
    }

    fun cancelAllActive() {
        scope.launch {
            val list = dao.getCurrentlyActiveDownloads() + dao.getQueuedDownloads() + dao.getWaitingNetworkDownloads()
            list.forEach { cancelDownload(it.id, deleteTemp = true) }
        }
    }

    fun deleteDownloadRecord(id: String) {
        scope.launch {
            mutexFor(id).withLock {
                canceledByUserIds.add(id)
                activeJobs[id]?.cancel()
                ytDlpRunner.cancelOrPauseProcess(id)
                val current = dao.getDownloadById(id)
                dao.deleteDownload(id)
                clearLiveStat(id)
                if (current != null) {
                    runCatching { File(current.tempPartPath).delete() }
                    runCatching { cleanYtDlpPartials(id) }
                }
            }
        }
    }

    fun onNetworkLost() {
        scope.launch {
            val active = dao.getCurrentlyActiveDownloads() + dao.getQueuedDownloads()
            active.forEach { item ->
                pausedByNetworkIds.add(item.id)
                activeJobs[item.id]?.cancel()
                ytDlpRunner.cancelOrPauseProcess(item.id)
                dao.updateStatus(item.id, DownloadStatus.WAITING_NETWORK, threads = 0)
                clearLiveStat(item.id)
            }
        }
    }

    fun onNetworkRestored(isWifi: Boolean) {
        scope.launch {
            val settings = settingsRepo.getSettings()
            if (settings.wifiOnly && !isWifi) return@launch
            val waiting = dao.getWaitingNetworkDownloads()
            if (waiting.isEmpty()) return@launch
            waiting.forEach { item ->
                pausedByNetworkIds.remove(item.id)
                dao.updateStatus(item.id, DownloadStatus.QUEUED)
            }
            DownloadForegroundService.startOrRefresh(appContext)
            scheduleNextQueued()
        }
    }

    fun onServiceTimeoutGracefulPause() {
        scope.launch {
            val active = dao.getCurrentlyActiveDownloads()
            active.forEach { item ->
                pausedByNetworkIds.add(item.id)
                activeJobs[item.id]?.cancel()
                ytDlpRunner.cancelOrPauseProcess(item.id)
                dao.updateStatus(item.id, DownloadStatus.QUEUED, threads = 0)
                clearLiveStat(item.id)
            }
        }
    }

    fun notifyConnectivityMaybeChanged() {
        scope.launch {
            if (!shouldWaitForWifiOrNetwork()) {
                val waiting = dao.getWaitingNetworkDownloads()
                waiting.forEach { dao.updateStatus(it.id, DownloadStatus.QUEUED) }
                if (waiting.isNotEmpty()) {
                    DownloadForegroundService.startOrRefresh(appContext)
                    scheduleNextQueued()
                }
            }
        }
    }

    suspend fun cleanOrphanTempFiles(): Int {
        val nonTerminal = dao.getNonTerminalDownloadIds().toSet()
        return StorageHelper.cleanupOrphanPartFiles(appContext, nonTerminal)
    }

    private suspend fun shouldWaitForWifiOrNetwork(): Boolean {
        if (!networkMonitor.isNetworkConnected()) return true
        val settings = settingsRepo.getSettings()
        return settings.wifiOnly && !networkMonitor.isWifiConnected()
    }

    private fun scheduleNextQueued() {
        scope.launch {
            schedulerMutex.withLock {
                val settings = settingsRepo.getSettings()
                if (shouldWaitForWifiOrNetwork()) {
                    val queued = dao.getQueuedDownloads()
                    queued.forEach { dao.updateStatus(it.id, DownloadStatus.WAITING_NETWORK) }
                    return@withLock
                }

                val currentRunning = activeJobs.count { it.value.isActive }
                val freeSlots = (settings.maxConcurrent - currentRunning).coerceAtLeast(0)
                if (freeSlots <= 0) return@withLock

                val queuedList = dao.getQueuedDownloads().take(freeSlots)
                for (item in queuedList) {
                    if (activeJobs[item.id]?.isActive == true) continue
                    val job = scope.launch {
                        runDownloadTask(item.id)
                    }
                    activeJobs[item.id] = job
                }
            }
        }
    }

    private suspend fun runDownloadTask(id: String) {
        try {
            val entity = dao.getDownloadById(id) ?: return
            val settings = settingsRepo.getSettings()

            if (pausedByUserIds.contains(id) || canceledByUserIds.contains(id)) return

            if (shouldWaitForWifiOrNetwork()) {
                dao.updateStatus(id, DownloadStatus.WAITING_NETWORK)
                return
            }

            dao.updateStatus(
                id,
                DownloadStatus.DOWNLOADING,
                threads = if (entity.isHlsOrDash || entity.needsMux) 16 else settings.chunkCount
            )

            val producedFile: File = if (!entity.isHlsOrDash && !entity.needsMux &&
                entity.resolvedUrl.startsWith("http", true)
            ) {
                val headers = decodeHeaders(entity.headersJson)
                chunkDownloader.download(
                    entity = entity,
                    headers = headers,
                    requestedChunks = settings.chunkCount,
                    speedLimitKBps = settings.speedLimitKBps,
                    onRefreshDirectUrl = {
                        refreshDirectDownloadUrl(entity)
                    },
                    onProgress = { _, _, snap ->
                        _liveStatsFlow.update { it + (id to snap) }
                    }
                )
            } else {
                var lastDbSave = 0L
                ytDlpRunner.executeDownload(
                    entity = entity,
                    speedLimitKBps = settings.speedLimitKBps,
                    onMerging = {
                        dao.updateStatus(id, DownloadStatus.MERGING, threads = 1)
                    },
                    onProgress = { done, total, snap ->
                        _liveStatsFlow.update { it + (id to snap) }
                        val now = System.currentTimeMillis()
                        if (now - lastDbSave >= 1000L) {
                            lastDbSave = now
                            scope.launch {
                                dao.updateProgress(id, done, total, snap.activeThreads)
                            }
                        }
                    }
                )
            }

            if (pausedByUserIds.contains(id) || pausedByNetworkIds.contains(id) || canceledByUserIds.contains(id)) {
                return
            }

            dao.updateStatus(id, DownloadStatus.MERGING, threads = 1)
            val finalLength = producedFile.length()
            val moveResult = StorageHelper.moveTempFileToPublicDownloads(
                context = appContext,
                sourceFile = producedFile,
                desiredFileName = entity.fileName,
                mimeType = entity.mimeType,
                isAudioOnly = entity.isAudioOnly
            )

            moveResult.fold(
                onSuccess = { (uriStr, displayPath) ->
                    dao.deleteChunksForDownload(id)
                    dao.markCompleted(
                        id = id,
                        finalBytes = finalLength,
                        outputUri = uriStr,
                        displayPath = displayPath
                    )
                    clearLiveStat(id)
                },
                onFailure = { err ->
                    val (friendly, diag) = LinkExtractor.mapThrowableToFriendlyError(err)
                    dao.updateStatus(id, DownloadStatus.FAILED, threads = 0, errorMsg = friendly, diagnostic = diag)
                    clearLiveStat(id)
                }
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            if (!pausedByUserIds.contains(id) && !pausedByNetworkIds.contains(id) && !canceledByUserIds.contains(id)) {
                val (friendly, diag) = LinkExtractor.mapThrowableToFriendlyError(t)
                dao.updateStatus(
                    id = id,
                    status = DownloadStatus.FAILED,
                    threads = 0,
                    errorMsg = friendly,
                    diagnostic = diag
                )
            }
            clearLiveStat(id)
        } finally {
            activeJobs.remove(id)
            clearLiveStat(id)
            scheduleNextQueued()
        }
    }

    private suspend fun refreshDirectDownloadUrl(entity: DownloadEntity): Pair<String, Map<String, String>>? {
        val result = LinkExtractor.extract(
            context = appContext,
            rawInputUrl = entity.originalUrl,
            settingsRepo = settingsRepo,
            checkPlaylist = false
        )
        if (result !is ExtractionResult.SingleVideo) return null
        val matched = result.qualities.firstOrNull { it.id == entity.qualityId }
            ?: result.qualities.firstOrNull { !it.isAudioOnly }
            ?: return null
        if (matched.directUrl.isBlank()) return null
        return matched.directUrl to (result.headers + matched.headers)
    }

    private fun cleanYtDlpPartials(downloadId: String) {
        val dir = StorageHelper.getTempPartDir(appContext)
        dir.listFiles()?.forEach { file ->
            if (file.name.startsWith("yt_$downloadId")) {
                file.delete()
            }
        }
    }

    private fun clearLiveStat(id: String) {
        _liveStatsFlow.update { it - id }
    }

    private fun encodeHeaders(headers: Map<String, String>): String? {
        if (headers.isEmpty()) return null
        return JSONObject(headers).toString()
    }

    private fun decodeHeaders(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            buildMap {
                obj.keys().forEach { key ->
                    put(key, obj.optString(key))
                }
            }
        }.getOrDefault(emptyMap())
    }

    companion object {
        @Volatile
        private var INSTANCE: SmartDownloadManager? = null

        fun getInstance(context: Context): SmartDownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SmartDownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
