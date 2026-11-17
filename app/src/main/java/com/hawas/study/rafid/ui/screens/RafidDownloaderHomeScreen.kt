package com.hawas.study.rafid.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ReadexProFontFamily
import com.hawas.study.rafid.models.RafidActivityFilter
import com.hawas.study.rafid.models.RafidConfigState
import com.hawas.study.rafid.models.RafidDownloadEntity
import com.hawas.study.rafid.models.RafidDownloadStatus
import com.hawas.study.rafid.models.RafidFormatUtils
import com.hawas.study.rafid.ui.components.RafidLogoEmblem
import com.hawas.study.rafid.ui.components.StackedHeroCardContainer
import com.hawas.study.rafid.ui.theme.LocalRafidIsEnglish
import com.hawas.study.rafid.ui.theme.LocalRafidPalette
import kotlin.math.abs

@Composable
fun RafidDownloaderHomeScreen(
    downloads: List<RafidDownloadEntity>,
    config: RafidConfigState,
    showSearchBar: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onBackToApp: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onOpenLanguageSheet: () -> Unit = {},
    onOpenNewDownloadSheet: (String?) -> Unit,
    onOpenClipboardAction: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenQueues: () -> Unit,
    onOpenProtection: () -> Unit,
    onOpenBrowser: () -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onDeleteDownload: (String) -> Unit,
    onOpenCompletedFile: (RafidDownloadEntity) -> Unit,
    onShowItemInfo: (RafidDownloadEntity) -> Unit,
    onClearCompleted: () -> Unit
) {
    val palette = LocalRafidPalette.current
    val isEnglish = LocalRafidIsEnglish.current
    var selectedFilter by remember { mutableStateOf(RafidActivityFilter.ALL) }

    val activeDownload = remember(downloads) {
        downloads.firstOrNull {
            it.status == RafidDownloadStatus.DOWNLOADING.name || it.status == RafidDownloadStatus.MUXING.name
        }
    }
    val totalActiveSpeed = remember(downloads) {
        downloads.filter { it.status == RafidDownloadStatus.DOWNLOADING.name }
            .sumOf { it.speedBytesPerSec }
    }

    val filteredDownloads = remember(downloads, selectedFilter, searchQuery) {
        downloads.filter { item ->
            val matchesFilter = when (selectedFilter) {
                RafidActivityFilter.ALL -> true
                RafidActivityFilter.ACTIVE -> item.status in setOf(
                    RafidDownloadStatus.DOWNLOADING.name,
                    RafidDownloadStatus.MUXING.name,
                    RafidDownloadStatus.PAUSED.name
                )
                RafidActivityFilter.COMPLETED -> item.status == RafidDownloadStatus.COMPLETED.name
                RafidActivityFilter.QUEUED -> item.status == RafidDownloadStatus.QUEUED.name
            }
            val matchesQuery = searchQuery.isBlank() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.sourceDomain.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding()
            .testTag("rafid_home_screen"),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 10.dp,
            bottom = 100.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Clean Top Header Bar (without side translate button; smooth Dark/Light toggle)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBackToApp,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("rafid_back_to_app")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEnglish) "Back to Study Obsession" else "العودة لتطبيق هوس الدراسة",
                            tint = palette.textPrimary
                        )
                    }

                    RafidLogoEmblem(size = 48.dp)

                    Column {
                        Text(
                            text = if (isEnglish) "Al-Risalah" else "الرسالة",
                            fontFamily = ReadexProFontFamily,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary,
                            modifier = Modifier.testTag("rafid_brand_title")
                        )
                        Text(
                            text = if (activeDownload != null) {
                                if (totalActiveSpeed > 0L) {
                                    "${RafidFormatUtils.formatSpeed(totalActiveSpeed, isEnglish)} " +
                                        if (isEnglish) "now" else "الآن"
                                } else {
                                    if (isEnglish) "Processing media..." else "جاري التجهيز والدمج..."
                                }
                            } else {
                                if (isEnglish) "No active downloads" else "لا تحميل جارٍ الآن"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (activeDownload != null) palette.heroBlue else palette.textSecondary
                        )
                    }
                }

                Surface(
                    onClick = onToggleDarkMode,
                    shape = CircleShape,
                    color = palette.surfaceVariant,
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("rafid_btn_dark_mode")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = palette.isDark,
                            transitionSpec = {
                                (fadeIn(tween(240)) + scaleIn(initialScale = 0.8f)) togetherWith
                                    (fadeOut(tween(180)) + scaleOut(targetScale = 0.8f))
                            },
                            label = "rafidThemeIcon"
                        ) { dark ->
                            Icon(
                                imageVector = if (dark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = if (isEnglish) "Toggle Dark/Light Mode" else "تبديل الوضع الليلي والنهاري",
                                tint = palette.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Clean Welcome Headline
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (isEnglish) {
                        "Welcome, what enters your phone stays with you"
                    } else {
                        "أَهلاً بك، ما يدخل هاتفك يبقى معك"
                    },
                    fontFamily = ReadexProFontFamily,
                    fontSize = 25.sp,
                    lineHeight = 35.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    modifier = Modifier.testTag("rafid_welcome_title")
                )
                Text(
                    text = if (isEnglish) {
                        "Choose what benefits your study journey."
                    } else {
                        "اختر ما ينفعك وينظم محاضراتك بسهولة."
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textSecondary
                )
            }
        }

        // 3. Stacked Hero Download Card (#1565C0, 28.dp)
        item {
            StackedHeroDownloadCard(
                activeDownload = activeDownload,
                onNewDownloadClick = { onOpenNewDownloadSheet(null) },
                onPauseActive = { id -> onPauseDownload(id) },
                onInfoClick = { item -> onShowItemInfo(item) }
            )
        }

        // 4. Four Quick Shortcut Tiles (الحافظة | قوائم تشغيل | الطوابير | الحماية)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickShortcutTile(
                    title = if (isEnglish) "Clipboard" else "الحافظة",
                    icon = Icons.Filled.ContentPaste,
                    iconTint = Color(0xFF7C3AED),
                    onClick = onOpenClipboardAction,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("shortcut_clipboard")
                )
                QuickShortcutTile(
                    title = if (isEnglish) "Playlists" else "قوائم تشغيل",
                    icon = Icons.Filled.PlaylistPlay,
                    iconTint = Color(0xFFB91C1C),
                    onClick = onOpenPlaylists,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("shortcut_playlists")
                )
                QuickShortcutTile(
                    title = if (isEnglish) "Queues" else "الطوابير",
                    icon = Icons.Filled.FormatListBulleted,
                    iconTint = Color(0xFF15803D),
                    onClick = onOpenQueues,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("shortcut_queues")
                )
                QuickShortcutTile(
                    title = if (isEnglish) "Protection" else "الحماية",
                    icon = Icons.Filled.Security,
                    iconTint = Color(0xFF0369A1),
                    onClick = onOpenProtection,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("shortcut_protection")
                )
            }
        }

        // Optional inline search box when user taps search in bottom bar
        if (showSearchBar || searchQuery.isNotBlank()) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    textStyle = TextStyle(fontSize = 15.5.sp, fontWeight = FontWeight.Medium),
                    placeholder = {
                        Text(
                            text = if (isEnglish) "Search saved downloads (Muqtana)..." else "ابحث في مُقتَنَى والتنزيلات...",
                            fontSize = 14.5.sp,
                            color = palette.textSecondary
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.heroBlue,
                        unfocusedBorderColor = palette.borderSubtle,
                        focusedContainerColor = palette.surface,
                        unfocusedContainerColor = palette.surface,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rafid_activity_search_input")
                )
            }
        }

        // 5. Activity Section Header + Filter Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isEnglish) "Muqtana & Activity (${downloads.size})" else "مُقتَنَى والنشاط (${downloads.size})",
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = palette.textPrimary
                    )
                    if (downloads.any { it.status == RafidDownloadStatus.COMPLETED.name }) {
                        TextButton(onClick = onClearCompleted) {
                            Text(
                                text = if (isEnglish) "Clear completed" else "تنظيف المكتمل",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.heroBlue
                            )
                        }
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(RafidActivityFilter.entries) { filter ->
                        val selected = selectedFilter == filter
                        FilterChip(
                            selected = selected,
                            onClick = { selectedFilter = filter },
                            label = {
                                Text(
                                    text = if (isEnglish) filter.labelEn else filter.labelAr,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.heroBlue,
                                selectedLabelColor = Color.White,
                                containerColor = palette.surface,
                                labelColor = palette.textSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = palette.borderSubtle
                            ),
                            modifier = Modifier.testTag("activity_chip_${filter.name}")
                        )
                    }
                }
            }
        }

        // 6. Real Downloads List or Clean Empty State
        if (filteredDownloads.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = palette.surface,
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rafid_activity_empty")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Videocam,
                            contentDescription = null,
                            tint = palette.heroBlue,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = if (isEnglish) "Your smart vault (Al-Risalah) is ready" else "الرسالة — خزينة الوسائط والمحاضرات جاهزة",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = palette.textPrimary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (isEnglish) {
                                "Paste a link from YouTube, Pinterest, TikTok or open the Smart Browser. Files save directly in Download/Rafid."
                            } else {
                                "الصق رابط محاضرة أو فيديو من يوتيوب أو بينتيريست أو تيك توك، أو افتح المتصفح الذكي للالتقاط التلقائي. تُحفظ الملفات في Download/Rafid."
                            },
                            fontSize = 12.5.sp,
                            color = palette.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            onClick = onOpenBrowser,
                            shape = RoundedCornerShape(18.dp),
                            color = palette.heroBlue.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = if (isEnglish) "Open Smart Browser" else "افتح المتصفح الذكي",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = palette.heroBlue,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        } else {
            items(filteredDownloads, key = { it.id }) { item ->
                DownloadActivityCard(
                    item = item,
                    onPause = { onPauseDownload(item.id) },
                    onResume = { onResumeDownload(item.id) },
                    onDelete = { onDeleteDownload(item.id) },
                    onOpen = { onOpenCompletedFile(item) },
                    onInfo = { onShowItemInfo(item) }
                )
            }
        }
    }
}

