package com.example.download

import android.content.Context
import android.os.Build
import com.example.download.core.data.DataStoreEngineUpdateStore
import com.example.download.core.domain.AddDownloadUseCase
import com.example.download.core.domain.CancelDownloadUseCase
import com.example.download.core.domain.DownloadRepository
import com.example.download.core.domain.GetDownloadStatusUseCase
import com.example.download.core.domain.GetMediaFormatsUseCase
import com.example.download.core.domain.PauseDownloadUseCase
import com.example.download.core.domain.ResolveMediaUseCase
import com.example.download.core.domain.ResumeDownloadUseCase
import com.example.download.core.domain.RetryDownloadUseCase
import com.example.download.core.domain.StartDownloadUseCase
import com.example.download.core.domain.UpdateVideoEngineUseCase
import com.example.download.core.media.MediaMergeEngine
import com.example.download.core.model.DiagnosticRedactor
import com.example.download.core.model.DownloadError
import com.example.download.core.model.DownloadErrorMapper
import com.example.download.core.model.EngineDownloadState
import com.example.download.core.resolver.AndroidWebViewSniffGateway
import com.example.download.core.resolver.AndroidYtDlpGateway
import com.example.download.core.resolver.UnifiedMediaResolver
import com.example.download.core.updater.EngineCompatibilityProfile
import com.example.download.core.updater.EngineUpdateManager
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
import com.example.download.service.DownloadNotificationManager
import com.example.download.service.NetworkAutoResumeMonitor
import com.example.download.core.resolver.ResolverHttpUtils
import com.hawas.study.rafid.worker.SerialFFmpegMuxerWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

sealed class EnqueueOutcome {
    data class Enqueued(val downloadId: String) : EnqueueOutcome()
    data class Duplicate(val existingTitle: String) : EnqueueOutcome()
    data class InsufficientSpace(val requiredBytes: Long) : EnqueueOutcome()
}

