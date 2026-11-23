package com.example.download.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.download.data.DownloadEntity
import com.example.download.data.DownloadStatus
import com.example.download.data.LiveProgressSnapshot
import com.example.download.engine.StorageHelper
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadCenterScreen(
    initialSharedUrl: String? = null,
    onConsumeSharedUrl: () -> Unit = {},
    onNavigateBack: () -> Unit,
    vm: DownloadViewModel = viewModel()
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val liveStats by vm.liveStats.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val urlInput by vm.urlInput.collectAsStateWithLifecycle()
    val extractionState by vm.extractionState.collectAsStateWithLifecycle()
    val clipboardPrompt by vm.clipboardPromptUrl.collectAsStateWithLifecycle()
    val snackbarMessage by vm.snackbarMessage.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var cookieLoginUrl by remember { mutableStateOf<String?>(null) }
    var diagnosticTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var activePlayerItem by remember { mutableStateOf<DownloadEntity?>(null) }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val legacyStoragePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun ensureRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.M..Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                legacyStoragePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    LaunchedEffect(initialSharedUrl) {
        if (!initialSharedUrl.isNullOrBlank()) {
            ensureRuntimePermissions()
            vm.startExtraction(initialSharedUrl)
            onConsumeSharedUrl()
        }
    }

    LaunchedEffect(Unit) {
        vm.inspectClipboardText(clipboard.getText()?.text)
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            vm.consumeSnackbar()
        }
    }

    if (activePlayerItem != null) {
        val item = activePlayerItem!!
        val mediaUri = item.outputUri ?: Uri.fromFile(File(item.tempPartPath)).toString()
        InternalVideoPlayerScreen(
            title = item.title,
            mediaUriString = mediaUri,
            onBack = { activePlayerItem = null }
        )
        return
    }

    val activeItems = remember(downloads) {
        downloads.filter {
            it.status in setOf(
                DownloadStatus.QUEUED,
                DownloadStatus.EXTRACTING,
                DownloadStatus.DOWNLOADING,
                DownloadStatus.PAUSED,
                DownloadStatus.WAITING_NETWORK,
                DownloadStatus.MERGING
            )
        }
    }
    val completedItems = remember(downloads) {
        downloads.filter { it.status == DownloadStatus.COMPLETED }
    }
    val failedItems = remember(downloads) {
        downloads.filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELED }
    }

    val aggregateSpeed = remember(liveStats) { liveStats.values.sumOf { it.speedBytesPerSec } }
    val runningCount = remember(activeItems) {
        activeItems.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.MERGING || it.status == DownloadStatus.EXTRACTING }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("التحميل الذكي السريع", fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("smart_download_back_btn")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.testTag("smart_download_settings_btn")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "إعدادات التحميل")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = if (runningCount > 0) "جارٍ تنزيل $runningCount ملف حالياً" else "المحرك جاهز للتحميل السريع",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "نشط: ${activeItems.size} • مكتمل: ${completedItems.size} • فاشل: ${failedItems.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = if (aggregateSpeed > 0L) StorageHelper.formatSpeed(aggregateSpeed) else "${settings.chunkCount} أجزاء",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = clipboardPrompt != null) {
                        clipboardPrompt?.let { clipUrl ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Text(
                                        text = "تم رصد رابط في الحافظة — تحميل؟",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    TextButton(onClick = {
                                        ensureRuntimePermissions()
                                        vm.acceptClipboardUrl(clipUrl)
                                    }) { Text("تحميل") }
                                    IconButton(onClick = { vm.dismissClipboardPrompt() }) {
                                        Icon(Icons.Default.Close, contentDescription = "إغلاق", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { vm.setUrlInput(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("smart_download_url_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            placeholder = { Text("ألصق رابط يوتيوب، تيك توك، بنترست، فيسبوك، إنستغرام أو رابط مباشر...") },
                            trailingIcon = {
                                IconButton(onClick = {
                                    val pasted = clipboard.getText()?.text
                                    if (!pasted.isNullOrBlank()) {
                                        vm.setUrlInput(pasted)
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "لصق")
                                }
                            }
                        )
                        Button(
                            onClick = {
                                ensureRuntimePermissions()
                                vm.startExtraction()
                            },
                            enabled = extractionState !is UiExtractionState.Extracting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("smart_download_extract_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (extractionState is UiExtractionState.Extracting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("جارٍ التحليل الصامت واستخراج الجودات...")
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("استخراج الجودات والتحميل السريع")
                            }
                        }
                    }

                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("جارٍ (${activeItems.size})") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("مكتمل (${completedItems.size})") }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("فاشل (${failedItems.size})") }
                        )
                    }

                    val displayedList = when (selectedTab) {
                        0 -> activeItems
                        1 -> completedItems
                        else -> failedItems
                    }

                    if (displayedList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (selectedTab) {
                                    0 -> "لا توجد تنزيلات نشطة الآن. ألصق أي رابط للبدء فوراً."
                                    1 -> "لم يكتمل تنزيل أي ملف بعد."
                                    else -> "لا توجد مهام فاشلة."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(displayedList, key = { it.id }) { item ->
                                DownloadItemCard(
                                    item = item,
                                    live = liveStats[item.id],
                                    onPause = { vm.pauseDownload(item.id) },
                                    onResume = { vm.resumeDownload(item.id) },
                                    onRetry = { vm.retryDownload(item.id) },
                                    onCancel = { vm.cancelDownload(item.id) },
                                    onDelete = { vm.deleteRecord(item.id) },
                                    onPlayInternal = { activePlayerItem = item },
                                    onOpenExternal = { openDownloadedExternally(context, item) },
                                    onShare = { shareDownloadedFile(context, item) },
                                    onShowDiagnostic = {
                                        diagnosticTarget = item.title to (item.diagnosticLog ?: "لا توجد تفاصيل")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    when (val state = extractionState) {
        is UiExtractionState.SingleVideoReady -> {
            QualitySelectionBottomSheet(
                video = state.video,
                defaultQualityId = state.defaultQualityId,
                onDismiss = { vm.dismissExtractionSheet() },
                onSelectQuality = { quality ->
                    vm.confirmSingleDownload(state.video, quality)
                }
            )
        }
        is UiExtractionState.PlaylistReady -> {
            PlaylistSelectionBottomSheet(
                playlist = state.playlist,
                onDismiss = { vm.dismissExtractionSheet() },
                onConfirmBatch = { entries, qId ->
                    vm.confirmPlaylistDownload(entries, qId)
                }
            )
        }
        is UiExtractionState.DrmBlocked -> {
            AlertDialog(
                onDismissRequest = { vm.dismissExtractionSheet() },
                title = { Text("محتوى محمي") },
                text = { Text(state.messageAr) },
                confirmButton = {
                    Button(onClick = { vm.dismissExtractionSheet() }) { Text("حسناً") }
                }
            )
        }
        is UiExtractionState.Error -> {
            AlertDialog(
                onDismissRequest = { vm.dismissExtractionSheet() },
                title = { Text("تنبيه استخلاص الرابط") },
                text = { Text(state.friendlyMessageAr) },
                confirmButton = {
                    Button(onClick = { vm.startExtraction(state.url) }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("إعادة المحاولة")
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (state.suggestCookieLogin) {
                            TextButton(onClick = {
                                vm.dismissExtractionSheet()
                                val target = if (state.url.contains("instagram", true)) {
                                    "https://www.instagram.com/accounts/login/"
                                } else {
                                    "https://m.facebook.com/login"
                                }
                                cookieLoginUrl = target
                            }) { Text("تسجيل دخول") }
                        }
                        TextButton(onClick = {
                            vm.dismissExtractionSheet()
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.url)))
                            }
                        }) { Text("فتح في المتصفح") }
                    }
                }
            )
        }
        else -> Unit
    }

    if (showSettingsSheet) {
        DownloadSettingsBottomSheet(
            settings = settings,
            hasSavedCookies = vm.hasSavedCookies(),
            onDismiss = { showSettingsSheet = false },
            onUpdateChunkCount = { vm.updateChunkCount(it) },
            onUpdateWifiOnly = { vm.updateWifiOnly(it) },
            onUpdateMaxConcurrent = { vm.updateMaxConcurrent(it) },
            onUpdateSpeedLimit = { vm.updateSpeedLimitKBps(it) },
            onCleanOrphanTemp = { vm.cleanOrphanTempFiles() },
            onOpenCookieLogin = { loginUrl ->
                showSettingsSheet = false
                cookieLoginUrl = loginUrl
            },
            onClearCookies = { vm.clearSavedCookies() }
        )
    }

    cookieLoginUrl?.let { loginUrl ->
        LocalCookieLoginDialog(
            loginUrl = loginUrl,
            cookiesFile = vm.getCookiesFile(),
            onDismiss = { cookieLoginUrl = null },
            onSaved = {
                cookieLoginUrl = null
                Toast.makeText(context, "تم حفظ الجلسة محلياً بأمان", Toast.LENGTH_SHORT).show()
            }
        )
    }

    diagnosticTarget?.let { (title, diag) ->
        DiagnosticLogDialog(
            title = title,
            diagnosticText = diag,
            onDismiss = { diagnosticTarget = null }
        )
    }
}

@Composable
private fun DownloadItemCard(
    item: DownloadEntity,
    live: LiveProgressSnapshot?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onPlayInternal: () -> Unit,
    onOpenExternal: () -> Unit,
    onShare: () -> Unit,
    onShowDiagnostic: () -> Unit
) {
    val statusLabel = when (item.status) {
        DownloadStatus.QUEUED -> "في الطابور"
        DownloadStatus.EXTRACTING -> "جارٍ التحليل"
        DownloadStatus.DOWNLOADING -> "جاري التحميل..."
        DownloadStatus.PAUSED -> "موقوف مؤقتاً"
        DownloadStatus.WAITING_NETWORK -> "بانتظار الإنترنت"
        DownloadStatus.MERGING -> "جارٍ الدمج"
        DownloadStatus.COMPLETED -> "مكتمل"
        DownloadStatus.FAILED -> "تعذر التحميل"
        DownloadStatus.CANCELED -> "ملغى"
    }

    val resolvedThumb = remember(item.thumbnailUrl, item.originalUrl, item.resolvedUrl) {
        item.thumbnailUrl?.takeIf { it.startsWith("http", true) }
            ?: listOf(item.originalUrl, item.resolvedUrl).firstNotNullOfOrNull { u ->
                Regex("""(?:youtu\.be/|youtube\.com/(?:shorts/|watch\?.*v=))([A-Za-z0-9_-]{6,})""")
                    .find(u)?.groupValues?.getOrNull(1)
                    ?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
            }
    }
    val emeraldGreen = Color(0xFF10B981)
    val progressFraction = if (item.totalBytes > 0L) {
        (item.downloadedBytes.toFloat() / item.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val percent = (progressFraction * 100f).toInt()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!resolvedThumb.isNullOrBlank()) {
                    AsyncImage(
                        model = resolvedThumb,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 68.dp, height = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.status == DownloadStatus.DOWNLOADING) {
                        val inf = rememberInfiniteTransition(label = "dc_dl")
                        val offsetY by inf.animateFloat(
                            initialValue = -3f,
                            targetValue = 4f,
                            animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
                            label = "dc_dl_y"
                        )
                        val alphaAnim by inf.animateFloat(
                            initialValue = 0.6f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
                            label = "dc_dl_a"
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(emeraldGreen.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDownward,
                                    contentDescription = "جاري التحميل",
                                    tint = emeraldGreen,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .graphicsLayer {
                                            translationY = offsetY.dp.toPx()
                                            alpha = alphaAnim
                                        }
                                )
                            }
                            Text(
                                text = "جاري التحميل... • ${item.qualityId}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = emeraldGreen
                            )
                        }
                    } else {
                        Text(
                            text = "${item.platform} • ${item.qualityId} • $statusLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.PAUSED ||
                item.status == DownloadStatus.WAITING_NETWORK || item.status == DownloadStatus.MERGING ||
                item.status == DownloadStatus.EXTRACTING
            ) {
                if (item.totalBytes > 0L) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        color = if (item.status == DownloadStatus.DOWNLOADING) emeraldGreen else MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                } else {
                    LinearProgressIndicator(
                        color = if (item.status == DownloadStatus.DOWNLOADING) emeraldGreen else MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val speedStr = live?.speedBytesPerSec?.takeIf { it > 0L }?.let { StorageHelper.formatSpeed(it) } ?: "--"
                    val etaStr = live?.etaSeconds?.takeIf { it > 0L }?.let { "متبقي ${StorageHelper.formatEta(it)}" } ?: ""
                    val threadsCount = live?.activeThreads?.takeIf { it > 0 } ?: item.activeThreads
                    val sizeSummary = if (item.totalBytes > 0L) {
                        "$percent% • ${StorageHelper.formatBytes(item.downloadedBytes)} / ${StorageHelper.formatBytes(item.totalBytes)}"
                    } else {
                        StorageHelper.formatBytes(item.downloadedBytes)
                    }
                    Text(sizeSummary, style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = buildString {
                            append(speedStr)
                            if (threadsCount > 0) append(" • $threadsCount خيوط")
                            if (etaStr.isNotBlank()) append(" • $etaStr")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (item.status == DownloadStatus.COMPLETED) {
                Text(
                    text = "تم الحفظ في: ${item.outputDisplayPath} (${StorageHelper.formatBytes(item.totalBytes)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (item.status == DownloadStatus.FAILED && !item.errorFriendlyMessage.isNullOrBlank()) {
                Text(
                    text = item.errorFriendlyMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (item.status) {
                    DownloadStatus.DOWNLOADING, DownloadStatus.EXTRACTING, DownloadStatus.QUEUED, DownloadStatus.MERGING -> {
                        OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("إيقاف مؤقت")
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = "إلغاء")
                        }
                    }
                    DownloadStatus.PAUSED, DownloadStatus.WAITING_NETWORK -> {
                        Button(onClick = onResume, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("استئناف")
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "حذف")
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        Button(onClick = onPlayInternal, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("تشغيل")
                        }
                        IconButton(onClick = onOpenExternal) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "فتح")
                        }
                        IconButton(onClick = onShare) {
                            Icon(Icons.Default.Share, contentDescription = "مشاركة")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "حذف من السجل")
                        }
                    }
                    DownloadStatus.FAILED, DownloadStatus.CANCELED -> {
                        Button(onClick = onRetry, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("إعادة المحاولة")
                        }
                        if (!item.diagnosticLog.isNullOrBlank()) {
                            IconButton(onClick = onShowDiagnostic) {
                                Icon(Icons.Default.BugReport, contentDescription = "سجل التشخيص")
                            }
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "حذف")
                        }
                    }
                }
            }
        }
    }
}

private fun openDownloadedExternally(context: android.content.Context, item: DownloadEntity) {
    val rawUri = item.outputUri ?: return
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(rawUri), item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "فتح بواسطة"))
    }.onFailure {
        Toast.makeText(context, "تعذر فتح الملف بتطبيق خارجي", Toast.LENGTH_SHORT).show()
    }
}

private fun shareDownloadedFile(context: android.content.Context, item: DownloadEntity) {
    val rawUri = item.outputUri ?: return
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType
            putExtra(Intent.EXTRA_STREAM, Uri.parse(rawUri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة الملف"))
    }.onFailure {
        Toast.makeText(context, "تعذر مشاركة الملف", Toast.LENGTH_SHORT).show()
    }
}
