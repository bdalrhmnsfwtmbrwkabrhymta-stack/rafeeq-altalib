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
import com.example.download.core.media.MediaMergeEngine
import com.example.download.core.model.DiagnosticRedactor
import com.example.download.core.model.DownloadEngineException
import com.example.download.core.model.DownloadError
import com.example.download.core.model.DownloadErrorMapper
import com.example.download.core.model.EngineDownloadState
import com.example.download.core.resolver.AndroidWebViewSniffGateway
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
import com.hawas.study.rafid.models.RafidMediaCategory
import com.hawas.study.rafid.models.RafidPlatform
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
import org.json.JSONObject
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
            dao.updateEngineState(downloadId, "QUEUED")
            return@withContext Result.retry()
        }

        val tempDir = File(applicationContext.cacheDir, "rafid_tmp/$downloadId").apply { mkdirs() }
        val primaryTemp = File(tempDir, "primary.${record.containerExt.ifBlank { "mp4" }}")
        val ytDlpGateway = AndroidYtDlpGateway(applicationContext)
        val extractor = UniversalMediaExtractor(
            httpClient = client,
            ytDlpGateway = ytDlpGateway,
            webViewSniffGateway = AndroidWebViewSniffGateway(applicationContext)
        )

        var resolvedStreamUrl = record.videoStreamUrl
        var resolvedAudioUrl = record.audioStreamUrl
        var activeCookies = record.cookiesHeader
        var activeHeaders = buildEffectiveHeaders(
            headersJson = record.headersJson,
            userAgent = record.userAgent,
            fallbackReferer = record.resolvedUrl,
            cookiesHeader = activeCookies
        )

        var currentStage = "RESOLVING"
        val isImageOrDoc = record.categoryName == RafidMediaCategory.IMAGE.name ||
            record.categoryName == RafidMediaCategory.DOCUMENT.name

        val isPausedCheck = {
            if (isStopped) true
            else runBlocking(Dispatchers.IO) {
                val r = dao.getDownloadById(downloadId)
                r == null || r.status == RafidDownloadStatus.PAUSED.name || r.status == RafidDownloadStatus.CANCELLED.name
            }
        }

        suspend fun refreshStreamsFromSource(): Pair<String, Map<String, String>>? {
            dao.updateEngineState(downloadId, "RESOLVING")
            val reExtracted = extractor.extract(record.resolvedUrl, activeCookies, record.userAgent, checkCollection = false).getOrNull()
                ?: extractor.extract(record.originalUrl, activeCookies, record.userAgent, checkCollection = false).getOrNull()
                ?: return null

            val targetMaxHeight = Regex("""(\d{3,4})p""").find(record.qualityLabel)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
            val preferDubbedArabic = record.audioLanguage.equals("ar", ignoreCase = true) ||
                record.audioLanguage?.contains("مدبلج") == true

            val sameKindFormats = reExtracted.formats.filter {
                !it.videoUrl.isNullOrBlank() &&
                    it.videoUrl != record.resolvedUrl &&
                    it.isAudioOnly == record.isAudioOnly
            }

            val candidate = if (record.isAudioOnly) {
                val langMatched = if (preferDubbedArabic) {
                    sameKindFormats.filter { it.isAutoDubbed || it.languageCode.startsWith("ar", true) }.ifEmpty { sameKindFormats }
                } else {
                    sameKindFormats.filter { !it.isAutoDubbed }.ifEmpty { sameKindFormats }
                }
                langMatched.firstOrNull { it.containerExt.equals(record.containerExt, true) }
                    ?: langMatched.firstOrNull()
            } else {
                val exactMatch = sameKindFormats.firstOrNull { fmt ->
                    fmt.label == record.qualityLabel &&
                        (record.audioLanguage.isNullOrBlank() || fmt.languageCode == record.audioLanguage)
                }
                exactMatch ?: run {
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
                }
            } ?: return null

            val newVideoUrl = candidate.videoUrl ?: return null
            val newAudioUrl = if (!record.isAudioOnly && candidate.audioUrl.isNullOrBlank() && !candidate.hasAudio) {
                val audioPool = reExtracted.formats.filter {
                    it.isAudioOnly && !it.videoUrl.isNullOrBlank() && it.videoUrl != record.resolvedUrl
                }
                val preferredPool = if (preferDubbedArabic) {
                    audioPool.filter { it.isAutoDubbed || it.languageCode.startsWith("ar", true) }.ifEmpty { audioPool }
                } else {
                    audioPool.filter { !it.isAutoDubbed }.ifEmpty { audioPool }
                }
                preferredPool.firstOrNull { it.containerExt.equals("m4a", true) }?.videoUrl
                    ?: preferredPool.firstOrNull()?.videoUrl
            } else {
                candidate.audioUrl
            }

            activeCookies = ResolverHttpUtils.mergeCookieStrings(activeCookies, reExtracted.cookiesHeader)
            val refreshedHeaders = buildMap {
                putAll(activeHeaders)
                putAll(candidate.headers)
                if (!activeCookies.isNullOrBlank()) put("Cookie", activeCookies!!)
            }
            activeHeaders = refreshedHeaders
            resolvedStreamUrl = newVideoUrl
            if (!newAudioUrl.isNullOrBlank()) resolvedAudioUrl = newAudioUrl
            dao.updateRefreshedStreams(
                id = downloadId,
                videoStreamUrl = newVideoUrl,
                audioStreamUrl = resolvedAudioUrl,
                headersJson = JSONObject(refreshedHeaders).toString(),
                cookiesHeader = activeCookies
            )
            return newVideoUrl to refreshedHeaders
        }

        return@withContext try {
            if (resolvedStreamUrl == record.resolvedUrl && !ResolverHttpUtils.isManifestUrl(resolvedStreamUrl)) {
                dao.updateEngineState(downloadId, "RESOLVING")
                refreshStreamsFromSource()
            }

            currentStage = if (record.isAudioOnly) "DOWNLOADING_AUDIO" else "DOWNLOADING_VIDEO"
            dao.updateEngineState(downloadId, currentStage)
            dao.updateDownloadProgress(
                id = downloadId,
                status = RafidDownloadStatus.DOWNLOADING.name,
                downloadedBytes = primaryTemp.length(),
                totalBytes = record.totalBytes,
                speedBytesPerSec = 0L,
                etaSeconds = 0L
            )

            val mustRequireVideoAndAudio = !record.isAudioOnly && !isImageOrDoc &&
                (record.isMuxRequired || record.platformName == RafidPlatform.TIKTOK.name || record.isPlaylistItem)

            // Handle HLS / DASH manifests or unresolved page/watch URLs cleanly through yt-dlp + FFmpeg
            val shouldUseYtDlpManifestPath = !isImageOrDoc && (
                ResolverHttpUtils.isManifestUrl(resolvedStreamUrl) ||
                    resolvedStreamUrl == record.resolvedUrl ||
                    (record.isMuxRequired && resolvedAudioUrl.isNullOrBlank() && !record.formatExpression.isNullOrBlank() && !record.isAudioOnly)
                )

            if (shouldUseYtDlpManifestPath) {
                val manifestDownloadUrl = if (resolvedStreamUrl == record.resolvedUrl ||
                    record.formatExpression?.contains("+") == true ||
                    (record.isAudioOnly && record.resolvedUrl.startsWith("http", true))
                ) {
                    record.resolvedUrl.ifBlank { resolvedStreamUrl }
                } else {
                    resolvedStreamUrl
                }
                val downloadedManifestFile = downloadManifestWithYtDlp(
                    downloadId = downloadId,
                    url = manifestDownloadUrl,
                    outDir = tempDir,
                    isAudioOnly = record.isAudioOnly,
                    targetExt = record.containerExt.ifBlank { if (record.isAudioOnly) "m4a" else "mp4" },
                    formatExpression = record.formatExpression,
                    headers = activeHeaders,
                    knownTotalBytes = record.totalBytes,
                    ytDlpGateway = ytDlpGateway,
                    dao = dao
                )
                currentStage = "VERIFYING"
                dao.updateEngineState(downloadId, "VERIFYING")
                val manifestExt = downloadedManifestFile.extension.ifBlank { record.containerExt.ifBlank { "mp4" } }
                val manifestMime = MediaMergeEngine.mimeForExt(manifestExt, record.isAudioOnly)
                MediaMergeEngine.verifyFinalArtifact(
                    context = applicationContext,
                    file = downloadedManifestFile,
                    requireVideo = !record.isAudioOnly && !isImageOrDoc,
                    requireAudio = record.isAudioOnly || mustRequireVideoAndAudio,
                    isImageOrDocument = isImageOrDoc
                )
                val category = AdAndTrackerShield.categoryFromMimeOrExt(manifestMime, manifestExt)
                val saved = RafidMediaStoreSaver.saveFileToRafidDownloads(
                    context = applicationContext,
                    sourceFile = downloadedManifestFile,
                    title = record.title,
                    extension = manifestExt,
                    mimeType = manifestMime,
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

            // Pre-flight check to ensure direct candidate does not silently point to text/html
            if (!isImageOrDoc) {
                val preProbe = ResolverHttpUtils.probeStreamMedia(client, resolvedStreamUrl, activeHeaders)
                if (preProbe.reachable && preProbe.isHtmlPage) {
                    val refreshed = refreshStreamsFromSource()
                    val postProbe = refreshed?.let { ResolverHttpUtils.probeStreamMedia(client, it.first, it.second) }
                    if (postProbe == null || (postProbe.reachable && postProbe.isHtmlPage)) {
                        throw DownloadEngineException(
                            DownloadError.UnsupportedMedia(
                                mediaType = "text/html",
                                technicalDetail = "Stream URL returned ${preProbe.contentType.ifBlank { "text/html" }} before download"
                            )
                        )
                    }
                }
            }

            val chunkStore = RafidFileChunkStore(File(tempDir, "chunks.meta"), dao, CoroutineScope(Dispatchers.IO))
            val engine = SegmentedDownloadEngine(chunkStore, client)

            val downloadedPrimary = engine.download(
                DownloadJobSpec(
                    downloadId = downloadId,
                    url = resolvedStreamUrl,
                    headers = activeHeaders,
                    targetPartFile = primaryTemp,
                    expectedMimeType = record.mimeType,
                    requestedChunks = 8,
                    savedTotalBytes = record.totalBytes,
                    isPausedOrCanceled = isPausedCheck,
                    onRefreshExpiredUrl = { refreshStreamsFromSource() },
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
                currentStage = "DOWNLOADING_AUDIO"
                dao.updateEngineState(downloadId, "DOWNLOADING_AUDIO")
                val inferredAudioExt = when {
                    resolvedAudioUrl!!.contains("mime=audio/webm", ignoreCase = true) ||
                        resolvedAudioUrl!!.contains("mime=audio%2Fwebm", ignoreCase = true) ||
                        resolvedAudioUrl!!.contains(".webm", ignoreCase = true) -> "webm"
                    resolvedAudioUrl!!.contains(".mp3", ignoreCase = true) -> "mp3"
                    else -> "m4a"
                }
                val audioFile = File(tempDir, "audio_track.$inferredAudioExt")
                val audioStore = RafidFileChunkStore(File(tempDir, "audio_chunks.meta"), dao, CoroutineScope(Dispatchers.IO))
                val audioEngine = SegmentedDownloadEngine(audioStore, client)
                audioEngine.download(
                    DownloadJobSpec(
                        downloadId = "${downloadId}_audio",
                        url = resolvedAudioUrl!!,
                        headers = activeHeaders,
                        targetPartFile = audioFile,
                        expectedMimeType = if (inferredAudioExt == "webm") "audio/webm" else "audio/mp4",
                        requestedChunks = 4,
                        isPausedOrCanceled = isPausedCheck,
                        onRefreshExpiredUrl = {
                            val re = extractor.extract(record.resolvedUrl, activeCookies, record.userAgent, checkCollection = false).getOrNull()
                            val candAudio = re?.formats?.firstOrNull {
                                !it.audioUrl.isNullOrBlank() &&
                                    (record.audioLanguage.isNullOrBlank() || it.languageCode == record.audioLanguage)
                            }?.audioUrl ?: re?.formats?.firstNotNullOfOrNull { it.audioUrl }
                            candAudio?.let {
                                resolvedAudioUrl = it
                                it to activeHeaders
                            }
                        }
                    )
                )
                audioFile
            } else {
                null
            }

            if (record.isMuxRequired || secondaryAudioTemp != null) {
                currentStage = "MERGING"
                dao.updateEngineState(downloadId, "MERGING")
                dao.updateDownloadStatus(downloadId, RafidDownloadStatus.MUXING.name, null)

                val targetExt = if (record.isAudioOnly) {
                    if (record.containerExt.equals("mp3", ignoreCase = true)) "mp3" else "m4a"
                } else {
                    record.containerExt.ifBlank { "mp4" }
                }
                val muxedOutput = File(tempDir, "muxed_output.$targetExt")

                val mergeExecution = try {
                    val merged = SerialFFmpegMuxerWorker.runSerializedMux(
                        context = applicationContext,
                        primaryMediaFile = downloadedPrimary,
                        secondaryAudioFile = secondaryAudioTemp,
                        desiredOutputFile = muxedOutput,
                        desiredExtension = targetExt,
                        audioOnly = record.isAudioOnly
                    )
                    currentStage = "VERIFYING"
                    dao.updateEngineState(downloadId, "VERIFYING")
                    MediaMergeEngine.verifyFinalArtifact(
                        context = applicationContext,
                        file = merged.outputFile,
                        requireVideo = !record.isAudioOnly && !isImageOrDoc,
                        requireAudio = record.isAudioOnly || mustRequireVideoAndAudio,
                        isImageOrDocument = isImageOrDoc
                    )
                    merged.outputFile to (merged.finalExtension to merged.finalMimeType)
                } catch (mergeFailure: Throwable) {
                    if (record.resolvedUrl.startsWith("http", ignoreCase = true) && !isImageOrDoc) {
                        val fallbackFile = downloadManifestWithYtDlp(
                            downloadId = downloadId,
                            url = record.resolvedUrl,
                            outDir = tempDir,
                            isAudioOnly = record.isAudioOnly,
                            targetExt = targetExt,
                            formatExpression = record.formatExpression,
                            headers = activeHeaders,
                            knownTotalBytes = record.totalBytes,
                            ytDlpGateway = ytDlpGateway,
                            dao = dao
                        )
                        currentStage = "VERIFYING"
                        dao.updateEngineState(downloadId, "VERIFYING")
                        val fbExt = fallbackFile.extension.ifBlank { targetExt }
                        val fbMime = MediaMergeEngine.mimeForExt(fbExt, record.isAudioOnly)
                        MediaMergeEngine.verifyFinalArtifact(
                            context = applicationContext,
                            file = fallbackFile,
                            requireVideo = !record.isAudioOnly,
                            requireAudio = record.isAudioOnly || mustRequireVideoAndAudio,
                            isImageOrDocument = false
                        )
                        fallbackFile to (fbExt to fbMime)
                    } else {
                        throw mergeFailure
                    }
                }

                val (finalMergedFile, extAndMime) = mergeExecution
                val (finalExt, finalMime) = extAndMime
                val category = AdAndTrackerShield.categoryFromMimeOrExt(finalMime, finalExt)
                val saved = RafidMediaStoreSaver.saveFileToRafidDownloads(
                    context = applicationContext,
                    sourceFile = finalMergedFile,
                    title = record.title,
                    extension = finalExt,
                    mimeType = finalMime,
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
            } else {
                currentStage = "VERIFYING"
                dao.updateEngineState(downloadId, "VERIFYING")
                MediaMergeEngine.verifyFinalArtifact(
                    context = applicationContext,
                    file = downloadedPrimary,
                    requireVideo = !record.isAudioOnly && !isImageOrDoc,
                    requireAudio = record.isAudioOnly || mustRequireVideoAndAudio,
                    isImageOrDocument = isImageOrDoc
                )
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
                val mapped = DownloadErrorMapper.fromThrowable(t, record.platformName)
                val structuredDiag = DiagnosticRedactor.buildStructuredDiagnostic(
                    platform = record.platformName,
                    failStage = currentStage,
                    error = mapped,
                    formatId = record.formatExpression ?: record.qualityLabel,
                    tempPath = tempDir.absolutePath
                )
                dao.updateDownloadFailure(
                    id = downloadId,
                    status = RafidDownloadStatus.FAILED.name,
                    errorMessage = mapped.userMessageAr,
                    engineState = "FAILED",
                    errorCode = mapped.code,
                    diagnosticLog = structuredDiag
                )
            }
            Result.failure()
        } finally {
            runCatching {
                val snap = RafidPreferencesRepository(applicationContext).getSnapshot()
                if (!snap.isQueuePaused) {
                    val activeCount = dao.countActiveDownloads()
                    val freeSlots = (snap.maxConcurrentDownloads - activeCount).coerceAtLeast(0)
                    if (freeSlots > 0) {
                        dao.getQueuedDownloads().take(freeSlots).forEach { next ->
                            enqueueDownload(
                                context = applicationContext,
                                downloadId = next.id,
                                wifiOnly = snap.wifiOnly
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun downloadManifestWithYtDlp(
        downloadId: String,
        url: String,
        outDir: File,
        isAudioOnly: Boolean,
        targetExt: String,
        formatExpression: String?,
        headers: Map<String, String>,
        knownTotalBytes: Long,
        ytDlpGateway: AndroidYtDlpGateway,
        dao: RafidDao
    ): File {
        if (!ytDlpGateway.ensureInitialized()) {
            throw DownloadEngineException(DownloadError.EngineUpdateFailed("yt-dlp/FFmpeg unavailable for HLS/DASH"))
        }
        val runtimePaths = MediaMergeEngine.ensureRuntimeReady(applicationContext)
        val outBase = File(outDir, "hls_out")
        fun buildYtDlpRequest(exprOverride: String?): YoutubeDLRequest = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--continue")
            addOption("--concurrent-fragments", "8")
            addOption("--retries", "8")
            addOption("--fragment-retries", "8")
            addOption("--ffmpeg-location", runtimePaths.binDir.absolutePath)
            addOption("-o", "${outBase.absolutePath}.%(ext)s")
            headers.forEach { (k, v) ->
                when {
                    k.equals("User-Agent", true) -> addOption("--user-agent", v)
                    else -> addOption("--add-header", "$k:$v")
                }
            }
            if (isAudioOnly) {
                addOption("-x")
                addOption("--audio-format", targetExt.ifBlank { "m4a" })
                addOption("--audio-quality", "0")
            } else {
                val chosenExpr = exprOverride?.takeIf { it.isNotBlank() }
                    ?: "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=1080]+bestaudio/best[height<=1080]/best"
                addOption("-f", chosenExpr)
                addOption("--merge-output-format", "mp4")
            }
        }

        val fallbackExpr = if (isAudioOnly) {
            "bestaudio/best"
        } else {
            "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=1080]+bestaudio/best[height<=1080]/best"
        }
        val expressionsToTry = listOfNotNull(
            formatExpression?.takeIf { it.isNotBlank() },
            fallbackExpr
        ).distinct()

        var mergedSent = false
        var lastExecError: Throwable? = null
        for (expr in expressionsToTry) {
            lastExecError = null
            try {
                YoutubeDL.getInstance().execute(buildYtDlpRequest(expr), "rafid_$downloadId") { progress, eta, line ->
                    val cleanLine = line ?: ""
                    if (!mergedSent && (cleanLine.contains("[Merger]", true) || cleanLine.contains("Fixing", true))) {
                        mergedSent = true
                        runBlocking {
                            dao.updateEngineState(downloadId, "MERGING")
                            dao.updateDownloadStatus(downloadId, RafidDownloadStatus.MUXING.name, null)
                        }
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
                break
            } catch (t: Throwable) {
                lastExecError = t
            }
        }
        if (lastExecError != null) throw lastExecError

        val producedFiles = outDir.listFiles()
            ?.filter { it.name.startsWith("hls_out.") && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && it.length() > 0L }
            .orEmpty()

        // If yt-dlp left unmerged video/audio files, invoke MediaMergeEngine explicitly
        if (!isAudioOnly && producedFiles.size >= 2) {
            val vidPart = producedFiles.firstOrNull { !it.extension.equals("m4a", true) && !it.extension.equals("opus", true) && !it.extension.equals("mp3", true) }
            val audPart = producedFiles.firstOrNull { it != vidPart }
            if (vidPart != null && audPart != null) {
                dao.updateEngineState(downloadId, "MERGING")
                val muxedFile = File(outDir, "hls_merged.${targetExt.ifBlank { "mp4" }}")
                val res = SerialFFmpegMuxerWorker.runSerializedMux(
                    context = applicationContext,
                    primaryMediaFile = vidPart,
                    secondaryAudioFile = audPart,
                    desiredOutputFile = muxedFile,
                    desiredExtension = targetExt.ifBlank { "mp4" },
                    audioOnly = false
                )
                return res.outputFile
            }
        }

        val latest = producedFiles.maxByOrNull { it.lastModified() }
            ?: throw DownloadEngineException(DownloadError.MergeFailed("HLS/DASH output file missing after FFmpeg merge"))

        if (isAudioOnly) {
            val cleanAudioExt = if (targetExt.equals("mp3", true)) "mp3" else "m4a"
            if (!latest.extension.equals(cleanAudioExt, ignoreCase = true)) {
                dao.updateEngineState(downloadId, "MERGING")
                val convertedAudio = File(outDir, "hls_audio.$cleanAudioExt")
                val res = SerialFFmpegMuxerWorker.runSerializedMux(
                    context = applicationContext,
                    primaryMediaFile = latest,
                    secondaryAudioFile = null,
                    desiredOutputFile = convertedAudio,
                    desiredExtension = cleanAudioExt,
                    audioOnly = true
                )
                return res.outputFile
            }
        }

        return latest
    }

    private fun buildEffectiveHeaders(
        headersJson: String?,
        userAgent: String?,
        fallbackReferer: String?,
        cookiesHeader: String?
    ): Map<String, String> {
        val parsed = buildMap {
            if (!headersJson.isNullOrBlank()) {
                runCatching {
                    val obj = JSONObject(headersJson)
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = obj.optString(k)
                        if (k.isNotBlank() && v.isNotBlank()) put(k, v)
                    }
                }
            }
            if (keys.none { it.equals("User-Agent", true) }) {
                put("User-Agent", userAgent ?: UniversalLinkResolver.DESKTOP_USER_AGENT)
            }
            if (keys.none { it.equals("Referer", true) } && !fallbackReferer.isNullOrBlank()) {
                put("Referer", fallbackReferer)
            }
            if (!cookiesHeader.isNullOrBlank() && keys.none { it.equals("Cookie", true) }) {
                put("Cookie", cookiesHeader)
            }
        }
        return parsed
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
