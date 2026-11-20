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
        ytDlpGateway = com.example.download.core.resolver.AndroidYtDlpGateway(appContext)
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
                val category = AdAndTrackerShield.categoryFromMimeOrExt(selectedFormat.mimeType, selectedFormat.containerExt)
                val entity = RafidDownloadEntity(
                    id = UUID.randomUUID().toString(),
                    originalUrl = metadata.originalUrl,
                    resolvedUrl = metadata.resolvedUrl,
                    videoStreamUrl = streamUrl,
                    audioStreamUrl = selectedFormat.audioUrl,
                    title = metadata.title,
                    sourceDomain = metadata.sourceDomain,
                    platformName = metadata.platform.name,
                    thumbnailUrl = metadata.thumbnailUrl,
                    qualityLabel = selectedFormat.label,
                    containerExt = selectedFormat.containerExt,
                    mimeType = selectedFormat.mimeType,
                    categoryName = category.name,
                    isAudioOnly = selectedFormat.isAudioOnly,
                    isMuxRequired = selectedFormat.isMuxRequired || !selectedFormat.audioUrl.isNullOrBlank(),
                    saveToStudyFolder = saveToStudyFolder,
                    cookiesHeader = metadata.cookiesHeader,
                    userAgent = metadata.userAgent,
                    status = RafidDownloadStatus.QUEUED.name,
                    totalBytes = selectedFormat.estimatedSizeBytes,
                    displayPath = RafidMediaStoreSaver.buildDisplaySubfolder(category, saveToStudyFolder)
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
        val entries = mutableListOf<RafidDownloadEntity>()

        for (item in metadata.playlistItems) {
            val extractedItem = extractor.extract(
                rawUrl = item.videoUrl,
                cookiesHeader = metadata.cookiesHeader,
                userAgent = metadata.userAgent
            ).getOrNull()

            val chosenFormat = if (extractedItem != null) {
                if (wantAudioOnly) {
                    extractedItem.formats.firstOrNull { it.isAudioOnly } ?: extractedItem.formats.firstOrNull()
                } else {
                    extractedItem.formats.firstOrNull { !it.isAudioOnly && (it.resolutionHeight ?: 0) <= 1080 }
                        ?: extractedItem.formats.firstOrNull { !it.isAudioOnly }
                        ?: extractedItem.formats.firstOrNull()
                }
            } else {
                null
            }

            if (chosenFormat != null && !chosenFormat.videoUrl.isNullOrBlank()) {
                val category = AdAndTrackerShield.categoryFromMimeOrExt(chosenFormat.mimeType, chosenFormat.containerExt)
                entries += RafidDownloadEntity(
                    id = UUID.randomUUID().toString(),
                    originalUrl = item.videoUrl,
                    resolvedUrl = extractedItem?.resolvedUrl ?: item.videoUrl,
                    videoStreamUrl = chosenFormat.videoUrl,
                    audioStreamUrl = chosenFormat.audioUrl,
                    title = "${item.index}. ${item.title}",
                    sourceDomain = metadata.sourceDomain,
                    platformName = metadata.platform.name,
                    thumbnailUrl = item.thumbnailUrl ?: metadata.thumbnailUrl,
                    qualityLabel = chosenFormat.label,
                    containerExt = chosenFormat.containerExt,
                    mimeType = chosenFormat.mimeType,
                    categoryName = category.name,
                    isAudioOnly = chosenFormat.isAudioOnly,
                    isMuxRequired = chosenFormat.isMuxRequired || !chosenFormat.audioUrl.isNullOrBlank(),
                    saveToStudyFolder = saveToStudyFolder,
                    isPlaylistItem = true,
                    playlistTitle = metadata.title,
                    cookiesHeader = metadata.cookiesHeader,
                    userAgent = metadata.userAgent,
                    status = RafidDownloadStatus.QUEUED.name,
                    totalBytes = chosenFormat.estimatedSizeBytes,
                    displayPath = RafidMediaStoreSaver.buildDisplaySubfolder(category, saveToStudyFolder)
                )
            }
        }

        if (entries.isNotEmpty()) {
            dao.upsertDownloads(entries)
            dispatchQueueSlots()
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
            val snap = prefs.getSnapshot()
            if (snap.isQueuePaused) return@launch

            val activeCount = dao.countActiveDownloads()
            val freeSlots = (snap.maxConcurrentDownloads - activeCount).coerceAtLeast(0)
            if (freeSlots == 0) return@launch

            val initialDelay = if (snap.isScheduledStartEnabled) {
                computeDelayUntilMillis(snap.scheduledHour, snap.scheduledMinute)
            } else {
                0L
            }

            val queued = dao.getQueuedDownloads().take(freeSlots)
            queued.forEach { item ->
                RafidDownloadWorker.enqueueDownload(
                    context = appContext,
                    downloadId = item.id,
                    wifiOnly = snap.wifiOnly,
                    initialDelayMillis = initialDelay
                )
            }
        }
    }

    private fun computeDelayUntilMillis(targetHour: Int, targetMinute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            // If scheduled time already passed within this day, treat <=12h window as active or schedule next occurrence
            val diff = now.timeInMillis - target.timeInMillis
            if (diff <= 6 * 3600_000L) return 0L
            target.add(Calendar.DAY_OF_MONTH, 1)
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
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
