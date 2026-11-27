package com.hawas.study.rafid.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.download.core.resolver.AndroidWebViewSniffGateway
import com.example.download.core.resolver.AndroidYtDlpGateway
import com.example.download.core.resolver.ResolverHttpUtils
import com.example.download.service.DownloadForegroundService
import com.hawas.study.rafid.data.RafidDao
import com.hawas.study.rafid.data.RafidDatabase
import com.hawas.study.rafid.data.RafidPreferencesRepository
import com.hawas.study.rafid.engine.UniversalMediaExtractor
import com.hawas.study.rafid.models.RafidDownloadEntity
import com.hawas.study.rafid.models.RafidDownloadStatus
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object DownloadQueueManager {

    private val queueMutex = Mutex()
    private val preExtractScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preExtractingIds = ConcurrentHashMap.newKeySet<String>()
    private val inFlightDownloadIds = ConcurrentHashMap.newKeySet<String>()
    private val serviceActiveJobs = ConcurrentHashMap<String, Job>()

    private val preExtractClient: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 12
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(12, 5, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_1_1))
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    fun tryClaimExecution(downloadId: String): Boolean = inFlightDownloadIds.add(downloadId)

    fun releaseExecution(downloadId: String) {
        inFlightDownloadIds.remove(downloadId)
    }

    fun hasActiveInProcessDownloads(): Boolean {
        serviceActiveJobs.entries.removeAll { !it.value.isActive }
        return inFlightDownloadIds.isNotEmpty() || serviceActiveJobs.isNotEmpty()
    }

    fun cancelInProcessJob(downloadId: String) {
        serviceActiveJobs.remove(downloadId)?.cancel()
        runCatching { YoutubeDL.getInstance().destroyProcessById("rafid_$downloadId") }
    }

    suspend fun enqueueAndStart(context: Context, taskId: String? = null) {
        val appContext = context.applicationContext
        DownloadForegroundService.startQueue(appContext, taskId)
        triggerQueueProcessing(appContext)
    }

    fun processQueueInService(
        context: Context,
        serviceScope: CoroutineScope,
        prioritizedTaskId: String? = null
    ) {
        val appContext = context.applicationContext
        serviceScope.launch(Dispatchers.IO) {
            var itemsToPreExtract: List<RafidDownloadEntity> = emptyList()
            queueMutex.withLock {
                val prefs = RafidPreferencesRepository(appContext).getSnapshot()
                if (prefs.isQueuePaused) return@withLock

                if (prefs.isScheduledStartEnabled &&
                    computeDelayUntilMillis(prefs.scheduledHour, prefs.scheduledMinute) > 0L
                ) {
                    return@withLock
                }

                if (prefs.wifiOnly && !isOnUnmeteredNetwork(appContext)) {
                    return@withLock
                }

                val dao = RafidDatabase.getInstance(appContext).rafidDao()
                val workManager = runCatching { WorkManager.getInstance(appContext) }.getOrNull()

                serviceActiveJobs.entries.removeAll { !it.value.isActive }

                // Recover orphaned DOWNLOADING / MUXING records not currently running in service or WorkManager
                val staleActives = dao.getActiveDownloads()
                for (active in staleActives) {
                    if (inFlightDownloadIds.contains(active.id) || serviceActiveJobs[active.id]?.isActive == true) {
                        continue
                    }
                    val hasRunningWork = workManager?.let { wm ->
                        runCatching {
                            wm.getWorkInfosForUniqueWork(RafidDownloadWorker.uniqueWorkName(active.id)).get()
                        }.getOrDefault(emptyList()).any { it.state == WorkInfo.State.RUNNING }
                    } ?: false
                    if (!hasRunningWork) {
                        dao.resetOrphanedActiveToQueued(active.id)
                    }
                }

                val activeCount = maxOf(
                    dao.countActiveDownloads(),
                    inFlightDownloadIds.size,
                    serviceActiveJobs.count { it.value.isActive }
                )
                val freeSlots = (prefs.maxConcurrentDownloads - activeCount).coerceAtLeast(0)
                val rawQueued = dao.getQueuedDownloads()
                val allQueued = if (!prioritizedTaskId.isNullOrBlank()) {
                    val prioritized = rawQueued.firstOrNull { it.id == prioritizedTaskId }
                    if (prioritized != null) {
                        listOf(prioritized) + rawQueued.filterNot { it.id == prioritizedTaskId }
                    } else {
                        rawQueued
                    }
                } else {
                    rawQueued
                }

                if (freeSlots > 0) {
                    val toDispatch = allQueued.take(freeSlots)
                    for (next in toDispatch) {
                        if (inFlightDownloadIds.contains(next.id) || serviceActiveJobs[next.id]?.isActive == true) {
                            continue
                        }
                        // Immediately transition from QUEUED -> DOWNLOADING so hero card & list update at once
                        dao.updateEngineState(next.id, "RESOLVING")
                        dao.updateDownloadProgress(
                            id = next.id,
                            status = RafidDownloadStatus.DOWNLOADING.name,
                            downloadedBytes = next.downloadedBytes,
                            totalBytes = next.totalBytes,
                            speedBytesPerSec = 0L,
                            etaSeconds = 0L
                        )
                        val job = serviceScope.launch(
                            context = Dispatchers.IO,
                            start = kotlinx.coroutines.CoroutineStart.LAZY
                        ) {
                            try {
                                RafidDownloadExecutor.execute(appContext, next.id)
                            } finally {
                                serviceActiveJobs.remove(next.id)
                                processQueueInService(appContext, serviceScope)
                            }
                        }
                        serviceActiveJobs[next.id] = job
                        job.start()
                    }
                }

                itemsToPreExtract = allQueued
                    .drop(freeSlots)
                    .filter {
                        it.videoStreamUrl == it.resolvedUrl &&
                            !ResolverHttpUtils.isManifestUrl(it.resolvedUrl) &&
                            !preExtractingIds.contains(it.id)
                    }
                    .take(2)
            }

            if (itemsToPreExtract.isNotEmpty()) {
                val dao = RafidDatabase.getInstance(appContext).rafidDao()
                val extractor = UniversalMediaExtractor(
                    httpClient = preExtractClient,
                    ytDlpGateway = AndroidYtDlpGateway(appContext),
                    webViewSniffGateway = AndroidWebViewSniffGateway(appContext)
                )
                itemsToPreExtract.forEach { candidateItem ->
                    if (preExtractingIds.add(candidateItem.id)) {
                        preExtractScope.launch {
                            try {
                                preExtractQueuedItem(dao, candidateItem, extractor)
                            } finally {
                                preExtractingIds.remove(candidateItem.id)
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun triggerQueueProcessing(context: Context) {
        val appContext = context.applicationContext
        var itemsToPreExtract: List<RafidDownloadEntity> = emptyList()
        queueMutex.withLock {
            val prefs = RafidPreferencesRepository(appContext).getSnapshot()
            if (prefs.isQueuePaused) return

            val dao = RafidDatabase.getInstance(appContext).rafidDao()
            val workManager = runCatching { WorkManager.getInstance(appContext) }.getOrNull()

            // Recover any stale DOWNLOADING / MUXING records whose WorkManager job disappeared
            if (workManager != null) {
                val staleActives = dao.getActiveDownloads()
                for (active in staleActives) {
                    if (inFlightDownloadIds.contains(active.id) || serviceActiveJobs[active.id]?.isActive == true) {
                        continue
                    }
                    val infos = runCatching {
                        workManager.getWorkInfosForUniqueWork(RafidDownloadWorker.uniqueWorkName(active.id)).get()
                    }.getOrDefault(emptyList())
                    val hasLiveWork = infos.any {
                        it.state == WorkInfo.State.RUNNING ||
                            it.state == WorkInfo.State.ENQUEUED ||
                            it.state == WorkInfo.State.BLOCKED
                    }
                    if (!hasLiveWork) {
                        dao.resetOrphanedActiveToQueued(active.id)
                    }
                }
            }

            val activeCount = dao.countActiveDownloads()
            val freeSlots = (prefs.maxConcurrentDownloads - activeCount).coerceAtLeast(0)
            val allQueued = dao.getQueuedDownloads()

            if (freeSlots > 0) {
                val initialDelay = if (prefs.isScheduledStartEnabled) {
                    computeDelayUntilMillis(prefs.scheduledHour, prefs.scheduledMinute)
                } else {
                    0L
                }

                val toDispatch = allQueued.take(freeSlots)
                var dispatchedAny = false
                for (next in toDispatch) {
                    if (inFlightDownloadIds.contains(next.id) || serviceActiveJobs[next.id]?.isActive == true) {
                        continue
                    }
                    val alreadyScheduled = if (workManager != null) {
                        val infos = runCatching {
                            workManager.getWorkInfosForUniqueWork(RafidDownloadWorker.uniqueWorkName(next.id)).get()
                        }.getOrDefault(emptyList())
                        infos.any {
                            it.state == WorkInfo.State.RUNNING ||
                                it.state == WorkInfo.State.ENQUEUED ||
                                it.state == WorkInfo.State.BLOCKED
                        }
                    } else {
                        false
                    }
                    if (!alreadyScheduled) {
                        dispatchedAny = true
                        RafidDownloadWorker.enqueueDownload(
                            context = appContext,
                            downloadId = next.id,
                            wifiOnly = prefs.wifiOnly,
                            initialDelayMillis = initialDelay
                        )
                    }
                }
                if (dispatchedAny && initialDelay == 0L) {
                    DownloadForegroundService.startQueue(appContext, toDispatch.firstOrNull()?.id)
                }
            }

            // Pick next 1-2 queued items beyond immediate dispatch slots whose direct streams are not yet extracted
            itemsToPreExtract = allQueued
                .drop(freeSlots)
                .filter {
                    it.videoStreamUrl == it.resolvedUrl &&
                        !ResolverHttpUtils.isManifestUrl(it.resolvedUrl) &&
                        !preExtractingIds.contains(it.id)
                }
                .take(2)
        }

        if (itemsToPreExtract.isNotEmpty()) {
            val dao = RafidDatabase.getInstance(appContext).rafidDao()
            val extractor = UniversalMediaExtractor(
                httpClient = preExtractClient,
                ytDlpGateway = AndroidYtDlpGateway(appContext),
                webViewSniffGateway = AndroidWebViewSniffGateway(appContext)
            )
            itemsToPreExtract.forEach { candidateItem ->
                if (preExtractingIds.add(candidateItem.id)) {
                    preExtractScope.launch {
                        try {
                            preExtractQueuedItem(dao, candidateItem, extractor)
                        } finally {
                            preExtractingIds.remove(candidateItem.id)
                        }
                    }
                }
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

    internal suspend fun preExtractQueuedItem(
        dao: RafidDao,
        record: RafidDownloadEntity,
        extractor: UniversalMediaExtractor
    ): Boolean {
        val current = dao.getDownloadById(record.id) ?: return false
        if (current.status != com.hawas.study.rafid.models.RafidDownloadStatus.QUEUED.name ||
            current.videoStreamUrl != current.resolvedUrl
        ) {
            return false
        }

        val extracted = extractor.extract(
            rawUrl = current.resolvedUrl,
            cookiesHeader = current.cookiesHeader,
            userAgent = current.userAgent,
            checkCollection = false
        ).getOrNull() ?: return false

        val targetMaxHeight = Regex("""(\d{3,4})p""").find(current.qualityLabel)
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
        val preferDubbedArabic = current.audioLanguage.equals("ar", ignoreCase = true) ||
            current.audioLanguage?.contains("مدبلج") == true

        val sameKindFormats = extracted.formats.filter {
            !it.videoUrl.isNullOrBlank() &&
                it.videoUrl != current.resolvedUrl &&
                it.isAudioOnly == current.isAudioOnly
        }
        if (sameKindFormats.isEmpty()) return false

        val chosen = if (current.isAudioOnly) {
            val langMatched = if (preferDubbedArabic) {
                sameKindFormats.filter { it.isAutoDubbed || it.languageCode.startsWith("ar", true) }.ifEmpty { sameKindFormats }
            } else {
                sameKindFormats.filter { !it.isAutoDubbed }.ifEmpty { sameKindFormats }
            }
            langMatched.firstOrNull { it.containerExt.equals(current.containerExt, true) }
                ?: langMatched.firstOrNull()
        } else {
            val langMatched = if (preferDubbedArabic) {
                sameKindFormats.filter { it.isAutoDubbed || it.languageCode.startsWith("ar", true) }.ifEmpty { sameKindFormats }
            } else {
                sameKindFormats.filter { !it.isAutoDubbed }.ifEmpty { sameKindFormats }
            }
            if (targetMaxHeight != null) {
                val withinCap = langMatched.filter { (it.resolutionHeight ?: 0) in 1..targetMaxHeight }
                val bestHeight = withinCap.maxOfOrNull { it.resolutionHeight ?: 0 }
                if (bestHeight != null) {
                    val atBestHeight = withinCap.filter { (it.resolutionHeight ?: 0) == bestHeight }
                    atBestHeight.firstOrNull { it.hasAudio && it.containerExt.equals("mp4", true) }
                        ?: atBestHeight.firstOrNull { it.hasAudio }
                        ?: atBestHeight.firstOrNull { it.containerExt.equals("mp4", true) }
                        ?: atBestHeight.firstOrNull()
                } else {
                    langMatched.minByOrNull { it.resolutionHeight ?: Int.MAX_VALUE }
                }
            } else {
                langMatched.firstOrNull { it.hasAudio } ?: langMatched.firstOrNull()
            }
        } ?: return false

        val newVideoUrl = chosen.videoUrl ?: return false
        val newAudioUrl = if (!current.isAudioOnly && chosen.audioUrl.isNullOrBlank() && !chosen.hasAudio) {
            val audioPool = extracted.formats.filter {
                it.isAudioOnly && !it.videoUrl.isNullOrBlank() && it.videoUrl != current.resolvedUrl
            }
            val preferredPool = if (preferDubbedArabic) {
                audioPool.filter { it.isAutoDubbed || it.languageCode.startsWith("ar", true) }.ifEmpty { audioPool }
            } else {
                audioPool.filter { !it.isAutoDubbed }.ifEmpty { audioPool }
            }
            preferredPool.firstOrNull { it.containerExt.equals("m4a", true) }?.videoUrl
                ?: preferredPool.firstOrNull()?.videoUrl
        } else {
            chosen.audioUrl
        }

        val mergedCookies = ResolverHttpUtils.mergeCookieStrings(current.cookiesHeader, extracted.cookiesHeader)
        val mergedHeaders = buildMap {
            if (!current.headersJson.isNullOrBlank()) {
                runCatching {
                    val obj = JSONObject(current.headersJson)
                    obj.keys().forEach { k ->
                        val v = obj.optString(k)
                        if (k.isNotBlank() && v.isNotBlank()) put(k, v)
                    }
                }
            }
            putAll(chosen.headers)
            if (!mergedCookies.isNullOrBlank()) put("Cookie", mergedCookies)
        }
        val headersJson = mergedHeaders.takeIf { it.isNotEmpty() }?.let { JSONObject(it).toString() }
        val needsMux = chosen.isMuxRequired || !newAudioUrl.isNullOrBlank() || (!current.isAudioOnly && !chosen.hasAudio)

        val rows = dao.updatePreExtractedStreamsIfQueued(
            id = current.id,
            videoStreamUrl = newVideoUrl,
            audioStreamUrl = if (current.isAudioOnly) null else newAudioUrl,
            totalBytes = chosen.estimatedSizeBytes,
            headersJson = headersJson,
            cookiesHeader = mergedCookies,
            formatExpression = chosen.formatExpression,
            qualityLabel = chosen.label,
            isMuxRequired = needsMux
        )
        return rows > 0
    }

    internal fun computeDelayUntilMillis(targetHour: Int, targetMinute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            val diff = now.timeInMillis - target.timeInMillis
            if (diff <= 6 * 3600_000L) return 0L
            target.add(Calendar.DAY_OF_MONTH, 1)
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
    }
}
