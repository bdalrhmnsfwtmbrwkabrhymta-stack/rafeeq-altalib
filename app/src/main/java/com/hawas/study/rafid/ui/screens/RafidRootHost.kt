package com.hawas.study.rafid.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.ui.theme.ReadexProFontFamily
import com.hawas.study.rafid.ExtractionUiState
import com.hawas.study.rafid.RafidViewModel
import com.hawas.study.rafid.models.RafidDownloadEntity
import com.hawas.study.rafid.models.RafidDownloadStatus
import com.hawas.study.rafid.models.RafidFormatUtils
import com.hawas.study.rafid.models.RafidScreenTab
import com.hawas.study.rafid.ui.components.DownloadItemInfoDialog
import com.hawas.study.rafid.ui.components.NewDownloadLinkSheet
import com.hawas.study.rafid.ui.components.RafidFloatingBottomBar
import com.hawas.study.rafid.ui.components.RafidLanguageSheet
import com.hawas.study.rafid.ui.theme.LocalRafidIsEnglish
import com.hawas.study.rafid.ui.theme.LocalRafidPalette
import com.hawas.study.rafid.ui.theme.RafidTheme

@Composable
fun RafidRootHost(
    initialSharedUrl: String? = null,
    onConsumeSharedUrl: () -> Unit = {},
    onNavigateBackToApp: () -> Unit,
    rafidViewModel: RafidViewModel = viewModel()
) {
    val context = LocalContext.current
    val downloads by rafidViewModel.downloads.collectAsStateWithLifecycle()
    val bookmarks by rafidViewModel.bookmarks.collectAsStateWithLifecycle()
    val excludedDomains by rafidViewModel.excludedDomains.collectAsStateWithLifecycle()
    val config by rafidViewModel.config.collectAsStateWithLifecycle()
    val extractionState by rafidViewModel.extractionState.collectAsStateWithLifecycle()
    val sniffedBrowserMedia by rafidViewModel.sniffedBrowserMedia.collectAsStateWithLifecycle()
    val currentBrowserUrl by rafidViewModel.currentBrowserUrl.collectAsStateWithLifecycle()
    val isIncognitoBrowser by rafidViewModel.isIncognitoBrowser.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(RafidScreenTab.HOME) }
    var showNewLinkSheet by remember { mutableStateOf(false) }
    var newLinkInitialUrl by remember { mutableStateOf("") }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showHomeSearchBar by remember { mutableStateOf(false) }
    var homeSearchQuery by remember { mutableStateOf("") }
    var selectedInfoItem by remember { mutableStateOf<RafidDownloadEntity?>(null) }

    // Trigger immediate extraction if opened via external Android Share ("مشاركة")
    LaunchedEffect(initialSharedUrl) {
        if (!initialSharedUrl.isNullOrBlank()) {
            currentTab = RafidScreenTab.HOME
            rafidViewModel.analyzeUrl(initialSharedUrl)
            onConsumeSharedUrl()
        }
    }

    // Sub-tab back handling -> HOME; on HOME -> exit Rafid back to Study Obsession Home
    BackHandler {
        if (currentTab != RafidScreenTab.HOME) {
            currentTab = RafidScreenTab.HOME
        } else {
            onNavigateBackToApp()
        }
    }

    fun openNewDownloadSheetWithClipboard(explicitUrl: String? = null) {
        val seed = explicitUrl ?: rafidViewModel.inspectClipboardOnDemand().orEmpty()
        newLinkInitialUrl = seed
        showNewLinkSheet = true
    }

    fun openCompletedFile(item: RafidDownloadEntity) {
        val uriStr = item.mediaStoreUri
        if (uriStr.isNullOrBlank()) {
            selectedInfoItem = item
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uriStr), item.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(
                context,
                "تم حفظ الملف في: ${item.displayPath}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    RafidTheme(
        darkOverride = config.darkModeOverride,
        languageMode = config.languageMode
    ) {
        val palette = LocalRafidPalette.current
        val excludedSet = remember(excludedDomains) {
            excludedDomains.map { it.domain }.toSet()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background)
        ) {
            when (currentTab) {
                RafidScreenTab.HOME -> {
                    RafidDownloaderHomeScreen(
                        downloads = downloads,
                        config = config,
                        showSearchBar = showHomeSearchBar,
                        searchQuery = homeSearchQuery,
                        onSearchQueryChange = { homeSearchQuery = it },
                        onBackToApp = onNavigateBackToApp,
                        onToggleDarkMode = { rafidViewModel.toggleDarkMode(palette.isDark) },
                        onOpenLanguageSheet = { showLanguageSheet = true },
                        onOpenNewDownloadSheet = { seed -> openNewDownloadSheetWithClipboard(seed) },
                        onOpenClipboardAction = {
                            val clipUrl = rafidViewModel.inspectClipboardOnDemand()
                            if (!clipUrl.isNullOrBlank()) {
                                rafidViewModel.analyzeUrl(clipUrl)
                            } else {
                                openNewDownloadSheetWithClipboard("")
                            }
                        },
                        onOpenPlaylists = { currentTab = RafidScreenTab.PLAYLISTS },
                        onOpenQueues = { currentTab = RafidScreenTab.QUEUES },
                        onOpenProtection = { currentTab = RafidScreenTab.PROTECTION },
                        onOpenBrowser = { currentTab = RafidScreenTab.BROWSER },
                        onPauseDownload = { rafidViewModel.pauseDownload(it) },
                        onResumeDownload = { rafidViewModel.resumeDownload(it) },
                        onDeleteDownload = { rafidViewModel.deleteDownload(it) },
                        onOpenCompletedFile = ::openCompletedFile,
                        onShowItemInfo = { selectedInfoItem = it },
                        onClearCompleted = { rafidViewModel.clearCompletedDownloads() }
                    )
                }

                RafidScreenTab.BROWSER -> {
                    RafidBrowserScreen(
                        bookmarks = bookmarks,
                        config = config,
                        excludedDomains = excludedSet,
                        isIncognito = isIncognitoBrowser,
                        activePageUrl = currentBrowserUrl,
                        sniffedMedia = sniffedBrowserMedia,
                        onToggleIncognito = { rafidViewModel.toggleIncognitoBrowser() },
                        onPageChanged = { url, title -> rafidViewModel.updateBrowserPage(url, title) },
                        onMediaSniffed = { candidate -> rafidViewModel.onMediaSniffedInBrowser(candidate) },
                        onRequestExtractCurrentOrSniffed = { url, cookies ->
                            rafidViewModel.analyzeUrl(url, explicitCookies = cookies)
                        },
                        onRecordBlocked = { cat -> rafidViewModel.recordBlockedNetworkRequest(cat) },
                        onAddCustomBookmark = { title, url -> rafidViewModel.addCustomBookmark(title, url) },
                        onDeleteCustomBookmark = { id -> rafidViewModel.deleteCustomBookmark(id) }
                    )
                }

                RafidScreenTab.QUEUES -> {
                    RafidQueuesScreen(
                        config = config,
                        downloads = downloads,
                        onBack = { currentTab = RafidScreenTab.HOME },
                        onAdjustMaxConcurrent = { rafidViewModel.adjustMaxConcurrent(it) },
                        onSetWifiOnly = { rafidViewModel.setWifiOnly(it) },
                        onSetScheduledStart = { enabled, hr, min ->
                            rafidViewModel.setScheduledStart(enabled, hr, min)
                        },
                        onToggleQueuePaused = { rafidViewModel.toggleQueuePaused() },
                        onSetBlockAdsAndTrackers = { rafidViewModel.setBlockAdsAndTrackers(it) },
                        onSetSmartEngineEnabled = { rafidViewModel.setSmartEngineEnabled(it) },
                        onOpenProtection = { currentTab = RafidScreenTab.PROTECTION },
                        onPauseDownload = { rafidViewModel.pauseDownload(it) },
                        onResumeDownload = { rafidViewModel.resumeDownload(it) },
                        onDeleteDownload = { rafidViewModel.deleteDownload(it) }
                    )
                }

                RafidScreenTab.PROTECTION -> {
                    RafidProtectionScreen(
                        config = config,
                        excludedDomains = excludedDomains,
                        onBack = { currentTab = RafidScreenTab.QUEUES },
                        onSetBlockAdsAndTrackers = { rafidViewModel.setBlockAdsAndTrackers(it) },
                        onSetBlockMalwareSites = { rafidViewModel.setBlockMalwareSites(it) },
                        onRefreshRulesNow = { rafidViewModel.refreshProtectionRulesNow() },
                        onAddExcludedDomain = { rafidViewModel.addExcludedDomain(it) },
                        onRemoveExcludedDomain = { rafidViewModel.removeExcludedDomain(it) }
                    )
                }

                RafidScreenTab.PLAYLISTS -> {
                    RafidPlaylistsScreen(
                        downloads = downloads,
                        onBack = { currentTab = RafidScreenTab.HOME },
                        onAddPlaylistLink = { openNewDownloadSheetWithClipboard("") },
                        onOpenItem = ::openCompletedFile
                    )
                }
            }

            // Persistent Floating Bottom Pill Bar
            RafidFloatingBottomBar(
                currentTab = currentTab,
                sniffedCount = sniffedBrowserMedia.size,
                onSelectTab = { nextTab -> currentTab = nextTab },
                onCenterFabClick = {
                    if (currentTab == RafidScreenTab.BROWSER && sniffedBrowserMedia.isNotEmpty()) {
                        val first = sniffedBrowserMedia.first()
                        rafidViewModel.analyzeUrl(first.url, explicitCookies = first.cookiesHeader)
                    } else if (currentTab == RafidScreenTab.BROWSER && !currentBrowserUrl.isNullOrBlank()) {
                        rafidViewModel.analyzeUrl(currentBrowserUrl!!)
                    } else {
                        openNewDownloadSheetWithClipboard()
                    }
                },
                onSearchClick = {
                    currentTab = RafidScreenTab.HOME
                    showHomeSearchBar = !showHomeSearchBar
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (showNewLinkSheet) {
            NewDownloadLinkSheet(
                initialUrl = newLinkInitialUrl,
                onPasteFromClipboard = { rafidViewModel.inspectClipboardOnDemand() },
                onAnalyzeUrl = { url ->
                    showNewLinkSheet = false
                    rafidViewModel.analyzeUrl(url)
                },
                onDismiss = { showNewLinkSheet = false }
            )
        }

        if (extractionState !is ExtractionUiState.Idle) {
            SmartDownloadBottomSheet(
                extractionState = extractionState,
                onRetry = { url -> rafidViewModel.analyzeUrl(url) },
                onOpenInBrowser = { url ->
                    rafidViewModel.resetExtraction()
                    rafidViewModel.updateBrowserPage(url, null)
                    currentTab = RafidScreenTab.BROWSER
                },
                onStartDownload = { metadata, format, saveToStudyFolder ->
                    rafidViewModel.enqueueDownload(metadata, format, saveToStudyFolder)
                    if (metadata.isPlaylist && metadata.playlistItems.isNotEmpty()) {
                        val count = metadata.playlistItems.size
                        Toast.makeText(
                            context,
                            if (config.languageMode == com.hawas.study.rafid.models.RafidLanguageMode.ENGLISH) {
                                "Added $count videos to download queue"
                            } else {
                                "تمت إضافة $count فيديوهات لطابور التحميل"
                            },
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    rafidViewModel.resetExtraction()
                    currentTab = RafidScreenTab.HOME
                },
                onDismiss = { rafidViewModel.resetExtraction() }
            )
        }

        if (showLanguageSheet) {
            RafidLanguageSheet(
                currentMode = config.languageMode,
                onSelectMode = { rafidViewModel.setLanguageMode(it) },
                onDismiss = { showLanguageSheet = false }
            )
        }

        selectedInfoItem?.let { info ->
            DownloadItemInfoDialog(
                item = info,
                onDismiss = { selectedInfoItem = null }
            )
        }
    }
}

@Composable
private fun RafidPlaylistsScreen(
    downloads: List<RafidDownloadEntity>,
    onBack: () -> Unit,
    onAddPlaylistLink: () -> Unit,
    onOpenItem: (RafidDownloadEntity) -> Unit
) {
    val palette = LocalRafidPalette.current
    val isEnglish = LocalRafidIsEnglish.current
    val playlistItems = remember(downloads) {
        downloads.filter { it.isPlaylistItem }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .testTag("rafid_playlists_screen")
    ) {
        Surface(
            color = palette.heroBlue,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (isEnglish) "Back" else "رجوع",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isEnglish) "Course Playlists" else "قوائم التشغيل والمقررات",
                    fontFamily = ReadexProFontFamily,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Button(
                    onClick = onAddPlaylistLink,
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.heroBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("playlists_btn_add")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEnglish) "Paste YouTube Playlist Link" else "لصق رابط قائمة تشغيل محاضرات",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (playlistItems.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = palette.surface,
                        border = BorderStroke(1.dp, palette.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlaylistPlay,
                                contentDescription = null,
                                tint = palette.heroBlue,
                                modifier = Modifier.size(38.dp)
                            )
                            Text(
                                text = if (isEnglish) "No playlists downloaded yet" else "لا توجد قوائم تشغيل محمّلة بعد",
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary
                            )
                            Text(
                                text = if (isEnglish) {
                                    "Paste any YouTube course playlist link to extract all lectures at once."
                                } else {
                                    "الصق رابط أي قائمة تشغيل أو دورة على يوتيوب لتحميل جميع المحاضرات بضغطة واحدة."
                                },
                                fontSize = 12.5.sp,
                                color = palette.textSecondary
                            )
                        }
                    }
                }
            } else {
                items(playlistItems, key = { it.id }) { item ->
                    val resolvedThumb = remember(item.thumbnailUrl, item.originalUrl, item.resolvedUrl) {
                        resolveDownloadItemThumbnail(item)
                    }
                    Surface(
                        onClick = { onOpenItem(item) },
                        shape = RoundedCornerShape(18.dp),
                        color = palette.surface,
                        border = BorderStroke(1.dp, palette.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 86.dp, height = 52.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.heroBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlaylistPlay,
                                    contentDescription = null,
                                    tint = palette.heroBlue,
                                    modifier = Modifier.size(26.dp)
                                )
                                if (!resolvedThumb.isNullOrBlank()) {
                                    AsyncImage(
                                        model = resolvedThumb,
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                if (!item.playlistTitle.isNullOrBlank()) {
                                    Text(
                                        text = item.playlistTitle,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.heroBlue,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = item.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.qualityLabel} • ${
                                        RafidFormatUtils.formatFileSize(item.totalBytes, isEnglish)
                                    }",
                                    fontSize = 12.sp,
                                    color = palette.textSecondary
                                )
                                if (item.status == RafidDownloadStatus.DOWNLOADING.name) {
                                    GreenDownloadingBadgePill(isEnglish = isEnglish)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