class SmartDownloadManager private constructor(
    private val appContext: Context
) : DownloadRepository {

    private val db = DownloadDatabase.getInstance(appContext)
    private val dao = db.downloadDao()
    val settingsRepo = DownloadSettingsRepo(appContext)
    private val chunkDownloader = ParallelChunkDownloader(dao)
    private val ytDlpRunner = YtDlpRunner(appContext, settingsRepo)
    private val networkMonitor = NetworkAutoResumeMonitor(appContext)

    private val resolverClient = OkHttpClient.Builder()
        .dispatcher(
            Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 24
            }
        )
        .connectionPool(ConnectionPool(24, 5, TimeUnit.MINUTES))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val mediaResolver = UnifiedMediaResolver(
        httpClient = resolverClient,
        ytDlpGateway = AndroidYtDlpGateway(appContext),
        webViewSniffGateway = AndroidWebViewSniffGateway(appContext)
    )

    val engineUpdateManager = EngineUpdateManager(
        engineWorkDir = File(appContext.filesDir, "hawas_video_engine"),
        stateStore = DataStoreEngineUpdateStore(appContext),
        trustedKeys = emptyMap(), // Configured with ECDSA SPKI keyId when signed manifest channel is active
        compatibility = EngineCompatibilityProfile(
            androidApi = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS.toList()
        )
    )

    // Exposed Domain UseCases for clean architecture wiring
    val resolveMediaUseCase = ResolveMediaUseCase(mediaResolver)
    val getMediaFormatsUseCase = GetMediaFormatsUseCase(mediaResolver)
    val addDownloadUseCase = AddDownloadUseCase(this)
    val startDownloadUseCase = StartDownloadUseCase(this)
    val pauseDownloadUseCase = PauseDownloadUseCase(this)
    val resumeDownloadUseCase = ResumeDownloadUseCase(this)
    val cancelDownloadUseCase = CancelDownloadUseCase(this)
    val retryDownloadUseCase = RetryDownloadUseCase(this)
    val getDownloadStatusUseCase = GetDownloadStatusUseCase(this)
    val updateVideoEngineUseCase = UpdateVideoEngineUseCase(engineUpdateManager)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val schedulerMutex = Mutex()
    private val perDownloadMutexes = ConcurrentHashMap<String, Mutex>()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pausedByUserIds = ConcurrentHashMap.newKeySet<String>()
    private val pausedByNetworkIds = ConcurrentHashMap.newKeySet<String>()
    private val canceledByUserIds = ConcurrentHashMap.newKeySet<String>()
    private val preExtractingIds = ConcurrentHashMap.newKeySet<String>()

    private val _liveStatsFlow = MutableStateFlow<Map<String, LiveProgressSnapshot>>(emptyMap())
    val liveStatsFlow: StateFlow<Map<String, LiveProgressSnapshot>> = _liveStatsFlow.asStateFlow()

    val allDownloadsFlow: Flow<List<DownloadEntity>> = dao.observeAllDownloads()
    val settingsFlow: Flow<DownloadSettings> = settingsRepo.settingsFlow

    init {
        networkMonitor.register()
    }

    private fun mutexFor(id: String): Mutex =
        perDownloadMutexes.getOrPut(id) { Mutex() }

    override fun observeAll(): Flow<List<DownloadEntity>> = allDownloadsFlow

    override suspend fun getById(id: String): DownloadEntity? = dao.getDownloadById(id)

    suspend fun extractLink(url: String, checkPlaylist: Boolean = true): ExtractionResult {
        return LinkExtractor.extract(
            context = appContext,
            rawInputUrl = url,
            settingsRepo = settingsRepo,
            checkPlaylist = checkPlaylist
        )
    }

    override suspend fun enqueueSingle(
        video: ExtractionResult.SingleVideo,
        quality: MediaQualityOption
    ): EnqueueOutcome = enqueueSingleDownload(video, quality)

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
            quality.ext == "pdf" -> "application/pdf"
            quality.ext in setOf("jpg", "jpeg", "png", "webp") -> "image/${quality.ext}"
            else -> "video/mp4"
        }

        val waitingNet = shouldWaitForWifiOrNetwork()
        val initialStatus = if (waitingNet) DownloadStatus.WAITING_NETWORK else DownloadStatus.QUEUED
        val initialEngineState = if (waitingNet) EngineDownloadState.PAUSED else EngineDownloadState.READY

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
            engineState = initialEngineState.name,
            durationSeconds = video.durationSeconds
        )

        mutexFor(id).withLock {
            dao.upsertDownload(entity)
        }

        DownloadForegroundService.startQueue(appContext, id)
        scheduleNextQueued()
        return EnqueueOutcome.Enqueued(id)
    }

    suspend fun enqueuePlaylistBatch(
        entries: List<PlaylistEntry>,
        preferredQualityId: String = "720p"
    ): Int {
        var addedCount = 0
        val baseTimestamp = System.currentTimeMillis()
        entries.forEachIndexed { idx, entry ->
            if (!entry.selected) return@forEachIndexed
            val existing = dao.findDuplicate(entry.url, preferredQualityId)
            if (existing != null) return@forEachIndexed

            val id = UUID.randomUUID().toString()
            val isAudio = preferredQualityId.startsWith("audio")
            val ext = if (preferredQualityId == "audio_mp3") "mp3" else if (isAudio) "m4a" else "mp4"
            val fileName = StorageHelper.sanitizeFileName(entry.title, ext)
            val partFile = StorageHelper.prepareTempPartFile(appContext, id, fileName)
            val fmtExpr = when (preferredQualityId) {
                "2160p" -> "bestvideo[height<=2160][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=2160]+bestaudio/best[height<=2160]/best"
                "1440p" -> "bestvideo[height<=1440][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=1440]+bestaudio/best[height<=1440]/best"
                "1080p" -> "bestvideo[height<=1080][vcodec^=avc1]+bestaudio[ext=m4a]/bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=1080]+bestaudio/best[height<=1080][ext=mp4]/best[height<=1080]/best"
                "720p" -> "bestvideo[height<=720][vcodec^=avc1]+bestaudio[ext=m4a]/bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=720]+bestaudio/best[height<=720][ext=mp4]/best[height<=720]/best"
                "480p" -> "bestvideo[height<=480][vcodec^=avc1]+bestaudio[ext=m4a]/bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=480]+bestaudio/best[height<=480]/best"
                "360p" -> "bestvideo[height<=360][vcodec^=avc1]+bestaudio[ext=m4a]/bestvideo[height<=360][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=360]+bestaudio/best[height<=360]/best"
                "240p" -> "bestvideo[height<=240][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=240]+bestaudio/best[height<=240]/best"
                "144p" -> "bestvideo[height<=144][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=144]+bestaudio/best[height<=144]/best"
                "audio_m4a", "audio_mp3" -> "bestaudio/best"
                else -> "bestvideo[height<=720][vcodec^=avc1]+bestaudio[ext=m4a]/bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=720]+bestaudio/best[height<=720]/best"
            }

            val waitingNet = shouldWaitForWifiOrNetwork()
            val resolvedMime = when {
                preferredQualityId == "audio_mp3" -> "audio/mpeg"
                isAudio -> "audio/mp4"
                else -> "video/mp4"
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
                mimeType = resolvedMime,
                tempPartPath = partFile.absolutePath,
                outputDisplayPath = "Download/HawasElDirasa/${if (isAudio) "Audio" else "Video"}/$fileName",
                status = if (waitingNet) DownloadStatus.WAITING_NETWORK else DownloadStatus.QUEUED,
                engineState = if (waitingNet) EngineDownloadState.PAUSED.name else EngineDownloadState.QUEUED.name,
                durationSeconds = entry.durationSeconds,
                createdAt = baseTimestamp + idx
            )
            dao.upsertDownload(entity)
            addedCount++
        }
        if (addedCount > 0) {
            DownloadForegroundService.startQueue(appContext, null)
            scheduleNextQueued()
        }
        return addedCount
    }

    override suspend fun startOrSchedule(id: String) {
        DownloadForegroundService.startQueue(appContext, id)
        scheduleNextQueued()
    }

    override suspend fun pause(id: String) {
        mutexFor(id).withLock {
            pausedByUserIds.add(id)
            pausedByNetworkIds.remove(id)
            dao.updateStatus(
                id = id,
                status = DownloadStatus.PAUSED,
                threads = 0,
                engineState = EngineDownloadState.PAUSING.name
            )
            activeJobs[id]?.cancel()
            ytDlpRunner.cancelOrPauseProcess(id)
            val current = dao.getDownloadById(id)
            if (current != null && current.status != DownloadStatus.COMPLETED && current.status != DownloadStatus.CANCELED) {
                dao.updateStatus(
                    id = id,
                    status = DownloadStatus.PAUSED,
                    threads = 0,
                    engineState = EngineDownloadState.PAUSED.name
                )
            }
            clearLiveStat(id)
        }
        scheduleNextQueued()
    }

    override suspend fun resume(id: String) {
        mutexFor(id).withLock {
            pausedByUserIds.remove(id)
            pausedByNetworkIds.remove(id)
            canceledByUserIds.remove(id)
            val item = dao.getDownloadById(id) ?: return@withLock
            if (item.status == DownloadStatus.COMPLETED) return@withLock
            val waitingNet = shouldWaitForWifiOrNetwork()
            val nextStatus = if (waitingNet) DownloadStatus.WAITING_NETWORK else DownloadStatus.QUEUED
            val nextEngine = if (waitingNet) EngineDownloadState.PAUSED else EngineDownloadState.QUEUED
            dao.updateStatus(
                id = id,
                status = nextStatus,
                threads = 0,
                errorMsg = null,
                diagnostic = null,
                engineState = nextEngine.name,
                errorCode = null
            )
        }
        DownloadForegroundService.startQueue(appContext, id)
        scheduleNextQueued()
    }

    override suspend fun cancel(id: String, deleteTemp: Boolean) {
        mutexFor(id).withLock {
            canceledByUserIds.add(id)
            pausedByUserIds.remove(id)
            pausedByNetworkIds.remove(id)
            activeJobs[id]?.cancel()
            ytDlpRunner.cancelOrPauseProcess(id)
            val current = dao.getDownloadById(id)
            dao.updateStatus(
                id = id,
                status = DownloadStatus.CANCELED,
                threads = 0,
                engineState = EngineDownloadState.CANCELLED.name
            )
            dao.deleteChunksForDownload(id)
            clearLiveStat(id)
            if (deleteTemp && current != null) {
                runCatching { File(current.tempPartPath).delete() }
                runCatching { cleanYtDlpPartials(id) }
            }
        }
        scheduleNextQueued()
    }

    override suspend fun retry(id: String) {
        mutexFor(id).withLock {
            pausedByUserIds.remove(id)
            pausedByNetworkIds.remove(id)
            canceledByUserIds.remove(id)
            val item = dao.getDownloadById(id) ?: return@withLock
            if (item.status == DownloadStatus.COMPLETED) return@withLock
            dao.updateStatus(
                id = id,
                status = DownloadStatus.QUEUED,
                threads = 0,
                errorMsg = null,
                diagnostic = null,
                engineState = EngineDownloadState.RETRYING.name,
                errorCode = null
            )
        }
        DownloadForegroundService.startQueue(appContext, id)
        scheduleNextQueued()
    }

    fun pauseDownload(id: String) {
        scope.launch { pauseDownloadUseCase(id) }
    }

    fun pauseAllActive() {
        scope.launch {
            val active = dao.getCurrentlyActiveDownloads() + dao.getQueuedDownloads()
            active.forEach { item ->
                pausedByUserIds.add(item.id)
                activeJobs[item.id]?.cancel()
                ytDlpRunner.cancelOrPauseProcess(item.id)
                dao.updateStatus(
                    id = item.id,
                    status = DownloadStatus.PAUSED,
                    threads = 0,
                    engineState = EngineDownloadState.PAUSED.name
                )
                clearLiveStat(item.id)
            }
        }
    }

    fun resumeDownload(id: String) {
        scope.launch { resumeDownloadUseCase(id) }
    }

    fun resumeAllInterrupted() {
        scope.launch {
            val all = dao.getResumableDownloads()
            for (item in all) {
                pausedByUserIds.remove(item.id)
                pausedByNetworkIds.remove(item.id)
                if (item.status == DownloadStatus.WAITING_NETWORK && !shouldWaitForWifiOrNetwork()) {
                    dao.updateStatus(item.id, DownloadStatus.QUEUED, engineState = EngineDownloadState.QUEUED.name)
                }
            }
            if (all.isNotEmpty()) {
                DownloadForegroundService.startQueue(appContext, all.first().id)
                scheduleNextQueued()
            }
        }
    }

    suspend fun resumeInterruptedOnStartup() {
        val resumable = dao.getResumableDownloads()
        if (resumable.isEmpty()) return
        for (item in resumable) {
            if (item.status == DownloadStatus.DOWNLOADING ||
                item.status == DownloadStatus.EXTRACTING ||
                item.status == DownloadStatus.MERGING
            ) {
                val waitingNet = shouldWaitForWifiOrNetwork()
                val targetStatus = if (waitingNet) DownloadStatus.WAITING_NETWORK else DownloadStatus.QUEUED
                val targetEngine = if (waitingNet) EngineDownloadState.PAUSED else EngineDownloadState.QUEUED
                dao.updateStatus(item.id, targetStatus, engineState = targetEngine.name)
            }
        }
        DownloadForegroundService.startQueue(appContext, resumable.first().id)
        scheduleNextQueued()
    }

    fun retryDownload(id: String) {
        scope.launch { retryDownloadUseCase(id) }
    }

    fun cancelDownload(id: String, deleteTemp: Boolean = true) {
        scope.launch { cancelDownloadUseCase(id, deleteTemp) }
    }

    fun cancelAllActive() {
        scope.launch {
            val list = dao.getCurrentlyActiveDownloads() + dao.getQueuedDownloads() + dao.getWaitingNetworkDownloads()
            list.forEach { cancel(it.id, deleteTemp = true) }
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
                dao.updateStatus(
                    id = item.id,
                    status = DownloadStatus.WAITING_NETWORK,
                    threads = 0,
                    engineState = EngineDownloadState.PAUSED.name,
                    errorCode = DownloadError.NetworkUnavailable().code
                )
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
                dao.updateStatus(item.id, DownloadStatus.QUEUED, engineState = EngineDownloadState.QUEUED.name, errorCode = null)
            }
            DownloadForegroundService.startQueue(appContext, waiting.first().id)
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
                dao.updateStatus(item.id, DownloadStatus.QUEUED, threads = 0, engineState = EngineDownloadState.QUEUED.name)
                clearLiveStat(item.id)
            }
        }
    }

    fun notifyConnectivityMaybeChanged() {
        scope.launch {
            if (!shouldWaitForWifiOrNetwork()) {
                val waiting = dao.getWaitingNetworkDownloads()
                waiting.forEach {
                    dao.updateStatus(it.id, DownloadStatus.QUEUED, engineState = EngineDownloadState.QUEUED.name, errorCode = null)
                }
                if (waiting.isNotEmpty()) {
                    DownloadForegroundService.startQueue(appContext, waiting.first().id)
                    scheduleNextQueued()
                }
            }
        }
    }

    fun ensureQueueRunning() {
        scheduleNextQueued()
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
            var toPreExtract: List<DownloadEntity> = emptyList()
            schedulerMutex.withLock {
                val settings = settingsRepo.getSettings()
                if (shouldWaitForWifiOrNetwork()) {
                    val queued = dao.getQueuedDownloads()
                    queued.forEach {
                        dao.updateStatus(it.id, DownloadStatus.WAITING_NETWORK, engineState = EngineDownloadState.PAUSED.name)
                    }
                    return@withLock
                }

                val allQueued = dao.getQueuedDownloads()
                val currentRunning = activeJobs.count { it.value.isActive }
                val freeSlots = (settings.maxConcurrent - currentRunning).coerceAtLeast(0)
                if (freeSlots > 0) {
                    val queuedList = allQueued.take(freeSlots)
                    for (item in queuedList) {
                        if (activeJobs[item.id]?.isActive == true) continue
                        val job = scope.launch {
                            runDownloadTask(item.id)
                        }
                        activeJobs[item.id] = job
                    }
                }
                toPreExtract = allQueued
                    .drop(freeSlots)
                    .filter {
                        it.resolvedUrl == it.originalUrl &&
                            !ResolverHttpUtils.isManifestUrl(it.originalUrl) &&
                            !preExtractingIds.contains(it.id)
                    }
                    .take(2)
            }
            toPreExtract.forEach { candidate ->
                if (preExtractingIds.add(candidate.id)) {
                    scope.launch {
                        try {
                            preExtractSmartQueuedItem(candidate)
                        } finally {
                            preExtractingIds.remove(candidate.id)
                        }
                    }
                }
            }
        }
    }

    private suspend fun preExtractSmartQueuedItem(entity: DownloadEntity) {
        val current = dao.getDownloadById(entity.id) ?: return
        if (current.status != DownloadStatus.QUEUED || current.resolvedUrl != current.originalUrl) return
        val extracted = extractLink(current.originalUrl, checkPlaylist = false)
        if (extracted !is ExtractionResult.SingleVideo) return

        val targetMaxHeight = Regex("""(\d{3,4})p""").find(current.qualityId)
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
        val chosen = if (current.isAudioOnly) {
            extracted.qualities.firstOrNull { it.isAudioOnly && it.ext.equals(current.fileName.substringAfterLast('.', "m4a"), true) }
                ?: extracted.qualities.firstOrNull { it.isAudioOnly }
        } else {
            val videoCandidates = extracted.qualities.filter { !it.isAudioOnly }
            if (targetMaxHeight != null) {
                val withinCap = videoCandidates.filter { it.resolutionHeight in 1..targetMaxHeight }
                val bestHeight = withinCap.maxOfOrNull { it.resolutionHeight }
                if (bestHeight != null) {
                    withinCap.filter { it.resolutionHeight == bestHeight }
                        .let { atBest ->
                            atBest.firstOrNull { it.hasAudio && it.ext.equals("mp4", true) }
                                ?: atBest.firstOrNull { it.ext.equals("mp4", true) }
                                ?: atBest.firstOrNull()
                        }
                } else {
                    videoCandidates.minByOrNull { if (it.resolutionHeight > 0) it.resolutionHeight else Int.MAX_VALUE }
                }
            } else {
                videoCandidates.firstOrNull()
            }
        } ?: return

        if (chosen.directUrl.isBlank()) return
        val mergedHeaders = extracted.headers + chosen.headers
        dao.updatePreExtractedStreamsIfQueued(
            id = current.id,
            resolvedUrl = chosen.directUrl,
            audioUrl = if (current.isAudioOnly) null else chosen.audioUrl,
            totalBytes = chosen.estimatedBytes,
            headersJson = encodeHeaders(mergedHeaders),
            formatId = chosen.formatId ?: current.formatId,
            qualityLabel = chosen.labelAr,
            isHlsOrDash = chosen.isHlsOrDash,
            needsMux = chosen.needsMux || !chosen.audioUrl.isNullOrBlank()
        )
    }

    private suspend fun runDownloadTask(id: String) {
        var loadedEntity: DownloadEntity? = null
        try {
            val entity = dao.getDownloadById(id) ?: return
            loadedEntity = entity
            val settings = settingsRepo.getSettings()

            if (pausedByUserIds.contains(id) || canceledByUserIds.contains(id)) return

            if (shouldWaitForWifiOrNetwork()) {
                dao.updateStatus(id, DownloadStatus.WAITING_NETWORK, engineState = EngineDownloadState.PAUSED.name)
                return
            }

            val useSegmentedHttp = !entity.isHlsOrDash && !entity.needsMux &&
                entity.resolvedUrl.startsWith("http", true)
            val useParallelMuxHttp = !entity.isHlsOrDash && entity.needsMux &&
                !entity.isAudioOnly &&
                entity.resolvedUrl.startsWith("http", true) &&
                !entity.audioUrl.isNullOrBlank() &&
                entity.audioUrl.startsWith("http", true)

            val activeDownloadPhase = if (entity.isAudioOnly) "DOWNLOADING_AUDIO" else "DOWNLOADING_VIDEO"
            dao.updateStatus(
                id = id,
                status = DownloadStatus.DOWNLOADING,
                threads = if (useSegmentedHttp || useParallelMuxHttp) settings.chunkCount else 8,
                engineState = activeDownloadPhase,
                errorCode = null
            )

            val producedFile: File = if (useSegmentedHttp) {
                val headers = ResolverHttpUtils.defaultHeadersForUrl(
                    entity.resolvedUrl,
                    decodeHeaders(entity.headersJson)
                )
                chunkDownloader.download(
                    entity = entity,
                    headers = headers,
                    requestedChunks = settings.chunkCount,
                    speedLimitKBps = settings.speedLimitKBps,
                    onRefreshDirectUrl = {
                        dao.updateEngineState(id, EngineDownloadState.RESOLVING.name)
                        val refreshed = refreshDirectDownloadUrl(entity)
                        if (refreshed != null) {
                            dao.updateEngineState(id, activeDownloadPhase)
                        }
                        refreshed
                    },
                    onProgress = { _, _, snap ->
                        _liveStatsFlow.update { it + (id to snap) }
                    }
                )
            } else if (useParallelMuxHttp) {
                val decodedHeaders = decodeHeaders(entity.headersJson)
                val headers = ResolverHttpUtils.defaultHeadersForUrl(entity.resolvedUrl, decodedHeaders)
                val audioHeaders = ResolverHttpUtils.defaultHeadersForUrl(entity.audioUrl!!, decodedHeaders)
                val audioTempFile = File(File(entity.tempPartPath).parentFile, "${id}_audio.m4a.part")
                val muxOutFile = File(File(entity.tempPartPath).parentFile, "${id}_muxed.${entity.fileName.substringAfterLast('.', "mp4")}")
                val vDone = AtomicLong(0L)
                val vTotal = AtomicLong(maxOf(0L, entity.totalBytes))
                val vSpeed = AtomicLong(0L)
                val aDone = AtomicLong(0L)
                val aTotal = AtomicLong(0L)
                val aSpeed = AtomicLong(0L)
                val lastDbSave = AtomicLong(0L)

                fun publishCombined() {
                    val done = vDone.get() + aDone.get()
                    val total = (vTotal.get() + aTotal.get()).coerceAtLeast(done)
                    val speed = vSpeed.get() + aSpeed.get()
                    val eta = if (speed > 0L && total > done) (total - done) / speed else -1L
                    _liveStatsFlow.update { it + (id to LiveProgressSnapshot(speed, eta, settings.chunkCount)) }
                    val now = System.currentTimeMillis()
                    val prev = lastDbSave.get()
                    if (now - prev >= 900L && lastDbSave.compareAndSet(prev, now)) {
                        scope.launch { dao.updateProgress(id, done, total, settings.chunkCount) }
                    }
                }

                val parallelResult = runCatching {
                    val (vidFile, audFile) = coroutineScope {
                        val vJob = async(Dispatchers.IO) {
                            chunkDownloader.downloadSideTrack(
                                downloadId = "${id}_v",
                                url = entity.resolvedUrl,
                                targetFile = File(entity.tempPartPath),
                                expectedMimeType = entity.mimeType,
                                headers = headers,
                                requestedChunks = settings.chunkCount,
                                speedLimitKBps = settings.speedLimitKBps,
                                onRefreshDirectUrl = { refreshDirectDownloadUrl(entity) },
                                onProgress = { done, total, snap ->
                                    vDone.set(done)
                                    if (total > 0L) vTotal.set(total)
                                    vSpeed.set(snap.speedBytesPerSec)
                                    publishCombined()
                                }
                            )
                        }
                        val aJob = async(Dispatchers.IO) {
                            chunkDownloader.downloadSideTrack(
                                downloadId = "${id}_a",
                                url = entity.audioUrl!!,
                                targetFile = audioTempFile,
                                expectedMimeType = "audio/mp4",
                                headers = audioHeaders,
                                requestedChunks = settings.chunkCount,
                                speedLimitKBps = settings.speedLimitKBps,
                                onRefreshDirectUrl = { null },
                                onProgress = { done, total, snap ->
                                    aDone.set(done)
                                    if (total > 0L) aTotal.set(total)
                                    aSpeed.set(snap.speedBytesPerSec)
                                    publishCombined()
                                }
                            )
                        }
                        vJob.await() to aJob.await()
                    }
                    dao.updateStatus(id, DownloadStatus.MERGING, threads = 1, engineState = EngineDownloadState.MERGING.name)
                    val merged = SerialFFmpegMuxerWorker.runSerializedMux(
                        context = appContext,
                        primaryMediaFile = vidFile,
                        secondaryAudioFile = audFile,
                        desiredOutputFile = muxOutFile,
                        desiredExtension = entity.fileName.substringAfterLast('.', "mp4"),
                        audioOnly = false
                    )
                    runCatching { audFile.delete() }
                    runCatching { vidFile.delete() }
                    merged.outputFile
                }

                parallelResult.getOrElse {
                    var lastDbSaveYt = 0L
                    ytDlpRunner.executeDownload(
                        entity = entity,
                        speedLimitKBps = settings.speedLimitKBps,
                        onMerging = {
                            dao.updateStatus(id, DownloadStatus.MERGING, threads = 1, engineState = EngineDownloadState.MERGING.name)
                        },
                        onProgress = { done, total, snap ->
                            _liveStatsFlow.update { it + (id to snap) }
                            val now = System.currentTimeMillis()
                            if (now - lastDbSaveYt >= 900L) {
                                lastDbSaveYt = now
                                scope.launch {
                                    dao.updateProgress(id, done, total, snap.activeThreads)
                                }
                            }
                        }
                    )
                }
            } else {
                var lastDbSave = 0L
                ytDlpRunner.executeDownload(
                    entity = entity,
                    speedLimitKBps = settings.speedLimitKBps,
                    onMerging = {
                        dao.updateStatus(id, DownloadStatus.MERGING, threads = 1, engineState = EngineDownloadState.MERGING.name)
                    },
                    onProgress = { done, total, snap ->
                        _liveStatsFlow.update { it + (id to snap) }
                        val now = System.currentTimeMillis()
                        if (now - lastDbSave >= 900L) {
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

            dao.updateStatus(id, DownloadStatus.MERGING, threads = 1, engineState = EngineDownloadState.VERIFYING.name)
            val finalLength = producedFile.length()
            if (finalLength <= 0L) {
                val emptyErr = DownloadError.IntegrityCheckFailed("Output artifact is empty (0 bytes)")
                dao.updateStatus(
                    id = id,
                    status = DownloadStatus.FAILED,
                    threads = 0,
                    errorMsg = emptyErr.userMessageAr,
                    diagnostic = DiagnosticRedactor.buildStructuredDiagnostic(
                        platform = entity.platform,
                        failStage = "VERIFYING",
                        error = emptyErr,
                        formatId = entity.formatId ?: entity.qualityId,
                        tempPath = producedFile.absolutePath
                    ),
                    engineState = EngineDownloadState.FAILED.name,
                    errorCode = emptyErr.code
                )
                clearLiveStat(id)
                return
            }

            val isImageOrDoc = entity.mimeType.startsWith("image/") || entity.mimeType.contains("pdf", ignoreCase = true)
            val requireAudioForVideo = !isImageOrDoc &&
                (entity.needsMux || entity.platform.contains("تيك توك") || entity.platform.contains("قائمة"))
            val probeReport = MediaMergeEngine.verifyFinalArtifact(
                context = appContext,
                file = producedFile,
                requireVideo = !entity.isAudioOnly && !isImageOrDoc,
                requireAudio = entity.isAudioOnly || requireAudioForVideo,
                isImageOrDocument = isImageOrDoc
            )

            val actualExt = producedFile.extension.lowercase().takeIf { it.isNotBlank() && it != "part" }
                ?: entity.fileName.substringAfterLast('.', "mp4")
            val finalFileName = if (entity.fileName.contains('.')) {
                "${entity.fileName.substringBeforeLast('.')}.$actualExt"
            } else {
                entity.fileName
            }
            val finalMime = if (isImageOrDoc) entity.mimeType else MediaMergeEngine.mimeForExt(actualExt, entity.isAudioOnly)

            val moveResult = StorageHelper.moveTempFileToPublicDownloads(
                context = appContext,
                sourceFile = producedFile,
                desiredFileName = finalFileName,
                mimeType = finalMime,
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
                    DownloadNotificationManager.notifyDownloadCompleted(
                        context = appContext,
                        downloadId = id,
                        source = DownloadNotificationManager.SOURCE_SMART,
                        videoTitle = entity.title,
                        totalBytes = finalLength,
                        mimeType = finalMime,
                        savedUriString = uriStr,
                        displayPath = displayPath,
                        thumbnailUrl = entity.thumbnailUrl
                    )
                },
                onFailure = { err ->
                    val mapped = DownloadErrorMapper.fromThrowable(err, entity.platform)
                    dao.updateStatus(
                        id = id,
                        status = DownloadStatus.FAILED,
                        threads = 0,
                        errorMsg = mapped.userMessageAr,
                        diagnostic = DiagnosticRedactor.buildStructuredDiagnostic(
                            platform = entity.platform,
                            failStage = "STORAGE",
                            error = mapped,
                            formatId = entity.formatId ?: entity.qualityId,
                            tempPath = producedFile.absolutePath,
                            ffprobeSummary = probeReport.toSummaryString()
                        ),
                        engineState = EngineDownloadState.FAILED.name,
                        errorCode = mapped.code
                    )
                    clearLiveStat(id)
                }
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            if (!pausedByUserIds.contains(id) && !pausedByNetworkIds.contains(id) && !canceledByUserIds.contains(id)) {
                val currentEntity = dao.getDownloadById(id) ?: loadedEntity
                val mapped = DownloadErrorMapper.fromThrowable(t, currentEntity?.platform.orEmpty())
                dao.updateStatus(
                    id = id,
                    status = DownloadStatus.FAILED,
                    threads = 0,
                    errorMsg = mapped.userMessageAr,
                    diagnostic = DiagnosticRedactor.buildStructuredDiagnostic(
                        platform = currentEntity?.platform ?: "غير محدد",
                        failStage = currentEntity?.engineState ?: "UNKNOWN",
                        error = mapped,
                        formatId = currentEntity?.formatId ?: currentEntity?.qualityId,
                        tempPath = currentEntity?.tempPartPath
                    ),
                    engineState = EngineDownloadState.FAILED.name,
                    errorCode = mapped.code
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
            ?: result.qualities.firstOrNull { it.isAudioOnly == entity.isAudioOnly }
            ?: result.qualities.firstOrNull { !it.isAudioOnly }
            ?: return null
        if (matched.directUrl.isBlank()) return null
        val mergedHeaders = result.headers + matched.headers
        dao.updateResolvedUrl(
            id = entity.id,
            resolvedUrl = matched.directUrl,
            headersJson = encodeHeaders(mergedHeaders),
            etag = null,
            lastModified = null
        )
        return matched.directUrl to mergedHeaders
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