@Composable
private fun StackedHeroDownloadCard(
    activeDownload: RafidDownloadEntity?,
    onNewDownloadClick: () -> Unit,
    onPauseActive: (String) -> Unit,
    onInfoClick: (RafidDownloadEntity) -> Unit
) {
    val palette = LocalRafidPalette.current
    val isEnglish = LocalRafidIsEnglish.current

    StackedHeroCardContainer(modifier = Modifier.testTag("rafid_stacked_hero_card")) {
        if (activeDownload == null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isEnglish) "Nothing downloading right now" else "لا شيء يُحمَّل الآن",
                    fontFamily = ReadexProFontFamily,
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (isEnglish) "Paste a link, or open a site in the browser." else "الصق رابطاً، أو افتح موقعاً في المتصفح.",
                    fontSize = 15.5.sp,
                    lineHeight = 22.sp,
                    color = Color.White.copy(alpha = 0.90f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    onClick = onNewDownloadClick,
                    shape = RoundedCornerShape(26.dp),
                    color = Color(0xFFE3ECFA),
                    modifier = Modifier
                        .heightIn(min = 52.dp)
                        .testTag("hero_btn_new_download")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = palette.heroBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = if (isEnglish) "New Download" else "تحميل جديد",
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.heroBlue
                        )
                    }
                }
            }
        } else {
            val isMuxing = activeDownload.status == RafidDownloadStatus.MUXING.name
            val progress = when {
                isMuxing -> 1f
                activeDownload.totalBytes > 0L -> (activeDownload.downloadedBytes.toFloat() / activeDownload.totalBytes.toFloat()).coerceIn(0f, 1f)
                else -> 0f
            }
            val pct = (progress * 100).toInt()
            var swipeOffset by remember(activeDownload.id) { mutableFloatStateOf(0f) }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Pills: Quality badge + Live Speed + ETA
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFD6E4FA)
                    ) {
                        val badgeLabel = if (isMuxing) {
                            if (isEnglish) "Muxing..." else "دمج سريع..."
                        } else if (activeDownload.isAudioOnly) {
                            activeDownload.qualityLabel
                        } else {
                            (if (isEnglish) "Video " else "فيديو ") + activeDownload.qualityLabel
                        }
                        Text(
                            text = badgeLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.heroBlue,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = palette.heroBlueDarkPill.copy(alpha = 0.72f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = Color(0xFFFACC15),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = RafidFormatUtils.formatSpeed(activeDownload.speedBytesPerSec, isEnglish),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = palette.heroBlueDarkPill.copy(alpha = 0.72f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = RafidFormatUtils.formatEta(activeDownload.etaSeconds, isEnglish),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Title
                Text(
                    text = activeDownload.title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                // Progress Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "$pct%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier
                            .weight(1f)
                            .height(7.dp)
                            .clip(CircleShape)
                    )
                    Text(
                        text = if (activeDownload.totalBytes > 0L) {
                            "${RafidFormatUtils.formatFileSize(activeDownload.downloadedBytes, isEnglish)} / ${
                                RafidFormatUtils.formatFileSize(activeDownload.totalBytes, isEnglish)
                            }"
                        } else {
                            RafidFormatUtils.formatFileSize(activeDownload.downloadedBytes, isEnglish)
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }

                // Bottom Swipe-to-Pause Bar & Info Circle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = Color.White.copy(alpha = 0.20f),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .pointerInput(activeDownload.id) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (abs(swipeOffset) > 65f) {
                                            onPauseActive(activeDownload.id)
                                        }
                                        swipeOffset = 0f
                                    },
                                    onHorizontalDrag = { _, dragAmount ->
                                        swipeOffset += dragAmount
                                    }
                                )
                            }
                            .clickable { onPauseActive(activeDownload.id) }
                            .testTag("hero_swipe_to_pause")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = palette.heroBlueDarkPill,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Pause,
                                        contentDescription = if (isEnglish) "Pause" else "إيقاف مؤقت",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (isEnglish) "Swipe to pause >>>" else "اسحب للإيقاف <<<",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.92f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                    }

                    Surface(
                        onClick = { onInfoClick(activeDownload) },
                        shape = CircleShape,
                        color = Color(0xFFD6E4FA),
                        modifier = Modifier
                            .size(50.dp)
                            .testTag("hero_btn_info")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = if (isEnglish) "Download Info" else "معلومات التحميل",
                                tint = palette.heroBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickShortcutTile(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalRafidPalette.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(18.dp),
            color = palette.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DownloadActivityCard(
    item: RafidDownloadEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    onInfo: () -> Unit
) {
    val palette = LocalRafidPalette.current
    val isEnglish = LocalRafidIsEnglish.current
    val statusEnum = runCatching { RafidDownloadStatus.valueOf(item.status) }
        .getOrDefault(RafidDownloadStatus.QUEUED)

    val typeIcon = when {
        item.isAudioOnly -> Icons.Filled.Audiotrack
        item.containerExt == "pdf" -> Icons.Outlined.Description
        item.containerExt in setOf("jpg", "jpeg", "png", "webp") -> Icons.Outlined.Image
        else -> Icons.Filled.Videocam
    }

    val statusColor = when (statusEnum) {
        RafidDownloadStatus.COMPLETED -> palette.emeraldTint
        RafidDownloadStatus.FAILED -> palette.dangerTint
        RafidDownloadStatus.DOWNLOADING, RafidDownloadStatus.MUXING -> palette.heroBlue
        RafidDownloadStatus.PAUSED -> palette.warningTint
        else -> palette.textSecondary
    }

    val statusText = when (statusEnum) {
        RafidDownloadStatus.QUEUED -> if (isEnglish) "Queued" else "في الانتظار"
        RafidDownloadStatus.DOWNLOADING -> "${RafidFormatUtils.formatSpeed(item.speedBytesPerSec, isEnglish)}"
        RafidDownloadStatus.MUXING -> if (isEnglish) "Serial Muxing..." else "جاري الدمج المتسلسل..."
        RafidDownloadStatus.PAUSED -> if (isEnglish) "Paused" else "متوقف مؤقتاً"
        RafidDownloadStatus.COMPLETED -> if (isEnglish) "Saved in ${item.displayPath}" else "محفوظ في ${item.displayPath}"
        RafidDownloadStatus.FAILED -> item.errorMessage ?: if (isEnglish) "Failed" else "تعذر التحميل"
        RafidDownloadStatus.CANCELLED -> if (isEnglish) "Cancelled" else "ملغى"
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.borderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (statusEnum == RafidDownloadStatus.COMPLETED) onOpen() else onInfo()
            }
            .testTag("download_card_${item.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = statusColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = typeIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp,
                        color = palette.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.qualityLabel} • ${item.sourceDomain} • ${
                            RafidFormatUtils.formatFileSize(
                                if (statusEnum == RafidDownloadStatus.COMPLETED) item.totalBytes else item.downloadedBytes,
                                isEnglish
                            )
                        }",
                        fontSize = 13.sp,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = statusText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (statusEnum) {
                        RafidDownloadStatus.DOWNLOADING -> {
                            IconButton(onClick = onPause, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Filled.Pause, contentDescription = "Pause", tint = palette.heroBlue)
                            }
                        }
                        RafidDownloadStatus.PAUSED, RafidDownloadStatus.FAILED -> {
                            IconButton(onClick = onResume, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    imageVector = if (statusEnum == RafidDownloadStatus.FAILED) Icons.Filled.Refresh else Icons.Filled.PlayArrow,
                                    contentDescription = "Resume",
                                    tint = palette.heroBlue
                                )
                            }
                        }
                        RafidDownloadStatus.COMPLETED -> {
                            IconButton(onClick = onOpen, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Open", tint = palette.emeraldTint)
                            }
                        }
                        else -> {
                            IconButton(onClick = onInfo, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Filled.Info, contentDescription = "Info", tint = palette.textSecondary)
                            }
                        }
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete", tint = palette.dangerTint)
                    }
                }
            }

            if (statusEnum == RafidDownloadStatus.DOWNLOADING || statusEnum == RafidDownloadStatus.MUXING) {
                val p = if (statusEnum == RafidDownloadStatus.MUXING) 1f
                else if (item.totalBytes > 0L) (item.downloadedBytes.toFloat() / item.totalBytes.toFloat()).coerceIn(0f, 1f)
                else 0f
                LinearProgressIndicator(
                    progress = { p },
                    color = palette.heroBlue,
                    trackColor = palette.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape)
                )
            }
        }
    }
}
