package com.hawas.study.rafid

import android.app.Application
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hawas.study.rafid.data.BlockCategory
import com.hawas.study.rafid.data.RafidDatabase
import com.hawas.study.rafid.data.RafidPreferencesRepository
import com.hawas.study.rafid.engine.AdAndTrackerShield
import com.hawas.study.rafid.engine.RafidMediaStoreSaver
import com.hawas.study.rafid.engine.SmartClipboardDetector
import com.hawas.study.rafid.engine.SniffedMediaCandidate
import com.hawas.study.rafid.engine.UniversalMediaExtractor
import com.hawas.study.rafid.models.ExtractedMediaMetadata
import com.hawas.study.rafid.models.MediaFormatOption
import com.hawas.study.rafid.models.RafidBookmarkEntity
import com.hawas.study.rafid.models.RafidConfigState
import com.hawas.study.rafid.models.RafidDownloadEntity
import com.hawas.study.rafid.models.RafidDownloadStatus
import com.hawas.study.rafid.models.RafidExcludedDomainEntity
import com.hawas.study.rafid.models.RafidLanguageMode
import com.hawas.study.rafid.worker.DownloadQueueManager
import com.hawas.study.rafid.worker.RafidDownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID

sealed interface ExtractionUiState {
    data object Idle : ExtractionUiState
    data class Loading(val url: String) : ExtractionUiState
    data class Success(val metadata: ExtractedMediaMetadata) : ExtractionUiState
    data class Error(val url: String, val message: String) : ExtractionUiState
}

class RafidViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val dao = RafidDatabase.getInstance(appContext).rafidDao()
    private val prefs = RafidPreferencesRepository(appContext)
    private val extractor = UniversalMediaExtractor(
        ytDlpGateway = com.example.download.core.resolver.AndroidYtDlpGateway(appContext),
        webViewSniffGateway = com.example.download.core.resolver.AndroidWebViewSniffGateway(appContext)
    )

    val downloads: StateFlow<List<RafidDownloadEntity>> = dao.observeAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks: StateFlow<List<RafidBookmarkEntity>> = dao.observeBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RafidDatabase.DEFAULT_PLATFORM_BOOKMARKS)

    val excludedDomains: StateFlow<List<RafidExcludedDomainEntity>> = dao.observeExcludedDomains()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val config: StateFlow<RafidConfigState> = prefs.configFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RafidConfigState())

    private val _extractionState = MutableStateFlow<ExtractionUiState>(ExtractionUiState.Idle)
    val extractionState: StateFlow<ExtractionUiState> = _extractionState.asStateFlow()

    private val _sniffedBrowserMedia = MutableStateFlow<List<SniffedMediaCandidate>>(emptyList())
    val sniffedBrowserMedia: StateFlow<List<SniffedMediaCandidate>> = _sniffedBrowserMedia.asStateFlow()

    private val _currentBrowserUrl = MutableStateFlow<String?>(null)
    val currentBrowserUrl: StateFlow<String?> = _currentBrowserUrl.asStateFlow()

    private val _currentBrowserTitle = MutableStateFlow<String?>(null)
    val currentBrowserTitle: StateFlow<String?> = _currentBrowserTitle.asStateFlow()

    private val _isIncognitoBrowser = MutableStateFlow(false)
    val isIncognitoBrowser: StateFlow<Boolean> = _isIncognitoBrowser.asStateFlow()

    private val _clipboardBannerUrl = MutableStateFlow<String?>(null)
    val clipboardBannerUrl: StateFlow<String?> = _clipboardBannerUrl.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            RafidDatabase.ensureDefaultsSeeded(dao)
            val snap = prefs.getSnapshot()
            if (snap.lastRulesUpdateMillis == 0L) {
                prefs.touchRulesUpdated()
            }
            dispatchQueueSlots()
        }
    }

    fun inspectClipboardOnDemand(): String? {
        val url = SmartClipboardDetector.readClipboardUrl(appContext)
        _clipboardBannerUrl.value = url
        return url
    }

    fun dismissClipboardBanner(url: String) {
        _clipboardBannerUrl.value = null
        viewModelScope.launch { prefs.setLastSeenClipboardUrl(url) }
    }

    fun analyzeUrl(rawUrl: String, explicitCookies: String? = null, userAgent: String? = null) {
        val cleanUrl = AdAndTrackerShield.extractFirstUrl(rawUrl) ?: rawUrl.trim()
        if (cleanUrl.isBlank()) return

        _extractionState.value = ExtractionUiState.Loading(cleanUrl)
        viewModelScope.launch {
            val browserCookies = withContext(Dispatchers.Main) {
                explicitCookies ?: runCatching {
                    CookieManager.getInstance().getCookie(cleanUrl)
                }.getOrNull()
            }
            val result = extractor.extract(cleanUrl, browserCookies, userAgent)
            _extractionState.value = result.fold(
                onSuccess = { metadata ->
                    ExtractionUiState.Success(metadata)
                },
                onFailure = { err ->
                    val mapped = com.example.download.core.model.DownloadErrorMapper.fromThrowable(err)
                    ExtractionUiState.Error(
                        url = cleanUrl,
                        message = mapped.userMessageAr
                    )
                }
            )
        }
    }

    fun resetExtraction() {
        _extractionState.value = ExtractionUiState.Idle
    }

    fun enqueueDownload(
        metadata: ExtractedMediaMetadata,
        selectedFormat: MediaFormatOption,
        saveToStudyFolder: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (selectedFormat.isPlaylistBundle && metadata.playlistItems.isNotEmpty()) {
                enqueuePlaylistBundle(metadata, selectedFormat, saveToStudyFolder)
            } else {
                val streamUrl = selectedFormat.videoUrl ?: metadata.resolvedUrl
                val fallbackPairedAudioUrl = if (!selectedFormat.isAudioOnly &&
                    !selectedFormat.hasAudio &&
                    selectedFormat.audioUrl.isNullOrBlank()
                ) {
                    metadata.formats.firstOrNull {
                        it.isAudioOnly &&
                            it.containerExt.equals("m4a", true) &&
                            !it.videoUrl.isNullOrBlank() &&
                            it.videoUrl != metadata.resolvedUrl
                    }?.videoUrl ?: metadata.formats.firstOrNull {
                        it.isAudioOnly &&
                            !it.videoUrl.isNullOrBlank() &&
                            it.videoUrl != metadata.resolvedUrl
                    }?.videoUrl
                } else {
                    selectedFormat.audioUrl
                }
                val needsAudioTrackMux = selectedFormat.isMuxRequired ||
                    !fallbackPairedAudioUrl.isNullOrBlank() ||
                    (!selectedFormat.isAudioOnly && !selectedFormat.hasAudio)
                val category = AdAndTrackerShield.categoryFromMimeOrExt(selectedFormat.mimeType, selectedFormat.containerExt)
                val headersJson = selectedFormat.headers.takeIf { it.isNotEmpty() }?.let {
                    org.json.JSONObject(it).toString()
                }
                val audioLang = selectedFormat.languageCode.takeIf { it.isNotBlank() }
                    ?: selectedFormat.languageLabel.takeIf { it.isNotBlank() }
                val entity = RafidDownloadEntity(
                    id = UUID.randomUUID().toString(),
                    originalUrl = metadata.originalUrl,
                    resolvedUrl = metadata.resolvedUrl,
                    videoStreamUrl = streamUrl,
                    audioStreamUrl = fallbackPairedAudioUrl,
                    title = metadata.title,
                    sourceDomain = metadata.sourceDomain,
                    platformName = metadata.platform.name,
                    thumbnailUrl = metadata.thumbnailUrl,
                    qualityLabel = selectedFormat.label,
                    containerExt = selectedFormat.containerExt,
                    mimeType = selectedFormat.mimeType,
                    categoryName = category.name,
                    isAudioOnly = selectedFormat.isAudioOnly,
                    isMuxRequired = needsAudioTrackMux,
                    saveToStudyFolder = saveToStudyFolder,
                    cookiesHeader = metadata.cookiesHeader,
                    userAgent = metadata.userAgent,
                    status = RafidDownloadStatus.QUEUED.name,
                    totalBytes = selectedFormat.estimatedSizeBytes,
                    displayPath = RafidMediaStoreSaver.buildDisplaySubfolder(category, saveToStudyFolder),
                    headersJson = headersJson,
                    formatExpression = selectedFormat.formatExpression,
                    audioLanguage = audioLang,
                    engineState = "QUEUED"
                )
                dao.upsertDownload(entity)
                dispatchQueueSlots()
            }
        }
    }

    private suspend fun enqueuePlaylistBundle(
        metadata: ExtractedMediaMetadata,
        selectedFormat: MediaFormatOption,
        saveToStudyFolder: Boolean
    ) {
        val wantAudioOnly = selectedFormat.isAudioOnly
        val targetHeight = (selectedFormat.resolutionHeight ?: 1080).coerceAtLeast(144)
        val preferDubbedArabic = selectedFormat.isAutoDubbed ||
            selectedFormat.languageCode.equals("ar", ignoreCase = true) ||
            selectedFormat.languageLabel.contains("مدبلج") ||
            selectedFormat.id.contains("dubbed", ignoreCase = true)

        val targetExt = if (wantAudioOnly) selectedFormat.containerExt.ifBlank { "m4a" } else "mp4"
        val targetMime = if (wantAudioOnly) {
            if (targetExt.equals("mp3", true)) "audio/mpeg" else "audio/mp4"
        } else {
            "video/mp4"
        }
        val category = AdAndTrackerShield.categoryFromMimeOrExt(targetMime, targetExt)
        val fallbackAudioExpr = if (preferDubbedArabic) {
            "bestaudio[language^=ar][ext=m4a]/bestaudio[language^=ar]/bestaudio[ext=m4a]/bestaudio"
        } else {
            "bestaudio[ext=m4a]/bestaudio"
        }
        val effectiveFormatExpr = if (wantAudioOnly) {
            if (preferDubbedArabic) "bestaudio[language^=ar]/bestaudio/best" else "bestaudio/best"
        } else if (targetHeight <= 1080) {
            "bestvideo[height<=$targetHeight][vcodec^=avc1]+$fallbackAudioExpr/bestvideo[height<=$targetHeight][ext=mp4]+$fallbackAudioExpr/bestvideo[height<=$targetHeight]+bestaudio/best[height<=$targetHeight][ext=mp4]/best[height<=$targetHeight]/best"
        } else {
            "bestvideo[height<=$targetHeight][ext=mp4]+$fallbackAudioExpr/bestvideo[height<=$targetHeight]+bestaudio/best[height<=$targetHeight]/best"
        }
        val baseTimestamp = System.currentTimeMillis()
        val orderedItems = metadata.playlistItems.sortedBy { it.index }

        val entries = orderedItems.mapIndexed { idx, item ->
            val watchUrl = if (metadata.platform == com.hawas.study.rafid.models.RafidPlatform.YOUTUBE &&
                item.videoId.isNotBlank() &&
                !item.videoId.startsWith("item_")
            ) {
                "https://www.youtube.com/watch?v=${item.videoId}"
            } else {
                item.videoUrl
            }
            RafidDownloadEntity(
                id = UUID.randomUUID().toString(),
                originalUrl = watchUrl,
                resolvedUrl = watchUrl,
                videoStreamUrl = watchUrl,
                audioStreamUrl = null,
                title = "${item.index}. ${item.title}",
                sourceDomain = metadata.sourceDomain,
                platformName = metadata.platform.name,
                thumbnailUrl = item.thumbnailUrl ?: metadata.thumbnailUrl,
                qualityLabel = if (wantAudioOnly) targetExt.uppercase() else "${targetHeight}p",
                containerExt = targetExt,
                mimeType = targetMime,
                categoryName = category.name,
                isAudioOnly = wantAudioOnly,
                isMuxRequired = !wantAudioOnly,
                saveToStudyFolder = saveToStudyFolder,
                isPlaylistItem = true,
                playlistTitle = metadata.title,
                cookiesHeader = metadata.cookiesHeader,
                userAgent = metadata.userAgent,
                status = RafidDownloadStatus.QUEUED.name,
                totalBytes = -1L,
                displayPath = RafidMediaStoreSaver.buildDisplaySubfolder(category, saveToStudyFolder),
                formatExpression = effectiveFormatExpr,
                audioLanguage = if (preferDubbedArabic) "ar" else null,
                createdAtMillis = baseTimestamp + idx,
                engineState = "QUEUED"
            )
        }
        if (entries.isNotEmpty()) {
            dao.upsertDownloads(entries)
            DownloadQueueManager.triggerQueueProcessing(appContext)
        }
    }

    internal companion object {
        internal suspend fun buildPlaylistDownloadEntities(
            metadata: ExtractedMediaMetadata,
            selectedFormat: MediaFormatOption,
            saveToStudyFolder: Boolean,
            extractor: UniversalMediaExtractor
        ): List<RafidDownloadEntity> {
            val wantAudioOnly = selectedFormat.isAudioOnly
            val targetHeight = (selectedFormat.resolutionHeight ?: 1080).coerceAtLeast(144)
            val preferDubbedArabic = selectedFormat.isAutoDubbed ||
                selectedFormat.languageCode.equals("ar", ignoreCase = true) ||
                selectedFormat.languageLabel.contains("مدبلج") ||
                selectedFormat.id.contains("dubbed", ignoreCase = true)

            val baseTimestamp = System.currentTimeMillis()
            val orderedItems = metadata.playlistItems.sortedBy { it.index }
            val entries = mutableListOf<RafidDownloadEntity>()

            fun MediaFormatOption.isArabicDubbedTrack(): Boolean =
                isAutoDubbed || languageCode.startsWith("ar", ignoreCase = true) || languageLabel.contains("مدبلج")

            orderedItems.forEachIndexed { idx, item ->
                val extractedItem = extractor.extract(
                    rawUrl = item.videoUrl,
                    cookiesHeader = metadata.cookiesHeader,
                    userAgent = metadata.userAgent,
                    checkCollection = false
                ).getOrNull()

                val chosenFormat = if (extractedItem != null) {
                    if (wantAudioOnly) {
                        val audioCandidates = extractedItem.formats.filter { it.isAudioOnly }
                        if (preferDubbedArabic) {
                            audioCandidates.firstOrNull {
                                it.isArabicDubbedTrack() && it.containerExt.equals(selectedFormat.containerExt, ignoreCase = true)
                            } ?: audioCandidates.firstOrNull { it.isArabicDubbedTrack() }
                                ?: audioCandidates.firstOrNull { it.containerExt.equals(selectedFormat.containerExt, ignoreCase = true) }
                                ?: audioCandidates.firstOrNull()
                        } else {
                            audioCandidates.firstOrNull {
                                !it.isAutoDubbed && it.containerExt.equals(selectedFormat.containerExt, ignoreCase = true)
                            } ?: audioCandidates.firstOrNull { !it.isAutoDubbed }
                                ?: audioCandidates.firstOrNull { it.containerExt.equals(selectedFormat.containerExt, ignoreCase = true) }
                                ?: audioCandidates.firstOrNull()
                        }
                    } else {
                        val videoCandidates = extractedItem.formats.filter { !it.isAudioOnly }
                        val audioMatchedVideos = if (preferDubbedArabic) {
                            videoCandidates.filter { it.isArabicDubbedTrack() }.ifEmpty { videoCandidates }
                        } else {
                            videoCandidates.filter { !it.isAutoDubbed }.ifEmpty { videoCandidates }
                        }
                        val withinCap = audioMatchedVideos.filter { (it.resolutionHeight ?: 0) in 1..targetHeight }
                        val bestHeight = withinCap.maxOfOrNull { it.resolutionHeight ?: 0 }
                        if (bestHeight != null) {
                            val atBestHeight = withinCap.filter { (it.resolutionHeight ?: 0) == bestHeight }
                            atBestHeight.firstOrNull { it.hasAudio && it.containerExt.equals("mp4", true) }
                                ?: atBestHeight.firstOrNull { it.hasAudio }
                                ?: atBestHeight.firstOrNull { it.containerExt.equals("mp4", true) }
                                ?: atBestHeight.firstOrNull()
                        } else {
                            audioMatchedVideos.firstOrNull { it.hasAudio }
                                ?: audioMatchedVideos.firstOrNull()
                        }
                    }
                } else {
                    null
                }

                val targetExt = if (wantAudioOnly) {
                    chosenFormat?.containerExt?.ifBlank { selectedFormat.containerExt }
                        ?: selectedFormat.containerExt.ifBlank { "m4a" }
                } else {
                    "mp4"
                }
                val targetMime = if (wantAudioOnly) {
                    if (targetExt.equals("mp3", true)) "audio/mpeg" else "audio/mp4"
                } else {
                    "video/mp4"
                }

                val pairedAudioUrl = if (!wantAudioOnly && chosenFormat != null && chosenFormat.audioUrl.isNullOrBlank() && !chosenFormat.hasAudio) {
                    val audioPool = extractedItem?.formats?.filter {
                        it.isAudioOnly && !it.videoUrl.isNullOrBlank() && it.videoUrl != extractedItem.resolvedUrl
                    }.orEmpty()
                    val preferredPool = if (preferDubbedArabic) {
                        audioPool.filter { it.isArabicDubbedTrack() }.ifEmpty { audioPool }
                    } else {
                        audioPool.filter { !it.isAutoDubbed }.ifEmpty { audioPool }
                    }
                    preferredPool.firstOrNull { it.containerExt.equals("m4a", true) }?.videoUrl
                        ?: preferredPool.firstOrNull()?.videoUrl
                } else {
                    chosenFormat?.audioUrl
                }

                val effectiveVideoUrl = chosenFormat?.videoUrl?.takeIf { it.isNotBlank() } ?: item.videoUrl
                val fallbackAudioExpr = if (preferDubbedArabic) {
                    "bestaudio[language^=ar][ext=m4a]/bestaudio[language^=ar]/bestaudio[ext=m4a]/bestaudio"
                } else {
                    "bestaudio[ext=m4a]/bestaudio"
                }
                val effectiveFormatExpr = chosenFormat?.formatExpression?.takeIf { it.isNotBlank() }
                    ?: if (wantAudioOnly) {
                        if (preferDubbedArabic) "bestaudio[language^=ar]/bestaudio/best" else "bestaudio/best"
                    } else if (targetHeight <= 1080) {
                        "bestvideo[height<=$targetHeight][vcodec^=avc1]+$fallbackAudioExpr/bestvideo[height<=$targetHeight][ext=mp4]+$fallbackAudioExpr/bestvideo[height<=$targetHeight]+bestaudio/best[height<=$targetHeight]/best"
                    } else {
                        "bestvideo[height<=$targetHeight][ext=mp4]+$fallbackAudioExpr/bestvideo[height<=$targetHeight]+bestaudio/best[height<=$targetHeight]/best"
                    }
                val needsMux = chosenFormat == null ||
                    chosenFormat.isMuxRequired ||
                    !pairedAudioUrl.isNullOrBlank() ||
                    (!wantAudioOnly && !chosenFormat.hasAudio)

                val category = AdAndTrackerShield.categoryFromMimeOrExt(targetMime, targetExt)
                val headersJson = chosenFormat?.headers?.takeIf { it.isNotEmpty() }?.let {
                    org.json.JSONObject(it).toString()
                }
                val audioLang = chosenFormat?.languageCode?.takeIf { it.isNotBlank() }
                    ?: chosenFormat?.languageLabel?.takeIf { it.isNotBlank() }
                    ?: if (preferDubbedArabic) "ar" else null

                entries += RafidDownloadEntity(
                    id = UUID.randomUUID().toString(),
                    originalUrl = item.videoUrl,
                    resolvedUrl = extractedItem?.resolvedUrl ?: item.videoUrl,
                    videoStreamUrl = effectiveVideoUrl,
                    audioStreamUrl = if (wantAudioOnly) null else pairedAudioUrl,
                    title = "${item.index}. ${item.title}",
                    sourceDomain = metadata.sourceDomain,
                    platformName = metadata.platform.name,
                    thumbnailUrl = item.thumbnailUrl ?: metadata.thumbnailUrl,
                    qualityLabel = chosenFormat?.label ?: if (wantAudioOnly) targetExt.uppercase() else "${targetHeight}p MP4",
                    containerExt = targetExt,
                    mimeType = targetMime,
                    categoryName = category.name,
                    isAudioOnly = wantAudioOnly,
                    isMuxRequired = needsMux,
                    saveToStudyFolder = saveToStudyFolder,
                    isPlaylistItem = true,
                    playlistTitle = metadata.title,
                    cookiesHeader = extractedItem?.cookiesHeader ?: metadata.cookiesHeader,
                    userAgent = metadata.userAgent,
                    status = RafidDownloadStatus.QUEUED.name,
                    totalBytes = chosenFormat?.estimatedSizeBytes ?: -1L,
                    displayPath = RafidMediaStoreSaver.buildDisplaySubfolder(category, saveToStudyFolder),
                    headersJson = headersJson,
                    formatExpression = effectiveFormatExpr,
                    audioLanguage = audioLang,
                    createdAtMillis = baseTimestamp + idx,
                    engineState = "QUEUED"
                )
            }
            return entries
        }
    }

    fun pauseDownload(downloadId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateDownloadStatus(downloadId, RafidDownloadStatus.PAUSED.name, null)
            RafidDownloadWorker.cancelDownloadWork(appContext, downloadId)
            dispatchQueueSlots()
        }
    }

    fun resumeDownload(downloadId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateDownloadStatus(downloadId, RafidDownloadStatus.QUEUED.name, null)
            dispatchQueueSlots()
        }
    }

    fun deleteDownload(downloadId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            RafidDownloadWorker.cancelDownloadWork(appContext, downloadId)
            dao.deleteDownloadById(downloadId)
            dispatchQueueSlots()
        }
    }

    fun clearCompletedDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearCompletedDownloads()
        }
    }

    fun dispatchQueueSlots() {
        viewModelScope.launch(Dispatchers.IO) {
            DownloadQueueManager.triggerQueueProcessing(appContext)
        }
    }

    // Settings & Controls
    fun toggleDarkMode(currentlyDark: Boolean) {
        viewModelScope.launch { prefs.setDarkMode(!currentlyDark) }
    }

    fun setLanguageMode(mode: RafidLanguageMode) {
        viewModelScope.launch { prefs.setLanguageMode(mode) }
    }

    fun adjustMaxConcurrent(delta: Int) {
        viewModelScope.launch {
            val current = prefs.getSnapshot().maxConcurrentDownloads
            prefs.setMaxConcurrentDownloads((current + delta).coerceIn(1, 7))
            dispatchQueueSlots()
        }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setWifiOnly(enabled)
        }
    }

    fun setScheduledStart(enabled: Boolean, hour: Int? = null, minute: Int? = null) {
        viewModelScope.launch {
            prefs.setScheduledStart(enabled, hour, minute)
        }
    }

    fun toggleQueuePaused() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentPaused = prefs.getSnapshot().isQueuePaused
            val nextPaused = !currentPaused
            prefs.setQueuePaused(nextPaused)
            if (nextPaused) {
                dao.getActiveDownloads().forEach { active ->
                    dao.updateDownloadStatus(active.id, RafidDownloadStatus.PAUSED.name, null)
                    RafidDownloadWorker.cancelDownloadWork(appContext, active.id)
                }
            } else {
                dispatchQueueSlots()
            }
        }
    }

    fun setBlockAdsAndTrackers(enabled: Boolean) {
        viewModelScope.launch { prefs.setBlockAdsAndTrackers(enabled) }
    }

    fun setBlockMalwareSites(enabled: Boolean) {
        viewModelScope.launch { prefs.setBlockMalwareSites(enabled) }
    }

    fun setSmartEngineEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setSmartEngineEnabled(enabled) }
    }

    fun refreshProtectionRulesNow() {
        viewModelScope.launch { prefs.touchRulesUpdated(System.currentTimeMillis()) }
    }

    fun recordBlockedNetworkRequest(category: BlockCategory) {
        viewModelScope.launch(Dispatchers.IO) {
            prefs.recordBlockedRequest(category)
        }
    }

    fun addExcludedDomain(rawDomain: String) {
        val clean = AdAndTrackerShield.normalizeDomain(rawDomain)
        if (clean.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.addExcludedDomain(RafidExcludedDomainEntity(domain = clean))
        }
    }

    fun removeExcludedDomain(domain: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.removeExcludedDomain(domain)
        }
    }

    // Browser state
    fun updateBrowserPage(url: String?, title: String?) {
        _currentBrowserUrl.value = url
        _currentBrowserTitle.value = title
        if (url == null) {
            _sniffedBrowserMedia.value = emptyList()
        }
    }

    fun onMediaSniffedInBrowser(candidate: SniffedMediaCandidate) {
        val existing = _sniffedBrowserMedia.value
        if (existing.none { it.url == candidate.url }) {
            _sniffedBrowserMedia.value = listOf(candidate) + existing.take(14)
        }
    }

    fun clearSniffedBrowserMedia() {
        _sniffedBrowserMedia.value = emptyList()
    }

    fun toggleIncognitoBrowser() {
        _isIncognitoBrowser.value = !_isIncognitoBrowser.value
    }

    fun addCustomBookmark(title: String, rawUrl: String) {
        val cleanTitle = title.trim()
        val normalizedUrl = if (rawUrl.trim().startsWith("http://") || rawUrl.trim().startsWith("https://")) {
            rawUrl.trim()
        } else {
            "https://${rawUrl.trim()}"
        }
        if (cleanTitle.isBlank() || normalizedUrl.length < 10) return
        viewModelScope.launch(Dispatchers.IO) {
            val count = dao.countBookmarks()
            dao.upsertBookmark(
                RafidBookmarkEntity(
                    id = "custom_${UUID.randomUUID()}",
                    titleAr = cleanTitle,
                    titleEn = cleanTitle,
                    url = normalizedUrl,
                    platformKey = "CUSTOM",
                    sortOrder = count + 1,
                    isDefault = false
                )
            )
        }
    }

    fun deleteCustomBookmark(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteCustomBookmark(id)
        }
    }
}
