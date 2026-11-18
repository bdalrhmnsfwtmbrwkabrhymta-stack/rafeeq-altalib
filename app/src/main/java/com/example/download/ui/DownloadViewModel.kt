package com.example.download.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.download.EnqueueOutcome
import com.example.download.SmartDownloadManager
import com.example.download.data.DownloadEntity
import com.example.download.data.DownloadSettings
import com.example.download.data.ExtractionResult
import com.example.download.data.LiveProgressSnapshot
import com.example.download.data.MediaQualityOption
import com.example.download.data.PlaylistEntry
import com.example.download.engine.StorageHelper
import com.example.download.extract.LinkExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class UiExtractionState {
    data object Idle : UiExtractionState()
    data class Extracting(val url: String) : UiExtractionState()
    data class SingleVideoReady(
        val video: ExtractionResult.SingleVideo,
        val defaultQualityId: String
    ) : UiExtractionState()

    data class PlaylistReady(val playlist: ExtractionResult.Playlist) : UiExtractionState()
    data class DrmBlocked(val url: String, val messageAr: String, val diagnostic: String) : UiExtractionState()
    data class Error(
        val url: String,
        val friendlyMessageAr: String,
        val diagnostic: String,
        val suggestCookieLogin: Boolean
    ) : UiExtractionState()
}

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = SmartDownloadManager.getInstance(application)

    val downloads: StateFlow<List<DownloadEntity>> = manager.allDownloadsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val liveStats: StateFlow<Map<String, LiveProgressSnapshot>> = manager.liveStatsFlow

    val settings: StateFlow<DownloadSettings> = manager.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DownloadSettings())

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _extractionState = MutableStateFlow<UiExtractionState>(UiExtractionState.Idle)
    val extractionState: StateFlow<UiExtractionState> = _extractionState.asStateFlow()

    private val _clipboardPromptUrl = MutableStateFlow<String?>(null)
    val clipboardPromptUrl: StateFlow<String?> = _clipboardPromptUrl.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private var lastPromptedUrl: String? = null

    fun setUrlInput(value: String) {
        _urlInput.value = value
    }

    fun inspectClipboardText(rawClipboard: String?) {
        val extracted = LinkExtractor.extractFirstHttpUrl(rawClipboard) ?: return
        if (extracted != lastPromptedUrl && extracted != _urlInput.value.trim()) {
            lastPromptedUrl = extracted
            _clipboardPromptUrl.value = extracted
        }
    }

    fun dismissClipboardPrompt() {
        _clipboardPromptUrl.value = null
    }

    fun acceptClipboardUrl(url: String) {
        _clipboardPromptUrl.value = null
        _urlInput.value = url
        startExtraction(url)
    }

    fun startExtraction(rawUrl: String = _urlInput.value) {
        val clean = LinkExtractor.extractFirstHttpUrl(rawUrl) ?: rawUrl.trim()
        if (clean.isBlank()) {
            _snackbarMessage.value = "يرجى لصق رابط الفيديو أولاً"
            return
        }
        _urlInput.value = clean
        _extractionState.value = UiExtractionState.Extracting(clean)

        viewModelScope.launch {
            when (val res = manager.extractLink(clean, checkPlaylist = true)) {
                is ExtractionResult.SingleVideo -> {
                    val lastPref = settings.value.lastSelectedQualityId
                    val pickedDefault = pickSmartDefaultQualityId(res.qualities, lastPref)
                    _extractionState.value = UiExtractionState.SingleVideoReady(res, pickedDefault)
                }
                is ExtractionResult.Playlist -> {
                    _extractionState.value = UiExtractionState.PlaylistReady(res)
                }
                is ExtractionResult.DrmProtected -> {
                    _extractionState.value = UiExtractionState.DrmBlocked(clean, res.messageAr, res.diagnostic)
                }
                is ExtractionResult.Failed -> {
                    _extractionState.value = UiExtractionState.Error(
                        url = clean,
                        friendlyMessageAr = res.friendlyMessageAr,
                        diagnostic = res.diagnostic,
                        suggestCookieLogin = res.suggestCookieLogin
                    )
                }
            }
        }
    }

    fun dismissExtractionSheet() {
        _extractionState.value = UiExtractionState.Idle
    }

    fun confirmSingleDownload(video: ExtractionResult.SingleVideo, quality: MediaQualityOption) {
        _extractionState.value = UiExtractionState.Idle
        viewModelScope.launch {
            when (val outcome = manager.enqueueSingleDownload(video, quality)) {
                is EnqueueOutcome.Enqueued -> {
                    _snackbarMessage.value = "بدأت إضافة «${video.title}» إلى التحميل السريع"
                }
                is EnqueueOutcome.Duplicate -> {
                    _snackbarMessage.value = "هذا العنصر موجود بالفعل بنفس الجودة: ${outcome.existingTitle}"
                }
                is EnqueueOutcome.InsufficientSpace -> {
                    _snackbarMessage.value = "المساحة المتاحة غير كافية (المطلوب: ${StorageHelper.formatBytes(outcome.requiredBytes)})"
                }
            }
        }
    }

    fun confirmPlaylistDownload(entries: List<PlaylistEntry>, qualityId: String) {
        _extractionState.value = UiExtractionState.Idle
        viewModelScope.launch {
            val count = manager.enqueuePlaylistBatch(entries, qualityId)
            _snackbarMessage.value = if (count > 0) {
                "تمت إضافة $count فيديو من قائمة التشغيل إلى الطابور"
            } else {
                "لم يتم تحديد عناصر جديدة أو كانت مكررة بالفعل"
            }
        }
    }

    fun pauseDownload(id: String) = manager.pauseDownload(id)
    fun resumeDownload(id: String) = manager.resumeDownload(id)
    fun retryDownload(id: String) = manager.retryDownload(id)
    fun cancelDownload(id: String) = manager.cancelDownload(id, deleteTemp = true)
    fun deleteRecord(id: String) = manager.deleteDownloadRecord(id)

    fun updateChunkCount(count: Int) {
        viewModelScope.launch { manager.settingsRepo.setChunkCount(count) }
    }

    fun updateWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            manager.settingsRepo.setWifiOnly(enabled)
            manager.notifyConnectivityMaybeChanged()
        }
    }

    fun updateMaxConcurrent(count: Int) {
        viewModelScope.launch { manager.settingsRepo.setMaxConcurrent(count) }
    }

    fun updateSpeedLimitKBps(kbps: Int) {
        viewModelScope.launch { manager.settingsRepo.setSpeedLimitKBps(kbps) }
    }

    fun cleanOrphanTempFiles() {
        viewModelScope.launch {
            val removed = manager.cleanOrphanTempFiles()
            _snackbarMessage.value = if (removed > 0) {
                "تم تنظيف $removed ملفاً مؤقتاً غير مكتمل"
            } else {
                "لا توجد ملفات مؤقتة تالفة لتنظيفها"
            }
        }
    }

    fun hasSavedCookies(): Boolean = manager.settingsRepo.hasSavedCookies()

    fun getCookiesFile() = manager.settingsRepo.getCookiesFile()

    fun clearSavedCookies() {
        manager.settingsRepo.clearCookies()
        _snackbarMessage.value = "تم مسح جلسة تسجيل الدخول المحلية"
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    private fun pickSmartDefaultQualityId(qualities: List<MediaQualityOption>, lastPref: String): String {
        val exactLast = qualities.firstOrNull { it.id == lastPref }
        if (exactLast != null && (exactLast.isAudioOnly || exactLast.resolutionHeight <= 1080)) {
            return exactLast.id
        }
        val capped1080 = qualities.firstOrNull { !it.isAudioOnly && it.resolutionHeight <= 1080 }
        return capped1080?.id ?: qualities.firstOrNull()?.id ?: "best"
    }
}
